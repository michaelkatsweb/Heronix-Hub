@echo off
echo ========================================
echo    Heronix-Hub Build and Run Script
echo ========================================
echo.

cd /d "%~dp0"

echo [1/3] Cleaning project...
call mvn clean
if %ERRORLEVEL% neq 0 (
    echo ERROR: Clean failed!
    pause
    exit /b %ERRORLEVEL%
)
echo.

echo [2/3] Compiling project...
call mvn compile
if %ERRORLEVEL% neq 0 (
    echo ERROR: Compile failed!
    pause
    exit /b %ERRORLEVEL%
)
echo.

echo [3/3] Running with JavaFX...
call mvn javafx:run
if %ERRORLEVEL% neq 0 (
    echo ERROR: JavaFX run failed!
    pause
    exit /b %ERRORLEVEL%
)

pause
