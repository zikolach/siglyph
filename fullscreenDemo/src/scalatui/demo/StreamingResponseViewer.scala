package scalatui.demo

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}
import scalatui.components.{Editor, ScrollView, StackEntry, StackEntryOptions, Text, VStack}
import scalatui.core.{ApplicationScope, ScopedTask, TUI, TUIOptions}
import scalatui.markdown.StreamingMarkdown
import scalatui.scheduling.{ExecutorTaskScheduler, TaskScheduler}
import scalatui.terminal.{Terminal, TerminalMouseTrackingMode, TerminalMouseTrackingOptions}

/** Application-owned local response simulator; no provider, transport or conversation service. */
final class StreamingResponseViewer private (
    val tui: TUI,
    val response: StreamingMarkdown,
    val view: ScrollView,
    val editor: Editor
):
  private var timer = Option.empty[ScopedTask]

  /** Bind the composer to a session-owned timer; executor ownership remains with the caller. */
  def attach(scope: ApplicationScope, scheduler: TaskScheduler): Unit =
    editor.onSubmit = prompt =>
      timer.foreach(_.cancel())
      response.reset()
      view.clearSelection()
      editor.addToHistory(prompt)
      editor.setText("")

      val source = StreamingResponseSamples.response(prompt)
      val chunks = source.grouped(StreamingResponseSamples.ChunkUnits)
      view.invalidateSearchDocument()
      timer = Some(scope.scheduleEvery(scheduler, 0L, StreamingResponseSamples.IntervalMillis) {
        task =>
          if chunks.hasNext then append(chunks.next())
          else
            update { response.finish() }
            task.cancel()
            timer = None
      })

  /** Append without jumping the viewport; reject capacity overflow as a whole chunk. */
  def append(chunk: String): Boolean =
    var accepted = false
    update { accepted = response.append(chunk) }
    accepted

  private def update(change: => Unit): Unit =
    change
    // ScrollView validates the retained selection prefix against the refreshed complete mapping.
    // Source mutation already invalidated the document output; keep stable block/highlight reuse.
    view.invalidateSearchDocument(invalidateChild = false)

  /** Run the interactive viewer, cancelling scoped work before shutting down the owned executor. */
  def run(): Unit =
    val executor  = new ScheduledThreadPoolExecutor(1)
    val scheduler = ExecutorTaskScheduler(executor, _ => tui.requestExit())
    try tui.runWithSetup { running =>
        val scope = running.openApplicationScope()
        attach(scope, scheduler)
      }
    finally
      try tui.stop()
      finally
        try scheduler.close()
        finally
          executor.shutdownNow()
          if !executor.awaitTermination(5L, TimeUnit.SECONDS) then
            throw IllegalStateException("streaming viewer executor did not terminate")
    scheduler.lastFailure.foreach(throw _)

object StreamingResponseViewer:
  /** Build around either platform backend; the same child/view survive all response chunks. */
  def apply(terminal: Terminal): StreamingResponseViewer =
    val response = StreamingMarkdown(maxSourceUnits = 64 * 1024)
    val view     = ScrollView(
      response,
      followEnd = true,
      primary = true,
      jumpToEndIndicator = Some(() => "Jump to latest")
    )
    val editor   = Editor()
    val footer   = Text(
      "Enter: local response | wheel: scroll | drag: select | Ctrl+C: exit",
      paddingX = 0,
      paddingY = 0
    )
    val root     = VStack(Seq(
      StackEntry(view, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(editor, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(4))),
      StackEntry(footer, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1)))
    ))
    val tui      = TUI.fullscreen(
      terminal,
      root,
      TUIOptions(
        mouseTracking = Some(TerminalMouseTrackingOptions(TerminalMouseTrackingMode.Drag))
      )
    )
    tui.setFocus(editor)
    new StreamingResponseViewer(tui, response, view, editor)
