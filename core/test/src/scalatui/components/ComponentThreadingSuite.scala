package scalatui.components

import scalatui.autocomplete.*
import scalatui.core.{
  Component,
  ContextualComponent,
  OverlayHandle,
  OverlayHost,
  OverlayOptions,
  TUI,
  TUIContext
}
import scalatui.editing.EditorCursor
import scalatui.terminal.{TerminalInput, TerminalKey, VirtualTerminal}

import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class ComponentThreadingSuite extends munit.FunSuite:
  private val DeadlockGuardSeconds = 5L

  test("editor commits mutation before reentrant change callback and later render request"):
    val events  = ConcurrentLinkedQueue[String]()
    val context = RecordingContext(events)
    val editor  = Editor()
    editor.tuiContext_=(Some(context))
    editor.onChange = value =>
      events.add(s"change:$value")
      assertEquals(editor.text, value)
      editor.setCursor(EditorCursor(0, 0))

    editor.insertAtCursor("x")

    assertEquals(events.toArray.toVector, Vector("change:x", "render"))
    assertEquals(editor.cursor, EditorCursor(0, 0))

  test("editor change callback does not participate in application lock inversion"):
    val editor          = Editor()
    val applicationLock = Object()
    val holderReady     = CountDownLatch(1)
    val callbackEntered = CountDownLatch(1)
    val readerDone      = CountDownLatch(1)
    editor.onChange = _ =>
      callbackEntered.countDown()
      applicationLock.synchronized(())

    val holder  = Thread(() =>
      applicationLock.synchronized {
        holderReady.countDown()
        callbackEntered.await()
        editor.text
        readerDone.countDown()
      }
    )
    val mutator = Thread(() => editor.insertAtCursor("x"))

    holder.start()
    holderReady.await()
    mutator.start()

    assert(readerDone.await(DeadlockGuardSeconds, TimeUnit.SECONDS), "lock inversion deadlock")
    holder.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    mutator.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(holder.isAlive || mutator.isAlive, false)

  test("slow editor callback does not block coherent reads or rendering"):
    val editor          = Editor()
    val callbackEntered = CountDownLatch(1)
    val releaseCallback = CountDownLatch(1)
    val readDone        = CountDownLatch(1)
    editor.onChange = _ =>
      callbackEntered.countDown()
      releaseCallback.await()

    val mutator = Thread(() => editor.insertAtCursor("value"))
    mutator.start()
    callbackEntered.await()
    val reader  = Thread(() => {
      assertEquals(editor.text, "value")
      assertEquals(editor.render(20).lines.nonEmpty, true)
      readDone.countDown()
    })
    reader.start()

    assert(
      readDone.await(DeadlockGuardSeconds, TimeUnit.SECONDS),
      "slow callback retained editor state"
    )
    releaseCallback.countDown()
    mutator.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    reader.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(mutator.isAlive || reader.isAlive, false)

  test("synchronous autocomplete provider runs outside editor state boundary"):
    val workerDone = CountDownLatch(1)
    var editor     = Option.empty[Editor]
    val provider   = new AutocompleteProvider:
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle =
        val worker = Thread(() => {
          editor.get.text
          workerDone.countDown()
        })
        worker.start()
        assert(
          workerDone.await(DeadlockGuardSeconds, TimeUnit.SECONDS),
          "provider retained editor state"
        )
        callback.complete(Some(AutocompleteSuggestions(
          Vector(AutocompleteItem("done", "done")),
          ""
        )))
        AutocompleteRequestHandle.Noop

      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)

    val instance = Editor(options = EditorOptions(autocompleteProvider = Some(provider)))
    editor = Some(instance)

    assertEquals(
      instance.handleInputResult(TerminalInput.Key(TerminalKey.Tab)),
      scalatui.core.InputResult.Render
    )
    instance.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(instance.text, "done")

  test("concurrent autocomplete completions reject the stale generation"):
    val callbacks = ConcurrentLinkedQueue[AutocompleteCallback]()
    val provider  = new AutocompleteProvider:
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle =
        callbacks.add(callback)
        AutocompleteRequestHandle.Noop

      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)

    val editor            = Editor(
      "/",
      EditorOptions(
        autocompleteProvider = Some(provider),
        autocompleteTrigger = EditorAutocompleteTrigger.ExplicitTabOnly
      )
    )
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Character("x")))
    assertEquals(callbacks.size(), 2)
    val first             = callbacks.poll()
    val second            = callbacks.poll()
    val release           = CountDownLatch(1)
    val done              = CountDownLatch(2)
    val oldCompletion     = Thread(() => {
      release.await()
      first.complete(Some(AutocompleteSuggestions(Vector(AutocompleteItem("old", "old")), "/")))
      done.countDown()
    })
    val currentCompletion = Thread(() => {
      release.await()
      second.complete(Some(AutocompleteSuggestions(Vector(AutocompleteItem("new", "new")), "/x")))
      done.countDown()
    })
    oldCompletion.start()
    currentCompletion.start()
    release.countDown()

    assert(done.await(DeadlockGuardSeconds, TimeUnit.SECONDS), "autocomplete completion deadlock")
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(editor.text, "new")

  test("autocomplete cancellation handle runs outside editor state boundary"):
    val cancelEntered = CountDownLatch(1)
    val releaseCancel = CountDownLatch(1)
    val readDone      = CountDownLatch(1)
    val provider      = new AutocompleteProvider:
      override def requestSuggestions(
          request: AutocompleteRequest,
          callback: AutocompleteCallback
      ): AutocompleteRequestHandle = () => {
        cancelEntered.countDown()
        releaseCancel.await()
      }

      override def applyCompletion(request: CompletionRequest): CompletionResult =
        AutocompleteProvider.defaultCompletion(request)

    val editor    = Editor(options = EditorOptions(autocompleteProvider = Some(provider)))
    editor.handleInputResult(TerminalInput.Key(TerminalKey.Tab))
    val canceller = Thread(() => editor.setAutocompleteProvider(None))
    canceller.start()
    cancelEntered.await()
    val reader    = Thread(() => {
      editor.text
      readDone.countDown()
    })
    reader.start()

    assert(
      readDone.await(DeadlockGuardSeconds, TimeUnit.SECONDS),
      "cancellation handle retained editor state"
    )
    releaseCancel.countDown()
    canceller.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    reader.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(canceller.isAlive || reader.isAlive, false)

  test("callback failure leaves editor state boundary available"):
    val failure = RuntimeException("change failed")
    val editor  = Editor(options = EditorOptions(onChange = _ => throw failure))

    assertEquals(intercept[RuntimeException](editor.insertAtCursor("x")), failure)
    val completed = CountDownLatch(1)
    val reader    = Thread(() => {
      assertEquals(editor.text, "x")
      editor.setCursor(EditorCursor(0, 0))
      completed.countDown()
    })
    reader.start()

    assert(
      completed.await(DeadlockGuardSeconds, TimeUnit.SECONDS),
      "failed callback retained editor state"
    )
    reader.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(reader.isAlive, false)

  test("runtime cleans up after editor callback failure"):
    val failure  = RuntimeException("change failed")
    val terminal = VirtualTerminal(20, 5)
    val editor   = Editor(options = EditorOptions(onChange = _ => throw failure))
    val tui      = TUI(terminal)
    tui.addChild(editor)
    tui.setFocus(editor)
    tui.start()

    terminal.sendInput(TerminalInput.Key(TerminalKey.Character("x")))

    assertEquals(terminal.isRunning, false)
    assertEquals(editor.text, "x")

  test("loader mutation remains coherent while style hook is blocked"):
    val styleEntered = CountDownLatch(1)
    val releaseStyle = CountDownLatch(1)
    val mutationDone = CountDownLatch(1)
    val loader       = Loader(LoaderOptions(
      messageStyle = value =>
        styleEntered.countDown()
        releaseStyle.await()
        value
    ))
    loader.start()
    val renderer     = Thread(() => {
      loader.render(40)
      ()
    })
    renderer.start()
    styleEntered.await()
    val mutator      = Thread(() => {
      loader.setMessage("updated")
      loader.setIndicator(LoaderIndicatorOptions(Vector("a", "b")))
      loader.tick()
      mutationDone.countDown()
    })
    mutator.start()

    assert(
      mutationDone.await(DeadlockGuardSeconds, TimeUnit.SECONDS),
      "style hook retained loader state"
    )
    releaseStyle.countDown()
    renderer.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    mutator.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(renderer.isAlive || mutator.isAlive, false)
    assertEquals(loader.message, "updated")
    assertEquals(loader.frame, "b")

  test("container context effects preserve commit order without cross-boundary waiting"):
    val events         = ConcurrentLinkedQueue[String]()
    val attachEntered  = CountDownLatch(1)
    val releaseAttach  = CountDownLatch(1)
    val removeStarted  = CountDownLatch(1)
    val removeFinished = CountDownLatch(1)
    val context        = RecordingContext(ConcurrentLinkedQueue[String]())
    val child          = new Component with ContextualComponent:
      override def render(width: Int): scalatui.core.ComponentRender =
        scalatui.core.ComponentRender.empty
      override def tuiContext_=(value: Option[TUIContext]): Unit     =
        value match
          case Some(_) =>
            events.add("attach")
            attachEntered.countDown()
            releaseAttach.await()
          case None    => events.add("detach")
    val container      = scalatui.core.Container()
    container.tuiContext_=(Some(context))
    val addThread      = Thread(() => container.addChild(child))
    val removeThread   = Thread(() => {
      removeStarted.countDown()
      container.removeChild(child)
      removeFinished.countDown()
    })

    addThread.start()
    attachEntered.await()
    removeThread.start()
    removeStarted.await()
    assertEquals(removeFinished.await(50, TimeUnit.MILLISECONDS), true)
    assertEquals(events.toArray.toVector, Vector("attach"))
    releaseAttach.countDown()

    addThread.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    removeThread.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(events.toArray.toVector, Vector("attach", "detach"))
    assertEquals(container.children, Vector.empty)

  test("concurrent editor callbacks preserve commit order without cross-boundary waiting"):
    val events        = ConcurrentLinkedQueue[String]()
    val firstEntered  = CountDownLatch(1)
    val releaseFirst  = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val secondDone    = CountDownLatch(1)
    val active        = AtomicInteger(0)
    val maxActive     = AtomicInteger(0)
    val editor        = Editor(options = EditorOptions(onChange = value => {
      val now = active.incrementAndGet()
      maxActive.updateAndGet(current => math.max(current, now))
      events.add(value)
      if value.equals("a") then
        firstEntered.countDown()
        releaseFirst.await()
      active.decrementAndGet()
      ()
    }))
    val first         = Thread(() => editor.insertAtCursor("a"))
    val second        = Thread(() => {
      secondStarted.countDown()
      editor.insertAtCursor("b")
      secondDone.countDown()
    })

    first.start()
    firstEntered.await()
    second.start()
    secondStarted.await()
    assertEquals(secondDone.await(50, TimeUnit.MILLISECONDS), true)
    assertEquals(events.toArray.toVector, Vector("a"))
    releaseFirst.countDown()

    first.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    second.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(events.toArray.toVector, Vector("a", "ab"))
    assertEquals(maxActive.get(), 1)

  test("failed editor effect does not corrupt later committed effect batches"):
    val failure       = RuntimeException("first failed")
    val firstEntered  = CountDownLatch(1)
    val releaseFirst  = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val events        = ConcurrentLinkedQueue[String]()
    val firstFailure  = AtomicReference[Throwable]()
    val editor        = Editor(options = EditorOptions(onChange = value => {
      events.add(value)
      if value.equals("a") then
        firstEntered.countDown()
        releaseFirst.await()
        throw failure
    }))
    val first         = Thread(() =>
      try editor.insertAtCursor("a")
      catch case error: Throwable => firstFailure.set(error)
    )
    val second        = Thread(() => {
      secondStarted.countDown()
      editor.insertAtCursor("b")
    })

    first.start()
    firstEntered.await()
    second.start()
    secondStarted.await()
    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds)
    while !editor.text.equals("ab") && System.currentTimeMillis() < deadline do Thread.`yield`()
    assertEquals(editor.text, "ab")
    releaseFirst.countDown()
    first.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    second.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))

    assertEquals(firstFailure.get(), failure)
    assertEquals(events.toArray.toVector, Vector("a", "ab"))
    assertEquals(editor.text, "ab")

  test("detached reentrant callback failure propagates through the active outer drain"):
    val failure        = RuntimeException("nested failed")
    var editor: Editor = null
    editor = Editor(options =
      EditorOptions(onChange =
        value =>
          if value.equals("a") then editor.insertAtCursor("b")
          else if value.equals("ab") then throw failure
      )
    )

    val thrown = intercept[RuntimeException](editor.insertAtCursor("a"))

    assertEquals(thrown, failure)
    assertEquals(editor.text, "ab")

  test("detached effect admission is bounded and rejects before state mutation"):
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val context = new RecordingContext(ConcurrentLinkedQueue[String]()):
      override def requestRender(force: Boolean): Unit =
        entered.countDown()
        release.await()
    val loader  = Loader()
    loader.tuiContext_=(Some(context))
    val owner   = Thread(() => loader.setMessage("owner"))
    owner.start()
    assert(entered.await(DeadlockGuardSeconds, TimeUnit.SECONDS))

    (0 until ComponentEffectCoordinator.QueueCapacity).foreach(index =>
      loader.setMessage(index.toString)
    )
    val failure = intercept[ComponentEffectAdmissionException](loader.setMessage("rejected"))
    assertEquals(failure.capacity, ComponentEffectCoordinator.QueueCapacity)
    assertEquals(loader.message, (ComponentEffectCoordinator.QueueCapacity - 1).toString)

    release.countDown()
    owner.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(owner.isAlive, false)

  test("attached components share effect order without making a concurrent caller wait"):
    def list(id: String): SettingsList = SettingsList(Vector(SettingItem(
      id,
      id,
      "a",
      values = Vector("a", "b")
    )))

    val first        = list("first")
    val second       = list("second")
    val events       = ConcurrentLinkedQueue[String]()
    val firstEntered = CountDownLatch(1)
    val releaseFirst = CountDownLatch(1)
    val secondDone   = CountDownLatch(1)
    first.onChange = (_, _) =>
      events.add("first")
      firstEntered.countDown()
      releaseFirst.await()
    second.onChange = (_, _) => events.add("second")
    val tui          = TUI(VirtualTerminal(20, 4))
    tui.addChild(first)
    tui.addChild(second)
    tui.start()

    val owner     = Thread(() => {
      first.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
      ()
    })
    owner.start()
    assert(firstEntered.await(DeadlockGuardSeconds, TimeUnit.SECONDS))
    val publisher = Thread(() => {
      second.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
      secondDone.countDown()
    })
    publisher.start()

    assert(secondDone.await(DeadlockGuardSeconds, TimeUnit.SECONDS))
    assertEquals(events.toArray.toVector, Vector("first"))
    releaseFirst.countDown()
    owner.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    publisher.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))

    assertEquals(owner.isAlive || publisher.isAlive, false)
    assertEquals(events.toArray.toVector, Vector("first", "second"))
    tui.stop()

  test("attached stopping rejects an effectful mutation before state change"):
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val list    = SettingsList(Vector(SettingItem(
      "mode",
      "Mode",
      "a",
      values = Vector("a", "b")
    )))
    list.onChange = (_, _) =>
      entered.countDown()
      release.await()
    val tui     = TUI(VirtualTerminal(20, 4))
    tui.addChild(list)
    tui.start()
    val owner   = Thread(() => {
      list.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
      ()
    })
    owner.start()
    assert(entered.await(DeadlockGuardSeconds, TimeUnit.SECONDS))
    tui.stop()
    val before  = list.items.head.currentValue

    intercept[ComponentEffectAdmissionException](
      list.handleInputResult(TerminalInput.Key(TerminalKey.Enter))
    )
    assertEquals(list.items.head.currentValue, before)

    release.countDown()
    owner.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds))
    assertEquals(owner.isAlive, false)

  test("concurrent loader mutations preserve a valid final snapshot"):
    val loader  = Loader()
    val failure = AtomicReference[Throwable]()
    loader.start()
    val workers = Vector.tabulate(8) { worker =>
      Thread(() =>
        try
          (0 until 100).foreach { index =>
            loader.setMessage(s"$worker:$index")
            loader.setIndicator(LoaderIndicatorOptions(Vector("0", "1", "2")))
            loader.tick()
            loader.render(30)
          }
        catch
          case error: Throwable =>
            failure.compareAndSet(null, error)
            ()
      )
    }
    workers.foreach(_.start())
    workers.foreach(_.join(TimeUnit.SECONDS.toMillis(DeadlockGuardSeconds)))

    assertEquals(workers.exists(_.isAlive), false)
    assertEquals(Option(failure.get()), None)
    loader.setMessage("final")
    loader.setIndicator(LoaderIndicatorOptions(Vector("a", "b")))
    assertEquals(loader.tick(), true)
    assertEquals(loader.message, "final")
    assertEquals(loader.frameIndex, 1)
    assertEquals(loader.frame, "b")

  private class RecordingContext(events: ConcurrentLinkedQueue[String]) extends TUIContext:
    override def requestRender(force: Boolean): Unit         = events.add("render")
    override def flushRender(): Unit                         = ()
    override def requestExit(): Unit                         = ()
    override def setFocus(component: Component | Null): Unit = ()
    override val overlays: OverlayHost                       = new OverlayHost:
      override def showOverlay(component: Component, options: OverlayOptions): OverlayHandle =
        throw UnsupportedOperationException()
      override def hideOverlay(): Unit                                                       = ()
      override def hasOverlay: Boolean                                                       = false
