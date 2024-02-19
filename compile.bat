@echo off
set ROOTDIR=%CD%

cd %ROOTDIR%\src

set BLITZPATH=%ROOTDIR%\compiler\BlitzPlus

"%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Server.exe" "%ROOTDIR%\src\Server.bb"

set BLITZPATH=%ROOTDIR%\compiler\BlitzRC

"%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\Project Manager.exe" "%ROOTDIR%\src\Project Manager.bb"
"%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\GUE.exe" "%ROOTDIR%\src\GUE.bb"
"%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Client.exe" "%ROOTDIR%\src\Client.bb"

if not exist "%ROOTDIR%\bin\tools" mkdir "%ROOTDIR%\bin\tools"

cd %ROOTDIR%\src\tools

for %%f in (*.bb) do (
    "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\tools\%%~nf.exe" "%ROOTDIR%\src\tools\%%~nf.bb"
)

cd %ROOTDIR%
