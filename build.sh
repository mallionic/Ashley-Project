#!/usr/bin/env bash
# =============================================================================
# ashc SDK builder - REAL native binary with GraalVM native-image
# =============================================================================

set -euo pipefail

NAME="ashc"

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUT="$ROOT/out"
CLASSES="$OUT/classes"
DIST="$OUT/dist"

if [ -z "${VERSION:-}" ]; then
    if [ -f "$ROOT/VERSION" ]; then
        VERSION="$(tr -d '[:space:]' < "$ROOT/VERSION")"
    else
        VERSION="1.0.0"
    fi
fi

MAIN_CLASS="${MAIN_CLASS:-}"
FAST_INIT="${FAST_INIT:-0}"
QUICK="${QUICK:-0}"
PREFIX="${PREFIX:-$HOME/.local}"
VERBOSE="${VERBOSE:-0}"

SRC_LIST=""

JAR="$OUT/$NAME-$VERSION.jar"
BIN="$OUT/$NAME"
NI_LOG="$OUT/native-image.log"
SDK="$DIST/$NAME-$VERSION"

PLATFORM="$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m)"
ARCHIVE="$OUT/$NAME-$VERSION-$PLATFORM.tar.gz"

trap 'if [ -n "$SRC_LIST" ]; then rm -f "$SRC_LIST"; fi' EXIT

SECONDS=0

# =============================================================================
# Colors
# =============================================================================

if [ -t 1 ]; then
    C_B=$'\033[1m'
    C_G=$'\033[32m'
    C_R=$'\033[31m'
    C_Y=$'\033[33m'
    C_C=$'\033[36m'
    C_0=$'\033[0m'
else
    C_B=""
    C_G=""
    C_R=""
    C_Y=""
    C_C=""
    C_0=""
fi

# =============================================================================
# Output helpers
# =============================================================================

step() {
    printf '\n%s==>%s %s%s%s\n' \
        "$C_B" "$C_0" "$C_B" "$*" "$C_0"
}

info() {
    printf '  %s\n' "$*"
}

ok() {
    printf '  %s[ OKAY ]%s %s\n' \
        "$C_G" "$C_0" "$*"
}

fault() {
    printf '  %s[ FAULT ]%s %s\n' \
        "$C_R" "$C_0" "$*" >&2
}

warn() {
    printf '  %s[ WARN ]%s %s\n' \
        "$C_Y" "$C_0" "$*" >&2
}

die() {
    printf '  %s[ FAIL ]%s %s\n' \
        "$C_R" "$C_0" "$*" >&2
    exit 1
}

# =============================================================================
# Usage
# =============================================================================

usage() {
    printf '%sUsage:%s ./build.sh [command] [options]\n\n' \
        "$C_B" "$C_0"

    printf '%sCommands:%s\n' "$C_B" "$C_0"

    printf '  all        clean + compile + jar + native + test + sdk\n'
    printf '  clean      remove out/\n'
    printf '  compile    compile Java sources\n'
    printf '  jar        build out/%s-<version>.jar\n' "$NAME"
    printf '  native     build the native binary with GraalVM native-image\n'
    printf '  agent      generate native-image reflection configuration\n'
    printf '  test       run the native smoke test with test.ash\n'
    printf '  sdk        assemble the SDK and compressed archive\n'
    printf '  install    install the SDK into --prefix\n'
    printf '  deps       check and optionally install dependencies\n'
    printf '  version    print version\n'
    printf '  help       print this help\n\n'

    printf '%sOptions:%s\n' "$C_B" "$C_0"

    printf '  --prefix DIR       installation prefix\n'
    printf '  --quick            use faster native compilation (-Ob)\n'
    printf '  --fast-init        initialize selected classes at build time\n'
    printf '  -v, --verbose      list Java sources\n'
    printf '  -V, --version      print version\n'
    printf '  -h, --help         print this help\n\n'

    printf '%sEnvironment:%s\n' "$C_B" "$C_0"

    printf '  VERSION\n'
    printf '  MAIN_CLASS\n'
    printf '  QUICK\n'
    printf '  FAST_INIT\n'
    printf '  PREFIX\n'
    printf '  VERBOSE\n'
}

# =============================================================================
# Options
# =============================================================================

