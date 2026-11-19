## Why

Up/Down currently move logical lines despite visual wrapping and history discards the interrupted draft. Improve composer interaction without adding application chat infrastructure.

## What Changes

- Move Up/Down through rendered visual rows with a persistent preferred display column and safe source projection.
- Enter history deliberately from a nonempty draft at the first visual row's start; restore exact draft text, cursor and private paste metadata.
- Add deterministic shared tests and a runnable core-only conversational composer example.
- Defer public full-editor snapshots and arbitrary key-policy configuration.

## Capabilities

### Modified Capabilities
- `text-editing`: visual-row navigation and draft-preserving prompt history.

## Impact

Shared Editor and private layout mappings; JVM and Native reuse the same implementation. No runtime dependencies or new public API.
