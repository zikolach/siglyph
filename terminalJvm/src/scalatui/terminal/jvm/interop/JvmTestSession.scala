package scalatui.terminal.jvm.interop

import java.util.Objects
import scala.jdk.CollectionConverters.*
import scalatui.core.{ApplicationScope, TUI}
import scalatui.scheduling.TimeAdvance
import scalatui.terminal.TerminalKey
import scalatui.testing.TUITestDriver

/**
 * Unreleased Java-friendly managed virtual session. Valid only inside JvmUi.test's callback.
 * Delegates bounded actions and cleanup to the shared driver; no worker/executor is owned here.
 */
final class JvmTestSession private[interop] (driver: TUITestDriver):
  def tui(): TUI                                                      = driver.tui
  def openScope(maxPending: Int, maxScheduled: Int): ApplicationScope =
    driver.tui.openApplicationScope(maxPending, maxScheduled)
  def typeText(value: String): Unit                                   = driver.typeText(Objects.requireNonNull(value, "text"))
  def paste(value: String): Unit                                      = driver.paste(Objects.requireNonNull(value, "paste"))

  /** Named navigation keys, without exposing Scala enum singleton construction. */
  def key(name: String): Unit                                     =
    val key = Objects.requireNonNull(name, "key") match
      case "Enter"     => TerminalKey.Enter
      case "Up"        => TerminalKey.Up
      case "Down"      => TerminalKey.Down
      case "Left"      => TerminalKey.Left
      case "Right"     => TerminalKey.Right
      case "Home"      => TerminalKey.Home
      case "End"       => TerminalKey.End
      case "Tab"       => TerminalKey.Tab
      case "Escape"    => TerminalKey.Escape
      case "Backspace" => TerminalKey.Backspace
      case _           => throw new IllegalArgumentException("unsupported named key: " + name)
    driver.key(key)
  def resize(columns: Int, rows: Int): Unit                       = driver.resize(columns, rows)
  def advanceTimeBy(millis: Long, maxCallbacks: Int): TimeAdvance =
    driver.advanceTimeBy(millis, maxCallbacks)
  def flush(): Unit                                               = driver.flush()
  def assertContains(value: String): Unit                         =
    driver.assertContains(Objects.requireNonNull(value, "expected"))
  def assertNoPendingTasks(): Unit                                = driver.assertNoPendingTasks()
  def screenLines(): java.util.List[String]                       = java.util.List.copyOf(driver.screen.lines.asJava)
