## Context

`scala-tui` has delivered shared-core overlay, interactive runtime, and rendered `Editor` behavior, but it remains behind upstream in several high-touch `pi-tui` interactions. The missing area is now concentrated in advanced text-editing workflows and additional content components: richer `Input`/`Editor` key semantics, file-aware autocomplete, concrete markdown rendering, and image protocol output.

The project constraints remain unchanged: Scala 3, dependency-light, JVM + Scala Native, no Node runtime, and strong width-safe rendering/runtime guardrails. This design therefore keeps work in shared core where possible and avoids adding third-party runtime dependencies.

## Goals / Non-Goals

**Goals:**
- Deliver parity-oriented behavior for editing, completion, markdown, and image features while preserving shared-core portability.
- Keep user-visible editing commands and default keybindings as close to current `pi-tui` as Scala typed input allows.
- Extend existing public contracts in `scalatui` without making terminal backends or external effect runtimes part of the core API.
- Preserve crash-safety and width contracts in both component rendering and terminal runtime paths.
- Keep implementation test-first and spec-driven with deterministic scenarios.

**Non-Goals:**
- No Windows support and no browser/JS-only runtime refactoring.
- No change to the asynchronous model of core rendering (no built-in scheduler ownership in shared core).
- No mandatory dependency on external Markdown parser libraries in this cycle.
- No third-party Markdown or image dependency is added to core; optional dependency-bearing modules require explicit approval of concrete libraries.
- No breaking rename/removal of existing public APIs.

## Decisions

1. **Add capability modules in shared `core` and `markdown` only, with optional platform-specific internals behind narrow interfaces**
   - *Why*: Keeps runtimes backend-independent and follows existing architecture.
   - *Alternatives considered*: adding JVM-only parser/runtime dependencies or platform-specific implementations in the editor component itself; rejected due to parity goals for Native + dependency-light constraints.

2. **Implement markdown and image support behind explicit, explicit-fallback component contracts**
   - *Why*: Existing `TerminalCapabilities` already models image protocol capability, and markdown is already isolated in its own module.
   - *Alternatives considered*: keeping these as placeholders indefinitely; rejected because feature completeness request is explicit and demos/apps currently have no usable rendering path.

3. **Mirror `pi-tui` editing keybindings and undo model unless there is a documented Scala-specific reason to deviate**
   - *Why*: Editing muscle memory matters for migration. Current upstream exposes an undo-only stack and default `tui.editor.undo` binding of `Ctrl+-`; Scala should not invent redo or switch to `Ctrl+Z` in this parity batch.
   - *Alternatives considered*: adding redo and using platform-common desktop shortcuts; rejected for this change because it would drift from the canonical behavior source.

4. **Add a combined autocomplete model in `autocomplete` rather than embedding path logic in `Editor`**
   - *Why*: Keeps `Editor` independent from completion strategy and allows apps to swap custom providers.
   - *Alternatives considered*: `Editor`-specific path parser; rejected because it duplicates logic and complicates future custom providers.

5. **Reuse existing `TerminalInput`/`TerminalInputParser` and add focused normalization utilities for editing commands**
   - *Why*: Existing runtime already normalizes control keys and parsed input; editing helpers can target typed commands without changing parser contracts.
   - *Alternatives considered*: reworking the parser to add richer protocol-specific key events; rejected to avoid widening parsing scope before editing behavior is stabilized.

6. **Keep markdown dependency-free by default, with optional parser adapter boundaries**
   - *Why*: A basic renderer should work on JVM and Native without extra dependencies, while richer parser support can be supplied by optional modules when suitable JVM/Native libraries are chosen.
   - *Alternatives considered*: requiring a JVM-only parser or blocking markdown until a single cross-platform parser exists; rejected because both would undermine portability or delay useful baseline rendering.

7. **Keep image protocol/capability contracts small in core and put dependency-bearing image helpers/components in an optional module**
   - *Why*: Terminal capability detection is a runtime concern already in core, but image decoding/dimension extraction and richer helpers may need third-party or platform-specific code. Splitting the module preserves core dependency-light behavior while allowing `pi-tui`-like image rendering for applications that opt in.
   - *Alternatives considered*: placing all image code in core; rejected because it could force unwanted dependencies into every application.

8. **Implement image protocol emission conservatively (capability-gated), with readable fallback text on unsupported terminals**
   - *Why*: Prevents breaking behavior on terminals without image protocol support while allowing rich output where available.
   - *Alternatives considered*: always emitting escape sequences with best-effort no-op handling; rejected because this can create undefined behavior and noisy logs on unsupported terminals.

## Risks / Trade-offs

- **Risk: Undo/kill-ring correctness is tricky with grapheme boundaries and newline operations** → Mitigation: isolate helpers in pure, tested units and add regression tests for multi-cluster, unicode, and multiline edge cases.
- **Risk: Large-paste marker behavior can hide content if marker expansion fails or is omitted** → Mitigation: require deterministic marker metadata that always expands on submit and is test-asserted.
- **Risk: File/path completion can perform expensive directory enumeration** → Mitigation: limit results, cache best-effort tokens where practical, and treat completion as best-effort with strict timeout/error fallbacks.
- **Risk: Image protocols differ between Kitty and iTerm2** → Mitigation: model protocol output adapters separately and keep component fallback behavior unchanged when protocol output is unsupported or disabled.
- **Risk: Markdown parity can become open-ended** → Mitigation: define a first supported subset in specs and mark deferred constructs explicitly.

## Migration Plan

1. Add/adjust core primitives and helper models first (`editing`, `autocomplete`, `markdown` entry points, and image-capability contracts).
2. Implement `Input` and `Editor` parity behaviors while retaining existing control flow and callback contracts, matching upstream keybindings/undo semantics by default.
3. Implement completion path providers and integrate into editor/autocomplete options without changing existing demo logic semantics.
4. Add dependency-free markdown component behavior, optional parser boundaries, and image protocol/optional image module behavior plus fallback tests.
5. Update shared interactive demos and docs to expose new behavior.
6. Run validation pipeline and OpenSpec sync checks; if any behavior must be intentionally deferred, record it in docs before completion.

## Open Questions

- Should large-paste compaction be purely marker-based or include configurable preview snippets in all editor themes?
- Should image helper APIs expose pixel-size metadata from external sources (e.g., file headers vs caller-supplied dimensions), and is caller-supplied metadata required for first cut?
- For undo semantics, should an undo step include all insert/delete mutations or only high-level edit commands (`undo`, `yank`, `yank-pop`), matching upstream undo-only behavior?
