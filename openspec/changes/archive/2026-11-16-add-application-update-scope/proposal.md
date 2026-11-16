## Why
Continue #94 with the bounded, lifecycle-bound UI-update slice before introducing timers. Async producers need an explicit admission result, serialized application updates and a cancellation boundary that cannot leak old results into a restarted TUI.

## What Changes
- Add experimental ApplicationScope handles opened on an already-running TUI.
- Reuse the existing bounded component-effect coordinator for serialized delivery, with an additional per-scope pending limit and a 64-open-scope limit.
- Reject overload/closed-session submissions explicitly; cancel unclaimed work on scope close or TUI stop.
- Define cooperative close/quiescence and retain the existing TUI failure channel.
- Add deterministic concurrency tests using the application driver, plus JVM/Native coverage and usage docs.

## Capabilities
### New Capabilities
- `application-update-scope`: bounded session-owned application update delivery.
### Modified Capabilities

## Impact
Shared core TUI/application-scope API, tests and docs. No new thread, timer, scheduler, dependency or input queue. Scheduled ticks, virtual time and log-explorer automatic production remain the next #94 slice; this change does not complete #94 or #96.
