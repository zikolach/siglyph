## MODIFIED Requirements

### Requirement: Nested container bounds
Container-style components, including padded vertical `Box` composition, SHALL record nested child bounds in visual render order using the same normalized geometry as their rendered output.

#### Scenario: Vertical container records child rows
- **WHEN** a vertical container renders two child components with heights 2 and 3 from row 0
- **THEN** the retained bounds tree records the first child at row 0 and the second child at row 2

#### Scenario: Nested container records descendants
- **WHEN** a container renders another container that renders a child component
- **THEN** the retained bounds tree includes the descendant child with terminal-relative bounds

#### Scenario: Padded Box translates descendant bounds
- **WHEN** a `Box` with normalized horizontal and vertical padding renders a child or nested container
- **THEN** the retained tree records every descendant at the padded column and accumulated padded body row used for its visible output
