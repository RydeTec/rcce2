@echo off
setlocal

set ROOTDIR=%CD%
set BLITZPATH=%ROOTDIR%\compiler\BlitzForge

REM Check if the submodule directory exists and is not empty
IF NOT EXIST "%BLITZPATH%\.git" (
    echo "BlitzForge not found, initializing and updating..."
    git submodule update --init --recursive
)

endlocal