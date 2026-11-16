package scalatui.demo

import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scalatui.scheduling.{
  ExecutorTaskScheduler, SchedulingRejectedException, SchedulingRejection, TaskScheduler
}
import java.nio.file.Path
import scalatui.ansi.Ansi
import scalatui.theme.SemanticTheme
import scalatui.components.{
  DataTable, Input, ScrollView, ScrollbarMode, StackEntry, StackEntryOptions, TableColumn,
  TableData, VStack
}
import scalatui.core.{
  ApplicationScope, ApplicationSubmission, Component, ComponentRender, InputResult,
  ScheduledCleanupPendingException, ScopedTask, TUI
}
import scalatui.syntax.Equality.*
import scalatui.terminal.{KeyEventType, KeyModifiers, Terminal, TerminalInput, TerminalKey}

/**
 * Shared step-driven/live generated-log example for roadmap #93; file ingestion remains separate.
 */
final class LogExplorerDemo private (
    val tui: TUI,
    private[demo] val model: LogExplorerModel,
    private[demo] val table: DataTable[LogEvent],
    private[demo] val viewport: ScrollView,
    generate: Int => Unit,
    markLive: () => Unit,
    refreshFile: () => Unit
):
  private val sourceLock                = new Object
  private var filePump                  = Option.empty[FileLogPump]
  private var fileMode                  = false
  private var cancelPolling: () => Unit = () => ()
  private var sourceStopped             = false
  private var sourceStarted             = false
  private var sourceScope               = Option.empty[ApplicationScope]
  private var sourceTask                = Option.empty[ScopedTask]

  private[demo] def sourceStatus: String =
    val file     = sourceLock.synchronized(filePump)
    val snapshot = sourceLock.synchronized(sourceStarted -> sourceTask)
    if file.nonEmpty then file.get.status
    else if !snapshot._1 then "ready"
    else
      snapshot._2 match
        case Some(task) if task.failure.nonEmpty                                        => "failed"
        case Some(task) if task.lastSubmission.exists(_ === ApplicationSubmission.Full) =>
          "overloaded"
        case Some(task) if task.isDone                                                  => "stopped"
        case _                                                                          => "live"

  private def validateStream(intervalMillis: Long, batchSize: Int): Unit =
    require(intervalMillis > 0L, "stream interval must be positive")
    require(batchSize >= 1 && batchSize <= 1000, "stream batch must be 1..1000")

  /** Start once after TUI startup; cancellation is terminal for this demo instance. */
  private[demo] def startStreaming(
      scheduler: TaskScheduler,
      intervalMillis: Long = 250L,
      batchSize: Int = 20
  ): Option[ApplicationScope] =
    validateStream(intervalMillis, batchSize)
    val start = sourceLock.synchronized {
      if sourceStopped then false
      else
        require(!sourceStarted, "source streaming was already started")
        sourceStarted = true
        true
    }
    if !start then None
    else
      val created = try Some(tui.openApplicationScope(maxScheduled = 1))
      catch
        case error: Throwable =>
          try tui.rethrowRuntimeFailure()
          catch
            case runtime: Throwable =>
              suppress(runtime, error)
              throw runtime
          if error.isInstanceOf[IllegalArgumentException] && sourceLock.synchronized(sourceStopped)
          then None
          else throw error
      created.map { scope =>
        val install = sourceLock.synchronized {
          sourceScope = Some(scope)
          !sourceStopped
        }
        try
          if install then
            scope.submit { markLive() }
            val task = scope.scheduleEvery(scheduler, intervalMillis, intervalMillis) { _ =>
              if model.snapshot.cancelled then scope.close()
              else generate(batchSize)
            }
            sourceLock.synchronized { sourceTask = Some(task) }
            tui.requestRender()
            tui.flushRender()
          else scope.close()
        catch
          case rejected: SchedulingRejectedException
              if (rejected.reason === SchedulingRejection.Closed) && model.snapshot.cancelled => ()
          case error: Throwable =>
            try scope.close()
            catch case cleanup: Throwable => suppress(error, cleanup)
            throw error
        scope
      }

  private[demo] def cancelSource(allowPending: Boolean = false): Unit =
    model.cancel()
    val scope  = sourceLock.synchronized {
      sourceStopped = true
      sourceScope
    }
    val cancel = sourceLock.synchronized(cancelPolling)
    managed(())(
      cancel,
      () =>
        scope.foreach { current =>
          try current.close()
          catch case _: ScheduledCleanupPendingException if allowPending => ()
        }
    )

  private def suppress(primary: Throwable, secondary: Throwable): Unit =
    if (primary ne secondary) && !primary.getSuppressed.exists(_ eq secondary) then
      primary.addSuppressed(secondary)

  private def managed(body: => Unit)(cleanup: (() => Unit)*): Unit =
    var failure = Option.empty[Throwable]
    try body
    catch case error: Throwable => failure = Some(error)
    cleanup.foreach { action =>
      try action()
      catch
        case error: Throwable => failure match
            case Some(primary) => suppress(primary, error)
            case None          => failure = Some(error)
    }
    failure.foreach(throw _)

  def run(): Unit = managed(tui.run())(() => cancelSource(), () => tui.stop())

  def stop(): Unit = managed(())(() => cancelSource(), () => tui.stop())

  /** Application-owned wall-clock producer; step mode remains available through run(). */
  def runLive(intervalMillis: Long = 250L, batchSize: Int = 20): Unit =
    validateStream(intervalMillis, batchSize)
    val executor             = new ScheduledThreadPoolExecutor(1)
    val backgroundFailure    = new AtomicReference[Throwable]()
    val scheduler            = ExecutorTaskScheduler(
      executor,
      error =>
        backgroundFailure.compareAndSet(null, error)
        tui.requestExit()
    )
    def stopProducer(): Unit =
      managed(())(
        () => scheduler.close(),
        () => { executor.shutdownNow(); () },
        () => {
          if !executor.awaitTermination(5L, TimeUnit.SECONDS) then
            throw new IllegalStateException("log producer did not terminate")
        }
      )
    runScheduled(
      scheduler,
      () => stopProducer(),
      () => Option(backgroundFailure.get()),
      intervalMillis,
      batchSize
    )

  private[demo] def isFileMode: Boolean = sourceLock.synchronized(fileMode)

  /** File I/O runs on an application-owned worker, never inside a scoped UI callback. */
  def runFile(path: Path): Unit =
    val executor             = new ScheduledThreadPoolExecutor(
      1,
      new java.util.concurrent.ThreadFactory:
        override def newThread(work: Runnable): Thread =
          val thread = new Thread(work, "siglyph-file-follower")
          thread.setDaemon(true)
          thread
    )
    val failure              = new AtomicReference[Throwable]()
    val active               = new java.util.concurrent.atomic.AtomicBoolean(true)
    var future               = Option.empty[java.util.concurrent.ScheduledFuture[?]]
    val futureLock           = new Object
    def cancelWorker(): Unit =
      active.set(false)
      val task = futureLock.synchronized(future)
      task.foreach(_.cancel(false))
    sourceLock.synchronized {
      require(!sourceStarted, "source was already started")
      sourceStarted = true
      fileMode = true
      cancelPolling = () => {
        sourceLock.synchronized(filePump).foreach(_.cancel())
        cancelWorker()
      }
    }
    managed {
      tui.start()
      tui.rethrowRuntimeFailure()
      val scope   = tui.openApplicationScope()
      val install = sourceLock.synchronized { sourceScope = Some(scope); !sourceStopped }
      if install then
        val pump       = new FileLogPump(
          new FileLogReader(path),
          scope,
          () => { val snapshot = model.snapshot; !snapshot.paused && !snapshot.cancelled },
          report =>
            val accepted = report match
              case Left(_)      => !model.snapshot.paused
              case Right(batch) =>
                if model.snapshot.following && !viewport.isFollowingEnd then model.move(0)
                model.appendFile(batch)
            if accepted then refreshFile()
            accepted
        )
        sourceLock.synchronized { filePump = Some(pump) }
        val scheduled  = executor.scheduleWithFixedDelay(
          new Runnable:
            override def run(): Unit =
              if active.get() then
                try pump.poll()
                catch
                  case error: Throwable =>
                    failure.compareAndSet(null, error)
                    tui.requestExit()
          ,
          0L,
          250L,
          TimeUnit.MILLISECONDS
        )
        val cancelLate = futureLock.synchronized { future = Some(scheduled); !active.get() }
        if cancelLate then scheduled.cancel(false)
      else scope.close()
      tui.run()
      Option(failure.get()).foreach(throw _)
    }(
      () => cancelSource(),
      () => tui.stop(),
      () => { executor.shutdownNow(); () },
      () => {
        if !executor.awaitTermination(5L, TimeUnit.SECONDS) then
          throw new IllegalStateException("file producer did not terminate")
      },
      () => tui.stop(),
      () => Option(failure.get()).foreach(throw _)
    )

  private[demo] def runScheduled(
      scheduler: TaskScheduler,
      stopProducer: () => Unit,
      producerFailure: () => Option[Throwable] = () => None,
      intervalMillis: Long = 250L,
      batchSize: Int = 20
  ): Unit =
    managed {
      validateStream(intervalMillis, batchSize)
      tui.start()
      tui.rethrowRuntimeFailure()
      startStreaming(scheduler, intervalMillis, batchSize)
      tui.rethrowRuntimeFailure()
      tui.run()
      producerFailure().foreach(throw _)
    }(
      () => cancelSource(),
      () => tui.stop(),
      stopProducer,
      () => tui.stop(),
      () => producerFailure().foreach(throw _)
    )

