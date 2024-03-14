@echo off
set ROOTDIR=%CD%

call .\compile.bat

cd %ROOTDIR%

if exist "%ROOTDIR%\release" rmdir /S /Q "%ROOTDIR%\release"

mkdir "%ROOTDIR%\release"

xcopy /E /Y /I "%ROOTDIR%\bin" "%ROOTDIR%\release\bin"
xcopy /Y "%ROOTDIR%\Project Manager.exe" "%ROOTDIR%\release\"
xcopy /E /Y /I "%ROOTDIR%\data" "%ROOTDIR%\release\data"
xcopy /E /Y /I "%ROOTDIR%\res" "%ROOTDIR%\release\res"
xcopy /E /Y /I "%ROOTDIR%\docs" "%ROOTDIR%\release\docs"
xcopy /E /Y /I "%ROOTDIR%\extras\Freemake" "%ROOTDIR%\release\extras\Freemake"