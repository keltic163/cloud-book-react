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
  monthKey?: string;
  year?: number;
  searchTokens?: string[];
}

export interface SpendingSummary {
  totalIncome: number;
  totalExpense: number;
  totalRewards: number;
  balance: number;
}

export type StatsTimeRange = 'month' | 'year';

export interface StatsSummaryParams {
  year: number;
  month?: number;
  timeRange: StatsTimeRange;
  selectedMemberId?: string | 'all';
  filterCategory?: string | 'all';
  keyword?: string;
  viewType?: TransactionType;
}

export interface MonthStats {
  monthKey: string;
  year: number;
  totalIncome: number;
  totalExpense: number;
  totalRewards: number;
  transactionCount: number;
  categoryIncome: Record<string, number>;
  categoryExpense: Record<string, number>;
  memberIncome: Record<string, number>;
  memberExpense: Record<string, number>;
}

export interface StatsSummary {
  yearlyData: Array<{ income: number; expense: number }>;
  displayTotalIncome: number;
  displayTotalExpense: number;
  displayBalance: number;
  categoryStats: Array<{ name: string; amount: number }>;
  memberStats: Array<{ uid: string; displayName: string | null; photoURL: string | null; color?: string; val: number }>;
  chartTotalAmount: number;
  source: 'aggregate' | 'query' | 'local';
}

export interface SystemAnnouncement {
  text: string;
  isEnabled: boolean;
  startAt: any;
  endAt: any;
  type?: 'info' | 'warning' | 'error';
}
