@echo off
setlocal EnableDelayedExpansion

set TOOLCHAIN=0
set RCCETOOLS=1
set RCCE=1

set ROOTDIR=%CD%

:parse_args
if "%1"=="" goto end_args
if "%1"=="-b" (
    set TOOLCHAIN=1
) else if "%1"=="--blitz" (
    set TOOLCHAIN=1
) else if "%1"=="-t" (
    set RCCETOOLS=0
) else if "%1"=="--skip-tools" (
    set RCCETOOLS=0
) else if "%1"=="-h" (
    goto help_text
) else if "%1"=="--help" (
    goto help_text
) else if "%1"=="-e" (
    set RCCE=0
) else if "%1"=="--skip-engine" (
    set RCCE=0
) else (
    echo Unknown flag: %1
    endlocal
    exit /b 1
)
shift
goto parse_args

:help_text
echo RCCE2 Compiler Script
echo.
echo -t ^| --skip-tools     Skip compilation of the RCCE2 tool applications in \src\tools
echo -b ^| --blitz          Compile the full blitz toolchain
echo -e ^| --skip-engine    Skip compilation of the RCCE2 engine itself in \src
endlocal
exit /b

:end_args

if %TOOLCHAIN%==1 (
    echo Compiling Blitz Toolchain...
    call .\scripts\msbuild_init.bat

    cd %ROOTDIR%

    call .\scripts\msbuild_blitzrc.bat

    cd %ROOTDIR%

    call .\scripts\msbuild_blitzrcplus.bat
)

if %RCCE%==1 (
    echo Compiling RealmCrafter CE Engine...

    cd %ROOTDIR%\src

    set BLITZPATH=%ROOTDIR%\compiler\BlitzPlus

    "!BLITZPATH!\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Server.exe" "%ROOTDIR%\src\Server.bb"

    set BLITZPATH=%ROOTDIR%\compiler\BlitzRC

    "!BLITZPATH!\bin\blitzcc.exe" -o "%ROOTDIR%\Project Manager.exe" "%ROOTDIR%\src\Project Manager.bb"
    "!BLITZPATH!\bin\blitzcc.exe" -o "%ROOTDIR%\bin\GUE.exe" "%ROOTDIR%\src\GUE.bb"
    "!BLITZPATH!\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Client.exe" "%ROOTDIR%\src\Client.bb"
)

if %RCCETOOLS%==1 (
    echo Compiling RealmCrafter CE Tools...

    if not exist "%ROOTDIR%\bin\tools" (
        mkdir "%ROOTDIR%\bin\tools"
    )

    cd %ROOTDIR%\src\tools

    set "BLITZPATH=%ROOTDIR%\compiler\BlitzRC"

    for %%f in (*.bb) do (
        "!BLITZPATH!\bin\blitzcc.exe" -o "%ROOTDIR%\bin\tools\%%~nf.exe" "%ROOTDIR%\src\tools\%%~nf.bb"
    )
)

cd %ROOTDIR%
endlocal