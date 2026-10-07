## MODIFIED Requirements

### Requirement: Buffered terminal input
The terminal runtime SHALL buffer incomplete raw input framing and emit bounded ordered typed input events without retaining complete paste or raw streams. Built-in backends SHALL measure idle cutoffs from the last pending read: 35 ms for lone Escape and 75 ms for other non-paste framing. A cutoff makes input eligible for flush, not a hard real-time callback guarantee. Stream EOF and zero-length reads flush immediately; active paste has no timeout.

#### Scenario: Split escape sequence
- **WHEN** a terminal backend receives an arrow-key escape sequence split across multiple read chunks before each applicable idle cutoff
- **THEN** the runtime emits one typed arrow-key event after the complete sequence is available

#### Scenario: Split bracketed paste
- **WHEN** bracketed paste start, content, and end markers arrive across multiple chunks
- **THEN** the runtime emits `PasteStart`, zero or more bounded `PasteChunk` events containing every content byte exactly once, and `PasteEnd`

#### Scenario: Lone Escape cutoff
- **WHEN** Escape remains the only pending byte for 35 ms after its read
- **THEN** the runtime becomes eligible to emit one standalone typed Escape, and later bytes start a new input frame

#### Scenario: Incomplete escape timeout
- **WHEN** an escape sequence remains incomplete beyond its applicable idle cutoff
- **THEN** the runtime flushes the incomplete data as bounded raw or best-effort input without blocking future input forever

#### Scenario: Incomplete non-paste cutoff
- **WHEN** an incomplete CSI, Alt UTF-8, or other non-paste sequence has no new fragment for 75 ms
- **THEN** the runtime becomes eligible to flush exact pending bytes as bounded raw or best-effort input without blocking future input forever

#### Scenario: Fragment restarts the cutoff
- **WHEN** a continuation fragment arrives before the current cutoff and leaves non-paste framing pending
- **THEN** the cutoff is measured anew from that fragment rather than a worker startup phase

#### Scenario: Active paste does not time out
- **WHEN** bracketed paste has begun but its end marker is incomplete after any idle interval
- **THEN** idle deadline checks preserve paste framing and content until completion, explicit clear, or generation termination
