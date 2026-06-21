## ADDED Requirements

### Requirement: Editor buffer micro-parity audit
The project SHALL audit upstream editor buffer behaviors against local editor buffer tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Buffer behavior is already covered
- **WHEN** an upstream editor buffer behavior has an equivalent local test and matching local behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Buffer behavior lacks only a test
- **WHEN** local editor buffer behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Buffer behavior gap is found
- **WHEN** local editor buffer behavior differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Intentional buffer deviation is found
- **WHEN** local behavior intentionally differs from upstream due to Scala API or typed-input design
- **THEN** the audit records intentional deviation and updates porting notes
