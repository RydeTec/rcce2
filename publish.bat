@echo off
setlocal

set "ROOTDIR=%~dp0"
if "%ROOTDIR:~-1%"=="\" set "ROOTDIR=%ROOTDIR:~0,-1%"

for %%A in (%*) do (
    if "%%~A"=="-h" set "HELP_ARG=%%~A"
    if "%%~A"=="--help" set "HELP_ARG=%%~A"
)
if defined HELP_ARG goto help

call "%ROOTDIR%\compile.bat" %* || (
    echo Compilation failed; aborting publish.
    endlocal
    exit /b 1
)
goto publish

:help
call "%ROOTDIR%\compile.bat" %HELP_ARG%
set "HELP_STATUS=%ERRORLEVEL%"
endlocal & exit /b %HELP_STATUS%

:publish
cd /d "%ROOTDIR%"

if exist "%ROOTDIR%\release" rmdir /S /Q "%ROOTDIR%\release"

mkdir "%ROOTDIR%\release"

call :CopyRequired /E /Y /I "%ROOTDIR%\bin" "%ROOTDIR%\release\bin" || exit /b 1
call :CopyRequired /E /Y /I "%ROOTDIR%\bin\ReShade.ini.example" "%ROOTDIR%\release\bin\ReShade.ini" || exit /b 1
call :CopyRequired /Y "%ROOTDIR%\Project Manager.exe" "%ROOTDIR%\release\" || exit /b 1
call :CopyRequired /E /Y /I "%ROOTDIR%\data" "%ROOTDIR%\release\data" || exit /b 1
call :CopyRequired /E /Y /I "%ROOTDIR%\res" "%ROOTDIR%\release\res" || exit /b 1
call :CopyRequired /E /Y /I "%ROOTDIR%\docs" "%ROOTDIR%\release\docs" || exit /b 1
call :CopyRequired /E /Y /I "%ROOTDIR%\extras\Freemake" "%ROOTDIR%\release\extras\Freemake" || exit /b 1

if exist "%ROOTDIR%\release\res\Recent.dat" del "%ROOTDIR%\release\res\Recent.dat"

endlocal
exit /b 0

:CopyRequired
xcopy %*
if errorlevel 1 (
    echo Copy failed; aborting publish.
    exit /b 1
)
exit /b 0
