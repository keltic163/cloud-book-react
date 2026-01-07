import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { Transaction, TransactionType } from '../types';

const TransactionList = () => {
  const { transactions = [], users = [], deleteTransaction, updateTransaction } = useAppContext();
  const [editingId, setEditingId] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const pageSize = 30;
  const [visibleCount, setVisibleCount] = useState(pageSize);
  const loadMoreRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const id = setTimeout(() => setDebouncedSearch(search.trim().toLowerCase()), 200);
    return () => clearTimeout(id);
  }, [search]);

  useEffect(() => {
    setVisibleCount(pageSize);
  }, [debouncedSearch, transactions.length]);

  const filteredTransactions = useMemo(() => {
    if (!debouncedSearch) return transactions;
    const tokens = debouncedSearch.split(/\s+/).filter(Boolean);
    return transactions.filter((t) => {
      const desc = (t.description || '').toLowerCase();
      const cat = (t.category || '').toLowerCase();
      const amt = String(t.amount || '');
      return tokens.every((token) => desc.includes(token) || cat.includes(token) || amt.includes(token));
    });
  }, [transactions, debouncedSearch]);

  const hasMore = filteredTransactions.length > visibleCount;

  useEffect(() => {
    if (!loadMoreRef.current || !hasMore) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisibleCount((prev) => Math.min(prev + pageSize, filteredTransactions.length));
        }
      },
      { rootMargin: '200px' }
    );
    observer.observe(loadMoreRef.current);
    return () => observer.disconnect();
  }, [hasMore, filteredTransactions.length, pageSize]);

  const getCategoryColor = (cat: string) => {
    switch (cat) {
      case '餐飲': return 'bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400';
      case '交通': return 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400';
      case '日常': return 'bg-pink-100 text-pink-600 dark:bg-pink-900/30 dark:text-pink-400';
      case '居家': return 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400';
      case '社交': return 'bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400';
      case '娛樂': return 'bg-yellow-100 text-yellow-600 dark:bg-yellow-900/30 dark:text-yellow-400';
      case '教育': return 'bg-cyan-100 text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-400';
      default: return 'bg-[color:var(--app-bg)] text-slate-600 dark:bg-slate-700 dark:text-slate-300';
    }
  };

  const getUser = (id: string) => users.find((u) => u.uid === id);
  const editingTransaction = transactions.find((t) => t.id === editingId);

  if (transactions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-slate-400">
        <div className="w-16 h-16 bg-[color:var(--app-bg)] dark:bg-slate-800 rounded-full flex items-center justify-center mb-4 transition-colors">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
        </div>
        <p>尚無交易紀錄</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 pb-24">
      <div className="flex items-center justify-between">
        <h3 className="font-bold text-slate-800 dark:text-slate-100 text-lg transition-colors">近期紀錄</h3>
        <div className="text-sm text-slate-500 dark:text-slate-400">
          總計 {transactions.length} / 符合 {filteredTransactions.length} / 顯示 {Math.min(visibleCount, filteredTransactions.length)}
        </div>
      </div>

      <div className="flex items-center gap-2">
        <div className="flex-1 relative">
          <svg className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/></svg>
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜尋：描述 / 分類 / 金額"
            className="w-full pl-10 pr-10 py-2 rounded-lg border border-[color:var(--app-border)] dark:border-slate-700 bg-[color:var(--app-surface)] dark:bg-slate-800 focus:ring-2 focus:ring-indigo-500 outline-none text-sm text-slate-800 dark:text-slate-100 transition-colors"
            aria-label="搜尋紀錄"
          />
          {search && (
            <button onClick={() => setSearch('')} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-700 dark:text-slate-400">
              ✕
            </button>
          )}
        </div>
      </div>

      <div className="space-y-3">
        {filteredTransactions.length === 0 && (
          <div className="py-8 text-center text-slate-500">沒有符合條件的紀錄</div>
        )}

        {filteredTransactions.slice(0, visibleCount).map((t) => {
          const targetUser = getUser(t.targetUserUid || t.creatorUid);
          const isExpense = t.type === TransactionType.EXPENSE;
          return (
            <div
              key={t.id}
              onClick={() => setEditingId(t.id)}
              className="group relative bg-[color:var(--app-surface)] dark:bg-slate-800 p-4 rounded-xl border border-[color:var(--app-border)] dark:border-slate-700 shadow-sm flex items-center justify-between gap-3 transition-all hover:shadow-md cursor-pointer hover:border-indigo-200 dark:hover:border-indigo-500/50 active:scale-[0.98]"
            >
              <div className="flex items-center gap-4 min-w-0">
                <div className={`w-12 h-12 rounded-full flex items-center justify-center text-lg font-bold shrink-0 transition-colors ${getCategoryColor(t.category)}`}>
                  {t.category?.[0] || '?'}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h4 className="font-semibold text-slate-800 dark:text-slate-100 truncate transition-colors">{t.description}</h4>
                    {t.rewards > 0 && (
                      <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400 flex items-center shrink-0 transition-colors">
                        +{t.rewards} 點
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
                  {targetUser && (
                    <img
                      src={targetUser.photoURL || ''}
                      alt={targetUser.displayName || 'Guest'}
                      className="w-5 h-5 rounded-full ring-2 ring-white dark:ring-slate-700"
                      title={`被記帳人：${targetUser.displayName || 'Guest'}`}
                    />
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {hasMore && (
        <div className="flex flex-col items-center gap-3 pt-2">
          <button
            type="button"
            onClick={() => setVisibleCount((prev) => Math.min(prev + pageSize, filteredTransactions.length))}
            className="px-4 py-2 rounded-full border border-[color:var(--app-border)] dark:border-slate-700 bg-[color:var(--app-surface)] dark:bg-slate-800 text-sm text-slate-600 dark:text-slate-300 hover:border-indigo-300 dark:hover:border-indigo-500/60 transition-colors"
          >
            載入更多
          </button>
          <div ref={loadMoreRef} className="h-1 w-full" aria-hidden="true" />
        </div>
      )}

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

export const EditTransactionModal = ({
  transaction,
  onClose,
  onSave,
  onDelete
}: {
  transaction: Transaction;
  onClose: () => void;
  onSave: (updates: Partial<Transaction>) => void;
  onDelete: () => void;
}) => {
  const { expenseCategories = [], incomeCategories = [], users = [], currentUser } = useAppContext();
  const [amount, setAmount] = useState(transaction.amount.toString());
  const [type, setType] = useState(transaction.type);
  const [category, setCategory] = useState(transaction.category);
  const [description, setDescription] = useState(transaction.description);
  const [rewards, setRewards] = useState(transaction.rewards.toString());
  const [date, setDate] = useState(transaction.date.split('T')[0]);
  const [targetUserUid, setTargetUserUid] = useState(transaction.targetUserUid || transaction.creatorUid);
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);

  const currentCategories = type === TransactionType.EXPENSE ? expenseCategories : incomeCategories;
  const availableUsers = users.length ? users : (currentUser ? [currentUser] : []);

  useEffect(() => {
    if (currentCategories.length > 0 && !currentCategories.includes(category)) {
      setCategory(currentCategories[0]);
    }
  }, [type, currentCategories, category]);

  const inputClass = 'w-full border border-[color:var(--app-border)] dark:border-slate-700 rounded-lg px-3 py-2 text-sm bg-[color:var(--app-bg)] dark:bg-slate-800 text-slate-800 dark:text-slate-100 focus:ring-2 focus:ring-indigo-500 outline-none';

  return (
    <div className="fixed inset-0 z-[200] bg-black/50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="bg-[color:var(--app-surface)] dark:bg-slate-900 rounded-2xl w-full max-w-md p-5" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-bold text-slate-800 dark:text-slate-100">編輯紀錄</h3>

        <div className="mt-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-slate-500">金額</label>
              <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} className={inputClass} />
            </div>
            <div>
              <label className="text-xs text-slate-500">點數 / 回饋</label>
              <input type="number" value={rewards} onChange={(e) => setRewards(e.target.value)} className={inputClass} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-slate-500">類型</label>
              <select value={type} onChange={(e) => setType(e.target.value as TransactionType)} className={inputClass}>
                <option value={TransactionType.EXPENSE}>支出</option>
                <option value={TransactionType.INCOME}>收入</option>
              </select>
            </div>
            <div>
              <label className="text-xs text-slate-500">分類</label>
              <select value={category} onChange={(e) => setCategory(e.target.value)} className={inputClass}>
                {currentCategories.map((cat) => (
                  <option key={cat} value={cat}>{cat}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="text-xs text-slate-500">描述</label>
            <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} className={inputClass} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs text-slate-500">日期</label>
              <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputClass} />
            </div>
            <div>
              <label className="text-xs text-slate-500">被記帳人</label>
              <select value={targetUserUid} onChange={(e) => setTargetUserUid(e.target.value)} className={inputClass}>
                {availableUsers.map((u) => (
                  <option key={u.uid} value={u.uid}>{u.displayName || '訪客'}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <div className="mt-5 flex gap-2">
          <button onClick={onClose} className="flex-1 px-3 py-2 rounded-lg bg-[color:var(--app-bg)] dark:bg-slate-800 text-slate-700 dark:text-slate-300 text-sm">取消</button>
          <button
            onClick={() => onSave({ amount: parseFloat(amount), type, category, description, rewards: parseFloat(rewards) || 0, date, targetUserUid })}
            className="flex-1 px-3 py-2 rounded-lg bg-indigo-600 text-white text-sm"
          >
            儲存
          </button>
        </div>

        <div className="mt-3">
          {!isConfirmingDelete ? (
            <button onClick={() => setIsConfirmingDelete(true)} className="text-xs text-rose-500 hover:underline">刪除紀錄</button>
          ) : (
            <div className="flex gap-2">
              <button onClick={() => setIsConfirmingDelete(false)} className="text-xs text-slate-500">取消</button>
              <button onClick={onDelete} className="text-xs text-rose-600">確認刪除</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default TransactionList;


