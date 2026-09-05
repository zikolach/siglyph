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
