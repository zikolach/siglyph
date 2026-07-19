## ADDED Requirements

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
