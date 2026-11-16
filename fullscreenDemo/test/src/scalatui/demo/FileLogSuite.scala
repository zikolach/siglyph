package scalatui.demo

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scalatui.components.Text
import scalatui.core.TUI
import scalatui.testing.TUITestDriver

class FileLogSuite extends munit.FunSuite:
  private def withFile(test: Path => Unit): Unit =
    val directory = Files.createTempDirectory("siglyph-follow-")
    val file      = directory.resolve("events.log")
    Files.write(file, Array.emptyByteArray)
    try test(file)
    finally
      Files.deleteIfExists(file)
      Files.deleteIfExists(directory.resolve("old.log"))
      Files.deleteIfExists(directory)

  private def write(path: Path, value: String, append: Boolean = false): Unit =
    val options = if append then Array(StandardOpenOption.APPEND)
    else Array(StandardOpenOption.TRUNCATE_EXISTING)
    Files.write(path, value.getBytes(StandardCharsets.UTF_8), options*)
    ()

  test("UTF-8 and CRLF may span polls; incomplete EOF waits for newline"):
    withFile { path =>
      val reader    = FileLogReader(path, maxReadBytes = 2)
      write(path, "界🐱\r\npartial")
      val lines     = (0 until 12).flatMap(_ => reader.poll().toOption.toVector.flatMap(_.lines))
      assertEquals(lines.map(_.text).toVector, Vector("界🐱"))
      write(path, " tail\n", append = true)
      val completed = (0 until 8).flatMap(_ => reader.poll().toOption.toVector.flatMap(_.lines))
      assertEquals(completed.map(_.text).toVector, Vector("partial tail"))
    }

  test("byte/line limits retain offsets and oversized lines remain bounded"):
    withFile { path =>
      val reader = FileLogReader(path, maxReadBytes = 16, maxLineBytes = 4, maxLines = 1)
      write(path, "abcdefgh\nx\ny\n")
      val first  = reader.poll().toOption.get
      assertEquals(first.lines, Vector(FileLogLine("abcd", true)))
      assertEquals(first.offset, 9L)
      assertEquals(reader.poll().toOption.get.lines.map(_.text), Vector("x"))
      assertEquals(reader.poll().toOption.get.lines.map(_.text), Vector("y"))
      assert(reader.poll().toOption.get.lines.isEmpty)
    }

  test("observed truncation and replacement reset state with notices"):
    withFile { path =>
      val reader    = FileLogReader(path)
      write(path, "old line\npartial")
      reader.poll()
      write(path, "new\n")
      val truncated = reader.poll().toOption.get
      assertEquals(truncated.lines.map(_.text), Vector("new"))
      assert(truncated.notice.nonEmpty)
      Files.move(path, path.resolveSibling("old.log"))
      Files.write(path, "replacement\n".getBytes(StandardCharsets.UTF_8))
      val rotated   = reader.poll().toOption.get
      assertEquals(rotated.lines.map(_.text), Vector("replacement"))
      assert(rotated.notice.nonEmpty)
    }

  test("errors preserve cursor state and a returning file can recover"):
    withFile { path =>
      val reader = FileLogReader(path)
      write(path, "first\n")
      reader.poll()
      Files.delete(path)
      assert(reader.poll().isLeft)
      Files.write(path, "returned\n".getBytes(StandardCharsets.UTF_8))
      assertEquals(reader.poll().toOption.get.lines.map(_.text), Vector("returned"))
      assert(FileLogReader(path.getParent).poll().isLeft)
    }

  test("malformed UTF-8 is replaced and line state is not unbounded"):
    withFile { path =>
      Files.write(path, Array[Byte](0xc3.toByte, 0x28.toByte, 10.toByte))
      val line = FileLogReader(path).poll().toOption.get.lines.head
      assert(line.text.contains("\ufffd"))
      assert(!line.truncated)
    }

  test("pump preserves one batch under scope backpressure and cancellation prevents delivery"):
    withFile { path =>
      write(path, "one\ntwo\n")
      TUITestDriver.withSession(40, 3)(terminal => TUI.fullscreen(terminal, Text("ready"))) {
        driver =>
          val scope    = driver.tui.openApplicationScope(maxPending = 1)
          var received = Vector.empty[String]
          var paused   = false
          val pump     = FileLogPump(
            FileLogReader(path),
            scope,
            () => !paused,
            report =>
              if paused then false
              else
                received ++= report.toOption.toVector.flatMap(_.lines.map(_.text))
                true
          )
          scope.submit {
            scope.submit { () }
            pump.poll()
          }
          assertEquals(received, Vector.empty)
          write(path, "three\n", append = true)
          pump.poll()
          assertEquals(received, Vector("one", "two"))
          paused = true
          pump.poll()
          assertEquals(received.size, 2)
          paused = false
          pump.poll()
          assertEquals(received, Vector("one", "two", "three"))
          pump.cancel()
          write(path, "late\n", append = true)
          pump.poll()
          assertEquals(received.size, 3)
      }
    }

  test("file model retains bounded data without synthetic records"):
    val model = LogExplorerModel(2)
    assert(model.appendFile(FileLogBatch(
      Vector(FileLogLine("a", false), FileLogLine("b", false), FileLogLine("c", true)),
      None,
      6
    )))
    assertEquals(model.snapshot.retained, 2)
    assertEquals(model.snapshot.evicted, 1L)
    assertEquals(model.snapshot.rows.map(_.service), Vector("file", "file"))
    model.togglePause()
    assert(!model.appendFile(FileLogBatch(Vector(FileLogLine("d", false)), None, 8)))
    assertEquals(model.snapshot.generated, 3L)
