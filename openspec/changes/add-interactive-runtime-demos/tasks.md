## 1. Input Buffering and Parsing

- [x] 1.1 Add a shared `TerminalInputBuffer` that accepts raw chunks and emits complete sequences or paste payloads
- [x] 1.2 Add tests for split CSI arrow sequences, split modified-key sequences, split bracketed paste, and incomplete escape flush
- [x] 1.3 Extend `TerminalInputParser` to normalize raw control bytes for Ctrl+C, Ctrl+A, Ctrl+E, Ctrl+U, Ctrl+K, and Ctrl+W
- [x] 1.4 Update stream, JVM, and Native terminal backends to feed reads through the shared input buffer before parsing

## 2. Terminal Lifecycle Hardening

- [x] 2.1 Add bracketed-paste enable and disable lifecycle writes for JVM `SttyTerminal`
- [x] 2.2 Add bracketed-paste enable and disable lifecycle writes for Native `PosixTerminal`
- [x] 2.3 Make terminal backend `stop()` methods idempotent and safe to call after partial startup
- [x] 2.4 Add tests or documented smoke coverage for protocol lifecycle writes and stop idempotency

## 3. Interactive TUI Runtime

- [x] 3.1 Add a TUI exit-request API callable from input handlers
- [x] 3.2 Add a blocking interactive run API that starts the terminal, processes events, and stops in `finally`
- [x] 3.3 Add render request coalescing so repeated input/state changes before flush produce one render
- [x] 3.4 Update stop behavior to leave the cursor below rendered content before restoring terminal control
- [x] 3.5 Add tests for focus routing, render coalescing, exit request, and stop positioning

## 4. Shared Interactive Demo

- [x] 4.1 Extract shared demo UI construction and interaction logic for a live Input plus SelectList demo
- [x] 4.2 Add a JVM interactive demo target using `SttyTerminal`
- [x] 4.3 Add a Scala Native interactive demo target using `PosixTerminal`
- [x] 4.4 Ensure both demos exit safely on Escape and Ctrl+C
- [x] 4.5 Update README with demo commands and expected interaction controls

## 5. Validation

- [x] 5.1 Run `mill __.compile`
- [x] 5.2 Run `mill core.test`
- [x] 5.3 Build or run the JVM interactive demo target in a TTY smoke test where feasible
- [x] 5.4 Build or run the Native interactive demo target in a TTY smoke test where feasible
- [x] 5.5 Run `openspec validate --all --strict`
