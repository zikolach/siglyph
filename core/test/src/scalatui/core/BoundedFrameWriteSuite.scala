package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  Terminal,
  TerminalImageProtocol,
  TerminalInput,
  TerminalRenderControlEncoder,
  VirtualTerminal
}

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable.ArrayBuffer

class BoundedFrameWriteSuite extends munit.FunSuite:
  private final class RecordingTerminal extends Terminal:
    val writes                                                                     = ArrayBuffer.empty[String]
    var stopCalled                                                                 = false
    var beforeWrite: String => Unit                                                = _ => ()
    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit = ()
    override def stop(): Unit                                                      = stopCalled = true
    override def write(data: String): Unit                                         =
      beforeWrite(data)
      writes += data
    override def columns: Int                                                      = 80
    override def rows: Int                                                         = 24
    override def moveBy(lines: Int): Unit                                          = ()
    override def hideCursor(): Unit                                                = ()
    override def showCursor(): Unit                                                = ()
    override def clearLine(): Unit                                                 = ()
    override def clearFromCursor(): Unit                                           = ()
    override def clearScreen(): Unit                                               = ()

  private val payload      = Base64ImagePayload.from("A" * 140000).toOption.get
  private val image        = TerminalImageProtocol.encodeKitty(payload, 117, 1, 1)
  private val encodedImage = TerminalRenderControlEncoder.encode(image)

  test(
    "bounded chunks preserve normal and alternate frame bytes, typed control, ANSI, OSC, and cursor"
  ):
    val osc  = "\u001b]8;;https://example.com\u001b\\link\u001b]8;;\u001b\\"
    val line = "\u001b[31mred\u001b[0m " + osc + ("界🙂" * 12000)
    Vector(TUIScreenMode.Normal, TUIScreenMode.Alternate).foreach { mode =>
      val terminal     = new RecordingTerminal
      val counters     = new RuntimeCounters
      val diagnostics  = ArrayBuffer.empty[(TUIDiagnosticWriteKind, Int)]
      val services     = new RuntimeTerminalServices(
        terminal,
        counters,
        (kind, value) =>
          diagnostics += ((kind, value.getBytes(StandardCharsets.UTF_8).length))
      )
      val policy       = new NormalScreenPolicy(
        terminal,
        TUIOptions(screenMode = mode, hardwareCursorPositioning = true),
        counters,
        services,
        (_, _, _, _, _, _) => ()
      )
      policy.start()
      val frame        = policy.prepareFrame(
        ComponentRender(
          Vector(" ", line),
          Vector(TerminalControlPlacement(0, 0, image)),
          Vector(CursorPlacement(0, 3))
        ),
        80
      )
      policy.render(frame, 80, 24, force = true, clear = true, recovery = None)
      val renderWrites =
        terminal.writes.toVector.drop(if mode === TUIScreenMode.Alternate then 1 else 0)
      val clear        =
        if mode === TUIScreenMode.Alternate then TUI.AlternateScreenClear else TUI.NormalScreenClear
      val expected     = TUI.SyncStart + TUI.AutoWrapOff + clear + encodedImage + "\r" +
        frame.lines(0) + "\r\n" + frame.lines(1) + "\u001b[1A\r\u001b[3C" +
        TUI.SyncEnd + TUI.AutoWrapOn
      assert(renderWrites.length > 2)
      assert(renderWrites.forall(_.length <= 65536))
      assert(renderWrites.mkString === expected, s"frame bytes differ in $mode")
      assertEquals(counters.snapshot.terminalWrites, terminal.writes.length.toLong)
      assertEquals(
        diagnostics.collect { case (TUIDiagnosticWriteKind.Render, bytes) => bytes }.sum,
        expected.getBytes(StandardCharsets.UTF_8).length
      )
      assertEquals(diagnostics.count(_._1 === TUIDiagnosticWriteKind.Render), renderWrites.length)
    }

  test("fullscreen bounded chunks preserve exact positioning and protocol framing"):
    val terminal = new RecordingTerminal
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val policy   = new FullscreenViewportPolicy(
      terminal,
      TUIOptions(hardwareCursorPositioning = true),
      counters,
      services,
      (_, _, _, _, _, _) => ()
    )
    val lines    = Vector(" ") ++ Vector.fill(22)("") ++ Vector("tail")
    val frame    = policy.prepareFrame(
      ComponentRender(
        lines,
        Vector(TerminalControlPlacement(0, 0, image)),
        Vector(CursorPlacement(23, 2))
      ),
      80
    )
    policy.render(frame, 80, 24, force = true, clear = true, recovery = None)
    val expected =
      new java.lang.StringBuilder(TUI.SyncStart + TUI.AutoWrapOff + TUI.AlternateScreenClear)
    frame.lines.indices.foreach { row =>
      expected.append(s"\u001b[${row + 1};1H\u001b[2K")
      if row === 0 then expected.append("\u001b[1;1H").append(encodedImage)
      expected.append(s"\u001b[${row + 1};1H").append(frame.lines(row))
    }
    expected.append("\u001b[24;3H").append(TUI.SyncEnd).append(TUI.AutoWrapOn)
    assert(terminal.writes.length > 2)
    assert(terminal.writes.forall(_.length <= 65536))
    assert(terminal.writes.mkString === expected.toString, "fullscreen frame bytes differ")

  test("chunk boundaries never separate a UTF-16 surrogate pair or corrupt UTF-8"):
    val frame = new FrameOutput
    frame.append("x" * 65535)
    frame.append("🙂\u001b[31m界\u001b]8;;https://example.com\u001b\\text\u001b]8;;\u001b\\")
    val parts = frame.chunks
    val whole = parts.mkString
    assert(parts.length > 1)
    assert(parts.forall(_.length <= 65536))
    assert(parts.forall(part => !part.endsWith("\ud83d") && !part.startsWith("\ude42")))
    assertEquals(
      parts.flatMap(_.getBytes(StandardCharsets.UTF_8)).toArray.toVector,
      whole.getBytes(StandardCharsets.UTF_8).toVector
    )

  test("a concurrent control write cannot interleave between frame chunks"):
    val terminal = new RecordingTerminal
    val entered  = new CountDownLatch(1)
    val release  = new CountDownLatch(1)
    terminal.beforeWrite = data =>
      if data.startsWith(TUI.SyncStart) then
        entered.countDown()
        if !release.await(30, TimeUnit.SECONDS) then throw new IOException("release timeout")
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val frame    = new FrameOutput
    frame.append(TUI.SyncStart).append("x" * 150000).append(TUI.SyncEnd)
    val chunks   = frame.chunks
    val producer = new Thread(() => services.writeRenderChunks(chunks))
    val other    = new Thread(() => services.writeData("OTHER", TUIDiagnosticWriteKind.Control))
    producer.start()
    assert(entered.await(30, TimeUnit.SECONDS))
    other.start()
    release.countDown()
    producer.join(30000)
    other.join(30000)
    assert(!producer.isAlive && !other.isAlive)
    assert(terminal.writes.toVector === (chunks :+ "OTHER"), "control write interleaved with frame")

  test("second-chunk failure attempts protocol and sync cleanup and preserves original failure"):
    val terminal    = new RecordingTerminal
    val failure     = new IOException("second chunk")
    var attempts    = 0
    terminal.beforeWrite = _ =>
      attempts += 1
      if attempts === 2 then throw failure
    val counters    = new RuntimeCounters
    val diagnostics = ArrayBuffer.empty[(TUIDiagnosticWriteKind, String)]
    val services    = new RuntimeTerminalServices(
      terminal,
      counters,
      (kind, value) => diagnostics += ((kind, value))
    )
    val frame       = new FrameOutput
    frame.append(
      TUI.SyncStart
    ).append(TUI.AutoWrapOff).append(encodedImage).append(TUI.SyncEnd).append(TUI.AutoWrapOn)
    val thrown      = intercept[IOException](services.writeRenderChunks(frame.chunks))
    assert(thrown eq failure)
    assertEquals(attempts, 3)
    assertEquals(terminal.writes.length, 2)
    assert(terminal.writes.head.startsWith(TUI.SyncStart + TUI.AutoWrapOff))
    assertEquals(terminal.writes.last, "\u001b\\" + TUI.SyncEnd + TUI.AutoWrapOn)
    assertEquals(counters.snapshot.terminalWrites, 3L)
    assertEquals(
      diagnostics.map(_._1).toVector,
      Vector(TUIDiagnosticWriteKind.Render, TUIDiagnosticWriteKind.Cleanup)
    )

  test("second-chunk failure in TUI propagates and still performs lifecycle cleanup"):
    val terminal    = new RecordingTerminal
    val failure     = new IOException("broken sink")
    var frameWrites = 0
    terminal.beforeWrite = data =>
      if data.startsWith(TUI.SyncStart) || frameWrites > 0 then
        frameWrites += 1
        if frameWrites === 2 then throw failure
    val component   = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, image)),
        Vector.empty
      )
    val tui         = TUI(terminal)
    tui.addChild(component)
    val thrown      = intercept[IOException](tui.start())
    assert(thrown eq failure)
    assert(terminal.stopCalled)
    assert(terminal.writes.exists(_.contains(TUI.SyncEnd)))

  test("virtual terminal models a control spanning writes without painting its payload"):
    val terminal  = VirtualTerminal(80, 4)
    val component = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector(" ", "below"),
        Vector(TerminalControlPlacement(0, 0, image)),
        Vector.empty
      )
    val tui       = TUI(terminal)
    tui.addChild(component)
    tui.start()
    assert(terminal.writes.length > 2)
    assert(!terminal.screenLines.mkString.contains("AAAA"))
    assert(terminal.screenLines.contains("below"))
    tui.stop()

  test("large iTerm2 OSC keeps filename and BEL framing across chunks"):
    val iterm    = TerminalImageProtocol.encodeITerm2(payload, Some("café.png"), 1, 1)
    val encoded  = TerminalRenderControlEncoder.encode(iterm)
    val terminal = new RecordingTerminal
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val policy   = new NormalScreenPolicy(
      terminal,
      TUIOptions(),
      counters,
      services,
      (_, _, _, _, _, _) => ()
    )
    val frame    = policy.prepareFrame(
      ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, iterm)),
        Vector.empty
      ),
      80
    )
    policy.render(frame, 80, 24, force = true, clear = false, recovery = None)
    val expected = TUI.SyncStart + TUI.AutoWrapOff + encoded + "\r" + frame.lines.head +
      TUI.SyncEnd + TUI.AutoWrapOn
    assert(terminal.writes.length > 2)
    assert(terminal.writes.forall(_.length <= 65536))
    assert(terminal.writes.mkString === expected, "iTerm2 frame bytes differ")
    assert(encoded.contains("name="))
    assert(encoded.endsWith("\u0007"))

  test("failed best-effort repair is suppressed beneath the original write error"):
    val terminal = new RecordingTerminal
    val primary  = new IOException("failed second chunk")
    val repair   = new IOException("sink remains broken")
    var attempts = 0
    terminal.beforeWrite = _ =>
      attempts += 1
      if attempts === 2 then throw primary
      if attempts === 3 then throw repair
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val frame    = new FrameOutput
    frame.append(TUI.SyncStart).append("x" * 140000).append(TUI.SyncEnd)
    val thrown   = intercept[IOException](services.writeRenderChunks(frame.chunks))
    assert(thrown eq primary)
    assertEquals(thrown.getSuppressed.toVector, Vector(repair))
    assertEquals(terminal.writes.length, 1)
    assertEquals(counters.snapshot.terminalWrites, 3L)

  test("same sink exception on chunk and repair preserves the original error"):
    val terminal = new RecordingTerminal
    val failure  = new IOException("sink remains broken")
    var attempts = 0
    terminal.beforeWrite = _ =>
      attempts += 1
      if attempts >= 2 then throw failure
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val frame    = new FrameOutput
    frame.append(TUI.SyncStart).append("x" * 140000).append(TUI.SyncEnd)
    val thrown   = intercept[IOException](services.writeRenderChunks(frame.chunks))
    assert(thrown eq failure)
    assertEquals(thrown.getSuppressed.toVector, Vector.empty)
    assertEquals(counters.snapshot.terminalWrites, 3L)

  test("fullscreen second-chunk failure exits alternate screen through lifecycle cleanup"):
    val terminal    = new RecordingTerminal
    val failure     = new IOException("fullscreen second chunk")
    var frameWrites = 0
    terminal.beforeWrite = data =>
      if data.startsWith(TUI.SyncStart) || frameWrites > 0 then
        frameWrites += 1
        if frameWrites === 2 then throw failure
    val component   = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, image)),
        Vector.empty
      )
    val tui         = TUI.fullscreen(terminal, component)
    val thrown      = intercept[IOException](tui.start())
    assert(thrown eq failure)
    assert(terminal.stopCalled)
    assert(terminal.writes.contains(TUI.AlternateScreenEnter))
    assert(terminal.writes.contains(TUI.AlternateScreenExit))
    assert(terminal.writes.exists(_.contains(TUI.SyncEnd)))

  test("append-only publication keeps one synchronized multi-chunk frame"):
    val terminal = new RecordingTerminal
    val counters = new RuntimeCounters
    val services = new RuntimeTerminalServices(terminal, counters, (_, _) => ())
    val policy   = new NormalScreenPolicy(
      terminal,
      TUIOptions(),
      counters,
      services,
      (_, _, _, _, _, _) => ()
    )
    val appended = policy.prepareFrame(
      ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, image)),
        Vector.empty
      ),
      80
    )
    val retained = policy.prepareFrame(ComponentRender.text("live"), 80)
    policy.publishAppend(appended, retained, terminal.rows)
    val expected = TUI.SyncStart + TUI.AutoWrapOff + "\r\u001b[J" + encodedImage + "\r" +
      appended.lines.head + "\r\n" + retained.lines.head + TUI.SyncEnd + TUI.AutoWrapOn
    assert(terminal.writes.length > 2)
    assert(terminal.writes.forall(_.length <= 65536))
    assert(terminal.writes.mkString === expected, "append frame bytes differ")
