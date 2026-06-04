package scalatui.terminal

/** Backend abstraction for terminal lifecycle, input, output, and dimensions. */
trait Terminal:
  def start(onInput: TerminalInput => Unit, onResize: () => Unit): Unit
  def stop(): Unit

  def write(data: String): Unit

  def columns: Int
  def rows: Int

  def moveBy(lines: Int): Unit
  def hideCursor(): Unit
  def showCursor(): Unit
  def clearLine(): Unit
  def clearFromCursor(): Unit
  def clearScreen(): Unit
