## ADDED Requirements

### Requirement: Public ANSI geometry behavior is documented when clarified
If this change clarifies or exposes public ANSI geometry behavior, the public API SHALL document tab width, wide-grapheme slicing behavior, and width-safety expectations.

#### Scenario: Public tab behavior is documented
- **WHEN** tab width is part of public helper behavior
- **THEN** Scaladoc and project documentation state the tab display width used by the helper

#### Scenario: Public slicing behavior is documented
- **WHEN** a public helper slices or truncates terminal text
- **THEN** Scaladoc explains that ANSI escape sequences are non-printing and wide grapheme clusters are not emitted partially

#### Scenario: No public API change needs no new API docs
- **WHEN** geometry hardening changes only internal behavior and tests
- **THEN** no new public helper documentation is required beyond updated regression notes if relevant
