@echo off
setlocal EnableDelayedExpansion

set TOOLCHAIN=0
set RCCETOOLS=1
set RCCE=1
set RUSTCLIENT=0

set "ROOTDIR=%~dp0"
if "%ROOTDIR:~-1%"=="\" set "ROOTDIR=%ROOTDIR:~0,-1%"

set "BLITZPATH=%ROOTDIR%\compiler\BlitzForge"

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
) else if "%1"=="-r" (
    set RUSTCLIENT=1
) else if "%1"=="--rust" (
    set RUSTCLIENT=1
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
echo -t ^| --skip-tools     Skip compilation of the RCCE2 tool applications in \src\Tools
echo -b ^| --blitz          Compile the BlitzForge toolchain
echo -e ^| --skip-engine    Skip compilation of the RCCE2 engine itself in \src
echo -r ^| --rust           Build the Rust client (client-rs) to bin\ClientRS.exe (needs cargo)
endlocal
exit /b

:end_args

if %TOOLCHAIN%==1 (
    echo Compiling BlitzForge Toolchain...
    call "%ROOTDIR%\scripts\submodules_init.bat" || (
        echo Failed to initialize submodules.
        endlocal
        exit /b 1
    )
    call "%BLITZPATH%\scripts\msbuild_init.bat"

    cd /d "%ROOTDIR%"

    call "%BLITZPATH%\scripts\msbuild_blitzforge.bat"
)

if %RCCE%==1 (
    if not exist "%BLITZPATH%\bin\blitzcc.exe" (
        echo "%BLITZPATH%\bin\blitzcc.exe not found!"
        echo "Compile source or download binaries from https://github.com/RydeTec/blitz-forge/releases"
        endlocal
        exit /b 1
    )

    echo Compiling RealmCrafter CE Engine...

    cd /d "%ROOTDIR%\src"

    "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Server.exe" "%ROOTDIR%\src\Server.bb" || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
    "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\Project Manager.exe" -n "%ROOTDIR%\res\Icon.ico" "%ROOTDIR%\src\Project Manager.bb" || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
    "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\GUE.exe" -n "%ROOTDIR%\res\Icon.ico" "%ROOTDIR%\src\GUE.bb" || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
    "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Loom.exe" -n "%ROOTDIR%\res\Icon.ico" "%ROOTDIR%\src\Loom.bb" || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
    "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\Client.exe" -n "%ROOTDIR%\res\Icon.ico" "%ROOTDIR%\src\Client.bb" || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
)

if %RCCETOOLS%==1 (
    if not exist "%BLITZPATH%\bin\blitzcc.exe" (
        echo "%BLITZPATH%\bin\blitzcc.exe not found!"
        echo "Compile source or download binaries from https://github.com/RydeTec/blitz-forge/releases"
        endlocal
        exit /b 1
    )

    echo Compiling RealmCrafter CE Tools...

    if not exist "%ROOTDIR%\bin\tools" (
        mkdir "%ROOTDIR%\bin\tools"
    )

    set "TOOLSDIR="
    if exist "%ROOTDIR%\src\Tools" (
        set "TOOLSDIR=%ROOTDIR%\src\Tools"
    ) else if exist "%ROOTDIR%\src\tools" (
        set "TOOLSDIR=%ROOTDIR%\src\tools"
    )

    if not defined TOOLSDIR (
        echo Tools directory not found. Expected src\Tools or src\tools.
        endlocal
        exit /b 1
    )

    cd /d "!TOOLSDIR!"

    for %%f in (*.bb) do (
        "%BLITZPATH%\bin\blitzcc.exe" -o "%ROOTDIR%\bin\tools\%%~nf.exe" -n "%ROOTDIR%\res\Icon.ico" -w "%ROOTDIR%\src" "!TOOLSDIR!\%%~nf.bb" || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
    )
)

if not %RUSTCLIENT%==1 goto skip_rust

echo Compiling RealmCrafter CE Rust client (client-rs)...
where cargo >nul 2>nul
if errorlevel 1 (
    echo   cargo not found on PATH -- install Rust from https://rustup.rs to build the Rust client. Skipping ClientRS.exe.
    goto skip_rust
)
cd /d "%ROOTDIR%\client-rs"
cargo build --release -p rcce-client --bin client-window || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
copy /Y "%ROOTDIR%\client-rs\target\release\client-window.exe" "%ROOTDIR%\bin\ClientRS.exe" >nul || (cd /d "%ROOTDIR%" & endlocal & exit /b 1)
echo   Built bin\ClientRS.exe
cd /d "%ROOTDIR%"

:skip_rust

cd /d "%ROOTDIR%"
endlocal
