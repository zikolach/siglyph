# Post-MVP Plan

The first milestone intentionally stops at the core renderer, terminal abstraction, virtual terminal, and MVP components (`Text`, `Box`, `Spacer`, `SelectList`, `Input`). The following features are planned after that milestone.

## Components

- `TruncatedText`: single-line status/header component with ANSI-safe truncation and padding.
- `Loader`: animated spinner driven by the TUI render scheduler.
- `SettingsList`: selectable setting rows with value cycling and optional submenus.
- Overlays: composited modal/non-modal components with placement, sizing, visibility, and focus policies.
- Image helpers: Kitty/iTerm2 escape encoders, image dimension sniffers, and fallback rendering.

## Editor and autocomplete

- `EditorBuffer`: pure text model for multiline editing, grapheme-aware movement, split/merge, delete, undo, and kill-ring helpers. Initial foundation is in place; undo/kill-ring and large-paste marker expansion remain follow-ups.
- Multiline `Editor`: visual wrapping, fake cursor, IME cursor marker, submit/newline key handling, and large paste markers.
- Autocomplete APIs: slash commands, file paths, `@` attachment paths, provider abstraction, and selectable suggestion UI.
- Paste expansion: replace visible large-paste markers with original content on submit.

Suggested follow-up chain after the editor-buffer foundation:

1. Rendered multiline editor demo using `EditorBuffer`.
2. Overlay and autocomplete components anchored by the editor.
3. Terminal runtime polish: resize signals, PTY/manual smoke coverage, lifecycle docs.
4. Markdown parser selection and JVM/Native-compatible renderer.
5. Public API stabilization after editor and overlay pressure is real.

## Images

- Start with terminal capability detection already present in the core.
- Add Kitty and iTerm2 protocol encoders in a later helper module or component package.
- Keep images optional and fallback to readable text when unsupported.

## Intentional deviations from pi-tui so far

- Components receive typed `TerminalInput` events rather than raw escape strings.
- Markdown is a separate pluggable module rather than a core dependency.
- The JVM backend starts with `stty` instead of JLine.
- The Native backend is compiled as a Scala Native module, but POSIX raw-mode implementation remains pending.
