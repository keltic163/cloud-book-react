import React, { useState, useEffect } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { TransactionType, Transaction } from '../types';

const TransactionList = () => {
  const { transactions, users, deleteTransaction, updateTransaction } = useAppContext();
  const [editingId, setEditingId] = useState<string | null>(null);

  // ✅ 優化：加入 dark mode 的顏色配色，讓標籤在黑底上比較柔和
  const getCategoryColor = (cat: string) => {
    switch (cat) {
      case '餐飲': return 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400';
      case '交通': return 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400';
      case '購物': return 'bg-pink-100 text-pink-600 dark:bg-pink-900/30 dark:text-pink-400';
      case '居住': return 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400';
      case '薪資': return 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400';
      case '娛樂': return 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400';
      case '投資': return 'bg-cyan-100 text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-400';
      // 預設顏色 (包含自訂分類)
      default: return 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300';
    }
  };

  const getUser = (id: string) => users.find(u => u.uid === id);

  const editingTransaction = transactions.find(t => t.id === editingId);

  if (transactions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-slate-400">
        <div className="w-16 h-16 bg-slate-100 dark:bg-slate-800 rounded-full flex items-center justify-center mb-4 transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
        </div>
        <p>尚無交易紀錄</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 pb-24">
      <h3 className="font-bold text-slate-800 dark:text-slate-100 text-lg transition-colors">近期紀錄</h3>
      <div className="space-y-3">
        {transactions.map((t) => {
          const user = getUser(t.creatorUid);
          const isExpense = t.type === TransactionType.EXPENSE;
          
          return (
            <div 
                key={t.id} 
                onClick={() => setEditingId(t.id)}
                // ✅ 優化：加入 dark:bg-slate-800, dark:border-slate-700
                className="group relative bg-white dark:bg-slate-800 p-4 rounded-xl border border-slate-100 dark:border-slate-700 shadow-sm flex items-center justify-between gap-3 transition-all hover:shadow-md cursor-pointer hover:border-indigo-200 dark:hover:border-indigo-500/50 active:scale-[0.98]"
            >
              <div className="flex items-center gap-4 min-w-0">
                {/* Category Icon */}
                <div className={`w-12 h-12 rounded-full flex items-center justify-center text-lg font-bold shrink-0 transition-colors ${getCategoryColor(t.category)}`}>
                  {t.category[0]}
                </div>
                
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h4 className="font-semibold text-slate-800 dark:text-slate-100 truncate transition-colors">{t.description}</h4>
                    {t.rewards > 0 && (
                      <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400 flex items-center shrink-0 transition-colors">
                        +{t.rewards} 點/元
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-xs text-slate-400 dark:text-slate-500">
                      {new Date(t.date).toLocaleDateString()}
                    </span>
                    <span className="text-xs text-slate-300 dark:text-slate-600">•</span>
                    <span className="text-xs text-slate-500 dark:text-slate-400 font-medium">{t.category}</span>
                  </div>
                </div>
              </div>

              <div className="text-right shrink-0">
                <div className={`font-bold text-lg ${isExpense ? 'text-rose-500 dark:text-rose-400' : 'text-emerald-500 dark:text-emerald-400'}`}>
                  {isExpense ? '-' : '+'}${t.amount.toLocaleString()}
                </div>
                <div className="flex justify-end mt-1">
                   {user && (
                     <img src={user.photoURL || ''} alt={user.displayName || 'Guest'} className="w-5 h-5 rounded-full ring-2 ring-white dark:ring-slate-700" title={`紀錄者：${user.displayName || 'Guest'}`} />
                   )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Edit Modal */}
      {editingTransaction && (
        <EditTransactionModal 
            transaction={editingTransaction}
            onClose={() => setEditingId(null)}
            onSave={(updates) => {
                updateTransaction(editingTransaction.id, updates);
                setEditingId(null);
            }}
            onDelete={() => {
                deleteTransaction(editingTransaction.id);
                setEditingId(null);
            }}
        />
      )}
    </div>
  );
};

// 放在 TransactionList.tsx 的最下方

// Exporting Modal for use in Dashboard
export const EditTransactionModal = ({ 
    transaction, 
    onClose, 
    onSave, 
    onDelete 
}: { 
    transaction: Transaction, 
    onClose: () => void, 
    onSave: (updates: Partial<Transaction>) => void,
    onDelete: () => void
}) => {
    // ✅ 1. 修正：取出新的分類清單
    const { expenseCategories, incomeCategories } = useAppContext();
    
    const [amount, setAmount] = useState(transaction.amount.toString());
    const [type, setType] = useState(transaction.type);
    const [category, setCategory] = useState(transaction.category);
    const [description, setDescription] = useState(transaction.description);
    const [rewards, setRewards] = useState(transaction.rewards.toString());
    const [date, setDate] = useState(transaction.date.split('T')[0]);
    
    // UI state for delete confirmation
    const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);

    // ✅ 2. 動態決定目前該顯示哪一組分類 (防呆：給予空陣列預設值)
    const currentCategories = type === TransactionType.EXPENSE 
        ? (expenseCategories || []) 
        : (incomeCategories || []);

    // 當切換收支類型時，如果當前分類不在新清單中，重設為第一個
    useEffect(() => {
        if (currentCategories.length > 0 && !currentCategories.includes(category)) {
            setCategory(currentCategories[0]);
        }
    }, [type, currentCategories]);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        onSave({
            amount: parseFloat(amount),
            type,
            category,
            description,
            rewards: parseFloat(rewards) || 0,
            date: new Date(date).toISOString(),
        });
    };

    const handleDeleteClick = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        
        if (isConfirmingDelete) {
            onDelete();
        } else {
            setIsConfirmingDelete(true);
        }
    };

    const handleFormInteract = () => {
        if (isConfirmingDelete) setIsConfirmingDelete(false);
    };

    // 統一樣式 class
    const inputClass = "w-full p-2.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 focus:ring-2 focus:ring-indigo-500 outline-none text-sm text-slate-800 dark:text-slate-100 transition-colors";

    return (
        <div 
            className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-in fade-in duration-200"
            onClick={onClose}
        >
            <div 
                className="bg-white dark:bg-slate-900 w-full max-w-sm rounded-2xl overflow-hidden shadow-2xl animate-in slide-in-from-bottom-10 duration-300 border dark:border-slate-800"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex justify-between items-center p-4 border-b border-slate-100 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50">
                    <h3 className="font-bold text-slate-800 dark:text-slate-100">編輯交易</h3>
                    <button 
                        type="button"
                        onClick={(e) => { e.stopPropagation(); onClose(); }} 
                        className="p-2 bg-slate-200 dark:bg-slate-700 rounded-full text-slate-500 dark:text-slate-400 hover:bg-slate-300 dark:hover:bg-slate-600 transition-colors"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                    </button>
                </div>
                
                <form 
                    onSubmit={handleSubmit} 
                    className="p-5 space-y-4"
                    onClick={handleFormInteract}
                >
                      {/* Type Toggle */}
                      <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-lg">
                        <button
                            type="button"
                            onClick={() => setType(TransactionType.EXPENSE)}
                            className={`flex-1 py-1.5 rounded-md text-sm font-medium transition-all ${type === TransactionType.EXPENSE ? 'bg-white dark:bg-slate-700 text-rose-600 dark:text-rose-400 shadow-sm' : 'text-slate-500 dark:text-slate-400'}`}
                        >
                            支出
                        </button>
                        <button
                            type="button"
                            onClick={() => setType(TransactionType.INCOME)}
                            className={`flex-1 py-1.5 rounded-md text-sm font-medium transition-all ${type === TransactionType.INCOME ? 'bg-white dark:bg-slate-700 text-emerald-600 dark:text-emerald-400 shadow-sm' : 'text-slate-500 dark:text-slate-400'}`}
                        >
                            收入
                        </button>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div className="col-span-2">
                            <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">金額</label>
                            <div className="relative">
                                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 font-bold">$</span>
                                <input
                                    type="number"
                                    step="0.01"
                                    value={amount}
                                    onChange={(e) => setAmount(e.target.value)}
                                    required
                                    className="w-full pl-8 pr-4 py-2 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 focus:ring-2 focus:ring-indigo-500 outline-none font-bold text-lg text-slate-800 dark:text-slate-100 transition-colors"
                                />
                            </div>
                        </div>

                        <div>
                            <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">分類</label>
                            <select
                                value={category}
                                onChange={(e) => setCategory(e.target.value)}
                                className={inputClass}
                            >
                                {/* ✅ 3. 修正：使用 currentCategories 渲染選項 */}
                                {currentCategories.map((c) => (
                                    <option key={c} value={c}>{c}</option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">日期</label>
                            <input
                                type="date"
                                value={date}
                                onChange={(e) => setDate(e.target.value)}
                                className={inputClass}
                            />
                        </div>
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">描述</label>
                        <input
                            type="text"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            required
                            className={inputClass}
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1 flex items-center justify-between">
                            <span>回饋 / 點數</span>
                        </label>
                        <input
                            type="number"
                            step="0.1"
                            value={rewards}
                            onChange={(e) => setRewards(e.target.value)}
                            className={inputClass}
                        />
                    </div>

                    <div className="flex gap-3 pt-2">
                        <button
                            type="button"
                            onClick={handleDeleteClick}
                            className={`flex-1 py-3 rounded-xl font-medium transition-all duration-200 ${
                                isConfirmingDelete 
                                ? 'bg-red-600 text-white shadow-lg scale-105' 
                                : 'bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/40'
                            }`}
                        >
                            {isConfirmingDelete ? '確定刪除？' : '刪除'}
                        </button>
                        <button
                            type="submit"
                            className="flex-[2] bg-indigo-600 text-white py-3 rounded-xl font-medium shadow-md hover:bg-indigo-700 transition-colors"
                        >
                            儲存變更
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};

export default TransactionList;