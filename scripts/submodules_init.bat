@echo off
setlocal EnableDelayedExpansion

:: Anchor on the script's own location so this works regardless of cwd.
:: %~dp0 resolves to ...\rcce2\scripts\, so step up one directory for ROOTDIR.
set "SCRIPTDIR=%~dp0"
if "%SCRIPTDIR:~-1%"=="\" set "SCRIPTDIR=%SCRIPTDIR:~0,-1%"
for %%I in ("%SCRIPTDIR%\..") do set "ROOTDIR=%%~fI"

set "BLITZPATH=%ROOTDIR%\compiler\BlitzForge"

REM Check if the submodule directory exists and is initialized.
if not exist "%BLITZPATH%\.git" (
    echo BlitzForge not found, initializing and updating submodules...
    pushd "%ROOTDIR%" >nul
    git submodule update --init --recursive
    set "GITRC=!ERRORLEVEL!"
    popd >nul
    if not "!GITRC!"=="0" (
        echo Failed to initialize submodules ^(exit code !GITRC!^).
        endlocal
        exit /b 1
    )
)

endlocal
exit /b 0
