## Context

The current implementation has reached broad `pi-tui` parity for the core renderer, typed input model, Unicode-aware editing, overlays, loaders, basic Markdown, and terminal image protocol emission. The remaining gaps are concentrated in quality-of-life features and richer helpers that upstream `pi-tui` users rely on: fuzzy completion/ranking, built-in path completion behavior, Markdown fidelity, image convenience helpers, richer selector/settings APIs, and advanced keyboard protocol support.

The project constraints still apply: shared core remains Scala 3, dependency-light, JVM/Native-friendly, and testable through virtual/stream terminals. Optional dependencies are acceptable only when isolated in optional modules and explicitly documented.

## Goals / Non-Goals

**Goals:**

- Close the most visible `pi-tui` parity gaps without making `siglyph` a TypeScript API clone.
- Keep default/shared modules dependency-light and deterministic.
- Prefer pure, testable helpers for fuzzy ranking, prefix parsing, Markdown rendering decisions, image metadata, and terminal input parsing.
- Keep JVM and Scala Native behavior either shared or explicitly platform-scoped.
- Make intentional deviations from `pi-tui` explicit in docs and tests.

**Non-Goals:**

- No breaking rewrite of the existing typed `TerminalInput`, component, or autocomplete contracts.
- No mandatory Markdown parser, image decoding, shell-tool, JLine, or effect-runtime dependency in `core`.
- No attempt to support Windows or Scala.js as part of this parity pass.
- No implicit command execution for slash or stacked autocomplete entries; applications remain responsible for semantics.

## Decisions

### 1. Add fuzzy ranking as a pure shared-core utility

Introduce a dependency-free fuzzy matching/ranking utility modeled after `pi-tui`'s behavior: ordered character matching, boundary/consecutive/exact-match scoring, and tokenized multi-word filtering. Use it in optional ranking paths for autocomplete, `SelectList`, and `SettingsList` rather than hard-wiring fuzzy behavior everywhere.

Alternatives considered:
- Pull a third-party fuzzy matcher: rejected for dependency surface and Native uncertainty.
- Implement only containment filtering: already exists in `SettingsList`, but does not match upstream result quality.

### 2. Keep filesystem autocomplete helper optional and injectable

Add a built-in helper that can enumerate paths using Java/NIO or platform-appropriate facilities, but keep the existing `PathCompletionProvider` boundary. The combined provider should work with application-supplied providers and with an optional default provider. Shell tools such as `fd` may be supported as an optional strategy, not a requirement.

Alternatives considered:
- Mirror `pi-tui`'s `fd` spawning exactly: rejected as mandatory shell-tool dependency and Native portability risk.
- Leave all filesystem enumeration to applications: safe but leaves a high-value parity gap.

### 3. Treat richer Markdown as adapter-driven

Enhance the baseline renderer only where it can remain dependency-free and width-safe. Rich parsing, syntax highlighting, and advanced CommonMark behavior should live behind optional parser/highlighter adapters. Theme hooks can be expanded without requiring a parser dependency.

Alternatives considered:
- Add a JVM Markdown parser directly to the baseline module: rejected because Native compatibility and dependency approval are unresolved.
- Keep Markdown minimal forever: rejected because `pi-tui` migration often depends on styled Markdown output.

### 4. Split image protocol output from image convenience helpers

Keep current Kitty/iTerm2 protocol encoding in core/image contracts. Add optional helpers for file loading, image header dimension sniffing, and cell-size bounding; avoid mandatory transcoding/scaling dependencies. Any richer scaling/transcoding helper should live in a separate optional module if it needs third-party code.

Alternatives considered:
- Make `Image` accept file paths directly and parse everything in the baseline module: rejected due to dependency and platform surface.
- Require callers to continue supplying base64 and dimensions only: safe but less usable than upstream.

### 5. Extend components through options/themes instead of new component families

Add options/theme/callback fields to existing `SelectList` and `SettingsList` APIs. Preserve current constructors where possible and layer richer behavior through additional options types or overloads. Submenus should use the existing component/overlay contracts rather than inventing a separate modal runtime.

Alternatives considered:
- Create separate `RichSelectList`/`RichSettingsList`: rejected because it fragments parity and documentation.

### 6. Model advanced keyboard protocol metadata without forcing all components to consume it

Extend terminal input modeling to represent key release/repeat and Kitty protocol negotiation state where available. Components continue to receive typed inputs; only components that opt into key-release behavior use the additional metadata. Terminal backends should negotiate protocol support conservatively and ignore stale/mismatched responses.

Alternatives considered:
- Keep parsing only press events: simpler, but leaves `wantsKeyRelease` and advanced keyboard parity incomplete.
- Expose raw Kitty escape strings to components: rejected because it breaks the typed-input design.

## Risks / Trade-offs

- Optional adapters can fragment platform behavior → document platform support and keep shared contracts stable.
- Fuzzy ranking may alter existing result ordering → make ranking opt-in/configurable where existing deterministic containment ordering matters.
- Filesystem autocomplete can be slow on large trees → require cancellation/debounce behavior and max-result limits.
- Terminal keyboard negotiation can swallow legitimate input if too aggressive → negotiate conservatively, time out, and preserve raw/press fallback behavior.
- Rich Markdown/theme APIs can grow quickly → add only test-backed upstream parity points and defer full CommonMark compliance to adapters.
