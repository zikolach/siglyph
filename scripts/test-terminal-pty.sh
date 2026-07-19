#!/usr/bin/env sh
set -eu

original_stty=""
if [ -t 0 ]; then
  original_stty=$(stty -g 2>/dev/null || true)
fi

restore_terminal() {
  if [ -n "$original_stty" ]; then
    stty "$original_stty" 2>/dev/null || stty sane 2>/dev/null || true
  fi
}
trap restore_terminal EXIT HUP INT TERM

if ! command -v mill >/dev/null 2>&1; then
  echo "mill is required on PATH" >&2
  exit 1
fi
if ! command -v script >/dev/null 2>&1; then
  echo "script is required to allocate a PTY" >&2
  exit 1
fi

test_target="terminalJvm.test.testOnly scalatui.terminal.jvm.SttyTerminalPtySuite"
case "$(uname -s)" in
  Linux)
    script -q -e -c "SIGLYPH_PTY_TEST=1 mill --no-daemon $test_target" /dev/null
    ;;
  Darwin)
    script -q -e /dev/null env SIGLYPH_PTY_TEST=1 mill --no-daemon \
      terminalJvm.test.testOnly scalatui.terminal.jvm.SttyTerminalPtySuite
    ;;
  *)
    echo "PTY lifecycle tests support Linux and macOS only" >&2
    exit 2
    ;;
esac
