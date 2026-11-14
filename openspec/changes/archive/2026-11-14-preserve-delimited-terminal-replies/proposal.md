# Preserve delayed terminal reply framing

## Why
Published Siglyph0.10.0 turns the suffix of an OSC11 reply delayed beyond75ms into typed text and Ctrl+G. Pi commit edb2eb966d7ed7238e70c2216adab6f69aabe8bb supplies useful framing/resynchronization regressions.

## What Changes
- Recognize bounded, unambiguous OSC color/palette/status, DCS version and APC graphics reply headers.
- Preserve their delimiter framing across idle and zero-read timeouts, streaming bounded raw chunks.
- Recover on CAN/SUB, new non-ST escape sequences, explicit flush/EOF and clear without inventing keyboard events.

## Capabilities
### New Capabilities
None.
### Modified Capabilities
- terminal-runtime: delayed recognized replies remain raw protocol input rather than keyboard tails.

## Impact
Shared parser, StreamTerminal timeout handling and JVM/Native tests; no new dependencies or public Terminal interface changes.
