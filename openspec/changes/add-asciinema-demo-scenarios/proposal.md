## Why

The current demo commands prove functionality, but they do not produce short, repeatable recordings that explain siglyph visually. Purpose-built asciinema scenarios will make README and release demos clearer without requiring manual terminal interaction or adding build-time requirements.

## What Changes

- Add deterministic local recording scripts for polished asciinema scenarios.
- Add purpose-built demo flows that emphasize visible typing, autocomplete, command palette behavior, settings changes, Unicode-safe editing, and typed terminal input.
- Document how to record, replay, and upload casts for README usage.
- Keep generated `.cast` files out of required build outputs unless a maintainer explicitly records and publishes them.
- Do not add asciinema, renderers, Node.js, npm, or browser tooling as runtime or CI dependencies.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `developer-api`: demo and documentation requirements for deterministic asciinema recording scenarios and local playback/upload instructions.

## Impact

- Affected files: demo scripts or demo entry points, README or docs, OpenSpec developer API spec.
- Public API impact: none intended.
- Dependency impact: no new runtime dependency and no required CI dependency.
- Platform impact: recording scripts target local developer environments with asciinema available; normal build and test commands remain independent of asciinema.
