package scalatui.terminal.jvm.interop

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import javax.tools.{DiagnosticCollector, JavaFileObject, ToolProvider}

final class SiglyphJvmInteropCompileSuite extends munit.FunSuite:

  test("Java smoke source compiles through JVM interop facade"):
    assert(!JavaSmoke.contains("$lessinit$greater$default"))
    compileJava(JavaSmoke)

  test("Kotlin smoke source compiles through JVM interop facade"):
    assert(!KotlinSmoke.contains("scala.Function1"))
    compileKotlin(KotlinSmoke)

  private def compileJava(source: String): Unit =
    val compiler = ToolProvider.getSystemJavaCompiler
    assert(compiler != null, "Java smoke compilation requires a JDK with javac")
    withTempDir("siglyph-java-interop") { dir =>
      val sourceFile  = dir.resolve("JavaInteropSmoke.java")
      val outputDir   = Files.createDirectory(dir.resolve("classes"))
      Files.writeString(sourceFile, source, StandardCharsets.UTF_8)
      val diagnostics = DiagnosticCollector[JavaFileObject]()
      val fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)
      try
        val units    = fileManager.getJavaFileObjectsFromFiles(List(sourceFile.toFile).asJava)
        val options  = List("-classpath", classpath, "-d", outputDir.toString).asJava
        val compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
        assert(compiled, renderDiagnostics(diagnostics))
      finally fileManager.close()
    }

  private def compileKotlin(source: String): Unit =
    withTempDir("siglyph-kotlin-interop") { dir =>
      val sourceFile = dir.resolve("KotlinInteropSmoke.kt")
      val outputDir  = Files.createDirectory(dir.resolve("classes"))
      val outputFile = dir.resolve("kotlinc.out")
      Files.writeString(sourceFile, source, StandardCharsets.UTF_8)
      val command    = List(
        javaBinary.toString,
        "-cp",
        classpath,
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib",
        "-no-reflect",
        "-classpath",
        classpath,
        "-d",
        outputDir.toString,
        sourceFile.toString
      )
      val process    = ProcessBuilder(command.asJava)
        .redirectErrorStream(true)
        .redirectOutput(outputFile.toFile)
        .start()
      val completed  = process.waitFor(60.seconds.toSeconds, TimeUnit.SECONDS)
      if !completed then
        process.destroyForcibly()
        fail("Kotlin smoke compilation timed out")
      val output     = Files.readString(outputFile, StandardCharsets.UTF_8)
      assertEquals(process.exitValue(), 0, output)
    }

  private def withTempDir(prefix: String)(use: Path => Unit): Unit =
    val dir = Files.createTempDirectory(prefix)
    try use(dir)
    finally deleteRecursively(dir)

  private def deleteRecursively(path: Path): Unit =
    if Files.exists(path) then
      if Files.isDirectory(path) then
        Files.list(path).forEach(child => deleteRecursively(child))
      Files.deleteIfExists(path)

  private def classpath: String = System.getProperty("java.class.path")

  private def javaBinary: Path =
    val executable =
      if System.getProperty("os.name").toLowerCase.contains("win") then "java.exe" else "java"
    Path.of(System.getProperty("java.home"), "bin", executable)

  private def renderDiagnostics(diagnostics: DiagnosticCollector[JavaFileObject]): String =
    diagnostics.getDiagnostics.asScala
      .map(diagnostic =>
        s"${diagnostic.getKind}: ${diagnostic.getSource}:${diagnostic.getLineNumber}: ${diagnostic.getMessage(null)}"
      )
      .mkString(System.lineSeparator())

  private val JavaSmoke = """
import scalatui.components.Input;
import scalatui.core.TUI;
import scalatui.terminal.jvm.interop.SiglyphJvm;

public final class JavaInteropSmoke {
  public static void main(String[] args) {
    SiglyphJvm siglyph = new SiglyphJvm();
    TUI tui = siglyph.createTui();
    Input input = siglyph.createInput();
    siglyph.onSubmit(input, value -> {
      input.setValue("");
      siglyph.addChild(tui, siglyph.createText("You typed: " + value));
    });
    siglyph.addChild(tui, siglyph.createText("siglyph Java demo"));
    siglyph.addChild(tui, input);
    siglyph.setFocus(tui, input);
    siglyph.run(tui);
  }
}
"""

  private val KotlinSmoke = """
import scalatui.terminal.jvm.interop.SiglyphJvm

fun main() {
  val siglyph = SiglyphJvm()
  val tui = siglyph.createTui()
  val input = siglyph.createInput()
  siglyph.onSubmit(input) { value ->
    input.setValue("")
    siglyph.addChild(tui, siglyph.createText("You typed: $value"))
  }
  siglyph.addChild(tui, siglyph.createText("siglyph Kotlin demo"))
  siglyph.addChild(tui, input)
  siglyph.setFocus(tui, input)
  siglyph.run(tui)
}
"""
