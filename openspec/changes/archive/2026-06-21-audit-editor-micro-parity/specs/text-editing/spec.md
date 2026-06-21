## ADDED Requirements

### Requirement: Text editing key micro-parity audit
The project SHALL audit upstream editor and input editing key behaviors against local text-editing tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Editing key behavior is already covered
- **WHEN** upstream movement, deletion, history, undo, yank, or submit behavior has an equivalent local test and matching behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Editing key behavior lacks only a test
- **WHEN** local editing key behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Editing key behavior gap is found
- **WHEN** local editing key behavior differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Editing key intentional deviation is found
- **WHEN** local editing key behavior intentionally differs from upstream because a terminal encoding cannot be represented reliably
- **THEN** the audit records intentional deviation and updates porting notes
