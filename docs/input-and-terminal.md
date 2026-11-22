# Input, optional modules and terminal integration

[Documentation index](README.md)

## Mouse input

Mouse input is disabled by default because terminal mouse reporting captures normal terminal text
selection and wheel scrollback. `TUIOptions(mouseInput = true)` enables basic press, release, and
wheel reporting through xterm mode `1000` and SGR coordinates. `mouseTracking` can request mode
`1002` for drag motion or mode `1003` for uncaptured movement. All-motion degrades to drag mode in
tmux and screen unless that TUI instance explicitly allows forwarding.

`TerminalInput.Mouse` includes zero-based terminal cell coordinates and typed press, release, move,
and wheel actions. Fullscreen derives semantic move, click, multi-click, drag, release, and wheel
events against committed layout. `MouseRegion` preserves child output and returns typed handled,
render, capture, and focus intent. Backends disable the selected tracking mode and SGR coordinates
during cleanup. Unhandled wheel events cannot be returned to terminal scrollback reliably.

A multiline editor with slash, filesystem, attachment, fuzzy, and `#` trigger autocomplete:

```scala
import java.io.File
import scalatui.autocomplete.*
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def editorTui(): Unit =
  val tags = Vector("#bug", "#docs", "#demo")
  TriggerCompletionSource.fromPrefix(
    "#",
    query => Some(tags.filter(_.drop(1).startsWith(query)).map(tag =>
      AutocompleteItem(tag.drop(1), tag, Some("application-owned tag"))
    ))
  ) match
    case Left(error) =>
      System.err.println(s"Invalid autocomplete trigger: ${error.message}")
    case Right(tagSource) =>
      val tui = TUI(SttyTerminal())
      val provider = CombinedAutocompleteProvider(
        commands = Vector(SlashCommand("help"), SlashCommand("quit")),
        pathProvider = Some(FileSystemPathCompletionProvider(FileSystemPathCompletionOptions(
          // Legacy mode: this directory is both the current directory and containment root;
          // parent, home, and absolute syntax remain disabled unless explicitly enabled.
          baseDirectory = File("."),
          maxResults = 20
        ))),
        triggerSources = Vector(tagSource),
        fuzzyRanking = AutocompleteFuzzyRanking.Enabled
      )
      val editor = Editor(options = EditorOptions(
        autocompleteProvider = Some(provider),
        // Debounce is explicit/injectable; pending requests are cancelled and late results ignored.
        autocompleteDebouncer = EditorAutocompleteDebouncer.Immediate,
        onSubmit = text => println(s"Submitted: $text")
      ))

      tui.addChild(Text("Try /he, ./, @\"README, or #do then Tab"))
      tui.addChild(editor)
      tui.setFocus(editor)
      tui.run()
```

## Optional helper modules

Markdown rendering stays in `siglyph-markdown`. The baseline renderer is dependency-free, is available for JVM and Scala Native releases that include the Native artifact, and supports theme hooks, readable link fallback, OSC 8 links when `TerminalCapabilities.hyperlinks` is true, parser adapters, optional fenced-code highlighter hooks, normalized list markers by default, and opt-in source list marker preservation with `MarkdownRenderOptions(preserveSourceListMarkers = true)`. Task-list markers render as visible text; they are not interactive checkboxes. A `Markdown` component retains at most one successful render keyed by its text, geometry, renderer identity, and `MarkdownRenderer.cacheGeneration`; mutable custom renderers must advance that generation or call `invalidate()`.

Image rendering stays in `siglyph-image`. JVM and Scala Native releases that include the Native artifact expose the same baseline public API. `ImageSource.fromFile(path)` loads supported PNG, JPEG, GIF, and WebP files into a validated payload, MIME type, and dimensions. Unsupported terminals render readable fallback text. The `Image` component uses runtime cell-size replies by default; pass `ImageRenderOptions(cellDimensionsSource = ImageCellDimensionsSource.Fixed, cellDimensions = ...)` for deterministic fixed sizing. Fullscreen clips the typed image footprint before protocol encoding. Fully hidden controls are omitted. Known-dimension Kitty images support vertical source-pixel cropping; unknown geometry, horizontal partial clips and iTerm2 partial images remain conservatively omitted. See [vertical Kitty cropping](../docs/kitty-vertical-cropping.md) for the published API and bounds. Per-TUI Kitty retention can reuse recently offscreen unchanged data within count and generation-age bounds. `examples/scala-cli/image.scala` is the quickest visual smoke test for protocol rendering, fallback behavior, runtime cell-size sizing, and row reservation.

Raw terminal image data must cross the shared-core `scalatui.terminal.Base64ImagePayload` boundary before protocol or component output. Use `Base64ImagePayload.from(raw)` for `Either[scalatui.terminal.Base64ImagePayloadError, Base64ImagePayload]`, `Base64ImagePayload.encode(bytes)` for standard padded base64, or `scalatui.image.Image.fromBase64` to validate and construct an image in one typed step. Validation accepts empty input, padded standard base64, and decoder-valid unpadded lengths whose remainder modulo four is zero, two, or three. Remainder one and non-standard syntax are rejected. Accepted spelling is preserved exactly on JVM and Scala Native. Decoder validation transiently allocates and discards decoded bytes; image format, dimensions, chunking, and size limits are not validated.

