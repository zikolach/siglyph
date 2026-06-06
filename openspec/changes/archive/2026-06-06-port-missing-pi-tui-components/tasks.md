## 1. Advanced Input and Editor Editing Parity

- [x] 1.1 Add a shared undo stack and kill-ring utility model in `core` for use by text components.
- [x] 1.2 Extend `Input` to expose and apply `pi-tui`-aligned undo-only, yank, and yank-pop command behavior while preserving existing single-line callbacks.
- [x] 1.3 Add word-boundary helpers for Unicode-aware word navigation and word deletion used by both `Input` and `Editor`.
- [x] 1.4 Add large-paste marker insertion in `EditorBuffer` and rendering metadata for collapsed large-paste display.
- [x] 1.5 Add marker expansion behavior on submit or explicit expansion request while preserving submitted logical text.
- [x] 1.6 Add kill-ring-backed delete/yank history for editor delete commands (`deleteWord*`, `deleteToStart`, `deleteToEnd`, etc.) with upstream default keybindings (`Ctrl+-` undo, `Ctrl+Y` yank, `Alt+Y` yank-pop, etc.).
- [x] 1.7 Add IME/hardware cursor marker support in focused `Editor`/`Input` output with no marker leakage into logical text values.

## 2. Combined Slash and Path Autocomplete

- [x] 2.1 Add a combined provider abstraction that composes slash command and file/attachment completion providers.
- [x] 2.2 Add deterministic prefix parsing for slash-prefixed tokens, quoted paths, `@` attachment markers, and delimiter-aware replacement.
- [x] 2.3 Add asynchronous file-path suggestion support with cancellable request handle integration and stale-response filtering.
- [x] 2.4 Extend `Editor` key-trigger behavior so explicit completion mode and force-refresh semantics remain deterministic with combined provider input.
- [x] 2.5 Add tests covering mixed suggestions, quoted prefixes, stale suggestion results, and completion replacement semantics.

## 3. Markdown Rendering Component

- [x] 3.1 Implement a concrete dependency-free basic markdown pipeline in `markdown` module under the existing `MarkdownRenderer` API.
- [x] 3.2 Implement parser abstraction boundaries that allow dependency-free/JVM/Native strategy selection without changing the public API.
- [x] 3.3 Support and test a minimum markdown subset (headings, paragraphs, inline code, fenced/indented code, emphasis, lists, links, block quotes, horizontal rules, and tables where feasible).
- [x] 3.4 Add parser error-to-fallback behavior that keeps rendering stable for unsupported constructs.
- [x] 3.5 Ensure markdown output participates in width-aware truncation and sanitization behavior.
- [x] 3.6 Document optional third-party parser adapter module boundaries and do not add mandatory parser dependencies without explicit dependency approval.

## 4. Image Rendering and Runtime Capability Wiring

- [x] 4.1 Add capability-aware image model/types and dependency-free protocol helpers for Kitty and iTerm2 output paths in core where needed by terminal/runtime decisions.
- [x] 4.2 Add an optional image module with an `Image` component that emits protocol escapes only when `TerminalCapabilities.images` is available.
- [x] 4.3 Add image fallback rendering (readable metadata/placeholder) when image support is unavailable.
- [x] 4.4 Add image lifecycle helpers for ID reuse/cleanup in helper APIs where protocol defines reuse behavior, keeping dependency-requiring file/dimension/scaling helpers optional.
- [x] 4.5 Add tests for supported protocol rendering, unsupported fallback, and sanitized width-safe output.

## 5. Public API and Documentation

- [x] 5.1 Extend public APIs and Scaladoc for `Input`, `Editor`, autocomplete providers, optional image options, and markdown/renderer contracts.
- [x] 5.2 Update README, parity docs, and smoke docs with explicit new controls, capabilities, and intentional deviations.
- [x] 5.3 Update post-MVP plan or roadmap files with this batch of parity work and any deferred follow-ups.

## 6. Demo and Test Coverage

- [x] 6.1 Add/extend shared interactive demo actions to demonstrate new editing parity features (undo/kill-ring/markers), combined autocomplete, markdown, and image fallback.
- [x] 6.2 Add or extend component/runtime tests for `Input`, `Editor`, autocomplete parsing/completion, markdown renderer, and image capability behavior.
- [x] 6.3 Add/extend virtual terminal integration tests that cover overlay-assisted completion and over-wide final output behavior with new components.

## 7. Validation

- [x] 7.1 Run `mill __.compile` and ensure all modules compile.
- [x] 7.2 Run `mill core.test` with new editor/input, markdown, and autocomplete tests.
- [x] 7.3 Run demo/interactive-related checks (`mill interactiveDemo.test`, `mill interactiveJvmDemo.compile`, `mill interactiveNativeDemo.nativeLink`).
- [x] 7.4 Run `mill scalafmtCheck`, `mill scalafixCheck`, and `mill quality`.
- [x] 7.5 Run `openspec validate --all --strict` and fix any spec-sync issues.
