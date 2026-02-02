@echo off
REM ============================================================
REM  Heronix Hub - Kiosk Mode Setup Script
REM  Run as Administrator on student terminals
REM ============================================================

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: This script must be run as Administrator.
    echo Right-click and select "Run as administrator".
    pause
    exit /b 1
)

echo ============================================================
echo  Heronix Hub - Kiosk Mode Setup
echo ============================================================
echo.
echo This will configure this computer as a kiosk terminal:
echo  - Disable Task Manager
echo  - Disable Shut Down option
echo  - Set Heronix Hub to auto-start on login
echo  - (Optional) Replace Explorer shell with Hub
echo.
echo Press Ctrl+C to cancel, or
pause

REM --- Disable Task Manager ---
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\System" /v DisableTaskMgr /t REG_DWORD /d 1 /f
echo [OK] Task Manager disabled

REM --- Disable Shut Down from Start Menu ---
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\Explorer" /v NoClose /t REG_DWORD /d 1 /f
echo [OK] Shut Down option disabled

REM --- Disable Change Password from Ctrl+Alt+Delete ---
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\System" /v DisableChangePassword /t REG_DWORD /d 1 /f
echo [OK] Change Password disabled

REM --- Disable Lock Workstation from Ctrl+Alt+Delete ---
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Policies\System" /v DisableLockWorkstation /t REG_DWORD /d 1 /f
echo [OK] Lock Workstation disabled

REM --- Auto-start Hub on login ---
set HUB_PATH=%~dp0..\target\heronix-hub-1.0.0.jar
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run" /v HeronixHub /t REG_SZ /d "javaw -jar \"%HUB_PATH%\"" /f
echo [OK] Hub set to auto-start on login

echo.
echo ============================================================
echo  Setup complete. Restart the computer to apply all changes.
echo ============================================================
pause
