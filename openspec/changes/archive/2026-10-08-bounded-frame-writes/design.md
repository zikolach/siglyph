## Decision

Use a frame-local 64 Ki-character accumulator and retain completed chunks until rendering has fully planned and encoded the frame. This preserves pre-write validation and output ordering while avoiding a second frame-sized buffer and final full-frame string. Chunk boundaries avoid splitting a UTF-16 surrogate pair; terminal backends encode each write separately.

The shared terminal service writes all chunks under one existing terminal-write lock. Counters count attempted physical writes, while write diagnostics report completed writes outside the lock. A failed chunk can have partially reached a terminal; its exact byte count is unknowable. On failure the service makes one best-effort write of string terminator, synchronized-output end, and autowrap restore; it attaches repair failures to the original error. Renderer state commits only after a completed frame. Subsequent lifecycle cleanup remains responsible for cursor, screen mode, and retained image state. No terminal-state guarantee is made when the sink is broken.

The virtual test terminal skips payloads of string controls incrementally, so a multi-write Kitty or OSC control does not become screen text. Small incomplete CSI fragments remain buffered until a final byte arrives.

## Alternatives

A pre-encoded synthetic chunker omitted image encoding and terminal syscalls. Splitting an already aggregated frame does not remove its large intermediate copies. Incremental terminal writes during frame planning would allow partial output before validation or encoder errors. This design retains planned chunks and serializes only emission.
