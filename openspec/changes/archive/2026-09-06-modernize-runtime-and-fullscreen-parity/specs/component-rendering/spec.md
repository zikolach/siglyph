## ADDED Requirements

### Requirement: Viewport layout contract
The renderer SHALL support a height-constrained layout path that produces one retained tree of component rectangles, clipping rectangles, scroll ownership, and typed render metadata for a requested terminal width and height. Direct `Component.render(width)` SHALL remain the unbounded width-only fallback.

#### Scenario: Layout assigns child rectangle
- **WHEN** a viewport layout allocates a child width and height
- **THEN** rendering and committed mouse geometry use the same assigned and clipped rectangle

#### Scenario: Direct render remains unbounded
- **WHEN** a stack or scroll component is rendered directly through `render(width)` outside a viewport
- **THEN** it returns its complete width-constrained document without applying terminal-height clipping

#### Scenario: Typed metadata follows clipping
- **WHEN** layout clips component rows or columns
- **THEN** text, cursor candidates, and terminal controls use the same clipping boundary and no partially visible typed control executes

### Requirement: Component mutation and render serialization
Built-in mutable components SHALL define one JVM and Scala Native threading contract. Mutation, input handling, autocomplete state transitions, and rendering SHALL observe consistent state without data races.

#### Scenario: Loader ticks during rendering
- **WHEN** an application scheduler requests loader ticks while the TUI can render
- **THEN** frame, indicator, message, and running state remain consistent and each accepted visible mutation schedules at most the required render

#### Scenario: Concurrent component mutations are ordered
- **WHEN** supported mutation APIs are called from more than one execution context
- **THEN** their visible state transitions use the documented serialization boundary rather than relying on unsynchronized field visibility

#### Scenario: Attached effects share session order
- **WHEN** effectful transitions commit across components attached to one active TUI
- **THEN** state commit and immutable effect-batch admission are atomic and the existing single-owner TUI drain executes batches in one session order outside library locks

#### Scenario: Effect admission is bounded
- **WHEN** 4096 effect batches are queued while one batch executes
- **THEN** the next effectful transition is rejected before state mutation with bounded diagnostics

#### Scenario: Concurrent attached caller does not wait
- **WHEN** another attached callback batch is executing
- **THEN** a concurrent or reentrant transition returns after state commit and enqueue rather than waiting for callback completion

#### Scenario: Detached reentrant failure reaches the outer drain
- **WHEN** a detached callback triggers a reentrant transition whose effect fails after the nested caller returned
- **THEN** the active outer drain reports that failure and suppresses later failures on the first failure

### Requirement: Component callback isolation
Built-in components SHALL invoke application callbacks, providers, cancellation handles, and other application-controlled code without holding component-state, TUI lifecycle, or terminal-write locks. State required for a callback SHALL be captured before the callback, and callback results SHALL be applied through the component's serialized state boundary.

#### Scenario: Editor change callback reenters editor
- **WHEN** an editor `onChange` callback reads or updates editor or TUI state
- **THEN** it runs without component or runtime lock inversion and any follow-up render is coalesced

#### Scenario: Autocomplete provider blocks
- **WHEN** an autocomplete provider takes time to return a request handle
- **THEN** it does not retain the editor-state lock and later completion is accepted only if its captured request is still current

#### Scenario: Callback throws
- **WHEN** application-controlled component code throws during runtime-owned input handling
- **THEN** the runtime records the failure and performs normal cleanup without leaving component locks retained

#### Scenario: Stop closes component admission
- **WHEN** stopping begins while attached effect batches remain accepted
- **THEN** the TUI drains that finite prefix before cleanup and rejects later external effectful mutations before state change

#### Scenario: Context handoff requires detach
- **WHEN** a contextual component is assigned a second active TUI context without an intervening completed detach
- **THEN** the handoff is rejected and attach and detach effects remain on their respective new and old coordinators

### Requirement: Viewport rendering avoids unnecessary work
The viewport renderer SHALL cache reusable component renders by component identity and width for one frame, SHALL avoid recompositing unchanged full-width visible rows, and SHALL skip layout painting and search highlighting for clipped or offscreen rows where semantic control cleanup does not require work.

#### Scenario: Shared measurement renders once per width
- **WHEN** layout measures and paints the same component at the same width in one frame
- **THEN** the component render result is reused instead of invoking `render` twice

#### Scenario: Offscreen transcript rows are skipped
- **WHEN** a scroll view exposes a small viewport over a large transcript
- **THEN** painting work is bounded by visible rows plus required retained state rather than recompositing every transcript row

#### Scenario: Idle frame emits no redraw
- **WHEN** input or focus events do not change visible state, layout, cursor, controls, or viewport position
- **THEN** the differential renderer emits no frame repaint
