package scalatui.terminal.jvm.interop

import scalatui.components.{Input, Text}
import scalatui.core.{Component, TUI}
import scalatui.terminal.jvm.SttyTerminal

import java.util.Objects
import java.util.function.Consumer

/**
 * JVM-only Java and Kotlin interop facade for basic terminal-backed siglyph apps.
 *
 * This facade is intentionally narrow. It covers the common JVM path: create a default
 * [[scalatui.core.TUI]] with [[scalatui.terminal.jvm.SttyTerminal]], create [[Text]] and [[Input]]
 * components, add components, set focus, run, request exit, and register input submit callbacks
 * through JDK functional interfaces.
 *
 * The Scala-first API remains the primary API for advanced options. This facade does not change
 * Scala Native artifacts and does not promise Java or Kotlin support for Scala Native modules.
 */
final class SiglyphJvm:

  /** Create a terminal-backed TUI using the JVM `stty` backend and default runtime options. */
  def createTui(): TUI = TUI(SttyTerminal())

  /** Create a text component with the standard siglyph padding and no custom style function. */
  def createText(content: String): Text = Text(Objects.requireNonNull(content, "content"))

  /** Create a text component with explicit padding and no custom style function. */
  def createText(content: String, paddingX: Int, paddingY: Int): Text =
    Text(Objects.requireNonNull(content, "content"), paddingX, paddingY)

  /** Create an empty single-line input component with default keybindings. */
  def createInput(): Input = Input()

  /** Create a single-line input component with an initial value and default keybindings. */
  def createInput(initialValue: String): Input =
    Input(Objects.requireNonNull(initialValue, "initialValue"))

  /**
   * Register an input submit callback using a JDK consumer.
   *
   * The callback receives the submitted input value. The returned input is the same instance passed
   * to this method, which lets Java and Kotlin call sites keep construction chains concise.
   */
  def onSubmit(input: Input, callback: Consumer[String]): Input =
    val checkedInput    = Objects.requireNonNull(input, "input")
    val checkedCallback = Objects.requireNonNull(callback, "callback")
    checkedInput.onSubmit = value => checkedCallback.accept(value)
    checkedInput

  /** Add a component to a TUI and return the same TUI instance. */
  def addChild(tui: TUI, component: Component): TUI =
    val checkedTui       = Objects.requireNonNull(tui, "tui")
    val checkedComponent = Objects.requireNonNull(component, "component")
    checkedTui.addChild(checkedComponent)
    checkedTui

  /** Set focus to a component and return the same TUI instance. */
  def setFocus(tui: TUI, component: Component): TUI =
    val checkedTui       = Objects.requireNonNull(tui, "tui")
    val checkedComponent = Objects.requireNonNull(component, "component")
    checkedTui.setFocus(checkedComponent)
    checkedTui

  /** Run the TUI until the application requests exit or the runtime fails. */
  def run(tui: TUI): Unit = Objects.requireNonNull(tui, "tui").run()

  /** Request exit through the normal TUI shutdown path. */
  def requestExit(tui: TUI): Unit = Objects.requireNonNull(tui, "tui").requestExit()
