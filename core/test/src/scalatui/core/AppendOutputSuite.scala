package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Base64ImagePayload,
  ImageCellDimensions,
  ImageProtocol,
  TerminalCapabilities,
  MouseAction,
  MouseButton,
  MouseInputContext,
  TerminalImageProtocol,
  Terminal,
  TerminalInput,
  TerminalRenderControlDetails,
  VirtualTerminal
}

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class AppendOutputSuite extends munit.FunSuite:
  private final class Line(value: String) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(value)

  private final class Mutable(var frame: ComponentRender) extends Component:
    override def render(width: Int): ComponentRender = frame

  private final class HookTerminal(initialColumns: Int = 20, initialRows: Int = 8) extends Terminal:
    val delegate                                  = VirtualTerminal(initialColumns, initialRows)
    var beforeStop: () => Unit                    = () => ()
    var writeFailure: String => Option[Throwable] = _ => None

    override def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit =
      delegate.start(onInput, onResize)
    override def stop(): Unit                                                      =
      beforeStop()
      delegate.stop()
    override def write(data: String): Unit                                         = writeFailure(data).fold(delegate.write(data))(throw _)
    override def columns: Int                                                      = delegate.columns
    override def rows: Int                                                         = delegate.rows
    override def moveBy(lines: Int): Unit                                          = delegate.moveBy(lines)
    override def hideCursor(): Unit                                                = delegate.hideCursor()
    override def showCursor(): Unit                                                = delegate.showCursor()
    override def clearLine(): Unit                                                 = delegate.clearLine()
    override def clearFromCursor(): Unit                                           = delegate.clearFromCursor()
    override def clearScreen(): Unit                                               = delegate.clearScreen()

  private def running(
      terminal: VirtualTerminal = VirtualTerminal(20, 8),
      component: Component = Line("live"),
      options: TUIOptions = TUIOptions(
        normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback
      )
  ): (TUI, VirtualTerminal) =
    val tui = TUI(terminal, options)
    tui.addChild(component)
    tui.start()
    terminal.clearWrites()
    tui -> terminal

  private def payload: Base64ImagePayload = Base64ImagePayload.from("YQ==").toOption.get

  test("append publishes text above one retained frame and completes before return") {
    val (tui, terminal) = running()
    var result          = Option.empty[AppendResult]

    tui.appendToScrollback(Line("history"), value => result = Some(value))

    assertEquals(result, Some(AppendResult.Published(1, 0)))
    assert(terminal.output.contains("history"))
    assert(terminal.output.contains("live"))
    assertEquals(terminal.screenLines.filter(_.nonEmpty).takeRight(2), Vector("history", "live"))
    tui.stop()
  }

  test("empty append publishes no bytes") {
    val (tui, terminal) = running()
    var result          = Option.empty[AppendResult]

    tui.appendToScrollback(Line(""), value => result = Some(value))

    assertEquals(result, Some(AppendResult.Published(1, 0)))
    assert(terminal.output.nonEmpty)
    terminal.clearWrites()
    tui.appendToScrollback(
      new Component:
        override def render(width: Int): ComponentRender = ComponentRender.empty
      ,
      value => result = Some(value)
    )
    assertEquals(result, Some(AppendResult.Published(0, 0)))
    assertEquals(terminal.output, "")
    tui.stop()
  }

  test("append admission rejects incompatible lifecycle and screen policies") {
    val stopped       = TUI(VirtualTerminal())
    var stoppedResult = Option.empty[AppendResult]
    stopped.appendToScrollback(Line("x"), value => stoppedResult = Some(value))
    assertEquals(
      stoppedResult,
      Some(AppendResult.Rejected(AppendRejection.LifecycleUnavailable(
        TUIDiagnosticLifecycleState.Stopped
      )))
    )

    val clearTerminal = VirtualTerminal()
    val clearTui      = TUI(clearTerminal)
    clearTui.addChild(Line("live"))
    clearTui.start()
    clearTerminal.clearWrites()
    var clearResult   = Option.empty[AppendResult]
    clearTui.appendToScrollback(Line("x"), value => clearResult = Some(value))
    assertEquals(
      clearResult,
      Some(AppendResult.Rejected(AppendRejection.ScrollbackClearingResizePolicy))
    )
    assertEquals(clearTerminal.output, "")
    clearTui.stop()

    val alternateOptions      = TUIOptions(
      screenMode = TUIScreenMode.Alternate,
      normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback
    )
    val (alternate, terminal) = running(options = alternateOptions)
    terminal.clearWrites()
    var alternateResult       = Option.empty[AppendResult]
    alternate.appendToScrollback(Line("x"), value => alternateResult = Some(value))
    assertEquals(alternateResult, Some(AppendResult.Rejected(AppendRejection.AlternateScreen)))
    assertEquals(terminal.output, "")
    alternate.stop()
  }

  test("attached component is rejected without output") {
    val attached        = Line("live")
    val (tui, terminal) = running(component = attached)
    var result          = Option.empty[AppendResult]
    tui.appendToScrollback(attached, value => result = Some(value))
    assertEquals(result, Some(AppendResult.Rejected(AppendRejection.AttachedComponent)))
    assertEquals(terminal.output, "")
    tui.stop()
  }

  test("restricted context latches caught forbidden operations and revokes retained reference") {
    final class ContextProbe extends Component, ContextualComponent:
      var context                                                = Option.empty[TUIContext]
      var retained                                               = Option.empty[TUIContext]
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        context = value
        retained = retained.orElse(value)
      override def render(width: Int): ComponentRender           =
        try context.foreach(_.requestRender())
        catch case _: IllegalStateException => ()
        ComponentRender.text("forbidden")

    val probe           = ContextProbe()
    val (tui, terminal) = running()
    var result          = Option.empty[AppendResult]
    tui.appendToScrollback(probe, value => result = Some(value))
    assert(result.exists(_.isInstanceOf[AppendResult.Failed]))
    assertEquals(terminal.output.contains("forbidden"), false)
    intercept[IllegalStateException](probe.retained.get.imageCellDimensions)
  }

  test("context detachment failure prevents publication after revocation") {
    final class DetachFailure extends Component, ContextualComponent:
      var context                                                = Option.empty[TUIContext]
      override def tuiContext_=(value: Option[TUIContext]): Unit =
        if value.isEmpty then
          context.foreach(current => intercept[IllegalStateException](current.imageCellDimensions))
          throw RuntimeException("detach failed")
        context = value
      override def render(width: Int): ComponentRender           = ComponentRender.text("not-published")

    val (tui, terminal) = running()
    var result          = Option.empty[AppendResult]
    tui.appendToScrollback(DetachFailure(), value => result = Some(value))
    assert(result.exists(_.isInstanceOf[AppendResult.Failed]))
    assert(!terminal.output.contains("not-published"))
  }

  test("resize-invalidated append retries and completes once") {
    val terminal  = VirtualTerminal(20, 8)
    val (tui, _)  = running(terminal)
    val renders   = AtomicInteger(0)
    val callbacks = AtomicInteger(0)
    val component = new Component:
      override def render(width: Int): ComponentRender =
        if renders.incrementAndGet() === 1 then terminal.resize(19, 8)
        ComponentRender.text("retry")

    tui.appendToScrollback(component, _ => callbacks.incrementAndGet())

    assertEquals(renders.get(), 2)
    assertEquals(callbacks.get(), 1)
    assert(terminal.output.contains("retry"))
    tui.stop()
  }

  test("accepted incomplete append operations are bounded at 64") {
    val (tui, _) = running()
    val entered  = CountDownLatch(1)
    val release  = CountDownLatch(1)
    val blocker  = new Component:
      override def render(width: Int): ComponentRender =
        entered.countDown()
        release.await(2, TimeUnit.SECONDS)
        ComponentRender.text("first")
    val owner    = Thread(() => tui.appendToScrollback(blocker))
    owner.start()
    assert(entered.await(1, TimeUnit.SECONDS))
    (1 until 64).foreach(index => tui.appendToScrollback(Line(s"queued-$index")))
    val overflow = AtomicReference[AppendResult]()
    tui.appendToScrollback(Line("overflow"), overflow.set)
    release.countDown()
    owner.join(3000)
    tui.flushRender()

    assertEquals(
      overflow.get(),
      AppendResult.Rejected(AppendRejection.QueueCapacityExceeded)
    )
    tui.stop()
  }

  test("external capacity rejection uses bounded ingress backpressure") {
    val (tui, terminal) = running()
    val entered         = CountDownLatch(1)
    val release         = CountDownLatch(1)
    val blocker         = new Component:
      override def render(width: Int): ComponentRender =
        entered.countDown()
        release.await(2, TimeUnit.SECONDS)
        ComponentRender.empty
    val owner           = Thread(() => tui.appendToScrollback(blocker))
    owner.start()
    assert(entered.await(1, TimeUnit.SECONDS))
    val empty           = new Component:
      override def render(width: Int): ComponentRender = ComponentRender.empty
    (1 until 64).foreach(_ => tui.appendToScrollback(empty))
    (1 to 4096).foreach(_ =>
      terminal.sendInput(
        TerminalInput.Key(scalatui.terminal.TerminalKey.Character("queued"))
      )
    )
    val overflowResult  = AtomicReference[AppendResult]()
    val overflowStarted = CountDownLatch(1)
    val overflowDone    = CountDownLatch(1)
    val overflow        = Thread(() =>
      overflowStarted.countDown()
      try tui.appendToScrollback(Line("overflow"), overflowResult.set)
      finally overflowDone.countDown()
    )
    overflow.start()
    assert(overflowStarted.await(1, TimeUnit.SECONDS), "capacity publisher did not start")
    assert(
      !overflowDone.await(100, TimeUnit.MILLISECONDS),
      "capacity rejection did not backpressure on full ingress"
    )
    release.countDown()
    owner.join(5000)
    assert(overflowDone.await(5, TimeUnit.SECONDS), "capacity publisher did not finish")
    overflow.join(1000)

    assert(!owner.isAlive)
    assert(!overflow.isAlive)
    assertEquals(
      overflowResult.get(),
      AppendResult.Rejected(AppendRejection.QueueCapacityExceeded)
    )
    tui.stop()
  }

  test("cursor placements and Kitty cleanup fail before append publication") {
    val (cursorTui, cursorTerminal) = running()
    var cursorResult                = Option.empty[AppendResult]
    val cursor                      = Mutable(ComponentRender(
      Vector("x"),
      Vector.empty,
      Vector(CursorPlacement(0, 0))
    ))
    cursorTui.appendToScrollback(cursor, value => cursorResult = Some(value))
    assert(cursorResult.exists(_.isInstanceOf[AppendResult.Failed]))
    assertEquals(cursorTerminal.output.contains("x"), false)

    val cleanupControl                = TerminalImageProtocol.deleteAllImages(
      TerminalCapabilities(
        trueColor = false,
        hyperlinks = false,
        images = Some(ImageProtocol.Kitty)
      )
    ).get
    val cleanupTuiTerminal            = VirtualTerminal()
    val (cleanupTui, cleanupTerminal) = running(cleanupTuiTerminal)
    var cleanupResult                 = Option.empty[AppendResult]
    cleanupTui.appendToScrollback(
      Mutable(ComponentRender(
        Vector(""),
        Vector(TerminalControlPlacement(0, 0, cleanupControl)),
        Vector.empty
      )),
      value => cleanupResult = Some(value)
    )
    assert(cleanupResult.exists(_.isInstanceOf[AppendResult.Failed]))
    assertEquals(cleanupTerminal.output.contains("a=d"), false)
  }

  test("appended Kitty IDs are remapped and appended iTerm2 remains supported") {
    val kitty           = TerminalImageProtocol.encodeKitty(payload, Int.MaxValue, 1, 1)
    val (tui, terminal) = running()
    var kittyResult     = Option.empty[AppendResult]
    tui.appendToScrollback(
      Mutable(ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, kitty)),
        Vector.empty
      )),
      value => kittyResult = Some(value)
    )
    assertEquals(kittyResult, Some(AppendResult.Published(1, 1)))
    assert(!terminal.output.contains(s"i=${Int.MaxValue}"))

    terminal.clearWrites()
    val iterm       = TerminalImageProtocol.encodeITerm2(payload, None, 1, 1)
    var itermResult = Option.empty[AppendResult]
    tui.appendToScrollback(
      Mutable(ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, iterm)),
        Vector.empty
      )),
      value => itermResult = Some(value)
    )
    assertEquals(itermResult, Some(AppendResult.Published(1, 1)))
    assert(terminal.output.contains("1337;File="))
    tui.stop()
  }

  test("retained iTerm2 frame rejects append without rendering it") {
    val iterm           = TerminalImageProtocol.encodeITerm2(payload, None, 1, 1)
    val retained        = Mutable(ComponentRender(
      Vector(" "),
      Vector(TerminalControlPlacement(0, 0, iterm)),
      Vector.empty
    ))
    val (tui, terminal) = running(component = retained)
    val renders         = AtomicInteger(0)
    var result          = Option.empty[AppendResult]
    tui.appendToScrollback(
      new Component:
        override def render(width: Int): ComponentRender =
          renders.incrementAndGet()
          ComponentRender.text("never")
      ,
      value => result = Some(value)
    )
    assertEquals(result, Some(AppendResult.Rejected(AppendRejection.RetainedITerm2Control)))
    assertEquals(renders.get(), 0)
    assertEquals(terminal.output, "")
    tui.stop()
  }

  test("append restores retained hardware cursor and mouse frame origin") {
    final class MouseCursorLine extends Component, MouseInputHandler:
      var mouse                                                         = Option.empty[MouseInputContext]
      override def render(width: Int): ComponentRender                  = ComponentRender(
        Vector("live"),
        Vector.empty,
        Vector(CursorPlacement(0, 1))
      )
      override def handleMouse(context: MouseInputContext): InputResult =
        mouse = Some(context)
        InputResult.NoRender

    val target   = MouseCursorLine()
    val terminal = VirtualTerminal(20, 8)
    val options  = TUIOptions(
      hardwareCursorPositioning = true,
      mouseInput = true,
      normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback
    )
    val (tui, _) = running(terminal, target, options)
    tui.appendToScrollback(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text(
        Vector("one", "two")
      ))

    assertEquals(terminal.cursorPosition, 2 -> 1)
    terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Press(MouseButton.Left),
      row = 2,
      col = 0
    ))
    assertEquals(target.mouse.map(_.localRow), Some(0))
    assertEquals(target.mouse.map(_.boundsRow), Some(2))
    tui.stop()
  }

  test("stop rejects claimed and queued appends once in accepted order") {
    val (tui, terminal) = running()
    val entered         = CountDownLatch(1)
    val release         = CountDownLatch(1)
    val results         = scala.collection.mutable.ArrayBuffer.empty[(Int, AppendResult)]
    val active          = new Component:
      override def render(width: Int): ComponentRender =
        entered.countDown()
        release.await(2, TimeUnit.SECONDS)
        ComponentRender.text("active")
    val owner           = Thread(() => tui.appendToScrollback(active, result => results += 1 -> result))
    owner.start()
    assert(entered.await(1, TimeUnit.SECONDS))
    tui.appendToScrollback(Line("queued"), result => results += 2 -> result)
    tui.stop()
    release.countDown()
    owner.join(3000)

    assertEquals(results.map(_._1).toVector, Vector(1, 2))
    assertEquals(
      results.map(_._2).toVector,
      Vector(
        AppendResult.Rejected(AppendRejection.StoppedBeforePublication),
        AppendResult.Rejected(AppendRejection.StoppedBeforeClaim)
      )
    )
    assert(!terminal.output.contains("active"))
    assert(!terminal.output.contains("queued"))

  }

  test("Cleaning cutoff retains external append completions until the owner can invoke them") {
    val terminal        = HookTerminal()
    val tui             = TUI(
      terminal,
      TUIOptions(normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback)
    )
    val restorationHit  = CountDownLatch(1)
    val allowRestore    = CountDownLatch(1)
    val detachedEntered = CountDownLatch(1)
    val allowDetached   = CountDownLatch(1)
    val lateResult      = AtomicReference[AppendResult]()
    tui.addChild(Line("live"))
    tui.start()
    terminal.beforeStop = () => {
      restorationHit.countDown()
      allowRestore.await(2, TimeUnit.SECONDS)
    }

    val stopper = Thread(() => tui.stop())
    stopper.start()
    assert(restorationHit.await(2, TimeUnit.SECONDS), "terminal stop hook did not run")
    tui.appendToScrollback(
      Line("during-cleaning"),
      _ => {
        detachedEntered.countDown()
        allowDetached.await(2, TimeUnit.SECONDS)
      }
    )
    allowRestore.countDown()
    assert(detachedEntered.await(2, TimeUnit.SECONDS), "detached completion did not run")
    tui.appendToScrollback(Line("after-cutoff"), lateResult.set)
    assertEquals(lateResult.get(), null)
    allowDetached.countDown()
    stopper.join(3000)

    assert(!stopper.isAlive)
    assertEquals(
      lateResult.get(),
      AppendResult.Rejected(AppendRejection.LifecycleUnavailable(
        TUIDiagnosticLifecycleState.Cleaning
      ))
    )
  }

  test("Kitty remapping skips current retained IDs and ledger collisions fail retained render") {
    val allocated       = TerminalImageProtocol.allocateImageId()
    val retainedId      = allocated + 1
    val retainedControl = TerminalImageProtocol.encodeKitty(payload, retainedId, 1, 1)
    val retained        = Mutable(ComponentRender(
      Vector(" "),
      Vector(TerminalControlPlacement(0, 0, retainedControl)),
      Vector.empty
    ))
    val (tui, terminal) = running(component = retained)
    terminal.clearWrites()
    val appendControl   = TerminalImageProtocol.encodeKitty(payload, 77, 1, 1)
    tui.appendToScrollback(Mutable(ComponentRender(
      Vector(" "),
      Vector(TerminalControlPlacement(0, 0, appendControl)),
      Vector.empty
    )))
    val transmitted     = "a=T,[^;]*i=([0-9]+)".r.findAllMatchIn(terminal.output)
      .map(_.group(1).toInt)
      .toVector
    assertEquals(transmitted.length, 2)
    assert(transmitted.head !== retainedId)
    assertEquals(transmitted.last, retainedId)

    val appendId = transmitted.head
    retained.frame = retained.frame.copy(controls =
      Vector(TerminalControlPlacement(
        0,
        0,
        TerminalImageProtocol.encodeKitty(payload, appendId, 1, 1)
      ))
    )
    terminal.clearWrites()
    tui.requestRender(force = true)
    tui.flushRender()
    assert(!terminal.isRunning)
    assert(!terminal.output.contains(s"a=T,f=100,i=$appendId"))
  }

  test("completion callback failure is isolated and terminal cleanup still completes") {
    val (tui, terminal) = running()
    val calls           = AtomicInteger(0)
    tui.appendToScrollback(
      Line("history"),
      _ =>
        calls.incrementAndGet()
        throw RuntimeException("callback failed")
    )
    assertEquals(calls.get(), 1)
    assert(!terminal.isRunning)
  }

  test("Kitty ownership ledger is capped while text append remains available") {
    val events   = scala.collection.mutable.ArrayBuffer.empty[TUIDiagnosticEvent]
    val options  = TUIOptions(
      normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback,
      diagnosticObserver = Some(TUIDiagnosticObserver(events += _))
    )
    val (tui, _) = running(VirtualTerminal(2, 10), options = options)
    val controls = Vector.tabulate(4096) { index =>
      TerminalControlPlacement(
        index,
        0,
        TerminalImageProtocol.encodeKitty(payload, index + 1, 1, 1)
      )
    }
    var first    = Option.empty[AppendResult]
    tui.appendToScrollback(
      Mutable(ComponentRender(Vector.fill(4096)(" "), controls, Vector.empty)),
      value => first = Some(value)
    )
    assertEquals(first, Some(AppendResult.Published(4096, 4096)))

    var text = Option.empty[AppendResult]
    tui.appendToScrollback(Line("still-text"), value => text = Some(value))
    assertEquals(text, Some(AppendResult.Published(1, 0)))

    var overflow = Option.empty[AppendResult]
    val kitty    = TerminalImageProtocol.encodeKitty(payload, 5000, 1, 1)
    tui.appendToScrollback(
      Mutable(ComponentRender(
        Vector(" "),
        Vector(TerminalControlPlacement(0, 0, kitty)),
        Vector.empty
      )),
      value => overflow = Some(value)
    )
    assert(overflow.exists(_.isInstanceOf[AppendResult.Failed]))
    assert(events.exists {
      case TUIDiagnosticEvent.Append(
            TUIDiagnosticAppendOutcome.Failed,
            Some(TUIDiagnosticAppendFailure.Identity),
            0,
            0,
            TUIScreenMode.Normal,
            _
          ) => true
      case _ => false
    })
  }

  test("append diagnostics remain structural") {
    val events          = scala.collection.mutable.ArrayBuffer.empty[TUIDiagnosticEvent]
    val options         = TUIOptions(
      normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback,
      diagnosticObserver = Some(TUIDiagnosticObserver(events += _))
    )
    val (tui, _)        = running(options = options)
    tui.appendToScrollback(Line("secret"))
    assert(events.exists {
      case TUIDiagnosticEvent.Append(
            TUIDiagnosticAppendOutcome.Published,
            None,
            1,
            0,
            TUIScreenMode.Normal,
            _
          ) => true
      case _ => false
    })
    assert(!events.mkString.contains("secret"))
    val rejectionEvents = scala.collection.mutable.ArrayBuffer.empty[TUIDiagnosticEvent]
    val stopped         = TUI(
      VirtualTerminal(),
      TUIOptions(diagnosticObserver = Some(TUIDiagnosticObserver(rejectionEvents += _)))
    )
    stopped.appendToScrollback(Line("rejected-secret"))
    assert(rejectionEvents.exists {
      case TUIDiagnosticEvent.Append(
            TUIDiagnosticAppendOutcome.Rejected,
            Some(TUIDiagnosticAppendFailure.Lifecycle),
            0,
            0,
            TUIScreenMode.Normal,
            _
          ) => true
      case _ => false
    })
    assert(!rejectionEvents.mkString.contains("rejected-secret"))

    val writeEvents   = scala.collection.mutable.ArrayBuffer.empty[TUIDiagnosticEvent]
    val writeTerminal = HookTerminal()
    val writeTui      = TUI(
      writeTerminal,
      TUIOptions(
        normalResizeClearPolicy = NormalResizeClearPolicy.PreserveScrollback,
        diagnosticObserver = Some(TUIDiagnosticObserver(writeEvents += _))
      )
    )
    writeTui.addChild(Line("live"))
    writeTui.start()
    writeTerminal.writeFailure = data =>
      Option.when(data.contains("write-secret"))(RuntimeException("injected write failure"))
    writeTui.appendToScrollback(Line("write-secret"))
    assert(writeEvents.exists {
      case TUIDiagnosticEvent.Append(
            TUIDiagnosticAppendOutcome.Failed,
            Some(TUIDiagnosticAppendFailure.Write),
            0,
            0,
            TUIScreenMode.Normal,
            _
          ) => true
      case _ => false
    })
    assert(!writeEvents.mkString.contains("write-secret"))
    tui.stop()
  }
