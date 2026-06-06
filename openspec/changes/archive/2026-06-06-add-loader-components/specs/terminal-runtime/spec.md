## ADDED Requirements

### Requirement: Contextual loader render requests
The terminal runtime SHALL allow contextual loader components to request renders after deterministic state changes without requiring a concrete runtime dependency or background scheduler.

#### Scenario: Loader requests render through context
- **WHEN** a loader with an attached `TUIContext` changes message, indicator, running state, cancellation state, or advances a tick
- **THEN** it requests a render through `TUIContext.requestRender()`

#### Scenario: Loader state changes without context are safe
- **WHEN** a loader has no attached `TUIContext`
- **THEN** message changes, indicator changes, lifecycle calls, cancellation, and ticks update component state without throwing

#### Scenario: Runtime remains scheduler-free
- **WHEN** loader components are added
- **THEN** the terminal runtime does not need to own a new timer scheduler or effect runtime to compile and run existing applications

#### Scenario: Runtime lifecycle is not compromised
- **WHEN** a loader is stopped, removed from a TUI, or cancelled
- **THEN** no loader-owned background work remains that could request renders after removal or prevent terminal shutdown
