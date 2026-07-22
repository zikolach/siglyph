#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using dep io.github.zikolach::siglyph-core:0.7.1
//> using dep io.github.zikolach::siglyph-terminal-jvm:0.7.1

import scalatui.ansi.Ansi
import scalatui.components.*
import scalatui.core.{Component, ComponentRender, InputResult, MouseInputHandler, TUI, TUIOptions}
import scalatui.terminal.*
import scalatui.terminal.jvm.SttyTerminal

@main def mouseDemo(): Unit =
  val tui          = TUI(SttyTerminal(), TUIOptions(mouseInput = true))
  val latestEvent  = Text("Latest mouse event: (move, click, or scroll after start)")
  val selectedItem = Text("Selected item: Item 1")
  val pad          = MousePad()
  val list         = SelectList(
    (1 to 30).map(index => SelectItem(s"item-$index", s"Item $index", Some("scroll me"))).toVector,
    maxVisible = 8
  )
  val editor       = Editor()

  list.onSelectionChange = item =>
    selectedItem.text = s"Selected item: ${item.map(_.label).getOrElse("(none)")}"

  editor.setText(
    """
      |This editor receives routed wheel events.
      |Scroll while the pointer is over this text.
      |The cursor moves by a small page.
      |
      |Line 5
      |Line 6
      |Line 7
      |Line 8
      |Line 9
      |Line 10
      |Line 11
      |Line 12
      |Line 13
      |Line 14
      |Line 15
      |Line 16
      |Line 17
      |Line 18
      |Line 19
      |Line 20
      |""".stripMargin.trim
  )

  tui.addInputListener {
    case mouse: TerminalInput.Mouse =>
      latestEvent.text = s"Latest mouse event: ${describeMouse(mouse)}"
      InputResult.Ignored
    case _                          => InputResult.Ignored
  }

  tui.addChild(Text("siglyph mouse demo"))
  tui.addChild(Text("Mouse reporting is opt-in here with TUIOptions(mouseInput = true)."))
  tui.addChild(Text("Try clicking the mouse pad, scrolling the list, and scrolling the editor."))
  tui.addChild(Text("While mouse mode is active, terminal scrollback is captured by the app."))
  tui.addChild(Text("Esc or Ctrl+C exits."))
  tui.addChild(Spacer(1))
  tui.addChild(latestEvent)
  tui.addChild(selectedItem)
  tui.addChild(Spacer(1))
  tui.addChild(Text("Mouse pad"))
  tui.addChild(pad)
  tui.addChild(Spacer(1))
  tui.addChild(Text("Scrollable SelectList"))
  tui.addChild(list)
  tui.addChild(Spacer(1))
  tui.addChild(Text("Scrollable Editor"))
  tui.addChild(editor)
  tui.setFocus(editor)
  tui.exitsOnEscape = true
  tui.run()

final class MousePad extends Component, MouseInputHandler:
  private var message = "No routed event yet. Click or scroll inside this block."

  override def render(width: Int): ComponentRender =
    val safeWidth = math.max(0, width)
    if safeWidth <= 0 then ComponentRender.text("")
    else
      ComponentRender.text(Vector(
        fit("+------------------------------------------------------------+", safeWidth),
        fit("| This component handles routed mouse events directly.       |", safeWidth),
        fit("| It reports terminal coordinates and component-local cells. |", safeWidth),
        fit(s"| $message", safeWidth),
        fit("+------------------------------------------------------------+", safeWidth)
      ))

  override def handleMouse(context: MouseInputContext): InputResult =
    message =
      s"${describeAction(context.input.action)} terminal=(${context.input.row},${context.input.col}) local=(${context.localRow},${context.localCol})"
    InputResult.Render

  private def fit(value: String, width: Int): String =
    Ansi.truncateToWidth(value, width, "")

private def describeMouse(mouse: TerminalInput.Mouse): String =
  val modifiers = describeModifiers(mouse.modifiers)
  val suffix    = if modifiers.isEmpty then "" else s" modifiers=$modifiers"
  s"${describeAction(mouse.action)} row=${mouse.row} col=${mouse.col}$suffix"

private def describeAction(action: MouseAction): String = action match
  case MouseAction.Press(button)    => s"press ${describeButton(button)}"
  case MouseAction.Release(button)  => s"release ${describeButton(button)}"
  case MouseAction.Wheel(direction) => s"wheel ${describeWheel(direction)}"

private def describeButton(button: MouseButton): String = button match
  case MouseButton.Left        => "left"
  case MouseButton.Middle      => "middle"
  case MouseButton.Right       => "right"
  case MouseButton.Other(code) => s"button-$code"

private def describeWheel(direction: MouseWheelDirection): String = direction match
  case MouseWheelDirection.Up    => "up"
  case MouseWheelDirection.Down  => "down"
  case MouseWheelDirection.Left  => "left"
  case MouseWheelDirection.Right => "right"

private def describeModifiers(modifiers: KeyModifiers): String =
  Vector(
    Option.when(modifiers.ctrl)("ctrl"),
    Option.when(modifiers.shift)("shift"),
    Option.when(modifiers.alt)("alt"),
    Option.when(modifiers.superKey)("super")
  ).flatten.mkString("+")
