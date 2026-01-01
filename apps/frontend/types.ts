export enum TransactionType {
  INCOME = 'INCOME',
  EXPENSE = 'EXPENSE'
}

export type Category = string;

export interface User {
  uid: string;
  displayName: string | null;
  email: string | null;
  photoURL: string | null;
  color?: string;
}

export interface SavedLedger {
  id: string;
  alias: string;
  lastAccessedAt: number;
}

export interface UserProfile {
  uid: string;
  lastLedgerId?: string;
  savedLedgers: SavedLedger[];
}

export interface Ledger {
  id: string;
  name: string;
  ownerUid: string;
  members: string[];
  categories: string[];
  createdAt: number;
}

export interface Transaction {
  id: string;
  amount: number;
  type: TransactionType;
  category: string;
  description: string;
  rewards: number;
  date: string;
  creatorUid: string;
  targetUserUid?: string;
  ledgerId: string;
  createdAt: number;
  updatedAt?: number;
  deleted?: boolean;
  deletedAt?: number;
}

export interface SpendingSummary {
  totalIncome: number;
  totalExpense: number;
  totalRewards: number;
  balance: number;
}

export interface SystemAnnouncement {
  text: string;
  isEnabled: boolean;
  startAt: any;
  endAt: any;
  type?: 'info' | 'warning' | 'error';
}
