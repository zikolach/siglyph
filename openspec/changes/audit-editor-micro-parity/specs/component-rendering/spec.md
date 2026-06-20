## ADDED Requirements

### Requirement: Editor visual layout micro-parity audit
The project SHALL audit upstream editor visual layout behaviors against local component rendering tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Visual layout behavior is already covered
- **WHEN** upstream editor wrapping, cursor placement, or Unicode layout behavior has an equivalent local test and matching behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Visual layout lacks only a test
- **WHEN** local editor rendering behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Visual layout behavior gap is found
- **WHEN** local editor rendering differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Visual layout intentional deviation is found
- **WHEN** local rendering intentionally differs from upstream because of fake cursor, hardware cursor marker, or typed component contracts
- **THEN** the audit records intentional deviation and updates porting notes
