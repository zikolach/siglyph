# extras-widgets Specification

## Purpose
Defines the optional extras widget module, including reusable expandable UI helpers, expansion state coordination, dependency boundaries, and excluded agent-specific concepts.
## Requirements
### Requirement: Optional extras widget module
The library SHALL provide an optional `siglyph-extras` module for reusable UI helpers that sit above the core component primitives and depend only on `siglyph-core`.

#### Scenario: Application opts into extras
- **WHEN** an application adds the `siglyph-extras` dependency
- **THEN** it can import extras APIs from the `scalatui.extras` package without adding third-party runtime dependencies

#### Scenario: Core remains independent
- **WHEN** an application depends only on `siglyph-core`
- **THEN** it does not receive extras widget APIs or additional runtime dependencies

### Requirement: Expandable protocol
The extras module SHALL expose an `Expandable` protocol for components or helper objects that can switch between collapsed and expanded presentation states.

#### Scenario: Component accepts expansion state
- **WHEN** application code calls `setExpanded(true)` on an expandable component
- **THEN** the next render uses the expanded presentation state

#### Scenario: Component accepts collapsed state
- **WHEN** application code calls `setExpanded(false)` on an expandable component
- **THEN** the next render uses the collapsed presentation state

### Requirement: Expandable text component
The extras module SHALL provide an `ExpandableText` component that renders collapsed text or expanded text using the existing component width contract.

#### Scenario: Collapsed text renders by default
- **WHEN** an `ExpandableText` is created with default collapsed state and rendered
- **THEN** it renders the collapsed text within the requested width

#### Scenario: Expanded text renders after state change
- **WHEN** `setExpanded(true)` is called on an `ExpandableText`
- **THEN** it renders the expanded text within the requested width

#### Scenario: Text updates invalidate render cache
- **WHEN** the collapsed or expanded text provider changes visible output
- **THEN** the component invalidates cached output before the next render

### Requirement: Expandable section component
The extras module SHALL provide an `ExpandableSection` component that renders a title and a collapsed or expanded body while preserving width-safe output.

#### Scenario: Section renders collapsed body
- **WHEN** an `ExpandableSection` is rendered while collapsed
- **THEN** it includes the section title and collapsed body within the requested width

#### Scenario: Section renders expanded body
- **WHEN** `setExpanded(true)` is called on an `ExpandableSection`
- **THEN** it includes the section title and expanded body within the requested width

#### Scenario: Section can show hint text
- **WHEN** an `ExpandableSection` is configured with hint text
- **THEN** the rendered output includes that hint in the state where the configuration says it is visible

### Requirement: Expansion controller
The extras module SHALL provide an expansion controller that owns one Boolean expansion state and applies it to registered expandable instances without depending on `TUI`.

#### Scenario: Controller applies expanded state
- **WHEN** application code sets the controller state to expanded
- **THEN** each currently registered expandable receives `setExpanded(true)`

#### Scenario: Controller applies collapsed state
- **WHEN** application code sets the controller state to collapsed
- **THEN** each currently registered expandable receives `setExpanded(false)`

#### Scenario: Controller supports unregistering
- **WHEN** an expandable is unregistered from the controller
- **THEN** later controller state changes do not mutate that expandable

#### Scenario: Controller does not request render directly
- **WHEN** controller state changes
- **THEN** the controller does not call `TUI.requestRender` or retain a `TUI` reference

### Requirement: Agent concepts remain excluded
The extras module SHALL NOT define agent-session, LLM, tool execution, extension runtime, model selection, or message-history APIs.

#### Scenario: Extras package is inspected
- **WHEN** public APIs under `scalatui.extras` are inspected
- **THEN** they contain reusable TUI helpers only and do not expose agent-specific types

### Requirement: Final themed extras output remains width-safe
Extras components SHALL apply configured theme callbacks before constraining each final output line to the requested width with ANSI-aware visible-width semantics.

#### Scenario: Theme adds visible text
- **WHEN** an extras theme callback adds visible characters to a rendered line
- **THEN** the component constrains the final themed line to the requested visible width

#### Scenario: Theme adds ANSI styling
- **WHEN** an extras theme callback adds ANSI styling and visible content
- **THEN** the component preserves valid terminal styling while returning no line wider than the requested visible width

#### Scenario: Narrow width follows final output contract
- **WHEN** a themed extras component is rendered at a narrow requested width
- **THEN** every final returned line has visible width less than or equal to that width

