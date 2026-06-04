## Context

The current `scala-tui` baseline has a core renderer, typed terminal input model, JVM and Native terminal backends, MVP components, and two demos. However, the main demo is non-interactive and the terminal read path parses each backend read chunk directly. Real terminals can split escape sequences and bracketed paste markers across reads, so live interactivity needs buffering before parsing. `pi-tui` solves this with `StdinBuffer`; TauTUI demonstrates a native interactive runtime with typed input and a demo loop.

The next change should turn the existing pieces into a reliable interactive foundation across both JVM and Scala Native. It should not jump ahead to multiline editor, overlays, autocomplete, or Markdown rendering.

## Goals / Non-Goals

**Goals:**

- Add a shared input buffer that emits complete logical sequences and paste events from arbitrary raw chunks.
- Normalize common raw control bytes into typed Ctrl-key events.
- Add a small interactive run/exit lifecycle to `TUI` with safe terminal restoration.
- Enable and disable bracketed paste in JVM and Native terminal backends.
- Provide equivalent JVM and Native interactive demos using the same UI construction logic.
- Add tests for fragmented input, control-key normalization, lifecycle behavior, and demo-focused interaction.

**Non-Goals:**

- Do not implement multiline `Editor`, autocomplete, overlays, Loader, or Markdown rendering in this change.
- Do not add new runtime dependencies.
- Do not implement full Kitty keyboard protocol negotiation yet; parsing existing Kitty CSI-u sequences remains supported.
- Do not attempt full virtual terminal emulation beyond what is needed for this change's tests.

## Decisions

### Decision: Introduce an input buffer before `TerminalInputParser`

Backends should feed raw strings into a shared buffering component. The buffer emits either complete escape/control sequences or paste payloads, which are then parsed into `TerminalInput` values. This mirrors the role of `pi-tui`'s `StdinBuffer` without copying Node-specific event machinery.

Alternative considered: make `TerminalInputParser` stateful. Keeping buffering separate makes parser tests simpler and allows terminal backends to share the same buffering lifecycle.

### Decision: Normalize raw control bytes in the parser layer

Control-byte normalization belongs after buffering but before components. For example, byte `0x03` should become `TerminalInput.Key(TerminalKey.Character("c"), KeyModifiers(ctrl = true))`. This lets `Input` and application exit handling operate on typed events consistently.

Alternative considered: let each component interpret raw control bytes. That would duplicate terminal-specific behavior and weaken the typed-input API.

### Decision: Add minimal `TUI.run` / `requestExit` semantics

The runtime should provide a blocking interactive run path suitable for CLI demos. `requestExit` should be callable from input handlers, including Escape/Ctrl+C handling. Internally, the run loop can be a simple wait/notify or queue; it does not need a full async framework yet.

Alternative considered: keep application-owned sleep loops. That keeps core smaller but makes every demo responsible for lifecycle correctness.

### Decision: Use a shared demo builder with platform launchers

Create one demo UI construction function and use it from JVM and Native demo modules. Platform-specific entrypoints only select the terminal backend. This proves the terminal abstraction and keeps demo behavior comparable across targets.

Alternative considered: separate JVM and Native demos. That would make platform-specific differences harder to spot.

### Decision: Terminal lifecycle owns bracketed paste mode

Interactive backends should write bracketed-paste enable/disable sequences during start/stop. The TUI can remain backend-agnostic while still receiving paste events reliably.

Alternative considered: have `TUI` write protocol setup sequences. That might simplify shared behavior but mixes terminal-protocol ownership into the renderer and complicates non-interactive stream terminals.

## Risks / Trade-offs

- **Fragment timeout may misclassify slow escape sequences** → Use a configurable timeout and tests for common split sequences; keep the default conservative.
- **Threading races between backend input threads and render state** → Serialize input handling and render requests through the TUI run-loop queue where practical.
- **Terminal not restored after unexpected exceptions** → Wrap interactive run in `try/finally` and make backend `stop()` idempotent.
- **Native demo debugging can be slower** → Keep the demo small and share most logic with JVM so failures isolate to backend/run target behavior.
- **Bracketed paste lifecycle can leak if stop is skipped** → Ensure `run` and demo entrypoints always call `stop()` in `finally`.

## Migration Plan

1. Add `TerminalInputBuffer` in core and unit tests for split CSI sequences, split bracketed paste, ESC/meta ambiguity, and incomplete flush.
2. Extend `TerminalInputParser` to normalize raw control bytes for common Ctrl-key shortcuts.
3. Update `StreamTerminal`, `SttyTerminal`, and `PosixTerminal` to use the shared input buffer before parsing.
4. Add bracketed-paste enable/disable lifecycle to interactive JVM and Native backends.
5. Add TUI interactive run/exit APIs and safe stop behavior.
6. Extract a shared interactive demo UI builder.
7. Add JVM and Native interactive demo targets using the shared builder.
8. Add tests for focus/input/render flow and lifecycle cleanup where feasible.

## Open Questions

- Should Escape always request exit in the demo, or should Escape be routed to focused components first with demo-level fallback?
- Should Ctrl+C handling be a TUI-level default or purely demo/application behavior?
- Should input buffer timeout be configurable per terminal backend or globally on the buffer?
