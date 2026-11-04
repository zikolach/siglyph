## Why

Viewport metadata previously committed independently of revision-checked selection text, allowing an obsolete child render to publish semantic navigation state.

## What Changes

- Capture content revision before measurement and conditionally clamp early geometry by child identity and revision.
- Publish immutable rows, metadata, child, revision, and measured geometry through one viewport snapshot callback.
- Accept current snapshots atomically in ScrollView, retaining source-compatible legacy provider defaults.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `fullscreen-viewport`: revision-aware atomic publication of viewport document state.

## Impact

Shared Layout and ScrollView internals and focused tests. No renderer, overlay, terminal backend, or TUI changes.
