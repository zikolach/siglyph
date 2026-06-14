# Changelog

All notable changes to siglyph will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses semantic versioning while it remains pre-1.0.

## [Unreleased]

### Fixed

- Fixed editor submit callbacks so UI updates made during `onSubmit` render immediately.

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

[Unreleased]: https://github.com/zikolach/siglyph/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/zikolach/siglyph/releases/tag/v0.1.1
[0.1.0]: https://github.com/zikolach/siglyph/releases/tag/v0.1.0
