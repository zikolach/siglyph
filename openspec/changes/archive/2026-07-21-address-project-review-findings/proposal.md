## Why

Siglyph now covers the complete high-level `pi-tui` feature surface, but a current parity and design review found correctness gaps in multiline text, keyboard decoding, session isolation, cancellation, and autocomplete plus performance, diagnostics, platform-validation, and documentation gaps. Addressing them together establishes a reliable compatibility baseline before the project expands its public surface further.

## What Changes

- Render CRLF, CR, and LF as logical line boundaries in text components without weakening ordinary-string terminal-output sanitization.
- Expand typed key parsing and its parity corpus for function, SS3, legacy modifier, keypad, control-byte, and supported platform-fallback sequences.
- Replace process-global terminal cell geometry with runtime/session-owned image sizing state.
- Make public loader cancellation safe and idempotent across threads.
- Define filesystem autocomplete policies for current-directory traversal, sandbox containment, home/absolute paths, deterministic limits, and optional bounded recursive attachment search; align examples and smoke documentation with the implemented policy.
- Cache Markdown parsing/rendering by all behavior-affecting inputs while retaining the dependency-free baseline and optional parser boundary.
- Add session-scoped, redaction-safe terminal write/redraw diagnostics and a configurable resize clearing policy whose default preserves current behavior.
- Strengthen terminal conformance validation with macOS CI and focused PTY/emulator-style coverage while retaining the fast `VirtualTerminal` unit fake.
- Publish an explicit `pi-tui` compatibility matrix, repair stale roadmap references, and archive superseded completed OpenSpec work.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `component-rendering`: Require cross-platform logical line-ending handling in text components while preserving the trusted-output boundary.
- `terminal-runtime`: Expand typed keyboard coverage, isolate terminal geometry per runtime, add scoped diagnostics, make resize clearing configurable, and define higher-fidelity terminal conformance coverage.
- `image-rendering`: Require runtime cell metrics to be session-owned so concurrent image-capable TUIs cannot affect one another.
- `autocomplete`: Define safe traversal and recursive-search policies plus deterministic, cancellable filesystem completion behavior.
- `markdown-rendering`: Require render-result caching with correct invalidation while keeping richer parsers optional and dependency-isolated.
- `developer-api`: Require thread-safe loader cancellation, source-compatible diagnostic and resize configuration, cross-platform validation, and maintained compatibility documentation.

## Impact

- Shared core text, input, loader, runtime, render, autocomplete, image-protocol, and virtual-terminal APIs and tests.
- JVM and Scala Native terminal backends, with additional platform-specific keyboard and PTY validation where supported.
- Markdown and image modules on JVM and Scala Native.
- CI workflows, README, porting notes, smoke instructions, roadmap notes, and OpenSpec lifecycle artifacts.
- No new core runtime dependency. Any future full CommonMark adapter remains optional and requires separate dependency approval.
- Public configuration additions are source-compatible; removing process-global image geometry may require a deprecated compatibility bridge for direct low-level callers.
