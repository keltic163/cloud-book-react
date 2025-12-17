import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { GoogleGenAI, Type } from "@google/genai";
// 如果您有用到 Firestore 觸發器 (例如建立新帳本)，請記得 import admin/db
// import * as admin from "firebase-admin";

// 1. 定義 Secret
const geminiApiKey = defineSecret("GEMINI_API_KEY");

// ✅ 修改：定義分開的預設分類 (與前端 constants.ts 保持一致)
const DEFAULT_EXPENSE_CATEGORIES = [
  '餐飲', '交通', '購物', '居住', '娛樂', '醫療', '教育', '其他'
];

const DEFAULT_INCOME_CATEGORIES = [
  '薪資', '獎金', '投資', '兼職', '零用金', '消費回饋', '其他'
];

interface SmartInputRequest {
  text: string;
  categories?: string[];
}

// 2. 解析交易的函式
export const parseTransaction = onCall(
  { secrets: [geminiApiKey] },
  async (request) => {
    // 檢查使用者是否登入
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "請先登入");
    }

    const { text, categories } = request.data as SmartInputRequest;
    
    // ✅ 修改：準備備用的分類清單 (將支出與收入合併)
    // 這樣如果前端沒傳分類，AI 依然可以從所有預設分類中選擇
    const defaultAllCategories = [...DEFAULT_EXPENSE_CATEGORIES, ...DEFAULT_INCOME_CATEGORIES];

    const availableCategories = categories && categories.length > 0 
        ? categories 
        : defaultAllCategories;

    // 初始化 Gemini
    const ai = new GoogleGenAI({ apiKey: geminiApiKey.value() });
    const today = new Date().toISOString().split('T')[0];

    try {
      const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: `
          Analyze this financial input: "${text}".
          Context: Today is ${today}.
          Requirements:
          1. Amount: Extract number.
          2. Type: 'EXPENSE' or 'INCOME'.
          3. Category: Select strictly from: [${availableCategories.join(', ')}]. If unsure, use '其他'.
          4. Description: Short summary in Traditional Chinese (NO numbers).
          5. Rewards: Extract points/cashback value.
          6. Date: YYYY-MM-DD format if mentioned, else null.
        `,
        config: {
          responseMimeType: "application/json",
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              amount: { type: Type.NUMBER },
              type: { type: Type.STRING, enum: ["INCOME", "EXPENSE"] },
              category: { type: Type.STRING, enum: availableCategories },
              description: { type: Type.STRING },
              rewards: { type: Type.NUMBER },
              date: { type: Type.STRING }
            },
            required: ["amount", "type", "category", "description"],
          },
        },
      });

      const resultText = response.text;
      if (!resultText) throw new Error("No response from AI");

      return JSON.parse(resultText);

    } catch (error: any) {
      console.error("Gemini Backend Error:", error);
      throw new HttpsError("internal", "AI 解析失敗");
    }
  }
);

// ------------------------------------------------------------------
// 💡 補充建議：如果您有「自動建立使用者帳本」的 Trigger (onUserCreate)
// 請記得也要在那邊使用這兩個新變數寫入資料庫，範例如下：
/*
import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
// admin.initializeApp(); // 確保有初始化

export const onUserCreate = functions.auth.user().onCreate(async (user) => {
  const db = admin.firestore();
  await db.collection('ledgers').add({
    name: '我的帳本',
    ownerUid: user.uid,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    members: [{
      uid: user.uid,
      displayName: user.displayName,
      email: user.email,
      photoURL: user.photoURL
    }],
    // ✅ 這裡也要改成寫入分開的欄位
    expenseCategories: DEFAULT_EXPENSE_CATEGORIES,
    incomeCategories: DEFAULT_INCOME_CATEGORIES
  });
});
*/