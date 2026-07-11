package scalatui

import scalatui.terminal.{
  TerminalInput,
  TerminalInputBuffer,
  TerminalInputChunk,
  TerminalRawKind,
  TerminalRawTermination
}

import java.nio.charset.StandardCharsets

object TestInputStreams:
  def parse(value: String): Vector[TerminalInput] =
    val buffer = TerminalInputBuffer()
    value.getBytes(StandardCharsets.UTF_8).grouped(TerminalInputChunk.MaxBytes).flatMap { bytes =>
      buffer.process(TerminalInputChunk(bytes))
    }.toVector ++ buffer.flush()

  def paste(value: String): Vector[TerminalInput] =
    TerminalInput.PasteStart +:
      value.getBytes(StandardCharsets.UTF_8).grouped(TerminalInputChunk.MaxBytes).map { bytes =>
        TerminalInput.PasteChunk(TerminalInputChunk(bytes))
      }.toVector :+ TerminalInput.PasteEnd

  def raw(
      value: String,
      kind: TerminalRawKind = TerminalRawKind.Bytes,
      termination: TerminalRawTermination = TerminalRawTermination.Complete
  ): Vector[TerminalInput] =
    TerminalInput.RawStart(kind) +:
      value.getBytes(StandardCharsets.UTF_8).grouped(TerminalInputChunk.MaxBytes).map { bytes =>
        TerminalInput.RawChunk(TerminalInputChunk(bytes))
      }.toVector :+ TerminalInput.RawEnd(termination)
