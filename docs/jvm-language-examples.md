# JVM language examples

This page shows the same basic terminal-backed siglyph app in Scala, Java, and Kotlin.

GitHub Markdown does not provide native code tabs. This page uses language links instead:
[Scala](#basic-tui-scala) · [Java](#basic-tui-java) · [Kotlin](#basic-tui-kotlin)

Java and Kotlin support applies to JVM artifacts only through `scalatui.terminal.jvm.interop.SiglyphJvm`. Scala Native artifacts remain Scala-focused.

## Basic TUI

The example creates a JVM `stty` terminal-backed TUI with text, input, focus, and submit handling.

### Basic TUI: Scala

```scala
import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.jvm.SttyTerminal

@main def helloTui(): Unit =
  val tui = TUI(SttyTerminal())
  val input = Input()
  input.onSubmit = value =>
    input.setValue("")
    tui.addChild(Text(s"You typed: $value"))

  tui.addChild(Text("siglyph demo — type and press Enter"))
  tui.addChild(input)
  tui.setFocus(input)
  tui.run()
```

### Basic TUI: Java

```java
import scalatui.components.Input;
import scalatui.core.TUI;
import scalatui.terminal.jvm.interop.SiglyphJvm;

public final class HelloTui {
  public static void main(String[] args) {
    SiglyphJvm siglyph = new SiglyphJvm();
    TUI tui = siglyph.createTui();
    Input input = siglyph.createInput();

    siglyph.onSubmit(input, value -> {
      input.setValue("");
      siglyph.addChild(tui, siglyph.createText("You typed: " + value));
    });

    siglyph.addChild(tui, siglyph.createText("siglyph demo — type and press Enter"));
    siglyph.addChild(tui, input);
    siglyph.setFocus(tui, input);
    siglyph.run(tui);
  }
}
```

### Basic TUI: Kotlin

```kotlin
import scalatui.terminal.jvm.interop.SiglyphJvm

fun main() {
  val siglyph = SiglyphJvm()
  val tui = siglyph.createTui()
  val input = siglyph.createInput()

  siglyph.onSubmit(input) { value ->
    input.setValue("")
    siglyph.addChild(tui, siglyph.createText("You typed: $value"))
  }

  siglyph.addChild(tui, siglyph.createText("siglyph demo — type and press Enter"))
  siglyph.addChild(tui, input)
  siglyph.setFocus(tui, input)
  siglyph.run(tui)
}
```
