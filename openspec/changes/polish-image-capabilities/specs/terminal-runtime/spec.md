## ADDED Requirements

### Requirement: Terminal cell-size query support
The terminal runtime SHALL support terminal cell-size query response parsing for image capability decisions.

#### Scenario: Cell-size response is parsed
- **WHEN** the terminal sends a valid cell-size response containing pixel width and pixel height for a terminal cell
- **THEN** the runtime exposes those positive dimensions to image sizing code

#### Scenario: Cell-size query has safe fallback
- **WHEN** no valid cell-size response is available
- **THEN** image sizing can continue with default cell dimensions without blocking terminal input

#### Scenario: Invalid cell-size response is ignored
- **WHEN** the terminal sends a malformed or non-positive cell-size response
- **THEN** the runtime ignores the response and does not update image cell dimensions

### Requirement: Cell-size protocol reply interception
The TUI runtime SHALL consume terminal cell-size protocol replies before routing input to focused components.

#### Scenario: Cell-size response is not component input
- **WHEN** a terminal cell-size response arrives during an interactive session
- **THEN** the runtime consumes it for capability handling and does not deliver it as typed input to the focused component

#### Scenario: Unrelated input still routes normally
- **WHEN** keyboard input arrives while a cell-size query is pending
- **THEN** unrelated input is routed through normal focused-component handling
