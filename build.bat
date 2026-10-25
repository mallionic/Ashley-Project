@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem =============================================================================
rem ashc SDK builder - REAL native Windows binary with GraalVM native-image
rem CMD / Windows version
rem =============================================================================

set "NAME=ashc"

rem =============================================================================
rem Root directory
rem =============================================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

set "OUT=%ROOT%\out"
set "CLASSES=%OUT%\classes"
set "DIST=%OUT%\dist"

rem =============================================================================
rem Version
rem =============================================================================

if not defined VERSION (
    if exist "%ROOT%\VERSION" (
        set /p VERSION=<"%ROOT%\VERSION"
    ) else (
        set "VERSION=1.0.0"
    )
)

if not defined MAIN_CLASS set "MAIN_CLASS="
if not defined FAST_INIT set "FAST_INIT=0"
if not defined QUICK set "QUICK=0"
if not defined PREFIX set "PREFIX=%USERPROFILE%\.local"
if not defined VERBOSE set "VERBOSE=0"

set "JAR=%OUT%\%NAME%-%VERSION%.jar"
set "BIN=%OUT%\%NAME%.exe"
set "NI_LOG=%OUT%\native-image.log"
set "SDK=%DIST%\%NAME%-%VERSION%"
set "ARCHIVE=%OUT%\%NAME%-%VERSION%-windows-%PROCESSOR_ARCHITECTURE%.zip"

set "CMD="

rem =============================================================================
rem IMPORTANT:
rem Jump over all subroutines to the real entry point.
rem Without this, CMD would execute :step immediately and exit.
rem =============================================================================

goto main


rem =============================================================================
rem Output helpers
rem =============================================================================

:step
echo.
echo ==^> %~1
exit /b 0

:info
echo   %~1
exit /b 0

:ok
echo   [ OKAY ] %~1
exit /b 0

:fault
echo   [ FAULT ] %~1
exit /b 0

:warn
echo   [ WARN ] %~1
exit /b 0

:die
echo   [ FAIL ] %~1
exit /b 1


rem =============================================================================
rem Usage
rem =============================================================================

:usage
echo Usage: build.bat [command] [options]
echo.
echo Commands:
echo   all        clean + compile + jar + native + test + sdk
echo   clean      remove out\
echo   compile    compile Java sources
echo   jar        build out\%NAME%-^<version^>.jar
echo   native     build the native Windows binary with GraalVM native-image
echo   agent      generate native-image reflection configuration
echo   test       run the native smoke test with test.ash
echo   sdk        assemble the SDK and compressed ZIP archive
echo   install    install the SDK into --prefix
echo   deps       check required dependencies
echo   version    print version
echo   help       print this help
echo.
echo Options:
echo   --prefix DIR       installation prefix
echo   --prefix=DIR       installation prefix
echo   --quick            use faster native compilation (-Ob)
echo   --fast-init        initialize selected classes at build time
echo   -v, --verbose      list Java sources
echo   -V, --version      print version
echo   -h, --help         print this help
echo.
echo Environment:
echo   VERSION
echo   MAIN_CLASS
echo   QUICK
echo   FAST_INIT
echo   PREFIX
echo   VERBOSE
exit /b 0


rem =============================================================================
rem Argument parser
rem =============================================================================

:parse_args

if "%~1"=="" goto args_done

if /I "%~1"=="-h" (
    set "CMD=help"
    shift
    goto parse_args
)

if /I "%~1"=="--help" (
    set "CMD=help"
    shift
    goto parse_args
)

if /I "%~1"=="-V" (
    set "CMD=version"
    shift
    goto parse_args
)

if /I "%~1"=="--version" (
    set "CMD=version"
    shift
    goto parse_args
)

if /I "%~1"=="--prefix" (
    if "%~2"=="" (
        echo --prefix requires a directory
        exit /b 2
    )

    set "PREFIX=%~2"
    shift
    shift
    goto parse_args
)

