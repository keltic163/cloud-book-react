import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { parseSmartInput } from '../services/geminiService';
import { TransactionType } from '../types';
import { Mic, MicOff, Check, Wand2 } from 'lucide-react';

// Speech recognition support for browsers
declare global {
  interface Window {
    webkitSpeechRecognition: any;
    SpeechRecognition: any;
  }
}

const MagicWandIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="m19 14-4-4 4-4"/><path d="M15 10H7"/><path d="M7 21a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2Z"/></svg>
);

const CheckIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><polyline points="20 6 9 17 4 12"/></svg>
);

interface Props {
  onComplete: () => void;
  autoStartVoice?: boolean;
}

const AddTransaction: React.FC<Props> = ({ onComplete, autoStartVoice = false }) => {
  const { addTransaction, createRecurringTemplate, currentUser, ledgerId, selectedDate, expenseCategories, incomeCategories, users = [] } = useAppContext();
  const [isAIEnabled, setIsAIEnabled] = useState<boolean>(() => localStorage.getItem('user_gemini_enabled') === '1');
  const [mode, setMode] = useState<'manual' | 'smart'>(() => (localStorage.getItem('user_gemini_enabled') === '1' ? 'smart' : 'manual'));

  const [smartInput, setSmartInput] = useState('');
  const [isParsing, setIsParsing] = useState(false);
  const [isListening, setIsListening] = useState(false);

  const [amount, setAmount] = useState<string>('');
  const [type, setType] = useState<TransactionType>(TransactionType.EXPENSE);
  const [category, setCategory] = useState<string>('');
  const [description, setDescription] = useState('');
  const [rewards, setRewards] = useState<string>('');
  const [targetUserUid, setTargetUserUid] = useState('');
  const [isRecurring, setIsRecurring] = useState(false);
  const [executeDay, setExecuteDay] = useState<number>(() => new Date().getDate());
  const [intervalMonths, setIntervalMonths] = useState<number>(1);
  const [runMode, setRunMode] = useState<'continuous' | 'limited'>('continuous');
  const [totalRuns, setTotalRuns] = useState<string>('12');

  const recognitionRef = useRef<any>(null);

  const currentCategories = type === TransactionType.EXPENSE
    ? (expenseCategories || [])
    : (incomeCategories || []);

  const availableUsers = useMemo(() => {
    const list = users.length ? users : [];
    if (currentUser && !list.find((u) => u.uid === currentUser.uid)) {
      return [currentUser, ...list];
    }
    return list;
  }, [users, currentUser]);

  const [date, setDate] = useState(() => {
    const target = selectedDate || new Date();
    const year = target.getFullYear();
    const month = String(target.getMonth() + 1).padStart(2, '0');
    const day = String(target.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  });

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent).detail as { enabled?: boolean } | undefined;
      const enabled = detail?.enabled ?? (localStorage.getItem('user_gemini_enabled') === '1');
      setIsAIEnabled(enabled);
      setMode(enabled ? 'smart' : 'manual');
    };
    window.addEventListener('user-gemini-enabled-change', handler as EventListener);
    return () => window.removeEventListener('user-gemini-enabled-change', handler as EventListener);
  }, []);

  useEffect(() => {
    setIsAIEnabled(localStorage.getItem('user_gemini_enabled') === '1');
  }, []);

  useEffect(() => {
    if (currentCategories.length > 0 && !currentCategories.includes(category)) {
      setCategory(currentCategories[0]);
    }
  }, [currentCategories, category]);

  useEffect(() => {
    if (!selectedDate) return;
    const year = selectedDate.getFullYear();
    const month = String(selectedDate.getMonth() + 1).padStart(2, '0');
    const day = String(selectedDate.getDate()).padStart(2, '0');
    setDate(`${year}-${month}-${day}`);
  }, [selectedDate]);

  useEffect(() => {
    if (!currentUser) return;
    if (!targetUserUid) {
      setTargetUserUid(currentUser.uid);
      return;
    }
    const exists = availableUsers.some((u) => u.uid === targetUserUid);
    if (!exists) {
      setTargetUserUid(currentUser.uid);
    }
  }, [currentUser, availableUsers, targetUserUid]);

  useEffect(() => {
    if (isRecurring) return;
    const day = Number(date.split('-')[2]);
    if (!Number.isNaN(day)) {
      setExecuteDay(day);
    }
  }, [date, isRecurring]);

  const addMonthsWithDay = (base: Date, months: number, day: number) => {
    const year = base.getFullYear();
    const monthIndex = base.getMonth() + months;
    const targetYear = year + Math.floor(monthIndex / 12);
    const targetMonth = ((monthIndex % 12) + 12) % 12;
    const daysInMonth = new Date(targetYear, targetMonth + 1, 0).getDate();
    const safeDay = Math.min(day, daysInMonth);
    const next = new Date(targetYear, targetMonth, safeDay);
    next.setHours(0, 0, 0, 0);
    return next;
  };

  const computeNextRunAt = (day: number, interval: number) => {
    const now = new Date();
    now.setHours(0, 0, 0, 0);
    const base = new Date(date);
    base.setHours(0, 0, 0, 0);
    const daysInMonth = new Date(base.getFullYear(), base.getMonth() + 1, 0).getDate();
    const safeDay = Math.min(day, daysInMonth);
    let next = new Date(base.getFullYear(), base.getMonth(), safeDay);
    next.setHours(0, 0, 0, 0);
    if (next < now) {
      next = addMonthsWithDay(next, interval, day);
    }
    return next;
  };

  const handleVoiceInput = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert('此瀏覽器不支援語音輸入。');
      return;
    }

    if (isListening && recognitionRef.current) {
      recognitionRef.current.stop();
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.lang = 'zh-TW';
    recognition.interimResults = false;
    recognition.onresult = (event: any) => {
      const transcript = Array.from(event.results)
        .map((result: any) => result[0]?.transcript)
        .join(' ');
      setSmartInput((prev) => (prev ? `${prev} ${transcript}` : transcript));
    };
    recognition.onerror = () => {
      setIsListening(false);
    };
    recognition.onend = () => {
      setIsListening(false);
    };

    recognitionRef.current = recognition;
    setIsListening(true);
    recognition.start();
  };

  useEffect(() => {
    if (!autoStartVoice || mode !== 'smart') return;
    handleVoiceInput();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoStartVoice, mode]);

  const handleSmartSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ledgerId || !currentUser) return;
    if (!smartInput.trim()) return;

    setIsParsing(true);
    try {
      const parsed = await parseSmartInput(smartInput, currentCategories);
      if (!parsed) {
        alert('解析失敗，請確認輸入內容。');
        return;
      }

      await addTransaction({
        amount: parsed.amount,
        type: parsed.type,
        category: parsed.category,
        description: parsed.description,
        rewards: parsed.rewards || 0,
        date: parsed.date ? new Date(parsed.date).toISOString() : new Date(date).toISOString(),
        creatorUid: currentUser.uid,
        targetUserUid: targetUserUid || currentUser.uid,
        ledgerId
      });

      setSmartInput('');
      onComplete();
    } catch (err) {
      console.error('Smart input failed:', err);
      alert('智慧輸入失敗，請稍後再試。');
    } finally {
      setIsParsing(false);
    }
  };

  const handleManualSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!ledgerId || !currentUser) return;
    const amountValue = parseFloat(amount);
    if (Number.isNaN(amountValue)) return;

    try {
      await addTransaction({
        amount: amountValue,
        type,
        category,
        description,
        rewards: rewards.trim() === '' ? 0 : parseFloat(rewards) || 0,
        date: new Date(date).toISOString(),
        creatorUid: currentUser.uid,
        targetUserUid: targetUserUid || currentUser.uid,
        ledgerId
      });

      if (isRecurring) {
        const day = Math.min(Math.max(executeDay, 1), 31);
        const interval = Math.max(Number(intervalMonths) || 1, 1);
        const nextRunAt = computeNextRunAt(day, interval);
        const isLimited = runMode === 'limited';
        const runs = isLimited ? Math.max(parseInt(totalRuns, 10) || 1, 1) : undefined;

        await createRecurringTemplate({
          title: description,
          amount: amountValue,
          type: type === TransactionType.EXPENSE ? 'expense' : 'income',
          category,
          note: '',
          intervalMonths: interval,
          executeDay: day,
          nextRunAt,
          totalRuns: runs,
          remainingRuns: runs
        });
      }

      onComplete();
    } catch (err) {
      console.error('Save transaction failed:', err);
      alert('儲存失敗，請稍後再試。');
    }
  };

  const inputClass = 'w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 focus:bg-white dark:focus:bg-slate-700 focus:ring-2 focus:ring-indigo-500 outline-none text-slate-900 dark:text-slate-100 transition-colors';

  return (
    <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-800 overflow-hidden transition-colors">
      <div className="flex border-b border-slate-100 dark:border-slate-800">
        {isAIEnabled && (
          <button
            onClick={() => setMode('smart')}
            className={`flex-1 py-3 text-sm font-medium transition-colors flex items-center justify-center gap-2 ${mode === 'smart' ? 'bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-400 border-b-2 border-indigo-600 dark:border-indigo-500' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'}`}
          >
            <Wand2 className="w-4 h-4" />
            智慧輸入
          </button>
        )}

        <button
          onClick={() => setMode('manual')}
          className={`flex-1 py-3 text-sm font-medium transition-colors ${mode === 'manual' ? 'bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-400 border-b-2 border-indigo-600 dark:border-indigo-500' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'}`}
        >
          手動輸入
        </button>
      </div>

      <div className="p-5">
        {mode === 'smart' ? (
          <form onSubmit={handleSmartSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">輸入描述</label>
              <div className="relative">
                <textarea
                  value={smartInput}
                  onChange={(e) => setSmartInput(e.target.value)}
                  placeholder="例：昨天晚餐 義大利麵 500 折扣 20"
                  className="w-full p-3 pb-12 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 focus:bg-white dark:focus:bg-slate-700 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 min-h-[120px] text-slate-900 dark:text-slate-100 placeholder:text-slate-400 resize-none transition-colors"
                />
                <button
                  type="button"
                  onClick={handleVoiceInput}
                  className={`absolute bottom-3 right-3 p-2 rounded-full transition-all duration-200 shadow-sm ${isListening ? 'bg-rose-500 text-white animate-pulse shadow-rose-200' : 'bg-white dark:bg-slate-700 text-slate-500 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-600'}`}
                  title="語音輸入"
                >
                  {isListening ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
                </button>
              </div>
              <p className="text-xs text-slate-400 mt-2">
                AI 會自動解析金額、分類、描述與日期。
                </p>
              <p className="text-[11px] text-indigo-500 mt-1">
                長按畫面下方的「+」可快速開啟語音辨識，立即開始輸入。
                </p>
            </div>
            <button
              type="submit"
              disabled={isParsing || !smartInput}
              className="w-full bg-gradient-to-r from-indigo-600 to-purple-600 text-white py-3 rounded-xl font-medium shadow-lg shadow-indigo-200 dark:shadow-none hover:shadow-xl hover:shadow-indigo-300 transition-all disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isParsing ? '解析中...' : (
                <>
                  <MagicWandIcon className="w-5 h-5" />
                  解析並儲存
                </>
              )}
            </button>
          </form>
        ) : (
          <form onSubmit={handleManualSubmit} className="space-y-4">
            
            {/* Type Toggle */}
            <div className="flex bg-slate-100 dark:bg-slate-800 p-1 rounded-lg mb-4">
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
                    className={`${inputClass} pl-8 pr-4 py-2.5 font-bold text-lg`}
                    placeholder="0.00"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">分類</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className={`${inputClass} h-10 px-3 py-0 text-sm leading-4`}
                >
                  {/* 5. 使用 currentCategories 渲染選項 */}
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
                  className={`${inputClass} h-10 px-3 py-0 text-sm text-slate-600 dark:text-slate-300 leading-4`}
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
                className={`${inputClass} p-2.5 text-sm`}
                placeholder="這筆消費是為了什麼？"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1 flex items-center justify-between leading-4 min-h-[16px]">
                  <span>回饋 / 點數</span>
                  <span className="text-[10px] bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-1.5 py-0 rounded-full">選填</span>
                </label>
                <div className="relative">
                  <input
                    type="number"
                    step="0.1"
                  value={rewards}
                  onChange={(e) => setRewards(e.target.value)}
                  className={`${inputClass} h-10 px-3 py-0 text-sm text-slate-600 dark:text-slate-300 leading-4`}
                    placeholder="0"
                  />
                </div>
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">被記帳人</label>
                <select
                  value={targetUserUid}
                  onChange={(e) => setTargetUserUid(e.target.value)}
                  className={`${inputClass} h-10 px-3 py-0 text-sm leading-4`}
                >
                  {availableUsers.map((u) => (
                    <option key={u.uid} value={u.uid}>{u.displayName || '訪客'}</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 dark:border-slate-700 p-3 bg-slate-50 dark:bg-slate-800">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase">設為週期性</div>
                  <div className="text-[11px] text-slate-400">每月自動記帳（分期或固定費用）</div>
                </div>
                <label className="inline-flex relative items-center cursor-pointer">
                  <input type="checkbox" checked={isRecurring} onChange={(e) => setIsRecurring(e.target.checked)} className="sr-only" />
                  <div className={`relative w-11 h-6 rounded-full transition-colors ${isRecurring ? 'bg-indigo-600' : 'bg-gray-200'}`}>
                    <span className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition-transform ${isRecurring ? 'translate-x-5' : ''}`}></span>
                  </div>
                </label>
              </div>

              {isRecurring && (
                <div className="mt-3 space-y-3">
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">每月扣款日</label>
                      <select
                        value={executeDay}
                        onChange={(e) => setExecuteDay(Number(e.target.value))}
                        className={`${inputClass} h-10 px-3 py-0 text-sm leading-4`}
                      >
                        {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (
                          <option key={d} value={d}>{d} 號</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">每 N 個月</label>
                      <input
                        type="number"
                        min={1}
                        value={intervalMonths}
                        onChange={(e) => setIntervalMonths(Number(e.target.value))}
                        className={`${inputClass} p-2.5 text-sm`}
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1">執行次數</label>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => setRunMode('continuous')}
                        className={`flex-1 px-3 py-2 rounded-lg text-sm border ${runMode === 'continuous' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'}`}
                      >
                        持續
                      </button>
                      <button
                        type="button"
                        onClick={() => setRunMode('limited')}
                        className={`flex-1 px-3 py-2 rounded-lg text-sm border ${runMode === 'limited' ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300'}`}
                      >
                        指定次數
                      </button>
                    </div>
                    {runMode === 'limited' && (
                      <input
                        type="number"
                        min={1}
                        value={totalRuns}
                        onChange={(e) => setTotalRuns(e.target.value)}
                        className={`${inputClass} p-2.5 text-sm mt-2`}
                        placeholder="例如：12"
                      />
                    )}
                  </div>
                </div>
              )}
            </div>

            <button
              type="submit"
              className="w-full mt-2 bg-indigo-600 text-white py-3 rounded-xl font-medium shadow-md hover:bg-indigo-700 transition-colors flex items-center justify-center gap-2"
            >
              <CheckIcon className="w-5 h-5" />
              儲存交易
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

export default AddTransaction;
