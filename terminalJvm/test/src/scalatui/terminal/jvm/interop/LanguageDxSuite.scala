package scalatui.terminal.jvm.interop

import java.nio.file.{Files, Path}
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import scala.jdk.CollectionConverters.*
import scalatui.scheduling.ManualTaskScheduler

final class LanguageDxSuite extends munit.FunSuite:
  private def root: Path =
    var path = Path.of(System.getProperty("user.dir")).toAbsolutePath
    while !Files.exists(path.resolve("build.mill")) && Option(path.getParent).nonEmpty do
      path = path.getParent
    path

  test("Java candidate consumer compiles and runs"):
    withOutput { output =>
      val compiler = ToolProvider.getSystemJavaCompiler
      assert(compiler ne null)
      val result   = compiler.run(
        null,
        null,
        null,
        "-classpath",
        System.getProperty("java.class.path"),
        "-d",
        output.toString,
        root.resolve("examples/language-dx/java/DxApp.java").toString
      )
      assertEquals(result, 0)
      execute(output, "DxApp")
    }

  test("optional Kotlin receiver DSL and consumer compile and run"):
    withOutput { output =>
      val command = List(
        Path.of(System.getProperty("java.home"), "bin", "java").toString,
        "-cp",
        System.getProperty("java.class.path"),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib",
        "-no-reflect",
        "-classpath",
        System.getProperty("java.class.path"),
        "-d",
        output.toString,
        root.resolve("examples/language-dx/kotlin/SiglyphDsl.kt").toString,
        root.resolve("examples/language-dx/kotlin/DxApp.kt").toString
      )
      val log     = output.resolve("compiler.log")
      val process =
        ProcessBuilder(command.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start()
      if !process.waitFor(60, TimeUnit.SECONDS) then
        process.destroyForcibly()
        fail("Kotlin compilation timed out")
      assertEquals(process.exitValue(), 0, Files.readString(log))
      execute(output, "DxAppKt")
    }

  test("copied rows, boundary validation and managed cleanup preserve failures"):
    val dx       = JvmUi()
    val rows     = new java.util.ArrayList[String]()
    rows.add("a")
    val builder  = dx.table[String](value => value).rows(rows).column("Name", 8, value => value)
    rows.clear()
    assertEquals(builder.build().data.size, 1)
    intercept[NullPointerException](
      builder.rows(java.util.Arrays.asList(null.asInstanceOf[String]))
    )
    intercept[IllegalArgumentException](builder.rows(java.util.List.of("a", "a")))
    val clock    = ManualTaskScheduler()
    val expected = new IllegalStateException("body failed")
    val actual   = intercept[IllegalStateException] {
      dx.test(dx.app(), 20, 4, clock, _ => throw expected)
    }
    assert(actual eq expected)
    assert(clock.isClosed)

  test("theme snapshots copy Java maps and scheduler cleanup keeps executor borrowed"):
    val dx       = JvmUi()
    val styles   = new java.util.HashMap[scalatui.theme.SemanticRole, scalatui.theme.SemanticStyle]()
    styles.put(scalatui.theme.SemanticRole.Warning, scalatui.theme.SemanticStyle(bold = true))
    val builder  = dx.theme().overrides(styles)
    val snapshot = builder.build()
    styles.clear()
    builder.preset("plain")
    assert(snapshot.style(scalatui.theme.SemanticRole.Warning).bold)
    val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    try
      val scheduler = JvmScheduler(executor, 2, _ => ())
      scheduler.close()
      assert(!executor.isShutdown)
    finally executor.shutdownNow()

  test("managed assertion failures contain screen diagnostics and cleanup cancels scopes"):
    val dx    = JvmUi()
    val clock = ManualTaskScheduler()
    val error = intercept[AssertionError] {
      dx.test(
        dx.app().add(scalatui.components.Text("actual")),
        20,
        4,
        clock,
        session => {
          val scope = session.openScope(1, 1)
          dx.schedule(scope, clock, 100L, () => fail("stale callback"))
          session.assertContains("missing")
        }
      )
    }
    assert(error.getMessage.contains("missing"))
    assert(clock.isClosed)
    assertEquals(clock.pendingCount, 0)

  test("managed cleanup attaches leaked-clock failure to the original body failure"):
    val clock    = ManualTaskScheduler()
    val expected = new IllegalStateException("primary")
    val actual   = intercept[IllegalStateException] {
      JvmUi().test(
        JvmUi().app(),
        20,
        4,
        clock,
        _ => {
          clock.schedule(100L) { () }
          throw expected
        }
      )
    }
    assert(actual eq expected)
    assert(actual.getSuppressed.nonEmpty)
    assert(clock.isClosed)

  test("managed run restores an injected terminal when setup fails"):
    val terminal = scalatui.terminal.VirtualTerminal()
    val expected = new IllegalArgumentException("setup failed")
    val actual   = intercept[IllegalArgumentException] {
      JvmUi().run(JvmUi().app(), terminal, _ => throw expected)
    }
    assert(actual eq expected)
    assert(!terminal.isRunning)

  private def execute(output: Path, name: String): Unit =
    val loader = URLClassLoader(Array(output.toUri.toURL), getClass.getClassLoader)
    try loader.loadClass(name).getMethod(
        "main",
        classOf[Array[String]]
      ).invoke(null, Array.empty[String])
    finally loader.close()

  private def withOutput(body: Path => Unit): Unit =
    val output = Files.createTempDirectory("siglyph-dx")
    try body(output)
    finally
      val paths = Files.walk(output)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.delete)
      finally paths.close()
