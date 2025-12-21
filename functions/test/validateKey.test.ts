import { describe, test, expect, beforeEach, vi } from 'vitest';
import { validateKeyHandler } from '../src/index';

// Mock defineSecret to return .value() reading from process.env
vi.mock('firebase-functions/params', () => ({
  defineSecret: (name: string) => ({
    value: () => process.env[name]
  })
}));

// Mock GoogleGenAI with behavior depending on provided API key
vi.mock('@google/genai', () => {
  return {
    GoogleGenAI: vi.fn().mockImplementation(({ apiKey }: any) => ({
      apiKey,
      models: {
        generateContent: async ({ model }: any) => {
          // If apiKey is an explicitly invalid string, simulate auth failure
          if (apiKey === 'invalid-key') throw new Error('Auth failed');
          // Simulate available model only for gemini-3-flash-preview
          if (model === 'gemini-3-flash-preview') return { text: 'YES' };
          throw new Error('Model not available');
        }
      }
    }))
  };
});

describe('validateKeyHandler', () => {
  beforeEach(() => {
    vi.resetModules();
    process.env['GEMINI_API_KEY'] = 'server-secret-key';
    process.env['DEV_KEY_CODE'] = '6yhn%TGB';
  });

  test('uses server key when provided dev-code', async () => {
    const req = { auth: { uid: 'u1' }, data: { apiKey: '6yhn%TGB' } } as any;

    const res = await validateKeyHandler(req);

    expect(res.valid).toBe(true);
    expect(res.models).toContain('gemini-3-flash-preview');
  });

  test('invalid key returns false', async () => {
    const req = { auth: { uid: 'u1' }, data: { apiKey: 'invalid-key' } } as any;

    const res = await validateKeyHandler(req);

    expect(res.valid).toBe(false);
    expect(res.models.length).toBe(0);
  });

  test('rejects unauthenticated', async () => {
    const req = { auth: null, data: { apiKey: '6yhn%TGB' } } as any;
    await expect(validateKeyHandler(req)).rejects.toThrow();
  });
});
