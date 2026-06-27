#!/usr/bin/env sh
set -eu

usage() {
  cat <<'USAGE'
Usage: scripts/publish-local-snapshot.sh [base-version]

Compile all modules, then publish all publishable modules to Maven local with a
-SNAPSHOT version.

Version resolution:
  1. [base-version] argument, if provided
  2. SIGLYPH_VERSION, if set
  3. GITHUB_REF_NAME without a leading v, if it starts with v
  4. Latest Git tag, without a leading v

Examples:
  scripts/publish-local-snapshot.sh
  scripts/publish-local-snapshot.sh 0.2.5
  SIGLYPH_VERSION=0.2.5 scripts/publish-local-snapshot.sh
USAGE
}

latest_git_tag() {
  git tag --list --sort=-version:refname | sed -n '1p'
}

case "${1:-}" in
  -h | --help)
    usage
    exit 0
    ;;
esac

if [ "$#" -gt 1 ]; then
  usage >&2
  exit 2
fi

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd "$script_dir/.." && pwd)
cd "$repo_root"

if [ "$#" -eq 1 ]; then
  version_input="$1"
elif [ "${SIGLYPH_VERSION:-}" ]; then
  version_input="$SIGLYPH_VERSION"
elif [ "${GITHUB_REF_NAME:-}" ] && [ "${GITHUB_REF_NAME#v}" != "$GITHUB_REF_NAME" ]; then
  version_input="${GITHUB_REF_NAME#v}"
else
  latest_tag=$(latest_git_tag)
  if [ -z "$latest_tag" ]; then
    echo "No version provided and no Git tags found." >&2
    exit 2
  fi
  version_input="$latest_tag"
fi

base_version="${version_input#v}"
base_version="${base_version%-SNAPSHOT}"
base_version="${base_version%-snapshot}"

if [ -z "$base_version" ]; then
  echo "Version must not be empty." >&2
  exit 2
fi

snapshot_version="$base_version-SNAPSHOT"

echo "Compiling siglyph modules..."
mill __.compile

echo "Publishing all publishable siglyph modules to Maven local as $snapshot_version..."
SIGLYPH_VERSION="$snapshot_version" mill __.publishM2Local

echo "Published siglyph artifacts to Maven local as $snapshot_version."