set "ARG=%~1"

if /I "!ARG:~0,9!"=="--prefix=" (
    set "PREFIX=!ARG:~9!"
    shift
    goto parse_args
)

if /I "%~1"=="--quick" (
    set "QUICK=1"
    shift
    goto parse_args
)

if /I "%~1"=="--fast-init" (
    set "FAST_INIT=1"
    shift
    goto parse_args
)

if /I "%~1"=="-v" (
    set "VERBOSE=1"
    shift
    goto parse_args
)

if /I "%~1"=="--verbose" (
    set "VERBOSE=1"
    shift
    goto parse_args
)

if "!ARG:~0,1!"=="-" (
    echo Unknown option: %~1
    exit /b 2
)

if not defined CMD (
    set "CMD=%~1"
) else (
    echo Only one command is allowed: !CMD! / %~1
    exit /b 2
)

shift
goto parse_args

:args_done

if not defined CMD set "CMD=all"

exit /b 0


rem =============================================================================
rem Check command
rem =============================================================================

:check_command

where "%~1" >nul 2>&1

if errorlevel 1 (
    call :fault "%~2: %~1"
    set "DEPENDENCY_FAILURE=1"
    exit /b 1
)

call :ok "%~2: %~1"
exit /b 0


rem =============================================================================
rem GraalVM / Java dependencies
rem =============================================================================

:check_graalvm

call :check_command java "Java runtime"
call :check_command javac "Java compiler"
call :check_command jar "Java archive tool"
call :check_command native-image "GraalVM Native Image"

if defined JAVA_HOME (
    echo   JAVA_HOME: %JAVA_HOME%
) else (
    call :warn "JAVA_HOME is not defined"
)

where java >nul 2>&1

if not errorlevel 1 (
    for /f "tokens=*" %%A in ('java -version 2^>^&1') do (
        echo   Java: %%A
        goto graal_java_done
    )
)

:graal_java_done

where native-image >nul 2>&1

if not errorlevel 1 (
    for /f "tokens=*" %%A in ('native-image --version 2^>^&1') do (
        echo   Native Image: %%A
        goto graal_native_done
    )
)

:graal_native_done

exit /b 0


rem =============================================================================
rem Dependencies
rem =============================================================================

:check_dependencies

call :step "Checking required Windows dependencies"

set "DEPENDENCY_FAILURE=0"

echo.

call :check_graalvm

echo.

rem =============================================================================
rem Microsoft C compiler
rem =============================================================================

where cl >nul 2>&1

if errorlevel 1 (
    call :warn "MSVC cl.exe not found in current CMD environment"
    echo   GraalVM Native Image normally requires the Microsoft C/C++ toolchain.
    echo   Use a Visual Studio Developer Command Prompt when building natively.
) else (
    call :ok "Microsoft C/C++ compiler: cl.exe"
)

rem =============================================================================
rem Optional Git
rem =============================================================================

where git >nul 2>&1

if errorlevel 1 (
    call :warn "Git: git not installed (optional)"
) else (
    call :ok "Git: git"
)

echo.

if "%DEPENDENCY_FAILURE%"=="0" (
    call :ok "Required Java/GraalVM dependencies are available."
    exit /b 0
)

call :fault "One or more required dependencies are missing."
exit /b 1


rem =============================================================================
rem Dependency message
rem =============================================================================

:install_dependencies

echo.
echo Required dependencies are missing.
echo.
echo Required software:
echo   - GraalVM JDK 25
echo   - javac
echo   - jar
echo   - native-image
echo   - Microsoft Visual C++ Build Tools
echo.
echo No PowerShell is required by this build script.
echo.
echo Configure the Windows Java/GraalVM/MSVC environment and run:
echo.
echo   build.bat deps
echo.
exit /b 1


rem =============================================================================
rem Ensure dependencies
rem =============================================================================

:ensure_dependencies

call :check_dependencies

