## Why
Roadmap #96 calls for consumer-facing application tests without copying internal VirtualTerminal setup. The log and table examples currently repeat typed input, flush, resize and cleanup boilerplate.

## What Changes
- Add an experimental framework-independent `scalatui.testing.TUITestDriver` in shared core beside the existing VirtualTerminal foundation.
- Manage start/stop, scripted typed keys/text/paste/resize, immutable screen snapshots and bounded readable assertion diffs.
- Bound driver input, geometry and script steps; retain only capped output for the last action rather than an ever-growing write transcript.
- Document synchronous completion honestly. Virtual time and scheduler draining remain dependent on the #94 scheduling contract, not invented through sleeps or wall-clock polling.

## Capabilities
### New Capabilities
- `application-test-driver`: synchronous deterministic application test fixtures and screen assertions.
### Modified Capabilities

## Impact
Shared-core helpers, tests and docs only. No test-framework/runtime dependency, build module, hosted service or emulator implementation. Existing VirtualTerminal and TUI semantics remain unchanged. Example-test migration is a separate dependent PR.
