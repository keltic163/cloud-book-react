# Cloud Ledger

Cloud Ledger 是一套記帳與帳本管理服務，包含 Web 前台、後台管理與 Firebase Functions。

## 專案結構
- `apps/frontend`：使用者前台（記帳、帳本、統計、設定）
- `apps/admin`：後台管理（公告、使用者、帳本與設定）
- `apps/android`：Android 應用程式
- `functions`：Firebase Functions API

## 需求
- Node.js (建議 18+)
- Firebase CLI（部署時）
- Android Studio / JDK（Android 開發與打包）

## 本機開發
### 前台
```bash
npm --prefix apps/frontend run dev
```

### 後台
```bash
npm --prefix apps/admin run dev
```

### Functions
```bash
npm --prefix functions run build
```

## Build
### 前台
```bash
npm --prefix apps/frontend run build
```

### 後台
```bash
npm --prefix apps/admin run build
```

### Functions
```bash
npm --prefix functions run build
```

## 部署（Firebase）
`firebase.json` 已設定 Hosting 指向 `apps/frontend/dist`。
```bash
firebase deploy --only hosting,functions
```

若要部署後台，請在 `firebase.json` 設定第二個 hosting target，並指向 `apps/admin/dist`。

## Android
### Release APK
```bash
cd apps/android
./gradlew :app:assembleRelease
```

## 更新紀錄
前台更新紀錄存放在 `apps/frontend/public/changelog.json`。

## CI / 測試
GitHub Actions 會在 PR 或推送 `main` 時執行：
- `functions-tests`：Functions 單元與 emulator e2e
- `frontend-tests`：前台 Vitest

## 注意事項
- API Key、密鑰與敏感資訊請放在本機或 CI secrets，不要提交到版本庫。
