import React from 'react';
import {
  onAuthStateChanged,
  signInWithPopup,
  signOut,
  type User,
} from 'firebase/auth';
import { httpsCallable } from 'firebase/functions';
import { auth, functions, googleProvider } from './firebase';

const App = () => {
  const [user, setUser] = React.useState<User | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [backendAllowed, setBackendAllowed] = React.useState<boolean | null>(null);
  const [backendError, setBackendError] = React.useState<string>('');
  const [marqueePlatform, setMarqueePlatform] = React.useState<'web' | 'android'>('web');
  const [marqueeText, setMarqueeText] = React.useState('');
  const [marqueeEnabled, setMarqueeEnabled] = React.useState(true);
  const [marqueeType, setMarqueeType] = React.useState<'info' | 'warning' | 'error'>('info');
  const [marqueeStart, setMarqueeStart] = React.useState('');
  const [marqueeEnd, setMarqueeEnd] = React.useState('');
  const [isSaving, setIsSaving] = React.useState(false);
  const [isLoadingMarquee, setIsLoadingMarquee] = React.useState(false);
  const [statusMessage, setStatusMessage] = React.useState('');

  React.useEffect(() => {
    const unsub = onAuthStateChanged(auth, (nextUser) => {
      setUser(nextUser);
      setLoading(false);
    });
    return () => unsub();
  }, []);

  React.useEffect(() => {
    if (!user) {
      setBackendAllowed(null);
      setBackendError('');
      return;
    }

    let cancelled = false;
    const verify = async () => {
      try {
        const checkAdmin = httpsCallable(functions, 'adminCheck');
        await checkAdmin();
        if (!cancelled) {
          setBackendAllowed(true);
          setBackendError('');
        }
      } catch (error: any) {
        if (!cancelled) {
          setBackendAllowed(false);
          setBackendError('後端驗證未通過，請確認白名單設定。');
        }
      }
    };

    verify();
    return () => {
      cancelled = true;
    };
  }, [user]);

  const handleLogin = async () => {
    await signInWithPopup(auth, googleProvider);
  };

  const handleLogout = async () => {
    await signOut(auth);
  };

  const toLocalInput = (ms: number | null) => {
    if (!ms) return '';
    const offset = new Date(ms).getTimezoneOffset() * 60000;
    return new Date(ms - offset).toISOString().slice(0, 16);
  };

  React.useEffect(() => {
    if (!backendAllowed) return;
    let cancelled = false;

    const load = async () => {
      setIsLoadingMarquee(true);
      try {
        const getAnnouncement = httpsCallable(functions, 'adminGetAnnouncement');
        const res = await getAnnouncement({ platform: marqueePlatform });
        const data = res.data as {
          exists?: boolean;
          text?: string;
          isEnabled?: boolean;
          type?: 'info' | 'warning' | 'error';
          startAt?: number | null;
          endAt?: number | null;
        };
        if (!cancelled) {
          if (data.exists) {
            setMarqueeText(data.text || '');
            setMarqueeEnabled(Boolean(data.isEnabled));
            setMarqueeType(data.type === 'warning' || data.type === 'error' ? data.type : 'info');
            setMarqueeStart(toLocalInput(data.startAt ?? null));
            setMarqueeEnd(toLocalInput(data.endAt ?? null));
          } else {
            setMarqueeText('');
            setMarqueeEnabled(true);
            setMarqueeType('info');
            setMarqueeStart('');
            setMarqueeEnd('');
          }
        }
      } catch (error) {
        if (!cancelled) {
          setStatusMessage('讀取跑馬燈設定失敗');
          setTimeout(() => setStatusMessage(''), 2500);
        }
      } finally {
        if (!cancelled) setIsLoadingMarquee(false);
      }
    };

    load();
    return () => {
      cancelled = true;
    };
  }, [backendAllowed, marqueePlatform]);

  const handleSaveMarquee = async () => {
    if (!marqueeText.trim()) {
      setStatusMessage('請輸入跑馬燈內容');
      setTimeout(() => setStatusMessage(''), 2500);
      return;
    }
    if (!marqueeStart || !marqueeEnd) {
      setStatusMessage('請設定開始與結束時間');
      setTimeout(() => setStatusMessage(''), 2500);
      return;
    }

    setIsSaving(true);
    try {
      const setAnnouncement = httpsCallable(functions, 'adminSetAnnouncement');
      await setAnnouncement({
        text: marqueeText,
        isEnabled: marqueeEnabled,
        type: marqueeType,
        startAt: marqueeStart,
        endAt: marqueeEnd,
        platform: marqueePlatform,
      });
      setStatusMessage('已儲存跑馬燈設定');
    } catch (error) {
      setStatusMessage('儲存失敗，請稍後再試');
    } finally {
      setIsSaving(false);
      setTimeout(() => setStatusMessage(''), 2500);
    }
  };

  if (loading) {
    return (
      <div className="page">
        <div className="card">載入後台中...</div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="page">
        <div className="card stack">
          <div className="title">CloudLedger 後台</div>
          <div className="muted">
            請使用授權的 Google 帳號登入。
          </div>
          <div className="actions">
            <button onClick={handleLogin}>使用 Google 登入</button>
          </div>
        </div>
      </div>
    );
  }

  if (backendAllowed === false) {
    return (
      <div className="page">
        <div className="card stack">
          <div className="title">無權限</div>
          <div className="muted">
            {backendError || '後端驗證未通過，請稍後再試。'}
          </div>
          <div className="actions">
            <button onClick={handleLogout} className="secondary">
              登出
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (backendAllowed === null) {
    return (
      <div className="page">
        <div className="card">驗證後台權限中...</div>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="header">
        <div>
          <div className="title">CloudLedger 後台</div>
          <div className="muted">{user.email}</div>
        </div>
        <button onClick={handleLogout} className="secondary">
          登出
        </button>
      </div>

      <div className="stack">
        <section className="card stack">
          <h2>用量總覽</h2>
          <div className="muted">
            可串接用量指標（活躍使用者、儲存量、API 呼叫次數）。
          </div>
          <div className="actions">
            <button className="secondary">重新整理（待實作）</button>
            <button className="secondary">匯出（待實作）</button>
          </div>
        </section>

        <section className="card stack">
          <h2>資料調閱</h2>
          <div className="muted">
            輸入帳本 ID 或使用者 Email 以查詢資料。
          </div>
          <input placeholder="帳本 ID 或使用者 Email" />
          <div className="actions">
            <button className="secondary">查詢（待實作）</button>
            <button className="secondary">開啟紀錄（待實作）</button>
          </div>
        </section>

        <section className="card stack">
          <h2>跑馬燈設定</h2>
          <div className="muted">
            更新顯示給使用者的公告。
          </div>
          <div className="stack">
            <label className="muted">
              平台
              <div className="actions">
                <button
                  className={marqueePlatform === 'web' ? '' : 'secondary'}
                  onClick={() => setMarqueePlatform('web')}
                  type="button"
                >
                  Web
                </button>
                <button
                  className={marqueePlatform === 'android' ? '' : 'secondary'}
                  onClick={() => setMarqueePlatform('android')}
                  type="button"
                >
                  Android
                </button>
              </div>
            </label>

            <label className="muted">
              啟用狀態
              <div className="actions">
                <button
                  className={marqueeEnabled ? '' : 'secondary'}
                  onClick={() => setMarqueeEnabled(true)}
                  type="button"
                >
                  啟用
                </button>
                <button
                  className={!marqueeEnabled ? '' : 'secondary'}
                  onClick={() => setMarqueeEnabled(false)}
                  type="button"
                >
                  停用
                </button>
              </div>
            </label>

            <label className="muted">
              類型
              <select
                value={marqueeType}
                onChange={(event) => setMarqueeType(event.target.value as 'info' | 'warning' | 'error')}
              >
                <option value="info">資訊</option>
                <option value="warning">警告</option>
                <option value="error">錯誤</option>
              </select>
            </label>

            <label className="muted">
              開始時間
              <input
                type="datetime-local"
                value={marqueeStart}
                onChange={(event) => setMarqueeStart(event.target.value)}
              />
            </label>

            <label className="muted">
              結束時間
              <input
                type="datetime-local"
                value={marqueeEnd}
                onChange={(event) => setMarqueeEnd(event.target.value)}
              />
            </label>

            <label className="muted">
              公告內容
              <textarea
                rows={4}
                value={marqueeText}
                onChange={(event) => setMarqueeText(event.target.value)}
                placeholder="輸入跑馬燈內容..."
              />
            </label>
          </div>

          <div className="actions">
            <button onClick={handleSaveMarquee} disabled={isSaving || isLoadingMarquee}>
              {isSaving ? '儲存中...' : '儲存跑馬燈'}
            </button>
            <button className="secondary" disabled>
              預覽（待實作）
            </button>
          </div>
          {statusMessage ? <div className="muted">{statusMessage}</div> : null}
        </section>
      </div>
    </div>
  );
};

export default App;
