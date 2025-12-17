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
    
    // 發送請求
    const result = await parseTransactionFn({
      text: input,
      categories: availableCategories
    });

    // Cloud Functions 回傳的資料在 data 屬性中
    return result.data as ParsedTransactionData;

  } catch (error) {
    console.error("Cloud Function Call Error:", error);
    return null;
  }
};