## Decisions

Up/Down use the cached private EditorRenderPlan at the latest rendered width (one cell before first render). Save the first vertical command's display column; preserve it through consecutive vertical moves and short rows, including boundary no-ops. Map through projected units and validate source ownership rather than UTF-16 or raw grapheme arithmetic. A wrapped row's end belongs to the following row, so clamp to its last reachable boundary. Some sanitized expansions share source ownership and cannot independently host a logical cursor; skip unrepresentable rows in the requested direction, never split source units.

Reset preferred column on width change, explicit cursor placement, nonvertical movement commands, text edits, paste completion, undo, history transitions, submit and completion application. Page/wheel movement starts a separate column sequence. Pure render at unchanged width does not reset it.

History entry requires nonempty history and either an empty buffer or Up at visual row zero and display column zero. While browsing, Up at the first visual row loads older entries and Down at the last visual row loads newer entries. Otherwise arrows navigate rows; cursor movement does not abandon the browse. Capture the original EditorBuffer snapshot exactly once on entry, including text, cursor, paste registry and counter. Returning past newest restores it and clears browsing. History loads start at logical (0,0), matching current implementation. Entry pushes one undo snapshot; browsing/restoration adds none. Mutations, explicit setText/setCursor and undo end the browse and discard the retained draft; the currently displayed recalled text becomes editable. Submit also ends browsing. Actual history additions end browsing to avoid index drift; ignored blank or consecutive duplicate additions leave browsing and preferred column unchanged.

Only text changes emit onChange; cursor-only restoration still requests render. Pending streamed paste commits before arrows as before. Autocomplete retains first refusal on arrows.

## Deferred contract

No public Editor snapshot API is introduced. A future full snapshot must specify undo-stack and kill-ring ownership, paste registry/counter lifetime and collision safety, pending paste transactions, history state, preferred column, autocomplete cancellation and callback/render ordering. Text-plus-cursor is not a full portable editor snapshot. Composer queueing, busy-state policy and conversation execution remain application-owned.

## Validation

Shared deterministic navigation/history tests cover Unicode, ANSI, wrap boundaries, resize, callbacks, undo and paste. Run focused JVM/Native suites, full JVM core tests, quality, docs and strict OpenSpec validation. A core-only runnable composer exercises application-owned submission/history without network or chat infrastructure.
