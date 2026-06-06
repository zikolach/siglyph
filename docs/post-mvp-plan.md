# Post-MVP Plan

The first milestone intentionally stops at the core renderer, terminal abstraction, virtual terminal, and MVP components (`Text`, `Box`, `Spacer`, `SelectList`, `Input`). The following features are planned after that milestone.

## Components

- `TruncatedText`: single-line status/header component with ANSI-safe truncation and padding. Initial implementation is in place.
- `SettingsList`: selectable setting rows with value cycling, descriptions, scroll indicators, and optional dependency-free filtering. Initial implementation is in place; submenus and fuzzy ranking remain follow-ups.
- `Loader`: tick-driven spinner/message component with cancellation support. Initial implementation is in place; automatic scheduler integration remains a follow-up.
- Overlays: composited modal/non-modal components with placement, sizing, visibility, and focus policies. Initial implementation is in place.
- Image helpers: Kitty/iTerm2 escape encoders and fallback rendering are in place through dependency-free core helpers plus the optional `image` module. Image dimension sniffers/file loaders/scalers remain optional follow-ups.

## Editor and autocomplete

- `EditorBuffer`: pure text model for multiline editing, grapheme-aware movement, split/merge, delete, paste insertion, large-paste marker compaction, and marker expansion. Initial foundation is in place.
- Multiline `Editor`: rendered editor with visual wrapping, fake cursor, cursor marker emission, core editing keys, callbacks, configurable submit/newline behavior, undo/kill-ring/yank/yank-pop, autocomplete overlays, and large paste markers. Runtime-owned hardware cursor placement remains a follow-up.
- Autocomplete APIs: slash commands, path/attachment parsing, cancellable provider abstraction, and selectable suggestion UI are in place. Concrete filesystem enumeration can be supplied by applications or future optional helper modules.
- Paste expansion: visible large-paste markers expand in `onSubmit` and through explicit editor APIs.

Suggested follow-up chain after the editor-buffer foundation:

1. Optional loader scheduler integration if applications need runtime-owned ticking beyond the current manual `tick()` API.
2. Runtime-owned hardware cursor positioning using emitted cursor markers, if needed for IME-heavy applications.
3. SettingsList submenus and fuzzy ranking if real applications need them.
4. Optional filesystem autocomplete helpers if applications want built-in local path enumeration.
5. Richer Markdown parser adapters for JVM/Native-compatible rendering.
6. Public API stabilization after editor, overlay, and settings-list pressure is real.

## Images

- Terminal capability detection and Kitty/iTerm2 protocol helpers are present in core.
- The optional `image` module provides a dependency-free `Image` component for already-base64 image data and caller-supplied dimensions.
- Keep file loading, header parsing, scaling, and transcoding optional. Fallback remains readable text when unsupported.

## Intentional deviations from pi-tui so far

- Components receive typed `TerminalInput` events rather than raw escape strings.
- Markdown is a separate pluggable module rather than a core dependency.
- The JVM backend starts with `stty` instead of JLine.
- The Native backend is compiled as a Scala Native module, but POSIX raw-mode implementation remains pending.
