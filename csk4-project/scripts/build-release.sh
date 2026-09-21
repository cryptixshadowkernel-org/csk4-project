#!/bin/bash
# Build release APK

set -e
echo "🔨 Building CSK4 Release APK..."
cd "$(dirname "$0")/../android-app"

if [ -f "./gradlew" ]; then
    ./gradlew clean assembleRelease
else
    echo "❌ gradlew not found."
    exit 1
fi

echo ""
echo "✅ Release build successful!"
echo "📁 APK: android-app/app/build/outputs/apk/release/app-release-unsigned.apk"
echo "⚠️  Sign it before publishing."
