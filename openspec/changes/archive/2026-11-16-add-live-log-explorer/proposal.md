## Why
Complete the #94 reference-app acceptance slice by using scoped recurring work in the log explorer. Preserve the deterministic step mode and keep file-following (#93) separate.

## What Changes
- Add an opt-in --live-log-explorer mode on both shared launchers.
- Generate bounded batches through ApplicationScope on an explicitly application-owned executor.
- Preserve filtering, browse selection, pause and immediate terminal cancellation; stop timers and join the worker on all exit/failure paths.
- Add manual-clock interaction tests, lifecycle failure tests and real launcher PTY smoke.

## Capabilities
### New Capabilities
- `live-log-explorer`: automatically generated log events with explicit producer ownership.
### Modified Capabilities

## Impact
Demo/controller code, launchers, tests and documentation. No core runtime API, dependency or hosted CI job is added. Generated data is not file/network ingestion; rotation/truncation/error policy remains #93 follow-up work.
