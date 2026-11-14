# Design

## Evidence
A deterministic0.10.0 probe with OSC11 prefix ESC]11;#1122 and suffix33/BEL/x completes normally without a gap, but a100ms gap flushes the prefix and delivers phantom3,3,Ctrl+G.

## Decisions
Only a complete recognized header within64 bytes enters delimiter-framed reply mode. Ordinary Escape/CSI/unknown control strings keep35/75ms idle behavior. Header/payload bytes remain typed raw input: one RawStart, chunks bounded by TerminalInputChunk.MaxBytes, then RawEnd. Buffer memory stays bounded; oversized replies retain framing and report LimitExceeded rather than reclassifying the remaining tail as keys.

CAN/SUB end a reply as malformed. A new ESC followed by non-backslash ends the old reply and reparses the escape as input. A trailing ESC waits for the next byte so a split ST survives delays. Explicit flush/EOF ends pending raw input; clear resets framing. No wall-clock sleeps or global timer are added. A recognized but unterminated reply requires a delimiter, cancellation, new escape, explicit flush or clear to recover; ordinary typing is not guessed to be a reply tail.

The runtime keeps its existing bounded correlation/ingress ownership. Program-status integration is a later stacked change. Upstream raw-string behavior is not copied wholesale.
