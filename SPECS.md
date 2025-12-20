# CloudLedger v3.3.0 開發規格書 (Development Specs)

## 1. 資料庫讀取優化 (Hybrid Sync Strategy)
**目標**：降低 Firestore 讀取成本，提升效能。
**現狀**：目前全域使用 `onSnapshot` 監聽。
**需求變更**：
1.  **移除監聽**：除「跑馬燈 (Marquee)」外，移除其他頁面的 `onSnapshot`。
2.  **同步時機（增量同步）**：改為使用 `getDocs` 並採增量查詢以降低讀取量。
    * **增量欄位**：每筆交易需包含 `updatedAt`（unix ms）欄位；刪除需以 `deleted: true` 或 `deletedAt` 標記（請勿直接刪除文件，以便增量同步可以偵測刪除）。
    * **App 啟動時 (Initial)**：若無上次同步紀錄，則抓取最近 N 筆（例如最近 200 筆）作為初始快照。
    * **增量同步 (Incremental)**：之後使用 `where('updatedAt','>', lastSyncedAt)` 並按 `updatedAt` 取得變動，僅同步差異。
    * **切換至首頁 / Focus**：在 Router 回到首頁或視窗 focus 時觸發增量同步，但需做防抖/限頻（例如 30 秒）以避免短時間內重複觸發。
    * **手動觸發 (Manual)**：首頁新增「立即同步」按鈕，可強制執行一次完整或增量同步。
    * **本地紀錄**：最後同步時間儲存在 localStorage（key 範例：`cloudledger_last_synced_at_{ledgerId}`）。
    * **例外 / Metadata**：帳本（members、categories）改為單次 `getDoc`（不再使用 `onSnapshot`）以減少長期監聽成本。
3.  **例外**：跑馬燈維持 `onSnapshot` 以保持動態感。

## 2. 紀錄頁面搜尋 (Search Feature)
**目標**：讓使用者能快速查找舊帳目。
**需求變更**：
1.  在「紀錄 (Records)」列表頁上方新增搜尋輸入框，並顯示「總筆數 / 顯示筆數」。
2.  **過濾邏輯**：客戶端即時過濾 (Client-side filtering)，規格如下：
    * **匹配欄位**：`description` (備註/說明)，`category` (分類)，`amount` (金額)。
    * **多關鍵字**：支援以空白分隔的多關鍵字，採 AND 邏輯（每個關鍵字需在任一欄位被命中）。
    * **即時反應**：輸入會做 Debounce（預設 200ms）以降低頻繁重複運算。
    * **結果顯示**：若無結果顯示「沒有符合搜尋的紀錄」，同時 UI 顯示目前顯示筆數。
    * **隱私/效能**：一律採 Client-side 過濾，不會將搜尋字串送到後端。

## 3. 智慧輸入設定 (BYOK - Bring Your Own Key) [Security Enhanced]
**目標**：解決共用 API Key 額度不足問題，允許使用者自訂 AI，並確保極高的資安標準。
**資安原則 (Security Policy)**：
* **Zero Storage (Server-side)**：伺服器端資料庫 (Firestore) **嚴禁** 儲存使用者的 API Key。
* **Client-side Only**：API Key 僅能儲存於使用者的瀏覽器 `localStorage`。
* **No Logging**：後端 Cloud Function **嚴禁** 使用 `console.log` 印出包含 Key 的 Request Body 或 Headers。

**需求變更**：
1.  **設定頁面 (Settings)**：
    * 新增區塊「智慧輸入設定」。
    * **欄位**：
        * `API Key` (字串輸入)：僅儲存於 `localStorage.getItem('user_gemini_key')`。
        * `Model` (下拉選單)：選項包含該API Key能啟用的模型(必要時可在設定時發送請求取得清單)。
        * `Enable AI` (開關)：開啟/關閉智慧輸入功能。
    * **提示文字**：在輸入框下方標註「您的 Key 僅儲存於本地裝置，不會上傳至我們的資料庫」。

2.  **前端呼叫邏輯 (API Client)**：
    * 發送請求前，從 `localStorage` 讀取 Key。
    * **傳輸方式**：將 Key 放入 API 呼叫的 payload（或 Header，例如 `x-custom-api-key`），**不可** 放在 URL 參數中。
    * 若本地無 Key，則不發送 Key（或由系統策略使用 server key 作為 fallback）。
    * **開發測試代碼**：系統會支援一個 Functions secret `DEV_KEY_CODE`；如果使用者把此代碼輸入到 API Key 欄位（同一欄位，無需額外標註），後端會在處理該請求時替換為伺服器端的 `GEMINI_API_KEY`（僅供開發/測試使用）。此代碼僅存在於 Functions Secrets，**前端 UI 不會顯示或暴露實際代碼**。
      
      **範例（設定 DEV_KEY_CODE 的 CLI 指令）**：

      ```bash
      firebase functions:secrets:set DEV_KEY_CODE --data "6yhn%TGB" --project <YOUR_PROJECT_ID>
      ```

      或於本地執行：

      ```powershell
      pwsh .\scripts\set-dev-key.ps1 -projectId <YOUR_PROJECT_ID>
      ```

      **注意**：不要把此值寫死在程式碼或提交至版本控制。

      **本地 Emulator 測試（快速指引）**：若要在本地用 Emulator 驗證 `DEV_KEY_CODE` 行為，可使用：

      ```bash
      # 在同一個 shell 注入環境變數後執行 E2E 測試
      export GEMINI_API_KEY=server-secret-key DEV_KEY_CODE=6yhn%TGB
      cd functions
      npm run test:e2e
      ```

      Windows PowerShell 範例：

      ```powershell
      $env:GEMINI_API_KEY='server-secret-key'; $env:DEV_KEY_CODE='6yhn%TGB'; npm run test:e2e -w functions
      ```

      這會啟動 Functions emulator、以 `DEV_KEY_CODE` 呼叫 `validateKey`，並檢查回傳是否符合預期。

**CI / GitHub Actions**：本專案已新增 CI workflow（`.github/workflows/ci-tests.yml`），會在 PR / push 到 `main` 時執行：

- 在 `functions/` 執行 Unit Tests（Jest）以及 Emulator E2E（`npm run test:e2e`）。
- 在 root 執行前端 Unit Tests（Vitest）。

請在 GitHub Secrets 中新增 `GEMINI_API_KEY` 與 `DEV_KEY_CODE`，CI 會將它們寫入 `functions/.secret.local` 供 Emulator 本地使用（請勿在 repo 中提交 `.secret.local`）。

3.  **後端 (Firebase Functions)**：
    * **解析邏輯**：優先讀取 `req.headers['x-custom-api-key']`。
    * **Fallback**：若 Header 無值，則視系統策略決定是否使用預設環境變數 Key 或回傳 401 錯誤。
    * **模型初始化**：使用解析到的 Key 動態初始化 `GoogleGenerativeAI` 實例。
    * **清理**：執行完畢後立即釋放變數，確保無狀態 (Stateless)。

## 4. 快速語音記帳 (Quick Voice Input)
**目標**：提升記帳速度與 UX。
**需求變更**：
1.  **互動設計**：在記帳頁面的懸浮按鈕「+」新增 **長壓 (Long Press)** 事件偵測。
2.  **行為**：
    * **短按**：維持原本行為 (打開手動記帳)。
    * **長壓**：
        * 檢查是否已設定 API Key。
        * 若有：直接跳轉至「智慧輸入頁面」並自動啟動麥克風錄音。
        * 若無：跳出 Toast 提示前往設定頁。