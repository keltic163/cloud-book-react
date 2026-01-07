import React, { useMemo, useState } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { TransactionType } from '../types';
import { 
  ChevronLeft, 
  ChevronRight, 
  PieChart, 
  TrendingUp, 
  BarChart3, 
  Filter,
  Search,
  RotateCcw
} from 'lucide-react'; 

type TimeRange = 'month' | 'year';

const Statistics = () => {
  // 1. 從 Context 取出資料
  const context = useAppContext();
  const transactions = context.transactions || [];
  const users = context.users || [];
  const expenseCategories = context.expenseCategories || [];
  const incomeCategories = context.incomeCategories || [];

  // --- 狀態管理 ---
  const [currentDate, setCurrentDate] = useState(new Date());
  const [timeRange, setTimeRange] = useState<TimeRange>('month');
  const [viewType, setViewType] = useState<TransactionType>(TransactionType.EXPENSE);
  
  // 篩選狀態 UI
  const [showFilter, setShowFilter] = useState(false);
  
  // 核心篩選條件
  const [selectedMemberId, setSelectedMemberId] = useState<string | 'all'>('all');
  const [filterCategory, setFilterCategory] = useState<string | 'all'>('all');
  const [keyword, setKeyword] = useState('');

  // --- 核心計算邏輯 ---
  const stats = useMemo(() => {
    const year = currentDate.getFullYear();
    const month = currentDate.getMonth(); 

    // 1. 第一層篩選：全域過濾 (成員、關鍵字、分類)
    // 這些篩選會同時影響「年度趨勢」和「詳細圖表」
    let filteredTransactions = transactions.filter(t => {
        const targetId = t.targetUserUid || t.creatorUid;
        // A. 成員篩選（被記帳人）
        if (selectedMemberId !== 'all' && targetId !== selectedMemberId) return false;
        
        // B. 關鍵字篩選 (搜尋備註)
        if (keyword.trim() !== '' && !t.description.includes(keyword.trim())) return false;

        // C. 分類篩選
        // 注意：如果目前看支出，但選了收入分類，邏輯上會變 0，這是正常的
        if (filterCategory !== 'all' && t.category !== filterCategory) return false;

        return true;
    });

    // 2. 準備「年度」數據 (用於長條圖)
    const yearlyData = Array(12).fill(0).map(() => ({ income: 0, expense: 0 }));
    let yearTotalIncome = 0;
    let yearTotalExpense = 0;

    filteredTransactions.forEach(t => {
      const tDate = new Date(t.date);
      if (tDate.getFullYear() === year) {
        const m = tDate.getMonth();
        
        if (t.type === TransactionType.EXPENSE) {
          yearlyData[m].expense += t.amount;
          yearTotalExpense += t.amount;
        } 
        if (t.type === TransactionType.INCOME) {
          yearlyData[m].income += t.amount;
          yearTotalIncome += t.amount;
        }
        // 回饋金計入收入
        if (t.rewards && t.rewards > 0) {
          yearlyData[m].income += t.rewards;
          yearTotalIncome += t.rewards;
        }
      }
    });

    // 3. 準備「本月/本年」顯示用的詳細數據
    const activeTransactions = filteredTransactions.filter(t => {
      const tDate = new Date(t.date);
      if (timeRange === 'month') {
        return tDate.getFullYear() === year && tDate.getMonth() === month;
      } else {
        return tDate.getFullYear() === year;
      }
    });

    // 計算顯示用的總金額
    const displayTotalIncome = timeRange === 'month' 
      ? activeTransactions.reduce((acc, t) => {
          let val = t.type === TransactionType.INCOME ? t.amount : 0;
          val += (t.rewards || 0); 
          return acc + val;
        }, 0)
      : yearTotalIncome;

    const displayTotalExpense = timeRange === 'month'
      ? activeTransactions.reduce((acc, t) => t.type === TransactionType.EXPENSE ? acc + t.amount : acc, 0)
      : yearTotalExpense;

    const displayBalance = displayTotalIncome - displayTotalExpense;

    // 4. 分析圖表用的數據
    let chartTotalAmount = 0;
    let breakdownCategories: { name: string, amount: number }[] = [];
    
    if (viewType === TransactionType.EXPENSE) {
        // 支出分析
        const expTxs = activeTransactions.filter(t => t.type === TransactionType.EXPENSE);
        chartTotalAmount = expTxs.reduce((acc, t) => acc + t.amount, 0);
        
        const safeCategories = Array.isArray(expenseCategories) ? expenseCategories : [];
        breakdownCategories = safeCategories.map(cat => {
            const amount = expTxs
                .filter(t => t.category === cat)
                .reduce((acc, t) => acc + t.amount, 0);
            return { name: cat, amount };
        });
    } else {
        // 收入分析
        const safeCategories = Array.isArray(incomeCategories) ? incomeCategories : [];
        breakdownCategories = safeCategories.map(cat => {
            const amount = activeTransactions
                .filter(t => t.type === TransactionType.INCOME && t.category === cat)
                .reduce((acc, t) => acc + t.amount, 0);
            return { name: cat, amount };
        });

        // 點券折抵虛擬分類
        const totalRewards = activeTransactions.reduce((acc, t) => acc + (t.rewards || 0), 0);
        if (totalRewards > 0) {
            breakdownCategories.push({ name: '點券折抵', amount: totalRewards });
        }

        chartTotalAmount = breakdownCategories.reduce((acc, c) => acc + c.amount, 0);
    }

    breakdownCategories = breakdownCategories.filter(c => c.amount > 0).sort((a, b) => b.amount - a.amount);

    // 成員統計
    const memberStats = users.map(user => {
        let val = 0;
        if (viewType === TransactionType.EXPENSE) {
            val = activeTransactions
                .filter(t => (t.targetUserUid || t.creatorUid) === user.uid && t.type === TransactionType.EXPENSE)
                .reduce((acc, t) => acc + t.amount, 0);
        } else {
            val = activeTransactions
                .filter(t => (t.targetUserUid || t.creatorUid) === user.uid)
                .reduce((acc, t) => {
                    let income = t.type === TransactionType.INCOME ? t.amount : 0;
                    income += (t.rewards || 0);
                    return acc + income;
                }, 0);
        }
        return { ...user, val };
    }).sort((a, b) => b.val - a.val);

    return { 
      yearlyData,
      displayTotalIncome,
      displayTotalExpense,
      displayBalance,
      chartTotalAmount,
      categoryStats: breakdownCategories,
      memberStats
    };
  }, [transactions, users, expenseCategories, incomeCategories, currentDate, timeRange, viewType, selectedMemberId, keyword, filterCategory]);

  // --- Helpers ---
  const changeDate = (offset: number) => {
    const newDate = new Date(currentDate);
    if (timeRange === 'month') {
        newDate.setMonth(newDate.getMonth() + offset);
    } else {
        newDate.setFullYear(newDate.getFullYear() + offset);
    }
    setCurrentDate(newDate);
  };

  const getCategoryColor = (cat: string) => {
    if (cat === '點券折抵') return 'bg-amber-400';
    switch (cat) {
      case '餐飲': return 'bg-orange-500';
      case '交通': return 'bg-blue-500';
      case '購物': return 'bg-pink-500';
      case '居住': return 'bg-purple-500';
      case '娛樂': return 'bg-yellow-500';
      case '薪資': return 'bg-emerald-500';
      case '獎金': return 'bg-yellow-400';
      case '投資': return 'bg-cyan-500';
      default: return 'bg-slate-400'; 
    }
  };

  // 重置篩選
  const resetFilters = () => {
      setKeyword('');
      setSelectedMemberId('all');
      setFilterCategory('all');
  };

  const hasActiveFilters = keyword || selectedMemberId !== 'all' || filterCategory !== 'all';

  // 取得目前可用來篩選的分類清單 (根據當前分析模式)
  const availableFilterCategories = viewType === TransactionType.EXPENSE ? expenseCategories : incomeCategories;

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-24">
      
      {/* 1. Header Control Panel */}
      <div className="bg-[color:var(--app-surface)] dark:bg-slate-900 rounded-2xl p-4 shadow-sm border border-[color:var(--app-border)] dark:border-slate-800 space-y-4">
        
        {/* Top Row: Date & Filter Toggle */}
        <div className="flex items-center justify-between">
            <button onClick={() => changeDate(-1)} className="p-2 hover:bg-[color:var(--app-bg)] dark:hover:bg-slate-800 rounded-full transition-colors">
              <ChevronLeft className="w-5 h-5 text-slate-600 dark:text-slate-400" />
            </button>
            
            <div className="text-center">
              <div className="text-xs text-slate-400 font-medium mb-0.5">
                  {timeRange === 'month' ? `${currentDate.getFullYear()} 年` : '年度報表'}
              </div>
              <div className="text-lg font-bold text-slate-800 dark:text-white flex items-center gap-2 justify-center">
                {timeRange === 'month' ? `${currentDate.getMonth() + 1} 月` : `${currentDate.getFullYear()} 年`}
              </div>
            </div>

            <div className="flex gap-2">
                <button 
                    onClick={() => setShowFilter(!showFilter)}
                    className={`p-2 rounded-full transition-colors relative ${showFilter || hasActiveFilters ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-900/50 dark:text-indigo-400' : 'hover:bg-[color:var(--app-bg)] dark:hover:bg-slate-800 text-slate-600 dark:text-slate-400'}`}
                >
                    <Filter className="w-5 h-5" />
                    {hasActiveFilters && <div className="absolute top-1 right-1 w-2 h-2 bg-rose-500 rounded-full"></div>}
                </button>
                <button onClick={() => changeDate(1)} className="p-2 hover:bg-[color:var(--app-bg)] dark:hover:bg-slate-800 rounded-full transition-colors">
                  <ChevronRight className="w-5 h-5 text-slate-600 dark:text-slate-400" />
                </button>
            </div>
        </div>

        {/* Filter Panel (Expandable) */}
        {showFilter && (
            <div className="pt-4 border-t border-[color:var(--app-border)] dark:border-slate-800 animate-in slide-in-from-top-2 space-y-4">
                
                {/* 1. 檢視模式 & 重置 */}
                <div className="flex items-center justify-between">
                    <div className="flex bg-[color:var(--app-bg)] dark:bg-slate-800 p-0.5 rounded-lg">
                        <button 
                            onClick={() => setTimeRange('month')} 
                            className={`px-3 py-1 text-xs rounded-md transition-all ${timeRange === 'month' ? 'bg-[color:var(--app-surface)] dark:bg-slate-700 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500'}`}
                        >月報表</button>
                        <button 
                            onClick={() => setTimeRange('year')} 
                            className={`px-3 py-1 text-xs rounded-md transition-all ${timeRange === 'year' ? 'bg-[color:var(--app-surface)] dark:bg-slate-700 shadow-sm text-indigo-600 dark:text-indigo-300' : 'text-slate-500'}`}
                        >年趨勢</button>
                    </div>
                    {hasActiveFilters && (
                        <button onClick={resetFilters} className="text-xs text-rose-500 flex items-center gap-1 hover:underline">
                            <RotateCcw className="w-3 h-3" /> 重置條件
                        </button>
                    )}
                </div>

                {/* 2. 關鍵字搜尋 */}
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input 
                        type="text" 
                        value={keyword}
                        onChange={(e) => setKeyword(e.target.value)}
                        placeholder="搜尋備註關鍵字..." 
                        className="w-full pl-9 pr-4 py-2 text-sm bg-[color:var(--app-bg)] dark:bg-slate-800 border border-[color:var(--app-border)] dark:border-slate-700 rounded-xl focus:ring-2 focus:ring-indigo-500 outline-none dark:text-white"
                    />
                </div>
                
                {/* 3. 成員篩選 (橫向捲動) */}
                <div className="space-y-2">
                    <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">成員</span>
                    <div className="flex gap-2 overflow-x-auto pb-1 hide-scrollbar">
                        <button
                            onClick={() => setSelectedMemberId('all')}
                            className={`px-3 py-1.5 rounded-full text-xs font-medium border whitespace-nowrap transition-all ${selectedMemberId === 'all' ? 'bg-indigo-600 border-indigo-600 text-white' : 'bg-[color:var(--app-surface)] dark:bg-slate-800 border-[color:var(--app-border)] dark:border-slate-700 text-slate-600 dark:text-slate-300'}`}
                        >
                            全部
                        </button>
                        {users.map(u => (
                            <button
                                key={u.uid}
                                onClick={() => setSelectedMemberId(u.uid)}
                                className={`px-3 py-1.5 rounded-full text-xs font-medium border whitespace-nowrap flex items-center gap-1 transition-all ${selectedMemberId === u.uid ? 'bg-indigo-600 border-indigo-600 text-white' : 'bg-[color:var(--app-surface)] dark:bg-slate-800 border-[color:var(--app-border)] dark:border-slate-700 text-slate-600 dark:text-slate-300'}`}
                            >
                                <img src={u.photoURL || ''} className="w-3 h-3 rounded-full bg-slate-300" />
                                {u.displayName}
                            </button>
                        ))}
                    </div>
                </div>

                {/* 4. 分類篩選 (橫向捲動) */}
                <div className="space-y-2">
                    <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                            {viewType === TransactionType.EXPENSE ? '支出分類' : '收入分類'}
                        </span>
                        <span className="text-[10px] text-slate-400">(依下方分析模式連動)</span>
                    </div>
                    <div className="flex gap-2 overflow-x-auto pb-1 hide-scrollbar">
                        <button
                            onClick={() => setFilterCategory('all')}
                            className={`px-3 py-1.5 rounded-full text-xs font-medium border whitespace-nowrap transition-all ${filterCategory === 'all' ? 'bg-indigo-600 border-indigo-600 text-white' : 'bg-[color:var(--app-surface)] dark:bg-slate-800 border-[color:var(--app-border)] dark:border-slate-700 text-slate-600 dark:text-slate-300'}`}
                        >
                            全部
                        </button>
                        {availableFilterCategories.map(cat => (
                            <button
                                key={cat}
                                onClick={() => setFilterCategory(cat)}
                                className={`px-3 py-1.5 rounded-full text-xs font-medium border whitespace-nowrap transition-all ${filterCategory === cat ? 'bg-indigo-600 border-indigo-600 text-white' : 'bg-[color:var(--app-surface)] dark:bg-slate-800 border-[color:var(--app-border)] dark:border-slate-700 text-slate-600 dark:text-slate-300'}`}
                            >
                                {cat}
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        )}
      </div>

      {/* 2. 總覽卡片 */}
      <div className="grid grid-cols-3 gap-2 text-center">
        <div className="bg-emerald-50 dark:bg-emerald-900/20 p-3 rounded-xl border border-emerald-100 dark:border-emerald-800/30">
          <div className="text-xs text-emerald-600 dark:text-emerald-400 mb-1">總收入 (含回饋)</div>
          <div className="font-bold text-emerald-700 dark:text-emerald-300 text-sm md:text-base">${stats.displayTotalIncome.toLocaleString()}</div>
        </div>
        <div className="bg-rose-50 dark:bg-rose-900/20 p-3 rounded-xl border border-rose-100 dark:border-rose-800/30">
          <div className="text-xs text-rose-600 dark:text-rose-400 mb-1">總支出</div>
          <div className="font-bold text-rose-700 dark:text-rose-300 text-sm md:text-base">${stats.displayTotalExpense.toLocaleString()}</div>
        </div>
        <div className={`p-3 rounded-xl border ${stats.displayBalance >= 0 ? 'bg-blue-50 border-blue-100 dark:bg-blue-900/20 dark:border-blue-800/30' : 'bg-[color:var(--app-bg)] border-[color:var(--app-border)] dark:bg-slate-800 dark:border-slate-700'}`}>
          <div className="text-xs text-slate-500 dark:text-slate-400 mb-1">{timeRange === 'month' ? '本月' : '年度'}結餘</div>
          <div className={`font-bold text-sm md:text-base ${stats.displayBalance >= 0 ? 'text-blue-700 dark:text-blue-300' : 'text-slate-600 dark:text-slate-400'}`}>${stats.displayBalance.toLocaleString()}</div>
        </div>
      </div>

      {/* 3. 年度趨勢圖 (僅在年模式顯示) */}
      {timeRange === 'year' && (
          <div className="bg-[color:var(--app-surface)] dark:bg-slate-900 rounded-2xl p-5 shadow-sm border border-[color:var(--app-border)] dark:border-slate-800">
              <h3 className="font-semibold text-slate-800 dark:text-slate-100 mb-4 flex items-center gap-2">
                  <BarChart3 className="w-4 h-4" />
                  年度收支趨勢
              </h3>
              <div className="h-40 flex items-end gap-1.5 sm:gap-3">
                  {stats.yearlyData.map((data, index) => {
                      const maxVal = Math.max(
                          ...stats.yearlyData.map(d => Math.max(d.income, d.expense)), 
                          1 
                      );
                      
                      const incHeight = Math.max((data.income / maxVal) * 100, 2);
                      const expHeight = Math.max((data.expense / maxVal) * 100, 2);

                      return (
                          <div key={index} className="flex-1 flex flex-col justify-end items-center group relative">
                              {/* Tooltip */}
                              <div className="opacity-0 group-hover:opacity-100 absolute bottom-full mb-2 bg-slate-800 text-white text-[10px] p-2 rounded pointer-events-none z-10 w-24 text-center">
                                  {index + 1}月<br/>
                                  收: {data.income.toLocaleString()}<br/>
                                  支: {data.expense.toLocaleString()}
                              </div>
                              
                              <div className="w-full flex gap-0.5 sm:gap-1 items-end h-full">
                                  <div 
                                    className={`flex-1 rounded-t-sm transition-all ${data.income > 0 ? 'bg-emerald-400 dark:bg-emerald-500' : 'bg-transparent'}`} 
                                    style={{ height: `${data.income > 0 ? incHeight : 0}%` }}
                                  ></div>
                                  <div 
                                    className={`flex-1 rounded-t-sm transition-all ${data.expense > 0 ? 'bg-rose-400 dark:bg-rose-500' : 'bg-transparent'}`} 
                                    style={{ height: `${data.expense > 0 ? expHeight : 0}%` }}
                                  ></div>
                              </div>
                              <span className="text-[10px] text-slate-400 mt-1">{index + 1}</span>
                          </div>
                      );
                  })}
              </div>
          </div>
      )}

      {/* 4. 分析圖表 (Pie & Member) */}
      <div className="flex bg-[color:var(--app-bg)] dark:bg-slate-800 p-1 rounded-xl">
        <button 
            onClick={() => { setViewType(TransactionType.EXPENSE); setFilterCategory('all'); }} // 切換時重置分類篩選，避免邏輯打架
            className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all ${viewType === TransactionType.EXPENSE ? 'bg-[color:var(--app-surface)] dark:bg-slate-700 text-rose-600 shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'}`}
        >
          支出分析
        </button>
        <button 
            onClick={() => { setViewType(TransactionType.INCOME); setFilterCategory('all'); }} 
            className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all ${viewType === TransactionType.INCOME ? 'bg-[color:var(--app-surface)] dark:bg-slate-700 text-emerald-600 shadow-sm' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'}`}
        >
          收入與回饋
        </button>
      </div>

      {/* 類別排行 */}
      <div className="bg-[color:var(--app-surface)] dark:bg-slate-900 rounded-2xl p-5 shadow-sm border border-[color:var(--app-border)] dark:border-slate-800">
        <h3 className="font-semibold text-slate-800 dark:text-slate-100 mb-4 flex items-center gap-2">
          <PieChart className="w-4 h-4" />
          {viewType === TransactionType.EXPENSE ? '支出類別佔比' : '收入來源佔比'}
        </h3>
        {stats.categoryStats.length > 0 ? (
          <div className="space-y-4">
            {stats.categoryStats.map((cat) => {
              const percentage = stats.chartTotalAmount > 0 ? Math.round((cat.amount / stats.chartTotalAmount) * 100) : 0;
              return (
                <div key={cat.name}>
                  <div className="flex justify-between text-sm mb-1">
                    <span className="font-medium text-slate-700 dark:text-slate-300 flex items-center gap-2">
                        {cat.name}
                        {cat.name === '點券折抵' && <span className="text-[9px] bg-amber-100 text-amber-700 px-1 rounded">HOT</span>}
                    </span>
                    <span className="text-slate-500 dark:text-slate-400">{percentage}% (${cat.amount.toLocaleString()})</span>
                  </div>
                  <div className="w-full bg-[color:var(--app-bg)] dark:bg-slate-700 rounded-full h-2.5 overflow-hidden">
                    <div 
                      className={`h-2.5 rounded-full ${getCategoryColor(cat.name)}`} 
                      style={{ width: `${percentage}%` }}
                    ></div>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="text-center text-slate-400 dark:text-slate-500 py-8 text-sm">
              {hasActiveFilters ? '篩選條件下無資料' : '此期間無資料'}
          </div>
        )}
      </div>

      {/* 成員排行 */}
      <div className="bg-[color:var(--app-surface)] dark:bg-slate-900 rounded-2xl p-5 shadow-sm border border-[color:var(--app-border)] dark:border-slate-800">
        <h3 className="font-semibold text-slate-800 dark:text-slate-100 mb-4 flex items-center gap-2">
          <TrendingUp className="w-4 h-4" />
          {viewType === 'EXPENSE' ? '成員支出排行' : '成員貢獻排行'}
        </h3>
        <div className="space-y-4">
          {stats.memberStats.map((user, idx) => (
             <div key={user.uid} className={`flex items-center justify-between ${user.val === 0 ? 'opacity-50' : ''}`}>
                <div className="flex items-center gap-3">
                   <div className="font-bold text-slate-400 dark:text-slate-500 w-4">{idx + 1}</div>
                   <div className="relative">
                       <img src={user.photoURL || ''} alt={user.displayName || ''} className="w-10 h-10 rounded-full bg-[color:var(--app-bg)] dark:bg-slate-700 object-cover" />
                       <div className={`absolute bottom-0 right-0 w-3 h-3 rounded-full border-2 border-white dark:border-slate-900 ${user.color || 'bg-gray-400'}`}></div>
                   </div>
                   <div className="font-medium text-slate-700 dark:text-slate-200">{user.displayName || '未知成員'}</div>
                </div>
                <div className="text-right">
                   <div className="font-bold text-slate-800 dark:text-white">${user.val.toLocaleString()}</div>
                   <div className="text-xs text-slate-400">
                     {stats.chartTotalAmount > 0 ? Math.round((user.val / stats.chartTotalAmount) * 100) : 0}%
                   </div>
                </div>
             </div>
          ))}
          {stats.memberStats.every(u => u.val === 0) && <p className="text-center text-slate-400 py-2">此期間無資料</p>}
        </div>
      </div>

    </div>
  );
};

export default Statistics;


