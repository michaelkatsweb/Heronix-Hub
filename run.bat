@echo off
REM Heronix-Hub Build and Run Script
REM © 2025 Heronix Education Systems LLC

echo ========================================
echo HERONIX-HUB LAUNCHER
echo ========================================
echo.

REM Check if Java is installed
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 or higher
    pause
    exit /b 1
)

REM Check if Maven is installed
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven 3.8 or higher
    pause
    exit /b 1
)

echo [1/3] Cleaning previous build...
call mvn clean

echo.
echo [2/3] Building application...
call mvn package -DskipTests

if errorlevel 1 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo.
echo [3/3] Starting Heronix-Hub...
echo.
echo ========================================
echo Application is running...
echo Default login: admin / admin123
echo Press Ctrl+C to stop
echo ========================================
echo.

java -jar target\heronix-hub-1.0.0.jar

pause
