## ADDED Requirements

### Requirement: Buffered terminal input
The terminal runtime SHALL buffer raw input chunks and emit complete logical input sequences before parsing them into typed terminal input events.

#### Scenario: Split escape sequence
- **WHEN** a terminal backend receives an arrow-key escape sequence split across multiple read chunks
- **THEN** the runtime emits one typed arrow-key event after the complete sequence is available

#### Scenario: Split bracketed paste
- **WHEN** bracketed paste start, content, and end markers arrive across multiple chunks
- **THEN** the runtime emits one paste event containing the full pasted content

#### Scenario: Incomplete escape timeout
- **WHEN** an escape sequence remains incomplete beyond the configured buffer timeout
- **THEN** the runtime flushes the incomplete data as a raw or best-effort input event without blocking future input forever

### Requirement: Control-key normalization
The terminal runtime SHALL normalize raw ASCII control bytes into typed key events with control modifiers where those bytes represent common control-key combinations.

#### Scenario: Ctrl+C normalization
- **WHEN** the runtime receives raw byte `0x03`
- **THEN** it emits a typed key event equivalent to Ctrl+C

#### Scenario: Readline shortcut normalization
- **WHEN** the runtime receives raw control bytes for Ctrl+A, Ctrl+E, Ctrl+U, Ctrl+K, or Ctrl+W
- **THEN** it emits typed key events with the corresponding character and control modifier

### Requirement: Terminal protocol lifecycle
Interactive terminal backends SHALL enable terminal protocols needed for interactivity on start and disable them on stop.

#### Scenario: Bracketed paste enabled on start
- **WHEN** an interactive JVM or Native terminal backend starts
- **THEN** it enables bracketed paste mode before delivering input to the TUI

#### Scenario: Bracketed paste disabled on stop
- **WHEN** an interactive JVM or Native terminal backend stops
- **THEN** it disables bracketed paste mode before returning control to the shell

### Requirement: Safe interactive shutdown
Interactive terminal backends and the TUI runtime SHALL restore terminal state when the application exits normally, exits from Escape or Ctrl+C, or encounters an exception during startup or event handling.

#### Scenario: Stop restores terminal
- **WHEN** an interactive application requests exit
- **THEN** the runtime shows the cursor, disables bracketed paste, and restores terminal raw-mode state exactly once

#### Scenario: Exception restores terminal
- **WHEN** an exception occurs after raw mode has been enabled
- **THEN** the runtime restores terminal state before propagating or reporting the exception

### Requirement: JVM and Native interactive parity
The project SHALL provide interactive runtime behavior and demo launch targets for both JVM and Scala Native backends.

#### Scenario: JVM interactive demo launches
- **WHEN** the JVM interactive demo is launched in a macOS or Linux TTY
- **THEN** it enters raw mode, renders the demo UI, accepts live input, and exits safely on Escape or Ctrl+C

#### Scenario: Native interactive demo launches
- **WHEN** the Scala Native interactive demo is built and launched in a macOS or Linux TTY
- **THEN** it enters raw mode, renders the demo UI, accepts live input, and exits safely on Escape or Ctrl+C
