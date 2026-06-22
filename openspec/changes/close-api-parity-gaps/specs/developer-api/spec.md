## ADDED Requirements

### Requirement: Typed global input listener API
The public TUI API SHALL allow applications to register typed global input listeners that observe terminal input before the focused component receives it.

#### Scenario: Listener observes typed input first
- **WHEN** a terminal input event is received and a global input listener is registered
- **THEN** the listener receives the typed `TerminalInput` before the focused component input handler is invoked

#### Scenario: Ignored listener allows focused routing
- **WHEN** every global input listener reports ignored input
- **THEN** the TUI routes the input to the focused component using the existing focused input path

#### Scenario: Handled listener stops focused routing
- **WHEN** a global input listener reports handled input
- **THEN** the TUI does not route that same input event to the focused component

#### Scenario: Listener can request exit
- **WHEN** a global input listener returns the input result for application exit
- **THEN** the TUI exits through the existing shutdown path and restores terminal state

### Requirement: Public editor programmatic insertion API
The public editor API SHALL expose a Scala-idiomatic method for inserting application-supplied text at the current cursor without requiring applications to synthesize terminal input.

#### Scenario: Application inserts text programmatically
- **WHEN** application code calls the editor insertion API with text
- **THEN** the editor inserts that text at the current cursor using the same logical buffer rules as editor-owned insertion

#### Scenario: Programmatic insertion is documented
- **WHEN** the editor insertion API is added
- **THEN** Scaladoc and project documentation describe callbacks, undo behavior, paste normalization, autocomplete refresh behavior, and render behavior

### Requirement: API parity documentation
The project documentation SHALL record the selected `pi-tui` parity gaps closed by this change and any intentional default differences.

#### Scenario: Porting notes describe listener parity
- **WHEN** the change is complete
- **THEN** `docs/porting-notes.md` describes typed global input listeners as the siglyph counterpart to `pi-tui` raw input listeners

#### Scenario: Porting notes describe autocomplete default
- **WHEN** forced single-completion auto-apply is implemented as opt-in behavior
- **THEN** `docs/porting-notes.md` states that siglyph preserves explicit selection by default and enables auto-apply only when configured
