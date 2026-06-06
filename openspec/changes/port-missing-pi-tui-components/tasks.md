## 1. Advanced Input and Editor Editing Parity

- [ ] 1.1 Add a shared undo stack and kill-ring utility model in `core` for use by text components.
- [ ] 1.2 Extend `Input` to expose and apply undo, redo, yank, and yank-pop command behavior while preserving existing single-line callbacks.
- [ ] 1.3 Add word-boundary helpers for Unicode-aware word navigation and word deletion used by both `Input` and `Editor`.
- [ ] 1.4 Add large-paste marker insertion in `EditorBuffer` and rendering metadata for collapsed large-paste display.
- [ ] 1.5 Add marker expansion behavior on submit or explicit expansion request while preserving submitted logical text.
- [ ] 1.6 Add kill-ring-backed delete/yank history for editor delete commands (`deleteWord*`, `deleteToStart`, `deleteToEnd`, etc.).
- [ ] 1.7 Add IME/hardware cursor marker support in focused `Editor`/`Input` output with no marker leakage into logical text values.

## 2. Combined Slash and Path Autocomplete

- [ ] 2.1 Add a combined provider abstraction that composes slash command and file/attachment completion providers.
- [ ] 2.2 Add deterministic prefix parsing for slash-prefixed tokens, quoted paths, `@` attachment markers, and delimiter-aware replacement.
- [ ] 2.3 Add asynchronous file-path suggestion support with cancellable request handle integration and stale-response filtering.
- [ ] 2.4 Extend `Editor` key-trigger behavior so explicit completion mode and force-refresh semantics remain deterministic with combined provider input.
- [ ] 2.5 Add tests covering mixed suggestions, quoted prefixes, stale suggestion results, and completion replacement semantics.

## 3. Markdown Rendering Component

- [ ] 3.1 Implement a concrete default markdown pipeline in `markdown` module under the existing `MarkdownRenderer` API.
- [ ] 3.2 Implement parser abstraction boundaries that allow JVM/Native strategy selection without changing the public API.
- [ ] 3.3 Support and test a minimum markdown subset (headings, paragraphs, inline code, fenced/indented code, emphasis, lists, links, block quotes, horizontal rules, and tables where feasible).
- [ ] 3.4 Add parser error-to-fallback behavior that keeps rendering stable for unsupported constructs.
- [ ] 3.5 Ensure markdown output participates in width-aware truncation and sanitization behavior.

## 4. Image Rendering and Runtime Capability Wiring

- [ ] 4.1 Add capability-aware image model/types and protocol helpers for Kitty and iTerm2 output paths.
- [ ] 4.2 Add `Image` component in core/shared modules that emits protocol escapes only when `TerminalCapabilities.images` is available.
- [ ] 4.3 Add image fallback rendering (readable metadata/placeholder) when image support is unavailable.
- [ ] 4.4 Add image lifecycle helpers for ID reuse/cleanup in helper APIs where protocol defines reuse behavior.
- [ ] 4.5 Add tests for supported protocol rendering, unsupported fallback, and sanitized width-safe output.

## 5. Public API and Documentation

- [ ] 5.1 Extend public APIs and Scaladoc for `Input`, `Editor`, autocomplete providers, image options, and markdown/renderer contracts.
- [ ] 5.2 Update README, parity docs, and smoke docs with explicit new controls, capabilities, and intentional deviations.
- [ ] 5.3 Update post-MVP plan or roadmap files with this batch of parity work and any deferred follow-ups.

## 6. Demo and Test Coverage

- [ ] 6.1 Add/extend shared interactive demo actions to demonstrate new editing parity features (undo/kill-ring/markers), combined autocomplete, markdown, and image fallback.
- [ ] 6.2 Add or extend component/runtime tests for `Input`, `Editor`, autocomplete parsing/completion, markdown renderer, and image capability behavior.
- [ ] 6.3 Add/extend virtual terminal integration tests that cover overlay-assisted completion and over-wide final output behavior with new components.

## 7. Validation

- [ ] 7.1 Run `mill __.compile` and ensure all modules compile.
- [ ] 7.2 Run `mill core.test` with new editor/input, markdown, and autocomplete tests.
- [ ] 7.3 Run demo/interactive-related checks (`mill interactiveDemo.test`, `mill interactiveJvmDemo.compile`, `mill interactiveNativeDemo.nativeLink`).
- [ ] 7.4 Run `mill scalafmtCheck`, `mill scalafixCheck`, and `mill quality`.
- [ ] 7.5 Run `openspec validate --all --strict` and fix any spec-sync issues.
