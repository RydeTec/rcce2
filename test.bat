@echo off
setlocal

set ROOTDIR=%CD%

IF NOT EXIST "%ROOTDIR%\compiler\BlitzForge\bin\blitzcc.exe" (
    echo "%ROOTDIR%\compiler\BlitzForge\bin\blitzcc.exe not found!"
    echo "Compile source or download binaries from https://github.com/RydeTec/blitz-forge/releases"
    exit 1;
)

cd %ROOTDIR%\src\tests

set BLITZPATH=%ROOTDIR%\compiler\BlitzForge
set FAILED=0

for /R %%f in (*.bb) do (
    "%BLITZPATH%\bin\blitzcc.exe" -t -w "%ROOTDIR%\src" "%%f" || (echo "%%f failed at least one test" && SET FAILED=1)
)

cd %ROOTDIR%

if %FAILED% == 1 (
    echo "Tests failed"
    endlocal
    exit /b 1
)

echo "Tests passed"

endlocal