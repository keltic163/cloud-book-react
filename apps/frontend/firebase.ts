import * as firebaseApp from "firebase/app";
import { getAuth, GoogleAuthProvider, type Auth } from "firebase/auth";
import { getFirestore, type Firestore } from "firebase/firestore";
import { getFunctions, type Functions } from "firebase/functions";

const firebaseConfig = {
  apiKey: "AIzaSyCqYd0jEalHKO7xH5zvS9_zavr7GA-FyjU",
  authDomain: "ledger-butler.firebaseapp.com",
  projectId: "ledger-butler",
  storageBucket: "ledger-butler.firebasestorage.app",
  messagingSenderId: "622793086532",
  appId: "1:622793086532:web:7004d269aadd490616ce11",
  measurementId: "G-YTMG04WWJR"
};


// 定義變數
let app;
let auth: Auth | undefined;
let db: Firestore | undefined;
let functions: Functions | undefined;
let googleProvider: GoogleAuthProvider | undefined;
let isMockMode = false;




try {
  // 1. 嘗試初始化 Firebase (Modular Syntax - Named Import)
  // Use namespace import to avoid TypeScript error 'Module has no exported member initializeApp'
  app = firebaseApp.initializeApp(firebaseConfig);
  auth = getAuth(app);
  db = getFirestore(app);
  functions = getFunctions(app);
  googleProvider = new GoogleAuthProvider();
  console.log("✅ Firebase 連線嘗試成功");

} catch (error) {
  // 2. 如果這串 Key 無法使用 (例如專案不存在、Key 錯誤、網路不通)
  // 程式會跳到這裡，並自動切換成模擬模式
  console.warn("⚠️ Firebase 連線失敗，Key 可能無效或設定錯誤。", error);
  console.log("🔄 切換至 [模擬模式] (Mock Mode)");
  
  isMockMode = true;
}

const enableMockMode = () => {
  isMockMode = true;
  console.log("🔄 手動切換至 [模擬模式] (Mock Mode)");
};

export { app, auth, db, functions, googleProvider, isMockMode, enableMockMode };