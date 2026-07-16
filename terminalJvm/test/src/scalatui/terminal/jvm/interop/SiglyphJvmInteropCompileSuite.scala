package scalatui.terminal.jvm.interop

import scalatui.syntax.Equality.*

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

  test("Java can call Base64ImagePayload static factories"):
    compileJava(Base64PayloadPositive)

  test("Java cannot bypass Base64ImagePayload construction or finality"):
    Vector(
      "new Base64ImagePayload(\"TQ==\");",
      "new Base64ImagePayload();",
      "class InvalidPayload extends Base64ImagePayload {}"
    ).foreach(source => compileJavaFailure(javaFailureSource(source)))

  test("Java cannot construct arbitrary raw terminal controls"):
    compileJavaFailure(RawTerminalControlConstruction)

  test("Java cannot implement the removed line-vector component render contract"):
    compileJavaFailure(LegacyComponentRender)

  test("JVM payload bytecode keeps construction private and protocol APIs typed"):
    val payloadClass = classOf[scalatui.terminal.Base64ImagePayload]
    val constructors = payloadClass.getDeclaredConstructors.toVector
    assert(!payloadClass.isInterface)
    assert(java.lang.reflect.Modifier.isFinal(payloadClass.getModifiers))
    assert(payloadClass.getConstructors.isEmpty)
    assertEquals(constructors.length, 1)
    assertEquals(constructors.head.getParameterTypes.toVector, Vector(classOf[String]))
    assert(java.lang.reflect.Modifier.isPrivate(constructors.head.getModifiers))
    intercept[IllegalAccessException](constructors.head.newInstance("TQ=="))

    val valueField     = payloadClass.getDeclaredField("value")
    assert(java.lang.reflect.Modifier.isPrivate(valueField.getModifiers))
    assert(java.lang.reflect.Modifier.isFinal(valueField.getModifiers))
    val forbiddenNames = Set("apply", "copy", "raw", "setValue", "value_=")
    assert(payloadClass.getMethods.forall(method => !forbiddenNames.contains(method.getName)))

    val protocolClass = Class.forName("scalatui.terminal.TerminalImageProtocol$")
    Vector("renderBase64Image", "encodeKitty", "encodeITerm2").foreach { name =>
      val overloads = protocolClass.getMethods.filter(_.getName === name)
      assert(overloads.nonEmpty, name)
      assert(overloads.forall(_.getParameterTypes.head === payloadClass), name)
      assert(overloads.forall(!_.getParameterTypes.contains(classOf[String])), name)
    }

    val controlClass = classOf[scalatui.terminal.TerminalRenderControl]
    assert(!controlClass.isInterface)
    assert(controlClass.getConstructors.isEmpty)
    assert(controlClass.getDeclaredConstructors.forall(constructor =>
      java.lang.reflect.Modifier.isPrivate(constructor.getModifiers)
    ))

  test("Kotlin smoke source compiles through JVM interop facade"):
    assert(!KotlinSmoke.contains("$lessinit$greater$default"))
    assert(!KotlinSmoke.contains("scala.Function1"))
    compileKotlin(KotlinSmoke)

  private def compileJava(source: String): Unit =
    assertJavaCompilation(source, expectedSuccess = true)

  private def compileJavaFailure(source: String): Unit =
    assertJavaCompilation(source, expectedSuccess = false)

  private def assertJavaCompilation(source: String, expectedSuccess: Boolean): Unit =
    val compiler = ToolProvider.getSystemJavaCompiler
    assert(compiler ne null, "Java smoke compilation requires a JDK with javac")
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
        if expectedSuccess then assert(compiled, renderDiagnostics(diagnostics))
        else assert(!compiled, "Expected Java compilation to fail")
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
        val children = Files.list(path)
        try children.iterator().asScala.foreach(child => deleteRecursively(child))
        finally children.close()
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

  private val Base64PayloadPositive = """
import scalatui.terminal.Base64ImagePayload;

final class JavaInteropSmoke {
  void use() {
    Base64ImagePayload.from("TQ==");
    Base64ImagePayload.encode(new byte[] { 77 });
  }
}
"""

  private val RawTerminalControlConstruction = """
import scalatui.terminal.TerminalRenderControl;

final class JavaInteropSmoke {
  TerminalRenderControl control = new TerminalRenderControl();
}
"""

  private val LegacyComponentRender = """
import scalatui.core.Component;
import scala.collection.immutable.Vector;

abstract class JavaInteropSmoke implements Component {
  public Vector<String> render(int width) {
    return Vector.empty();
  }
}
"""

  private def javaFailureSource(statement: String): String = s"""
import scalatui.terminal.Base64ImagePayload;

class JavaInteropSmoke {
  void use() {
    $statement
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
