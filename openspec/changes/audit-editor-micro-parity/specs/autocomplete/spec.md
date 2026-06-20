## ADDED Requirements

### Requirement: Editor autocomplete micro-parity audit
The project SHALL audit upstream editor autocomplete interaction behaviors against local autocomplete tests and classify each reviewed behavior as exactly one of: already covered, missing test only, behavior gap, or intentional deviation.

#### Scenario: Autocomplete behavior is already covered
- **WHEN** upstream autocomplete navigation, refresh, completion, or stale-response behavior has an equivalent local test and matching behavior
- **THEN** the audit records the behavior as already covered with source references

#### Scenario: Autocomplete behavior lacks only a test
- **WHEN** local autocomplete behavior matches upstream but no focused local test exists
- **THEN** the audit records missing test only and adds or schedules the focused test

#### Scenario: Autocomplete behavior gap is found
- **WHEN** local autocomplete behavior differs from upstream without an intentional deviation
- **THEN** the audit records behavior gap and creates follow-up OpenSpec work instead of changing behavior in the audit

#### Scenario: Autocomplete intentional deviation is found
- **WHEN** local autocomplete behavior intentionally differs from upstream due to callback cancellation, overlay ownership, or typed input routing
- **THEN** the audit records intentional deviation and updates porting notes
