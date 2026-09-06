## ADDED Requirements

### Requirement: Resize recovery prefix is detached from differential frame state
The TUI renderer SHALL treat normal resize recovery rows as a one-shot physical prefix and SHALL keep all retained rendering, comparison, control, cursor, and layout state frame-relative to the live component frame.

#### Scenario: Recovery is not a differential baseline row
- **WHEN** a successful resize redraw emits recovery rows before the live frame
- **THEN** `previousFrame` SHALL contain only prepared live-frame lines, controls, and selected cursor metadata

#### Scenario: Live frame changes after recovery
- **WHEN** a later render changes one retained line or control
- **THEN** first-changed-row calculation and partial output SHALL use live-frame-relative rows and SHALL not compare or repaint recovery rows

#### Scenario: Live frame is unchanged after recovery
- **WHEN** a later ordinary render produces the same prepared live frame
- **THEN** no frame repaint SHALL occur solely because recovery rows were previously emitted

#### Scenario: Retained control replacement follows recovery
- **WHEN** a retained Kitty control changes after recovery
- **THEN** replacement cleanup and retransmission SHALL use only retained old/new frames and SHALL not treat recovery as control-owned state

#### Scenario: Retained layout follows relocated frame
- **WHEN** recovery changes the physical start row of the live frame
- **THEN** semantic base and overlay layout bounds SHALL remain frame-relative while mouse routing applies the updated physical frame origin

### Requirement: Recovery rendering remains part of serialized Render work
The live-frame render, recovery provider invocation, stale-geometry check, and combined redraw planning SHALL execute as one ordinary Render work unit under the existing deterministic single-owner scheduler.

#### Scenario: Other categories are continuously ready
- **WHILE** Structural, Action, Ingress, Control, Append, or later Render work remains ready during recovery rendering
- **WHEN** the current recovery Render work completes or is discarded as stale
- **THEN** owner selection SHALL return to the existing six-category fairness cycle

#### Scenario: Provider publishes a render request
- **WHEN** provider code requests follow-up rendering while the current Render work owns the drain
- **THEN** the request SHALL coalesce for later processing and SHALL not recursively invoke component or provider rendering

#### Scenario: Resize coalesces during provider rendering
- **WHEN** one or more resize notifications arrive while provider code runs
- **THEN** resize work SHALL remain capacity-free and coalesced, the stale candidate SHALL not mutate differential state, and a latest-geometry Render SHALL remain pending
