# Validation evidence

The initial handoff authorized local implementation only. Subsequent user authorization covers commit, PR merge and release after validation; release preparation and publication evidence are recorded separately. The results below describe pre-publication validation.

- New Editor API tests failed to compile against the prior JVM/Native API, then passed after implementation. `EditorDraftSuite` has nine tests covering typed streamed paste, 4100-character validation, Unicode, snapshot reuse/transfer, cursor/history, queue-save patterns, callbacks, autocomplete cancellation and invalid-handle atomicity.
- Four buffer regressions failed before the fix: recursive/metacharacter expansion corrupted payloads, literal IDs collided with new markers, out-of-range IDs threw NumberFormatException, and exhausted counters silently mutated state. Five added buffer tests now pass, including noncanonical literal IDs.
- Final combined validation passed: `mill core.test`; Native `EditorDraftSuite`, `EditorSuite`, `EditorNavigationSuite`, `EditorBufferSuite`; `mill quality`; documentation and API-inventory checks. Strict OpenSpec validation passed after splitting an overlong requirement.
- `javap` confirms the public `EditorDraftSnapshot` interface has no state-bearing members. Internal JVM bridges are not a security boundary.
- Four modules were staged locally as `0.13.0-editor-draft-1795378451429`. The artifact compatibility checker passed selected JVM ABI, old Scala 3.9.0/Java/Kotlin binaries on candidate, and Scala JVM/Java/Kotlin/Native source consumers. Native consumers were rebuilt and run. This is not a published coordinate or a complete ABI guarantee.

## Contract limits

Reads observe committed buffer state only; they neither commit nor include pending streamed paste bytes. Restore discards pending paste and undo, resets browsing/action state and cancels autocomplete; it does not restore a full editor checkpoint. Literal text inserted later that exactly matches an already registered marker retains the existing alias interpretation. New IDs avoid existing literal references, and payload expansion itself is literal and nonrecursive; a full structural-span/provenance redesign is not claimed. Published 0.12.0 and 0.13.0 do not contain these APIs. Current checkout/candidate uses Scala 3.10.1, JDK 21 and Native 0.5.12.
