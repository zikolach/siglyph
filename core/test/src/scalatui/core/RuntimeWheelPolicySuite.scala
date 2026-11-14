package scalatui.core

import scalatui.components.*
import scalatui.terminal.*

class RuntimeWheelPolicySuite extends munit.FunSuite:
  private final class Clock extends MonotonicClock:
    var now                       = 0L
    override def nanoTime(): Long = now

  private final class Lines(count: Int) extends Component:
    override def render(width: Int): ComponentRender =
      ComponentRender.text(Vector.tabulate(count)(i => s"row $i"))

  test("normal TUI wheel routing applies configured steps and the injected monotonic clock"):
    val clock                            = Clock()
    val view                             = ScrollView(Lines(100), primary = true)
    val terminal                         = VirtualTerminal(20, 6)
    val tui                              = TUI.fullscreen(
      terminal,
      view,
      TUIOptions(mouseInput = true, mouseGestures = MouseGestureOptions(clock = clock))
    )
    def down(alt: Boolean = false): Unit = terminal.sendMouse(TerminalInput.Mouse(
      MouseAction.Wheel(MouseWheelDirection.Down),
      1,
      1,
      KeyModifiers(alt = alt)
    ))
    tui.wheelScrollPolicy = WheelScrollPolicy(fixedLines = 3)
    tui.start()
    try
      down()
      assertEquals(view.offset, 3)
      down(alt = true)
      assertEquals(view.offset, 18)
      tui.wheelScrollPolicy = WheelScrollPolicy(accelerate = true)
      view.scrollTo(0)
      down()
      assertEquals(view.offset, 1)
      clock.now = 20000000L
      down()
      assertEquals(view.offset, 6)
      tui.wheelScrollPolicy = WheelScrollPolicy(accelerate = true, terminalAccelerated = true)
      clock.now = 40000000L
      down()
      assertEquals(view.offset, 7)
    finally tui.stop()

  test("normal TUI nested routing does not accelerate a wheel remainder twice"):
    val inner    = ScrollView(Lines(6))
    val content  = VStack(Seq(
      StackEntry(inner, StackEntryOptions(basis = Some(3), minSize = 3, maxSize = Some(3))),
      StackEntry(Lines(40), StackEntryOptions(basis = Some(40), minSize = 40, maxSize = Some(40)))
    ))
    val outer    = ScrollView(content, primary = true)
    val terminal = VirtualTerminal(20, 6)
    val tui      = TUI.fullscreen(terminal, outer, TUIOptions(mouseInput = true))
    tui.wheelScrollPolicy = WheelScrollPolicy(fixedLines = 5)
    tui.start()
    try
      inner.scrollTo(2)
      terminal.sendMouse(TerminalInput.Mouse(MouseAction.Wheel(MouseWheelDirection.Down), 1, 1))
      assertEquals(inner.offset, 3)
      assertEquals(outer.offset, 4)
    finally tui.stop()
