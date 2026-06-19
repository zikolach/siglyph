package scalatui.demo

import scalatui.terminal.TerminalInput
import scalatui.terminal.jvm.SttyTerminal

import java.util.concurrent.atomic.AtomicBoolean

@main def keyTester(): Unit =
  val running  = AtomicBoolean(true)
  val terminal = SttyTerminal()
  println("scala-tui key tester — press Escape to exit")
  terminal.start(
    input =>
      println(describe(input))
      input match
        case TerminalInput.Key(scalatui.terminal.TerminalKey.Escape, _) => running.set(false)
        case _                                                          => ()
    ,
    () => println(s"resize: ${terminal.columns}x${terminal.rows}")
  )
  try
    while running.get() do Thread.sleep(25)
  finally terminal.stop()

private def describe(input: TerminalInput): String = input match
  case TerminalInput.KeyEvent(key, modifiers, eventType) =>
    s"key=$key modifiers=$modifiers event=$eventType"
  case TerminalInput.Paste(text)                         => s"paste ${text.length} chars: ${text.take(80)}"
  case TerminalInput.Raw(data)                           =>
    s"raw ${data.toCharArray.map(ch => f"U+${ch.toInt}%04X").mkString(" ")}"
