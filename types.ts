export enum TransactionType {
  INCOME = 'INCOME',
  EXPENSE = 'EXPENSE'
}

// 定義 Category 為字串型別，方便後續擴充
export type Category = string;

export interface User {
  uid: string; // Firebase User ID
  displayName: string | null;
  email: string | null;
  photoURL: string | null;
  color?: string; // For UI display, like a fallback color
}

export interface SavedLedger {
  id: string;
  alias: string; // User's personal note/name for this ledger
  lastAccessedAt: number;
}

export interface UserProfile {
  uid: string;
  lastLedgerId?: string;
  savedLedgers: SavedLedger[];
}

export interface Ledger {
  id: string; // Ledger ID (Firestore document ID)
  name: string;
  ownerUid: string; // The UID of the user who created this ledger
  members: string[]; // Array of UIDs of all members
  
  // ✅ 新增：這個帳本專屬的分類清單
  categories: string[]; 
  
  createdAt: number;
}

export interface Transaction {
  id: string;
  amount: number;
  type: TransactionType;
  
  // 這裡改為 string 即可，因為現在分類是動態的
  category: string; 
  
  description: string;
  rewards: number; // Value of points/cashback received
  date: string; // ISO String
  creatorUid: string; // The UID of the user who created this record
  ledgerId: string; // Link to the associated ledger
  createdAt: number;
}

export interface SpendingSummary {
  totalIncome: number;
  totalExpense: number;
  totalRewards: number;
  balance: number;
}

// ✅ 2.3.0新增：系統公告型別
export interface SystemAnnouncement {
  text: string;
  isEnabled: boolean;
  startAt: any; // Firestore Timestamp
  endAt: any;   // Firestore Timestamp
  type?: 'info' | 'warning' | 'error';
}