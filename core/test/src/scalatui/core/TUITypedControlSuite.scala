package scalatui.core

import scalatui.syntax.Equality.*

import scalatui.terminal.{
  Base64ImagePayload,
  Terminal,
  TerminalImageProtocol,
  TerminalRenderControl,
  TerminalRenderControlEncoder,
  VirtualTerminal
}

class TUITypedControlSuite extends munit.FunSuite:
  private final class MutableTypedFrame(var frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  private final class RenderFailingTerminal extends Terminal:
    val writes                           = scala.collection.mutable.ArrayBuffer.empty[String]
    var showCursorCalled                 = false
    var stopCalled                       = false
    private var failedSynchronizedRender = false

    override def start(
        onInput: scalatui.terminal.TerminalInput => Unit,
        onResize: () => Unit
    ): Unit = ()
    override def stop(): Unit = stopCalled = true

    override def write(data: String): Unit =
      if data.contains(TUI.SyncStart) && !failedSynchronizedRender then
        failedSynchronizedRender = true
        writes += data
        throw RuntimeException("control render failed")
      writes += data

    override def columns: Int = 10
    override def rows: Int    = 5

    override def moveBy(lines: Int): Unit = ()
    override def hideCursor(): Unit       = ()
    override def showCursor(): Unit       = showCursorCalled = true
    override def clearLine(): Unit        = ()
    override def clearFromCursor(): Unit  = ()
    override def clearScreen(): Unit      = ()

  test("full render emits a typed control at its anchor and preserves reserved rows"):
    val control   = kittyControl("AAAA", imageId = 11, width = 2, rows = 2)
    val component = MutableTypedFrame(ComponentRender(
      Vector("", "", "below"),
      Vector(TerminalControlPlacement(row = 0, column = 1, control))
    ))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)

    tui.start()

    val output  = terminal.output
    val encoded = encode(control)
    assert(output.indexOf(encoded) > output.indexOf(TUI.SyncStart), output)
    assert(output.indexOf(encoded) < output.indexOf(TUI.SyncEnd), output)
    assert(!output.contains("\u001b_Ga=d,d=I"), output)
    assert(
      output.contains(s"\u001b[1C$encoded\r${TUI.LineReset}\r\n${TUI.LineReset}\r\nbelow"),
      output
    )

  test("unchanged typed control does not rewrite the frame"):
    val control   = kittyControl("AAAA", imageId = 12)
    val component = MutableTypedFrame(frameWithControl(control, row = 0))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    tui.requestRender()
    tui.flushRender()

    assertEquals(terminal.output, "")

  test("partial text update emits only controls anchored in the rewritten range"):
    val control   = kittyControl("AAAA", imageId = 13, rows = 2)
    val component = MutableTypedFrame(ComponentRender(
      Vector("", "", "before"),
      Vector(TerminalControlPlacement(0, 0, control))
    ))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = component.frame.copy(lines = Vector("", "", "after"))
    tui.requestRender()
    tui.flushRender()

    assert(terminal.output.contains("after" + TUI.LineReset), terminal.output)
    assert(!terminal.output.contains(encode(control)), terminal.output)
    assert(!terminal.output.contains(cleanupEncoding(control)), terminal.output)

  test("partial redraw cleans an existing Kitty ID once before retransmission"):
    val control   = kittyControl("AAAA", imageId = 33)
    val component = MutableTypedFrame(frameWithControl(
      control,
      row = 1,
      lines = Vector("before", "")
    ))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = component.frame.copy(lines = Vector("after", ""))
    tui.requestRender()
    tui.flushRender()

    val cleanup = cleanupEncoding(control)
    assertEquals(occurrences(terminal.output, cleanup), 1)
    assertEquals(occurrences(terminal.output, encode(control)), 1)
    assert(terminal.output.indexOf(cleanup) < terminal.output.indexOf(encode(control)))

  test("control addition selects its anchor row for partial output"):
    val control   = kittyControl("AAAA", imageId = 14)
    val component = MutableTypedFrame(ComponentRender.text(Vector("first", "")))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = frameWithControl(control, row = 1, lines = Vector("first", ""))
    tui.requestRender()
    tui.flushRender()

    assert(terminal.output.contains(encode(control)), terminal.output)
    assert(!terminal.output.contains(cleanupEncoding(control)), terminal.output)
    assert(!terminal.output.contains("first" + TUI.LineReset), terminal.output)

  test("moving a Kitty control cleans the old placement and emits the new placement"):
    val control   = kittyControl("AAAA", imageId = 15)
    val component = MutableTypedFrame(frameWithControl(control, row = 0))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = frameWithControl(control, row = 1)
    tui.requestRender()
    tui.flushRender()

    val cleanup = cleanupEncoding(control)
    assertEquals(occurrences(terminal.output, cleanup), 1)
    assert(
      terminal.output.indexOf(cleanup) < terminal.output.indexOf(encode(control)),
      terminal.output
    )

  test("changing a Kitty semantic field cleans the old image before replacement"):
    val oldControl = kittyControl("AAAA", imageId = 16)
    val newControl = kittyControl("TQ==", imageId = 16)
    val component  = MutableTypedFrame(frameWithControl(oldControl, row = 1))
    val terminal   = VirtualTerminal(10, 5)
    val tui        = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = frameWithControl(newControl, row = 1)
    tui.requestRender()
    tui.flushRender()

    assertEquals(occurrences(terminal.output, cleanupEncoding(oldControl)), 1)
    assert(terminal.output.contains(encode(newControl)), terminal.output)
    assert(!terminal.output.contains(encode(oldControl)), terminal.output)

  test("Kitty replacement cleanup does not target an unrelated unique image ID"):
    val oldControl     = kittyControl("AAAA", imageId = 30)
    val replacement    = kittyControl("TQ==", imageId = 30)
    val unrelated      = kittyControl("TWE=", imageId = 31)
    val oldPlacement   = TerminalControlPlacement(1, 0, oldControl)
    val newPlacement   = TerminalControlPlacement(1, 0, replacement)
    val otherPlacement = TerminalControlPlacement(0, 0, unrelated)
    val component      = MutableTypedFrame(ComponentRender(
      Vector("", ""),
      Vector(otherPlacement, oldPlacement)
    ))
    val terminal       = VirtualTerminal(10, 5)
    val tui            = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = ComponentRender(Vector("", ""), Vector(otherPlacement, newPlacement))
    tui.requestRender()
    tui.flushRender()

    assertEquals(occurrences(terminal.output, cleanupEncoding(oldControl)), 1)
    assert(!terminal.output.contains(cleanupEncoding(unrelated)), terminal.output)
    assert(
      terminal.output.indexOf(cleanupEncoding(oldControl)) <
        terminal.output.indexOf(encode(replacement)),
      terminal.output
    )

  test("removing a Kitty control emits typed cleanup without re-emitting the old image"):
    val control   = kittyControl("AAAA", imageId = 17)
    val component = MutableTypedFrame(frameWithControl(control, row = 1))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = ComponentRender.text(Vector("", ""))
    tui.requestRender()
    tui.flushRender()

    assertEquals(occurrences(terminal.output, cleanupEncoding(control)), 1)
    assert(!terminal.output.contains(encode(control)), terminal.output)

  test("duplicate active Kitty image IDs fail before synchronized frame output"):
    val first     = kittyControl("AAAA", imageId = 27)
    val duplicate = kittyControl("TQ==", imageId = 27)
    val component = MutableTypedFrame(ComponentRender(
      Vector("", ""),
      Vector(TerminalControlPlacement(0, 0, first), TerminalControlPlacement(1, 0, duplicate))
    ))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)

    intercept[IllegalArgumentException](tui.start())

    assert(!terminal.output.contains(TUI.SyncStart), terminal.output)
    assert(!terminal.output.contains(encode(first)), terminal.output)
    assert(!terminal.output.contains(encode(duplicate)), terminal.output)
    assert(!terminal.isRunning)

  test("pure reorder cleans rewritten existing IDs once before all retransmissions"):
    val unaffected = kittyControl("TWE=", imageId = 27)
    val first      = kittyControl("AAAA", imageId = 28)
    val second     = kittyControl("TQ==", imageId = 29)
    val component  = MutableTypedFrame(ComponentRender(
      Vector("", "", ""),
      Vector(
        TerminalControlPlacement(0, 0, unaffected),
        TerminalControlPlacement(1, 0, first),
        TerminalControlPlacement(1, 0, second)
      )
    ))
    val terminal   = VirtualTerminal(10, 5)
    val tui        = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = component.frame.copy(controls =
      Vector(
        component.frame.controls(0),
        component.frame.controls(2),
        component.frame.controls(1)
      )
    )
    tui.requestRender()
    tui.flushRender()

    val expected =
      TUI.SyncStart + TUI.AutoWrapOff + "\u001b[1A\r\u001b[J" +
        cleanupEncoding(first) + "\r" + cleanupEncoding(second) + "\r" +
        encode(second) + "\r" + encode(first) + "\r" +
        TUI.LineReset + "\r\n" + TUI.LineReset + TUI.SyncEnd + TUI.AutoWrapOn
    assertEquals(terminal.output, expected)
    assert(!terminal.output.contains(encode(unaffected)), terminal.output)
    assert(!terminal.output.contains(cleanupEncoding(unaffected)), terminal.output)

  test("forced full redraw cleans an existing Kitty ID before retransmission"):
    val control   = kittyControl("AAAA", imageId = 34)
    val component = MutableTypedFrame(frameWithControl(control, row = 0))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    tui.requestRender(force = true)
    tui.flushRender()

    val cleanup = cleanupEncoding(control)
    assertEquals(occurrences(terminal.output, cleanup), 1)
    assertEquals(occurrences(terminal.output, encode(control)), 1)
    assert(terminal.output.indexOf(cleanup) < terminal.output.indexOf(encode(control)))

  test("resize redraw emits all current controls"):
    val control   = kittyControl("AAAA", imageId = 18)
    val component = MutableTypedFrame(frameWithControl(control, row = 0))
    val terminal  = VirtualTerminal(10, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    terminal.resize(12, 5)

    assert(terminal.output.contains(TUI.NormalScreenClear), terminal.output)
    assertEquals(occurrences(terminal.output, cleanupEncoding(control)), 1)
    assertEquals(occurrences(terminal.output, encode(control)), 1)
    assert(
      terminal.output.indexOf(cleanupEncoding(control)) < terminal.output.indexOf(encode(control)),
      terminal.output
    )

  test("overlay controls relocate and higher overlay rectangles suppress lower controls"):
    val baseControl  = kittyControl("AAAA", imageId = 19, width = 3)
    val lowerControl = kittyControl("TQ==", imageId = 20, width = 2)
    val upperControl = kittyControl("TWE=", imageId = 21)
    val terminal     = VirtualTerminal(10, 5)
    val tui          = TUI(terminal)
    tui.addChild(MutableTypedFrame(ComponentRender(
      Vector("base"),
      Vector(TerminalControlPlacement(0, 0, baseControl))
    )))
    tui.showOverlay(
      MutableTypedFrame(ComponentRender(
        Vector("  "),
        Vector(TerminalControlPlacement(0, 0, lowerControl))
      )),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(2)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(1)),
        focusCapturing = false
      )
    )
    tui.showOverlay(
      MutableTypedFrame(ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, upperControl))
      )),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(1)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(2)),
        focusCapturing = false
      )
    )

    tui.start()

    val output = terminal.output
    assert(!output.contains(encode(baseControl)), output)
    assert(!output.contains(encode(lowerControl)), output)
    assert(output.contains(s"\u001b[2C${encode(upperControl)}\r"), output)

  test("overlay controls entirely outside retained height are clipped"):
    val control  = kittyControl("AAAA", imageId = 22)
    val overlay  = MutableTypedFrame(ComponentRender(
      Vector("one", "two", "three"),
      Vector(TerminalControlPlacement(2, 0, control))
    ))
    val terminal = VirtualTerminal(10, 5)
    val tui      = TUI(terminal)
    tui.showOverlay(
      overlay,
      OverlayOptions(
        width = Some(OverlaySize.Absolute(5)),
        maxHeight = Some(OverlaySize.Absolute(2)),
        row = Some(OverlaySize.Absolute(0)),
        col = Some(OverlaySize.Absolute(0)),
        focusCapturing = false
      )
    )

    tui.start()

    assert(!terminal.output.contains(encode(control)), terminal.output)
    assert(terminal.output.contains("two"))
    assert(!terminal.output.contains("three"))

  test("partially clipped overlay failure retains bounded redacted diagnostics"):
    val sensitivePayload = "QUJD".repeat(2048)
    val sensitiveName    = "secret-overlay-filename-".repeat(512)
    val control          = TerminalImageProtocol.encodeITerm2(
      Base64ImagePayload.from(sensitivePayload).toOption.get,
      Some(sensitiveName),
      widthCells = 1,
      heightCells = 2
    )
    val terminal         = VirtualTerminal(10, 5)
    val tui              = TUI(terminal)
    tui.showOverlay(
      MutableTypedFrame(ComponentRender(
        Vector("one", "two", "three"),
        Vector(TerminalControlPlacement(1, 0, control))
      )),
      OverlayOptions(
        width = Some(OverlaySize.Absolute(5)),
        maxHeight = Some(OverlaySize.Absolute(2)),
        focusCapturing = false
      )
    )

    val failure = intercept[IllegalArgumentException](tui.start())

    assert(!terminal.output.contains(TUI.SyncStart), terminal.output)
    assert(!terminal.output.contains(encode(control)), terminal.output)
    assert(!terminal.isRunning)
    assert(failure.getMessage.length < 512, failure.getMessage.length.toString)
    assert(!failure.toString.contains("QUJDQUJD"), failure.toString)
    assert(!failure.toString.contains("secret-overlay-filename-"), failure.toString)

  test("invalid final control placement fails before synchronized frame output"):
    val control  = kittyControl("AAAA", imageId = 24, width = 2)
    val terminal = VirtualTerminal(1, 5)
    val tui      = TUI(terminal)
    tui.addChild(MutableTypedFrame(ComponentRender(
      Vector(""),
      Vector(TerminalControlPlacement(0, 0, control))
    )))

    intercept[IllegalArgumentException](tui.start())

    assert(!terminal.output.contains(TUI.SyncStart), terminal.output)
    assert(!terminal.output.contains(encode(control)), terminal.output)
    assert(!terminal.isRunning)

  test("control render write failure restores autowrap cursor and terminal state"):
    val control  = kittyControl("AAAA", imageId = 25)
    val terminal = RenderFailingTerminal()
    val tui      = TUI(terminal)
    tui.addChild(MutableTypedFrame(frameWithControl(control, row = 0)))

    val failure = intercept[RuntimeException](tui.start())

    assertEquals(failure.getMessage, "control render failed")
    assert(terminal.writes.exists(_.contains(encode(control))), terminal.writes.toVector)
    assert(terminal.writes.contains(TUI.AutoWrapOn), terminal.writes.toVector)
    assert(terminal.showCursorCalled)
    assert(terminal.stopCalled)

  test("control output restores logical position before hardware cursor placement"):
    val control  = kittyControl("AAAA", imageId = 26, rows = 2)
    val terminal = VirtualTerminal(10, 5)
    val tui      = TUI(terminal, TUIOptions(hardwareCursorPositioning = true))
    tui.addChild(MutableTypedFrame(ComponentRender(
      Vector("", "", s"ab${CursorMarker.Sequence}c"),
      Vector(TerminalControlPlacement(0, 0, control))
    )))

    tui.start()

    assert(terminal.output.contains(s"${encode(control)}\r${TUI.LineReset}\r\n"), terminal.output)
    assert(terminal.output.contains(s"\r\u001b[2C${TUI.SyncEnd}"), terminal.output)

  test("image-like ordinary strings receive no control geometry or Kitty cleanup"):
    val kittyText = "\u001b_Ga=T,f=100,i=99,c=4,r=3,C=1;AAAA\u001b\\"
    val iTermText = "\u001b]1337;File=inline=1;width=4;height=3:AAAA\u0007"
    val component = MutableTypedFrame(ComponentRender.text(Vector(kittyText, iTermText, "after")))
    val terminal  = VirtualTerminal(20, 5)
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()

    component.frame = ComponentRender.text(Vector("plain", iTermText, "after"))
    tui.requestRender()
    tui.flushRender()

    assert(terminal.output.contains("plain" + TUI.LineReset), terminal.output)
    assert(terminal.output.contains(iTermText + TUI.LineReset), terminal.output)
    assert(!terminal.output.contains("\u001b_Ga=d,"), terminal.output)

  private def frameWithControl(
      control: TerminalRenderControl,
      row: Int,
      lines: Vector[String] = Vector("", "")
  ): ComponentRender =
    ComponentRender(lines, Vector(TerminalControlPlacement(row, 0, control)))

  private def kittyControl(
      payload: String,
      imageId: Int,
      width: Int = 1,
      rows: Int = 1
  ): TerminalRenderControl =
    TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from(payload).toOption.get,
      imageId,
      width,
      rows
    )

  private def encode(control: TerminalRenderControl): String =
    TerminalRenderControlEncoder.encode(control)

  private def cleanupEncoding(control: TerminalRenderControl): String =
    encode(TerminalRenderControl.cleanupForReplacement(control).get)

  private def occurrences(value: String, substring: String): Int =
    value.sliding(substring.length).count(_ === substring)
