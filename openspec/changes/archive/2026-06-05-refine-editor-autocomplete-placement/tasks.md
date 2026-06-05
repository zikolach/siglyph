## 1. Placement API

- [x] 1.1 Add or refine editor autocomplete placement configuration so the default mode is editor-adjacent placement.
- [x] 1.2 Preserve explicit overlay placement overrides for applications that need terminal-relative or custom positioning.
- [x] 1.3 Add Scaladoc for placement behavior, override semantics, and non-goals.

## 2. Editor Placement Implementation

- [x] 2.1 Teach the editor to remember enough render context to place suggestions after its current rendered visual area.
- [x] 2.2 Update autocomplete overlay creation/update to use editor-adjacent placement by default.
- [x] 2.3 Ensure placement updates when wrapping, multiline content, or terminal width changes alter editor visual height.
- [x] 2.4 Avoid scheduling extra render loops when placement is refreshed during rendering.

## 3. Demo Cleanup

- [x] 3.1 Remove manual autocomplete row calculation from the shared interactive demo.
- [x] 3.2 Keep demo slash-command suggestions appearing immediately after the editor area on JVM and Scala Native.
- [x] 3.3 Confirm overlay appearance does not clear scrollback, occupy the full terminal height, or trigger scroll jumps.

## 4. Tests

- [x] 4.1 Add editor tests for default adjacent autocomplete placement with single-line and multiline/wrapped editor content.
- [x] 4.2 Add tests proving explicit custom overlay options are preserved.
- [x] 4.3 Update interactive demo tests to assert the demo no longer computes placement manually and suggestions remain near the editor.
- [x] 4.4 Add resize or narrow-width regression coverage for adjacent placement and overlay frame height.
- [x] 4.5 Add resize-redraw regression coverage proving overlay resize does not emit clear-scrollback sequences or jump to terminal top.

## 5. Resize Redraw Behavior

- [x] 5.1 Repaint resize frames from the previous TUI frame start instead of clearing the whole terminal viewport/scrollback.
- [x] 5.2 Ensure resize with autocomplete overlay visible re-resolves placement without scrollback clearing.

## 6. Documentation

- [x] 6.1 Update README or interactive smoke docs if user-visible placement behavior or controls change.
- [x] 6.2 Update porting notes to clarify that Scala keeps overlay-backed autocomplete while matching pi-tui editor-adjacent placement behavior.

## 7. Validation

- [x] 7.1 Run `mill scalafmtAll` if formatting changes require it.
- [x] 7.2 Run `mill scalafmtCheck`.
- [x] 7.3 Run `mill scalafixCheck`.
- [x] 7.4 Run `mill quality`.
- [x] 7.5 Run `mill __.compile`.
- [x] 7.6 Run `mill core.test` and `mill interactiveDemo.test`.
- [x] 7.7 Run `mill interactiveJvmDemo.compile` and `mill interactiveNativeDemo.nativeLink`.
- [x] 7.8 Run `openspec validate --all --strict`.
