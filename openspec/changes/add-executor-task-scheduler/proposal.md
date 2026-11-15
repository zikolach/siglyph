## Why
#94 now has bounded update delivery and a deterministic scheduling contract. Add the production executor adapter independently before making scheduled registrations scope-owned.

## What Changes
- Add ExecutorTaskScheduler over an explicitly application-owned ScheduledExecutorService.
- Bound registrations, reject overload/closed admission, prevent unclaimed callbacks after cancel/close and handle callback-before-handle-publication races.
- Report asynchronous callback failures explicitly and retain failed cancellation obligations for retry.
- Verify the real executor path on JVM and Native alongside deterministic provider/race tests.

## Capabilities
### New Capabilities
- `executor-task-scheduling`: borrowed production executor scheduling with explicit ownership.
### Modified Capabilities

## Impact
Shared scheduling adapter, tests, docs and a source consumer example. No executor is created implicitly, no executor configuration is changed and no external dependency is added. Scope-owned timers, recurring work and automatic reference-app integration remain pending.