CMD=""

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            CMD="help"
            ;;

        -V|--version)
            CMD="version"
            ;;

        --prefix)
            shift
            PREFIX="${1:?--prefix requires a directory}"
            ;;

        --prefix=*)
            PREFIX="${1#--prefix=}"
            ;;

        --fast-init)
            FAST_INIT=1
            ;;

        --quick)
            QUICK=1
            ;;

        -v|--verbose)
            VERBOSE=1
            ;;

        -*)
            echo "Unknown option: $1 (try --help)" >&2
            exit 2
            ;;

        *)
            if [ -z "$CMD" ]; then
                CMD="$1"
            else
                echo "Only one command is allowed: $CMD / $1" >&2
                exit 2
            fi
            ;;
    esac

    shift || true
done

CMD="${CMD:-all}"

# =============================================================================
# Dependency state
# =============================================================================

MISSING_APT=()
MISSING_SPECIAL=()

DEPENDENCY_FAILURE=0

# =============================================================================
# Dependency checks
# =============================================================================

check_command() {
    local command_name="$1"
    local description="$2"

    if command -v "$command_name" >/dev/null 2>&1; then
        ok "$description: $command_name"
        return 0
    fi

    fault "$description: $command_name"
    DEPENDENCY_FAILURE=1
    return 1
}

check_package() {
    local package="$1"
    local description="$2"

    if dpkg-query -W -f='${Status}\n' "$package" 2>/dev/null \
        | grep -q '^install ok installed$'; then

        ok "$description: $package"
        return 0
    fi

    fault "$description: $package"
    MISSING_APT+=("$package")
    DEPENDENCY_FAILURE=1
    return 1
}

check_zlib() {
    if printf '#include <zlib.h>\nint main(void){return 0;}\n' |
        gcc -x c - -lz -o /tmp/ashc-zlib-test \
        >/dev/null 2>&1; then

        rm -f /tmp/ashc-zlib-test
        ok "zlib development headers: zlib1g-dev"
        return 0
    fi

    rm -f /tmp/ashc-zlib-test

    fault "zlib development headers: zlib1g-dev"
    MISSING_APT+=("zlib1g-dev")
    DEPENDENCY_FAILURE=1

    return 1
}

check_graalvm() {
    if ! command -v java >/dev/null 2>&1; then
        fault "Java runtime: java"
        MISSING_SPECIAL+=("java")
        DEPENDENCY_FAILURE=1
    else
        if java -version 2>&1 | grep -qi 'GraalVM'; then
            ok "GraalVM Java runtime: $(java -version 2>&1 | head -n 1)"
        else
            fault "GraalVM Java runtime: current java is not GraalVM"
            MISSING_SPECIAL+=("graalvm")
            DEPENDENCY_FAILURE=1
        fi
    fi

    if command -v javac >/dev/null 2>&1; then
        ok "Java compiler: javac"
    else
        fault "Java compiler: javac"
        MISSING_SPECIAL+=("javac")
        DEPENDENCY_FAILURE=1
    fi

    if command -v jar >/dev/null 2>&1; then
        ok "Java archive tool: jar"
    else
        fault "Java archive tool: jar"
        MISSING_SPECIAL+=("jar")
        DEPENDENCY_FAILURE=1
    fi

    if command -v native-image >/dev/null 2>&1; then
        ok "GraalVM Native Image: native-image"
    else
        fault "GraalVM Native Image: native-image"
        MISSING_SPECIAL+=("native-image")
        DEPENDENCY_FAILURE=1
    fi
}

check_dependencies() {
    step "Checking required dependencies"

    DEPENDENCY_FAILURE=0
    MISSING_APT=()
    MISSING_SPECIAL=()

    printf '\n'

    # -------------------------------------------------------------------------
    # Core shell utilities
    # -------------------------------------------------------------------------

    check_command bash "Shell"
    check_command find "File search utility"
    check_command sed "Stream editor"
    check_command tr "Text transformation utility"
    check_command grep "Text search utility"
    check_command awk "Text processing utility"
    check_command mktemp "Temporary file utility"
    check_command wc "Word count utility"
    check_command head "File reader utility"
    check_command od "Binary inspection utility"
    check_command du "Disk usage utility"
    check_command tar "Archive utility"
    check_command date "Date utility"

    # -------------------------------------------------------------------------
    # Build utilities
    # -------------------------------------------------------------------------

    check_package build-essential "Native build toolchain"

    check_command gcc "C compiler"

    if command -v gcc >/dev/null 2>&1; then
        check_zlib
    else
        fault "zlib development headers: gcc unavailable"
        MISSING_APT+=("zlib1g-dev")
        DEPENDENCY_FAILURE=1
    fi

    # -------------------------------------------------------------------------
    # Java / GraalVM
    # -------------------------------------------------------------------------

    check_graalvm

    # -------------------------------------------------------------------------
    # Optional packaging utilities
    # -------------------------------------------------------------------------

    check_command sha256sum "SHA-256 utility"

    if command -v git >/dev/null 2>&1; then
        ok "Git: git"
    else
        warn "Git: git not installed (optional)"
    fi

    printf '\n'

    if [ "$DEPENDENCY_FAILURE" -eq 0 ]; then
        ok "All required dependencies are installed."
        return 0
    fi

    fault "One or more required dependencies are missing."

    return 1
}