if errorlevel 1 (
    call :install_dependencies
    exit /b 1
)

exit /b 0


rem =============================================================================
rem Java information
rem =============================================================================

:java_banner

where java >nul 2>&1

if errorlevel 1 (
    echo unavailable
    exit /b 0
)

for /f "tokens=*" %%A in ('java -version 2^>^&1') do (
    echo %%A
    exit /b 0
)

echo unknown
exit /b 0


rem =============================================================================
rem Clean
rem =============================================================================

:do_clean

call :step "Cleaning output directory"

if exist "%OUT%" (
    rmdir /S /Q "%OUT%"
)

mkdir "%OUT%" >nul 2>&1

if not exist "%OUT%" (
    call :die "Unable to create output directory."
    exit /b 1
)

call :ok "%OUT%"
exit /b 0


rem =============================================================================
rem Java compilation
rem =============================================================================

:do_compile

where javac >nul 2>&1

if errorlevel 1 (
    call :die "javac is required."
    exit /b 1
)

call :step "Embedding nlib into the compiler"

java "%ROOT%\tools\EmbedNlib.java" "%ROOT%\nlib" "%ROOT%\Builder\ash\NlibEmbedded.java"
if errorlevel 1 (
    call :die "nlib embedding failed."
    exit /b 1
)

call :step "Searching Java sources"

if exist "%CLASSES%" (
    rmdir /S /Q "%CLASSES%"
)

mkdir "%CLASSES%" >nul 2>&1

set "SRC_LIST=%OUT%\sources.txt"

if exist "%SRC_LIST%" (
    del /Q "%SRC_LIST%"
)

rem =============================================================================
rem Find Java files using CMD
rem
rem javac @argfile parsing can mishandle Windows backslashes in paths.
rem Write Java source paths using forward slashes instead.
rem =============================================================================

for /R "%ROOT%" %%F in (*.java) do (
    set "JAVA_FILE=%%~fF"
    set "JAVA_FILE=!JAVA_FILE:\=/!"
    echo "!JAVA_FILE!">>"%SRC_LIST%"
)


if not exist "%SRC_LIST%" (
    call :die "No .java files found."
    exit /b 1
)

set "COUNT=0"

for /f %%A in ('find /v /c "" ^< "%SRC_LIST%"') do (
    set "COUNT=%%A"
)

if "%COUNT%"=="0" (
    call :die "No .java files found."
    exit /b 1
)

call :ok "Java sources found: %COUNT%"

if "%VERBOSE%"=="1" (
    echo.
    for /f "usebackq delims=" %%F in ("%SRC_LIST%") do (
        echo     %%~F
    )
)

call :step "Compiling Java sources"

javac ^
    -encoding UTF-8 ^
    -XDstringConcat=inline ^
    -nowarn ^
    -d "%CLASSES%" ^
    @"%SRC_LIST%"

if errorlevel 1 (
    call :die "javac compilation failed."
    exit /b 1
)

call :ok "%CLASSES%"

exit /b 0


rem =============================================================================
rem Ensure classes
rem =============================================================================

:ensure_classes

if not exist "%CLASSES%" (
    call :do_compile
    exit /b %errorlevel%
)

dir /B "%CLASSES%" >nul 2>&1

if errorlevel 1 (
    call :do_compile
    exit /b %errorlevel%
)

exit /b 0


rem =============================================================================
rem Main class detection
rem =============================================================================

:detect_main

if defined MAIN_CLASS (
    exit /b 0
)

if exist "%CLASSES%\arch\Main.class" (
    set "MAIN_CLASS=arch.Main"
    exit /b 0
)

if exist "%CLASSES%\Builder\Main.class" (
    set "MAIN_CLASS=Builder.Main"
    exit /b 0
)

if exist "%CLASSES%\Main.class" (
    set "MAIN_CLASS=Main"
    exit /b 0
)

echo.
echo Main.class candidates:

for /R "%CLASSES%" %%F in (Main.class) do (
    echo   %%F
)

