package scalatui.terminal.jvm.interop

import java.util.{Objects, Optional}
import scalatui.syntax.Equality.*
import java.util.function.{Consumer, Function}
import scala.jdk.CollectionConverters.*
import scalatui.components.{DataTable, TableColumn, TableData, TableSelection}
import scalatui.core.{
  ApplicationScope, ApplicationSubmission, Component, ScopedTask, TUI, TUIOptions, TUIScreenMode
}
import scalatui.dsl.App
import scalatui.scheduling.{ManualTaskScheduler, TaskScheduler}
import scalatui.terminal.Terminal
import scalatui.testing.{TUITestDriver, TUITestLimits}
import scalatui.theme.{SemanticRole, SemanticStyle, SemanticTheme, ThemeColor}

/** Unreleased JVM adapters. No executor ownership or independent lifecycle is introduced. */
final class JvmUi:
  def app(): AppBuilder                                  = new AppBuilder
  def table[A](id: Function[A, String]): TableBuilder[A] = new TableBuilder(id)
  def theme(): ThemeBuilder                              = new ThemeBuilder

  /** Open a bounded scope after startup. The TUI owns its lifetime; explicit close is supported. */
  def openScope(tui: TUI, maxPending: Int, maxScheduled: Int): ApplicationScope =
    Objects.requireNonNull(tui, "tui").openApplicationScope(maxPending, maxScheduled)

  /** Fixed-delay, scope-owned delivery; the scheduler/executor remains borrowed. */
  def scheduleEvery(
      scope: ApplicationScope,
      scheduler: TaskScheduler,
      initialMillis: Long,
      intervalMillis: Long,
      update: Consumer[ScopedTask]
  ): ScopedTask =
    val checked = Objects.requireNonNull(update, "update")
    Objects.requireNonNull(scope, "scope").scheduleEvery(
      Objects.requireNonNull(scheduler, "scheduler"),
      initialMillis,
      intervalMillis
    )(task => checked.accept(task))

  /** Register Tab focus switching and Escape exit for a two-control form. */
  def navigation(tui: TUI, first: Component, second: Component): AutoCloseable =
    val runtime      = Objects.requireNonNull(tui, "tui")
    val left         = Objects.requireNonNull(first, "first")
    val right        = Objects.requireNonNull(second, "second")
    var firstFocused = true
    runtime.setFocus(left)
    val remove       = runtime.addInputListener {
      case scalatui.terminal.TerminalInput.KeyEvent(scalatui.terminal.TerminalKey.Tab, _, kind)
          if kind !== scalatui.terminal.KeyEventType.Release =>
        firstFocused = !firstFocused
        runtime.setFocus(if firstFocused then left else right)
        scalatui.core.InputResult.Render
      case scalatui.terminal.TerminalInput.KeyEvent(scalatui.terminal.TerminalKey.Escape, _, kind)
          if kind !== scalatui.terminal.KeyEventType.Release => scalatui.core.InputResult.Exit
      case _ => scalatui.core.InputResult.Ignored
    }
    () => remove()

  /**
   * Run an interactive app, invoking setup after startup. Scope creation belongs in setup. Always
   * stop on setup failure; preserve cleanup failures as suppressed exceptions.
   */
  def run(app: AppBuilder, setup: Consumer[TUI]): Unit =
    run(app, scalatui.terminal.jvm.SttyTerminal(), setup)

  /** Backend-injected managed run, with the same startup and teardown contract. */
  def run(app: AppBuilder, terminal: Terminal, setup: Consumer[TUI]): Unit =
    val checked = Objects.requireNonNull(setup, "setup")
    val tui     = Objects.requireNonNull(app, "app").build(terminal)
    tui.runWithSetup(runtime => checked.accept(runtime))

  /** Submit finite, nonblocking work; Accepted denotes admission, not completion. */
  def submit(scope: ApplicationScope, update: Runnable): ApplicationSubmission =
    val checked = Objects.requireNonNull(update, "update")
    Objects.requireNonNull(scope, "scope").submit(checked.run())

  /** Borrow the scheduler. Scope close cancels its registrations, never the scheduler itself. */
  def schedule(
      scope: ApplicationScope,
      scheduler: TaskScheduler,
      delayMillis: Long,
      update: Runnable
  ): ScopedTask =
    val checked = Objects.requireNonNull(update, "update")
    Objects.requireNonNull(
      scope,
      "scope"
    ).schedule(Objects.requireNonNull(scheduler, "scheduler"), delayMillis)(checked.run())

  /** Stable Java-readable name without Scala enum-case access. */
  def admission(result: ApplicationSubmission): String =
    Objects.requireNonNull(result, "result").toString

  /** Start and always restore a virtual session. The optional clock is owned by this session. */
  def test(app: AppBuilder, columns: Int, rows: Int, body: Consumer[JvmTestSession]): Unit =
    testWithClock(app, columns, rows, None, body)

  /** Managed deterministic clock; cleanup and suppressed failures follow TUITestDriver. */
  def test(
      app: AppBuilder,
      columns: Int,
      rows: Int,
      clock: ManualTaskScheduler,
      body: Consumer[JvmTestSession]
  ): Unit =
    testWithClock(app, columns, rows, Some(Objects.requireNonNull(clock, "clock")), body)

  private def testWithClock(
      app: AppBuilder,
      columns: Int,
      rows: Int,
      clock: Option[ManualTaskScheduler],
      body: Consumer[JvmTestSession]
  ): Unit =
    val configuration = Objects.requireNonNull(app, "app").snapshot
    val checked       = Objects.requireNonNull(body, "body")
    TUITestDriver.withSession(columns, rows, TUITestLimits(), clock)(configuration.build)(driver =>
      checked.accept(new JvmTestSession(driver))
    )