# =============================================================================
# Dependency installation
# =============================================================================

install_dependencies() {
    if [ "${#MISSING_APT[@]}" -eq 0 ] &&
       [ "${#MISSING_SPECIAL[@]}" -eq 0 ]; then
        return 0
    fi

    printf '\n'

    printf '%sThe following required dependencies are missing:%s\n' \
        "$C_Y" "$C_0"

    if [ "${#MISSING_APT[@]}" -gt 0 ]; then
        for package in "${MISSING_APT[@]}"; do
            printf '  - apt package: %s\n' "$package"
        done
    fi

    if [ "${#MISSING_SPECIAL[@]}" -gt 0 ]; then
        for dependency in "${MISSING_SPECIAL[@]}"; do
            printf '  - GraalVM component: %s\n' "$dependency"
        done
    fi

    printf '\n'
    printf 'Do you want to install the missing dependencies? [Y/n] '

    read -r answer

    case "$answer" in
        n|N|no|NO|No)
            die "Dependency installation cancelled by user."
            ;;

        "")
            answer="y"
            ;;

        y|Y|yes|YES|Yes)
            ;;

        *)
            die "Invalid answer. Dependency installation aborted."
            ;;
    esac

    # -------------------------------------------------------------------------
    # APT dependencies
    # -------------------------------------------------------------------------

    if [ "${#MISSING_APT[@]}" -gt 0 ]; then
        if ! command -v apt-get >/dev/null 2>&1; then
            die "apt-get is required to install missing system dependencies."
        fi

        if [ "$(id -u)" -eq 0 ]; then
            APT=(apt-get)
        else
            if ! command -v sudo >/dev/null 2>&1; then
                die "sudo is required to install system dependencies."
            fi

            APT=(sudo apt-get)
        fi

        step "Installing missing system dependencies"

        "${APT[@]}" update || \
            die "apt update failed. Dependency installation aborted."

        "${APT[@]}" install -y "${MISSING_APT[@]}" || \
            die "apt dependency installation failed. Build aborted."
    fi

    # -------------------------------------------------------------------------
    # GraalVM / Native Image
    # -------------------------------------------------------------------------

    local need_graal=0

    for dependency in "${MISSING_SPECIAL[@]}"; do
        case "$dependency" in
            graalvm|native-image|java|javac|jar)
                need_graal=1
                ;;
        esac
    done

    if [ "$need_graal" -eq 1 ]; then
        if command -v sdk >/dev/null 2>&1; then

            step "Installing GraalVM JDK 25"

            if ! sdk install java 25.4.4+1-graal; then
                die "GraalVM installation failed. Build aborted."
            fi

            sdk default java 25.4.4+1-graal || \
                die "Failed to select GraalVM JDK 25 as the default JDK."

            export SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"

            if [ -f "$SDKMAN_DIR/bin/sdkman-init.sh" ]; then
                # shellcheck disable=SC1091
                source "$SDKMAN_DIR/bin/sdkman-init.sh"
            fi

        else
            printf '\n'
            warn "SDKMAN! is not installed."

            printf '%sGraalVM JDK 25 with Native Image is required.%s\n' \
                "$C_Y" "$C_0"

            printf '\n'
            printf 'Install SDKMAN! first and then install GraalVM JDK 25:\n'
            printf '\n'
            printf '  curl -s "https://get.sdkman.io" | bash\n'
            printf '  source "$HOME/.sdkman/bin/sdkman-init.sh"\n'
            printf '  sdk install java 25.4.4+1-graal\n'
            printf '\n'

            die "GraalVM dependency is unavailable. Build aborted."
        fi
    fi

    # -------------------------------------------------------------------------
    # Re-check everything
    # -------------------------------------------------------------------------

    printf '\n'

    if ! check_dependencies; then
        die "Dependency installation completed with errors. Build aborted."
    fi
}

