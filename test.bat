@echo off
set ROOTDIR=%CD%

cd %ROOTDIR%\src\tests

set BLITZPATH=%ROOTDIR%\compiler\BlitzRC
set FAILED=0

for /R %%f in (*.bb) do (
    "%BLITZPATH%\bin\blitzcc.exe" -t "%ROOTDIR%\src\tests\%%~nf.bb" || (echo "%ROOTDIR%\src\tests\%%~nf.bb failed at least one test" && SET FAILED=1)
)

cd %ROOTDIR%

if %FAILED% == 1 (
    echo "Tests failed"
    exit /b 1
)

echo "Tests passed"