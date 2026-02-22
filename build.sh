#!/bin/bash
# Build script for Broken Link Hijacker
# Uses plain javac + jar to produce a Burp-compatible JAR
# (Gradle's jar task creates ZIP v2.0 which some Burp versions reject)

set -e

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
JAVAC="$JAVA_HOME/bin/javac"
JAR_CMD="$JAVA_HOME/bin/jar"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
BUILD_DIR="$PROJECT_DIR/build/classes"
OUT_DIR="$PROJECT_DIR/build/libs"
OUTPUT_JAR="$OUT_DIR/BrokenLinkHijacker.jar"

# Find Montoya API JAR in Gradle cache
MONTOYA_JAR=$(find "$HOME/.gradle/caches" -name "montoya-api-*.jar" ! -name "*sources*" ! -name "*javadoc*" 2>/dev/null | head -1)

if [ -z "$MONTOYA_JAR" ]; then
    echo "[*] Montoya API not in Gradle cache. Downloading..."
    JAVA_HOME="$JAVA_HOME" "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" compileJava 2>&1 | tail -3
    MONTOYA_JAR=$(find "$HOME/.gradle/caches" -name "montoya-api-*.jar" ! -name "*sources*" ! -name "*javadoc*" 2>/dev/null | head -1)
fi

if [ -z "$MONTOYA_JAR" ]; then
    echo "[!] ERROR: Could not find Montoya API JAR."
    exit 1
fi

echo "[*] Montoya API: $MONTOYA_JAR"
echo "[*] Compiling..."

rm -rf "$BUILD_DIR" "$OUTPUT_JAR"
mkdir -p "$BUILD_DIR" "$OUT_DIR"

"$JAVAC" -cp "$MONTOYA_JAR" -d "$BUILD_DIR" "$SRC_DIR"/dev/msaad/burp/*.java

echo "[*] Packaging JAR..."
"$JAR_CMD" cf "$OUTPUT_JAR" -C "$BUILD_DIR" dev/

echo ""
echo "[+] Built: $OUTPUT_JAR ($(du -h "$OUTPUT_JAR" | cut -f1))"
echo "[+] Type:  $(file "$OUTPUT_JAR")"
echo "[+] Load this JAR in Burp Suite -> Extensions -> Add"
