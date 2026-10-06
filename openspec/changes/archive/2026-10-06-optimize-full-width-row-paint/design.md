## Context

The current painter slices the source to the effective clip, then `compositeLine` slices that result again and prepends/appends SGR reset around the overlay. A raw source-line assignment would bypass sanitization, width clipping, and closure of open SGR/OSC8 state. The final truncation cannot repair every such difference.

## Decision

Only when the effective source starts at column zero, spans the terminal width, and the target row is untouched, produce the empty-base composition directly from the already-sliced text. Preserve the compositing reset prefix/suffix and the second bounded slice, because replay/closure and metadata order must match the established path exactly. Mark the prepared row as width-safe and sanitized; skip its redundant final truncation/sanitization only if no later paint touches it. Later partial composition or decoration clears that mark. Continue final frame validation. If a child, overlay, or scrollbar later paints the row, it sees the same base bytes and follows the ordinary path. Do not apply the shortcut to partial clips, nonzero columns, nonempty targets, or typed controls.

## Verification

Shared tests assert exact row bytes for plain, short, Unicode, unclosed and adjacent-reset SGR/OSC8, and subsequent partial-width overpaint; existing tests cover typed control, cursor, and marker translation. Measure a fixed 80x24 full-width frame loop on JVM (thread allocation and time) and Native (time only) before and after, reporting raw medians and workload identity rather than a universal speedup claim.
