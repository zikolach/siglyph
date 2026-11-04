## Context

Interactive JVM and Native backends share paste, mouse, and Kitty wire protocols but differ in worker and raw-mode lifecycle.

## Goals / Non-Goals

Share protocol state and negotiation with no public compatibility change. Do not change StreamTerminal, OrderedInputDelivery, TerminalInputBuffer, worker management, or platform restoration.

## Decisions

Use a package-private InteractiveProtocolSession with an injected write function. Backend synchronization continues to serialize access. Set cleanup obligations before enable writes because writes can partially succeed. Clear obligations only after successful disable writes. Store the enabled mouse mode independently of later backend configuration.

Expose individual cleanup operations so existing backend attempt wrappers preserve failure injection, first-error/suppressed-error aggregation, and retries. JVM cleanup remains Kitty, mouse, paste, input, stty; Native remains input, Kitty, mouse, paste, termios. Pending Kitty cancellation never emits a pop; failed Kitty disable retains negotiation state and the pop obligation.

## Risks / Trade-offs

The helper deliberately does not coordinate platform cleanup. Backend call order remains explicit to avoid changing shutdown semantics. Fake-output tests exercise protocol failures without requiring a live TTY; backend suites remain the integration coverage.
