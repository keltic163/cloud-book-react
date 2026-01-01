import React from 'react';
import { useAuth } from '../contexts/AuthContext';

const WelcomeScreen: React.FC = () => {
  const { signInWithGoogle, enterMockMode, loading } = useAuth();

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-4 bg-gradient-to-br from-indigo-500 to-purple-600 text-white">
      <div className="bg-white p-8 rounded-2xl shadow-2xl text-center max-w-sm w-full animate-in fade-in zoom-in-95 duration-500">
        <div className="w-20 h-20 mx-auto mb-6 bg-indigo-600 rounded-full flex items-center justify-center text-white font-bold text-3xl">
          記
        </div>
        <h1 className="text-3xl font-bold text-slate-800 mb-2">CloudLedger 雲記</h1>
        <p className="text-slate-600 mb-8 text-sm">
          您的智慧共享記帳本，輕鬆管理財務。
        </p>
        
        <div className="space-y-3">
          <button
            onClick={signInWithGoogle}
            disabled={loading}
            className="w-full bg-blue-600 text-white py-3 px-4 rounded-xl font-semibold text-lg shadow-lg hover:bg-blue-700 transition-all disabled:opacity-60 disabled:cursor-not-allowed flex items-center justify-center gap-3"
          >
            {loading ? (
              <span className="flex items-center gap-2">
                <span className="w-2 h-2 bg-white rounded-full animate-bounce"></span>
                <span className="w-2 h-2 bg-white rounded-full animate-bounce delay-75"></span>
                <span className="w-2 h-2 bg-white rounded-full animate-bounce delay-150"></span>
                登入中...
              </span>
            ) : (
              <>
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M12 2a10 10 0 0 0-9.25 15.65C3 18 3 22 12 22s9-4 9-4A10 10 0 0 0 12 2Z"/><path d="M12 2a10 10 0 0 0 0 20v-8s3.458-.5 5-2c.866-.992 1.5-2.126 1.5-3A6.5 6.5 0 0 0 12 2Z"/></svg>
                使用 Google 帳號登入
              </>
            )}
          </button>

          <button
            onClick={enterMockMode}
            disabled={loading}
            className="w-full bg-slate-100 text-slate-600 py-3 px-4 rounded-xl font-medium text-sm hover:bg-slate-200 transition-all"
          >
            試用演示模式 (無需登入)
          </button>
        </div>
        
        <p className="mt-6 text-xs text-slate-400">
          演示模式資料僅儲存於本機，清除瀏覽器快取後消失。
        </p>
      </div>
    </div>
  );
};

export default WelcomeScreen;