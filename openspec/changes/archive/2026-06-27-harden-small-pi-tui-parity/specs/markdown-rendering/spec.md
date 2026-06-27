## ADDED Requirements

### Requirement: Markdown streaming fenced-code stability
The Markdown renderer SHALL keep fenced-code block rendering stable while markdown content is streaming and partial closing fences are received.

#### Scenario: Partial closing fence keeps code block stable
- **WHEN** markdown content contains an opening fenced-code block and a trailing partial closing fence that is not yet a complete fence
- **THEN** the renderer keeps the content in the code block and does not shrink the block as if the fence had closed

#### Scenario: Complete closing fence closes code block
- **WHEN** markdown content contains an opening fenced-code block followed by a complete closing fence
- **THEN** the renderer closes the code block and renders following markdown using normal block parsing

#### Scenario: Streaming fence behavior remains width-safe
- **WHEN** streaming fenced-code input is rendered at a narrow width
- **THEN** every output line remains ANSI-aware width-safe for the requested width
