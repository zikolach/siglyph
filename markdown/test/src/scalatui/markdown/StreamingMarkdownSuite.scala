package scalatui.markdown

import scalatui.ansi.Ansi
import scalatui.components.ScrollView
import scalatui.core.{
  Component, ComponentRender, LayoutBounds, MouseEvent, MouseEventLocation, ViewportLayoutEngine,
  ViewportRangeRenderer
}
import scalatui.terminal.{KeyModifiers, MouseButton}

class StreamingMarkdownSuite extends munit.FunSuite:
  test("viewport interactions validate only visible rows and append retains stable highlights"):
    var highlights                                       = 0
    val highlighter                                      = new MarkdownCodeHighlighter:
      def highlight(language: Option[String], code: String): Option[Vector[String]] =
        highlights += 1
        None
    val stream                                           = StreamingMarkdown(options = MarkdownRenderOptions(highlighter = Some(highlighter)))
    stream.append((1 to 30).map(i => s"```scala\nval item$i = $i\n```\n\n").mkString)
    val ranged                                           = stream.asInstanceOf[ViewportRangeRenderer]
    assertEquals(ranged.renderRange(60, 0, 0), ComponentRender.empty)
    assertEquals(ranged.renderRange(60, 0, -1), ComponentRender.empty)
    assertEquals(ranged.renderRange(0, 0, 8), ComponentRender.empty)
    assertEquals(stream.stats.parseCalls, 0L)
    assertEquals(highlights, 0)
    var fullRenders                                      = 0
    var rangeRows                                        = 0
    val counted                                          = new Component with ViewportRangeRenderer:
      override def invalidate(): Unit                                            = stream.invalidate()
      def render(width: Int): ComponentRender                                    =
        fullRenders += 1
        stream.render(width)
      def contentExtent(width: Int): Int                                         = ranged.contentExtent(width)
      def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        val result = ranged.renderRange(width, startRow, rowCount)
        rangeRows += result.lines.length
        result
    val view                                             = ScrollView(counted, primary = true)
    ViewportLayoutEngine.layout(view, 60, 8)
    val parsed                                           = stream.stats
    var fallbackRows                                     = 0
    val fallback                                         = new Component:
      def render(width: Int): ComponentRender =
        val result = stream.render(width)
        fallbackRows += result.lines.length
        result
    val fallbackView                                     = ScrollView(fallback)
    ViewportLayoutEngine.layout(fallbackView, 60, 8)
    fallbackRows = 0
    (1 to 20).foreach { _ =>
      fallbackView.scrollBy(1)
      ViewportLayoutEngine.layout(fallbackView, 60, 8)
    }
    assertEquals(fallbackRows, 20 * 119)
    rangeRows = 0
    (1 to 20).foreach { _ =>
      view.scrollBy(1)
      ViewportLayoutEngine.layout(view, 60, 8)
    }
    assertEquals(fullRenders, 0)
    assertEquals(rangeRows, 20 * 8)
    assertEquals(stream.stats, parsed)
    assertEquals(highlights, 30)
    def location(row: Int, col: Int): MouseEventLocation =
      MouseEventLocation(row, col, row, col, LayoutBounds(0, 0, 60, 8))
    view.handleMouseEvent(MouseEvent.Press(location(1, 0), MouseButton.Left, KeyModifiers.empty))
    assert(view.lastSelectionGraphemeScans > 0)
    rangeRows = 0
    (1 to 20).foreach { _ =>
      view.handleMouseEvent(MouseEvent.Drag(location(4, 5), MouseButton.Left, KeyModifiers.empty))
      ViewportLayoutEngine.layout(view, 60, 8)
    }
    assertEquals(rangeRows, 20 * 8)
    assertEquals(stream.stats, parsed)
    assert(view.selection.nonEmpty)
    stream.append("tail")
    view.invalidateSearchDocument(invalidateChild = false)
    ViewportLayoutEngine.layout(view, 60, 8)
    assertEquals(highlights, 30)
    assertEquals(view.offset, 20)
    view.invalidateSearchDocument()
    ViewportLayoutEngine.layout(view, 60, 8)
    assertEquals(highlights, 60)
    val wideScans                                        = view.lastSelectionGraphemeScans
    ViewportLayoutEngine.layout(view, 6, 8)
    assert(view.lastSelectionGraphemeScans < wideScans)
    assert(view.selection.nonEmpty)
    assertEquals(highlights, 60)

  private def lines(stream: StreamingMarkdown): Vector[String] =
    stream.render(80).lines.map(Ansi.strip)

  test("bounded admission is atomic and finish/reset define response lifecycle"):
    val stream = StreamingMarkdown(maxSourceUnits = 4)
    assert(stream.append("ab"))
    assert(!stream.append("cde"))
    assertEquals(stream.text, "ab")
    assert(stream.append("cd"))
    stream.finish()
    assert(!stream.append(""))
    assertEquals(lines(stream), Vector("abcd"))
    stream.reset()
    assertEquals(stream.text, "")
    assertEquals(stream.stats, StreamingMarkdownStats(0L, 0L, 0L, 0L, 0L))
    assert(stream.append("new"))

  test("simple unfinished emphasis and links have provisional output without invented source"):
    val options = MarkdownRenderOptions(theme = MarkdownTheme(strong = text => s"<$text>"))
    val stream  = StreamingMarkdown(options = options)
    stream.append("**hel")
    assertEquals(lines(stream), Vector("<hel>"))
    stream.append("lo** [site](https://exam")
    assertEquals(lines(stream), Vector("<hello> site"))
    assert(stream.text.endsWith("https://exam"))
    stream.append("ple.org)")
    assertEquals(
      stream.render(80).lines,
      BasicMarkdownRenderer(options = options).render(stream.text, 80)
    )
    stream.finish()
    assertEquals(
      stream.render(80).lines,
      BasicMarkdownRenderer(options = options).render(stream.text, 80)
    )

  test("finish restores literal incomplete syntax"):
    val stream = StreamingMarkdown()
    stream.append("[unfinished](https://secret")
    assertEquals(lines(stream), Vector("unfinished"))
    stream.finish()
    assertEquals(stream.render(80).lines, BasicMarkdownRenderer().render(stream.text, 80))

  test("completed paragraphs have deterministic parse and boundary scan reuse"):
    val stream = StreamingMarkdown()
    stream.append("one\n\n")
    lines(stream)
    assertEquals(stream.stats, StreamingMarkdownStats(2L, 5L, 5L, 0L, 0L))
    stream.append("two")
    lines(stream)
    assertEquals(stream.stats, StreamingMarkdownStats(3L, 8L, 8L, 1L, 0L))
    stream.render(40)
    stream.invalidate()
    stream.render(40)
    assertEquals(stream.stats, StreamingMarkdownStats(3L, 8L, 8L, 1L, 0L))
    stream.append("\n\nthree")
    lines(stream)
    assertEquals(stream.stats, StreamingMarkdownStats(5L, 18L, 18L, 2L, 0L))

  test(
    "all chunk splits preserve final cold rendering including malformed syntax and UTF-16 splits"
  ):
    val sources = Vector(
      "one\r\n\r\ntwo\r\n\r\nthree",
      "intro\n\n```scala\nx * y [z](url)\n```\n\nafter",
      "intro\n\n- first\n\n\n- second\n\nend",
      "intro\n\n> first\n\n> second\n\nend",
      "intro\n\n    first\n\n    second\nend",
      "intro\n\n| a | b |\n| --- | --- |\n| c | d |\n\nend",
      "**hello** [world](https://example.org) e\u0301 🙂 界",
      "```scala\nunfinished\n\n",
      "[label](unfinished **bold"
    )
    sources.foreach { source =>
      (0 to source.length).foreach { split =>
        val stream = StreamingMarkdown()
        assert(stream.append(source.take(split)))
        stream.render(19)
        assert(stream.append(source.drop(split)))
        stream.render(19)
        stream.finish()
        assertEquals(
          stream.render(19).lines,
          BasicMarkdownRenderer().render(source, 19),
          s"split $split: $source"
        )
      }
    }

  test("ambiguous suffix and CRLF have cold equivalence at every plain-text character prefix"):
    val sources = Vector(
      "one\r\n\r\ntwo\r\n\r\nthree",
      "prefix\n\n- first\n\n\n- second\n\nend",
      "prefix\n\n> first\n\n> second\n\nend",
      "prefix\n\n    first\n\n    second\nend",
      "prefix\n\n| a | b |\n| --- | --- |\n| c | d |\n\nend"
    )
    sources.foreach { source =>
      val stream = StreamingMarkdown()
      source.foreach { char =>
        stream.append(char.toString)
        assertEquals(
          stream.render(19).lines,
          BasicMarkdownRenderer().render(stream.text, 19),
          stream.text
        )
      }
    }

  test("fence bodies remain literal throughout streaming"):
    val stream = StreamingMarkdown()
    stream.append("```scala\n")
    "**literal [url](pending\n\nnext".foreach { char =>
      stream.append(char.toString)
      assertEquals(stream.render(30).lines, BasicMarkdownRenderer().render(stream.text, 30))
    }
    stream.append("\n```\n\nafter")
    assertEquals(stream.render(30).lines, BasicMarkdownRenderer().render(stream.text, 30))
    assert(stream.stats.reusedBlocks >= 0)

  test("custom parsers always receive unchanged whole source and skip boundary scanning"):
    var received = Vector.empty[String]
    val parser   = new MarkdownParser:
      def parse(source: String): Either[String, Vector[MarkdownBlock]] =
        received :+= source
        BasicMarkdownParser.parse(source)
    val stream   = StreamingMarkdown(parser = parser)
    Vector("one\n\n", "[tail](", "url)").foreach { chunk =>
      stream.append(chunk)
      assertEquals(stream.render(30).lines, BasicMarkdownRenderer(parser).render(stream.text, 30))
    }
    assertEquals(
      received,
      Vector(
        "one\n\n",
        "one\n\n",
        "one\n\n[tail](",
        "one\n\n[tail](",
        "one\n\n[tail](url)",
        "one\n\n[tail](url)"
      )
    )
    assertEquals(stream.stats.scannedUnits, 0L)
    assertEquals(stream.stats.reusedBlocks, 0L)
    assertEquals(stream.stats.fallbackParses, 3L)

  test(
    "literal streaming has cold equivalence at every character prefix across grammar transitions"
  ):
    val sources = Vector(
      "one\r\n\r\n**bold\ncontinued**\n\n```scala\nx\n```\n\nend",
      "plain\n\n* item\n\n* next\n\n- [ ] task\n- [x] done",
      "intro\n\n| a | b |\n| --- | --- |\n| **x** | [y](u) |\n\nend",
      "a\n\n```\n`\n``\n```\n\n[link](url) 🙂 e\u0301",
      "# h\n\n---\n\n> quote\n\n> continued\n\n    code\n\n    more"
    )
    sources.foreach { source =>
      val stream = StreamingMarkdown(provisionalInline = false)
      source.foreach { char =>
        stream.append(char.toString)
        assertEquals(
          stream.render(27).lines,
          BasicMarkdownRenderer().render(stream.text, 27),
          stream.text
        )
      }
    }

  test("complete structural and multiline inline forms keep baseline live output"):
    Vector(
      "* item",
      "- [ ] task",
      "- [x] done",
      "[note] trailing text",
      "**bold\ncontinued**",
      "| **cell** | [label](url) |"
    ).foreach { source =>
      val stream = StreamingMarkdown()
      stream.append(source)
      assertEquals(stream.render(40).lines, BasicMarkdownRenderer().render(source, 40), source)
    }

  test("custom parser callback can await parser state inspection without holding checkpoint lock"):
    var stream: StreamingMarkdown = null
    val parser                    = new MarkdownParser:
      def parse(source: String): Either[String, Vector[MarkdownBlock]] =
        val done   = new java.util.concurrent.CountDownLatch(1)
        val worker = new Thread(() => { stream.stats; done.countDown() })
        worker.setDaemon(true)
        worker.start()
        assert(done.await(2L, java.util.concurrent.TimeUnit.SECONDS))
        BasicMarkdownParser.parse(source)
    stream = StreamingMarkdown(parser = parser)
    stream.append("hello")
    assertEquals(lines(stream), Vector("hello"))

  test("custom parser reset during callback cannot publish a stale component cache"):
    var stream: StreamingMarkdown = null
    var resetOnce                 = true
    val parser                    = new MarkdownParser:
      def parse(source: String): Either[String, Vector[MarkdownBlock]] =
        if resetOnce then
          resetOnce = false
          val done   = new java.util.concurrent.CountDownLatch(1)
          val worker = new Thread(() => { stream.reset(); done.countDown() })
          worker.setDaemon(true)
          worker.start()
          assert(done.await(2L, java.util.concurrent.TimeUnit.SECONDS))
        BasicMarkdownParser.parse(source)
    stream = StreamingMarkdown(parser = parser)
    stream.append("old snapshot")
    stream.render(30)
    assertEquals(stream.text, "")
    assertEquals(stream.render(30).lines, Vector.empty)

  test("explicit invalidation recomputes mutable custom parser output"):
    var label  = "first"
    val parser = new MarkdownParser:
      def parse(source: String): Either[String, Vector[MarkdownBlock]] =
        Right(Vector(MarkdownBlock.Paragraph(label)))
    val stream = StreamingMarkdown(parser = parser)
    stream.append("source")
    assertEquals(lines(stream), Vector("first"))
    label = "second"
    stream.invalidate()
    assertEquals(lines(stream), Vector("second"))
    assertEquals(stream.stats.fallbackParses, 2L)

  test(
    "safe paragraph growth scans and parses only new suffix but lists retain worst-case fallback"
  ):
    val stream = StreamingMarkdown(provisionalInline = false)
    val chunk  = "paragraph\n\n"
    (1 to 100).foreach { _ =>
      stream.append(chunk)
      stream.render(40)
    }
    assertEquals(stream.stats.parsedUnits, 100L * chunk.length)
    assertEquals(stream.stats.scannedUnits, 100L * chunk.length)
    assertEquals(stream.stats.reusedBlocks, 4950L)
    val list   = StreamingMarkdown(provisionalInline = false)
    val item   = "- item\n\n"
    (1 to 20).foreach { _ => list.append(item); list.render(40) }
    assertEquals(list.stats.parsedUnits, 210L * item.length)
    assertEquals(list.stats.reusedBlocks, 0L)
