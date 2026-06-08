@echo off
setlocal EnableDelayedExpansion

set "ROOTDIR=%~1"
set "COMPILERNAME=%~2"
set "SRCPATH=%~3"
set "SRCFILE=%~4"

set "BLITZPATH=%ROOTDIR%\compiler\%COMPILERNAME%"

if not exist "%BLITZPATH%\bin\blitzcc.exe" (
    echo "%BLITZPATH%\bin\blitzcc.exe not found!"
    echo "Compile source or download binaries from https://github.com/RydeTec/blitz-forge/releases"
    endlocal
    exit /b 1
)

cd /d "%SRCPATH%"

:: Shift past the first four positional arguments so %1 now refers to the first
:: trailing flag (e.g. -c, -d, -d -t).
shift
shift
shift
shift

:: Collect any remaining trailing flags into ARGS verbatim.
set "ARGS="

:loop
if "%~1"=="" goto endloop
set "ARGS=%ARGS% %1"
shift
goto loop

:endloop

"%BLITZPATH%\bin\blitzcc.exe"%ARGS% -w "%ROOTDIR%\src" "%SRCFILE%"
set "EXITCODE=%ERRORLEVEL%"

cd /d "%ROOTDIR%"

endlocal & exit /b %EXITCODE%
