## Why

The editor already implements the main multiline, history, autocomplete, and Unicode behaviors, but upstream `pi-tui` contains many small editor tests where semantic drift can hide. An explicit audit prevents speculative fixes and identifies which gaps need implementation specs.

## What Changes

- Compare upstream `pi-tui` editor-related tests with local Scala tests and behavior.
- Classify every checked behavior as already covered, missing test only, behavior gap, or intentional deviation.
- Add missing tests only where behavior is already present and the test does not require product behavior changes.
- Produce follow-up tasks or OpenSpec proposals for real behavior gaps instead of bundling fixes into the audit.
- Preserve existing editor APIs unless a later implementation change is approved.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `editor-buffer`: audit expectations for editor buffer and cursor behavior against upstream fixtures.
- `component-rendering`: audit expectations for editor visual layout, wrapping, and cursor rendering.
- `autocomplete`: audit expectations for editor autocomplete interaction and stale request behavior.
- `text-editing`: audit expectations for input/history/editing key behavior where covered by existing specs.

## Impact

- Affected modules: `core` tests, docs/porting notes, possible follow-up OpenSpec artifacts.
- Public API impact: none for the audit itself.
- Dependency impact: no new runtime dependencies.
- Platform impact: audit findings should distinguish shared behavior from backend-specific input behavior.
