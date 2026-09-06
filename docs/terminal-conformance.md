# Terminal conformance testing

Siglyph uses three complementary validation layers. `VirtualTerminal` provides fast, deterministic
renderer tests for the terminal behavior the runtime depends on. It covers grapheme display width,
wide-cell cursor advance, DEC autowrap, cursor movement and reports, supported erase operations,
alternate-screen lifecycle, and fixed-height viewport inspection. It is not a complete terminal
emulator.

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
Scalafix, JVM and Scala Native terminal compilation and tests under a PTY, followed by the focused
lifecycle suite. Packaging remains on Linux.

## Automated behavior coverage

| Area | Automated evidence | Platform claim |
| --- | --- | --- |
| Fullscreen resize and restoration | `FullscreenViewportSuite` checks exact fixed-height frames, resize invalidation, stale-frame rejection, alternate-screen exit, and normal-screen isolation. `SttyTerminalPtySuite` checks real JVM terminal restoration. | Renderer checks run on JVM and Scala Native. Real `stty` snapshots are JVM-only on Linux and macOS. |
| Nested scrolling and mouse routing | `FullscreenViewportSuite`, `ViewportAffordanceSuite`, and `MouseFoundationSuite` check deepest-target routing, chain or contain overscroll, capture cleanup, scrollbars, and committed geometry. | Shared core tests run on JVM and Scala Native. Emulator forwarding remains manual. |
| Search and selection | `TranscriptSearchSuite` checks normalized cached search and focus capture. `ViewportSelectionSuite` checks wrapping, Unicode, edge scrolling, resize projection, overlays, bounds, and host copy results. | Shared core tests run on JVM and Scala Native. No operating-system clipboard is invoked. |
| Clipboard security | `ViewportSelectionSuite` checks supported, unsupported, success, rejection, callback failure, payload bounds, and absence of OSC 52 output. | Core supports only explicit `ClipboardTarget.Host` callbacks on both platforms. OSC 52 is excluded. |
| Image clipping and retention | `FullscreenViewportSuite` and `ImageSuite` check full omission, partial omission, sticky and overlay clips, Kitty reuse and eviction, iTerm2 fallback, session isolation, and cleanup failure. | Typed protocol bytes are portable. Actual Kitty and iTerm2 display is manual emulator coverage. |
| Worker failure reporting | `TUITerminalFailureSuite`, `StreamTerminalSuite`, `SttyTerminalSuite`, and `PosixTerminalSuite` check input, fragment-flush, and resize-worker failures, first-failure ownership, independent suppression, EOF, stop races, stale generations, and cleanup. | Shared lifecycle checks run on JVM and Scala Native. Backend-specific tests cover JVM and Native implementations. |

Manual emulator checks are listed in [interactive-smoke.md](interactive-smoke.md). They cover visual
fullscreen resize, mouse forwarding, search highlights, selection, host callback status, image
appearance and omission, and shell restoration. Manual checks do not replace the byte-level and
state-level automated assertions.

## Explicit exclusions

- Windows console and ConPTY behavior are outside the current platform scope.
- The focused resize mutation and `stty` snapshot assertions exercise the JVM `SttyTerminal` only.
  Scala Native configures `termios` directly rather than invoking `stty`; its interactive
  start/stop path runs under the CI PTY, while failure ordering and retry behavior use the Native
  backend's deterministic cleanup hooks.
- The pseudo-terminal tests validate lifecycle, resize delivery, protocol ordering, and restoration.
  They do not validate emulator-specific fullscreen appearance, search highlight color, selection
  appearance, operating-system clipboard contents, or image pixels. Renderer-critical behavior
  stays in `VirtualTerminal`; visual checks stay in [interactive-smoke.md](interactive-smoke.md).
- OSC 52 selection copy is not implemented or tested as a supported path. Automated tests assert
  that host-only selection copy emits no OSC 52 sequence.

The PTY suite treats the transient POSIX `PENDIN` flag and externally changed window dimensions as
non-restoration state. All stable raw/canonical mode flags must return to their initial values.
