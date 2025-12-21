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
  enterMockMode: () => void; // New explicit function
  isMockMode: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [loading, setLoading] = useState(true);
  const isIOS = () => {
    const ua = navigator?.userAgent || "";
    const isiOSDevice = /iPad|iPhone|iPod/.test(ua);
    const isTouchMac = ua.includes("Mac") && "ontouchend" in document;
    return isiOSDevice || isTouchMac;
  };

  // Checks mock mode status on mount or when changed externally
  useEffect(() => {
    // 優先檢查模擬模式
    if (isMockMode) {
      const mockUser = localStorage.getItem('cloudledger_mock_user');
      if (mockUser) {
        setUser(JSON.parse(mockUser));
      } else {
        // If isMockMode is true but no user in storage, we can auto-login a mock user
        // or wait for explicit action. Let's auto-login to be safe if they are stuck.
         const defaultMockUser: any = {
            uid: 'mock-user-123',
            displayName: '演示使用者',
            email: 'demo@example.com',
            photoURL: 'https://api.dicebear.com/7.x/avataaars/svg?seed=Felix',
            emailVerified: true
         };
         setUser(defaultMockUser);
         localStorage.setItem('cloudledger_mock_user', JSON.stringify(defaultMockUser));
      }
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
    // 如果已經是模擬模式，直接模擬登入
    if (isMockMode) {
      loginAsMockUser();
      return;
    }

    // 如果 Firebase 初始化失敗，切換並模擬登入
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
      console.error("Login Failed", error);
      
      const errorCode = error?.code || 'unknown';
      const errorMessage = error?.message || 'Unknown error';

      // Always allow explicit fallback, even if user closed popup
      // but only prompt if it looks like a real error OR if the user might be stuck.
      // If code is 'auth/popup-closed-by-user', typically we do nothing. 
      // BUT if the user is complaining "nothing happens", let's log it.
      
      if (errorCode === 'auth/popup-closed-by-user') {
          console.log("User closed popup. Not forcing mock mode.");
          return;
      }

      // 詢問使用者是否切換至模擬模式
      const confirmMock = window.confirm(
        `登入發生錯誤 (${errorCode})。\n${errorMessage}\n\n是否切換至「演示模式」(Mock Mode)？\n此模式下資料僅儲存於本機，不會上傳至雲端。`
      );

      if (confirmMock) {
        loginAsMockUser();
        return;
      }

      // Fallback to redirect if popup failed (covers popup_blocked and similar cases)
      if (errorCode?.startsWith("auth/popup")) {
        try {
          await signInWithRedirect(auth, googleProvider);
          return;
        } catch (redirectError) {
          console.error("Redirect login failed", redirectError);
        }
      }

      // Specific advice
      if (errorCode === 'auth/unauthorized-domain') {
        const currentDomain = window.location.hostname;
        alert(
          `⚠️ 網域未授權\n\n` +
          `請至 Firebase Console 將 "${currentDomain}" 加入授權網域白名單。`
        );
      }
    }
  };

  const signOut = async () => {
    if (isMockMode) {
      localStorage.removeItem('cloudledger_mock_user');
      setUser(null);
      // We do NOT toggle isMockMode back to false automatically to prevent weird loops,
      // but user can refresh to try real firebase again if they want.
      // Or we can force reload.
      window.location.reload(); 
      return;
    }

    if (!auth) return;
    try {
      await firebaseSignOut(auth);
    } catch (error) {
      console.error("Logout Failed", error);
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
