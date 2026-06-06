## ADDED Requirements

### Requirement: Loader component rendering
The library SHALL provide a loader component that renders an optional indicator frame and message as width-safe terminal output.

#### Scenario: Loader renders default message with indicator
- **WHEN** a loader renders with default options
- **THEN** output includes an indicator frame and loading message within the requested width

#### Scenario: Loader hides empty indicator
- **WHEN** the loader indicator frames are configured as empty
- **THEN** output includes the message without a leading indicator cell

#### Scenario: Loader applies style functions
- **WHEN** indicator or message style functions add ANSI escapes
- **THEN** rendered output preserves valid escapes while keeping visible width less than or equal to the requested width

#### Scenario: Loader handles narrow width
- **WHEN** the loader renders at narrow positive widths including 1
- **THEN** every returned line has visible width less than or equal to the requested width

### Requirement: Loader tick behavior
The loader component SHALL support deterministic tick-driven animation in shared core without owning a background timer.

#### Scenario: Tick advances frame while running
- **WHEN** a started loader with multiple frames receives `tick()`
- **THEN** the current frame advances and a render is requested when context is available

#### Scenario: Tick does not advance while stopped
- **WHEN** a stopped loader receives `tick()`
- **THEN** the current frame does not advance

#### Scenario: Start and stop are idempotent
- **WHEN** `start()` or `stop()` are called repeatedly
- **THEN** loader running state remains consistent and no exception is thrown

#### Scenario: Message mutation updates rendering
- **WHEN** the loader message is changed
- **THEN** subsequent renders include the new message and a render is requested when context is available

#### Scenario: Indicator mutation resets frame sequence
- **WHEN** the loader indicator configuration changes
- **THEN** subsequent renders use the new frames from the first frame and remain width-safe

### Requirement: Cancellable loader input behavior
The library SHALL provide cancellation-enabled loader behavior that handles typed Escape input and exposes cancellation state.

#### Scenario: Escape cancels loader
- **WHEN** a cancellable loader receives `TerminalInput.Key(TerminalKey.Escape)`
- **THEN** it becomes cancelled, invokes the cancellation callback, and reports handled input

#### Scenario: Cancellation is idempotent
- **WHEN** cancellation is requested multiple times
- **THEN** cancellation state remains cancelled and the cancellation callback is invoked at most once

#### Scenario: Non-cancel input is ignored
- **WHEN** a cancellable loader receives input other than Escape
- **THEN** cancellation state is unchanged and the input is reported as ignored
