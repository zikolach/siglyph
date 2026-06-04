## 1. Input Buffering and Parsing

- [ ] 1.1 Add a shared `TerminalInputBuffer` that accepts raw chunks and emits complete sequences or paste payloads
- [ ] 1.2 Add tests for split CSI arrow sequences, split modified-key sequences, split bracketed paste, and incomplete escape flush
- [ ] 1.3 Extend `TerminalInputParser` to normalize raw control bytes for Ctrl+C, Ctrl+A, Ctrl+E, Ctrl+U, Ctrl+K, and Ctrl+W
- [ ] 1.4 Update stream, JVM, and Native terminal backends to feed reads through the shared input buffer before parsing

## 2. Terminal Lifecycle Hardening

- [ ] 2.1 Add bracketed-paste enable and disable lifecycle writes for JVM `SttyTerminal`
- [ ] 2.2 Add bracketed-paste enable and disable lifecycle writes for Native `PosixTerminal`
- [ ] 2.3 Make terminal backend `stop()` methods idempotent and safe to call after partial startup
- [ ] 2.4 Add tests or documented smoke coverage for protocol lifecycle writes and stop idempotency

## 3. Interactive TUI Runtime

- [ ] 3.1 Add a TUI exit-request API callable from input handlers
- [ ] 3.2 Add a blocking interactive run API that starts the terminal, processes events, and stops in `finally`
- [ ] 3.3 Add render request coalescing so repeated input/state changes before flush produce one render
- [ ] 3.4 Update stop behavior to leave the cursor below rendered content before restoring terminal control
- [ ] 3.5 Add tests for focus routing, render coalescing, exit request, and stop positioning

## 4. Shared Interactive Demo

- [ ] 4.1 Extract shared demo UI construction and interaction logic for a live Input plus SelectList demo
- [ ] 4.2 Add a JVM interactive demo target using `SttyTerminal`
- [ ] 4.3 Add a Scala Native interactive demo target using `PosixTerminal`
- [ ] 4.4 Ensure both demos exit safely on Escape and Ctrl+C
- [ ] 4.5 Update README with demo commands and expected interaction controls

## 5. Validation

- [ ] 5.1 Run `mill __.compile`
- [ ] 5.2 Run `mill core.test`
- [ ] 5.3 Build or run the JVM interactive demo target in a TTY smoke test where feasible
- [ ] 5.4 Build or run the Native interactive demo target in a TTY smoke test where feasible
- [ ] 5.5 Run `openspec validate --all --strict`
