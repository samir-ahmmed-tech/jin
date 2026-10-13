#!/usr/bin/env bash
set -euo pipefail

# -----------------------------------------------------------------------------
# JIN Dual-Edition APK Packager (v2.2.4+)
# Developed by Samir Ahmmed (https://github.com/samir-ahmmed-tech/jin)
# Builds TWO distinct production APKs:
#   1. jin-termux-v<VERSION>-release.apk (Full 6-Agent + Harness + Fusion Suite)
#   2. jin-antigravity-only-v<VERSION>-release.apk (Pure Antigravity 2.0 PC Edition)
# -----------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
OUTPUT_DIR="${ROOT_DIR}/output"

MODE="debug"
if [[ "${1:-}" == "--release" || "${1:-}" == "-r" ]]; then
    MODE="release"
fi

echo "================================================================="
echo "🚀 Building Dual JIN Packages in [${MODE^^}] mode"
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
    echo "🔨 [1/2] Compiling Full Multi-Agent & Harness Release APK (-PjinEdition=full)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleRelease -PjinEdition=full
    APKS_SRC="${ROOT_DIR}/app/build/outputs/apk/release"
    find "${APKS_SRC}" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-termux-v${VERSION_NAME}-release.apk" \;

    echo "🔨 [2/2] Compiling Pure Antigravity 2.0 PC Edition Release APK (-PjinEdition=antigravity)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleRelease -PjinEdition=antigravity
    find "${APKS_SRC}" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-antigravity-only-v${VERSION_NAME}-release.apk" \;

    if [[ -f "${ROOT_DIR}/app/src/main/assets/bin/agy_ide_bridge" ]]; then
        cp "${ROOT_DIR}/app/src/main/assets/bin/agy_ide_bridge" "${OUTPUT_DIR}/agy_ide_bridge-v${VERSION_NAME}-android-arm64"
    fi
else
    echo "🔨 [1/2] Compiling Full Multi-Agent & Harness Debug APK (-PjinEdition=full)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleDebug -PjinEdition=full
    APKS_SRC="${ROOT_DIR}/app/build/outputs/apk/debug"
    find "${APKS_SRC}" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-termux-v${VERSION_NAME}-debug.apk" \;

    echo "🔨 [2/2] Compiling Pure Antigravity 2.0 PC Edition Debug APK (-PjinEdition=antigravity)..."
    "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" :app:assembleDebug -PjinEdition=antigravity
    find "${APKS_SRC}" -name "*.apk" -exec cp {} "${OUTPUT_DIR}/jin-antigravity-only-v${VERSION_NAME}-debug.apk" \;

    if [[ -f "${ROOT_DIR}/app/src/main/assets/bin/agy_ide_bridge" ]]; then
        cp "${ROOT_DIR}/app/src/main/assets/bin/agy_ide_bridge" "${OUTPUT_DIR}/agy_ide_bridge-v${VERSION_NAME}-android-arm64"
    fi
fi

echo ""
echo "================================================================="
echo "✅ Dual Build Complete! Generated Artifacts in: ${OUTPUT_DIR}"
echo "================================================================="
ls -lh "${OUTPUT_DIR}"/* 2>/dev/null || true
echo "================================================================="
