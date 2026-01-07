import React, { useEffect, useState } from 'react';
import { useAppContext } from '../contexts/AppContext';

type OnboardingScreenProps = {
  onDone?: () => void;
};

const OnboardingScreen: React.FC<OnboardingScreenProps> = ({ onDone }) => {
  const { createLedger, joinLedger, savedLedgers, switchLedger, refreshUserProfile } = useAppContext();
  const [ledgerName, setLedgerName] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showJoin, setShowJoin] = useState(false);

  useEffect(() => {
    refreshUserProfile();
  }, [refreshUserProfile]);

  const handleCreate = async () => {
    if (busy) return;
    setError(null);
    setBusy(true);
    try {
      const name = ledgerName.trim() || '未命名帳本';
      await createLedger(name);
      if (onDone) onDone();
    } catch (e) {
      console.error('Create ledger failed:', e);
      setError('建立帳本失敗，請稍後再試。');
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = async () => {
    if (busy) return;
    const code = inviteCode.trim();
    if (!code) {
      setError('請輸入邀請碼。');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const ok = await joinLedger(code);
      if (!ok) {
        setError('加入帳本失敗，請確認邀請碼是否正確。');
        return;
      }
      if (onDone) onDone();
    } catch (e) {
      console.error('Join ledger failed:', e);
      setError('加入帳本失敗，請稍後再試。');
    } finally {
      setBusy(false);
    }
  };

  const handleSelectLedger = async (id: string) => {
    if (busy) return;
    setError(null);
    setBusy(true);
    try {
      await switchLedger(id);
      if (onDone) onDone();
    } catch (e) {
      console.error('Switch ledger failed:', e);
      setError('切換帳本失敗，請稍後再試。');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-gradient-to-br from-amber-100 via-rose-100 to-sky-100 text-slate-900">
      <div className="w-full max-w-md bg-[color:var(--app-surface)]/90 backdrop-blur rounded-2xl shadow-xl p-6 sm:p-8">
        <div className="flex items-center gap-3 mb-4">
          <div>
            <h1 className="text-xl font-bold">開始使用 CloudLedger</h1>
            <p className="text-sm text-slate-500">建立新帳本或加入現有帳本</p>
          </div>
        </div>

        <div className="space-y-4">
          {savedLedgers.length > 0 && (
            <div className="rounded-xl border border-[color:var(--app-border)] p-4">
              <h2 className="text-sm font-semibold text-slate-700 mb-2">選擇現有帳本</h2>
              <div className="space-y-2">
                {savedLedgers.map((ledger) => (
                  <button
                    key={ledger.id}
                    onClick={() => handleSelectLedger(ledger.id)}
                    disabled={busy}
                    className="w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg border border-[color:var(--app-border)] bg-[color:var(--app-surface)] hover:bg-[color:var(--app-bg)] transition text-left disabled:opacity-60 disabled:cursor-not-allowed"
                  >
                    <div>
                      <div className="text-sm font-semibold text-slate-800">{ledger.alias}</div>
                      <div className="text-[11px] text-slate-400 font-mono truncate max-w-[220px]">ID: {ledger.id}</div>
                    </div>
                    <span className="text-xs text-slate-500">切換</span>
                  </button>
                ))}
              </div>
              <button
                type="button"
                onClick={() => setShowJoin((v) => !v)}
                className="mt-3 text-xs text-slate-500 hover:text-slate-700"
              >
                {showJoin ? '隱藏加入帳本' : '顯示加入帳本'}
              </button>
            </div>
          )}

          {(showJoin || savedLedgers.length === 0) && (
            <div className="rounded-xl border border-[color:var(--app-border)] p-4">
              <h2 className="text-sm font-semibold text-slate-700 mb-2">加入帳本</h2>
              <input
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value)}
                placeholder="輸入邀請碼"
                className="w-full border border-[color:var(--app-border)] rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
              />
              <button
                onClick={handleJoin}
                disabled={busy}
                className="mt-3 w-full bg-slate-800 text-white py-2.5 rounded-lg font-semibold text-sm hover:bg-slate-900 transition disabled:opacity-60 disabled:cursor-not-allowed"
              >
                加入帳本
              </button>
            </div>
          )}

          <div className="rounded-xl border border-[color:var(--app-border)] p-4">
            <h2 className="text-sm font-semibold text-slate-700 mb-2">建立新帳本</h2>
            <input
              value={ledgerName}
              onChange={(e) => setLedgerName(e.target.value)}
              placeholder="帳本名稱（可留空）"
              className="w-full border border-[color:var(--app-border)] rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
            />
            <button
              onClick={handleCreate}
              disabled={busy}
              className="mt-3 w-full bg-amber-500 text-white py-2.5 rounded-lg font-semibold text-sm hover:bg-amber-600 transition disabled:opacity-60 disabled:cursor-not-allowed"
            >
              建立帳本
            </button>
          </div>

          {error && (
            <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 px-3 py-2 rounded-lg">
              {error}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default OnboardingScreen;


