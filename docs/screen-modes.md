# Screen modes and scrollback

[Documentation index](README.md)

## Normal screen

`TUI(terminal)` runs in normal-screen mode. Frames use the normal terminal screen and can remain in
shell scrollback after exit. Existing applications can keep constructing `TUI(SttyTerminal())`
without entering alternate screen. Resize redraws clear the viewport, home the cursor, and clear
scrollback by default to avoid stale frame cells.

`TUIOptions(normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback)` is an opt-in
alternative that clears only the viewport on normal-screen resize. It may leave emulator-specific
stale rows, so full clearing remains the default. Instance-scoped, redacted lifecycle/write/resize
diagnostics are also opt-in; see [`docs/runtime-diagnostics.md`](../docs/runtime-diagnostics.md).

The same opt-in policy enables typed append-only output above the retained live frame:

```scala
import scalatui.components.Text
import scalatui.core.{AppendResult, NormalResizeClearPolicy, TUI, TUIOptions}

val tui = TUI(terminal, TUIOptions(
  normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback
))
val status = Text("Ready", paddingX = 0)
tui.addChild(status)
tui.start() // Commits the initial retained frame before append admission.

tui.appendToScrollback(Text("completed output", paddingX = 0)) { result =>
  status.text = result match
    case AppendResult.Published(rows, controls) =>
      s"Published $rows rows and $controls controls"
    case AppendResult.Rejected(reason) =>
      s"Append rejected: $reason"
    case AppendResult.Failed(cause) =>
      s"Append failed: ${cause.getClass.getSimpleName}"
  tui.requestRender()
}
```

`appendToScrollback` accepts one detached, one-shot component only after a normal-screen live frame
has been committed. It validates and encodes typed controls through the TUI-owned output boundary,
redraws the retained frame below the appended rows, and invokes its callback exactly once. At most
64 operations may remain incomplete. Cursor placements and Kitty cleanup are rejected. Kitty image
IDs are remapped so retained cleanup cannot remove historical output. A retained iTerm2 image makes
append incompatible because iTerm2 provides no reliable relocation cleanup; one-shot appended
iTerm2 images remain supported. Real image persistence depends on the terminal emulator.

Applications that retain a bounded semantic transcript can also reconstruct only the durable tail
lost from the active viewport during a normal-screen resize:

```scala
import scalatui.ansi.Ansi
import scalatui.core.{
  NormalResizeClearPolicy,
  NormalResizeRecoveryProvider,
  TUI,
  TUIOptions
}

val semanticTranscript = Vector("old result", "newer result") // Application-owned and bounded.
val recovery = NormalResizeRecoveryProvider { context =>
  val invalidatedEntries = semanticTranscript.reverseIterator
    .foldLeft((Vector.empty[String], 0)) { case ((entries, oldRows), entry) =>
      if oldRows >= context.previousMaxRows then entries -> oldRows
      else
        val entryRows = Ansi.wrapLogicalLinesWithAnsi(entry, context.previousWidth).length
        (entry +: entries) -> (oldRows + entryRows)
    }
    ._1

  invalidatedEntries
    .flatMap(Ansi.wrapLogicalLinesWithAnsi(_, context.width))
    .takeRight(context.maxRows) // Retains oldest-to-newest order within the newest tail.
}

val tui = TUI(terminal, TUIOptions(
  normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback,
  normalResizeRecovery = Some(recovery)
))
```

The provider runs synchronously only for a committed geometry-changing resize, after the live frame
has been rendered and its strict viewport budget is known. `previousWidth`, `previousHeight`, and
`previousMaxRows` describe the old viewport and its maximum durable prefix; `maxRows` is also
bounded by space above the new live frame, preventing viewport growth from replaying older history.
The provider may run again if another resize makes the candidate stale, so it must be fast,
side-effect-light, and retryable. It returns ordinary text lines only: existing SGR/OSC 8
sanitization is preserved, while typed controls, images, cursors, and raw terminal output are
unavailable. A later append is placed in chronological order as `recovered tail -> newly appended
output -> retained live frame`.

Siglyph does not retain the transcript or inspect emulator scrollback. The application must select
only the current-width newest tail that belonged in the invalidated viewport; exact survivor and
deduplication behavior remains terminal-dependent. Configuring recovery with alternate-screen mode
or `ClearScrollback` fails before terminal startup.

## Width-only alternate screen

```scala
import scalatui.core.{TUI, TUIOptions, TUIScreenMode}

val tui = TUI(terminal, TUIOptions(screenMode = TUIScreenMode.Alternate))
```

This legacy path enters the alternate screen on start and exits it during cleanup. It changes the
screen buffer lifecycle only. Rendering remains an unbounded width-only document through
`Component.render(width)`. It does not allocate component heights or provide viewport scrolling.

## Height-aware fullscreen

```scala
import scalatui.components.{Editor, ScrollView, StackEntry, StackEntryOptions, Text, VStack}
import scalatui.core.TUI

val transcript = ScrollView(Text("growing transcript", paddingX = 0), followEnd = true, primary = true)
val editor = Editor()
val root = VStack(Seq(
  StackEntry(transcript, StackEntryOptions(grow = 1, minSize = 1)),
  StackEntry(editor, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(4)))
))
val tui = TUI.fullscreen(terminal, root)
```

`TUI.fullscreen` enters the alternate screen and paints exactly the current terminal height. The
layout root owns stack sizing. A primary `ScrollView` owns viewport offset, follow-end, search,
scrollbars, selection, and navigation fallback. Width-only components remain valid measured leaves.
Selection copy is explicit. Configure `TUIOptions(hostClipboard = Some(...))` and handle
`ClipboardTarget.Host`, `ClipboardTargetSupport`, and `ClipboardCopyResult`. Core provides no
operating-system clipboard implementation and emits no OSC 52. Runtime renderer switching is not
supported. Fullscreen does not add Windows support, Node scheduling, or built-in LaTeX parsing. The
executable JVM and Scala Native example is
[`fullscreenDemo/src/scalatui/demo/FullscreenTranscriptDemo.scala`](../fullscreenDemo/src/scalatui/demo/FullscreenTranscriptDemo.scala).