call :die "No Main class found. Use MAIN_CLASS=package.Main."
exit /b 1


rem =============================================================================
rem JAR
rem =============================================================================

:do_jar

call :ensure_classes

if errorlevel 1 (
    exit /b 1
)

where jar >nul 2>&1

if errorlevel 1 (
    call :die "jar is required."
    exit /b 1
)

call :detect_main

if errorlevel 1 (
    exit /b 1
)

call :step "Creating JAR"

set "MF=%OUT%\MANIFEST.MF"

(
    echo Manifest-Version: 1.0
    echo Implementation-Title: %NAME%
    echo Implementation-Version: %VERSION%
) > "%MF%"

jar ^
    --create ^
    --file "%JAR%" ^
    --manifest "%MF%" ^
    --main-class "%MAIN_CLASS%" ^
    -C "%CLASSES%" .

if errorlevel 1 (
    del /Q "%MF%" >nul 2>&1
    call :die "JAR creation failed."
    exit /b 1
)

del /Q "%MF%" >nul 2>&1

call :ok "%JAR%"
call :info "Main class: %MAIN_CLASS%"

exit /b 0


rem =============================================================================
rem Ensure JAR
rem =============================================================================

:ensure_jar

if not exist "%JAR%" (
    call :do_jar
    exit /b %errorlevel%
)

exit /b 0


rem =============================================================================
rem Native Image preflight
rem =============================================================================

:preflight_native

call :step "Checking native build toolchain"

where native-image >nul 2>&1

if errorlevel 1 (
    call :die "native-image is not available."
    exit /b 1
)

for /f "tokens=*" %%A in ('native-image --version 2^>^&1') do (
    call :ok "native-image: %%A"
    goto native_checked
)

:native_checked

where cl >nul 2>&1

if errorlevel 1 (
    call :die "MSVC cl.exe is required for native Windows compilation."
    exit /b 1
)

call :ok "MSVC C compiler: cl.exe"

exit /b 0


rem =============================================================================
rem Native Image
rem =============================================================================

:do_native

call :ensure_jar

if errorlevel 1 (
    exit /b 1
)

call :preflight_native

if errorlevel 1 (
    exit /b 1
)

call :step "Building native Windows binary with GraalVM native-image"

if exist "%BIN%" (
    del /Q "%BIN%"
)

if exist "%NI_LOG%" (
    del /Q "%NI_LOG%"
)

set "NI_ARGS=-jar "%JAR%" -o "%BIN%" --no-fallback -march=compatibility -H:+ReportExceptionStackTraces"

if "%QUICK%"=="1" (
    set "NI_ARGS=%NI_ARGS% -Ob"
)

if "%FAST_INIT%"=="1" (
    set "NI_ARGS=%NI_ARGS% --initialize-at-build-time=Builder,arch"
)

if exist "%ROOT%\native-config" (
    set "NI_ARGS=%NI_ARGS% -H:ConfigurationFileDirectories=%ROOT%\native-config"
    call :info "Reflection configuration: %ROOT%\native-config"
)

call :info "Starting native-image..."

native-image -jar "Z:\out\ashc-1.0.0.jar" -o "Z:\out\ashc.exe" --no-fallback -march=compatibility -H:+ReportExceptionStackTraces

if errorlevel 1 (
    echo.
    echo Last native-image output:
    echo ----------------------------------------
    if exist "%NI_LOG%" (
        type "%NI_LOG%"
    )
    echo ----------------------------------------
    call :die "native-image failed. Full log: %NI_LOG%"
    exit /b 1
)

call :assert_native

if errorlevel 1 (
    exit /b 1
)

call :ok "%BIN%"

for %%A in ("%BIN%") do (
    call :info "Native Windows executable size: %%~zA bytes"
)

exit /b 0


rem =============================================================================
rem Native Windows validation
rem =============================================================================

:assert_native

