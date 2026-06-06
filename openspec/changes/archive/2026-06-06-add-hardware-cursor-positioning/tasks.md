## 1. Marker Scanning Foundation

- [x] 1.1 Add pure tests for cursor marker stripping/location with plain text, ANSI SGR, OSC hyperlinks, wide Unicode, combining marks, and end-of-line fake cursor spaces
- [x] 1.2 Implement a shared cursor marker scan result that returns marker-stripped lines plus an optional row/display-column position
- [x] 1.3 Ensure multiple markers are stripped deterministically and the first row-major marker is selected for placement

## 2. Public Runtime Option

- [x] 2.1 Add an additive TUI/runtime option for enabling marker-driven hardware cursor positioning with a disabled default
- [x] 2.2 Add Scaladoc for the option covering opt-in behavior, fake-cursor preservation, and backend independence
- [x] 2.3 Update construction/copy helpers so existing TUI creation code remains source-compatible

## 3. TUI Render Integration

- [x] 3.1 Strip cursor markers during final frame preparation after base and overlay composition
- [x] 3.2 Store and diff marker-stripped frame lines so marker metadata does not appear in virtual viewports or trigger marker-only redraws
- [x] 3.3 When enabled and a marker is found, move the hardware cursor to the computed frame row/column after frame output
- [x] 3.4 Preserve existing behavior when disabled or when no marker exists, including interactive stop positioning below rendered content

## 4. Component Behavior

- [x] 4.1 Verify focused `Input` and `Editor` emit a marker immediately before the fake cursor token
- [x] 4.2 Ensure unfocused `Input`/`Editor` instances do not emit markers
- [x] 4.3 Ensure editor autocomplete/focus ownership does not leave stale editor markers competing with an active focus-capturing overlay

## 5. Tests and Documentation

- [x] 5.1 Add virtual terminal/TUI tests for enabled cursor movement, disabled marker stripping, no-marker behavior, overlays, and differential redraws
- [x] 5.2 Update README or docs to describe the hardware cursor positioning option and caveats
- [x] 5.3 Update interactive smoke docs with a manual hardware-cursor/IME check
- [x] 5.4 Run `mill core.test`, `mill __.compile`, and `openspec validate --all --strict`