ensure_dependencies() {
    if ! check_dependencies; then
        install_dependencies
    fi
}

# =============================================================================
# Java information
# =============================================================================

java_banner() {
    java -version 2>&1 | head -n 1 || true
}

# =============================================================================
# Native ELF validation
# =============================================================================

assert_native() {
    local file="$1"

    if [ ! -f "$file" ]; then
        die "The output is not a native executable: $file"
    fi

    local magic

    magic="$(
        head -c 4 "$file" |
        od -An -tx1 |
        tr -d ' \n'
    )"

    if [ "$magic" != "7f454c46" ]; then
        die "The output is not a native ELF executable: $file"
    fi
}

# =============================================================================
# Clean
# =============================================================================

do_clean() {
    step "Cleaning output directory"

    rm -rf "$OUT"
    mkdir -p "$OUT"

    ok "$OUT"
}

# =============================================================================
# Java compilation
# =============================================================================

do_compile() {
    need_javac() {
        command -v javac >/dev/null 2>&1 || \
            die "javac is required."
    }

    need_javac

    step "Embedding nlib into the compiler"

    java "$ROOT/tools/EmbedNlib.java" \
        "$ROOT/nlib" \
        "$ROOT/Builder/ash/NlibEmbedded.java"

    step "Searching Java sources"

    rm -rf "$CLASSES"
    mkdir -p "$CLASSES"

    SRC_LIST="$(mktemp)"

    find "$ROOT" \
        -type f \
        -name '*.java' \
        -not -path "$OUT/*" \
        -not -path "$ROOT/tools/*" \
        -not -path "$ROOT/.git/*" \
        | sed 's/.*/"&"/' > "$SRC_LIST"

    COUNT="$(wc -l < "$SRC_LIST" | tr -d ' ')"

    if [ "$COUNT" -eq 0 ]; then
        die "No .java files found."
    fi

    ok "Java sources found: $COUNT"

    if [ "$VERBOSE" = "1" ]; then
        sed 's/"//g; s/^/    /' "$SRC_LIST"
    fi

    step "Compiling Java sources"

    javac \
        -encoding UTF-8 \
        -XDstringConcat=inline \
        -nowarn \
        -d "$CLASSES" \
        @"$SRC_LIST"

    ok "$CLASSES"
}

ensure_classes() {
    if [ ! -d "$CLASSES" ] ||
       [ -z "$(ls -A "$CLASSES" 2>/dev/null)" ]; then
        do_compile
    fi
}

# =============================================================================
# Main class detection
# =============================================================================

detect_main() {
    if [ -n "$MAIN_CLASS" ]; then
        return 0
    fi

    if [ -f "$CLASSES/arch/Main.class" ]; then
        MAIN_CLASS="arch.Main"

    elif [ -f "$CLASSES/Builder/Main.class" ]; then
        MAIN_CLASS="Builder.Main"

    elif [ -f "$CLASSES/Main.class" ]; then
        MAIN_CLASS="Main"

    else
        find "$CLASSES" \
            -type f \
            -name 'Main.class' \
            -print >&2 || true

        die "No Main class found. Use MAIN_CLASS=package.Main."
    fi
}

# =============================================================================
# JAR
# =============================================================================

do_jar() {
    ensure_classes

    command -v jar >/dev/null 2>&1 || \
        die "jar is required."

    detect_main

    step "Creating JAR"

    local MF="$OUT/MANIFEST.MF"

    {
        printf 'Implementation-Title: %s\n' "$NAME"
        printf 'Implementation-Version: %s\n' "$VERSION"
    } > "$MF"

    jar \
        --create \
        --file "$JAR" \
        --manifest "$MF" \
        --main-class "$MAIN_CLASS" \
        -C "$CLASSES" .

    rm -f "$MF"

    ok "$JAR"
    info "Main class: $MAIN_CLASS"
}

ensure_jar() {
    if [ ! -f "$JAR" ]; then
        do_jar
    fi
}

# =============================================================================
# Native Image preflight
# =============================================================================

preflight_native() {
    step "Checking native build toolchain"

    if ! command -v native-image >/dev/null 2>&1; then
        die "native-image is not available."
    fi

    ok "native-image: $(native-image --version 2>&1 | head -n 1)"

    if ! command -v gcc >/dev/null 2>&1; then
        die "gcc is required."
    fi

    ok "gcc: $(gcc --version | head -n 1)"

    if ! printf '#include <zlib.h>\n' |
        gcc -E -x c - >/dev/null 2>&1; then

        die "zlib development headers are missing."
    fi

    ok "zlib development headers"
}

