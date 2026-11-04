## Context

Query requests reserve a flight under the lifecycle lock, write outside that lock, and reconcile the matching flight identity afterward. Ingress classification snapshots subscribers before publication commits slot removal. Callback completion is separately claimed by the runtime drain.

## Goals / Non-Goals

Goals: extract cohesive flight/subscriber bookkeeping and preserve ordering, cancellation, failure, and cleanup behavior.

Non-goals: changing public APIs, query timeouts, queue admission, lifecycle ownership, renderer policy, terminal capabilities, or backend behavior.

## Decisions

- QueryCoordinator owns typed flight slots, monotonic identities, write reservations, and subscriber states. Its methods require the caller's existing lifecycle lock and never acquire locks or invoke callbacks.
- TUI retains lifecycle-dependent registration routing, retained/post-restoration completion queues, cutoff policy, and drain ownership.
- Cancellation removes only active subscribers, retaining even empty reserved/emitted flights. This preserves single-flight wire behavior.
- Completion snapshots are non-destructive. Classifiers construct completions before Publish.commit clears the slot.
- Emission reconciles only the matching flight id after the write, including when a reply already removed the flight. Reservations are released on both success and failure.
- Stop extracts only emitted background-color and color-scheme completions. Reserved flights await write reconciliation; cursor flights retain their existing cleanup behavior.
- TUI claims under lifecycleLock, invokes outside it, then marks Completed under the lock even when callbacks throw. Cleanup/cutoff and reentrant queue ordering remain unchanged.

## Risks / Trade-offs

The helper intentionally relies on caller synchronization rather than adding a second lock. Existing concurrency tests cover integration, while helper tests isolate transitions without introducing timing dependencies.

## Validation

Run QueryCoordinatorSuite, TUISuite, TUIConcurrencySuite, and TUIKeyboardProtocolSuite. Validate OpenSpec artifacts strictly. Public query signatures are unchanged.
