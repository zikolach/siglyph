# Runtime diagnostics and resize clearing

`TUIOptions.diagnosticObserver` enables structured diagnostics for one TUI instance. The observer
receives lifecycle, resize, redraw, and terminal-write metadata in runtime order. Events expose
only enums, terminal/frame geometry, resize generations, row indexes, and output byte counts. They
do not include application text, image payloads, raw terminal-query replies, or output bytes.

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
