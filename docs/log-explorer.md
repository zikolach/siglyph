# Log explorer discovery prototype

[Documentation index](README.md) · [Roadmap #99](https://github.com/zikolach/siglyph/issues/99) · [Reference app #93](https://github.com/zikolach/siglyph/issues/93)

This started with **Phase A** and now integrates the experimental [DataTable](data-table.md) from the independent #95 widget workstream. The source is deterministic and **step-driven**, not an automatically streaming file/network source. It owns no workers, timers, external dependencies or data-source descriptors.

## Run from a checkout

Use the [contributor toolchains](../CONTRIBUTING.md#development-setup) and a real macOS/Linux terminal.

```bash
mill fullscreenJvmDemo.run --log-explorer
```

For Scala Native:

```bash
mill fullscreenNativeDemo.nativeLink
./out/fullscreenNativeDemo/nativeLink.dest/out --log-explorer
```

Without the option, both launchers still run the original fullscreen transcript demo. Unknown options fail before opening a terminal.

## Controls

The filter input stays focused. Ordinary text edits the draft; Enter commits it. Filter matching is ASCII case-insensitive over retained event labels (ID, level, service and message).

| Key | Action |
| --- | --- |
| Ctrl+N | Generate 20 events (one logical source tick) |
| Ctrl+P | Pause/resume source ticks; paused ticks consume no IDs |
| Up / Down | Browse rows and disable follow-latest |
| PageUp / PageDown | Browse ten rows |
| Ctrl+G | Follow the latest matching event again |
| Ctrl+X | Cancel generation permanently; retained events remain inspectable |
| Enter | Commit the filter; empty text clears it |
| Esc / Ctrl+C | Exit and restore the terminal |

The initial fixture has 40 events. Every seventeenth event is ERROR; otherwise every fifth is WARN, and the rest INFO. Services rotate through store, worker and api. Try `ERROR`, Enter, Up, then Ctrl+N to inspect stable selection as new matching rows arrive.

## Limits and behavior

- Retention defaults to **200 events**; the application factory accepts capacities from 1 through 10,000. Evictions are visible in the header. There is no hidden history.
- The model accepts batches of at most 1,000 events. Generated messages have bounded format; the stress test processes 100,000 events and retains only the configured tail.
- Browse selection survives ingestion by identity until evicted, then falls back to the oldest matching row. Changing a filter intentionally selects the first match in browse mode or last match in follow mode. No matches means no selection.
- A committed filter is limited to 256 UTF-16 units; oversized values leave the previous filter active and report an error. The existing Input's **draft and undo history are not bounded by this limit**. The retention guarantee applies to generated records, not arbitrary application input memory.
- Case folding is ASCII-only because generated labels are ASCII. It preserves the committed filter length and avoids locale-dependent behavior or unsupported Native `Locale` APIs; broader Unicode search belongs to a future data-model contract.
- The application still projects its bounded matching set in O(retained data) work. It rebuilds indexed `TableData` only when generated data or the committed filter changes, not on arrow-key navigation. `DataTable` formats the requested viewport rows, with a non-sticky header; model filtering and indexing are not virtualized.
- Pause and cancellation affect generation only. No queued events are hidden, and cancellation cannot be resumed. Application stop/run completion also cancels the model, including terminal-start failure.
- Terminal stdin is reserved for keyboard input. File following, rotation/truncation policy and background delivery remain Phase B work.

## Discovery findings and candidate contracts

| Current application-owned work | Evidence in this slice | Candidate follow-up |
| --- | --- | --- |
| Source lifetime | Explicit model cancellation in run/stop and exit shortcuts; manual source ticks | [#94](https://github.com/zikolach/siglyph/issues/94): owned cancellation, injectable clock and serialized bounded updates. No async safety has been validated yet. |
| Data projection and selection | Queue retention and bounded filtering stay application-owned; the new table supplies columns and visible-row formatting | [#95](https://github.com/zikolach/siglyph/issues/95): indexed stable IDs and viewport ranges are implemented; explicit parent scrolling and domain selection policy remain in the app. |
| Test setup | VirtualTerminal start/flush/resize/input and explicit finally-stop in each scenario | [#96](https://github.com/zikolach/siglyph/issues/96): consumer driver and lifecycle assertions, with virtual time when scheduling is introduced. |
| Presentation | Application-owned row prefix, detail text, footer and keyboard commands | [#97](https://github.com/zikolach/siglyph/issues/97): semantic roles and navigation conventions after table usage validates the states. |

The table is an experimental public component; the other seams remain candidates, not newly public runtime APIs. This baseline intentionally has zero background workers and zero timers; a reduction in asynchronous plumbing cannot be measured until a real producer is integrated. Keeping this distinction avoids claiming a solved lifecycle problem from a synchronous prototype.

## Validation

Implementation: [shared model](../fullscreenDemo/src/scalatui/demo/LogExplorerModel.scala), [UI](../fullscreenDemo/src/scalatui/demo/LogExplorerDemo.scala), and [shared tests](../fullscreenDemo/test/src/scalatui/demo/LogExplorerSuite.scala).

The original Phase A verification passed on JVM and Native, including PTY restoration checks. Table integration adds an indexed-data reuse regression; see [table validation](data-table.md#validation) for the expanded checks. PTY checks do not establish pixel-level appearance.

```bash
mill fullscreenDemo.test
mill fullscreenDemoNative.test
```

The shared suite covers deterministic partition-independent generation, 100,000-event retention/eviction, stable selection, filtering and empty states, paused/cancelled ticks, key releases, narrow resize, explicit cleanup and startup failure. Existing fullscreen transcript tests remain in the same modules.

Virtual screen assertions and PTY byte-stream smoke checks are not pixel-level emulator validation. For a manual smoke, run the application, exercise the controls above, resize the terminal and verify that exit restores normal shell input.
