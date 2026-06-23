#!/usr/bin/env sh
set -eu

usage() {
  cat <<'USAGE'
Usage: scripts/asciinema/record-demo.sh <scenario|all>

Scenarios:
  agent-prompt     Agent prompt composer with slash, attachment, and tag completion
  command-palette  Command palette, loader/progress state, and settings changes
  unicode-input    Unicode-safe editing and typed terminal input proof
  all              Record all scenarios
USAGE
}

if [ "$#" -ne 1 ]; then
  usage >&2
  exit 2
fi

script_dir=$(CDPATH= cd "$(dirname "$0")" && pwd)
repo_root=$(CDPATH= cd "$script_dir/../.." && pwd)
cd "$repo_root"

if ! command -v asciinema >/dev/null 2>&1; then
  echo "asciinema is required only for local recording. Install it to generate .cast files." >&2
  exit 127
fi

artifact_dir="artifacts/asciinema"
mkdir -p "$artifact_dir"

record_one() {
  scenario="$1"
  case "$scenario" in
    agent-prompt)
      title="siglyph agent prompt composer"
      output="$artifact_dir/siglyph-agent-prompt.cast"
      ;;
    command-palette)
      title="siglyph command palette and settings"
      output="$artifact_dir/siglyph-command-palette.cast"
      ;;
    unicode-input)
      title="siglyph unicode and typed input"
      output="$artifact_dir/siglyph-unicode-input.cast"
      ;;
    *)
      echo "Unknown scenario: $scenario" >&2
      usage >&2
      exit 2
      ;;
  esac

  log="$artifact_dir/$scenario.stderr.log"
  pause_ms="${SIGLYPH_ASCIINEMA_PAUSE_MS:-2000}"
  command="SIGLYPH_ASCIINEMA_PAUSE_MS=$pause_ms mill asciinemaDemo.run \"$scenario\" 2>\"$log\""

  asciinema rec \
    --quiet \
    --return \
    --overwrite \
    --window-size 100x30 \
    --title "$title" \
    --command "$command" \
    "$output"

  echo "Recorded $output"
  echo "Hidden build-tool output: $log"
}

case "$1" in
  all)
    record_one agent-prompt
    record_one command-palette
    record_one unicode-input
    ;;
  agent-prompt | command-palette | unicode-input)
    record_one "$1"
    ;;
  *)
    echo "Unknown scenario: $1" >&2
    usage >&2
    exit 2
    ;;
esac
