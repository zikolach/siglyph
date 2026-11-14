# Delayed terminal reply framing

Recognized OSC10/11 colors, OSC4 palette entries, OSC7501 status replies, DCS XTVersion and Kitty
APC graphics replies retain delimiter framing across keyboard idle timeouts. This prevents a late
reply suffix or BEL from becoming application text or Ctrl+G. Headers must be unambiguous and fit
within64 bytes; ordinary Escape, CSI and unknown control strings retain35/75ms recovery.

The shared JVM/Native parser emits typed RawStart/RawChunk/RawEnd events. Chunks never exceed4096
bytes; replies beyond the typed-sequence budget remain framed and end with LimitExceeded. No whole
reply buffer grows with input. A recognized but unterminated reply requires BEL/ST, CAN/SUB, a new
non-ST escape, explicit EOF/flush or clear to recover; the parser does not guess that later text is
keyboard input. A split trailing ESC waits to distinguish ST from a new input escape.

Zero-byte stream reads are not EOF and do not end recognized replies. Explicit flush still reports
incomplete input, and clear discards pending parser state. Terminal runtime correlation remains
bounded and generation-owned; this does not grant raw strings any rendering authority.

Regression inspiration: pi-tui commit edb2eb966d7ed7238e70c2216adab6f69aabe8bb, reviewed at
8636e211e7834e4ca46a22aec1d65dfd2eac8677. Tests use controlled monotonic time rather than sleeps.
