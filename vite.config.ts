import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
// ✅ 1. 新增：引入 PWA 外掛
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig(({ mode }) => {
  
  const env = loadEnv(mode, '.', '');
  return {
    // 👇【修改這裡】Firebase 部署在根目錄，所以要改回 '/'
    base: '/',

    server: {
      port: 3000,
      host: '0.0.0.0',
    },
    
    // ✅ 2. 修改：在 plugins 陣列中加入 VitePWA 設定
    plugins: [
      react(),
      VitePWA({
        registerType: 'autoUpdate',
        includeAssets: ['favicon.ico', 'apple-touch-icon.png', 'favicon.svg'],
        manifest: {
          name: 'CloudLedger 雲記',
          short_name: 'CloudLedger',
          description: '您的雲端智慧記帳助手',
          theme_color: '#ffffff',
          background_color: '#000000ff',
          display: 'standalone', // 讓手機把網站當成 App 開啟的關鍵
          scope: '/',
          start_url: '/',
          icons: [
            {
              src: 'web-app-manifest-192x192.png',
              sizes: '192x192',
              type: 'image/png',
              purpose: 'any maskable'
            },
            {
              src: 'web-app-manifest-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'any maskable'
            }
          ]
        }
      })
    ],

    // ⚠️ 備註：既然 AI 邏輯已經移到後端，前端其實不再需要這些 KEY 了
    // 但為了避免改太多東西報錯，這段先留著沒關係
    define: {
      'process.env.API_KEY': JSON.stringify(env.GEMINI_API_KEY),
      'process.env.GEMINI_API_KEY': JSON.stringify(env.GEMINI_API_KEY)
    },
    resolve: {
      alias: {
        '@': path.resolve(__dirname, '.'),
      }
    },
    build: {
      outDir: 'dist',
    }
  };
});