package scalatui.demo

import scalatui.terminal.TerminalInput
import scalatui.terminal.jvm.SttyTerminal

import java.util.concurrent.atomic.AtomicBoolean

@main def keyTester(): Unit =
  val running  = AtomicBoolean(true)
  val terminal = SttyTerminal()
  println("siglyph key tester — press Escape to exit")
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
  case TerminalInput.PasteStart                          => "paste start"
  case TerminalInput.PasteChunk(chunk)                   => s"paste chunk ${chunk.length} bytes"
  case TerminalInput.PasteEnd                            => "paste end"
  case TerminalInput.RawStart(kind)                      => s"raw start kind=$kind"
  case TerminalInput.RawChunk(chunk)                     =>
    s"raw chunk ${chunk.toArray.map(byte => f"0x${byte & 0xff}%02X").mkString(" ")}"
  case TerminalInput.RawEnd(termination)                 => s"raw end termination=$termination"
  case TerminalInput.Mouse(action, row, col, modifiers)  =>
    s"mouse action=$action row=$row col=$col modifiers=$modifiers"
