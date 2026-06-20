## Context

The current `Terminal` abstraction covers lifecycle, writes, dimensions, cursor visibility, and clearing. Upstream `pi-tui` also exposes terminal window title, terminal progress, background color query, color-scheme query, and color-scheme change notifications. The Scala implementation must keep the backend abstraction stable for existing JVM, Native, stream, and virtual terminals.

## Goals / Non-Goals

**Goals:**

- Add terminal title and progress support without adding required methods to `Terminal`.
- Keep progress fire-and-forget. The API sends the sequence and does not promise terminal state tracking.
- Put query request/response correlation in `TUI`, not terminal backends.
- Consume query protocol replies before component input routing.
- Keep behavior testable through virtual terminals.

**Non-Goals:**

- No cell-size query work. That belongs to image capability work.
- No image capability detection changes.
- No mandatory runtime dependency.
- No Windows-specific terminal implementation.
- No broad recovery fallback beyond explicit unsupported-result reporting.

## Decisions

### 1. Use optional capability APIs for title and progress

Add optional traits or helper APIs for title and progress support instead of widening `Terminal`. Existing terminal implementations remain source-compatible. Unsupported terminals either do not implement the capability or return `false` through a helper.

Alternatives considered:

- Add `setTitle` and `setProgress` to `Terminal`: rejected because it breaks existing implementations.
- Treat title and progress as raw `write` snippets owned by applications: rejected because the library should expose upstream parity behavior in a typed, documented API.

### 2. Keep progress fire-and-forget

Progress support emits OSC 9;4 active and clear sequences where supported. It does not guarantee that a terminal displays or retains a progress indicator. No keepalive loop is required by this change.

Alternatives considered:

- Track progress state and send keepalives: rejected because it adds scheduler/state complexity and is not needed for the requested parity slice.

### 3. Put color queries in `TUI`

`TUI` owns OSC request emission, timeout handling, and response correlation. Terminal backends continue to deliver parsed input or protocol frames through existing input flow. This keeps backends simple and centralizes logic that must coordinate with component input routing.

Alternatives considered:

- Put query state in every backend: rejected because it duplicates logic across JVM, Native, stream, and virtual terminals.

### 4. Parse terminal color data into small shared models

Represent RGB as red, green, and blue 0-255 integer channels. Represent color scheme as the finite values `dark` and `light`. Invalid, absent, or timed-out replies return an unsupported/empty result rather than guessing.

Alternatives considered:

- Return raw escape strings: rejected because it leaks terminal protocol details into application code.
- Infer color scheme from environment variables: rejected for this change because query behavior should be evidence-based.

## Risks / Trade-offs

- Query replies can arrive interleaved with user input → route protocol replies through a runtime interceptor before focused component input.
- Some terminals do not answer OSC queries → timeout and return an empty result without blocking input.
- Optional capability traits add API surface → keep traits small and document platform scope.
- Title strings can contain control characters → implementation must sanitize or reject unsafe title text before emitting OSC output.
