# Terminal conformance testing

Siglyph uses two complementary terminal test layers. `VirtualTerminal` provides fast,
deterministic renderer tests for the subset of terminal behavior the runtime depends on:
grapheme display width, wide-cell cursor advance, DEC autowrap, cursor movement and reports, and
the supported erase operations. It is deliberately not a complete terminal emulator.

Real terminal lifecycle coverage runs in an allocated pseudo-terminal:

```bash
./scripts/test-terminal-pty.sh
```

The script supports Linux (`util-linux script`) and macOS (`BSD script`). It enables the otherwise
inert `SttyTerminalPtySuite`, which checks raw-mode flags, bracketed-paste write ordering, bounded
resize notification, retry after an injected cleanup failure, and terminal-state restoration. The
suite restores the original size and `stty` state from `finally` blocks. The wrapper also snapshots
and restores the caller's terminal on every shell exit path; if snapshot restoration itself fails,
it falls back to `stty sane`.

CI runs the complete test suite under a PTY on Linux. A separate cached macOS job runs formatting,
Scalafix, JVM and Scala Native terminal compilation/tests under a PTY, followed by the focused
lifecycle suite. Packaging remains on Linux.

## Explicit exclusions

- Windows console and ConPTY behavior are outside the current platform scope.
- The focused resize mutation and `stty` snapshot assertions exercise the JVM `SttyTerminal` only.
  Scala Native configures `termios` directly rather than invoking `stty`; its interactive
  start/stop path runs under the CI PTY, while failure ordering and retry behavior use the Native
  backend's deterministic cleanup hooks.
- The pseudo-terminal tests validate lifecycle and restoration, not emulator-specific rendering.
  Renderer-critical behavior stays in `VirtualTerminal`; visual smoke checks stay in
  [interactive-smoke.md](interactive-smoke.md).

The PTY suite treats the transient POSIX `PENDIN` flag and externally changed window dimensions as
non-restoration state. All stable raw/canonical mode flags must return to their initial values.
