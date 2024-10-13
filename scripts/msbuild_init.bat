@echo off
REM Check if msbuild is available
where msbuild >nul 2>&1

REM Check the exit code of the where command
if %ERRORLEVEL% neq 0 (
    REM Set up the Visual Studio environment
    call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvarsall.bat" x86
)