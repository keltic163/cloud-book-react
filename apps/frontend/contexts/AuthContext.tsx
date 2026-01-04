import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import {
  signInWithPopup,
  signInWithRedirect,
  signOut as firebaseSignOut,
  onAuthStateChanged,
  User as FirebaseUser
} from 'firebase/auth';
import { auth, googleProvider, isMockMode, enableMockMode as enableFirebaseMockMode } from '../firebase';

interface AuthContextType {
  user: FirebaseUser | null;
  loading: boolean;
  signInWithGoogle: () => Promise<void>;
  signOut: () => Promise<void>;
  enterMockMode: () => void;
  isMockMode: boolean;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [loading, setLoading] = useState(true);
  const isIOS = () => {
    const ua = navigator?.userAgent || '';
    const isiOSDevice = /iPad|iPhone|iPod/.test(ua);
    const isTouchMac = ua.includes('Mac') && 'ontouchend' in document;
    return isiOSDevice || isTouchMac;
  };

  // Checks mock mode status on mount
  useEffect(() => {
    if (isMockMode) {
      const mockUser = localStorage.getItem('cloudledger_mock_user');
      if (mockUser) {
        try {
          setUser(JSON.parse(mockUser));
          setLoading(false);
          return;
        } catch (e) {
          console.warn('Mock user payload invalid, resetting.', e);
        }
      }
      // Auto-login a mock user if storage is missing or invalid.
      const defaultMockUser: any = {
        uid: 'mock-user-123',
        displayName: '演示使用者',
        email: 'demo@example.com',
        photoURL: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix',
        emailVerified: true
      };
      setUser(defaultMockUser);
      localStorage.setItem('cloudledger_mock_user', JSON.stringify(defaultMockUser));
      setLoading(false);
      return;
    }

    if (!auth) {
      // Firebase not initialized
      setLoading(false);
      return;
    }

    const unsubscribe = onAuthStateChanged(auth, (currentUser) => {
      setUser(currentUser);
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  const loginAsMockUser = () => {
    setLoading(true);
    // Enable flag in firebase.ts
    enableFirebaseMockMode();

    setTimeout(() => {
      const mockUser: any = {
        uid: 'mock-user-123',
        displayName: '演示使用者',
        email: 'demo@example.com',
        photoURL: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix',
        emailVerified: true
      };
      setUser(mockUser);
      localStorage.setItem('cloudledger_mock_user', JSON.stringify(mockUser));
      setLoading(false);
    }, 500);
  };

  const signInWithGoogle = async () => {
    // If already in mock mode, go mock directly.
    if (isMockMode) {
      loginAsMockUser();
      return;
    }

    // If Firebase init failed, fallback to mock mode.
    if (!auth || !googleProvider) {
      loginAsMockUser();
      return;
    }

    try {
      // iOS Safari/PWA cannot reliably open popups; use redirect there.
      if (isIOS()) {
        await signInWithRedirect(auth, googleProvider);
        return;
      }

      await signInWithPopup(auth, googleProvider);
    } catch (error: any) {
      console.error('Login Failed', error);

      const errorCode = error?.code || 'unknown';
      const errorMessage = error?.message || 'Unknown error';

      if (errorCode === 'auth/popup-closed-by-user') {
        console.log('User closed popup. Not forcing mock mode.');
        return;
      }

      const confirmMock = window.confirm(
        `登入發生錯誤 (${errorCode})：\n${errorMessage}\n\n是否改用「演示模式」(Mock Mode)？\n此模式資料僅儲存在本機，不會上傳雲端。`
      );

      if (confirmMock) {
        loginAsMockUser();
        return;
      }

      if (errorCode?.startsWith('auth/popup')) {
        try {
          await signInWithRedirect(auth, googleProvider);
          return;
        } catch (redirectError) {
          console.error('Redirect login failed', redirectError);
        }
      }

      if (errorCode === 'auth/unauthorized-domain') {
        const currentDomain = window.location.hostname;
        alert(
          `無法登入：網域未授權\n\n請至 Firebase Console 將「${currentDomain}」加入授權網域。`
        );
      }
    }
  };

  const signOut = async () => {
    if (isMockMode) {
      localStorage.removeItem('cloudledger_mock_user');
      setUser(null);
      // Do not toggle isMockMode back to false automatically to prevent loops.
      window.location.reload();
      return;
    }

    if (!auth) return;
    try {
      await firebaseSignOut(auth);
    } catch (error) {
      console.error('Logout Failed', error);
    }
  };

  return (
    <AuthContext.Provider value={{
      user,
      loading,
      signInWithGoogle,
      signOut,
      enterMockMode: loginAsMockUser,
      isMockMode
    }}>
      {!loading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
};
