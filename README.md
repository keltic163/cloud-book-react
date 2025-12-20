## 📅 Version History (版本紀錄)

### v3.3.0 (In Development) 🚧
## [v3.3.0] - 2025-12-21
### Added
- 增量同步 (Hybrid Sync)
- 智慧輸入 BYOK 與 `DEV_KEY_CODE` 支援
- 快速語音記帳 (長按 +)

---

### v3.2.1 (Current Release) ✅
**PWA 安裝修復版**
- **🐛 Bug Fixes**：
  - 修復 Android 裝置上 PWA 無法安裝的問題。
  - 修正 `manifest` 設定，加入 `purpose: 'any maskable'` 以支援 Android 自適應圖示 (Adaptive Icons)。
  - 修復電腦版瀏覽器分頁圖示 (Favicon) 顯示異常的問題 (補回 `index.html` link 標籤)。

### v3.2.0
**PWA 應用程式化與 UI 重大更新**
- **✨ New Features**：
  - **PWA 支援**：導入 `vite-plugin-pwa`，網站現在可以安裝為應用程式 (Installable)。
  - **Service Worker**：新增離線快取機制，提升載入速度。
- **🎨 UI/UX Changes**：
  - **全新圖示系統**：將原本的地球圖示更換為全新的「CloudLedger 雲朵」品牌識別。
  - **介面微調**：更新標題列樣式與應用程式名稱顯示。

### v3.1.0
**基礎穩定版**
- **🧠 AI Core**：整合 Google Gemini API 進行自然語言記帳 (後端功能)。
- **🔥 Firebase**：完成 Firestore 資料庫串接與 Firebase Hosting 部署。
- **📱 Responsive**：完成手機版與電腦版的響應式切版。jo

---

## CI / 自動化測試 (GitHub Actions)
本專案包含 CI workflow（位於 `.github/workflows/ci-tests.yml`），會在 PR 與 push 到 `main` 時執行：

- **functions-tests**：在 `functions/` 執行 Unit Tests（Jest）並啟動 Firebase Functions Emulator 執行 E2E 測試（`npm run test:e2e`）。
- **frontend-tests**：執行前端單元測試（Vitest）。

### 必要的 GitHub Secrets
在專案的 Settings → Secrets & variables → Actions 中建立以下 Secret：
- `GEMINI_API_KEY`：用於 Emulator 的測試（請使用測試用或可控範例）。
- `DEV_KEY_CODE`：開發測試代碼（例如 `6yhn%TGB`），CI 會把它放入 `functions/.secret.local` 供 Emulator 使用。

**安全注意**：請勿把真實金鑰硬編在程式或提交到 repository；僅使用 Secrets 與本地 `.secret.local` 做測試替代。

欲在本機模擬 CI：請在 `functions/` 使用 `.secret.local` 或環境變數注入 `GEMINI_API_KEY` 與 `DEV_KEY_CODE`，再執行 `npm run test:e2e`。