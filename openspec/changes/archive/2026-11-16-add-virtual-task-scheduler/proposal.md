## Why
Continue #94/#96 with a scheduling contract and deterministic clock before binding production timers to application scopes. Tests need bounded deadline advancement, cancellable registrations and explicit pending-work cleanup rather than sleeps.

## What Changes
- Define experimental TaskScheduler/ScheduledTask contracts and explicit capacity/closed rejection.
- Add a bounded ManualTaskScheduler using deadline/registration ordering and explicit owner-thread advancement.
- Let TUITestDriver own an optional manual scheduler, advance virtual time and report pending callbacks while still cleaning up.
- Add shared JVM/Native tests and a source-checkout example integrating virtual deadlines with ApplicationScope.

## Capabilities
### New Capabilities
- `virtual-task-scheduling`: cancellable delayed callbacks and deterministic bounded advancement.
### Modified Capabilities

## Impact
Shared-core scheduling/testing helpers, tests and docs. No thread, executor, timer or external dependency is introduced. Production wall-clock adapters and scope-owned timer registration/cancellation remain the next #94 slice; this does not complete automatic log production.
