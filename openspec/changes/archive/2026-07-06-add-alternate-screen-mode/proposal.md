## Why

Some Siglyph applications need a full-screen terminal experience that does not leave intermediate frames in the normal shell scrollback. This is useful for full-screen editors, file managers, inspectors, and temporary modal workflows that should return users to the prior terminal screen when finished.

Siglyph currently preserves the normal terminal screen by default and explicitly avoids alternate-screen mode during resize redraws. That default should remain unchanged, but applications should be able to opt into alternate-screen ownership when the UI shape requires it.

## What Changes

- Add an opt-in TUI runtime mode that enters terminal alternate screen on start and exits alternate screen on stop.
- Keep normal-screen rendering as the default behavior for all existing `TUI` instances.
- Preserve the existing `Component.render(width): Vector[String]` contract.
- Preserve current resize redraw behavior for normal-screen mode, including pi-tui-style full clear redraws without alternate-screen mode.
- Define cleanup ordering so alternate-screen exit, cursor visibility, autowrap restoration, bracketed paste shutdown, keyboard protocol shutdown, and terminal backend stop remain safe on normal exits and runtime failures.
- Add test coverage proving the default mode emits no alternate-screen enter or exit sequences.
- Add test coverage proving opt-in alternate-screen mode emits enter and exit sequences exactly once per lifecycle and restores terminal state on `stop()` and `run()` failure paths.
- Document that temporary full-screen modal sessions, height-aware component rendering, and a scrollable full-screen editor are follow-up design areas unless explicitly scoped later.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `terminal-runtime`: Add opt-in alternate-screen lifecycle behavior while preserving normal-screen defaults, resize redraw behavior, and terminal cleanup guarantees.
- `developer-api`: Expose the alternate-screen mode through the public runtime options without changing existing constructor defaults or component contracts.

## Impact

- Affected runtime code:
  - `core/src/scalatui/core/TUI.scala`
  - `core/src/scalatui/terminal/Terminal.scala` only if a backend capability abstraction is chosen instead of direct `Terminal.write` lifecycle sequences
  - JVM, Native, and virtual terminal backends only if the design places alternate-screen entry and exit behind backend methods
- Affected tests:
  - `core/test/src/scalatui/core/TUISuite.scala`
  - `core/test/src/scalatui/terminal/VirtualTerminalSuite.scala` if virtual terminal assertions need clearer lifecycle coverage
- Affected documentation/specs:
  - `openspec/specs/terminal-runtime/spec.md`
  - `openspec/specs/developer-api/spec.md`
  - README or docs if public options are documented outside Scaladoc
- API compatibility:
  - No breaking change is intended.
  - Existing `TUI(terminal)` and `TUI(terminal, TUIOptions())` behavior remains normal-screen mode.
  - Existing components keep the width-only render contract.
- Dependencies:
  - No new runtime dependency is expected.