# =============================================================================
# Native Image
# =============================================================================

do_native() {
    ensure_jar
    preflight_native

    step "Building native binary with GraalVM native-image"

    mkdir -p "$OUT"
    rm -f "$BIN"

    set -- \
        -jar "$JAR" \
        -o "$BIN" \
        --no-fallback \
        -march=compatibility \
        -H:+ReportExceptionStackTraces

    if [ "$QUICK" = "1" ]; then
        set -- "$@" -Ob
    fi

    if [ "$FAST_INIT" = "1" ]; then
        set -- \
            "$@" \
            "--initialize-at-build-time=Builder,arch"
    fi

    if [ -d "$ROOT/native-config" ]; then
        set -- \
            "$@" \
            "-H:ConfigurationFileDirectories=$ROOT/native-config"

        info "Reflection configuration: $ROOT/native-config"
    fi

    info "native-image $*"

    if ! native-image "$@" 2>&1 | tee "$NI_LOG"; then
        tail -n 40 "$NI_LOG" >&2 || true
        die "native-image failed. Full log: $NI_LOG"
    fi

    assert_native "$BIN"

    chmod +x "$BIN"

    ok "$BIN"
    ok "Native ELF size: $(du -h "$BIN" | cut -f1)"
}

ensure_bin() {
    if [ ! -x "$BIN" ]; then
        do_native
    fi

    assert_native "$BIN"
}

# =============================================================================
# Smoke test
# =============================================================================

do_test() {
    ensure_bin

    step "Running native smoke test"

    if [ ! -f "$ROOT/test.ash" ]; then
        warn "test.ash not found; the smoke test cannot be executed."
        return 1
    fi

    local TMPD
    TMPD="$(mktemp -d)"

    if (
        cd "$TMPD"

        "$BIN" \
            "$ROOT/test.ash" \
            --compile \
            --elf \
            > "$TMPD/log.txt" \
            2>&1
    ); then

        if [ -n "$(find "$TMPD" -name '*.o' -print -quit)" ]; then
            ok "Native smoke test passed."
            rm -rf "$TMPD"
            return 0
        fi
    fi

    tail -n 30 "$TMPD/log.txt" >&2 || true

    rm -rf "$TMPD"

    warn "Native smoke test failed."

    return 1
}

# =============================================================================
# Native Image agent
# =============================================================================