This is a source-breaking API: protocol helpers and `Image` require `Base64ImagePayload`, and `ImageSource.payload` replaces the raw `base64Data` field. Callers must migrate directly; no compatibility or deprecation path exists. iTerm2 output encodes present filenames as standard base64 over UTF-8 and omits `name=` when absent. Unsupported-terminal fallback output renders C0, DEL, and C1 metadata controls as visible `\\uXXXX` text before ANSI-aware width truncation. Invalid raw payloads create no `Image` and produce no fallback or protocol output.

Expandable helpers stay in `siglyph-extras`. The module depends only on `siglyph-core` and provides reusable compact/detail widgets without terminal backend, Markdown, image, demo, agent-session, LLM message, tool execution, extension runtime, model-selection, or message-history APIs.

```scala
import scalatui.extras.*

val details = ExpandableText(
  collapsedText = "Build finished",
  expandedText = "Build finished with 14 tests and no failures",
  paddingX = 1
)

val section = ExpandableSection(
  title = "Output",
  collapsedBody = "2 lines hidden",
  expandedBody = "line 1\nline 2",
  hintText = Some("Ctrl+O toggles details")
)

val controller = ExpansionController()
controller.register(details)
controller.register(section)
controller.setExpanded(true)
```

## Terminal integration helpers

`TUI` exposes optional terminal integration helpers for supported interactive backends:

```scala
import scalatui.core.{InputResult, TUI, TerminalQueryResult}
import scalatui.terminal.{TerminalInput, TerminalKey}
import scalatui.terminal.jvm.SttyTerminal

val tui = TUI(SttyTerminal())
tui.handlesControlC = false
tui.exitsOnEscape = false

val unsubscribe = tui.onTerminalColorSchemeChange { scheme =>
  println(s"terminal scheme changed to ${scheme.value}")
}

var cancelBackground = () => ()
var cancelScheme = () => ()
var removeExitListener = () => ()
def cancelQueries(): Unit =
  cancelBackground()
  cancelScheme()

try
  tui.start()
  val titleApplied = tui.setTerminalTitle("siglyph")
  val progressApplied = tui.setTerminalProgress(active = true)
  cancelBackground = tui.queryTerminalBackgroundColor {
    case TerminalQueryResult.Success(color) => println(s"background: $color")
    case TerminalQueryResult.InvalidResponse => println("invalid background response")
    case TerminalQueryResult.Stopped => println("background query stopped")
    case TerminalQueryResult.Failed(cause) => cause.printStackTrace()
  }
  cancelScheme = tui.queryTerminalColorScheme {
    case TerminalQueryResult.Success(scheme) => println(s"scheme: ${scheme.value}")
    case TerminalQueryResult.InvalidResponse => println("invalid scheme response")
    case TerminalQueryResult.Stopped => println("scheme query stopped")
    case TerminalQueryResult.Failed(cause) => cause.printStackTrace()
  }
  tui.setTerminalColorSchemeNotifications(enabled = true)
  removeExitListener = tui.addInputListener {
    case TerminalInput.Key(TerminalKey.Escape, _) =>
      cancelQueries()
      InputResult.Exit
    case TerminalInput.Key(TerminalKey.Character("c"), modifiers) if modifiers.ctrl =>
      cancelQueries()
      InputResult.Exit
    case _ => InputResult.Ignored
  }
  tui.run()
finally
  cancelQueries()
  removeExitListener()
  unsubscribe()
  tui.stop()
```

Title and progress helpers return `false` when the backend does not support the protocol. Color queries and notifications require a running TUI so the backend can read and deliver terminal replies. Keep the runtime alive, as `tui.run()` does above, until replies arrive or the application exits. Before startup, the example disables built-in Ctrl+C and Escape exits so input cannot stop the runtime while query cancellation is being established. After both cancellation functions are installed, its global listener cancels the subscriptions before returning `InputResult.Exit` for normal runtime shutdown. Applications must route every exit path through cancellation-aware shutdown when active subscriptions are no longer needed.

Each query protocol uses one wire request shared by subscribers; the runtime retains that wire flight until a reply, stop, or failure. Each subscriber retains and owns its idempotent cancellation function and invokes it when the result is no longer wanted. Cancellation invokes no callback and does not remove the runtime-owned wire flight. Callbacks run in subscription and ingress order on the TUI drain and never concurrently with another application callback. Completion may occur before the query method returns. Applications also own timeout scheduling and call cancellation when their deadline expires; core has no timer, executor, `Future`, or effect dependency.

