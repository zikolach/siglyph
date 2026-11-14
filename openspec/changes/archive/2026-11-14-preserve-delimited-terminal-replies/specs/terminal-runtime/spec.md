## ADDED Requirements

### Requirement: Recognized terminal reply framing survives keyboard idle deadlines
A complete recognized terminal-reply header within the bounded recognition limit SHALL retain delimiter framing across keyboard idle deadlines and zero-byte stream reads. Ordinary Escape, CSI and unrecognized control strings SHALL retain existing idle recovery.

#### Scenario: Delayed color response
- **WHEN** an OSC11 response is split after its recognized header for longer than75ms
- **THEN** its suffix and BEL terminator remain raw protocol bytes rather than typed characters or Ctrl+G
- **AND** a genuine key after the response is delivered normally

### Requirement: Reply framing is bounded and recoverable
Reply framing SHALL stream bounded raw chunks, mark oversized replies, and preserve framing until termination or explicit recovery. CAN/SUB SHALL abort the reply. A new non-ST escape SHALL be reparsed as input. Explicit flush and clear SHALL recover pending reply state.

#### Scenario: Oversized reply
- **WHEN** a recognized reply exceeds the typed-sequence limit
- **THEN** chunks remain bounded and the reply ends with LimitExceeded
- **AND** its late terminator does not become a keyboard event

#### Scenario: New key sequence interrupts an unterminated reply
- **WHEN** a new ESC followed by a non-backslash byte arrives inside a recognized reply
- **THEN** the old reply ends incomplete and the new escape sequence is parsed normally

## MODIFIED Requirements

### Requirement: Buffered terminal input
The runtime SHALL buffer fragmented input as bounded ordered typed events without whole-stream retention. Built-in backends SHALL measure idle cutoffs from the last pending read: 35 ms for lone Escape and 75 ms for other non-paste framing, except recognized delimited replies. Cutoffs indicate flush eligibility, not callback deadlines. EOF SHALL immediately flush pending non-paste input; zero-byte reads SHALL flush only idle-timed framing. Paste SHALL have no idle timeout.

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
- **WHEN** an incomplete CSI, Alt UTF-8, or other idle-timed non-paste sequence has no new fragment for 75 ms
- **THEN** the runtime becomes eligible to flush exact pending bytes as bounded raw or best-effort input without blocking future input forever

#### Scenario: Fragment restarts the cutoff
- **WHEN** a continuation fragment arrives before the current cutoff and leaves idle-timed non-paste framing pending
- **THEN** the cutoff is measured anew from that fragment rather than a worker startup phase

#### Scenario: Active paste does not time out
- **WHEN** bracketed paste has begun but its end marker is incomplete after any idle interval
- **THEN** idle deadline checks preserve paste framing and content until completion, explicit clear, or generation termination
