#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ANDROID_SDK_ROOT_VALUE=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$HOME/Library/Android/sdk"}}
ANDROID_JAR="$ANDROID_SDK_ROOT_VALUE/platforms/android-35/android.jar"
SOURCE_DIR="$SCRIPT_DIR/library/src/main/java"
MANIFEST="$SCRIPT_DIR/library/src/main/AndroidManifest.xml"
RESOURCE_DIR="$SCRIPT_DIR/library/src/main/res"
CONSUMER_RULES="$SCRIPT_DIR/library/consumer-rules.pro"
OUTPUT_DIR="$SCRIPT_DIR/../../src/uni_modules/budlog-native/utssdk/app-android/libs"
OUTPUT_AAR="$OUTPUT_DIR/budlog-native-release.aar"

if [ ! -f "$ANDROID_JAR" ]; then
  echo "Android API 35 not found: $ANDROID_JAR" >&2
  exit 1
fi

TEMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/budlog-native-aar.XXXXXX")
trap 'rm -rf "$TEMP_DIR"' EXIT HUP INT TERM
mkdir -p "$TEMP_DIR/classes" "$TEMP_DIR/aar" "$OUTPUT_DIR"

find "$SOURCE_DIR" -name '*.java' -print > "$TEMP_DIR/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$TEMP_DIR/classes" \
  @"$TEMP_DIR/sources.txt"

jar cf "$TEMP_DIR/aar/classes.jar" -C "$TEMP_DIR/classes" .
cp "$MANIFEST" "$TEMP_DIR/aar/AndroidManifest.xml"
cp "$CONSUMER_RULES" "$TEMP_DIR/aar/consumer-rules.pro"
cp -R "$RESOURCE_DIR" "$TEMP_DIR/aar/res"
jar cf "$OUTPUT_AAR" -C "$TEMP_DIR/aar" .

echo "$OUTPUT_AAR"
