import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { Transaction, User, SavedLedger } from '../types';
import { DEFAULT_EXPENSE_CATEGORIES, DEFAULT_INCOME_CATEGORIES } from '../constants';
import { useAuth } from './AuthContext';
import { db, functions, isMockMode } from '../firebase';
import {
  collection,
  query,
  orderBy,
  getDocs,
  where,
  limit,
  addDoc,
  doc,
  updateDoc,
  setDoc,
  getDoc,
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';

interface AppContextType {
  transactions: Transaction[];
  addTransaction: (t: Omit<Transaction, 'id' | 'createdAt'>) => Promise<void>;
  deleteTransaction: (id: string) => void;
  updateTransaction: (id: string, updates: Partial<Omit<Transaction, 'id' | 'createdAt'>>) => void;
  loadData: (data: { transactions: Transaction[], users: User[] }) => void;
  currentUser: User;
  users: User[];
  ledgerId: string | null;
  isInitializing: boolean;
  refreshUserProfile: () => Promise<void>;
  joinLedger: (id: string) => Promise<boolean>;
  createLedger: (name: string) => Promise<void>;
  createRecurringTemplate: (data: {
    title: string;
    amount: number;
    type: 'expense' | 'income';
    category: string;
    note?: string;
    intervalMonths: number;
    executeDay: number;
    nextRunAt: Date;
    totalRuns?: number;
    remainingRuns?: number;
  }) => Promise<void>;
  switchLedger: (id: string) => Promise<void>;
  leaveLedger: (id: string) => Promise<void>;
  updateLedgerAlias: (id: string, alias: string) => Promise<void>;
  savedLedgers: SavedLedger[];
  switchUser: (userId: string) => void;
  addUser: (name: string) => void;
  removeUser: (userId: string) => void;
  selectedDate: Date | null;
  setSelectedDate: (date: Date | null) => void;
  isDarkMode: boolean;
  toggleTheme: () => void;
  expenseCategories: string[];
  incomeCategories: string[];
  addCategory: (type: 'expense' | 'income', category: string) => Promise<void>;
  deleteCategory: (type: 'expense' | 'income', category: string) => Promise<void>;
  resetCategories: () => Promise<void>;
  syncTransactions: (forceFull?: boolean) => Promise<void>;
  lastSyncedAt?: number;
  isSyncing?: boolean;
}

export const AppContext = createContext<AppContextType | undefined>(undefined);

const STORAGE_KEY_LEDGER_ID = 'cloudledger_ledger_id';
const MOCK_STORAGE_KEY_TXS = 'cloudledger_mock_txs';
const MOCK_STORAGE_KEY_USER_PROFILE = 'cloudledger_mock_profile';
const STORAGE_KEY_THEME = 'cloudledger_theme';

export const AppProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { user: authUser } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [ledgerId, setLedgerId] = useState<string | null>(null);
  const [savedLedgers, setSavedLedgers] = useState<SavedLedger[]>([]);
  const [isInitializing, setIsInitializing] = useState(true);
  const [selectedDate, setSelectedDate] = useState<Date | null>(null);
  const [isDarkMode, setIsDarkMode] = useState(false);

  const [expenseCategories, setExpenseCategories] = useState<string[]>(DEFAULT_EXPENSE_CATEGORIES);
  const [incomeCategories, setIncomeCategories] = useState<string[]>(DEFAULT_INCOME_CATEGORIES);

  // Sync state
  const [lastSyncedAt, setLastSyncedAt] = useState<number | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);

  // Local fallback state
  const [localUsers] = useState<User[]>([{
    uid: 'local_user',
    displayName: '訪客',
    email: null,
    photoURL: null,
    color: 'bg-blue-500'
  }]);

  const setThemeMeta = (dark: boolean) => {
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    if (themeMeta) {
      themeMeta.setAttribute('content', dark ? '#020617' : '#f5f2eb');
    }
    const appleStatus = document.querySelector('meta[name="apple-mobile-web-app-status-bar-style"]');
    if (appleStatus) {
      appleStatus.setAttribute('content', dark ? 'black-translucent' : 'default');
    }
  };

  // --- Theme Initialization ---
  useEffect(() => {
    const savedTheme = localStorage.getItem(STORAGE_KEY_THEME);
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const useDark = savedTheme === 'dark' || (!savedTheme && prefersDark);

    setIsDarkMode(useDark);
    if (useDark) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
    setThemeMeta(useDark);
  }, []);

  const toggleTheme = () => {
    const newMode = !isDarkMode;
    setIsDarkMode(newMode);
    if (newMode) {
      document.documentElement.classList.add('dark');
      localStorage.setItem(STORAGE_KEY_THEME, 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem(STORAGE_KEY_THEME, 'light');
    }
    setThemeMeta(newMode);
  };

  // --- Helper to update User Profile (Firestore or Local) ---
  const syncUserProfile = async (uid: string, data: { lastLedgerId?: string | null; savedLedgers?: SavedLedger[] }) => {
    if (isMockMode) {
      const currentStr = localStorage.getItem(MOCK_STORAGE_KEY_USER_PROFILE);
      const current = currentStr ? JSON.parse(currentStr) : { savedLedgers: [] };
      const updated = { ...current, ...data };
      localStorage.setItem(MOCK_STORAGE_KEY_USER_PROFILE, JSON.stringify(updated));
      return;
    }

    if (!db) return;
    const userRef = doc(db, 'users', uid);
    try {
      await setDoc(userRef, data, { merge: true });
    } catch (e) {
      console.error('Error syncing user profile:', e);
    }
  };

  const ensureUserProfileFields = async (uid: string, userData?: Record<string, any>) => {
    if (!authUser || !db || isMockMode) return;
    const updates: Record<string, any> = {};

    if (!userData?.displayName && authUser.displayName) updates.displayName = authUser.displayName;
    if (!userData?.email && authUser.email) updates.email = authUser.email;
    if (!userData?.photoURL && authUser.photoURL) updates.photoURL = authUser.photoURL;
    if (!userData?.createdAt) updates.createdAt = Date.now();

    if (Object.keys(updates).length === 0) return;
    updates.updatedAt = Date.now();

    try {
      await setDoc(doc(db, 'users', uid), updates, { merge: true });
    } catch (e) {
      console.error('Error backfilling user profile:', e);
    }
  };

  const refreshUserProfile = useCallback(async () => {
    if (!authUser || !db || isMockMode) return;
    try {
      const userRef = doc(db, 'users', authUser.uid);
      const userSnap = await getDoc(userRef);
      if (userSnap.exists()) {
        const userData = userSnap.data() || {};
        const currentSavedLedgers: SavedLedger[] = (userData as any).savedLedgers || [];
        setSavedLedgers(currentSavedLedgers);
        void ensureUserProfileFields(authUser.uid, userData as Record<string, any>);
      }
    } catch (e) {
      console.error('Error refreshing user profile:', e);
    }
  }, [authUser, db, isMockMode]);

  // --- 1. Initialization Logic (User Login -> Load Profile -> Load Ledger) ---
  useEffect(() => {
    if (!authUser) return;

    const initializeUser = async () => {
      setIsInitializing(true);
      // 1. Mock Mode Handling
      if (isMockMode) {
        try {
          setUsers([
            { uid: authUser.uid, displayName: authUser.displayName, email: authUser.email, photoURL: authUser.photoURL, color: 'bg-indigo-500' },
            { uid: 'mock-partner', displayName: '另一半 (範例)', email: 'partner@demo.com', photoURL: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Annie', color: 'bg-pink-500' }
          ]);

          const profileStr = localStorage.getItem(MOCK_STORAGE_KEY_USER_PROFILE);
          let profile: any = null;
          if (profileStr) {
            try {
              profile = JSON.parse(profileStr);
            } catch (e) {
              console.warn('Mock profile invalid, resetting.', e);
            }
          }

          let targetLedgerId = profile?.lastLedgerId || localStorage.getItem(STORAGE_KEY_LEDGER_ID);

          if (!targetLedgerId) {
            targetLedgerId = 'mock-ledger-demo';
            profile = {
              lastLedgerId: targetLedgerId,
              savedLedgers: [{ id: targetLedgerId, alias: '示範帳本', lastAccessedAt: Date.now() }]
            };
            localStorage.setItem(MOCK_STORAGE_KEY_USER_PROFILE, JSON.stringify(profile));
          }

          const mockSavedLedgers = profile?.savedLedgers || [];
          setLedgerId(targetLedgerId);
          setSavedLedgers(mockSavedLedgers);
          localStorage.setItem(STORAGE_KEY_LEDGER_ID, targetLedgerId);
        } catch (e) {
          console.error('Mock mode init failed:', e);
        } finally {
          setIsInitializing(false);
        }
        return;
      }

      if (!db) {
        setIsInitializing(false);
        return;
      }

      // 2. Real Firestore Handling
      const userRef = doc(db, 'users', authUser.uid);

      try {
        const userSnap = await getDoc(userRef);
        let targetId = '';
        let currentSavedLedgers: SavedLedger[] = [];

        if (userSnap.exists()) {
          const userData = userSnap.data() || {};
          targetId = (userData as any).lastLedgerId;
          currentSavedLedgers = (userData as any).savedLedgers || [];
          setSavedLedgers(currentSavedLedgers);
          void ensureUserProfileFields(authUser.uid, userData as Record<string, any>);
        } else {
          setSavedLedgers([]);
          await syncUserProfile(authUser.uid, { savedLedgers: [] });
          void ensureUserProfileFields(authUser.uid, {});
        }

        if (!targetId && currentSavedLedgers.length > 0) {
          const mostRecent = [...currentSavedLedgers].sort((a, b) => b.lastAccessedAt - a.lastAccessedAt)[0];
          targetId = mostRecent?.id || '';
        }

        if (!targetId) {
          targetId = localStorage.getItem(STORAGE_KEY_LEDGER_ID) || '';
        }

        if (targetId) {
          try {
            const ledgerRef = doc(db, 'ledgers', targetId);
            const ledgerSnap = await getDoc(ledgerRef);
            if (ledgerSnap.exists()) {
              setLedgerId(targetId);
              if (!currentSavedLedgers.find(l => l.id === targetId)) {
                const newList = [...currentSavedLedgers, { id: targetId, alias: ledgerSnap.data().name || '未命名帳本', lastAccessedAt: Date.now() }];
                setSavedLedgers(newList);
                await syncUserProfile(authUser.uid, { lastLedgerId: targetId, savedLedgers: newList });
              } else {
                await syncUserProfile(authUser.uid, { lastLedgerId: targetId });
              }
            } else {
              const missingId = targetId;
              targetId = '';
              const filtered = currentSavedLedgers.filter(l => l.id !== missingId);
              if (filtered.length !== currentSavedLedgers.length) {
                currentSavedLedgers = filtered;
                setSavedLedgers(filtered);
                await syncUserProfile(authUser.uid, { savedLedgers: filtered });
              }
            }
          } catch (e) {
            console.error('Error verifying ledger:', e);
            targetId = '';
          }
        }

        if (!targetId) {
          setLedgerId(null);
          localStorage.removeItem(STORAGE_KEY_LEDGER_ID);
          await syncUserProfile(authUser.uid, { lastLedgerId: null });
        }

      } catch (e) {
        console.error('Error initializing user:', e);
      } finally {
        setIsInitializing(false);
      }
    };

    initializeUser();
  }, [authUser]);

  // Internal helper to create ledger and update state
  const createNewLedgerInternal = async (user: User, name: string, currentList: SavedLedger[]) => {
    if (!db) return;
    try {
      const newLedgerRef = doc(collection(db, 'ledgers'));
      const newLedgerData = {
        name: name,
        createdAt: Date.now(),
        ownerUid: user.uid,
        members: [{
          uid: user.uid,
          displayName: user.displayName,
          photoURL: user.photoURL,
          email: user.email
        }],
        expenseCategories: DEFAULT_EXPENSE_CATEGORIES,
        incomeCategories: DEFAULT_INCOME_CATEGORIES
      };
      await setDoc(newLedgerRef, newLedgerData);

      const newEntry: SavedLedger = { id: newLedgerRef.id, alias: name, lastAccessedAt: Date.now() };
      const newList = [...currentList, newEntry];

      setLedgerId(newLedgerRef.id);
      setSavedLedgers(newList);

      await syncUserProfile(user.uid, { lastLedgerId: newLedgerRef.id, savedLedgers: newList });
      localStorage.setItem(STORAGE_KEY_LEDGER_ID, newLedgerRef.id);
    } catch (e: any) {
      console.error('Create ledger failed:', e);
      alert('建立帳本失敗: ' + e.message);
    }
  };

  // --- 2. Sync Ledger Data (Transactions & Members & Categories) ---

  // helpers for incremental sync
  const LAST_SYNC_KEY_PREFIX = 'cloudledger_last_synced_at_';

  const processDocs = (docs: any[]) => {
    setTransactions((prev) => {
      const map = new Map(prev.map(t => [t.id, t]));
      docs.forEach(d => {
        const id = d.id;
        const data = d.data();
        if (data.deleted) {
          map.delete(id);
        } else {
          map.set(id, { id, ...(data as any) } as Transaction);
        }
      });
      // return sorted by date desc
      const merged = Array.from(map.values()).sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
      localStorage.setItem(MOCK_STORAGE_KEY_TXS, JSON.stringify(merged));
      return merged;
    });
  };

  const syncTransactions = async (forceFull = false) => {
    if (!ledgerId || !db) return;
    setIsSyncing(true);
    try {
      const key = `${LAST_SYNC_KEY_PREFIX}${ledgerId}`;
      const last = Number(localStorage.getItem(key)) || 0;

      // initial fetch
      if (forceFull || last === 0) {
        const q = query(collection(db, `ledgers/${ledgerId}/transactions`), orderBy('date', 'desc'), limit(200));
        const snap = await getDocs(q);
        processDocs(snap.docs);

        const max = snap.docs.reduce((acc, d) => {
          const u = (d.data() as any).updatedAt || 0;
          return Math.max(acc, u);
        }, Date.now());
        localStorage.setItem(key, String(max));
        setLastSyncedAt(max);
        return;
      }

      // incremental fetch
      const incQuery = query(
        collection(db, `ledgers/${ledgerId}/transactions`),
        where('updatedAt', '>', last),
        orderBy('updatedAt', 'asc')
      );
      const incSnap = await getDocs(incQuery);
      if (!incSnap.empty) {
        processDocs(incSnap.docs);
        const max = incSnap.docs.reduce((acc, d) => {
          const u = (d.data() as any).updatedAt || 0;
          return Math.max(acc, u);
        }, last);
        localStorage.setItem(key, String(max));
        setLastSyncedAt(max);
      } else {
        const now = Date.now();
        localStorage.setItem(key, String(now));
        setLastSyncedAt(now);
      }
    } catch (e) {
      console.error('Sync transactions failed:', e);
    } finally {
      setIsSyncing(false);
    }
  };

  // UseEffect: fetch ledger metadata once and trigger initial sync
  useEffect(() => {
    if (!authUser || !ledgerId) return;

    if (isMockMode) {
      const stored = localStorage.getItem(MOCK_STORAGE_KEY_TXS);
      if (stored) {
        setTransactions(JSON.parse(stored));
      } else {
        setTransactions([]);
      }
      return;
    }

    if (!db) return;

    const fetchMetadata = async () => {
      const ledgerRef = doc(db, 'ledgers', ledgerId);
      try {
        const ledgerSnap = await getDoc(ledgerRef);
        if (ledgerSnap.exists()) {
          const data = ledgerSnap.data();
          if (data.members) setUsers(data.members);

          if (data.expenseCategories) {
            setExpenseCategories(data.expenseCategories);
          } else if (data.categories) {
            setExpenseCategories(data.categories);
            updateDoc(ledgerRef, {
              expenseCategories: data.categories,
              incomeCategories: data.incomeCategories || DEFAULT_INCOME_CATEGORIES
            }).catch(() => {});
          } else {
            setExpenseCategories(DEFAULT_EXPENSE_CATEGORIES);
          }

          if (data.incomeCategories) {
            setIncomeCategories(data.incomeCategories);
          } else {
            setIncomeCategories(DEFAULT_INCOME_CATEGORIES);
          }
        }
      } catch (e) {
        console.error('Error fetching ledger metadata:', e);
      }

      // initial sync
      await syncTransactions(true);
    };

    fetchMetadata();

    return () => {};
  }, [authUser, ledgerId]);

  // --- Actions ---

  const addCategory = async (type: 'expense' | 'income', category: string) => {
    if (!ledgerId || !db || isMockMode) return;

    if (type === 'expense') {
      if (expenseCategories.includes(category)) return;
      const newCategories = [...expenseCategories, category];
      setExpenseCategories(newCategories);
      await updateDoc(doc(db, 'ledgers', ledgerId), { expenseCategories: newCategories });
    } else {
      if (incomeCategories.includes(category)) return;
      const newCategories = [...incomeCategories, category];
      setIncomeCategories(newCategories);
      await updateDoc(doc(db, 'ledgers', ledgerId), { incomeCategories: newCategories });
    }
  };

  const deleteCategory = async (type: 'expense' | 'income', category: string) => {
    if (!ledgerId || !db || isMockMode) return;

    if (type === 'expense') {
      const newCategories = expenseCategories.filter(c => c !== category);
      setExpenseCategories(newCategories);
      await updateDoc(doc(db, 'ledgers', ledgerId), { expenseCategories: newCategories });
    } else {
      const newCategories = incomeCategories.filter(c => c !== category);
      setIncomeCategories(newCategories);
      await updateDoc(doc(db, 'ledgers', ledgerId), { incomeCategories: newCategories });
    }
  };

  const resetCategories = async () => {
    if (!ledgerId || !db || isMockMode) return;

    setExpenseCategories(DEFAULT_EXPENSE_CATEGORIES);
    setIncomeCategories(DEFAULT_INCOME_CATEGORIES);
    await updateDoc(doc(db, 'ledgers', ledgerId), {
      expenseCategories: DEFAULT_EXPENSE_CATEGORIES,
      incomeCategories: DEFAULT_INCOME_CATEGORIES
    });
  };

  const createLedger = async (name: string) => {
    if (!authUser) return;
    if (isMockMode) {
      try {
        setUsers([
          { uid: authUser.uid, displayName: authUser.displayName, email: authUser.email, photoURL: authUser.photoURL, color: 'bg-indigo-500' },
          { uid: 'mock-partner', displayName: '另一半 (範例)', email: 'partner@demo.com', photoURL: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Annie', color: 'bg-pink-500' }
        ]);

        const profileStr = localStorage.getItem(MOCK_STORAGE_KEY_USER_PROFILE);
        let profile: any = null;
        if (profileStr) {
          try {
            profile = JSON.parse(profileStr);
          } catch (e) {
            console.warn('Mock profile invalid, resetting.', e);
          }
        }

        const targetLedgerId = 'mock-ledger-demo';
        const nextProfile = {
          lastLedgerId: targetLedgerId,
          savedLedgers: [{ id: targetLedgerId, alias: name || '示範帳本', lastAccessedAt: Date.now() }]
        };
        localStorage.setItem(MOCK_STORAGE_KEY_USER_PROFILE, JSON.stringify(nextProfile));

        setLedgerId(targetLedgerId);
        setSavedLedgers(nextProfile.savedLedgers);
        localStorage.setItem(STORAGE_KEY_LEDGER_ID, targetLedgerId);
        setIsInitializing(false);
        return;
      } catch (e: any) {
        console.error('Create ledger failed:', e);
        alert('建立帳本失敗: ' + e.message);
        return;
      }
    }
    await createNewLedgerInternal(authUser, name, savedLedgers);
  };

  const createRecurringTemplate = async (data: {
    title: string;
    amount: number;
    type: 'expense' | 'income';
    category: string;
    note?: string;
    intervalMonths: number;
    executeDay: number;
    nextRunAt: Date;
    totalRuns?: number;
    remainingRuns?: number;
  }) => {
    if (!authUser || !db || !ledgerId || isMockMode) return;
    try {
      const payload: any = {
        userId: authUser.uid,
        ledgerId,
        title: data.title,
        amount: data.amount,
        type: data.type,
        category: data.category,
        note: data.note || null,
        frequency: 'monthly',
        intervalMonths: data.intervalMonths,
        executeDay: data.executeDay,
        nextRunAt: data.nextRunAt,
        isActive: true,
        createdAt: Date.now(),
        updatedAt: Date.now()
      };
      if (typeof data.totalRuns === 'number') payload.totalRuns = data.totalRuns;
      if (typeof data.remainingRuns === 'number') payload.remainingRuns = data.remainingRuns;
      await addDoc(collection(db, 'recurring_templates'), payload);
    } catch (e) {
      console.error('Create recurring template failed:', e);
      throw e;
    }
  };

  const switchLedger = async (id: string) => {
    if (!authUser) return;
    setLedgerId(id);
    localStorage.setItem(STORAGE_KEY_LEDGER_ID, id);
    if (!isMockMode && db) {
      await syncUserProfile(authUser.uid, { lastLedgerId: id });
      const updatedList = savedLedgers.map(l => l.id === id ? { ...l, lastAccessedAt: Date.now() } : l);
      setSavedLedgers(updatedList);
      await syncUserProfile(authUser.uid, { savedLedgers: updatedList });
    }
  };

  const leaveLedger = async (id: string) => {
    if (!authUser) return;
    if (isMockMode) {
      const newSaved = savedLedgers.filter(l => l.id !== id);
      setSavedLedgers(newSaved);
      if (id === ledgerId) {
        if (newSaved.length > 0) switchLedger(newSaved[0].id);
        else alert('演示模式下最後一本帳本已移除。');
      }
      return;
    }

    try {
      if (!functions) return;
      const callLeave = httpsCallable(functions, 'leaveLedger');
      await callLeave({ ledgerId: id });

      const newSavedList = savedLedgers.filter(l => l.id !== id);
      setSavedLedgers(newSavedList);
      await syncUserProfile(authUser.uid, { savedLedgers: newSavedList });

      if (ledgerId === id) {
        if (newSavedList.length > 0) {
          const nextId = newSavedList[0].id;
          setLedgerId(nextId);
          localStorage.setItem(STORAGE_KEY_LEDGER_ID, nextId);
          await syncUserProfile(authUser.uid, { lastLedgerId: nextId });
        } else {
          setLedgerId(null);
          localStorage.removeItem(STORAGE_KEY_LEDGER_ID);
          await syncUserProfile(authUser.uid, { lastLedgerId: null, savedLedgers: [] });
        }
      }
    } catch (e: any) {
      console.error('Leave ledger failed:', e);
      alert('退出帳本失敗: ' + e.message);
    }
  };

  const updateLedgerAlias = async (id: string, alias: string) => {
    if (!authUser) return;
    const updatedList = savedLedgers.map(l => l.id === id ? { ...l, alias } : l);
    setSavedLedgers(updatedList);
    await syncUserProfile(authUser.uid, { savedLedgers: updatedList });
  };

  const joinLedger = async (id: string): Promise<boolean> => {
    if (isMockMode) {
      alert('演示模式下無法加入真實帳本。');
      setLedgerId(id);
      return true;
    }

    if (!authUser || !functions) return false;
    try {
      const callJoin = httpsCallable(functions, 'joinLedger');
      const result = await callJoin({ ledgerId: id });
      const data = result.data as { ok?: boolean; ledgerName?: string | null };

      if (!data?.ok) return false;

      const newEntry: SavedLedger = {
        id: id,
        alias: data.ledgerName || '已加入帳本',
        lastAccessedAt: Date.now()
      };

      const newList = savedLedgers.filter(l => l.id !== id).concat(newEntry);

      setSavedLedgers(newList);
      setLedgerId(id);
      localStorage.setItem(STORAGE_KEY_LEDGER_ID, id);

      await syncUserProfile(authUser.uid, {
        lastLedgerId: id,
        savedLedgers: newList
      });

      return true;
    } catch (e: any) {
      console.error(e);
      alert(`加入帳本失敗：${e.message}`);
      return false;
    }
  };

  // Transaction Actions
  const addTransaction = async (t: Omit<Transaction, 'id' | 'createdAt'>) => {
    if (!authUser || !ledgerId) return;
    const now = Date.now();
    if (isMockMode) {
      const newTx: Transaction = { ...t, id: 'mock-' + now, createdAt: now, updatedAt: now, ledgerId, creatorUid: authUser.uid };
      setTransactions(prev => [newTx, ...prev]);
      localStorage.setItem(MOCK_STORAGE_KEY_TXS, JSON.stringify([newTx, ...transactions]));
      return;
    }
    if (!db) return;
    const tempId = 'tmp-' + now;
    const optimistic: Transaction = { ...t, id: tempId, createdAt: now, updatedAt: now, ledgerId, creatorUid: authUser.uid };
    setTransactions(prev => [optimistic, ...prev]);
    try {
      await addDoc(collection(db, `ledgers/${ledgerId}/transactions`), { ...t, createdAt: now, updatedAt: now, creatorUid: authUser.uid });
      setTransactions(prev => prev.filter(tx => tx.id !== tempId));
      // refresh incrementally
      await syncTransactions();
    } catch (e: any) {
      console.error(e);
      alert('Error: ' + e.message);
      // revert optimistic
      setTransactions(prev => prev.filter(tx => tx.id !== tempId));
    }
  };

  const updateTransaction = async (id: string, updates: Partial<Transaction>) => {
    const now = Date.now();
    if (isMockMode) {
      const updated = transactions.map(t => t.id === id ? { ...t, ...updates } : t);
      setTransactions(updated);
      localStorage.setItem(MOCK_STORAGE_KEY_TXS, JSON.stringify(updated));
      return;
    }
    if (!ledgerId || !db) return;
    // optimistic update
    setTransactions(prev => prev.map(t => t.id === id ? { ...t, ...updates, updatedAt: now } : t));
    try {
      await updateDoc(doc(db, `ledgers/${ledgerId}/transactions`, id), { ...updates, updatedAt: now });
      // fetch incremental changes
      await syncTransactions();
    } catch (e: any) {
      console.error(e);
      alert(`Update failed: ${e.message}`);
    }
  };

  const deleteTransaction = async (id: string) => {
    const now = Date.now();
    if (isMockMode) {
      const filtered = transactions.filter(t => t.id !== id);
      setTransactions(filtered);
      localStorage.setItem(MOCK_STORAGE_KEY_TXS, JSON.stringify(filtered));
      return;
    }
    if (!ledgerId || !db) return;
    // soft-delete to allow incremental sync to catch removals
    setTransactions(prev => prev.filter(t => t.id !== id));
    try {
      await updateDoc(doc(db, `ledgers/${ledgerId}/transactions`, id), { deleted: true, deletedAt: now, updatedAt: now });
      await syncTransactions();
    } catch (e: any) {
      console.error(e);
      alert(`Delete failed: ${e.message}`);
    }
  };

  // Placeholders
  const loadData = () => {};
  const switchUser = () => {};
  const addUser = () => {};
  const removeUser = () => {};

  const currentUser: User = authUser ? {
    uid: authUser.uid,
    displayName: authUser.displayName,
    email: authUser.email,
    photoURL: authUser.photoURL,
    color: 'bg-indigo-500'
  } : localUsers[0];

  const activeUsers = authUser ? users : localUsers;

  return (
    <AppContext.Provider value={{
      transactions,
      addTransaction,
      deleteTransaction,
      updateTransaction,
      loadData,
      currentUser,
      users: activeUsers,
      ledgerId,
      isInitializing,
      refreshUserProfile,
      joinLedger,
      createLedger,
      createRecurringTemplate,
      switchLedger,
      leaveLedger,
      updateLedgerAlias,
      savedLedgers,
      switchUser,
      addUser,
      removeUser,
      selectedDate,
      setSelectedDate,
      isDarkMode,
      toggleTheme,
      expenseCategories,
      incomeCategories,
      addCategory,
      deleteCategory,
      resetCategories,
      syncTransactions,
      lastSyncedAt: lastSyncedAt || undefined,
      isSyncing
    }}>
      {children}
    </AppContext.Provider>
  );
};

export const useAppContext = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useAppContext must be used within an AppProvider');
  }
  return context;
};
