## 1. Project Skeleton

- [ ] 1.1 Create a Mill Scala 3 project layout with separate core, Native backend, JVM backend, Markdown, and test modules
- [ ] 1.2 Add package namespaces for core, terminal, components, editing, autocomplete, markdown, and tests
- [ ] 1.3 Add a README section documenting the Node-free Scala TUI goal and initial platform scope
- [ ] 1.4 Add a porting notes document that records `pi-tui` and TauTUI source paths and sync procedure

## 2. Core Rendering Foundation

- [ ] 2.1 Define `Component`, `Focusable`, `Container`, and invalidation APIs
- [ ] 2.2 Implement ANSI escape parsing and visible-width measurement utilities
- [ ] 2.3 Implement ANSI-safe slicing, truncation, padding, and wrapping helpers
- [ ] 2.4 Implement the virtual terminal test backend and basic viewport assertions
- [ ] 2.5 Implement the differential renderer for first render, partial diff, and full redraw paths
- [ ] 2.6 Add renderer tests for width changes, changed tails, line overflow guards, and synchronized output wrapping

## 3. Terminal Runtime

- [ ] 3.1 Define typed terminal events, key events, modifiers, paste events, and resize callbacks
- [ ] 3.2 Implement escape sequence parsing for legacy keys, xterm modified keys, and Kitty CSI-u keys
- [ ] 3.3 Implement bracketed paste parsing and paste-safe event delivery
- [ ] 3.4 Implement Scala Native POSIX terminal startup, raw mode, stdin reads, resize handling, writes, and shutdown
- [ ] 3.5 Implement JVM Unix `stty` terminal startup, raw mode, stdin reads, resize handling, writes, and shutdown
- [ ] 3.6 Add terminal lifecycle tests using the virtual backend and parser-level unit tests for key sequences

## 4. Components

- [ ] 4.1 Implement MVP components: `Text`, `Spacer`, `Box`, `SelectList`, and `Input`
- [ ] 4.2 Add component render tests mirroring relevant `pi-tui` and TauTUI cases
- [ ] 4.3 Plan post-MVP components: `TruncatedText`, `Loader`, `SettingsList`, overlays, and image helpers

## 5. Editing and Autocomplete Plan

- [ ] 5.1 Implement the single-line `Input` component and readline-style key bindings for the MVP
- [ ] 5.2 Add Unicode input and paste regression tests for `Input`
- [ ] 5.3 Plan post-MVP `EditorBuffer` with insert, delete, split, merge, movement, undo, and kill-ring helpers
- [ ] 5.4 Plan post-MVP multiline `Editor` renderer with fake cursor and IME cursor marker support
- [ ] 5.5 Plan post-MVP autocomplete provider APIs for slash commands, file paths, and attachment-prefixed paths
- [ ] 5.6 Plan post-MVP large paste markers and submit-time paste expansion

## 6. Markdown, Capability Detection, and Examples

- [ ] 6.1 Create the separate Markdown module API and parser abstraction
- [ ] 6.2 Document candidate Markdown parser dependencies for JVM and Native before adding any runtime dependency
- [ ] 6.3 Implement terminal capability detection for true color, hyperlinks, and tmux limitations in the MVP
- [ ] 6.4 Plan post-MVP Kitty/iTerm2 image support
- [ ] 6.5 Add an MVP demo showing Text, Box, Spacer, SelectList, and Input
- [ ] 6.6 Add a key tester demo for raw and normalized terminal input diagnostics

## 7. Validation and Parity

- [ ] 7.1 Mirror high-value `pi-tui` tests for width, wrapping, keys, renderer, input, and MVP components first; plan editor, autocomplete, Markdown, and image fixtures later
- [ ] 7.2 Compare TauTUI tests and port useful Node-free edge cases into Scala tests
- [ ] 7.3 Document intentional deviations from current `pi-tui` behavior
- [ ] 7.4 Use normalized virtual-viewport tests for most rendering assertions and targeted raw ANSI snapshots for escape-stream compatibility
- [ ] 7.5 Run `openspec validate --all --strict` and fix any spec or change validation errors
