## Read policy
Read from byte zero. Poll local regular files, rejecting symlinks and special files. Each poll opens/closes its descriptor, reads at most 64 KiB and emits at most 256 lines. A partial line retains at most 4096 bytes; discard excess until LF and label the emitted prefix truncated. Decode UTF-8 with replacement, strip CR before LF, and hold an incomplete EOF line until its newline arrives.

Detect observed identity change, size below committed offset, or changed initial prefix; reset to zero and discard partial state, with a visible notice. Rotation switches immediately to the current path, not the unread old descriptor. Compare identity again after reading and discard raced batches. Polling cannot detect every same-inode truncate/regrow with an identical prefix, or adversarial ABA replacement; it is not exactly-once/audit ingestion. Blocking filesystem calls are not sandboxed.

## Delivery and ownership
An explicitly owned single-worker scheduled executor performs I/O outside UI callbacks. Retain at most one bounded batch until scoped UI delivery acknowledges it. Full admission retries that same batch without further reads; pause preserves it. Cancellation closes scope admission and stops polling; owner shutdown joins the worker before terminal cleanup completes. Generated/manual sources remain unchanged; Ctrl+N cannot inject synthetic rows into file mode. I/O failures are visible and retried, with retained data preserved.

## Validation
Shared JVM/Native tests cover fragmented UTF-8/CRLF, incomplete EOF, limits, long lines, truncation/replacement, missing/non-regular paths and recovery. Deterministic delivery tests cover pause/backpressure/cancellation. File contents retain ordinary typed-output safety. PTY smoke verifies real file growth and cleanup separately from pixels.
