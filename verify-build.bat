@echo off
REM Heronix-Hub Build Verification Script
REM Verifies all required files are in place before building

echo ========================================
echo HERONIX-HUB BUILD VERIFICATION
echo ========================================
echo.

SET ERRORS=0

echo Checking project structure...
echo.

REM Check core files
echo [1/5] Checking Maven configuration...
if not exist "pom.xml" (
    echo   ERROR: pom.xml not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: pom.xml found
)

echo.
echo [2/5] Checking Java source files...
if not exist "src\main\java\com\heronixedu\hub\HubApplication.java" (
    echo   ERROR: HubApplication.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: HubApplication.java found
)

if not exist "src\main\java\com\heronixedu\hub\model\User.java" (
    echo   ERROR: User.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: User.java found
)

if not exist "src\main\java\com\heronixedu\hub\model\Product.java" (
    echo   ERROR: Product.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: Product.java found
)

if not exist "src\main\java\com\heronixedu\hub\service\TokenService.java" (
    echo   ERROR: TokenService.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: TokenService.java found
)

if not exist "src\main\java\com\heronixedu\hub\service\AuthenticationService.java" (
    echo   ERROR: AuthenticationService.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: AuthenticationService.java found
)

if not exist "src\main\java\com\heronixedu\hub\service\ProductLauncherService.java" (
    echo   ERROR: ProductLauncherService.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: ProductLauncherService.java found
)

if not exist "src\main\java\com\heronixedu\hub\controller\LoginController.java" (
    echo   ERROR: LoginController.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: LoginController.java found
)

if not exist "src\main\java\com\heronixedu\hub\controller\DashboardController.java" (
    echo   ERROR: DashboardController.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: DashboardController.java found
)

if not exist "src\main\java\com\heronixedu\hub\util\TokenReader.java" (
    echo   ERROR: TokenReader.java not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: TokenReader.java found
)

echo.
echo [3/5] Checking resource files...
if not exist "src\main\resources\application.yml" (
    echo   ERROR: application.yml not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: application.yml found
)

if not exist "src\main\resources\schema.sql" (
    echo   ERROR: schema.sql not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: schema.sql found
)

if not exist "src\main\resources\data.sql" (
    echo   ERROR: data.sql not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: data.sql found
)

if not exist "src\main\resources\logback.xml" (
    echo   ERROR: logback.xml not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: logback.xml found
)

echo.
echo [4/5] Checking UI files...
if not exist "src\main\resources\fxml\login.fxml" (
    echo   ERROR: login.fxml not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: login.fxml found
)

if not exist "src\main\resources\fxml\dashboard.fxml" (
    echo   ERROR: dashboard.fxml not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: dashboard.fxml found
)

if not exist "src\main\resources\css\hub-style.css" (
    echo   ERROR: hub-style.css not found
    SET /A ERRORS=%ERRORS%+1
) else (
    echo   OK: hub-style.css found
)

echo.
echo [5/5] Checking documentation...
if not exist "README.md" (
    echo   WARNING: README.md not found
) else (
    echo   OK: README.md found
)

if not exist "QUICKSTART.md" (
    echo   WARNING: QUICKSTART.md not found
) else (
    echo   OK: QUICKSTART.md found
)

echo.
echo ========================================
if %ERRORS%==0 (
    echo VERIFICATION PASSED!
    echo All required files are present.
    echo You can now build the project with: run.bat
) else (
    echo VERIFICATION FAILED!
    echo Found %ERRORS% error(s).
    echo Please check missing files above.
)
echo ========================================
echo.

pause
