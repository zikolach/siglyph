# Proposal

## Why

Siglyph exposes Kitty keyboard negotiation hooks, but its TUI startup never calls them and its input path never accepts negotiation replies. Consequently, a terminal or multiplexer can deliver Shift+Enter as ordinary Enter even though Siglyph's parser correctly handles encoded modifiers.

## What Changes

- Make TUI startup request Kitty keyboard negotiation on backends that implement `KittyKeyboardProtocolTerminal`.
- Consume complete keyboard negotiation replies before component input and enable disambiguation through the existing backend hook.
- Keep protocol output under the runtime's serialized terminal write boundary.
- Cancel pending negotiation during shutdown without popping a keyboard mode that Siglyph never pushed.
- Preserve retryable cleanup after enable or disable output failure.
- Retain the alternate screen while keyboard cleanup remains incomplete and let callers retry restoration through `TUI.stop()`.
- Add lifecycle regression tests for fragmented replies, modified Enter, fallback, stop races, and failure cleanup.
- Document automatic negotiation and the limits of legacy terminal input.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `terminal-runtime`: Require automatic TUI keyboard negotiation, reply interception, and balanced keyboard mode cleanup.

## Impact

Shared TUI protocol handling, the shared keyboard negotiator, JVM and Native keyboard hooks, regression tests, and terminal documentation are affected. No dependency changes or new public configuration are planned. Stream-only and unsupported backends retain existing behavior.

This change covers Siglyph. Publishing a release, updating Sigma's dependency, and verifying physical Ghostty input through Herdr require separate downstream work and approval.
