## 1. Project Skeleton

- [x] 1.1 Create a latest-stable Mill Scala 3 project layout named `scala-tui` with separate core, Native backend, JVM backend, Markdown, and test modules
- [x] 1.2 Add the initial `scalatui` package namespace for core, terminal, components, editing, autocomplete, markdown, and tests
- [x] 1.3 Add a README section documenting the Node-free Scala TUI goal and initial platform scope
- [x] 1.4 Add a porting notes document that records `pi-tui` and TauTUI source paths and sync procedure

## 2. Core Rendering Foundation

- [x] 2.1 Define `Component`, `Focusable`, `Container`, and invalidation APIs
- [x] 2.2 Add a Scala CLI Unicode table generator targeting latest Unicode data and commit generated width/grapheme tables
- [x] 2.3 Implement ANSI escape parsing and visible-width measurement utilities using generated Unicode tables
- [x] 2.4 Implement ANSI-safe slicing, truncation, padding, and wrapping helpers
- [x] 2.5 Implement the virtual terminal test backend and basic viewport assertions
- [x] 2.6 Implement the differential renderer for first render, partial diff, and full redraw paths
- [x] 2.7 Add renderer tests for width changes, changed tails, line overflow guards, and synchronized output wrapping

## 3. Terminal Runtime

- [x] 3.1 Define typed terminal events, key events, modifiers, paste events, and resize callbacks
- [x] 3.2 Implement escape sequence parsing for legacy keys, xterm modified keys, and Kitty CSI-u keys
- [x] 3.3 Implement bracketed paste parsing and paste-safe event delivery
- [ ] 3.4 Implement Scala Native POSIX terminal startup, raw mode, stdin reads, resize handling, writes, and shutdown
- [ ] 3.5 Implement JVM Unix `stty` terminal startup, raw mode, stdin reads, resize handling, writes, and shutdown for interactive TTYs
- [ ] 3.6 Implement stream-backed non-interactive terminal operation with configured or environment-derived dimensions
- [ ] 3.7 Add terminal lifecycle tests using the virtual backend and parser-level unit tests for key sequences

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

- [x] 6.1 Create the separate Markdown module API and parser abstraction
- [ ] 6.2 Research and document candidate Markdown parser dependencies for JVM and Native before adding any runtime dependency
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
