## Why
Finish the ownership gap between ApplicationScope and TaskScheduler: delayed callbacks currently require separate cancellation and can outlive a TUI session. Recurring work must not accumulate ticks or silently ignore overload.

## What Changes
- Add bounded scope-owned one-shot and fixed-delay recurring registrations.
- Cancel timers on task/scope close and TUI stop, retaining failed/in-progress cleanup obligations and blocking restart until resolved.
- Handle fire-before-publication without running user code until a cancellation handle is available.
- Stop recurrence on UI admission rejection, callback failure or rearm failure; preserve explicit status/failure observation.
- Verify ManualTaskScheduler and real executor integration on JVM/Native.

## Capabilities
### New Capabilities
- `scope-owned-timers`: lifecycle-owned delayed and recurring application updates.
### Modified Capabilities

## Impact
Shared core scope/TUI cleanup, scoped task handles, tests and docs. Scheduler/executor ownership stays external. No hidden thread, dependency or automatic file follower is introduced. Reference-app automatic/file-backed production remains a separate integration slice.
