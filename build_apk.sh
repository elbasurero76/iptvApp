#!/bin/bash
# ============================================================
#  Script para compilar el APK de IPTV Player en Ubuntu
#  Ejecuta desde la carpeta IPTVPlayer:  bash build_apk.sh
# ============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$HOME/.iptv-build"
SDK_DIR="$BUILD_DIR/android-sdk"
JDK_DIR="$BUILD_DIR/jdk17"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
error()   { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

info "=== IPTV Player - Build APK ==="
mkdir -p "$BUILD_DIR" "$SDK_DIR"

# ── 1. JDK 17 ──────────────────────────────────────────────
if [ ! -f "$JDK_DIR/bin/java" ]; then
  info "Descargando JDK 17..."
  JDK_URL="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz"
  curl -L "$JDK_URL" -o "$BUILD_DIR/jdk17.tar.gz" --progress-bar
  mkdir -p "$JDK_DIR"
  tar -xzf "$BUILD_DIR/jdk17.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm "$BUILD_DIR/jdk17.tar.gz"
  info "JDK 17 instalado."
else
  info "JDK 17 ya disponible."
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

# ── 2. Android SDK Command Line Tools ─────────────────────
if [ ! -d "$SDK_DIR/cmdline-tools" ]; then
  info "Descargando Android SDK command-line tools..."
  SDK_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  curl -L "$SDK_URL" -o "$BUILD_DIR/cmdtools.zip" --progress-bar
  mkdir -p "$SDK_DIR/cmdline-tools"
  unzip -q "$BUILD_DIR/cmdtools.zip" -d "$SDK_DIR/cmdline-tools"
  # Renombrar para que sdkmanager funcione
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest" 2>/dev/null || true
  rm "$BUILD_DIR/cmdtools.zip"
  info "Command-line tools instaladas."
fi
export ANDROID_HOME="$SDK_DIR"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ── 3. SDK Platform y Build Tools ─────────────────────────
if [ ! -d "$SDK_DIR/platforms/android-34" ]; then
  info "Instalando Android SDK (Platform 34 + Build Tools)..."
  yes | sdkmanager --licenses > /dev/null 2>&1 || true
  sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
  info "SDK instalado."
else
  info "SDK Platform 34 ya disponible."
fi

# ── 4. Gradle Wrapper ─────────────────────────────────────
cd "$SCRIPT_DIR"
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
  info "Generando Gradle wrapper..."
  # Descargar Gradle si no está en PATH
  if ! command -v gradle &> /dev/null; then
    GRADLE_URL="https://services.gradle.org/distributions/gradle-8.6-bin.zip"
    curl -L "$GRADLE_URL" -o "$BUILD_DIR/gradle.zip" --progress-bar
    unzip -q "$BUILD_DIR/gradle.zip" -d "$BUILD_DIR/"
    export PATH="$BUILD_DIR/gradle-8.6/bin:$PATH"
    rm "$BUILD_DIR/gradle.zip"
  fi
  gradle wrapper --gradle-version 8.6
  chmod +x gradlew
  info "Gradle wrapper generado."
fi

# ── 5. Compilar APK ────────────────────────────────────────
info "Compilando APK (puede tardar 5-10 minutos la primera vez)..."
export GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx3g -Dorg.gradle.daemon=false"

./gradlew assembleDebug --no-daemon

# ── 6. Resultado ───────────────────────────────────────────
APK_PATH="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
  APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
  info "════════════════════════════════════════"
  info "✅ APK compilado con éxito!"
  info "   Tamaño: $APK_SIZE"
  info "   Ruta: $APK_PATH"
  info ""
  info "Para instalar en el Xiaomi 15:"
  info "  adb install $APK_PATH"
  info "  (o cópialo al móvil y ábrelo)"
  info "════════════════════════════════════════"
else
  error "No se encontró el APK. Revisa los errores de compilación."
fi
