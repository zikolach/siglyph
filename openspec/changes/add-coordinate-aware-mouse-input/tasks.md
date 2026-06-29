## 1. Public Mouse Model and Parser

- [x] 1.1 Add shared core mouse input types under `scalatui.terminal`, including `TerminalInput.Mouse`, mouse action values, button values, wheel direction values, and mouse routing context types.
- [x] 1.2 Add SGR mouse parsing to `TerminalInputParser`, including press, release, wheel, coordinate normalization, modifier decoding, invalid-coordinate handling, and raw fallback behavior.
- [x] 1.3 Extend `TerminalInputBuffer` tests to cover fragmented SGR mouse reports and incomplete-report flushing.
- [x] 1.4 Add parser tests for SGR press, release, wheel-up, wheel-down, horizontal wheel, modifier decoding, zero-based coordinates, and invalid coordinates.

## 2. Opt-in Terminal Mouse Mode

- [x] 2.1 Add public mouse input configuration to `TUIOptions` with mouse reporting disabled by default.
- [x] 2.2 Add terminal mouse protocol constants and lifecycle helpers for enabling and disabling `CSI ? 1000` and `CSI ? 1006` modes.
- [x] 2.3 Update the JVM interactive terminal backend to enable mouse reporting on start only when configured and to disable it on stop and startup failure.
- [x] 2.4 Update the Scala Native interactive terminal backend to enable mouse reporting on start only when configured and to disable it on stop and startup failure.
- [x] 2.5 Update virtual terminal test support so tests can assert mouse mode sequences and send mouse input events.
- [x] 2.6 Add lifecycle tests for default-disabled mouse mode, opt-in enable sequences, stop disable sequences, and startup-failure cleanup.

## 3. Retained Layout and Hit Testing

- [x] 3.1 Add shared core rendered-frame and layout-node types that carry frame lines plus component bounds.
- [x] 3.2 Add a layout-aware render path that preserves `Component.render(width): Vector[String]` for existing components and treats non-layout-aware components as leaf nodes.
- [x] 3.3 Update `Container` and `ComponentFrameBuilder` to record nested child bounds in terminal-relative display-cell coordinates.
- [x] 3.4 Update overlay rendering to retain final visible overlay bounds after resolving size, clamping, clipping, and z-order.
- [x] 3.5 Store the latest retained base and overlay layout trees in `TUI` during the synchronized render path.
- [x] 3.6 Add layout tests for leaf components, vertical child offsets, nested containers, overlay clamping, overlay clipping, and z-order retention.

## 4. Coordinate-aware Mouse Routing

- [x] 4.1 Add an explicit component mouse handling contract so components opt into mouse routing before receiving routed mouse events.
- [x] 4.2 Implement TUI mouse routing after protocol-reply consumption and global input listeners, using visible overlays from topmost to bottommost and then the base layout tree.
- [x] 4.3 Implement deepest-first hit testing with ancestor fallback when a child does not handle the mouse event.
- [x] 4.4 Preserve existing keyboard focus unless the routed mouse handler explicitly requests focus through the TUI context.
- [x] 4.5 Add routing tests for nested child handling, ancestor fallback, top overlay precedence, lower overlay fallback, hidden overlay skipping, unhandled mouse input, and focus preservation.

## 5. Built-in Component Wheel Handling

- [x] 5.1 Add mouse wheel handling to `SelectList` using existing selection movement and visibility logic.
- [x] 5.2 Add mouse wheel handling to `SettingsList` using existing selection movement and visibility logic while preserving active filter query state.
- [x] 5.3 Add mouse wheel handling to `Editor` using existing PageUp and PageDown page movement semantics without mutating editor text.
- [x] 5.4 Add mouse wheel handling to editor autocomplete suggestion overlays so wheel events move the selected suggestion without changing editor text.
- [x] 5.5 Add component tests for wheel-up, wheel-down, boundary no-render behavior, and autocomplete overlay selection changes.

## 6. Documentation and Validation

- [x] 6.1 Add Scaladoc for public mouse input types, mouse options, layout bounds types, and the mouse handling contract.
- [x] 6.2 Update README or project docs to explain opt-in mouse input, zero-based coordinates, supported SGR protocol scope, and terminal text-selection caveats.
- [x] 6.3 Update interactive smoke documentation with JVM and Native mouse scroll checks.
- [x] 6.4 Update porting notes to state that mouse input is a siglyph extension beyond upstream `pi-tui`.
- [x] 6.5 Run `mill __.compile`, `mill core.test`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.

## 7. Mouse Demo Hardening

- [x] 7.1 Track mouse-enabled TUI frame origin with cursor-position reports so hit testing aligns when previous terminal output exists.
- [x] 7.2 Account for terminal scrolling during the initial mouse-enabled frame render.
- [x] 7.3 Add focused tests for shifted and scrolled mouse frame origins.
- [x] 7.4 Document that terminal wheel scrollback is captured while mouse reporting is enabled.
