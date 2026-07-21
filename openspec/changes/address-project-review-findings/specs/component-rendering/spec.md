## ADDED Requirements

### Requirement: Text components preserve logical line boundaries
Built-in text components that accept multiline content SHALL interpret CRLF, CR, and LF as equivalent logical line boundaries before width wrapping, while ordinary text fragments SHALL continue to use the existing ANSI allowlist and control sanitization policy.

#### Scenario: LF creates a new rendered row
- **WHEN** a `Text` component renders content containing LF
- **THEN** content before and after LF is rendered on separate logical rows and each row is wrapped independently to the requested width

#### Scenario: CRLF and CR match LF behavior
- **WHEN** otherwise identical text uses CRLF, CR, or LF line endings
- **THEN** the component produces the same logical rows and width-safe visible content

#### Scenario: ANSI state remains safe across logical rows
- **WHEN** an allowed SGR or OSC 8 span crosses a logical line boundary
- **THEN** rendered rows preserve the supported visual state using bounded metadata replay or closure without granting authority to any other control bytes

#### Scenario: Other C0 controls remain ordinary data
- **WHEN** multiline text contains a C0 control other than a recognized line ending
- **THEN** the existing ordinary-string sanitization policy handles it and the control does not become terminal structure or semantic output authority
