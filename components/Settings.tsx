
import React, { useEffect, useRef, useState } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { useAuth } from '../contexts/AuthContext';
import { validateApiKey } from '../services/geminiService';

// --- Icons ---
const DownloadIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
);

const UploadIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
);

const FileTextIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
);

const LinkIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
);

const CopyIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
);

const PlusIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
);

const EditIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/></svg>
);

const CheckCircleIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
);

const MoonIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/></svg>
);

const SunIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/></svg>
);

const LogOutIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
);

const TagIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94 .94-2.48 0-3.42L12 2Z"/><path d="M7 7h.01"/></svg>
);

const TrashIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
);

const RefreshIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>
);

const ChevronRightIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="m9 18 6-6-6-6"/></svg>
);

const SectionCard = ({ title, description, children, defaultOpen = true }: { title: string; description?: string; children: React.ReactNode; defaultOpen?: boolean }) => {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="bg-white dark:bg-slate-900 rounded-2xl p-5 shadow-sm border border-slate-100 dark:border-slate-800">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-bold text-slate-800 dark:text-slate-100">{title}</h3>
          {description && <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">{description}</p>}
        </div>
        <button
          onClick={() => setOpen((v) => !v)}
          className="p-2 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 transition-colors"
          aria-label={open ? '收合' : '展開'}
        >
          <svg className={`w-5 h-5 transition-transform ${open ? '' : '-rotate-90'}`} xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="18 15 12 9 6 15"></polyline></svg>
        </button>
      </div>
      {open && <div className="mt-4 space-y-4">{children}</div>}
    </div>
  );
};

