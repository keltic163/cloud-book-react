param(
  [Parameter(Mandatory=$true)]
  [string]$projectId
)

# 用法：
# pwsh .\scripts\set-dev-key.ps1 -projectId your-firebase-project-id

Write-Host "Setting DEV_KEY_CODE secret (for Functions) in project: $projectId"

# 注意：請先登入 Firebase CLI 並確保已選擇正確專案
# The script will set the Functions secret DEV_KEY_CODE to the value below.
$devCode = '6yhn%TGB'

# Execute the firebase CLI command
$cmd = "firebase functions:secrets:set DEV_KEY_CODE --data \"$devCode\" --project $projectId"
Write-Host "Running: $cmd"
Invoke-Expression $cmd

Write-Host "Done. Verify with: firebase functions:secrets:list --project $projectId"