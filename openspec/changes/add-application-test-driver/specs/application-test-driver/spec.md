## ADDED Requirements

### Requirement: Managed synchronous application fixture
The test driver SHALL own start/stop of a TUI built against its supplied VirtualTerminal. It SHALL preserve a primary startup/body failure and suppress independent cleanup failure. It SHALL NOT claim asynchronous quiescence or silently accept incomplete stop.

#### Scenario: Body failure
- **WHEN** a test body throws and stop also fails
- **THEN** the body failure remains primary and the cleanup failure is suppressed

#### Scenario: Managed teardown
- **WHEN** a successful fixture body completes
- **THEN** the driver stops the TUI and rejects later scripted input

### Requirement: Bounded typed scripts
The driver SHALL validate geometry, input size and remaining step budget before delivering a complete text/paste operation. It SHALL provide typed input, grapheme text, chunked UTF-8 paste, resize and a single synchronous flush operation. Actions SHALL be single-threaded and non-reentrant.

#### Scenario: Budget exceeded
- **WHEN** a text/paste operation exceeds its remaining script budget
- **THEN** no event from that operation is delivered

#### Scenario: Fragmented paste
- **WHEN** a multibyte character is split across paste chunks
- **THEN** the application receives the same decoded text as an unfragmented paste

### Requirement: Bounded screen diagnostics
The driver SHALL expose immutable screen snapshots with explicit geometry and framework-independent assertions. Failure diagnostics SHALL limit differing rows and escaped line previews. Retained output SHALL contain at most a configured capped prefix for the last action, with an explicit truncation flag. Cleanup output SHALL be capped separately and SHALL NOT overwrite action diagnostics.

#### Scenario: Unicode mismatch
- **WHEN** expected and actual screen text differ
- **THEN** the assertion identifies geometry and differing rows without relying on code-unit column alignment

#### Scenario: Repeated input
- **WHEN** many actions render output
- **THEN** output retained by the driver does not accumulate earlier actions
