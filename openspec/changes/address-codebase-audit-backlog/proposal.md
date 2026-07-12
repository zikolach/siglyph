## Why

Nine retained audit findings identify concrete correctness, portability, identity, and test-reliability gaps. Addressing them as one explicitly partitioned backlog change keeps each fix independently executable while providing one complete quality-gated integration path.

## What Changes

- Constrain extras and image fallback output after theme callbacks using ANSI-aware visible-width truncation.
- Make CI run Scalafmt, Scalafix, and strict OpenSpec validation, with Scalafix covering every canonical current Scala source and test root.
- Expand portable Scala Native test coverage for core, extras, and the interactive demo by reusing classified canonical suites.
- Normalize current product identity from `scala-tui` to `siglyph` while retaining the `scalatui` package namespace and excluding archives, historical material, and agent-rule files unless their modification is separately and explicitly approved. Normal archive synchronization also updates the promoted `developer-api` Purpose metadata to identify the product as `siglyph`.
- Restrict basic Markdown parser recovery to `scala.util.control.NonFatal` and make direct Markdown rendering safe at width zero.
- Reject zero, negative, and non-representable sniffed image dimensions through the existing typed `InvalidImage` failure.
- Return precise `InputResult` values from `Input` and `SelectList` without changing callback conditions or callback cardinality.
- Replace fixed readiness sleeps in concurrency tests with deterministic bounded gates or latches while retaining sleeps that test actual timeout or polling behavior.
- Add focused validation and a final integration pass without new dependencies, compatibility layers, fallback behavior, or unrelated refactoring.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `extras-widgets`: Require final themed widget output to remain within the requested visible width.
- `image-rendering`: Require themed fallback output to remain width-safe and sniffed dimensions to be positive and representable.
- `developer-api`: Define the `siglyph` product identity and mandatory CI formatting, lint, and strict OpenSpec gates.
- `markdown-rendering`: Limit parser recovery to non-fatal failures and require direct rendering to honor width zero.
- `text-editing`: Define precise `Input` handling results without changing callback behavior.
- `component-rendering`: Define precise `SelectList` handling results without claiming parent input bubbling.

## Impact

Affected areas are extras and image rendering, image metadata sniffing, Markdown parsing/rendering, `Input` and `SelectList` input handling, Mill Native test wiring, concurrency-focused tests, current project documentation and metadata, and CI configuration. The package namespace remains `scalatui`; runtime and test dependencies remain unchanged. Archives, changelog history, historical notes, unrelated audit ideas, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files are excluded. Identity searches classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved. Application implementation does not directly edit promoted specs. Capability deltas are promoted through normal archive synchronization, and the archive handoff updates the non-requirement Purpose metadata in `openspec/specs/developer-api/spec.md` from `scala-tui` to `siglyph`.
