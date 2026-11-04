## 1. Implementation
- [x] Add an internal monotonic clock and monitor-wait boundary.
- [x] Route stream parser timestamps and ordered flush waits through the same timing instance.
- [x] Replace sleep-based split-CSI coverage and test exact deadline behavior.

## 2. Verification
- [x] Run focused timing and terminal suites on JVM and Native (29 tests each).
- [x] Run scoped Scalafmt/Scalafix and validate this OpenSpec change.

Full-tree formatting is verified again after the remaining stacked refactors finish; concurrent worker edits initially failed the full-tree formatting check.
