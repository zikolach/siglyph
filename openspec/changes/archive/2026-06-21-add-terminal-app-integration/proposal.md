## Why

`siglyph` now has broad core TUI parity, but applications still lack upstream `pi-tui` terminal integration features for window title, terminal progress, background color queries, and color-scheme queries. Adding these capabilities closes visible runtime API gaps while preserving the existing backend abstraction.

## What Changes

- Add optional terminal title support without widening the base `Terminal` contract.
- Add optional fire-and-forget terminal progress support using OSC 9;4 where supported.
- Add `TUI`-owned terminal background color query behavior based on OSC 11 replies.
- Add `TUI`-owned terminal color-scheme query and change-notification behavior.
- Ensure terminal protocol replies used by runtime queries are consumed before they reach focused components.
- Keep cell-size and image capability queries out of this change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `terminal-runtime`: terminal title/progress capabilities, runtime-owned color/background query handling, and protocol reply routing.
- `developer-api`: optional capability APIs and documentation expectations for new public runtime integration methods.

## Impact

- Affected modules: `core`, JVM terminal backend, Native terminal backend, virtual terminal tests, README/docs.
- Public API impact: new optional capability traits or helper APIs; no required new abstract methods on `Terminal`.
- Dependency impact: no new runtime dependencies.
- Platform impact: JVM and Scala Native backends must preserve existing input/render behavior when a terminal does not support these protocols.
