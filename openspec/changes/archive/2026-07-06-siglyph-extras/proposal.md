## Why

Siglyph has core `pi-tui` primitives, but some useful non-agentic Pi UI patterns still belong above the primitive component layer. A separate extras module gives applications reusable composite widgets without adding agent-session concepts or dependencies to core.

## What Changes

- Add a new optional `siglyph-extras` module that depends on `siglyph-core` only.
- Introduce reusable expandable UI helpers inspired by Pi's non-agentic TUI patterns:
  - an `Expandable` opt-in protocol,
  - `ExpandableText`,
  - `ExpandableSection`,
  - an expansion controller for applying one expansion state across registered components.
- Keep agent-specific concepts out of the module, including sessions, tools, model selection, LLM messages, and extension runtime APIs.
- Add tests and documentation for the new public extras APIs.
- Add no new runtime dependencies.

## Capabilities

### New Capabilities
- `extras-widgets`: Reusable optional UI helpers that sit above core components, beginning with expandable text/section widgets and expansion-state coordination.

### Modified Capabilities
- `developer-api`: Public module architecture and documentation requirements expand to include the optional `siglyph-extras` artifact.

## Impact

- Affected code: `build.mill`, new `extras/` sources and tests, README/docs, publishing/release metadata as needed.
- Public APIs: new `scalatui.extras` package APIs for expandable widgets and coordination.
- Dependencies: no new third-party runtime dependencies.
- Platform scope: shared JVM and Scala Native sources through the same source-root pattern used by existing shared modules where publishing requires it.
