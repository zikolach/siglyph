# TauTUI Test Comparison

TauTUI was reviewed as the Node-free porting reference. The first Scala slice ports the same broad seams rather than copying Swift code directly.

## Tests and features already reflected

- Component contract and vertical container rendering.
- Virtual terminal write capture and viewport-style assertions.
- ANSI visible-width and wrapping tests.
- Renderer tests for first render, width-change full redraw, changed-tail partial redraw, and overflow guard.
- Typed key normalization tests for legacy keys, modified arrows, Kitty CSI-u, xterm modifyOtherKeys, and bracketed paste.
- Component tests for Text, Box, Spacer, SelectList, and Input.
- Unicode input tests for combining/CJK/emoji/regional-indicator behavior.

## Differences from TauTUI

- Scala uses typed `TerminalInput` in component APIs from the start.
- Markdown stays pluggable and dependency-free at core level.
- JVM backend uses `stty`; TauTUI targets Swift/macOS/Linux native APIs.
- Scala Native POSIX raw-mode implementation remains pending even though the module now compiles as Scala Native.

## Follow-up TauTUI areas to revisit

- More complete virtual terminal emulation of cursor movement and scrollback.
- Editor buffer and paste-marker tests.
- Markdown rendering fixtures once the parser strategy is selected.
- Overlay positioning/focus tests when overlays are implemented.