const CategoryManagerModal = ({ onClose }: { onClose: () => void }) => {
  const { expenseCategories, incomeCategories, addCategory, deleteCategory, resetCategories } = useAppContext();
  const [newExpenseCat, setNewExpenseCat] = useState('');
  const [newIncomeCat, setNewIncomeCat] = useState('');

  const handleAdd = async (e: React.FormEvent, type: 'expense' | 'income') => {
    e.preventDefault();
    const val = type === 'expense' ? newExpenseCat : newIncomeCat;
    if (!val.trim()) return;
    await addCategory(type, val.trim());
    if (type === 'expense') setNewExpenseCat('');
    else setNewIncomeCat('');
  };

  const handleDelete = async (type: 'expense' | 'income', cat: string) => {
    if (window.confirm(`確定要刪除「${cat}」分類嗎？`)) {
      await deleteCategory(type, cat);
    }
  };

  const handleReset = async () => {
    if (window.confirm('確定要重置為預設分類嗎？')) {
      await resetCategories();
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm animate-in fade-in duration-200" onClick={onClose}>
      <div
        className="bg-white dark:bg-slate-900 w-full max-w-2xl max-h-[90vh] rounded-2xl shadow-2xl flex flex-col border border-slate-200 dark:border-slate-800 animate-in slide-in-from-bottom-10 duration-300"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex justify-between items-center p-5 border-b border-slate-100 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <TagIcon className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
            <h3 className="font-bold text-lg text-slate-800 dark:text-slate-100">分類管理</h3>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleReset}
              className="p-2 text-slate-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/20 rounded-lg transition-colors"
              title="重置為預設分類"
            >
              <RefreshIcon className="w-5 h-5" />
            </button>
            <button
              onClick={onClose}
              className="p-2 bg-slate-100 dark:bg-slate-800 rounded-full text-slate-500 dark:text-slate-400 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
        </div>

        <div className="overflow-y-auto p-6">
          <div className="grid md:grid-cols-2 gap-8">
            <div className="space-y-4">
              <h4 className="text-base font-bold text-rose-600 dark:text-rose-400 flex items-center gap-2 pb-2 border-b border-rose-100 dark:border-rose-900/30">
                <div className="w-2.5 h-2.5 rounded-full bg-rose-500"></div>
                支出分類
              </h4>
              <div className="flex flex-wrap gap-2 min-h-[100px] content-start">
                {expenseCategories.map((cat) => (
                  <div key={cat} className="group flex items-center gap-2 px-3 py-1.5 bg-rose-50 dark:bg-rose-900/20 border border-rose-100 dark:border-rose-800 rounded-full text-sm text-rose-900 dark:text-rose-100 transition-all hover:border-rose-300">
                    <span>{cat}</span>
                    <button onClick={() => handleDelete('expense', cat)} className="text-rose-400 hover:text-rose-600 dark:hover:text-rose-200 opacity-60 hover:opacity-100">
                      <TrashIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
              <form onSubmit={(e) => handleAdd(e, 'expense')} className="flex gap-2 mt-4">
                <input
                  type="text"
                  value={newExpenseCat}
                  onChange={(e) => setNewExpenseCat(e.target.value)}
                  placeholder="新增支出分類"
                  className="flex-1 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm focus:ring-2 focus:ring-rose-100 dark:focus:ring-rose-900 outline-none dark:text-white"
                />
                <button type="submit" className="bg-rose-500 text-white px-3.5 rounded-lg hover:bg-rose-600 transition-colors">
                  <PlusIcon className="w-5 h-5" />
                </button>
              </form>
            </div>

            <div className="space-y-4">
              <h4 className="text-base font-bold text-emerald-600 dark:text-emerald-400 flex items-center gap-2 pb-2 border-b border-emerald-100 dark:border-emerald-900/30">
                <div className="w-2.5 h-2.5 rounded-full bg-emerald-500"></div>
                收入分類
              </h4>
              <div className="flex flex-wrap gap-2 min-h-[100px] content-start">
                {incomeCategories.map((cat) => (
                  <div key={cat} className="group flex items-center gap-2 px-3 py-1.5 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-100 dark:border-emerald-800 rounded-full text-sm text-emerald-900 dark:text-emerald-100 transition-all hover:border-emerald-300">
                    <span>{cat}</span>
                    <button onClick={() => handleDelete('income', cat)} className="text-emerald-400 hover:text-emerald-600 dark:hover:text-emerald-200 opacity-60 hover:opacity-100">
                      <TrashIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
              <form onSubmit={(e) => handleAdd(e, 'income')} className="flex gap-2 mt-4">
                <input
                  type="text"
                  value={newIncomeCat}
                  onChange={(e) => setNewIncomeCat(e.target.value)}
                  placeholder="新增收入分類"
                  className="flex-1 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm focus:ring-2 focus:ring-emerald-100 dark:focus:ring-emerald-900 outline-none dark:text-white"
                />
                <button type="submit" className="bg-emerald-500 text-white px-3.5 rounded-lg hover:bg-emerald-600 transition-colors">
                  <PlusIcon className="w-5 h-5" />
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

const Settings = () => {
  const {
    transactions,
    users,
    currentUser,
    ledgerId,
    joinLedger,
    savedLedgers,
    switchLedger,
    createLedger,
    leaveLedger,
    updateLedgerAlias,
    isDarkMode,
    toggleTheme,
    syncTransactions,
    lastSyncedAt,
    isSyncing,
  } = useAppContext();
  const { signOut } = useAuth();

  const fileInputRef = useRef<HTMLInputElement>(null);
  const [msg, setMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [partnerLedgerId, setPartnerLedgerId] = useState('');
  const [newLedgerName, setNewLedgerName] = useState('');
  const [isJoining, setIsJoining] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [editingLedgerId, setEditingLedgerId] = useState<string | null>(null);
  const [tempAlias, setTempAlias] = useState('');
  const [showCategoryModal, setShowCategoryModal] = useState(false);

  const [apiKeyInput, setApiKeyInput] = useState<string>(localStorage.getItem('user_gemini_key') || '');
  const [isTesting, setIsTesting] = useState(false);
  const [testResultMsg, setTestResultMsg] = useState<string>('');
  const [models, setModels] = useState<string[]>([]);
  const [modelSelected, setModelSelected] = useState<string>(localStorage.getItem('user_gemini_model') || '');
  const [enabled, setEnabled] = useState<boolean>(localStorage.getItem('user_gemini_enabled') === '1');

  const saveKey = () => {
    try {
      localStorage.setItem('user_gemini_key', apiKeyInput);
      showMsg('success', 'API Key 已儲存於本地');
    } catch (e) {
      showMsg('error', '儲存失敗');
    }
  };

  const clearKey = () => {
    localStorage.removeItem('user_gemini_key');
    setApiKeyInput('');
    setModels([]);
    setModelSelected('');
    setTestResultMsg('');
    showMsg('success', 'API Key 已移除');
  };

  const testKey = async () => {
    setIsTesting(true);
    setTestResultMsg('');
    try {
      const res = await validateApiKey(apiKeyInput || undefined);
      if (res.valid) {
        setModels(res.models);
        setTestResultMsg(res.models.length > 0 ? `可用模型：${res.models.join(', ')}` : '沒有可用模型');
        if (!modelSelected && res.models.length > 0) setModelSelected(res.models[0]);
      } else {
        setModels([]);
        setTestResultMsg('Key 無效或沒有可用模型');
      }
    } catch (e) {
      setTestResultMsg('測試時發生錯誤');
    }
    setIsTesting(false);
  };

  const saveModel = () => {
    if (!modelSelected) {
      showMsg('error', '請先選擇模型');
      return;
    }
    localStorage.setItem('user_gemini_model', modelSelected);
    showMsg('success', '模型已儲存');
  };

  const toggleEnabled = () => {
    const next = !enabled;
    setEnabled(next);
    localStorage.setItem('user_gemini_enabled', next ? '1' : '0');
    try {
      window.dispatchEvent(new CustomEvent('user-gemini-enabled-change', { detail: { enabled: next } }));
    } catch (e) {
      /* ignore */
    }
    showMsg('success', next ? 'AI 已啟用' : 'AI 已停用');
  };

  useEffect(() => {
    if (modelSelected && !models.includes(modelSelected)) {
      setModels((cur) => (modelSelected ? [modelSelected, ...cur] : cur));
    }
  }, []);

  const handleCreateLedger = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newLedgerName.trim()) return;
    setIsCreating(true);
    await createLedger(newLedgerName.trim());
    setIsCreating(false);
    setNewLedgerName('');
    showMsg('success', '已建立並切換至新帳本！');
  };

  const startEditing = (id: string, currentAlias: string) => {
    setEditingLedgerId(id);
    setTempAlias(currentAlias);
  };

  const saveAlias = async () => {
    if (editingLedgerId && tempAlias.trim()) {
      await updateLedgerAlias(editingLedgerId, tempAlias.trim());
      setEditingLedgerId(null);
    }
  };

  const handleLeaveLedger = async (id: string, alias: string) => {
    const confirm = window.confirm(`確定要退出「${alias}」嗎？\n\n退出後帳本資料仍然存在，但您將無法再看到此帳本，除非重新加入。`);
    if (confirm) {
      await leaveLedger(id);
      showMsg('success', `已退出「${alias}」`);
    }
  };

  const copyId = (id: string) => {
    navigator.clipboard.writeText(id);
    showMsg('success', '帳本 ID 已複製');
  };

  const handleJoinLedger = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!partnerLedgerId.trim()) return;
    setIsJoining(true);
    const success = await joinLedger(partnerLedgerId.trim());
    setIsJoining(false);
    if (success) {
      setPartnerLedgerId('');
      showMsg('success', '成功加入共享帳本！');
    } else {
      showMsg('error', '找不到該帳本 ID，請檢查是否正確。');
    }
  };

  const handleExportJSON = () => {
    const backupData = {
      version: 1,
      users: users,
      transactions: transactions,
    };
    downloadFile(JSON.stringify(backupData, null, 2), `CloudLedger_backup_${getDateStr()}.json`, 'application/json');
    showMsg('success', '完整備份 (JSON) 匯出成功！');
  };

  const handleExportCSV = () => {
    const headers = ['Date', 'Type', 'Category', 'Amount', 'Description', 'Rewards', 'Member'];
    const rows = transactions.map((t) => {
      const memberName = users.find((u) => u.uid === t.creatorUid)?.displayName || 'Unknown';
      const safeDesc = `"${t.description.replace(/"/g, '""')}"`;
      const dateStr = t.date.split('T')[0];
      return [dateStr, t.type, t.category, t.amount, safeDesc, t.rewards, `"${memberName}"`].join(',');
    });
    const csvContent = '\uFEFF' + [headers.join(','), ...rows].join('\n');
    downloadFile(csvContent, `CloudLedger_transactions_${getDateStr()}.csv`, 'text/csv;charset=utf-8;');
    showMsg('success', '交易紀錄 (CSV) 匯出成功！');
  };

  const downloadFile = (content: string, filename: string, mimeType: string) => {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const getDateStr = () => new Date().toISOString().split('T')[0];

  const handleImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    showMsg('success', 'CSV 讀取成功！(示意)');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const showMsg = (type: 'success' | 'error', text: string) => {
    setMsg({ type, text });
    setTimeout(() => setMsg(null), 4000);
  };

  const formatLastSync = () => {
    return lastSyncedAt ? new Date(lastSyncedAt).toLocaleString('zh-TW') : '尚未同步';
  };

  const handleManualSync = async () => {
    if (!syncTransactions) return;
    await syncTransactions(true);
    showMsg('success', '已觸發同步');
  };
  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-bold text-slate-800 dark:text-white">設定</h2>
        <div className="flex items-center gap-3">
          <button
            onClick={toggleTheme}
            className="p-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 transition-colors"
            aria-label="切換夜間模式"
          >
            {isDarkMode ? <SunIcon className="w-5 h-5" /> : <MoonIcon className="w-5 h-5" />}
          </button>
          <button onClick={signOut} className="text-sm text-red-500 font-medium px-3 py-1 rounded-lg hover:bg-red-50 dark:hover:bg-red-900/20">
            登出
          </button>
        </div>
      </div>

      {msg && (
        <div className={`p-4 rounded-xl text-sm font-medium ${msg.type === 'success' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400' : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'}`}>
          {msg.text}
        </div>
      )}

      <SectionCard title="資料同步" description="顯示最後同步時間，可手動觸發同步。">
        <div className="flex items-center justify-between gap-3">
          <div className="text-sm text-slate-700 dark:text-slate-200">上次同步：{formatLastSync()}</div>
          <button
            onClick={handleManualSync}
            disabled={isSyncing}
            className="flex items-center gap-2 px-3 py-2 rounded-lg bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 font-medium hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors disabled:opacity-60 border border-indigo-100 dark:border-indigo-800"
          >
            <RefreshIcon className="w-4 h-4" />
            {isSyncing ? '同步中...' : '立即同步'}
          </button>
        </div>
      </SectionCard>

      <SectionCard title="帳本與分類" description="切換帳本、管理分類並查看成員。">
        <div className="space-y-4">
          <div className="space-y-3">
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider">我的帳本列表</label>
            {savedLedgers.map((ledger) => {
              const isActive = ledger.id === ledgerId;
              const isEditing = editingLedgerId === ledger.id;
              return (
                <div
                  key={ledger.id}
                  className={`p-3 rounded-xl border transition-all ${isActive ? 'bg-indigo-50 dark:bg-indigo-900/20 border-indigo-200 dark:border-indigo-800 ring-1 ring-indigo-200 dark:ring-indigo-800' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 hover:border-slate-300'}`}
                >
                  <div className="flex items-center justify-between gap-3">
                    {isEditing ? (
                      <div className="flex-1 flex gap-2">
                        <input
                          type="text"
                          value={tempAlias}
                          onChange={(e) => setTempAlias(e.target.value)}
                          className="flex-1 px-2 py-1 text-sm border rounded dark:bg-slate-700 dark:border-slate-600 dark:text-white"
                          autoFocus
                        />
                        <button onClick={saveAlias} className="text-xs bg-indigo-600 text-white px-2 py-1 rounded">儲存</button>
                      </div>
                    ) : (
                      <div className="flex-1 cursor-pointer" onClick={() => !isActive && switchLedger(ledger.id)}>
                        <div className="flex items-center gap-2">
                          <h4 className={`font-bold text-sm ${isActive ? 'text-indigo-900 dark:text-indigo-300' : 'text-slate-700 dark:text-slate-300'}`}>{ledger.alias}</h4>
                          {isActive && <CheckCircleIcon className="w-4 h-4 text-indigo-600 dark:text-indigo-400" />}
                        </div>
                        <div className="text-[10px] text-slate-400 font-mono mt-0.5 truncate max-w-[180px]">ID: {ledger.id}</div>
                      </div>
                    )}

                    <div className="flex items-center gap-1">
                      <button onClick={() => copyId(ledger.id)} className="p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg" title="複製 ID">
                        <CopyIcon className="w-4 h-4" />
                      </button>
                      <button onClick={() => startEditing(ledger.id, ledger.alias)} className="p-1.5 text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg" title="修改備註">
                        <EditIcon className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleLeaveLedger(ledger.id, ledger.alias)} className="p-1.5 text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg" title="退出帳本">
                        <LogOutIcon className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <div className="grid grid-cols-1 gap-4 border-t border-slate-100 dark:border-slate-800 pt-4">
            <form onSubmit={handleCreateLedger} className="space-y-2">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">建立新帳本</label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={newLedgerName}
                  onChange={(e) => setNewLedgerName(e.target.value)}
                  placeholder="帳本名稱 (例如：旅遊基金)"
                  className="flex-1 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-900 outline-none dark:text-white"
                />
                <button
                  type="submit"
                  disabled={isCreating}
                  className="bg-slate-800 dark:bg-slate-700 text-white px-3 py-2 rounded-lg font-bold text-sm hover:bg-slate-900 dark:hover:bg-slate-600 transition-colors disabled:opacity-50 flex items-center gap-1 whitespace-nowrap"
                >
                  <PlusIcon className="w-4 h-4" />
                  建立
                </button>
              </div>
            </form>

            <form onSubmit={handleJoinLedger} className="space-y-2">
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">加入現有帳本</label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={partnerLedgerId}
                  onChange={(e) => setPartnerLedgerId(e.target.value)}
                  placeholder="貼上對方傳來的 ID"
                  className="flex-1 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-900 outline-none dark:text-white"
                />
                <button
                  type="submit"
                  disabled={isJoining}
                  className="bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 px-3 py-2 rounded-lg font-bold text-sm hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors disabled:opacity-50 whitespace-nowrap"
                >
                  加入
                </button>
              </div>
            </form>
          </div>

          <button
            onClick={() => setShowCategoryModal(true)}
            className="w-full flex items-center justify-between p-4 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-indigo-50 dark:hover:bg-indigo-900/20 hover:border-indigo-200 dark:hover:border-indigo-800 transition-all group"
          >
            <div className="flex items-center gap-3">
              <div className="p-2 bg-indigo-100 dark:bg-indigo-900/40 rounded-lg text-indigo-600 dark:text-indigo-400">
                <TagIcon className="w-5 h-5" />
              </div>
              <div className="text-left">
                <div className="font-bold text-slate-700 dark:text-slate-200 group-hover:text-indigo-700 dark:group-hover:text-indigo-300">分類管理</div>
                <div className="text-xs text-slate-500 dark:text-slate-400">自訂收入與支出類別，整合到帳本管理</div>
              </div>
            </div>
            <ChevronRightIcon className="w-5 h-5 text-slate-400 group-hover:text-indigo-500 group-hover:translate-x-1 transition-all" />
          </button>

          <div className="space-y-2 border-t border-slate-100 dark:border-slate-800 pt-4">
            <div className="flex items-center justify-between">
              <div className="font-semibold text-slate-800 dark:text-slate-100">目前帳本成員 ({users.length})</div>
            </div>
            <div className="space-y-3 max-h-72 overflow-y-auto pr-1">
              {users.map((u, idx) => (
                <div key={u.uid || idx} className="flex items-center justify-between group p-2 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800">
                  <div className="flex items-center gap-3">
                    <div className="relative">
                      <img src={u.photoURL || `https://api.dicebear.com/7.x/avataaars/svg?seed=${u.displayName}`} className="w-10 h-10 rounded-full bg-slate-200 dark:bg-slate-700" alt={u.displayName || ''} />
                    </div>
                    <div>
                      <div className={`text-sm font-semibold ${currentUser.uid === u.uid ? 'text-indigo-900 dark:text-indigo-300' : 'text-slate-700 dark:text-slate-300'}`}>{u.displayName || '未知使用者'}</div>
                      {currentUser.uid === u.uid && <div className="text-xs text-indigo-500 dark:text-indigo-400">就是您</div>}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="資料與匯出" description="下載備份或匯入資料。">
        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={handleExportJSON}
            className="flex flex-col items-center justify-center p-4 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-indigo-50 dark:hover:bg-indigo-900/20 hover:border-indigo-200 dark:hover:border-indigo-800 transition-all gap-2"
          >
            <DownloadIcon className="w-6 h-6 text-indigo-600 dark:text-indigo-400" />
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">匯出 JSON</span>
            <span className="text-[10px] text-slate-400">完整備份</span>
          </button>

          <button
            onClick={handleExportCSV}
            className="flex flex-col items-center justify-center p-4 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-emerald-50 dark:hover:bg-emerald-900/20 hover:border-emerald-200 dark:hover:border-emerald-800 transition-all gap-2"
          >
            <FileTextIcon className="w-6 h-6 text-emerald-600 dark:text-emerald-400" />
            <span className="text-sm font-medium text-slate-700 dark:text-slate-300">匯出 CSV</span>
            <span className="text-[10px] text-slate-400">Excel 分析用</span>
          </button>
        </div>

        <button
          onClick={() => fileInputRef.current?.click()}
          className="w-full flex items-center justify-center gap-2 p-3 bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors text-slate-700 dark:text-slate-300 font-medium shadow-sm"
        >
          <UploadIcon className="w-4 h-4" />
          匯入 CSV 資料
        </button>
        <input type="file" ref={fileInputRef} onChange={handleImport} accept=".csv" className="hidden" />
      </SectionCard>

      <SectionCard title="智慧輸入設定 (AI)" description="Key 只存於本地，不會上傳。" defaultOpen={false}>
        <div className="space-y-3">
          <div>
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2 block">API Key</label>
            <div className="flex gap-2 items-center">
              <input
                type="password"
                value={apiKeyInput}
                onChange={(e) => setApiKeyInput(e.target.value)}
                placeholder="輸入 API Key 或開發測試代碼 (僅本地儲存)"
                className="flex-1 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm focus:ring-2 focus:ring-indigo-100 dark:focus:ring-indigo-900 outline-none dark:text-white"
              />
              <button onClick={saveKey} className="px-3 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700">儲存</button>
              <button onClick={clearKey} className="px-3 py-2 bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 rounded-lg text-sm hover:bg-slate-200">移除</button>
            </div>
            <div className="text-[12px] text-slate-400 mt-1">提示：輸入後只會存在於本地瀏覽器。</div>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2 block">測試 / 取得可用模型</label>
            <div className="flex gap-2 items-center">
              <button onClick={testKey} disabled={isTesting} className="px-3 py-2 bg-emerald-500 text-white rounded-lg text-sm hover:bg-emerald-600">{isTesting ? '測試中...' : '測試並抓取模型'}</button>
              <div className="text-sm text-slate-600 dark:text-slate-300">{testResultMsg}</div>
            </div>

            {models.length > 0 && (
              <div className="mt-3">
                <label className="text-xs font-semibold text-slate-400 mb-2 block">Model</label>
                <div className="flex gap-2 items-center">
                  <select value={modelSelected} onChange={(e) => setModelSelected(e.target.value)} className="flex-1 p-2.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-sm outline-none">
                    {models.map((m) => <option key={m} value={m}>{m}</option>)}
                  </select>
                  <button onClick={saveModel} className="px-3 py-2 bg-indigo-600 text-white rounded-lg text-sm hover:bg-indigo-700">儲存模型</button>
                </div>
              </div>
            )}
          </div>

          <div className="flex items-center justify-between">
            <div>
              <label className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1 block">啟用智慧輸入</label>
              <div className="text-[12px] text-slate-400">開啟後部分頁面會啟用 AI 智慧輸入功能 (需先設定 Key)</div>
            </div>
            <div>
              <label className="inline-flex relative items-center cursor-pointer">
                <input type="checkbox" checked={enabled} onChange={toggleEnabled} className="sr-only peer" />
                <div className={`w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer ${enabled ? 'peer-checked:bg-indigo-600' : ''}`}></div>
              </label>
            </div>
          </div>
        </div>
      </SectionCard>

      <div className="text-center text-xs text-slate-400 py-4">
        CloudLedger 雲記 v3.3.1 (c) 2025 KrendStudio
      </div>

      {showCategoryModal && <CategoryManagerModal onClose={() => setShowCategoryModal(false)} />}
    </div>
  );
};

export default Settings;
