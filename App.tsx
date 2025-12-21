import React, { useState } from 'react';
import { AppProvider, useAppContext } from './contexts/AppContext'; 
import { AuthProvider, useAuth } from './contexts/AuthContext';
import WelcomeScreen from './components/WelcomeScreen';
import Dashboard from './components/Dashboard';
import AddTransaction from './components/AddTransaction';
import TransactionList from './components/TransactionList';
import Settings from './components/Settings';
import Statistics from './components/Statistics';

// SVG Icons
const HomeIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
);
const PlusCircleIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><circle cx="12" cy="12" r="10"/><path d="M8 12h8"/><path d="M12 8v8"/></svg>
);
const ListIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
);
const SettingsIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.09a2 2 0 0 1-1-1.74v-.47a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.39a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>
);
const ChartPieIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}><path d="M21.21 15.89A10 10 0 1 1 8 2.83"/><path d="M22 12A10 10 0 0 0 12 2v10z"/></svg>
);

// Sync control component (placed in header)
const SyncControl: React.FC = () => {
  const { syncTransactions, isSyncing } = useAppContext();
  const lastFocusRef = React.useRef(0);

  React.useEffect(() => {
    if (!syncTransactions) return;
    const handler = () => {
      const now = Date.now();
      if (now - lastFocusRef.current > 30000) {
        lastFocusRef.current = now;
        syncTransactions();
      }
    };
    window.addEventListener('focus', handler);
    return () => window.removeEventListener('focus', handler);
  }, [syncTransactions]);

  const label = isSyncing ? '同步中...' : '立即同步';

  return (
    <div className="flex items-center gap-2">
      <button
        onClick={() => syncTransactions && syncTransactions(true)}
        className="flex items-center gap-1 px-3 py-1.5 rounded-md text-xs bg-white dark:bg-slate-700 hover:opacity-90 transition shadow-sm border border-slate-200 dark:border-slate-600"
        aria-label={label}
        title={label}
      >
        <svg className="w-4 h-4 text-slate-600 dark:text-slate-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 12a9 9 0 1 0-3.16 6.36"/><path d="M21 12v-5"/></svg>
        <span className="text-[11px] font-medium">{label}</span>
      </button>
    </div>
  );
};

