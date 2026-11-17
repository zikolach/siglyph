## ADDED Requirements

### Requirement: Language-native application composition

The library SHALL provide additive, explicitly unreleased typed Scala composition on JVM and Native and JVM-only Java builders using copied JDK collections and functional interfaces. Optional Kotlin receiver-lambda source extensions SHALL require no new library runtime dependencies or coroutines.

#### Scenario: Equivalent typed table workflow

- **WHEN** Scala, Java or Kotlin callers build a table/filter application and deliver a scheduled update
- **THEN** stable-ID selection, empty data and bounded scope admission follow the existing shared implementation
- **AND** stopping the session prevents stale delivery into a restarted session

#### Scenario: Setup stops before the run loop waits

- **WHEN** managed startup setup stops the runtime or a fast worker fails before waiting begins
- **THEN** the runtime SHALL not restart the terminal or erase the recorded failure
- **AND** the shared startup hook SHALL skip setup after a failed startup

#### Scenario: Managed JVM testing

- **WHEN** Java or Kotlin consumers exercise the workflow using managed virtual testing
- **THEN** callbacks require no Scala function or Option construction
- **AND** cleanup preserves the original failure and suppressed cleanup failures

#### Scenario: Unreleased artifact verification

- **WHEN** candidate consumers use new APIs
- **THEN** they compile and execute against explicitly selected candidate artifacts
- **AND** published baseline examples remain distinct from future post-publication checks
