## 1. Contract and regression tests
- [x] Specify opaque snapshot, read-only projection and restore invalidation semantics.
- [x] Add red JVM/Native tests for typed paste, logical length, Unicode, literals, repeated restore, cursor/history and queued edit saves.

## 2. Implementation
- [x] Implement synchronized submittedText and opaque detached draft snapshot/restore.
- [x] Verify marker identity and new-paste collision behavior, including literal payload expansion and the documented legacy alias boundary.
- [x] Document unreleased API and update compatibility inventory.

## 3. Verification
- [x] Run full JVM core and targeted Native tests.
- [x] Run formatting, strict quality, documentation, compatibility inventory and OpenSpec checks.
- [x] Verify a local candidate against selected ABI and Scala/Java/Kotlin/Native consumers.

See [validation evidence and limitations](verification.md).
