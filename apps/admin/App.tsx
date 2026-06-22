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
  const [usageStart, setUsageStart] = React.useState('');
  const [usageEnd, setUsageEnd] = React.useState('');
  const [usageLoading, setUsageLoading] = React.useState(false);
  const [usageMetrics, setUsageMetrics] = React.useState<{
    activeUsers: number;
    newUsers: number;
    sessions: number;
    eventCount: number;
  } | null>(null);
  const [adFreeQuery, setAdFreeQuery] = React.useState('');
  const [adFreeLoading, setAdFreeLoading] = React.useState(false);
  const [adFreeSaving, setAdFreeSaving] = React.useState(false);
  const [adFreeUser, setAdFreeUser] = React.useState<{
    uid: string;
    email: string | null;
    displayName: string | null;
    isAdFree: boolean;
    adFreeUpdatedAt: number | null;
    adFreeUpdatedBy: string | null;
  } | null>(null);

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

  const toDateInput = (date: Date) => date.toISOString().slice(0, 10);
  const formatDateTime = (ms: number | null) => {
    if (!ms) return '';
    return new Date(ms).toLocaleString();
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

  React.useEffect(() => {
    if (!backendAllowed) return;
    const today = new Date();
    const todayStr = toDateInput(today);
    setUsageStart(todayStr);
    setUsageEnd(todayStr);
  }, [backendAllowed]);

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

  const handleLoadUsage = async () => {
    if (!usageStart || !usageEnd) {
      setStatusMessage('請設定用量查詢日期');
      setTimeout(() => setStatusMessage(''), 2500);
      return;
    }

    setUsageLoading(true);
    try {
      const getUsage = httpsCallable(functions, 'adminGetUsageSummary');
      const res = await getUsage({ startDate: usageStart, endDate: usageEnd });
      const data = res.data as {
        metrics?: {
          activeUsers?: number;
          newUsers?: number;
          sessions?: number;
          eventCount?: number;
        };
      };
      if (data.metrics) {
        setUsageMetrics({
          activeUsers: Number(data.metrics.activeUsers ?? 0),
          newUsers: Number(data.metrics.newUsers ?? 0),
          sessions: Number(data.metrics.sessions ?? 0),
          eventCount: Number(data.metrics.eventCount ?? 0),
        });
      } else {
        setUsageMetrics(null);
      }
    } catch (error) {
      setStatusMessage('讀取用量失敗');
      setTimeout(() => setStatusMessage(''), 2500);
    } finally {
      setUsageLoading(false);
    }
  };

  const handleFindAdFreeUser = async () => {
    if (!adFreeQuery.trim()) {
      setStatusMessage('請輸入使用者 UID 或 Email');
      setTimeout(() => setStatusMessage(''), 2500);
      return;
    }

    setAdFreeLoading(true);
    try {
      const findUser = httpsCallable(functions, 'adminFindUser');
      const res = await findUser({ identifier: adFreeQuery.trim() });
      const data = res.data as {
        exists?: boolean;
        uid?: string;
        email?: string | null;
        displayName?: string | null;
        isAdFree?: boolean;
        adFreeUpdatedAt?: number | null;
        adFreeUpdatedBy?: string | null;
      };
      if (!data.exists) {
        setAdFreeUser(null);
        setStatusMessage('查無此使用者');
        setTimeout(() => setStatusMessage(''), 2500);
        return;
      }
      setAdFreeUser({
        uid: data.uid || '',
        email: data.email ?? null,
        displayName: data.displayName ?? null,
        isAdFree: Boolean(data.isAdFree),
        adFreeUpdatedAt: data.adFreeUpdatedAt ?? null,
        adFreeUpdatedBy: data.adFreeUpdatedBy ?? null,
      });
    } catch (error) {
      setStatusMessage('讀取使用者失敗');
      setTimeout(() => setStatusMessage(''), 2500);
    } finally {
      setAdFreeLoading(false);
    }
  };

  const handleSetAdFree = async (isAdFree: boolean) => {
    if (!adFreeUser) return;
    setAdFreeSaving(true);
    try {
      const setAdFree = httpsCallable(functions, 'adminSetAdFree');
      await setAdFree({ uid: adFreeUser.uid, isAdFree });
      setAdFreeUser({
        ...adFreeUser,
        isAdFree,
        adFreeUpdatedAt: Date.now(),
        adFreeUpdatedBy: user?.email ?? null,
      });
      setStatusMessage('已更新免廣告狀態');
    } catch (error) {
      setStatusMessage('更新失敗，請稍後再試');
    } finally {
      setAdFreeSaving(false);
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
            透過 Google Analytics 取得活躍使用者、事件等指標。
          </div>
          <div className="stack">
            <label className="muted">
              起始日期
              <input
                type="date"
                value={usageStart}
                onChange={(event) => setUsageStart(event.target.value)}
              />
            </label>
            <label className="muted">
              結束日期
              <input
                type="date"
                value={usageEnd}
                onChange={(event) => setUsageEnd(event.target.value)}
              />
            </label>
            <div className="actions">
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  const today = toDateInput(new Date());
                  setUsageStart(today);
                  setUsageEnd(today);
                }}
              >
                今日
              </button>
              <button onClick={handleLoadUsage} disabled={usageLoading}>
                {usageLoading ? '讀取中...' : '重新整理'}
              </button>
            </div>
          </div>
          {usageMetrics ? (
            <div className="stack">
              <div className="card">
                <div className="muted">活躍使用者</div>
                <div className="title">{usageMetrics.activeUsers}</div>
              </div>
              <div className="card">
                <div className="muted">新使用者</div>
                <div className="title">{usageMetrics.newUsers}</div>
              </div>
              <div className="card">
                <div className="muted">工作階段</div>
                <div className="title">{usageMetrics.sessions}</div>
              </div>
              <div className="card">
                <div className="muted">事件數</div>
                <div className="title">{usageMetrics.eventCount}</div>
              </div>
            </div>
          ) : null}
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
          <h2>免廣告帳號</h2>
          <div className="muted">
            查詢使用者並切換免廣告狀態。
          </div>
          <div className="stack">
            <label className="muted">
              使用者 UID / Email
              <input
                placeholder="輸入 UID 或 Email"
                value={adFreeQuery}
                onChange={(event) => setAdFreeQuery(event.target.value)}
              />
            </label>
            <div className="actions">
              <button onClick={handleFindAdFreeUser} disabled={adFreeLoading}>
                {adFreeLoading ? '查詢中...' : '查詢'}
              </button>
            </div>
          </div>
          {adFreeUser ? (
            <div className="stack">
              <div className="muted">UID：{adFreeUser.uid}</div>
              {adFreeUser.email ? <div className="muted">Email：{adFreeUser.email}</div> : null}
              {adFreeUser.displayName ? <div className="muted">名稱：{adFreeUser.displayName}</div> : null}
              <div className="muted">
                狀態：{adFreeUser.isAdFree ? '免廣告' : '一般'}
              </div>
              {adFreeUser.adFreeUpdatedAt ? (
                <div className="muted">
                  更新時間：{formatDateTime(adFreeUser.adFreeUpdatedAt)}
                </div>
              ) : null}
              {adFreeUser.adFreeUpdatedBy ? (
                <div className="muted">
                  更新人員：{adFreeUser.adFreeUpdatedBy}
                </div>
              ) : null}
              <div className="actions">
                <button
                  type="button"
                  className={adFreeUser.isAdFree ? '' : 'secondary'}
                  onClick={() => handleSetAdFree(true)}
                  disabled={adFreeSaving}
                >
                  設為免廣告
                </button>
                <button
                  type="button"
                  className={!adFreeUser.isAdFree ? '' : 'secondary'}
                  onClick={() => handleSetAdFree(false)}
                  disabled={adFreeSaving}
                >
                  取消免廣告
                </button>
              </div>
            </div>
          ) : null}
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
