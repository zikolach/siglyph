# Post-MVP Plan

The first milestone intentionally stopped at the core renderer, terminal abstraction, virtual terminal, and MVP components (`Text`, `Box`, `Spacer`, `SelectList`, `Input`). The project has since added the main `pi-tui`-style runtime and component behaviors: multiline editing, overlays, loaders, Markdown, image protocol helpers, JVM and Scala Native interactive backends, resize-safe rendering, and hardware cursor marker support.

The remaining post-MVP work is now tracked as parity hardening rather than foundational bring-up. The active OpenSpec change for the next batch is:

- `openspec/changes/close-pi-tui-parity-gaps/`

## Components

- `TruncatedText`: single-line status/header component with ANSI-safe truncation and padding. Initial implementation is in place.
- `SettingsList`: selectable setting rows with value cycling, descriptions, scroll indicators, cancellation callbacks, optional containment filtering, optional fuzzy ranking, and application-provided submenus through overlay contracts.
- `Loader` / `CancellableLoader`: shared-core, tick-driven loader components with cancellation support. Follow-up: optional runtime-owned scheduler integration only if real applications need it.
- Overlays: composited modal/non-modal components with placement, sizing, visibility, z-order, focus policies, and handle lifecycle. Initial implementation is in place.
- `SelectList`: selection, navigation, richer theme hooks, filtering, fuzzy ranking, selection-change callbacks, and layout customization are in place.
- Image helpers: Kitty/iTerm2 escape encoders and fallback rendering are in place through dependency-free core helpers plus the optional `image` module. File loading, PNG/JPEG/GIF/WebP dimension sniffing, and size-bounding helpers are in place. Scaling and transcoding remain optional dependency-backed work.

## Editor and autocomplete

- `EditorBuffer`: pure text model for multiline editing, grapheme-aware movement, split/merge, delete, paste insertion, large-paste marker compaction, and marker expansion. Initial foundation is in place.
- Multiline `Editor`: rendered editor with visual wrapping, fake cursor, cursor marker emission, core editing keys, callbacks, configurable submit/newline behavior, undo/kill-ring/yank/yank-pop, autocomplete overlays, and large paste markers. Opt-in runtime hardware cursor positioning is in place.
- Autocomplete APIs: slash commands, path/attachment parsing, dependency-free filesystem completion, cancellable provider abstraction, selectable suggestion UI, combined provider contracts, optional fuzzy ranking, debounce/cancellation behavior, and stacked/natural trigger prefixes such as `#` are in place.
- Paste expansion: visible large-paste markers expand in `onSubmit` and through explicit editor APIs.

Suggested follow-up chain for `pi-tui` parity:

1. Continue validating fuzzy ranking, filesystem autocomplete, natural triggers, and cancellation behavior in real applications.
2. Continue validating `SelectList` and `SettingsList` theme/filter/submenu APIs under application pressure.
3. Add optional Markdown parser modules only after dependency approval and platform-scope decisions.
4. Add optional image scaling/transcoding modules only after dependency approval and platform-scope decisions.
5. Expand advanced keyboard protocol support only where behavior is safe and testable on JVM and Native backends.
6. Consider optional loader scheduler integration if applications need runtime-owned ticking beyond the current deterministic `tick()` API.
7. Continue public API stabilization after editor, overlay, autocomplete, and selector/settings pressure is real.

## Markdown

- The `markdown` module is separate from `core` and currently provides a dependency-free baseline parser/renderer.
- The baseline renderer keeps headings, paragraphs, code, lists, links, block quotes, horizontal rules, and simple tables readable and width-safe.
- Theme hooks, OSC 8 link rendering when terminal capabilities allow it, optional syntax-highlighting hooks, and parser adapter boundaries are in place.
- Parser/highlighter dependencies must remain optional and require explicit approval before adoption.

## Images

- Terminal capability detection and Kitty/iTerm2 protocol helpers are present in core.
- The optional `image` module provides a dependency-free `Image` component for already-base64 image data and caller-supplied dimensions.
- The optional `image` module also provides helpers for loading files, detecting PNG/JPEG/GIF/WebP dimensions, and calculating bounded cell sizes.
- Scaling, transcoding, or richer media handling should stay in optional modules if they require third-party dependencies.
- Fallback rendering must remain readable text when image protocols are unsupported.

## Terminal runtime

- The JVM backend uses an interactive Unix `stty` compatibility layer.
- The Scala Native backend uses POSIX termios/ioctl APIs for raw mode, stdin reads, dimensions, and resize polling on macOS/Linux.
- Shared terminal input buffering handles fragmented escape sequences and bracketed paste.
- Shared input parsing supports press, repeat, and release event metadata, super modifiers, and CSI-u base-code handling for shifted/keypad-style events.
- JVM and Native backends expose conservative Kitty keyboard protocol negotiation hooks and testable public state. Platform-specific modifier fallbacks are documented but implemented only where behavior is safe and testable.

## Intentional deviations from pi-tui so far

- Components receive typed `TerminalInput` events rather than raw escape strings.
- Markdown is a separate pluggable module rather than a core dependency.
- The JVM backend starts with `stty` instead of JLine.
- The Scala Native POSIX backend is implemented, but terminal behavior remains scoped to macOS/Linux rather than Windows.
- Loader animation is application- or test-driven through `tick()` rather than owning a Node-style interval.
- Autocomplete path enumeration is available through the dependency-free `FileSystemPathCompletionProvider`; applications can still supply custom providers.
- Image rendering still accepts caller-supplied base64 data and dimensions, and the optional `image` module can now load supported files or bytes into that contract.
