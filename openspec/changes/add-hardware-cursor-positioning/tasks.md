## 1. Marker Scanning Foundation

- [ ] 1.1 Add pure tests for cursor marker stripping/location with plain text, ANSI SGR, OSC hyperlinks, wide Unicode, combining marks, and end-of-line fake cursor spaces
- [ ] 1.2 Implement a shared cursor marker scan result that returns marker-stripped lines plus an optional row/display-column position
- [ ] 1.3 Ensure multiple markers are stripped deterministically and the first row-major marker is selected for placement

## 2. Public Runtime Option

- [ ] 2.1 Add an additive TUI/runtime option for enabling marker-driven hardware cursor positioning with a disabled default
- [ ] 2.2 Add Scaladoc for the option covering opt-in behavior, fake-cursor preservation, and backend independence
- [ ] 2.3 Update construction/copy helpers so existing TUI creation code remains source-compatible

## 3. TUI Render Integration

- [ ] 3.1 Strip cursor markers during final frame preparation after base and overlay composition
- [ ] 3.2 Store and diff marker-stripped frame lines so marker metadata does not appear in virtual viewports or trigger marker-only redraws
- [ ] 3.3 When enabled and a marker is found, move the hardware cursor to the computed frame row/column after frame output
- [ ] 3.4 Preserve existing behavior when disabled or when no marker exists, including interactive stop positioning below rendered content

## 4. Component Behavior

- [ ] 4.1 Verify focused `Input` and `Editor` emit a marker immediately before the fake cursor token
- [ ] 4.2 Ensure unfocused `Input`/`Editor` instances do not emit markers
- [ ] 4.3 Ensure editor autocomplete/focus ownership does not leave stale editor markers competing with an active focus-capturing overlay

## 5. Tests and Documentation

- [ ] 5.1 Add virtual terminal/TUI tests for enabled cursor movement, disabled marker stripping, no-marker behavior, overlays, and differential redraws
- [ ] 5.2 Update README or docs to describe the hardware cursor positioning option and caveats
- [ ] 5.3 Update interactive smoke docs with a manual hardware-cursor/IME check
- [ ] 5.4 Run `mill core.test`, `mill __.compile`, and `openspec validate --all --strict`
