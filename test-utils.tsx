import React, { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { AppContext } from './contexts/AppContext';
import { AuthContext } from './contexts/AuthContext';

// Minimal mocks for contexts
const mockAppContextValue: any = {
  expenseCategories: [],
  incomeCategories: [],
  addCategory: async () => {},
  deleteCategory: async () => {},
  resetCategories: async () => {},
  transactions: [],
  users: [],
  currentUser: { uid: 'u1' },
  ledgerId: 'ledger1',
  joinLedger: async () => true,
  savedLedgers: [],
  switchLedger: async () => {},
  createLedger: async () => {},
  leaveLedger: async () => {},
  updateLedgerAlias: async () => {},
  isDarkMode: false,
  toggleTheme: () => {},
};

const mockAuthContextValue: any = {
  signOut: async () => {},
};

export function renderWithProviders(ui: ReactNode) {
  return render(
    <AuthContext.Provider value={mockAuthContextValue}>
      <AppContext.Provider value={mockAppContextValue}>{ui}</AppContext.Provider>
    </AuthContext.Provider>
  );
}
