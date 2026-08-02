## MODIFIED Requirements

### Requirement: Ordinary work selection is deterministic and fair
The runtime SHALL retain one serialized owner, prioritize retained query and append completions and stop or cleanup progression as urgent work, and select ordinary Structural, Action, Ingress, Control, Append, and Render work in deterministic cyclic order. Queued categories SHALL remain FIFO, Append SHALL retain at most 64 accepted incomplete operations, and Render SHALL remain coalesced.

#### Scenario: Continuously ready ordinary category is bounded
- **WHILE** ordinary categories remain continuously ready
- **WHEN** the owner selects ordinary work
- **THEN** the runtime services each category within six ordinary selections

#### Scenario: Urgent work precedes ordinary work
- **WHILE** retained query or append completions or stop or cleanup progression is ready
- **WHEN** the owner selects work
- **THEN** it selects urgent work before an ordinary category without creating a second owner

#### Scenario: Invalidated append yields to fair selection
- **WHEN** a resize-invalidated Append candidate remains pending
- **THEN** it retains FIFO priority over later appends but returns to the six-category selection cycle before another render attempt
