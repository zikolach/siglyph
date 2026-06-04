## Why

The interactive runtime is now real enough that richer components will quickly put pressure on public APIs for input handling, focus, invalidation, and application commands. Before adding autocomplete, overlays, or a full editor UI, the project needs a small API refinement pass plus a pure multiline text model that can be tested without terminal complexity.

## What Changes

- Refine the public component/input API enough to support richer interactive widgets without committing to autocomplete or overlay implementation yet.
- Add a pure, platform-independent multiline `EditorBuffer` model for line storage, cursor movement, grapheme-aware insertion/deletion, split/merge operations, and paste insertion.
- Add buffer-level callbacks or result types where needed to make editing state changes observable and testable.
- Add focused regression tests for Unicode/grapheme editing, line movement, delete-to-end, paste insertion, and submit-text extraction.
- Document follow-up changes so the editor UI, overlay/autocomplete work, terminal runtime polish, Markdown, and broader public API stabilization can be chained deliberately.
- Do not add runtime dependencies.

## Capabilities

### New Capabilities
- `editor-buffer`: Pure multiline editor buffer operations independent from rendering and terminal backends.

### Modified Capabilities
- `developer-api`: Public component/input API refinements for richer interactive widgets and command/result flow.
- `text-editing`: Multiline editing requirements become grounded in a pure editor buffer foundation.

## Impact

- Affects `core` APIs around components, input handling, focus, and editing models.
- Adds tests under `core.test`; no real terminal, JVM-only API, Native-only API, or dependency changes are expected.
- Prepares follow-up work for a rendered multiline editor component, autocomplete/overlays, terminal runtime polish, Markdown, and final public API cleanup.
