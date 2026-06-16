#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <download-url> <archive-name> <sha256> <vendor-dir>" >&2
  exit 2
fi

DOWNLOAD_URL="$1"
ARCHIVE_NAME="$2"
EXPECTED_SHA256="$3"
VENDOR_DIR="$4"
APP_DIR="$VENDOR_DIR/LibreOfficePortable"
APP_INFO="$APP_DIR/App/AppInfo/appinfo.ini"
CACHE_DIR="${FOURKFILEVIEW_VENDOR_CACHE:-${KKFILEVIEW_VENDOR_CACHE:-$HOME/.cache/4kfileview/vendor}}"
ARCHIVE_PATH="$CACHE_DIR/$ARCHIVE_NAME"

find_7z() {
  if command -v 7zz >/dev/null 2>&1; then
    command -v 7zz
  elif command -v 7z >/dev/null 2>&1; then
    command -v 7z
  elif command -v 7za >/dev/null 2>&1; then
    command -v 7za
  else
    return 1
  fi
}

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

verify_archive() {
  [ -f "$ARCHIVE_PATH" ] || return 1
  [ "$(hash_file "$ARCHIVE_PATH")" = "$EXPECTED_SHA256" ]
}

download_archive() {
  mkdir -p "$CACHE_DIR"
  local tmp="$ARCHIVE_PATH.tmp"
  rm -f "$tmp"
  echo "Downloading $ARCHIVE_NAME"
  curl -L --fail --retry 3 --retry-delay 5 -o "$tmp" "$DOWNLOAD_URL"
  mv "$tmp" "$ARCHIVE_PATH"
}

locate_portable_root() {
  local root="$1"
  local app_info

  [ -d "$root" ] || return 1

  if [ -f "$root/App/AppInfo/appinfo.ini" ]; then
    printf '%s\n' "$root"
    return 0
  fi

  app_info="$(find "$root" -path '*/App/AppInfo/appinfo.ini' -type f | head -n 1 || true)"
  if [ -n "$app_info" ]; then
    dirname "$(dirname "$(dirname "$app_info")")"
    return 0
  fi

  return 1
}

extract_archive() {
  local seven_zip="$1"
  local work_dir="$2"

  "$seven_zip" x -y "-o$work_dir/root" "$ARCHIVE_PATH" >/dev/null

  if locate_portable_root "$work_dir/root" >/dev/null; then
    return 0
  fi

  local nested_archive
  nested_archive="$(find "$work_dir/root" -type f \( -name '*.7z' -o -name '*.zip' \) | head -n 1 || true)"
  if [ -z "$nested_archive" ]; then
    return 0
  fi

  mkdir -p "$work_dir/nested"
  "$seven_zip" x -y "-o$work_dir/nested" "$nested_archive" >/dev/null
}

if [ -f "$APP_INFO" ]; then
  echo "LibreOfficePortable already prepared at $APP_DIR"
  exit 0
fi

if ! verify_archive; then
  download_archive
fi

if ! verify_archive; then
  echo "Checksum mismatch for $ARCHIVE_PATH" >&2
  echo "Expected: $EXPECTED_SHA256" >&2
  echo "Actual:   $(hash_file "$ARCHIVE_PATH")" >&2
  exit 1
fi

SEVEN_ZIP="$(find_7z)" || {
  echo "7z/7zz/7za is required to extract $ARCHIVE_NAME." >&2
  echo "Install p7zip first, for example: brew install p7zip or apt-get install p7zip-full." >&2
  exit 1
}

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT
mkdir -p "$WORK_DIR/root"

extract_archive "$SEVEN_ZIP" "$WORK_DIR"

PORTABLE_ROOT="$(locate_portable_root "$WORK_DIR/root" || locate_portable_root "$WORK_DIR/nested" || true)"
if [ -z "$PORTABLE_ROOT" ]; then
  echo "Could not locate LibreOfficePortable contents after extracting $ARCHIVE_NAME" >&2
  exit 1
fi

rm -rf "$APP_DIR"
mkdir -p "$VENDOR_DIR"
cp -R "$PORTABLE_ROOT" "$APP_DIR"

if [ ! -f "$APP_INFO" ]; then
  echo "LibreOfficePortable extraction did not produce $APP_INFO" >&2
  exit 1
fi

echo "LibreOfficePortable prepared at $APP_DIR"
