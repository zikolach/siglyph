# Post-MVP Plan

The first milestone intentionally stopped at the core renderer, terminal abstraction, virtual terminal, and MVP components (`Text`, `Box`, `Spacer`, `SelectList`, `Input`). The project has since added the main `pi-tui`-style runtime and component behaviors: multiline editing, overlays, loaders, Markdown, image protocol helpers, JVM and Scala Native interactive backends, resize-safe rendering, and hardware cursor marker support.

The remaining post-MVP work is now tracked as parity hardening rather than foundational bring-up. The active OpenSpec change for the next batch is:

- `openspec/changes/close-pi-tui-parity-gaps/`

## Components

- `TruncatedText`: single-line status/header component with ANSI-safe truncation and padding. Initial implementation is in place.
- `SettingsList`: selectable setting rows with value cycling, descriptions, scroll indicators, cancellation callbacks, and optional dependency-free containment filtering. Follow-ups: fuzzy ranking and submenu support.
- `Loader` / `CancellableLoader`: shared-core, tick-driven loader components with cancellation support. Follow-up: optional runtime-owned scheduler integration only if real applications need it.
- Overlays: composited modal/non-modal components with placement, sizing, visibility, z-order, focus policies, and handle lifecycle. Initial implementation is in place.
- `SelectList`: basic selection and navigation are in place. Follow-ups: richer theme hooks, filtering, fuzzy ranking, selection-change callbacks, and layout customization.
- Image helpers: Kitty/iTerm2 escape encoders and fallback rendering are in place through dependency-free core helpers plus the optional `image` module. Follow-ups: optional file loading, dimension sniffing, and size-bounding helpers.

## Editor and autocomplete

- `EditorBuffer`: pure text model for multiline editing, grapheme-aware movement, split/merge, delete, paste insertion, large-paste marker compaction, and marker expansion. Initial foundation is in place.
- Multiline `Editor`: rendered editor with visual wrapping, fake cursor, cursor marker emission, core editing keys, callbacks, configurable submit/newline behavior, undo/kill-ring/yank/yank-pop, autocomplete overlays, and large paste markers. Opt-in runtime hardware cursor positioning is in place.
- Autocomplete APIs: slash commands, path/attachment parsing, cancellable provider abstraction, selectable suggestion UI, and combined provider contracts are in place. Follow-ups: dependency-free fuzzy ranking, optional filesystem path provider, debounce/cancellation polish, and stacked/natural trigger prefixes such as `#`.
- Paste expansion: visible large-paste markers expand in `onSubmit` and through explicit editor APIs.

Suggested follow-up chain for `pi-tui` parity:

1. Add fuzzy ranking and optional filesystem autocomplete helpers.
2. Extend `SelectList` and `SettingsList` with richer theming/filtering/submenu parity.
3. Add richer Markdown theme hooks, hyperlink handling, syntax-highlighting hooks, and optional parser adapters.
4. Add optional image file loading, dimension sniffing, and size-bounding helpers.
5. Improve advanced terminal keyboard protocol support, including conservative Kitty negotiation and key release/repeat metadata.
6. Consider optional loader scheduler integration if applications need runtime-owned ticking beyond the current deterministic `tick()` API.
7. Continue public API stabilization after editor, overlay, autocomplete, and selector/settings pressure is real.

## Markdown

- The `markdown` module is separate from `core` and currently provides a dependency-free baseline parser/renderer.
- The baseline renderer keeps headings, paragraphs, code, lists, links, block quotes, horizontal rules, and simple tables readable and width-safe.
- Follow-ups should focus on theme parity, OSC 8 link rendering when terminal capabilities allow it, optional syntax highlighting hooks, and optional parser adapters.
- Parser/highlighter dependencies must remain optional and require explicit approval before adoption.

## Images

- Terminal capability detection and Kitty/iTerm2 protocol helpers are present in core.
- The optional `image` module provides a dependency-free `Image` component for already-base64 image data and caller-supplied dimensions.
- Follow-ups should add optional helpers for loading files, detecting PNG/JPEG/GIF/WebP dimensions, and calculating bounded cell sizes.
- Scaling, transcoding, or richer media handling should stay in optional modules if they require third-party dependencies.
- Fallback rendering must remain readable text when image protocols are unsupported.

## Terminal runtime

- The JVM backend uses an interactive Unix `stty` compatibility layer.
- The Scala Native backend uses POSIX termios/ioctl APIs for raw mode, stdin reads, dimensions, and resize polling on macOS/Linux.
- Shared terminal input buffering handles fragmented escape sequences and bracketed paste.
- Follow-ups should improve advanced keyboard parity: conservative Kitty keyboard protocol negotiation, key release/repeat event metadata, super/keypad edge cases, and safe platform-specific modifier fallbacks where practical.

## Intentional deviations from pi-tui so far

- Components receive typed `TerminalInput` events rather than raw escape strings.
- Markdown is a separate pluggable module rather than a core dependency.
- The JVM backend starts with `stty` instead of JLine.
- The Scala Native POSIX backend is implemented, but terminal behavior remains scoped to macOS/Linux rather than Windows.
- Loader animation is application- or test-driven through `tick()` rather than owning a Node-style interval.
- Autocomplete path enumeration is currently application-supplied; built-in filesystem helpers are planned as optional parity work.
- Image rendering currently expects caller-supplied base64 data and dimensions; file loading and dimension sniffing are planned optional helpers.
