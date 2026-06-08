@echo off
setlocal enabledelayedexpansion

set "ROOTDIR=%~dp0"
if "%ROOTDIR:~-1%"=="\" set "ROOTDIR=%ROOTDIR:~0,-1%"

set "BLITZPATH=%ROOTDIR%\compiler\BlitzForge"
set "TESTDIR="

if exist "%ROOTDIR%\src\Tests" (
    set "TESTDIR=%ROOTDIR%\src\Tests"
) else if exist "%ROOTDIR%\src\tests" (
    set "TESTDIR=%ROOTDIR%\src\tests"
)

if not exist "%BLITZPATH%\bin\blitzcc.exe" (
    echo "%BLITZPATH%\bin\blitzcc.exe not found!"
    echo "Compile source or download binaries from https://github.com/RydeTec/blitz-forge/releases"
    exit /b 1
)

if not defined TESTDIR (
    echo "Test directory not found. Expected src\Tests or src\tests."
    exit /b 1
)

REM Optional positional arg = substring filter on the test file basename.
REM e.g. `test.bat ItemsTest` runs only files matching *ItemsTest*.bb.
REM Useful for reproducing the documented intermittent ItemsTest flake
REM locally without re-running the whole suite.
set "FILTER=%~1"
if defined FILTER (
    set "GLOB=*!FILTER!*.bb"
    echo Filter: only files matching !GLOB!
) else (
    set "GLOB=*.bb"
)

cd /d "%TESTDIR%"

set /a TOTAL=0
set /a PASSED=0
set /a FAILED=0
set "FAILED_FILES="

for /R %%f in (!GLOB!) do (
    set /a TOTAL+=1
    echo [RUN ] %%~nxf
    "%BLITZPATH%\bin\blitzcc.exe" -t -w "%ROOTDIR%\src" "%%f"
    if !errorlevel! equ 0 (
        echo [PASS] %%~nxf
        set /a PASSED+=1
    ) else (
        echo [FAIL] %%~nxf
        set /a FAILED+=1
        set "FAILED_FILES=!FAILED_FILES! %%~nxf"
    )
)

cd /d "%ROOTDIR%"

if !TOTAL! equ 0 (
    if defined FILTER (
        echo No test files matched filter "!FILTER!"
    ) else (
        echo No test files found in "%TESTDIR%"
    )
    endlocal
    exit /b 1
)

echo.
echo Ran !TOTAL! files: !PASSED! passed, !FAILED! failed.

if !FAILED! gtr 0 (
    echo Failed files:
    for %%x in (!FAILED_FILES!) do echo   - %%x
    echo "Tests failed"
    endlocal
    exit /b 1
)

echo "Tests passed"

endlocal