private final class LogExplorerLines(lines: () => Vector[String]) extends Component:
  override def render(width: Int): ComponentRender =
    ComponentRender.text(lines().map(Ansi.truncateToWidth(_, math.max(0, width), "")))

object LogExplorerDemo:
  def apply(terminal: Terminal, capacity: Int = 200, initialGenerated: Int = 40): LogExplorerDemo =
    val model                                          = LogExplorerModel(capacity)
    model.advance(initialGenerated)
    var manualStep: () => Unit                         = () => model.advance(20)
    var stopSource: () => Unit                         = () => model.cancel()
    var sourceLabel: () => String                      = () => "ready"
    var notice                                         = "Up/Down/PgUp/PgDn browse | Enter filter | Esc/Ctrl+C quit"
    val input                                          = Input(theme = SemanticTheme.Monochrome)
    val initial                                        = model.snapshot
    var publishedGeneration                            = initial.generated
    var publishedFilter                                = initial.filter
    val table                                          = DataTable(
      TableData(initial.rows, (event: LogEvent) => event.id.toString),
      Vector(
        TableColumn[LogEvent]("ID", 7, _.id.toString),
        TableColumn[LogEvent]("Level", 5, _.level),
        TableColumn[LogEvent]("Service", 7, _.service),
        TableColumn[LogEvent]("Message", 60, _.message)
      ),
      theme = SemanticTheme.Monochrome
    )
    initial.selected.foreach(event => table.selectId(event.id.toString))
    val view                                           = ScrollView(table, followEnd = true, scrollbar = ScrollbarMode.Automatic)
    val header                                         = LogExplorerLines(() =>
      val state  = model.snapshot
      val source =
        if state.cancelled then "cancelled"
        else if sourceLabel() === "overloaded" then "overloaded"
        else if state.paused then "paused" else sourceLabel()
      Vector(
        s"Logs ${state.retained}/$capacity | evicted ${state.evicted} | $source | filter: ${state.filter}"
      )
    )
    val detail                                         = LogExplorerLines(() =>
      model.snapshot.selected match
        case Some(event) =>
          Vector(s"Selected #${event.id}: ${event.level} / ${event.service}", event.message)
        case None        => Vector("No matching retained events", notice)
    )
    val footer                                         = LogExplorerLines(() =>
      Vector(
        "Ctrl+N step | Ctrl+P pause | Ctrl+G follow | Ctrl+X cancel",
        notice
      )
    )
    val root                                           = VStack(Seq(
      StackEntry(header, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1))),
      StackEntry(input, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1))),
      StackEntry(view, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(detail, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(2))),
      StackEntry(footer, StackEntryOptions(basis = Some(2), minSize = 1, maxSize = Some(2)))
    ))
    val tui                                            = TUI.fullscreen(terminal, root)
    def refresh(revealSelection: Boolean = true): Unit =
      val anchor = if !revealSelection && view.offset > 0 then
        table.data.rows.lift(view.offset - 1).map(_.id)
      else None
      val state  = model.snapshot
      if (state.generated !== publishedGeneration) || (state.filter !== publishedFilter) then
        table.setData(TableData(state.rows, (event: LogEvent) => event.id.toString))
        publishedGeneration = state.generated
        publishedFilter = state.filter
      state.selected.foreach(event => table.selectId(event.id.toString))
      view.invalidateSearchDocument()
      if state.following then view.jumpToEnd()
      else if revealSelection then
        view.jumpToStart()
        val index = state.rows.indexWhere(event => state.selected.exists(_.id === event.id))
        view.scrollTo(math.max(0, index + 1))
      else
        anchor.foreach { id =>
          val index = state.rows.indexWhere(_.id === id)
          view.scrollTo(math.max(0, index + 1))
        }
      tui.requestRender()

    input.onSubmit = value =>
      notice = if model.setFilter(value) then
        "Up/Down/PgUp/PgDn browse | Enter filter | Esc/Ctrl+C quit"
      else "Filter rejected: maximum 256 UTF-16 units; previous filter retained"
      refresh()
    tui.addInputListener {
      case TerminalInput.KeyEvent(_, _, KeyEventType.Release) => InputResult.Ignored
      case TerminalInput.Key(key, modifiers)                  =>
        val handled =
          if modifiers === KeyModifiers(ctrl = true) then
            key match
              case TerminalKey.Character("n") => manualStep(); true
              case TerminalKey.Character("p") => model.togglePause(); true
              case TerminalKey.Character("g") => model.followLatest(); true
              case TerminalKey.Character("x") => stopSource(); true
              case _                          => false
          else if modifiers.isEmpty then
            key match
              case TerminalKey.Up       => model.move(-1); true
              case TerminalKey.Down     => model.move(1); true
              case TerminalKey.PageUp   => model.move(-10); true
              case TerminalKey.PageDown => model.move(10); true
              case _                    => false
          else false
        if handled then
          refresh()
          InputResult.Render
        else if (
            (key === TerminalKey.Escape && modifiers.isEmpty) ||
            (key === TerminalKey.Character("c") && modifiers === KeyModifiers(ctrl = true))
          )
        then
          stopSource()
          InputResult.Exit
        else InputResult.Ignored
      case _                                                  => InputResult.Ignored
    }
    tui.setFocus(input)
    val demo = new LogExplorerDemo(
      tui,
      model,
      table,
      view,
      count => {
        if model.snapshot.following && !view.isFollowingEnd then model.move(0)
        model.advance(count)
        refresh(revealSelection = false)
      },
      () => refresh(),
      () => refresh(revealSelection = false)
    )
    manualStep = () => { if !demo.isFileMode then model.advance(20) }
    sourceLabel = () => demo.sourceStatus
    stopSource = () => demo.cancelSource(allowPending = true)
    demo
