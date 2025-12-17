import React, { useState, useEffect } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { parseSmartInput } from '../services/geminiService';
import { TransactionType } from '../types';
// ✅ 新增 Mic, MicOff, Wand2 圖示
import { Mic, MicOff, Check, Wand2 } from 'lucide-react'; 

// 擴充 window 物件以支援瀏覽器語音 API
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
}

const AddTransaction: React.FC<Props> = ({ onComplete }) => {
  // 1. 從 AppContext 取出分類
  const { addTransaction, currentUser, selectedDate, expenseCategories, incomeCategories } = useAppContext();
  const [mode, setMode] = useState<'manual' | 'smart'>('smart');
  
  // Smart Input State
  const [smartInput, setSmartInput] = useState('');
  const [isParsing, setIsParsing] = useState(false);
  const [isListening, setIsListening] = useState(false); // ✅ 語音狀態

  // Manual Form State
  const [amount, setAmount] = useState<string>('');
  const [type, setType] = useState<TransactionType>(TransactionType.EXPENSE);
  
  const [category, setCategory] = useState<string>('');

  // 2. 動態決定目前該顯示哪一組分類
  const currentCategories = type === TransactionType.EXPENSE 
      ? (expenseCategories || []) 
      : (incomeCategories || []);

  // 3. 當切換收支類型時，自動重設分類為該類型的第一個選項
  useEffect(() => {
      if (currentCategories.length > 0) {
          if (!currentCategories.includes(category)) {
              setCategory(currentCategories[0]);
          }
      }
  }, [type, currentCategories, category]);
  
  const [description, setDescription] = useState('');
  const [rewards, setRewards] = useState<string>('0');
  
  // Initialize date with selectedDate or today
  const [date, setDate] = useState(() => {
    const target = selectedDate || new Date();
    const year = target.getFullYear();
    const month = String(target.getMonth() + 1).padStart(2, '0');
    const day = String(target.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  });

  // ✅ 語音輸入處理邏輯
  const handleVoiceInput = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert("您的瀏覽器不支援語音輸入功能 (請使用 Chrome 或 Safari)");
      return;
    }

    if (isListening) {
      setIsListening(false);
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.lang = 'zh-TW'; 
    recognition.continuous = false;
    recognition.interimResults = true;

    recognition.onstart = () => setIsListening(true);

    recognition.onresult = (event: any) => {
      const transcript = Array.from(event.results)
        .map((result: any) => result[0])
        .map((result) => result.transcript)
        .join('');
      
      setSmartInput(transcript); 
    };

    recognition.onend = () => setIsListening(false);
    recognition.onerror = (event: any) => {
      console.error("Speech error", event.error);
      setIsListening(false);
    };

    recognition.start();
  };

  const handleSmartSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!smartInput.trim()) return;

    setIsParsing(true);

    // 4. 將兩組分類合併傳給 AI
    const allCategories = [...(expenseCategories || []), ...(incomeCategories || [])];
    const result = await parseSmartInput(smartInput, allCategories);
    setIsParsing(false);

    if (result) {
      setAmount(result.amount.toString());
      
      const newType = result.type as TransactionType;
      setType(newType);
      
      const targetList = newType === TransactionType.EXPENSE ? expenseCategories : incomeCategories;

      if (targetList && targetList.includes(result.category)) {
          setCategory(result.category);
      } else {
          setCategory(targetList?.[0] || '其他');
      }
      
      setDescription(result.description);
      setRewards(result.rewards?.toString() || '0');

      if (result.date) {
        setDate(result.date);
      }

      setMode('manual');
    } else {
      alert('無法理解輸入內容，請重試或使用手動模式輸入。');
    }
  };

  const handleManualSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    addTransaction({
      amount: parseFloat(amount),
      type,
      category: category,
      description,
      rewards: parseFloat(rewards) || 0,
      date: new Date(date).toISOString(),
      creatorUid: currentUser.uid, 
      ledgerId: 'mock-ledger-1' 
    });
    onComplete();
  };

  const inputClass = "w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 focus:bg-white dark:focus:bg-slate-700 focus:ring-2 focus:ring-indigo-500 outline-none text-slate-900 dark:text-slate-100 transition-colors";

  return (
    <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-sm border border-slate-100 dark:border-slate-800 overflow-hidden transition-colors">
      {/* Tabs */}
      <div className="flex border-b border-slate-100 dark:border-slate-800">
        <button
          onClick={() => setMode('smart')}
          className={`flex-1 py-3 text-sm font-medium transition-colors flex items-center justify-center gap-2 ${mode === 'smart' ? 'bg-indigo-50 dark:bg-indigo-900/20 text-indigo-700 dark:text-indigo-400 border-b-2 border-indigo-600 dark:border-indigo-500' : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300'}`}
        >
          <Wand2 className="w-4 h-4" />
          智慧輸入
        </button>
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
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">
                用文字描述交易
              </label>
              
              {/* ✅ 輸入框與語音按鈕容器 */}
              <div className="relative">
                <textarea
                  value={smartInput}
                  onChange={(e) => setSmartInput(e.target.value)}
                  placeholder="例如：昨天晚餐吃義大利麵 500元，回饋 20 點"
                  className="w-full p-3 pb-12 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 focus:bg-white dark:focus:bg-slate-700 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 min-h-[120px] text-slate-900 dark:text-slate-100 placeholder:text-slate-400 resize-none transition-colors"
                />
                
                {/* 🎤 語音按鈕 */}
                <button
                  type="button"
                  onClick={handleVoiceInput}
                  className={`absolute bottom-3 right-3 p-2 rounded-full transition-all duration-200 shadow-sm ${
                    isListening 
                      ? 'bg-rose-500 text-white animate-pulse shadow-rose-200' 
                      : 'bg-white dark:bg-slate-700 text-slate-500 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-600'
                  }`}
                  title="語音輸入"
                >
                  {isListening ? <MicOff className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
                </button>
              </div>

              <p className="text-xs text-slate-400 mt-2">
                AI 將自動分析金額、分類、描述、回饋以及日期。
              </p>
            </div>
            <button
              type="submit"
              disabled={isParsing || !smartInput}
              className="w-full bg-gradient-to-r from-indigo-600 to-purple-600 text-white py-3 rounded-xl font-medium shadow-lg shadow-indigo-200 dark:shadow-none hover:shadow-xl hover:shadow-indigo-300 transition-all disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {isParsing ? '分析中...' : (
                <>
                  <MagicWandIcon className="w-5 h-5" />
                  解析內容
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
                  className={`${inputClass} p-2.5 text-sm`}
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
                  className={`${inputClass} p-2.5 text-sm text-slate-600 dark:text-slate-300`}
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

            <div>
              <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase mb-1 flex items-center justify-between">
                <span>回饋 / 點數</span>
                <span className="text-[10px] bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400 px-1.5 py-0.5 rounded-full">選填</span>
              </label>
              <div className="relative">
                <input
                  type="number"
                  step="0.1"
                  value={rewards}
                  onChange={(e) => setRewards(e.target.value)}
                  className={`${inputClass} pl-3 pr-4 py-2.5 text-sm focus:ring-amber-400`}
                  placeholder="0"
                />
              </div>
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