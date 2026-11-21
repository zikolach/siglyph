## Why

Conversational output arrives in fragments. Whole-document Markdown remains useful but needs an opt-in bounded streaming surface with explicit provisional rendering and conservative parse reuse.

## What Changes

- Add an opt-in streaming component with append admission, finish/reset, deterministic work counters, and baseline-only safe-prefix reuse.
- Project unfinished inline links/emphasis for live display without changing stored source or existing complete-document APIs.
- Add shared JVM/Native regressions and a runnable scoped-scheduler viewer using ScrollView follow-end behavior.

## Capabilities

### New Capabilities
- `streaming-markdown`: bounded streaming source, provisional rendering, conservative reuse and viewer behavior.

### Modified Capabilities

None. Existing complete-document/parser plugin contracts remain unchanged.

## Impact

Markdown module, shared tests, application-owned viewer example, documentation. No runtime dependencies or transport/provider integration.