if not exist "%BIN%" (
    call :die "Native executable was not generated: %BIN%"
    exit /b 1
)

rem =============================================================================
rem PE executable validation
rem =============================================================================

where certutil >nul 2>&1

if errorlevel 1 (
    call :warn "certutil not found; skipping PE signature validation."
    exit /b 0
)

certutil -dump "%BIN%" 2>nul | findstr /I /R /C:"^ *0000.*4D 5A" >nul

if errorlevel 1 (
    call :die "The output does not appear to be a native Windows PE executable."
    exit /b 1
)

call :ok "Valid Windows PE executable: %BIN%"

exit /b 0


rem =============================================================================
rem Ensure native binary
rem =============================================================================

:ensure_bin

if not exist "%BIN%" (
    call :do_native
    exit /b %errorlevel%
)

call :assert_native

exit /b %errorlevel%


rem =============================================================================
rem Smoke test
rem =============================================================================

:do_test

call :ensure_bin

if errorlevel 1 (
    exit /b 1
)

call :step "Running native smoke test"

if not exist "%ROOT%\test.ash" (
    call :warn "test.ash not found."
    exit /b 1
)

set "TMPD=%TEMP%\ashc-test-%RANDOM%-%RANDOM%"

mkdir "%TMPD%" >nul 2>&1

if not exist "%TMPD%" (
    call :die "Unable to create temporary test directory."
    exit /b 1
)

pushd "%TMPD%"

"%BIN%" ^
    "%ROOT%\test.ash" ^
    --compile ^
    --elf ^
    > "%TMPD%\log.txt" 2>&1

set "TEST_RESULT=%ERRORLEVEL%"

if "%TEST_RESULT%"=="0" (
    dir /S /B "%TMPD%\*.o" >nul 2>&1

    if not errorlevel 1 (
        popd
        rmdir /S /Q "%TMPD%"
        call :ok "Native smoke test passed."
        exit /b 0
    )

    rem Also accept Windows-style object files if the compiler emits them.
    dir /S /B "%TMPD%\*.obj" >nul 2>&1

    if not errorlevel 1 (
        popd
        rmdir /S /Q "%TMPD%"
        call :ok "Native smoke test passed."
        exit /b 0
    )
)

echo.
echo Smoke-test output:
echo ----------------------------------------

if exist "%TMPD%\log.txt" (
    type "%TMPD%\log.txt"
)

echo ----------------------------------------

popd
rmdir /S /Q "%TMPD%"

call :warn "Native smoke test failed."

exit /b 1


rem =============================================================================
rem Native Image agent
rem =============================================================================

:do_agent

call :ensure_jar

if errorlevel 1 (
    exit /b 1
)

call :step "Generating reflection configuration"

if not exist "%ROOT%\native-config" (
    mkdir "%ROOT%\native-config"
)

if exist "%ROOT%\test.ash" (
    call :agent_file "%ROOT%\test.ash"
)

for /R "%ROOT%" %%F in (*.ash) do (
    call :agent_file "%%F"
)

call :ok "%ROOT%\native-config"

exit /b 0


:agent_file

if not exist "%~1" (
    exit /b 0
)

set "AGENT_TMP=%TEMP%\ashc-agent-%RANDOM%-%RANDOM%"

mkdir "%AGENT_TMP%" >nul 2>&1

if not exist "%AGENT_TMP%" (
    exit /b 0
)

call :info "%~1"

pushd "%AGENT_TMP%"

java ^
    -agentlib:native-image-agent=config-merge-dir="%ROOT%\native-config" ^
    -jar "%JAR%" ^
    "%~1" ^
    --compile ^
    --elf ^
    > "%AGENT_TMP%\agent.log" 2>&1

if errorlevel 1 (
    type "%AGENT_TMP%\agent.log"
    popd
    rmdir /S /Q "%AGENT_TMP%"
    call :warn "Agent execution failed: %~1"
    exit /b 0
)

popd

