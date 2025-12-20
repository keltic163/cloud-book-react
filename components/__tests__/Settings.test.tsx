import React from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import Settings from '../Settings';
import { renderWithProviders } from '../../test-utils';

// Mock validateApiKey from services
jest.mock('../../services/geminiService', () => ({
  validateApiKey: jest.fn()
}));

const { validateApiKey } = require('../../services/geminiService');

describe('Settings BYOK UI', () => {
  beforeEach(() => {
    localStorage.clear();
    jest.resetAllMocks();
  });

  test('saves key to localStorage and tests models for dev-code', async () => {
    // dev-code should return a model list
    validateApiKey.mockResolvedValueOnce({ valid: true, models: ['gemini-3-flash-preview'] });

    renderWithProviders(<Settings />);

    const input = screen.getByPlaceholderText(/輸入 API Key/i);
    fireEvent.change(input, { target: { value: '6yhn%TGB' } });

    const saveButton = screen.getByText('儲存');
    fireEvent.click(saveButton);

    expect(localStorage.getItem('user_gemini_key')).toBe('6yhn%TGB');

    const testButton = screen.getByText(/測試並抓取模型/i);
    fireEvent.click(testButton);

    await waitFor(() => {
      expect(validateApiKey).toHaveBeenCalledWith('6yhn%TGB');
    });

    await waitFor(() => screen.getByText(/可用模型/i));
    expect(screen.getByText('gemini-3-flash-preview')).toBeInTheDocument();
  });

  test('shows invalid message for bad key', async () => {
    validateApiKey.mockResolvedValueOnce({ valid: false, models: [] });

    renderWithProviders(<Settings />);

    const input = screen.getByPlaceholderText(/輸入 API Key/i);
    fireEvent.change(input, { target: { value: 'invalid-key' } });

    const saveButton = screen.getByText('儲存');
    fireEvent.click(saveButton);

    const testButton = screen.getByText(/測試並抓取模型/i);
    fireEvent.click(testButton);

    await waitFor(() => expect(validateApiKey).toHaveBeenCalledWith('invalid-key'));
    await waitFor(() => screen.getByText(/Key 無效或沒有可用模型/i));
  });
});