do_agent() {
    ensure_jar

    step "Generating reflection configuration"

    mkdir -p "$ROOT/native-config"

    for f in \
        "$ROOT"/test.ash \
        "$ROOT"/*.ash \
        "$ROOT"/examples/*.ash
    do
        if [ -f "$f" ]; then

            local TMPD
            TMPD="$(mktemp -d)"

            info "$f"

            (
                cd "$TMPD"

                java \
                    -agentlib:native-image-agent=config-merge-dir="$ROOT/native-config" \
                    -jar "$JAR" \
                    "$f" \
                    --compile \
                    --elf \
                    > /dev/null \
                    2>&1
            ) || warn "Agent execution failed: $f"

            rm -rf "$TMPD"
        fi
    done

    ok "$ROOT/native-config"
}

# =============================================================================
# SDK packaging
# =============================================================================

do_sdk() {
    ensure_jar
    ensure_bin

    step "Packaging SDK"

    rm -rf "$SDK"

    mkdir -p \
        "$SDK/bin" \
        "$SDK/lib" \
        "$SDK/share/doc/$NAME" \
        "$SDK/share/examples"

    cp "$BIN" "$SDK/bin/$NAME"
    chmod +x "$SDK/bin/$NAME"

    cp "$JAR" "$SDK/lib/"

    for f in \
        "$ROOT"/README* \
        "$ROOT"/LICENSE* \
        "$ROOT"/NOTICE* \
        "$ROOT"/CHANGELOG*
    do
        if [ -f "$f" ]; then
            cp "$f" "$SDK/share/doc/$NAME/"
        fi
    done

    for f in \
        "$ROOT"/*.ash \
        "$ROOT"/examples/*.ash
    do
        if [ -f "$f" ]; then
            cp "$f" "$SDK/share/examples/"
        fi
    done

    local COMMIT

    COMMIT="$(
        git -C "$ROOT" rev-parse --short HEAD 2>/dev/null ||
        echo unknown
    )"

    printf '%s\n' "$VERSION" > "$SDK/VERSION"

    {
        printf 'name=%s\n' "$NAME"
        printf 'version=%s\n' "$VERSION"
        printf 'platform=%s\n' "$PLATFORM"
        printf 'mode=native\n'
        printf 'commit=%s\n' "$COMMIT"
        printf 'built=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf 'graalvm=%s\n' \
            "$(native-image --version 2>&1 | head -n 1 || true)"
    } > "$SDK/sdk.properties"

    tar \
        -C "$DIST" \
        -czf "$ARCHIVE" \
        "$NAME-$VERSION"

    if command -v sha256sum >/dev/null 2>&1; then
        (
            cd "$OUT"
            sha256sum \
                "$(basename "$ARCHIVE")" \
                > "$(basename "$ARCHIVE").sha256"
        )
    fi

    ok "$SDK"
    ok "$ARCHIVE"

    if [ -f "$ARCHIVE.sha256" ]; then
        ok "$ARCHIVE.sha256"
    fi
}

# =============================================================================
# Install
# =============================================================================

do_install() {
    if [ ! -d "$SDK" ]; then
        do_sdk
    fi

    step "Installing SDK"

    mkdir -p \
        "$PREFIX/bin" \
        "$PREFIX/lib" \
        "$PREFIX/share"

    cp -f \
        "$SDK/bin/$NAME" \
        "$PREFIX/bin/"

    cp -f \
        "$SDK/lib/"* \
        "$PREFIX/lib/"

    cp -rf \
        "$SDK/share/." \
        "$PREFIX/share/"

    ok "$PREFIX/bin/$NAME"

    case ":$PATH:" in
        *":$PREFIX/bin:"*)
            ok "$PREFIX/bin is already in PATH"
            ;;
        *)
            warn "$PREFIX/bin is not in PATH"
            ;;
    esac
}

# =============================================================================
# Header
# =============================================================================

header() {
    printf '%s========================================%s\n' \
        "$C_B" "$C_0"

    printf '%s  %s %s - Native SDK Builder%s\n' \
        "$C_B" \
        "$NAME" \
        "$VERSION" \
        "$C_0"

    printf '%s========================================%s\n' \
        "$C_B" "$C_0"

    printf '  Root     : %s\n' "$ROOT"
    printf '  Output   : %s\n' "$OUT"
    printf '  Platform : %s\n' "$PLATFORM"
    printf '  Java     : %s\n' "$(java_banner)"
}

# =============================================================================
# Build summary
# =============================================================================

summary() {
    printf '\n%s========================================%s\n' \
        "$C_G" "$C_0"

    printf '%s  BUILD COMPLETE%s\n' \
        "$C_G" "$C_0"

    printf '%s========================================%s\n' \
        "$C_G" "$C_0"

    printf '  JAR      : %s\n' "$JAR"

    printf '  Binary   : %s  (native ELF, %s)\n' \
        "$BIN" \
        "$(du -h "$BIN" | cut -f1)"

    printf '  SDK      : %s\n' "$SDK"
    printf '  Archive  : %s\n' "$ARCHIVE"

    printf '  Elapsed  : %ss\n' "$SECONDS"

    printf '\n'
    printf '  Usage example:\n'
    printf '    ./out/%s test.ash --compile --elf --debug\n' "$NAME"
}

# =============================================================================
# Dispatch
# =============================================================================

case "$CMD" in

    all)
        header

        ensure_dependencies

        do_clean
        do_compile
        do_jar
        do_native

        do_test || \
            die "Native smoke test failed."

        do_sdk

        summary
        ;;

    clean)
        do_clean
        ;;

    deps)
        ensure_dependencies
        ;;

    compile)
        ensure_dependencies
        do_compile
        ;;

    jar)
        ensure_dependencies
        do_jar
        ;;

    native)
        ensure_dependencies
        do_native
        ;;

    agent)
        ensure_dependencies
        do_agent
        ;;

    test)
        ensure_dependencies
        do_test || exit 1
        ;;

    sdk)
        ensure_dependencies
        do_sdk
        ;;

    install)
        ensure_dependencies
        do_install
        ;;

    version)
        echo "$NAME $VERSION"
        ;;

    help)
        usage
        ;;

    *)
        echo "Unknown command: $CMD" >&2
        usage >&2
        exit 2
        ;;

esac