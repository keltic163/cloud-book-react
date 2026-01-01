import React from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, test, expect, beforeEach, vi } from 'vitest';
import Settings from '../Settings';
import { renderWithProviders } from '../../test-utils';
import { validateApiKey } from '../../services/geminiService';

vi.mock('../../services/geminiService', () => ({
  validateApiKey: vi.fn()
}));

const mockedValidateApiKey = vi.mocked(validateApiKey);

const expandAllSections = () => {
  const buttons = screen.getAllByLabelText('展開');
  buttons.forEach((button) => fireEvent.click(button));
};

describe('Settings BYOK UI', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.resetAllMocks();
  });

  test('saves key to localStorage and tests models for dev-code', async () => {
    mockedValidateApiKey.mockResolvedValueOnce({ valid: true, models: ['gemini-3-flash-preview'] });

    renderWithProviders(<Settings />);
    expandAllSections();

    const input = screen.getByPlaceholderText('輸入 API Key 或開發測試代碼 (僅本地儲存)');
    fireEvent.change(input, { target: { value: '6yhn%TGB' } });

    fireEvent.click(screen.getByText('儲存'));
    expect(localStorage.getItem('user_gemini_key')).toBe('6yhn%TGB');

    fireEvent.click(screen.getByText('測試並抓取模型'));

    await waitFor(() => {
      expect(mockedValidateApiKey).toHaveBeenCalledWith('6yhn%TGB');
    });

    await waitFor(() => screen.getByText('gemini-3-flash-preview'));
    expect(screen.getByText('gemini-3-flash-preview')).toBeInTheDocument();
  });

  test('shows invalid message for bad key', async () => {
    mockedValidateApiKey.mockResolvedValueOnce({ valid: false, models: [] });

    renderWithProviders(<Settings />);
    expandAllSections();

    const input = screen.getByPlaceholderText('輸入 API Key 或開發測試代碼 (僅本地儲存)');
    fireEvent.change(input, { target: { value: 'invalid-key' } });

    fireEvent.click(screen.getByText('儲存'));
    fireEvent.click(screen.getByText('測試並抓取模型'));

    await waitFor(() => expect(mockedValidateApiKey).toHaveBeenCalledWith('invalid-key'));
    await waitFor(() => screen.getByText('Key 無效或沒有可用模型'));
  });
});
