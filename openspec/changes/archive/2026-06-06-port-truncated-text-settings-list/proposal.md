## Why

`scala-tui` has the core runtime, editor, overlays, autocomplete, and basic components, but it still lacks several small `pi-tui` utility widgets that applications need for status lines and configuration screens. Porting `TruncatedText` and `SettingsList` improves component parity while avoiding the timer/cancellation complexity of loader components until a focused follow-up change.

## What Changes

- Add a `TruncatedText` component for single-line, ANSI-aware truncation with horizontal and vertical padding.
- Add a `SettingsList` component for interactive settings rows with labels, current values, descriptions, value cycling, cancellation, and width-safe rendering.
- Add settings-list configuration models, item models, and callbacks using typed Scala APIs and typed terminal input.
- Reuse existing `Input`, `SelectList`, Unicode, ANSI, and component contracts where practical.
- Include optional search/filter behavior only if it can be implemented dependency-free and tested without expanding scope excessively.
- Defer animated `Loader` and `CancellableLoader` implementation to a follow-up `add-loader-components` change because loader animation raises timer, lifecycle, and Scala Native compatibility questions.
- Add a final exploration/planning task for `add-loader-components`, capturing recommended scope and OpenSpec direction after this change lands.

## Capabilities

### New Capabilities

<!-- No new standalone capability; this change extends the existing component and API capabilities. -->

### Modified Capabilities
- `component-rendering`: Add render and input behavior requirements for `TruncatedText` and `SettingsList` components.
- `developer-api`: Add public API, documentation, dependency, and validation expectations for the new utility components and the loader follow-up plan.

## Impact

- Affected core APIs: new `TruncatedText`, `SettingsList`, `SettingItem`, settings options/theme or style models, and possibly small `SelectList`/`Input` helpers.
- Affected tests: component rendering, Unicode/ANSI truncation, settings navigation/value cycling/search, callbacks, and narrow-width safety.
- Affected docs: README, porting notes, and possibly interactive smoke/demo notes if the shared demo adopts these widgets.
- Dependencies: no new third-party runtime dependencies; loader animation/cancellation remains deferred to a separate proposal.
