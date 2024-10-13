@echo off
setlocal

set originalDir=%CD%
set scriptDir=%~dp0

cd %scriptDir%

call .\msbuild_init.bat

REM Navigate to the solution directory
cd ..\compiler\BlitzPlus\src

REM Rebuild the solution
msbuild blitzplus.sln /t:Rebuild /p:Configuration=Release-Deploy /p:Platform=x86

cd %originalDir%

endlocal