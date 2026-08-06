# Runtime diagnostics and resize clearing

`TUIOptions.diagnosticObserver` enables structured diagnostics for one TUI instance. The observer
receives lifecycle, resize, redraw, resize-recovery, append-outcome, and terminal-write metadata in
runtime order. Events expose only enums, terminal/frame geometry, resize generations, row indexes,
budgets, row counts, and output byte counts. They do not include application text, image payloads,
raw terminal-query replies, or output bytes.

The callback runs synchronously outside the runtime lifecycle lock and terminal-write lock. If it
throws, Siglyph swallows that observer failure and permanently disables the observer for that TUI;
rendering and terminal restoration continue, and no recursive diagnostic event is emitted. Keep
observer work short and hand metadata to an application-owned queue when processing is expensive.

Normal-screen resizes default to `NormalResizeClearPolicy.ClearScrollback`, preserving the legacy
full-clear sequence: clear the viewport, home the cursor, and clear scrollback. Opting into
`PreserveScrollback` omits the scrollback clear while still clearing and homing the active viewport.
This retains shell history where the terminal supports it, but a terminal with unusual viewport
semantics may briefly show stale rows. Alternate-screen resize behavior is unchanged and never
clears normal-screen scrollback.

Optional `TUIOptions.normalResizeRecovery` adds a text-only recovery phase to committed
normal-screen `PreserveScrollback` geometry changes. Redundant same-size resize notifications do not
invoke recovery or clear the viewport. The runtime first prepares the retained live frame, reserves
its physical footprint (including one cursor anchor for an empty frame), and bounds `maxRows` by
both the old viewport's maximum durable-prefix capacity and space above the new live frame. The
context also supplies previous dimensions and that old capacity so applications can select the old
semantic tail before reflowing it at current width. Returning more rows fails before output rather
than being silently truncated. A stale geometry candidate is discarded and may invoke the provider
again for latest dimensions.

Recovery diagnostics report bounded `Completed`, `Discarded`, or `Failed` outcomes, provider,
row-budget, stale-geometry, or write categories where applicable, the strict budget, recovered row
count, and resize generation. They never retain provider lines, transcript entries, exception
messages, escape source, object references, typed controls, payloads, filenames, or terminal bytes.
Provider failure and row-budget violation use normal fail-fast cleanup; write failure makes no
rollback claim.

Siglyph cannot inspect emulator scrollback or know which rows survived resize reflow. Applications
own semantic transcript retention, current-width reflow, and newest-tail selection. Diagnostics and
the recovery API therefore do not promise terminal-independent deduplication.

Append diagnostics expose only bounded outcome/failure categories, row and control counts, screen
mode, and resize generation. They never retain component text, exception messages, image payloads,
filenames, encoded controls, remapped image IDs, or terminal-write contents. The application-owned
append callback separately receives its typed result.
