## ADDED Requirements

### Requirement: Bounded serialized synchronized-frame writes
The shared renderer SHALL emit large normal-screen, alternate-screen, append, and fullscreen frames in bounded character chunks under one terminal-write lock. Concatenating successful chunks SHALL preserve the existing terminal bytes, including typed controls, cursor movement, synchronized-output boundaries, ANSI/OSC, and Unicode. Terminal-write counters SHALL count physical write attempts; write diagnostics SHALL describe completed writes without exposing content.

#### Scenario: Large typed image frame
- **WHEN** a valid typed image makes a frame larger than the configured chunk bound
- **THEN** every frame write remains bounded, all chunks stay non-interleaved with other terminal writes, and their concatenation matches the unchunked frame bytes

#### Scenario: Unicode at a chunk boundary
- **WHEN** a supplementary Unicode character crosses the next character boundary
- **THEN** the renderer keeps its surrogate pair in one terminal write so independent UTF-8 encoding preserves the original bytes

#### Scenario: A middle chunk fails
- **WHEN** a terminal write fails after earlier frame chunks succeeded
- **THEN** the runtime attempts to close an incomplete string control, synchronized output, and disabled autowrap while holding the write lock, propagates the original failure through lifecycle cleanup, and does not claim repair if the sink rejects further writes
