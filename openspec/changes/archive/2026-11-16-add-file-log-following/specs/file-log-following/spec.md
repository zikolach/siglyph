## ADDED Requirements

### Requirement: Bounded regular-file ingestion
The file source SHALL bound bytes, emitted lines and partial-line retention per poll. It SHALL wait for LF before emitting an incomplete EOF line and visibly mark oversized lines.

#### Scenario: Partial multibyte text
- **WHEN** a UTF-8 line arrives over several polls
- **THEN** its completed line is decoded once after LF without unbounded buffering

### Requirement: Explicit replacement and error policy
Observed replacement/truncation SHALL reset reading and discard partial state with a notice. I/O failures SHALL remain visible and retryable without clearing retained events.

#### Scenario: File disappears and returns
- **WHEN** the path is temporarily unavailable
- **THEN** the UI reports the error, retains prior rows and retries the path

### Requirement: Bounded serialized delivery
I/O SHALL run outside UI callbacks. At most one batch SHALL wait for scoped admission/acknowledgement. Pause/backpressure SHALL prevent additional reads and cancellation SHALL prevent later UI mutation.

#### Scenario: UI admission is full
- **WHEN** a batch cannot be admitted
- **THEN** the same batch is retained for retry and the file is not read further