Terminal input and protocol reply batches share a lossless FIFO bounded at 4096 events. Correlation-only raw fragments consume no slot, a recognized protocol completion or notification consumes one slot regardless of subscriber count, and reconstructed ordinary raw events consume one slot each. A backend publisher waits when required capacity is full and wakes when the drain frees capacity or lifecycle stop rejects further input. Resize remains coalesced outside this capacity. Custom `Terminal` implementations must return from `start` without invoking either registered callback on the `start` call stack; callbacks may begin independently on another thread before `start` returns. Output-side methods follow the same callback-separation rule.

## Input and editor integration hooks

Applications can register typed global input listeners with `tui.addInputListener`. The listener receives `TerminalInput` before focused component routing. Return `InputResult.Ignored` to let normal routing continue, `InputResult.Handled(...)` to consume the input, or `InputResult.Exit` to stop through the normal terminal restoration path. The returned function removes the listener.

Built-in stream and interactive backends frame a standalone Escape after 35 ms of input idle time; other incomplete non-paste sequences wait 75 ms after the latest read fragment. A continuation before its cutoff remains in the same Alt/CSI frame, while one arriving after a standalone Escape cutoff begins a new input frame. These are parser deadlines, not hard real-time callback guarantees; stream EOF and zero-length reads still flush immediately, while active bracketed paste does not time out.

Paste and untyped terminal input use bounded byte streams. Paste arrives as `PasteStart`, zero or more `PasteChunk(TerminalInputChunk(...))` events, and `PasteEnd`. An empty bracketed paste is represented by `PasteStart` followed immediately by `PasteEnd`, with zero `PasteChunk` events. Untyped sequences use `RawStart`, `RawChunk`, and `RawEnd`. Each chunk contains 1 through 4096 copied bytes. Text consumers can use `TerminalUtf8Decoder` to decode chunk boundaries incrementally. Editor treats the full paste stream as one edit: newline normalization and marker thresholds span all chunks, and completion creates one undo entry, change callback, autocomplete refresh, and render.

The 4096-byte chunk, parser, and ingress limits bound only transport and runtime transit state. `Input` and other application-owned component values have no core content-size limit. Applications that need content limits must validate or reject content before retaining it. Core does not truncate or drop application-owned content, add a fixed content limit, add limit configuration, or add an overflow callback.

Built-in mutable components commit state and queue callback or runtime effects in order. Detached components use a local FIFO. An uncontended detached call runs effects before returning. Reentrant and concurrent calls queue effects and return after commit. Attached components share one per-lifecycle TUI effect queue with at most 4096 queued batches plus one executing batch. Full or stopping queues reject effectful mutation before state change. The TUI drains accepted batches before cleanup. Callback failures use the active detached outer drain or the attached TUI failure and cleanup path. Direct handoff between active TUI contexts requires an intervening detach.

The editor exposes `insertAtCursor(text)` for application-owned insertion such as selected file paths or templates. It uses the same buffer mutation path as editor input: newline normalization, large-paste markers, undo, change callbacks, active autocomplete refresh, and render requests when attached to a `TUI`.

### Draft handoff (unreleased)

`Editor.submittedText: String` reads logical submission content, resolving compact paste payloads without changing displayed text, cursor, registry, undo/history, autocomplete or callbacks. Validate this value **before** clearing/replacing the editor or saving a queued edit; `editor.text.length` measures marker text, not logical payload length. Reads are serialized with editor state but do not make a separate later application mutation atomic.

`Editor.snapshotDraft(): EditorDraftSnapshot` captures an opaque, detached, reusable draft. `Editor.restoreDraft(snapshot): Unit` restores displayed text, private compact paste payloads/identities and counter, and the exact logical cursor. Snapshots can be restored into another editor; copying `editor.text` cannot transfer registered paste payloads. These APIs are not in published 0.13.0 (nor Sigma's current 0.12 dependency); no publication is implied.

Snapshot opacity prevents callers from fabricating a draft by copying marker strings; it is not a JVM security boundary. The buffer retains legacy text-based marker recognition: literal text identical to an **already registered** marker in that same buffer aliases that payload. New marker allocation avoids IDs appearing in existing literal text or payloads, and expansion is nonrecursive, but this API does not introduce structural span provenance. Use the opaque draft handle rather than inserting copied marker text into an editor that still owns its registry.

Both reads include only the committed buffer: between `PasteStart` and `PasteEnd`, incoming chunks remain pending and are neither flushed nor canceled by reading. Restore discards a pending paste; trailing chunks/end are ignored until a fresh start. Restore clears undo, history browsing (not history entries), yank continuation, jump mode and preferred column, cancels autocomplete without refreshing, and invalidates layout. It preserves callbacks, focus, configuration, kill ring and TUI context, requests render, and emits one `onChange` with displayed text only when displayed or logical submitted content differs. Cursor-only or unchanged restores do not emit `onChange`; restore never submits. External effects run after the state boundary. This is a draft handoff, not a full undo/history or asynchronous editor checkpoint.

Forced autocomplete can opt into `pi-tui`-style single-result application with `EditorOptions(autoApplySingleForcedCompletion = true)`. The default remains explicit selection. Empty results, multiple results, and stale results keep the existing safe behavior.