const Layout = () => {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'add' | 'list' | 'settings' | 'stats'>('dashboard');
  const [voiceAutoStart, setVoiceAutoStart] = useState(false);
  const pressTimerRef = React.useRef<number | null>(null);
  const { currentUser } = useAppContext(); 

  const startPress = () => {
    // 700ms 長按判定
    if (pressTimerRef.current) window.clearTimeout(pressTimerRef.current);
    pressTimerRef.current = window.setTimeout(() => {
      // 長按處理：檢查是否設定了 API Key 並啟用
      const key = localStorage.getItem('user_gemini_key');
      const enabled = localStorage.getItem('user_gemini_enabled') === '1';
      if (key && enabled) {
        setVoiceAutoStart(true);
        setActiveTab('add');
      } else {
        const go = window.confirm('尚未設定 API Key 或未啟用 AI，是否現在前往設定頁？');
        if (go) setActiveTab('settings');
      }
    }, 700);
  };

  const cancelPress = () => {
    if (pressTimerRef.current) {
      window.clearTimeout(pressTimerRef.current);
      pressTimerRef.current = null;
    }
  };

  const endPress = () => {
    if (pressTimerRef.current) {
      // 短按 (未達 700ms) -> 正常行為 (由 onClick 處理切換到 add)
      window.clearTimeout(pressTimerRef.current);
      pressTimerRef.current = null;
    } else {
      // 如果 timer 已經觸發（長按），則重置 flag（在 AddTransaction mount 時會讀取）
      // Reset after a short delay so AddTransaction can see the flag
      setTimeout(() => setVoiceAutoStart(false), 1000);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-100 pb-24 transition-colors duration-300">
      {/* Top Header */}
      <header className="bg-white dark:bg-slate-900 shadow-sm border-b border-transparent dark:border-slate-800 px-4 py-3 sticky top-0 z-10 flex justify-between items-center transition-colors">
        <div className="flex items-center gap-2">
          <img 
                src="/apple-touch-icon.png" 
                alt="Logo" 
                className="w-10 h-10 rounded-xl shadow-lg shadow-indigo-200 dark:shadow-none" 
              />
          <h1 className="text-xl font-bold text-slate-800 dark:text-white tracking-tight">CloudLedger 雲記</h1>
        </div>
        
        <div className="relative flex items-center gap-3">
          {/* Sync button (moved from Dashboard) */}
          {/* Shows on larger screens; on small screens it's an icon only */}
          <SyncControl />

          <button 
            onClick={() => setActiveTab('settings')}
            className="flex items-center gap-2 bg-slate-100 dark:bg-slate-800 px-3 py-1.5 rounded-full hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
          >
            <img src={currentUser?.photoURL || 'https://api.dicebear.com/7.x/avataaars/svg?seed=placeholder'} alt={currentUser?.displayName || 'Guest'} className="w-6 h-6 rounded-full bg-white dark:bg-slate-700 object-cover" />
            <span className="text-sm font-medium hidden sm:inline dark:text-slate-200">{currentUser?.displayName || '訪客'}</span>
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-md mx-auto p-4 sm:p-6">
        {activeTab === 'dashboard' && <Dashboard />}
        {activeTab === 'add' && <AddTransaction autoStartVoice={voiceAutoStart} onComplete={() => { setActiveTab('dashboard'); setVoiceAutoStart(false); }} /> }
        {activeTab === 'list' && <TransactionList />}
        {activeTab === 'stats' && <Statistics />}
        {activeTab === 'settings' && <Settings />}
      </main>

      {/* Bottom Navigation */}
      <nav className="fixed bottom-0 left-0 right-0 bg-white dark:bg-slate-900 border-t border-slate-200 dark:border-slate-800 px-2 py-2 flex justify-between items-center z-50 safe-area-bottom max-w-md mx-auto transition-colors">
        <button 
          onClick={() => setActiveTab('dashboard')}
          className={`flex flex-col items-center gap-1 p-2 rounded-lg transition-colors flex-1 ${activeTab === 'dashboard' ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300'}`}
        >
          <HomeIcon className="w-5 h-5 sm:w-6 sm:h-6" />
          <span className="text-[10px] font-medium">首頁</span>
        </button>
        
        <button 
          onClick={() => setActiveTab('list')}
          className={`flex flex-col items-center gap-1 p-2 rounded-lg transition-colors flex-1 ${activeTab === 'list' ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300'}`}
        >
          <ListIcon className="w-5 h-5 sm:w-6 sm:h-6" />
          <span className="text-[10px] font-medium">紀錄</span>
        </button>

        <button 
          onClick={() => setActiveTab('add')}
          onMouseDown={() => startPress()}
          onMouseUp={() => endPress()}
          onMouseLeave={() => cancelPress()}
          onTouchStart={() => startPress()}
          onTouchEnd={() => endPress()}
          className={`flex flex-col items-center gap-1 p-2 -mt-8 mx-1`}
        >
          <div className={`w-12 h-12 sm:w-14 sm:h-14 rounded-full flex items-center justify-center shadow-lg transition-transform hover:scale-105 ${activeTab === 'add' ? 'bg-indigo-700 text-white shadow-indigo-500/50' : 'bg-indigo-600 text-white'}`}>
            <PlusCircleIcon className="w-6 h-6 sm:w-8 sm:h-8" />
          </div>
          <span className={`text-[10px] font-medium ${activeTab === 'add' ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500'}`}>記帳</span>
        </button>

        <button 
          onClick={() => setActiveTab('stats')}
          className={`flex flex-col items-center gap-1 p-2 rounded-lg transition-colors flex-1 ${activeTab === 'stats' ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300'}`}
        >
          <ChartPieIcon className="w-5 h-5 sm:w-6 sm:h-6" />
          <span className="text-[10px] font-medium">統計</span>
        </button>
        
        <button 
          onClick={() => setActiveTab('settings')}
          className={`flex flex-col items-center gap-1 p-2 rounded-lg transition-colors flex-1 ${activeTab === 'settings' ? 'text-indigo-600 dark:text-indigo-400' : 'text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300'}`}
        >
          <SettingsIcon className="w-5 h-5 sm:w-6 sm:h-6" />
          <span className="text-[10px] font-medium">設定</span>
        </button>
      </nav>
    </div>
  );
};

// Component to handle auth state logic
const Main = () => {
  const { user } = useAuth();

  // If user is not logged in, show WelcomeScreen
  if (!user) {
    return <WelcomeScreen />;
  }

  // If user is logged in, show the App wrapped in AppProvider (which needs AuthContext)
  return (
    <AppProvider>
      <Layout />
    </AppProvider>
  );
};

const App = () => {
  return (
    <AuthProvider>
      <Main />
    </AuthProvider>
  );
};

export default App;
