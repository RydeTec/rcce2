@echo off
setlocal

set "ROOTDIR=%~dp0"
if "%ROOTDIR:~-1%"=="\" set "ROOTDIR=%ROOTDIR:~0,-1%"

call "%ROOTDIR%\compile.bat" || (
    echo Compilation failed; aborting publish.
    endlocal
    exit /b 1
)

cd /d "%ROOTDIR%"

if exist "%ROOTDIR%\release" rmdir /S /Q "%ROOTDIR%\release"

mkdir "%ROOTDIR%\release"

xcopy /E /Y /I "%ROOTDIR%\bin" "%ROOTDIR%\release\bin"
xcopy /E /Y /I "%ROOTDIR%\bin\ReShade.ini.example" "%ROOTDIR%\release\bin\ReShade.ini"
xcopy /Y "%ROOTDIR%\Project Manager.exe" "%ROOTDIR%\release\"
xcopy /E /Y /I "%ROOTDIR%\data" "%ROOTDIR%\release\data"
xcopy /E /Y /I "%ROOTDIR%\res" "%ROOTDIR%\release\res"
xcopy /E /Y /I "%ROOTDIR%\docs" "%ROOTDIR%\release\docs"
xcopy /E /Y /I "%ROOTDIR%\extras\Freemake" "%ROOTDIR%\release\extras\Freemake"

if exist "%ROOTDIR%\release\res\Recent.dat" del "%ROOTDIR%\release\res\Recent.dat"

endlocal
