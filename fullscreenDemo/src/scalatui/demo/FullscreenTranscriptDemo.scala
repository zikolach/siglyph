package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.components.{
  Editor,
  EditorOptions,
  MouseRegion,
  ScrollView,
  ScrollbarMode,
  StackEntry,
  StackEntryOptions,
  VStack
}
import scalatui.core.{
  ClipboardCopyResult,
  Component,
  ComponentRender,
  HostClipboard,
  MouseEvent,
  MouseHandlerResult,
  TUI,
  TUIOptions
}
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  KeyDescriptor,
  KeyModifiers,
  KeybindingCommand,
  KeybindingManager,
  MouseButton,
  Terminal,
  TerminalKey,
  TerminalMouseTrackingMode,
  TerminalMouseTrackingOptions
}

/**
 * Shared JVM and Scala Native fullscreen transcript example.
 *
 * The example owns one growing transcript, one primary follow-end [[ScrollView]], a multiline
 * editor, and a clickable footer. Fullscreen search uses Ctrl+Shift+F. Dragging primary-button
 * input selects transcript text. Ctrl+Alt+C or a primary click on the footer calls the configured
 * host clipboard callback. Core emits no OSC 52 sequence.
 */
final class FullscreenTranscriptDemo private (
    val tui: TUI,
    transcript: DemoTranscript,
    transcriptView: ScrollView
):
  @volatile private var running = false

  /** Append one application-owned transcript row and invalidate search and selection mapping. */
  def append(value: String): Unit =
    transcript.append(value)
    transcriptView.invalidateSearchDocument()

  /**
   * Run until the TUI exits, then stop the application-owned growth worker and restore the
   * terminal. The worker is joined by its owner. [[TUI.stop]] remains idempotent if [[TUI.run]]
   * already completed cleanup after input, EOF, or a terminal worker failure.
   */
  def run(): Unit =
    running = true
    val growth = Thread(
      () =>
        var index = 1
        while running do
          try Thread.sleep(1000L)
          catch case _: InterruptedException => ()
          if running then
            append(s"background event $index")
            index += 1
      ,
      "siglyph-fullscreen-demo-growth"
    )
    growth.setDaemon(true)
    growth.start()
    try tui.run()
    finally
      running = false
      growth.interrupt()
      if growth ne Thread.currentThread() then growth.join()
      tui.stop()

private final class DemoTranscript extends Component:
  private var rows = Vector("session started", "drag to select transcript text")

  def append(value: String): Unit = synchronized { rows :+= value }

  override def render(width: Int): ComponentRender = synchronized {
    ComponentRender.text(rows.flatMap(row => Ansi.wrapTextWithAnsi(row, math.max(1, width))))
  }

private final class DemoFooter(status: () => String) extends Component:
  override def render(width: Int): ComponentRender = ComponentRender.text(
    Ansi.truncateToWidth(status(), math.max(0, width), "")
  )

object FullscreenTranscriptDemo:
  /**
   * Build the shared example around a JVM or Scala Native terminal backend.
   *
   * The host clipboard callback in this example deliberately stores copied text in application
   * memory. Replace it with platform integration when the host provides one. Returning true means
   * the complete bounded selection was accepted.
   */
  def apply(terminal: Terminal): FullscreenTranscriptDemo =
    val transcript          = DemoTranscript()
    var copied              = "nothing copied"
    var tuiRef              = Option.empty[TUI]
    val clipboard           = new HostClipboard:
      override def copy(value: String): Boolean =
        copied = s"copied ${value.length} characters through host callback"
        tuiRef.foreach(_.requestRender())
        true
    val copyKey             = KeyDescriptor(
      TerminalKey.Character("c"),
      KeyModifiers(ctrl = true, alt = true)
    )
    val keybindings         = KeybindingManager(Map(
      KeybindingCommand.ViewportCopySelection -> Vector(copyKey)
    ))
    var viewRef             = Option.empty[ScrollView]
    lazy val editor: Editor = Editor(options = EditorOptions(onSubmit = value =>
      val trimmed = value.trim
      if trimmed.nonEmpty then
        transcript.append(s"> $trimmed")
        viewRef.foreach(_.invalidateSearchDocument())
      editor.setText("")))
    val view                = ScrollView(
      transcript,
      followEnd = true,
      primary = true,
      scrollbar = ScrollbarMode.Automatic,
      jumpToEndIndicator = Some(() => "Jump to latest")
    )
    viewRef = Some(view)
    val footer              = MouseRegion(
      DemoFooter(() => s"Ctrl+Shift+F search | drag select | Ctrl+Alt+C or click copy | $copied"),
      {
        case click: MouseEvent.Click if click.button === MouseButton.Left =>
          tuiRef.foreach { tui =>
            tui.copySelection() match
              case ClipboardCopyResult.Success(_)     => ()
              case ClipboardCopyResult.Unsupported(_) => copied = "host clipboard unsupported"
              case ClipboardCopyResult.Failure(_, _)  => copied = "copy failed or selection empty"
            tui.requestRender()
          }
          MouseHandlerResult.Handled
        case _                                                            => MouseHandlerResult.Ignored
      }
    )
    val root                = VStack(Seq(
      StackEntry(view, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(editor, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(4))),
      StackEntry(footer, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1)))
    ))
    val tui                 = TUI.fullscreen(
      terminal,
      root,
      TUIOptions(
        mouseTracking = Some(TerminalMouseTrackingOptions(TerminalMouseTrackingMode.Drag)),
        keybindings = keybindings,
        hostClipboard = Some(clipboard)
      )
    )
    tuiRef = Some(tui)
    tui.setFocus(editor)
    new FullscreenTranscriptDemo(tui, transcript, view)
