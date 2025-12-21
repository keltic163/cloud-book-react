## 版本紀錄

### v3.3.1
- iOS Google 登入改用 redirect，popup 失敗會自動轉向；Android 流程不變。
- 智慧輸入區新增提示：長按畫面下方的「+」可快速啟動語音辨識。
- 同步 UI 調整：主畫面右上角只保留同步按鈕，最後同步時間移到設定頁「資料同步」區塊並可手動同步。
- 版本號更新至 3.3.1。

### v3.3.0
- 混合同步（Hybrid Sync）。
- 智慧輸入 BYOK 支援 `DEV_KEY_CODE`。
- 長按「+」優化快速加入帳本體驗。

### v3.2.1
**PWA 安裝修復版**
- 修正 Android 安裝 PWA 首頁異常。
- 更新 `manifest` 的 `purpose: 'any maskable'`，支援 Adaptive Icons。
- 修正 favicon 顯示問題（`index.html` link 標籤）。

### v3.2.0
**PWA 化與 UI 更新**
- 新增 `vite-plugin-pwa` 使網站可安裝。
- 新增 Service Worker，加速離線/快取載入。
- UI/UX 微調：新增首屏引導、標題樣式調整。

### v3.1.0
**AI 記帳核心**
- 串接 Google Gemini API，自然語言記帳（後端能力）。
- 接上 Firestore 並部署 Firebase Hosting。
- 響應式體驗優化。

---

## CI / 自動化測試 (GitHub Actions)
PR 與推送到 `main` 會觸發 `.github/workflows/ci-tests.yml`。
- `functions-tests`: `functions/` 的單元測試（Jest）與 Firebase Functions Emulator E2E (`npm run test:e2e`)。
- `frontend-tests`: 前端單元測試（Vitest）。

### 必填 GitHub Secrets
在 Settings → Secrets and variables → Actions 建立：
- `GEMINI_API_KEY`: Emulator 測試用 Key（用測試授權即可）。
- `DEV_KEY_CODE`: 測試代碼（例：`6yhn%TGB`），CI 會放入 `functions/.secret.local` 提供 Emulator 使用。

**安全注意**：勿將私密金鑰硬寫進版本庫；請用 Secrets 或 `.secret.local`。

### 本機跑 CI 模擬
在 `functions/` 建立 `.secret.local` 並填入 `GEMINI_API_KEY`、`DEV_KEY_CODE`，再執行 `npm run test:e2e`。
