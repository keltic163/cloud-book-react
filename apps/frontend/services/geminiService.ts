import { getFunctions, httpsCallable } from "firebase/functions";
import { app } from "../firebase"; // 確保引入的是初始化的 app
import { TransactionType } from "../types"; 
// ✅ 修改：引入新的兩組常數
import { DEFAULT_EXPENSE_CATEGORIES, DEFAULT_INCOME_CATEGORIES } from "../constants";

// 初始化 Cloud Functions
const functions = getFunctions(app);

export interface ParsedTransactionData {
  amount: number;
  type: TransactionType;
  category: string;
  description: string;
  rewards: number;
  date?: string;
}

// ✅ 修改：合併兩組分類作為預設備案
const defaultAllCategories = [...DEFAULT_EXPENSE_CATEGORIES, ...DEFAULT_INCOME_CATEGORIES];

/**
 * 呼叫 Firebase Cloud Function (後端) 進行 AI 解析
 */
export const parseSmartInput = async (
  input: string, 
  // ✅ 修改：預設值改用合併後的新陣列
  availableCategories: string[] = defaultAllCategories 
): Promise<ParsedTransactionData | null> => {
  try {
    // 建立後端函式參照
    const parseTransactionFn = httpsCallable(functions, 'parseTransaction');
    
    // Read user-provided key (if any) from localStorage. This allows a user to input a
    // dev-code here and have the backend substitute the server key when appropriate.
    const userKey = localStorage.getItem('user_gemini_key') || undefined;

    // 發送請求 (把 apiKey 一併送出供 Functions 決定處理方式)
    const result = await parseTransactionFn({
      text: input,
      categories: availableCategories,
      apiKey: userKey
    });

    // Cloud Functions 回傳的資料在 data 屬性中
    return result.data as ParsedTransactionData;

  } catch (error) {
    console.error("Cloud Function Call Error:", error);
    return null;
  }
};

/**
 * 驗證 / 測試使用者提供的 API Key，並回傳可用模型清單
 */
export const validateApiKey = async (apiKey?: string): Promise<{ valid: boolean; models: string[] } > => {
  try {
    const validateFn = httpsCallable(functions, 'validateKey');
    const result = await validateFn({ apiKey });
    return result.data as { valid: boolean; models: string[] };
  } catch (err) {
    console.error('validateKey call failed', err);
    return { valid: false, models: [] };
  }
};