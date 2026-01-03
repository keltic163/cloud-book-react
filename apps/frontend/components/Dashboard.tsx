import React, { useMemo, useState, useEffect, useRef } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { TransactionType } from '../types';
import { EditTransactionModal } from './TransactionList';
import SystemAnnouncement from './SystemAnnouncement';

const SparklesIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"/></svg>
);

const SWIPE_THRESHOLD = 50; // Minimum pixels for a swipe to register

const Dashboard = () => {
  const { transactions, users, updateTransaction, deleteTransaction, setSelectedDate } = useAppContext();
  const [currentDate, setCurrentDate] = useState(new Date());
  
  // States for Day Detail Drawer
  const [selectedDay, setSelectedDay] = useState<number | null>(null);
  const [editingTxId, setEditingTxId] = useState<string | null>(null);
  
  // Drawer Resizing State
  const [drawerHeight, setDrawerHeight] = useState(400); // Default px height
  const isDragging = useRef(false);
  const dragStartY = useRef(0);
  const dragStartHeight = useRef(0);

  // For swipe functionality on monthly balance card
  const touchStartX = useRef(0);

  // Initialize Drawer Height on Mount based on screen size
  useEffect(() => {
    setDrawerHeight(window.innerHeight * 0.6);
  }, []);

  useEffect(() => {
    if (!selectedDay) {
      document.body.style.overflow = '';
      return;
    }
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = '';
    };
  }, [selectedDay]);

  // Calculate Monthly Stats based on currentDate
  const monthlyStats = useMemo(() => {
    let income = 0;
    let expense = 0;
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    transactions.forEach(t => {
      const tDate = new Date(t.date);
      if (tDate.getFullYear() === year && tDate.getMonth() === month) {
        if (t.type === TransactionType.INCOME) income += t.amount;
        if (t.type === TransactionType.EXPENSE) expense += t.amount;
      }
    });

    return { income, expense, balance: income - expense };
  }, [transactions, currentDate]);

  // ✅ 修改：計算「本月回饋」與「歷史總回饋」
  // 這裡使用 currentDate，所以當你切換月份時，本月回饋也會跟著變
  const rewardStats = useMemo(() => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();

    let monthRewards = 0;
    let totalRewards = 0;

    transactions.forEach(t => {
      const reward = t.rewards || 0;
      if (reward > 0) {
        // 1. 累加到歷史總額
        totalRewards += reward;

        // 2. 檢查是否為當前檢視的月份
        const tDate = new Date(t.date);
        if (tDate.getFullYear() === year && tDate.getMonth() === month) {
          monthRewards += reward;
        }
      }
    });

    return { monthRewards, totalRewards };
  }, [transactions, currentDate]);

  // Calendar Logic
  const calendarData = useMemo(() => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const firstDayOfWeek = new Date(year, month, 1).getDay(); // 0 = Sunday
    
    // Create mapping of 'YYYY-MM-DD' -> { income, expense }
    const dailyStats: Record<string, { income: number, expense: number }> = {};
    
    transactions.forEach(t => {
      const tDate = new Date(t.date);
      if (tDate.getFullYear() === year && tDate.getMonth() === month) {
        const dayKey = tDate.getDate().toString();
        if (!dailyStats[dayKey]) dailyStats[dayKey] = { income: 0, expense: 0 };
        
        if (t.type === TransactionType.INCOME) dailyStats[dayKey].income += t.amount;
        if (t.type === TransactionType.EXPENSE) dailyStats[dayKey].expense += t.amount;
      }
    });

    const days = [];
    // Pad empty days at start
    for (let i = 0; i < firstDayOfWeek; i++) {
      days.push(null);
    }
    // Fill days
    for (let d = 1; d <= daysInMonth; d++) {
      days.push({
        day: d,
        data: dailyStats[d.toString()] || { income: 0, expense: 0 }
      });
    }

    return days;
  }, [transactions, currentDate]);

  // Selected Day Transactions Logic
  const selectedDayTransactions = useMemo(() => {
    if (!selectedDay) return [];
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    return transactions.filter(t => {
        const d = new Date(t.date);
        return d.getFullYear() === year && d.getMonth() === month && d.getDate() === selectedDay;
    }).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  }, [selectedDay, currentDate, transactions]);

  const changeMonth = (offset: number) => {
    const newDate = new Date(currentDate);
    newDate.setMonth(newDate.getMonth() + offset);
    setCurrentDate(newDate);
    setSelectedDay(null); // Close drawer on month change
  };

  const handleDayClick = (day: number) => {
      setSelectedDay(day);
      
      // Update global context selected date
      const newSelectedDate = new Date(currentDate.getFullYear(), currentDate.getMonth(), day);
      setSelectedDate(newSelectedDate);
      
      // Reset height if too small
      if (drawerHeight < 250) {
        setDrawerHeight(window.innerHeight * 0.6);
      }
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches[0].clientX;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    const touchEndX = e.changedTouches[0].clientX;
    const diffX = touchEndX - touchStartX.current;

    if (diffX > SWIPE_THRESHOLD) {
      changeMonth(-1); // Swipe right -> previous month
    } else if (diffX < -SWIPE_THRESHOLD) {
      changeMonth(1); // Swipe left -> next month
    }
    touchStartX.current = 0; // Reset
  };

  // --- Drawer Drag Logic ---
  const handleDragStart = (e: React.PointerEvent) => {
    e.preventDefault(); 
    isDragging.current = true;
    dragStartY.current = e.clientY;
    dragStartHeight.current = drawerHeight;
    
    window.addEventListener('pointermove', handleGlobalDrag);
    window.addEventListener('pointerup', handleDragEnd);
  };



  const handleGlobalDrag = (e: PointerEvent) => {
    if (!isDragging.current) return;
    const delta = dragStartY.current - e.clientY;
    const newHeight = dragStartHeight.current + delta;
    const clampedHeight = Math.min(Math.max(newHeight, 200), window.innerHeight * 0.95);
    setDrawerHeight(clampedHeight);
  };

  const handleDragEnd = () => {
    isDragging.current = false;
    window.removeEventListener('pointermove', handleGlobalDrag);
    window.removeEventListener('pointerup', handleDragEnd);
  };

  const formatCompactNumber = (num: number) => {
    if (num >= 10000) {
      return (num / 1000).toFixed(0) + 'k';
    }
    if (num >= 1000) {
      return (num / 1000).toFixed(1).replace('.0', '') + 'k';
    }
    return num.toString();
  };
  
  const getCategoryColor = (cat: string) => {
    switch (cat) {
      case '餐飲': return 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400';
      case '交通': return 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400';
      case '購物': return 'bg-pink-100 text-pink-600 dark:bg-pink-900/30 dark:text-pink-400';
      case '居住': return 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400';
      case '薪資': return 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400';
      case '娛樂': return 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400';
      case '投資': return 'bg-cyan-100 text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-400';
      default: return 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300';
    }
  };
  
  const getUser = (id: string) => users.find(u => u.uid === id);

  const monthLabel = currentDate.toLocaleString('zh-TW', { year: 'numeric', month: 'long' });
  const editingTransaction = transactions.find(t => t.id === editingTxId);

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10 relative">
      
      {/* 跑馬燈 (最上方) */}
      <SystemAnnouncement />

      {/* Calendar View */}
      <div className="bg-white dark:bg-slate-900 rounded-2xl p-4 shadow-sm border border-slate-100 dark:border-slate-800 relative z-0 transition-colors">
        <div className="flex items-center justify-between mb-4 px-1">
           <h3 className="font-bold text-slate-800 dark:text-white">收支日曆</h3>
           <div className="flex items-center gap-3 bg-slate-50 dark:bg-slate-800 rounded-lg p-1">
             <button 
               onClick={() => changeMonth(-1)} 
               className="p-1 hover:bg-white dark:hover:bg-slate-700 rounded-md transition-colors text-slate-500 dark:text-slate-400"
               aria-label="上一月"
             >
               <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6"/></svg>
             </button>
             <span className="text-sm font-semibold text-slate-700 dark:text-slate-200 w-24 text-center" aria-live="polite">{monthLabel}</span>
             <button 
               onClick={() => changeMonth(1)} 
               className="p-1 hover:bg-white dark:hover:bg-slate-700 rounded-md transition-colors text-slate-500 dark:text-slate-400"
               aria-label="下一月"
             >
               <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6"/></svg>
             </button>


           </div>
        </div>

        <div className="grid grid-cols-7 gap-1 text-center mb-2" role="rowgroup">
           {['日', '一', '二', '三', '四', '五', '六'].map(d => (
             <div key={d} className="text-xs text-slate-400 font-medium" role="columnheader">{d}</div>
           ))}
        </div>

        <div className="grid grid-cols-7 gap-1" role="grid">
           {calendarData.map((item, index) => {
             if (!item) return <div key={`empty-${index}`} className="aspect-square" role="gridcell" aria-hidden="true"></div>;
             
             const hasIncome = item.data.income > 0;
             const hasExpense = item.data.expense > 0;
             const isToday = new Date().toDateString() === new Date(currentDate.getFullYear(), currentDate.getMonth(), item.day).toDateString();
             const isSelected = selectedDay === item.day;

             return (
               <div 
                 key={item.day} 
                 onClick={() => handleDayClick(item.day)}
                 className={`aspect-square rounded-lg border flex flex-col items-center justify-between p-1 transition-all cursor-pointer select-none
                    ${isSelected ? 'bg-indigo-600 border-indigo-600 ring-2 ring-indigo-200 dark:ring-indigo-900 z-10' : ''}
                    ${!isSelected && isToday ? 'bg-indigo-50 border-indigo-200 dark:bg-indigo-900/20 dark:border-indigo-800' : ''}
                    ${!isSelected && !isToday ? 'bg-white border-slate-50 hover:border-slate-300 dark:bg-slate-800 dark:border-slate-700 dark:hover:border-slate-600' : ''}
                 `}
                 role="gridcell"
                 aria-label={`${currentDate.getMonth() + 1}月${item.day}日, 收入 ${item.data.income}元, 支出 ${item.data.expense}元`}
                 aria-current={isToday ? 'date' : undefined}
                 tabIndex={0}
               >
                 <span className={`text-xs font-medium ${isSelected ? 'text-white' : isToday ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400'}`}>{item.day}</span>
                 
                 <div className="flex flex-col items-center justify-end gap-0.5 w-full">
                    {hasIncome && (
                       <span className={`text-[9px] sm:text-[10px] font-bold leading-none tracking-tight ${isSelected ? 'text-emerald-200' : 'text-emerald-500 dark:text-emerald-400'}`}>
                         +{formatCompactNumber(item.data.income)}
                       </span>
                    )}
                    {hasExpense && (
                       <span className={`text-[9px] sm:text-[10px] font-bold leading-none tracking-tight ${isSelected ? 'text-rose-200' : 'text-rose-500 dark:text-rose-400'}`}>
                         -{formatCompactNumber(item.data.expense)}
                       </span>
                    )}
                 </div>
               </div>
             )
           })}
        </div>
      </div>

      {/* Monthly Balance Card */}
      <div 
        className="relative bg-gradient-to-br from-slate-800 to-slate-900 rounded-2xl p-6 text-white shadow-xl cursor-grab active:cursor-grabbing"
        onTouchStart={handleTouchStart}
        onTouchEnd={handleTouchEnd}
        role="region"
        aria-label={`${currentDate.getMonth() + 1}月資產變化`}
      >
        <div className="flex items-center justify-between mb-1"> 
          <button 
            onClick={() => changeMonth(-1)} 
            className="p-1 text-slate-400 hover:text-white rounded-md transition-colors"
            aria-label="上一月"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6"/></svg>
          </button>
          <h2 className="text-slate-400 text-sm font-medium">
            {currentDate.getMonth() + 1}月資產變化
          </h2>
          <button 
            onClick={() => changeMonth(1)} 
            className="p-1 text-slate-400 hover:text-white rounded-md transition-colors"
            aria-label="下一月"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6"/></svg>
          </button>
        </div>
        <div className={`text-4xl font-bold tracking-tight mb-6 ${monthlyStats.balance >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
          {monthlyStats.balance >= 0 ? '+' : ''}{monthlyStats.balance.toLocaleString()}
        </div>
        
        <div className="grid grid-cols-2 gap-4 border-t border-slate-700/50 pt-4">
          <div>
            <div className="flex items-center gap-1.5 text-slate-400 mb-0.5">
              <div className="w-2 h-2 rounded-full bg-emerald-500"></div>
              <span className="text-xs font-semibold uppercase tracking-wider">本月收入</span>
            </div>
            <div className="text-lg font-semibold text-white" aria-label={`本月收入 ${monthlyStats.income.toLocaleString()}元`}>${monthlyStats.income.toLocaleString()}</div>
          </div>
          <div>
            <div className="flex items-center gap-1.5 text-slate-400 mb-0.5">
              <div className="w-2 h-2 rounded-full bg-rose-500"></div>
              <span className="text-xs font-semibold uppercase tracking-wider">本月支出</span>
            </div>
            <div className="text-lg font-semibold text-white" aria-label={`本月支出 ${monthlyStats.expense.toLocaleString()}元`}>${monthlyStats.expense.toLocaleString()}</div>
          </div>
        </div>
      </div>

      {/* ✅ Rewards Card (已更新為：主顯示本月回饋，副顯示歷史累積) */}
      <div className="bg-gradient-to-br from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20 p-5 rounded-2xl border border-amber-100 dark:border-amber-800/30 shadow-sm" role="region" aria-label="信用卡回饋統計">
        <div className="flex items-center gap-3 mb-2">
          <div className="p-2 bg-amber-100 dark:bg-amber-800 rounded-lg text-amber-600 dark:text-amber-300">
            <SparklesIcon className="w-5 h-5" />
          </div>
          <span className="text-sm font-medium text-amber-800 dark:text-amber-200">
            點券折抵
          </span>
        </div>
        
        {/* 主要數字：本月回饋 */}
        <div className="flex items-baseline gap-1">
          <span className="text-2xl font-bold text-amber-700 dark:text-amber-400">
            ${rewardStats.monthRewards.toLocaleString()}
          </span>
          <span className="text-xs text-amber-600/70 dark:text-amber-500/70 font-medium">
            ({currentDate.getMonth() + 1}月)
          </span>
        </div>

        {/* 次要數字：歷史總計 */}
        <div className="mt-1 text-xs text-amber-600/60 dark:text-amber-500/60 font-medium">
          歷史累計: ${rewardStats.totalRewards.toLocaleString()}
        </div>
      </div>

      {/* Slide-Up Day Detail Drawer */}
      {selectedDay && (
        <>
            {/* Backdrop */}
            <div 
                className="fixed inset-0 bg-black/30 backdrop-blur-[1px] z-[40]"
                onClick={() => setSelectedDay(null)}
                role="button"
                aria-label="關閉日交易詳情"
                tabIndex={-1}
            ></div>
            
            {/* Drawer */}
            <div 
                className="fixed bottom-0 left-0 right-0 z-[50] flex justify-center pointer-events-none"
                role="dialog"
                aria-modal="true"
                aria-labelledby="day-detail-title"
            >
                <div 
                  style={{ height: `${drawerHeight}px` }}
                  className="bg-slate-50 dark:bg-slate-900 w-full max-w-md rounded-t-2xl shadow-[0_-5px_25px_-5px_rgba(0,0,0,0.1)] flex flex-col pointer-events-auto animate-in slide-in-from-bottom-full duration-300 transition-[height] ease-out will-change-[height]"
                >
                    {/* Drawer Header (Draggable) */}
                    <div 
                      className="p-4 bg-white dark:bg-slate-900 rounded-t-2xl border-b border-slate-100 dark:border-slate-800 flex flex-col sticky top-0 z-10 cursor-row-resize select-none"
                      style={{ touchAction: 'none' }} /* PREVENTS PULL-TO-REFRESH */
                      onPointerDown={handleDragStart}
                    >
                        {/* Drag Handle Bar */}
                        <div className="w-12 h-1.5 bg-slate-200 dark:bg-slate-700 rounded-full mx-auto mb-3"></div>

                        <div className="flex items-center justify-between pointer-events-auto cursor-auto">
                            <div>
                                <h3 id="day-detail-title" className="text-lg font-bold text-slate-800 dark:text-white">
                                    {currentDate.getMonth() + 1}月 {selectedDay}日
                                </h3>
                                <p className="text-xs text-slate-500 dark:text-slate-400 font-medium">
                                    {selectedDayTransactions.length} 筆交易
                                </p>
                            </div>
                            <button 
                                onClick={() => setSelectedDay(null)}
                                className="p-2 bg-slate-100 dark:bg-slate-800 rounded-full text-slate-500 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
                                aria-label="關閉"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                            </button>
                        </div>
                    </div>

                    {/* Drawer Content (Scrollable) */}
                    <div className="p-4 overflow-y-auto flex-1 bg-slate-50 dark:bg-slate-950">
                        {selectedDayTransactions.length > 0 ? (
                            <div className="space-y-3 pb-safe">
                                {selectedDayTransactions.map(t => {
                                    const user = getUser(t.creatorUid);
                                    const isExpense = t.type === TransactionType.EXPENSE;
                                    return (
                                        <div 
                                            key={t.id} 
                                            onClick={() => setEditingTxId(t.id)}
                                            className="group relative bg-white dark:bg-slate-900 p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm flex items-center justify-between transition-all active:scale-[0.98] cursor-pointer"
                                            role="listitem"
                                            aria-label={`${t.description}，金額 ${isExpense ? '-' : '+'}${t.amount.toLocaleString()}元，由 ${user?.displayName || '未知使用者'} 記錄`}
                                            tabIndex={0}
                                        >
                                            <div className="flex items-center gap-3">
                                                <div className={`w-10 h-10 rounded-full flex items-center justify-center text-base font-bold shrink-0 ${getCategoryColor(t.category)}`} aria-hidden="true">
                                                    {t.category[0]}
                                                </div>
                                                <div className="min-w-0">
                                                    <h4 className="font-semibold text-slate-800 dark:text-slate-100 truncate text-sm">{t.description}</h4>
                                                    <div className="flex items-center gap-2 mt-0.5">
                                                        <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">{t.category}</span>
                                                        {t.rewards > 0 && (
                                                            <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400" aria-label={`回饋 ${t.rewards}點或元`}>
                                                                +{t.rewards}
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="text-right shrink-0">
                                                <div className={`font-bold text-base ${isExpense ? 'text-rose-500 dark:text-rose-400' : 'text-emerald-500 dark:text-emerald-400'}`}>
                                                    {isExpense ? '-' : '+'}${t.amount.toLocaleString()}
                                                </div>
                                                <div className="flex justify-end mt-1">
                                                    {user && (
                                                        <img src={user.photoURL || ''} alt={user.displayName || ''} className="w-4 h-4 rounded-full ring-1 ring-white dark:ring-slate-700 bg-slate-200 dark:bg-slate-600" title={`記錄者：${user.displayName || '訪客'}`} />
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        ) : (
                            <div className="flex flex-col items-center justify-center h-full text-slate-400">
                                <p>當日無交易紀錄</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </>
      )}

      {/* Edit Modal (Imported from TransactionList) */}
      {editingTransaction && (
        <EditTransactionModal 
            transaction={editingTransaction}
            onClose={() => setEditingTxId(null)}
            onSave={(updates) => {
                updateTransaction(editingTransaction.id, updates);
                setEditingTxId(null);
            }}
            onDelete={() => {
                deleteTransaction(editingTransaction.id);
                setEditingTxId(null);
            }}
        />
      )}
    </div>
  );
};

export default Dashboard;
