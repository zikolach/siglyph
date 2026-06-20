# Changelog

All notable changes to siglyph will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses semantic versioning while it remains pre-1.0.

## [Unreleased]

## [0.2.0] - 2026-06-20

### Added

- Closed major `pi-tui` parity gaps across autocomplete, selectors, settings,
  Markdown rendering, image helpers, and terminal keyboard handling. #1
- Added dependency-free fuzzy matching and optional fuzzy ranking for
  autocomplete, `SelectList`, and `SettingsList`. #1
- Added filesystem path completion, attachment-style path completion, natural
  trigger prefixes such as `#`, and debounced/cancellable autocomplete refresh.
  #1
- Added richer `SelectList` and `SettingsList` options, theme hooks, filtering,
  selection-change callbacks, and settings submenus. #1
- Added Markdown theme hooks, OSC 8 link rendering, optional highlighter hooks,
  and parser adapter boundaries while keeping the baseline module dependency-free.
  #1
- Added image file loading, header-only dimension sniffing, and cell sizing
  helpers around existing terminal image protocol output. #1
- Added conservative Kitty keyboard protocol negotiation and typed key event
  metadata for press, repeat, and release events. #1

### Changed

- Updated terminal input APIs around `KeyEvent` while keeping `TerminalInput.Key`
  as a compatibility constructor and extractor where practical. #1
- Updated README, docs, examples, and demos for the expanded parity behavior. #1

### Fixed

- Prevented key-release events from triggering keybindings or global `Ctrl+C`
  exit handling. #1
- Validated PNG IHDR chunks before reading image dimensions and narrowed image
  file-read error handling to expected I/O and security failures. #1
- Made the JVM Kitty keyboard negotiation expiry test avoid fixed-duration
  sleep assumptions. #1
- Made `SettingsList` filter backspace delete a full grapheme cluster. #1

## [0.1.2] - 2026-06-14

### Fixed

- Fixed editor submit callbacks so UI updates made during `onSubmit` render
  immediately.

## [0.1.1] - 2026-06-14

### Added

- Maven Central publishing workflow and publishing documentation.
- Scala CLI executable demos for quick local or Gist-based experiments.
- Open-source project metadata: license, contribution guide, security policy,
  code of conduct, and upstream attribution notice.

## [0.1.0] - 2026-06-13

### Added

- Core component/rendering API with `Component`, `Focusable`, `Container`,
  differential rendering, overlays, cursor markers, and virtual terminal tests.
- Unicode-aware text editing primitives and rendered multiline editor.
- Configurable keybinding manager covering editor, input, autocomplete, and
  select-list commands.
- JVM and Scala Native terminal backends behind shared terminal abstractions.
- Utility components including `Text`, `Box`, `Spacer`, `Input`, `SelectList`,
  `SettingsList`, `Loader`, and `CancellableLoader`.
- Pluggable Markdown and image modules.
- Interactive and non-interactive demos.
- GitHub Actions CI, jar packaging, GitHub Packages publishing, and GitHub
  release artifacts.

[Unreleased]: https://github.com/zikolach/siglyph/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/zikolach/siglyph/releases/tag/v0.2.0
[0.1.2]: https://github.com/zikolach/siglyph/releases/tag/v0.1.2
[0.1.1]: https://github.com/zikolach/siglyph/releases/tag/v0.1.1
[0.1.0]: https://github.com/zikolach/siglyph/releases/tag/v0.1.0
