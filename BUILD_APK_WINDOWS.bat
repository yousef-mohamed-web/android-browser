@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0BUILD_APK_WINDOWS.ps1" %*
if errorlevel 1 (
  echo.
  echo APK build failed. Read the error above.
  pause
  exit /b 1
)
echo.
echo APK build finished. Check the dist-apk folder.
pause
