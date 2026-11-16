# log-explorer-demo Specification

## Purpose
Defines the deterministic step-driven reference application used to validate bounded event retention, filtering, selection and cross-platform terminal lifecycle.

## Requirements

### Requirement: Bounded deterministic event model
The reference application SHALL generate fixed-format events with stable increasing identities, retain at most its configured event capacity, report evictions and expose deterministic filtered snapshots. It SHALL reject invalid capacities and oversized step requests.

#### Scenario: Long-running generated source
- **WHEN** 100,000 events are generated into a capacity-200 model
- **THEN** exactly the latest 200 events remain and the eviction count is 99,800

#### Scenario: Pause and cancel
- **WHEN** a tick is delivered while paused or after cancellation
- **THEN** no record is appended and no event identity is consumed

### Requirement: Predictable projection and navigation
The application SHALL support filtering, explicit follow-latest mode and row navigation. In browse mode, ingestion SHALL preserve selection by identity while retained; eviction SHALL fall back to the oldest matching row. A new filter SHALL reset selection according to the active follow mode. Empty results SHALL have no selection.

#### Scenario: Selection survives ingestion
- **WHEN** additional events arrive while a browsed selected row is still retained
- **THEN** the selected identity is unchanged

#### Scenario: Selected row is evicted
- **WHEN** the selected row leaves retained history
- **THEN** selection moves to the oldest remaining matching row or becomes empty

### Requirement: Shared typed interactive prototype
The JVM and Native launchers SHALL expose the same opt-in prototype through `--log-explorer`, while retaining their existing default demo. The prototype SHALL use existing typed input and component output, display selected details and resource status, and ignore key-release commands.

#### Scenario: Filter and navigation
- **WHEN** a user commits a filter and navigates matching rows
- **THEN** selection and details refer to the same retained event and output fits the viewport after resize

#### Scenario: Exit or runtime failure
- **WHEN** the application run ends normally or through a terminal failure
- **THEN** its source model is cancelled and terminal restoration remains owned by TUI

### Requirement: Honest prototype boundaries
Documentation SHALL identify the source as deterministic and step-driven, describe limits and application-owned glue, and SHALL NOT claim asynchronous lifecycle safety, file following, virtualized table rendering or real-emulator validation from virtual tests.

#### Scenario: Discover future contracts
- **WHEN** the prototype is documented
- **THEN** remaining scheduling, table and testing work is linked to roadmap issues without adding speculative public core APIs
