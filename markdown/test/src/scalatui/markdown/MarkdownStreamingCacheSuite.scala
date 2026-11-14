package scalatui.markdown

class MarkdownStreamingCacheSuite extends munit.FunSuite:
  private def awaitInvalidation(action: => Unit): Unit =
    val finished = new java.util.concurrent.CountDownLatch(1)
    val worker   = new Thread(() => { action; finished.countDown() })
    worker.setDaemon(true)
    worker.start()
    assert(
      finished.await(2, java.util.concurrent.TimeUnit.SECONDS),
      "callback held renderer/cache lock during invalidation"
    )

  test("parser callbacks can await renderer invalidation"):
    var renderer: BasicMarkdownRenderer = null
    val parser                          = new MarkdownParser:
      def parse(text: String): Either[String, Vector[MarkdownBlock]] =
        awaitInvalidation(renderer.invalidateCaches())
        BasicMarkdownParser.parse(text)
    renderer = BasicMarkdownRenderer(parser)
    assertEquals(renderer.render("body", 20), Vector("body"))
    assertEquals(renderer.retainedCacheUsage, Vector((0, 0L), (0, 0L)))

  test("highlighter callbacks can await component invalidation and read component state"):
    var component: Markdown = null
    val highlighter         = new MarkdownCodeHighlighter:
      def highlight(language: Option[String], code: String): Option[Vector[String]] =
        awaitInvalidation(component.invalidate())
        assert(component.text.nonEmpty)
        Some(Vector(code))
    val renderer            =
      BasicMarkdownRenderer(options = MarkdownRenderOptions(highlighter = Some(highlighter)))
    component = Markdown("```scala\nx\n```", renderer)
    component.render(20)
    assertEquals(renderer.retainedCacheUsage, Vector((0, 0L), (0, 0L)))

  test("concurrent same-key misses retain exactly one entry and one byte charge"):
    val entered     = new java.util.concurrent.CountDownLatch(2)
    val release     = new java.util.concurrent.CountDownLatch(1)
    val failures    = new java.util.concurrent.atomic.AtomicReference[Throwable]()
    val finished    = new java.util.concurrent.CountDownLatch(2)
    val highlighter = new MarkdownCodeHighlighter:
      def highlight(language: Option[String], code: String): Option[Vector[String]] =
        entered.countDown()
        val released = release.await(5, java.util.concurrent.TimeUnit.SECONDS)
        assert(released)
        Some(Vector(code))
    val renderer    =
      BasicMarkdownRenderer(options = MarkdownRenderOptions(highlighter = Some(highlighter)))
    val source      = "```scala\nx\n```"
    val cold        = BasicMarkdownRenderer()
    val expected    = cold.render(source, 20)
    val workers     = Vector.fill(2)(new Thread(() =>
      try
        val actual = renderer.render(source, 20)
        assertEquals(actual, expected)
      catch case error: Throwable => failures.set(error)
      finally finished.countDown()
    ))
    workers.foreach { worker => worker.setDaemon(true); worker.start() }
    val overlapping = try entered.await(2, java.util.concurrent.TimeUnit.SECONDS)
    finally release.countDown()
    val completed   = finished.await(5, java.util.concurrent.TimeUnit.SECONDS)
    assert(overlapping, "same-key computations must overlap")
    assert(completed)
    Option(failures.get()).foreach(throw _)
    assertEquals(renderer.retainedCacheUsage, cold.retainedCacheUsage)
    assertEquals(renderer.render(source, 20), expected)
    assertEquals(renderer.retainedCacheUsage, cold.retainedCacheUsage)

  test("theme callbacks can invalidate without publishing their stale block"):
    var renderer: BasicMarkdownRenderer = null
    renderer = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        theme = MarkdownTheme(paragraph = text =>
          awaitInvalidation(renderer.invalidateCaches())
          text)
      )
    )
    assertEquals(renderer.render("body", 20), Vector("body"))
    assertEquals(renderer.retainedCacheUsage, Vector((0, 0L), (0, 0L)))

  test("append reparses but only styles and wraps new blocks; resize reuses highlighting"):
    var parses      = 0
    var paragraphs  = 0
    var highlights  = 0
    val parser      = new MarkdownParser:
      def parse(text: String): Either[String, Vector[MarkdownBlock]] =
        parses += 1
        BasicMarkdownParser.parse(text)
    val highlighter = new MarkdownCodeHighlighter:
      def highlight(language: Option[String], code: String): Option[Vector[String]] =
        highlights += 1
        Some(Vector(code))
    val renderer    = BasicMarkdownRenderer(
      parser,
      MarkdownRenderOptions(
        theme = MarkdownTheme(paragraph = text => { paragraphs += 1; text }),
        highlighter = Some(highlighter)
      )
    )
    val source      = "stable paragraph\n\n```scala\nval x = 1\n```"
    val component   = Markdown(source, renderer)
    component.render(40)
    component.render(40)
    assertEquals((parses, paragraphs, highlights), (1, 1, 1))
    component.text = source + "\n\nnew paragraph"
    component.render(40)
    assertEquals((parses, paragraphs, highlights), (2, 2, 1))
    component.render(20)
    assertEquals((parses, paragraphs, highlights), (3, 4, 1))
    component.invalidate()
    component.render(20)
    assertEquals((parses, paragraphs, highlights), (4, 6, 2))
    renderer.invalidateCaches()
    component.render(20)
    assertEquals((parses, paragraphs, highlights), (5, 8, 3))

  test("explicit invalidation refreshes mutable theme and context reattachment"):
    var prefix    = "old "
    var calls     = 0
    val renderer  = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        theme = MarkdownTheme(paragraph = text => { calls += 1; prefix + text })
      )
    )
    val component = Markdown("body", renderer)
    val old       = component.render(30)
    prefix = "new "
    component.invalidate()
    val updated   = component.render(30)
    assertNotEquals(updated, old)
    assertEquals(calls, 2)
    component.tuiContext_=(None)
    component.render(30)
    assertEquals(calls, 3)

  test("spacing, changed tails, and reference-like source match cold rendering"):
    val renderer = BasicMarkdownRenderer()
    val sources  = Vector(
      "first",
      "first\n\nsecond",
      "first\n\nsecond changed",
      "- one\n- two",
      "- one\n\n- two",
      "- one\n\n\n- two",
      "[label][ref]\n\n[ref]: https://one.test",
      "[label][ref]\n\n[ref]: https://two.test",
      "```scala\nx",
      "```scala\nxy\n```"
    )
    for source <- sources; width <- Vector(30, 7, 1, 0) do
      assertEquals(renderer.render(source, width), BasicMarkdownRenderer().render(source, width))

  test("entry eviction and oversized payload bypass are deterministic and never stale"):
    var highlights                 = 0
    val highlighter                = new MarkdownCodeHighlighter:
      def highlight(language: Option[String], code: String): Option[Vector[String]] =
        highlights += 1
        // Retain full source keys, but avoid turning this budget test into a wrapping benchmark.
        Some(Vector(code.take(8)))
    val renderer                   =
      BasicMarkdownRenderer(options = MarkdownRenderOptions(highlighter = Some(highlighter)))
    def code(index: Int): String   = s"```scala\n$index\n```"
    val first                      = renderer.render(code(0), 20)
    (1 to 256).foreach(index => renderer.render(code(index), 20))
    assertEquals(highlights, 257)
    assertEquals(renderer.render(code(0), 20), first)
    assertEquals(highlights, 258)
    val huge                       = "```\n" + "x".repeat(530000) + "\n```"
    renderer.render(huge, 20)
    renderer.render(huge, 20)
    assertEquals(highlights, 260)
    renderer.invalidateCaches()
    def medium(index: Int): String = "```\n" + index + "y".repeat(20000) + "\n```"
    val mediumFirst                = renderer.render(medium(0), 20)
    (1 to 40).foreach(index => renderer.render(medium(index), 20))
    val before                     = highlights
    assertEquals(renderer.render(medium(0), 20), mediumFirst)
    assertEquals(highlights, before + 1)
    assert(renderer.retainedCacheUsage.forall { (entries, bytes) =>
      entries <= 256 && bytes <= 1024L * 1024L
    })

    var outputCalls = 0
    val outputHeavy = BasicMarkdownRenderer(options =
      MarkdownRenderOptions(
        highlighter = Some(new MarkdownCodeHighlighter:
          def highlight(language: Option[String], code: String): Option[Vector[String]] =
            outputCalls += 1
            // Structural accounting exceeds 1 MiB without projecting a million glyphs.
            Some(Vector.fill(17000)("")))
      )
    )
    outputHeavy.render("```scala\nsmall\n```", 20)
    outputHeavy.render("```scala\nsmall\n```", 20)
    assertEquals(outputCalls, 2)
    assertEquals(outputHeavy.retainedCacheUsage, Vector((0, 0L), (0, 0L)))
