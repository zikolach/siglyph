## 1. Fuzzy matching and autocomplete

- [ ] 1.1 Add dependency-free fuzzy match/filter utilities with tests for exact, boundary, consecutive, swapped alphanumeric, and tokenized matches.
- [ ] 1.2 Add optional fuzzy ranking integration to slash/path/trigger autocomplete providers while preserving deterministic ordering for equal scores.
- [ ] 1.3 Add a cancellable filesystem `PathCompletionProvider` helper with max-results, hidden/git filtering, quoting, and attachment-prefix behavior.
- [ ] 1.4 Add configurable stacked/natural trigger prefix support, including `#`, without adding command execution semantics to the TUI runtime.
- [ ] 1.5 Add autocomplete refresh cancellation/debounce tests covering rapid typing, stale responses, and visible suggestion refresh behavior.
- [ ] 1.6 Update editor/demo documentation to show built-in path and trigger autocomplete usage.

## 2. Selector and settings component parity

- [ ] 2.1 Extend `SelectList` with options/theme hooks for prefixes, selected text, descriptions, no-match text, scroll info, and label truncation.
- [ ] 2.2 Add `SelectList` filtering, optional fuzzy ranking, and selection-change callback behavior with width-safety tests.
- [ ] 2.3 Extend `SettingsList` with optional fuzzy ranking alongside existing containment filtering.
- [ ] 2.4 Add settings submenu support using existing component/overlay contracts and tests for open, selection update, cancel, and focus restoration.
- [ ] 2.5 Update demos and docs to exercise richer selector/settings behavior.

## 3. Markdown parity

- [ ] 3.1 Add Markdown theme hooks for supported blocks and inline constructs while preserving ANSI width safety.
- [ ] 3.2 Add capability-aware OSC 8 Markdown link rendering with readable fallback when hyperlinks are unsupported.
- [ ] 3.3 Add optional syntax-highlighting hook support for fenced code blocks.
- [ ] 3.4 Define and test the optional parser adapter boundary, including safe fallback on parser errors.
- [ ] 3.5 Update Markdown docs to list baseline, adapter, and intentionally deferred `pi-tui` behaviors.

## 4. Image helper parity

- [ ] 4.1 Add image file/source helper APIs that return base64 data, MIME type, and dimensions for the existing image component contract.
- [ ] 4.2 Implement dependency-light dimension sniffing tests for PNG, JPEG, GIF, WebP, invalid bytes, and unsupported files.
- [ ] 4.3 Add image cell-size bounding helpers for width/height caps, including portrait image height-cap tests.
- [ ] 4.4 Document optional richer image parser/scaler dependency boundaries and update image examples.

## 5. Advanced terminal keyboard protocol

- [ ] 5.1 Extend typed terminal input to represent press, repeat, and release metadata without breaking existing press-only component behavior.
- [ ] 5.2 Add Kitty CSI-u parser coverage for event metadata, super modifiers, keypad edge cases, and stale/mismatched negotiation responses.
- [ ] 5.3 Add conservative Kitty keyboard protocol negotiation hooks to interactive JVM and Native backends with fallback behavior tests where possible.
- [ ] 5.4 Route key-release events only to components that opt in through `wantsKeyRelease` and add virtual terminal tests for routing behavior.
- [ ] 5.5 Investigate and document platform-specific modifier fallbacks such as Apple Terminal modified Enter; implement only safe/testable fallback paths.

## 6. Documentation and validation

- [ ] 6.1 Refresh README feature tables and examples for the new parity helpers.
- [ ] 6.2 Update `docs/porting-notes.md` and `docs/post-mvp-plan.md` with implemented behavior and remaining intentional deviations.
- [ ] 6.3 Add or update interactive smoke notes for autocomplete, Markdown, image helpers, settings submenus, and keyboard protocol behavior.
- [ ] 6.4 Run `mill __.test`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