rmdir /S /Q "%AGENT_TMP%"

exit /b 0


rem =============================================================================
rem SDK packaging
rem =============================================================================

:do_sdk

call :ensure_jar

if errorlevel 1 (
    exit /b 1
)

call :ensure_bin

if errorlevel 1 (
    exit /b 1
)

call :step "Packaging SDK"

if exist "%SDK%" (
    rmdir /S /Q "%SDK%"
)

mkdir "%SDK%\bin"
mkdir "%SDK%\lib"
mkdir "%SDK%\share\doc\%NAME%"
mkdir "%SDK%\share\examples"

copy /Y "%BIN%" "%SDK%\bin\%NAME%.exe" >nul

if errorlevel 1 (
    call :die "Unable to copy native executable."
    exit /b 1
)

copy /Y "%JAR%" "%SDK%\lib\" >nul

if errorlevel 1 (
    call :die "Unable to copy JAR."
    exit /b 1
)

rem =============================================================================
rem Documentation
rem =============================================================================

for %%F in (
    "%ROOT%\README*"
    "%ROOT%\LICENSE*"
    "%ROOT%\NOTICE*"
    "%ROOT%\CHANGELOG*"
) do (
    if exist "%%~F" (
        copy /Y "%%~F" "%SDK%\share\doc\%NAME%\" >nul
    )
)

rem =============================================================================
rem ASH examples
rem =============================================================================

for %%F in ("%ROOT%\*.ash") do (
    if exist "%%~F" (
        copy /Y "%%~F" "%SDK%\share\examples\" >nul
    )
)

if exist "%ROOT%\examples" (
    for %%F in ("%ROOT%\examples\*.ash") do (
        if exist "%%~F" (
            copy /Y "%%~F" "%SDK%\share\examples\" >nul
        )
    )
)

rem =============================================================================
rem Git commit
rem =============================================================================

set "COMMIT=unknown"

where git >nul 2>&1

if not errorlevel 1 (
    for /f "tokens=*" %%A in ('git -C "%ROOT%" rev-parse --short HEAD 2^>nul') do (
        set "COMMIT=%%A"
    )
)

rem =============================================================================
rem VERSION
rem =============================================================================

> "%SDK%\VERSION" echo %VERSION%

rem =============================================================================
rem sdk.properties
rem =============================================================================

set "GRAAL_VERSION=unknown"

where native-image >nul 2>&1

if not errorlevel 1 (
    for /f "tokens=*" %%A in ('native-image --version 2^>^&1') do (
        set "GRAAL_VERSION=%%A"
        goto graal_properties_done
    )
)

:graal_properties_done

(
    echo name=%NAME%
    echo version=%VERSION%
    echo platform=windows-%PROCESSOR_ARCHITECTURE%
    echo mode=native
    echo commit=%COMMIT%
    echo built=%DATE% %TIME%
    echo graalvm=%GRAAL_VERSION%
) > "%SDK%\sdk.properties"

rem =============================================================================
rem ZIP archive
rem =============================================================================

if exist "%ARCHIVE%" (
    del /Q "%ARCHIVE%"
)

where tar >nul 2>&1

if not errorlevel 1 (
    tar -a -c -f "%ARCHIVE%" -C "%DIST%" "%NAME%-%VERSION%"
) else (
    call :warn "tar.exe is unavailable; ZIP archive was not created."
)

if exist "%ARCHIVE%" (
    call :ok "%ARCHIVE%"
)

call :ok "%SDK%"

exit /b 0


rem =============================================================================
rem Install
rem =============================================================================

:do_install

if not exist "%SDK%" (
    call :do_sdk

    if errorlevel 1 (
        exit /b 1
    )
)

call :step "Installing SDK"

if not exist "%PREFIX%" (
    mkdir "%PREFIX%"
)

if not exist "%PREFIX%\bin" (
    mkdir "%PREFIX%\bin"
)

