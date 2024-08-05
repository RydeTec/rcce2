@echo off
setlocal

set originalDir=%CD%
set scriptDir=%~dp0

cd %scriptDir%

call .\msbuild_init.bat

REM Navigate to the solution directory
cd ..\compiler\BlitzRC\src\blitzrc

REM Rebuild the solution
msbuild blitzrc.sln /t:Rebuild /p:Configuration=BlitzRCDeploy /p:Platform=Win32

cd %originalDir%

endlocal