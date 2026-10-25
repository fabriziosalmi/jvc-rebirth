#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN="/tmp/android-toolchain"
JAVA_HOME="$TOOLCHAIN/zulu17.50.19-ca-jdk17.0.11-macosx_aarch64"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

BUILD_TOOLS="$TOOLCHAIN/android-14"
ANDROID_JAR="$TOOLCHAIN/android-30.jar"
JUNIT_JAR="$TOOLCHAIN/junit.jar"
HAMCREST_JAR="$TOOLCHAIN/hamcrest.jar"

BUILD_DIR="$SCRIPT_DIR/build"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/test-classes" "$BUILD_DIR/res_compiled" "$SCRIPT_DIR/../apks"

echo "[1/5] Compiling resources with aapt2..."
"$BUILD_TOOLS/aapt2" compile --dir "$SCRIPT_DIR/res" -o "$BUILD_DIR/res_compiled/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$SCRIPT_DIR/AndroidManifest.xml" \
  --java "$BUILD_DIR/gen" \
  -o "$BUILD_DIR/unaligned.apk" \
  "$BUILD_DIR/res_compiled/resources.zip"

echo "[2/5] Compiling Java sources (target Java 8 bytecode for Android API 28-30)..."
javac --release 8 \
  -cp "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" \
  "$BUILD_DIR/gen/tv/rebirth/launcher/R.java" \
  "$SCRIPT_DIR/src/main/java/tv/rebirth/launcher/LauncherModel.java" \
  "$SCRIPT_DIR/src/main/java/tv/rebirth/launcher/RebirthHomeView.java" \
  "$SCRIPT_DIR/src/main/java/tv/rebirth/launcher/MainActivity.java"

echo "[3/5] Running JUnit 4 local test suite..."
javac --release 8 \
  -cp "$BUILD_DIR/classes:$JUNIT_JAR:$HAMCREST_JAR" \
  -d "$BUILD_DIR/test-classes" \
  "$SCRIPT_DIR/src/test/java/tv/rebirth/launcher/LauncherModelTest.java"

java -Drebirth.classes.dir="$BUILD_DIR/classes" \
  -cp "$BUILD_DIR/classes:$BUILD_DIR/test-classes:$JUNIT_JAR:$HAMCREST_JAR" \
  org.junit.runner.JUnitCore tv.rebirth.launcher.LauncherModelTest

echo "[4/5] Converting JVM bytecode to Dalvik DEX (d8 --release)..."
"$BUILD_TOOLS/d8" --release --min-api 28 \
  --lib "$ANDROID_JAR" \
  --output "$BUILD_DIR" \
  "$BUILD_DIR"/classes/tv/rebirth/launcher/*.class

cd "$BUILD_DIR"
zip -q -0 -u unaligned.apk classes.dex

echo "[5/5] Aligning (zipalign -p 4) and signing (apksigner v1+v2+v3)..."
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/zipalign" -c -p 4 "$BUILD_DIR/aligned.apk"

KEYSTORE="$BUILD_DIR/release-key.jks"
keytool -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias rebirth \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass rebirth123 -keypass rebirth123 \
  -dname "CN=Rebirth Launcher, OU=TV, O=JVCRebirth, L=Genoa, ST=IT, C=IT" >/dev/null 2>&1

OUT_APK="$SCRIPT_DIR/../apks/RebirthLauncher-1.0.0.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:rebirth123 \
  --key-pass pass:rebirth123 \
  --out "$OUT_APK" \
  "$BUILD_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$OUT_APK" | head -n 5
ls -lh "$OUT_APK" "$BUILD_DIR/classes.dex"
