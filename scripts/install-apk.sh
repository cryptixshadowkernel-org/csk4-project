#!/bin/bash
# Install APK to connected device

APK_PATH="$(dirname "$0")/../android-app/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ APK not found. Run ./scripts/build-debug.sh first"
    exit 1
fi

echo "📱 Installing APK..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
    echo "✅ Installed successfully!"
else
    echo "❌ Installation failed. Is device connected? (adb devices)"
    exit 1
fi
