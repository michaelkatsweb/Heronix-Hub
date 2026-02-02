@echo off
REM ============================================================
REM  Heronix Hub - Kiosk Mode Restore Script
REM  Run as Administrator to undo kiosk setup
REM ============================================================

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: This script must be run as Administrator.
    echo Right-click and select "Run as administrator".
    pause
    exit /b 1
)

echo ============================================================
echo  Heronix Hub - Restoring Normal Desktop
echo ============================================================
echo.
pause

REM --- Re-enable Task Manager ---
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\System" /v DisableTaskMgr /f 2>nul
echo [OK] Task Manager re-enabled

REM --- Re-enable Shut Down ---
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer" /v NoClose /f 2>nul
echo [OK] Shut Down option re-enabled

REM --- Re-enable Change Password ---
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\System" /v DisableChangePassword /f 2>nul
echo [OK] Change Password re-enabled

REM --- Re-enable Lock Workstation ---
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\System" /v DisableLockWorkstation /f 2>nul
echo [OK] Lock Workstation re-enabled

REM --- Remove Hub auto-start ---
reg delete "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v HeronixHub /f 2>nul
echo [OK] Hub auto-start removed

echo.
echo ============================================================
echo  Normal desktop restored. Restart to apply all changes.
echo ============================================================
pause