if not exist "%PREFIX%\lib" (
    mkdir "%PREFIX%\lib"
)

if not exist "%PREFIX%\share" (
    mkdir "%PREFIX%\share"
)

copy /Y ^
    "%SDK%\bin\%NAME%.exe" ^
    "%PREFIX%\bin\" >nul

if errorlevel 1 (
    call :die "Unable to install executable."
    exit /b 1
)

copy /Y ^
    "%SDK%\lib\*" ^
    "%PREFIX%\lib\" >nul

xcopy ^
    "%SDK%\share" ^
    "%PREFIX%\share" ^
    /E /I /Y >nul

call :ok "%PREFIX%\bin\%NAME%.exe"

echo.
echo To add the SDK bin directory to the user PATH:
echo.
echo   setx PATH "%%PATH%%;%PREFIX%\bin"
echo.

exit /b 0


rem =============================================================================
rem Header
rem =============================================================================

:header

echo ========================================
echo   %NAME% %VERSION% - Native SDK Builder
echo ========================================
echo   Root     : %ROOT%
echo   Output   : %OUT%
echo   Platform : windows-%PROCESSOR_ARCHITECTURE%
echo   Java     :
call :java_banner
echo.

exit /b 0


rem =============================================================================
rem Build summary
rem =============================================================================

:summary

echo.
echo ========================================
echo   BUILD COMPLETE
echo ========================================
echo   JAR      : %JAR%
echo   Binary   : %BIN%

if exist "%BIN%" (
    for %%A in ("%BIN%") do (
        echo   Size     : %%~zA bytes
    )
)

echo   SDK      : %SDK%
echo   Archive  : %ARCHIVE%
echo.
echo   Usage:
echo     out\%NAME%.exe test.ash --compile --elf
echo.

exit /b 0


rem =============================================================================
rem Dispatch
rem =============================================================================

:dispatch

if /I "%CMD%"=="all" goto command_all
if /I "%CMD%"=="clean" goto command_clean
if /I "%CMD%"=="deps" goto command_deps
if /I "%CMD%"=="compile" goto command_compile
if /I "%CMD%"=="jar" goto command_jar
if /I "%CMD%"=="native" goto command_native
if /I "%CMD%"=="agent" goto command_agent
if /I "%CMD%"=="test" goto command_test
if /I "%CMD%"=="sdk" goto command_sdk
if /I "%CMD%"=="install" goto command_install
if /I "%CMD%"=="version" goto command_version
if /I "%CMD%"=="help" goto command_help

echo Unknown command: %CMD%
echo.

call :usage

exit /b 2


rem =============================================================================
rem Commands
rem =============================================================================

:command_all

call :header

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_clean
if errorlevel 1 exit /b 1

call :do_compile
if errorlevel 1 exit /b 1

call :do_jar
if errorlevel 1 exit /b 1

call :do_native
if errorlevel 1 exit /b 1

call :do_test
if errorlevel 1 exit /b 1

call :do_sdk
if errorlevel 1 exit /b 1

call :summary

exit /b 0


:command_clean

call :do_clean
exit /b %errorlevel%


:command_deps

call :ensure_dependencies
exit /b %errorlevel%


:command_compile

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_compile
exit /b %errorlevel%


:command_jar

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_jar
exit /b %errorlevel%


:command_native

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_native
exit /b %errorlevel%


:command_agent

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_agent
exit /b %errorlevel%


:command_test

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_test
exit /b %errorlevel%


:command_sdk

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_sdk
exit /b %errorlevel%


:command_install

call :ensure_dependencies
if errorlevel 1 exit /b 1

call :do_install
exit /b %errorlevel%


:command_version

echo %NAME% %VERSION%
exit /b 0


:command_help

call :usage
exit /b 0


rem =============================================================================
rem REAL ENTRY POINT
rem =============================================================================

:main

call :parse_args %*

if errorlevel 1 (
    exit /b %errorlevel%
)

goto dispatch