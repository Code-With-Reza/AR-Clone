#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-${ROOT_DIR}/releases}"
BUILD_DIR="${ROOT_DIR}/build/release"
TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
SKIP_COMPILE=0

usage() {
  cat <<'EOF'
Usage: ./release.sh [--output-dir DIR] [--skip-compile]

Build the Java sources, create both standalone HTML variants (lean and with music),
and zip the release artifacts into multiple zip targets under the output directory.

Options:
  -o, --output-dir DIR   Directory to write release zips into (default: ./releases)
  --skip-compile         Reuse existing build outputs and skip Gradle compile steps
  -h, --help             Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -o|--output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --skip-compile)
      SKIP_COMPILE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

mkdir -p "${OUTPUT_DIR}" "${BUILD_DIR}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set. Install a JDK 25 and export JAVA_HOME before running this script." >&2
  exit 1
fi

export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

if [[ ${SKIP_COMPILE} -eq 0 ]]; then
  echo "==> Compiling Java sources"
  "${ROOT_DIR}/gradlew" :game:compileJava --console=plain --no-daemon --max-workers=1

  echo "==> Building desktop asset EPK"
  "${ROOT_DIR}/gradlew" :target_lwjgl_desktop:buildAssetsEPK --console=plain --no-daemon --max-workers=1
fi

echo "==> Building lean standalone HTML client"
node "${ROOT_DIR}/wasm-toolchain/build-single-html.js" \
  --output "${BUILD_DIR}/eaglercraft-26.2-lean.html" \
  --music-pack-output "${BUILD_DIR}/eaglercraft-26.2-optional-music.zip"

echo "==> Building music-enabled standalone HTML client"
node "${ROOT_DIR}/wasm-toolchain/build-single-html.js" \
  --output "${BUILD_DIR}/eaglercraft-26.2-with-music.html" \
  --with-music

ZIP_WEB_HTML_LEAN="${OUTPUT_DIR}/eaglercraft-26.2-web-html-lean-${TIMESTAMP}.zip"
ZIP_WEB_HTML_MUSIC="${OUTPUT_DIR}/eaglercraft-26.2-web-html-with-music-${TIMESTAMP}.zip"
ZIP_WEB_FILES="${OUTPUT_DIR}/eaglercraft-26.2-web-files-${TIMESTAMP}.zip"
ZIP_DESKTOP="${OUTPUT_DIR}/eaglercraft-26.2-desktop-assets-${TIMESTAMP}.zip"

echo "==> Packaging release zips"
zip -j "${ZIP_WEB_HTML_LEAN}" "${BUILD_DIR}/eaglercraft-26.2-lean.html" "${BUILD_DIR}/eaglercraft-26.2-optional-music.zip"
zip -j "${ZIP_WEB_HTML_MUSIC}" "${BUILD_DIR}/eaglercraft-26.2-with-music.html"
zip -r "${ZIP_WEB_FILES}" "${ROOT_DIR}/target_teavm_wasm_gc/build/web"
zip -r "${ZIP_DESKTOP}" "${ROOT_DIR}/target_lwjgl_desktop/build/epk/assets.epk"

printf '\nRelease artifacts:\n'
ls -lh "${OUTPUT_DIR}"

printf '\nCreated:\n'
for artifact in "${ZIP_WEB_HTML_LEAN}" "${ZIP_WEB_HTML_MUSIC}" "${ZIP_WEB_FILES}" "${ZIP_DESKTOP}"; do
  printf ' - %s\n' "${artifact}"
done
