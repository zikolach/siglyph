## Why

`siglyph` now covers the main `pi-tui` runtime, editing, and component behaviors, but the remaining parity gaps are concentrated in a few user-visible areas: autocomplete quality, Markdown fidelity, image convenience, selector/settings richness, and advanced terminal keyboard handling. Closing these gaps will make migration from `pi-tui` more predictable while preserving the project's Scala-first, dependency-light design.

## What Changes

- Add a dependency-free fuzzy ranking utility and wire it into autocomplete and selector-like components where appropriate.
- Add an optional filesystem path completion helper for autocomplete, including deterministic cancellation and path filtering behavior without making shell tools or runtime dependencies mandatory.
- Expand autocomplete trigger coverage to include stacked/natural trigger prefixes such as `#` in addition to existing slash/path/attachment flows.
- Improve Markdown parity through richer theming and optional parser/highlighter adapters while keeping the baseline module dependency-free and readable on JVM/Native.
- Add optional image convenience helpers for file loading, dimension sniffing, and size bounding while keeping protocol emission contracts stable.
- Improve `SelectList` and `SettingsList` parity with theme hooks, filtering/fuzzy ranking, selection-change callbacks, and settings submenus.
- Add terminal/key protocol improvements for Kitty keyboard negotiation, key release/repeat modeling, and platform-specific modifier fallbacks where practical.
- Refresh post-MVP documentation to reflect implemented Native POSIX backend and the new parity roadmap.

## Capabilities

### New Capabilities

None. This change extends existing parity-related capabilities.

### Modified Capabilities

- `autocomplete`: fuzzy ranking, optional filesystem provider, stacked trigger prefixes, and cancellation/debounce expectations.
- `markdown-rendering`: richer pi-tui Markdown parity through theme hooks, OSC 8 links where supported, optional parser/highlighter adapters, and clearer baseline limitations.
- `image-rendering`: optional file/dimension/scaling helpers around existing Kitty/iTerm2 protocol output.
- `component-rendering`: richer selector/settings component behavior including theming, filtering, callbacks, and submenus.
- `terminal-runtime`: advanced keyboard protocol negotiation and typed key release/repeat support.

## Impact

- Affected modules: `core`, `markdown`, `image`, JVM/Native terminal backends, demos, tests, README/docs.
- Public API impact: additive APIs for fuzzy matching, autocomplete helpers, image helpers, component options/themes, and terminal input metadata. No breaking changes intended.
- Dependency impact: baseline `core`, `markdown`, and `image` remain dependency-light. Optional parser/image/helper dependencies require explicit documentation and approval before adoption.
- Platform impact: JVM and Scala Native parity must remain explicit; platform-specific helpers should be optional or isolated behind existing abstractions.
