# Deterministic terminal input timing

## Why
The split-CSI test sleeps 50 ms against a 75 ms framing deadline and failed in macOS CI. Wall-clock scheduling obscures deadline behavior.

## What Changes
- Add an internal monotonic clock and monitor-wait boundary with unchanged production deadlines.
- Inject timing into stream parsing and ordered flush waiting.
- Replace the wall-clock split-CSI test with controlled virtual-time coverage, retaining real terminal integration tests.

## Capabilities
### New Capabilities
None.
### Modified Capabilities
- `terminal-runtime`: deterministic input framing deadlines.

## Impact
Internal core terminal implementation and tests; no new dependencies or public constructor changes. First architecture PR, based on dependency upgrades PR #73.
