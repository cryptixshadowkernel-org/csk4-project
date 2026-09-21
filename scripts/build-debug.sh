#!/bin/bash
# Build debug APK

set -e
echo "🔨 Building CSK4 Debug APK..."
cd "$(dirname "$0")/../android-app"

if [ -f "./gradlew" ]; then
    ./gradlew clean assembleDebug
else
    echo "❌ gradlew not found. Run: gradle wrapper --gradle-version 8.2"
    exit 1
fi

echo ""
echo "✅ Build successful!"
echo "📁 APK: android-app/app/build/outputs/apk/debug/app-debug.apk"
