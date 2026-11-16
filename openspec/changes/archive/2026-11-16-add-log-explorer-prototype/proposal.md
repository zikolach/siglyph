## Why
Roadmap #99 starts with reference-app discovery (#93 Phase A). A small deterministic log explorer should expose application glue before we introduce scheduling, table or testing APIs.

## What Changes
- Add an application-owned bounded event model and a step-driven fullscreen prototype using existing Input, ScrollView and VStack components.
- Add filtering, stable row selection during ingestion, details, follow-latest, pause and terminal cancellation.
- Reuse the existing JVM/Native fullscreen launcher modules via an explicit `--log-explorer` option, preserving the default demo.
- Add shared deterministic tests, a 100,000-event retention test, run instructions and discovery findings.

## Capabilities
### New Capabilities
- `log-explorer-demo`: deterministic, bounded reference-application discovery slice.
### Modified Capabilities

## Impact
Demo sources, launchers, tests and documentation only. No public core API, third-party dependency, build module or hosted CI job is introduced. Automatic scheduling, file following, reusable tables, consumer test harnesses and theme extraction remain later work; this prototype is deliberately step-driven, not an asynchronous-streaming claim.