/**
 * Unreleased single-threaded builder. Each build snapshots configuration and remains unstarted.
 * Component instances are borrowed: never run concurrent sessions with the same components.
 */
final class AppBuilder:
  private var configuration                         = App()
  private[interop] def snapshot: App                = configuration
  def add(component: Component): AppBuilder         =
    configuration = configuration.add(Objects.requireNonNull(component, "component"))
    this
  def focus(component: Component): AppBuilder       =
    configuration = configuration.focused(Objects.requireNonNull(component, "component"))
    this
  def alternateScreen(enabled: Boolean): AppBuilder =
    configuration = configuration.copy(options =
      configuration.options.copy(screenMode =
        if enabled then TUIScreenMode.Alternate else TUIScreenMode.Normal
      )
    )
    this
  def options(value: TUIOptions): AppBuilder        =
    configuration = configuration.copy(options = Objects.requireNonNull(value, "options"))
    this
  def build(terminal: Terminal): TUI                =
    configuration.build(Objects.requireNonNull(terminal, "terminal"))

  /** Create an unstarted interactive JVM stty runtime. */
  def build(): TUI = build(scalatui.terminal.jvm.SttyTerminal())

/** Unreleased typed table builder. Rows/columns are copied; row values must remain immutable. */
final class TableBuilder[A] private[interop] (id: Function[A, String]):
  private val key                                            = Objects.requireNonNull(id, "id")
  private var data                                           = TableData(Vector.empty[A], (row: A) => key.apply(row))
  private var columns                                        = Vector.empty[TableColumn[A]]
  private var page                                           = 10
  private var appearance                                     = SemanticTheme.Plain
  private var changed: Consumer[Optional[TableSelection[A]]] = _ => ()

  def rows(values: java.util.Collection[A]): TableBuilder[A]                        =
    data = capture(values)
    this
  private def capture(values: java.util.Collection[A]): TableData[A]                =
    val copied = Objects.requireNonNull(values, "rows").asScala.iterator.map(row =>
      Objects.requireNonNull(row, "row")
    ).toVector
    TableData(copied, row => key.apply(row))
  def column(title: String, width: Int, cell: Function[A, String]): TableBuilder[A] =
    val checked = Objects.requireNonNull(cell, "cell")
    columns :+= TableColumn(
      Objects.requireNonNull(title, "title"),
      width,
      row => Objects.requireNonNull(checked.apply(row), "cell result")
    )
    this
  def pageSize(value: Int): TableBuilder[A]                                         =
    require(value > 0, "page size must be positive")
    page = value
    this
  def theme(value: SemanticTheme): TableBuilder[A]                                  =
    appearance = Objects.requireNonNull(value, "theme")
    this
  def onSelection(callback: Consumer[Optional[TableSelection[A]]]): TableBuilder[A] =
    changed = Objects.requireNonNull(callback, "callback")
    this
  def build(): DataTable[A]                                                         =
    val callback = changed
    DataTable(
      data,
      columns,
      value => callback.accept(value.fold(Optional.empty[TableSelection[A]]())(Optional.of)),
      page,
      appearance
    )

  /** Replace data using the same stable-ID function, preserving the core selection contract. */
  def replace(table: DataTable[A], rows: java.util.Collection[A]): Unit =
    Objects.requireNonNull(table, "table").setData(capture(rows))
  def selection(table: DataTable[A]): Optional[TableSelection[A]]       =
    Objects.requireNonNull(
      table,
      "table"
    ).selection.fold(Optional.empty[TableSelection[A]]())(Optional.of)

/** Unreleased immutable-theme snapshots configured with Java-readable role/color names. */
final class ThemeBuilder:
  private var value                      = SemanticTheme.Plain
  def preset(name: String): ThemeBuilder =
    value = SemanticTheme.named(Objects.requireNonNull(name, "name"))
    this
  def style(
      role: String,
      color: String,
      bold: Boolean,
      dim: Boolean,
      underline: Boolean,
      reverse: Boolean
  ): ThemeBuilder                        =
    val semantic   = SemanticRole.valueOf(Objects.requireNonNull(role, "role"))
    val foreground = ThemeColor.valueOf(Objects.requireNonNull(color, "color"))
    value = value.withOverrides(Map(semantic ->
      SemanticStyle(Some(foreground), bold, dim, underline, reverse)))
    this

  /**
   * Copy Java overrides; keys and values must be non-null. Existing theme snapshots never mutate.
   */
  def overrides(styles: java.util.Map[SemanticRole, SemanticStyle]): ThemeBuilder =
    val copied = Objects.requireNonNull(styles, "styles").asScala.iterator.map { (role, style) =>
      Objects.requireNonNull(role, "role") -> Objects.requireNonNull(style, "style")
    }.toMap
    value = value.withOverrides(copied)
    this
  def build(): SemanticTheme                                                      = value
