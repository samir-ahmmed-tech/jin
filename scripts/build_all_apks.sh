#!/usr/bin/env bash
set -euo pipefail

# -----------------------------------------------------------------------------
# JIN Multi-Package APK Build & Output Packager
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
# Builds standard and termux flavors.
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/output"

MODE="debug"
if [[ "${1:-}" == "--release" || "${1:-}" == "-r" ]]; then
    MODE="release"
fi

echo "================================================================="
echo "🚀 Building JIN Packages in [${MODE^^}] mode"
echo "================================================================="

# Extract version from version.json
VERSION_NAME="1.0.0"
VERSION_CODE="1"
if [[ -f "${ROOT_DIR}/version.json" ]]; then
    VERSION_NAME=$(python3 -c "import json; print(json.load(open('${ROOT_DIR}/version.json')).get('version_name', '1.0.0'))" 2>/dev/null || echo "1.0.0")
    VERSION_CODE=$(python3 -c "import json; print(json.load(open('${ROOT_DIR}/version.json')).get('version_code', 1))" 2>/dev/null || echo "1")
fi

echo "📦 Version Name: v${VERSION_NAME} (Code: ${VERSION_CODE})"
mkdir -p "${OUTPUT_DIR}"

if [[ "${MODE}" == "release" ]]; then
    echo "🔨 Compiling Standard Online Release (:app:assembleStandardOnlineRelease)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleStandardOnlineRelease

    echo "🔨 Compiling Standard Offline Release (:app:assembleStandardOfflineRelease)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleStandardOfflineRelease

    echo "🔨 Compiling Termux Online Release (:app:assembleTermuxOnlineRelease)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleTermuxOnlineRelease

    echo "🔨 Compiling Termux Offline Release (:app:assembleTermuxOfflineRelease)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleTermuxOfflineRelease

    APKS_SRC="${ROOT_DIR}/app/build/outputs/apk"
    find "${APKS_SRC}/standardOnline/release" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-standard-online-v${VERSION_NAME}-release.apk" \; 2>/dev/null || true
    find "${APKS_SRC}/standardOffline/release" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-standard-offline-v${VERSION_NAME}-release.apk" \; 2>/dev/null || true
    find "${APKS_SRC}/termuxOnline/release" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-termux-online-v${VERSION_NAME}-release.apk" \; 2>/dev/null || true
    find "${APKS_SRC}/termuxOffline/release" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-termux-offline-v${VERSION_NAME}-release.apk" \; 2>/dev/null || true
else
    echo "🔨 Compiling Standard Online Debug (:app:assembleStandardOnlineDebug)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleStandardOnlineDebug

    echo "🔨 Compiling Standard Offline Debug (:app:assembleStandardOfflineDebug)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleStandardOfflineDebug

    echo "🔨 Compiling Termux Online Debug (:app:assembleTermuxOnlineDebug)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleTermuxOnlineDebug

    echo "🔨 Compiling Termux Offline Debug (:app:assembleTermuxOfflineDebug)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleTermuxOfflineDebug

    APKS_SRC="${ROOT_DIR}/app/build/outputs/apk"
    find "${APKS_SRC}/standardOnline/debug" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-standard-online-v${VERSION_NAME}-debug.apk" \; 2>/dev/null || true
    find "${APKS_SRC}/standardOffline/debug" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-standard-offline-v${VERSION_NAME}-debug.apk" \; 2>/dev/null || true
    find "${APKS_SRC}/termuxOnline/debug" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-termux-online-v${VERSION_NAME}-debug.apk" \; 2>/dev/null || true
    find "${APKS_SRC}/termuxOffline/debug" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-termux-offline-v${VERSION_NAME}-debug.apk" \; 2>/dev/null || true

    if [[ -f "${ROOT_DIR}/app/src/main/assets/bin/agy_ide_bridge" ]]; then
        cp "${ROOT_DIR}/app/src/main/assets/bin/agy_ide_bridge" "${OUTPUT_DIR}/agy_ide_bridge-v${VERSION_NAME}-android-arm64"
    fi
fi

echo ""
echo "================================================================="
echo "✅ Build Complete! Generated Artifacts in: ${OUTPUT_DIR}"
echo "================================================================="
ls -lh "${OUTPUT_DIR}"/* 2>/dev/null || true
echo "================================================================="
