# Getting started

[Documentation index](README.md) · [Runnable first app](../README.md#quickstart)

## Install

Version **0.11.0** is published on Maven Central. No GitHub credentials or local publishing are needed. Maintainers can find release details in [Publishing](publishing.md).

### Toolchain compatibility

Scala consumers need **Scala 3.9.0 or a compatible newer compiler**; JVM applications use **JDK 21**. Scala Native uses **0.5.12**. Java/Kotlin applications use the JVM facade and do not need to install a Scala compiler.

Mill is needed only if you choose Mill as your build tool or build this repository. Contributor toolchain pins live in [CONTRIBUTING](../CONTRIBUTING.md#development-setup).

Scala 3.7.x consumers must upgrade to read the published artifacts' TASTy. See [release history](../CHANGELOG.md) for older baselines.

### SBT

Add to `build.sbt`:

```scala
scalaVersion := "3.9.0"

libraryDependencies ++= Seq(
  "io.github.zikolach" %% "siglyph-core" % "0.11.0",
  "io.github.zikolach" %% "siglyph-terminal-jvm" % "0.11.0"
)
```

### Mill

In `build.mill` (place application sources under `app/src/`):

```scala
//| mill-version: 1.3.1
//| mill-jvm-version: temurin:21

import mill.*
import mill.scalalib.*

object app extends ScalaModule {
  def scalaVersion = "3.9.0"

  def mvnDeps = Seq(
    mvn"io.github.zikolach::siglyph-core::0.11.0",
    mvn"io.github.zikolach::siglyph-terminal-jvm::0.11.0"
  )
}
```

### Optional modules

Add `siglyph-markdown`, `siglyph-image` or `siglyph-extras` only when needed. For example, extras uses:

- **SBT:** `"io.github.zikolach" %% "siglyph-extras" % "0.11.0"`
- **Mill:** `mvn"io.github.zikolach::siglyph-extras::0.11.0"`

Optional Native modules are published in **0.11.0**. Add these platform-aware Mill coordinates from a `ScalaNativeModule` in addition to `siglyph-core` and `siglyph-terminal-native`:

```scala
mvn"io.github.zikolach::siglyph-markdown::0.11.0"
mvn"io.github.zikolach::siglyph-image::0.11.0"
mvn"io.github.zikolach::siglyph-extras::0.11.0"
```

### Maven for Java and Kotlin JVM apps

The Java/Kotlin facade is available in **0.11.0**.

```xml
<dependencies>
  <dependency>
    <groupId>io.github.zikolach</groupId>
    <artifactId>siglyph-core_3</artifactId>
    <version>0.11.0</version>
  </dependency>
  <dependency>
    <groupId>io.github.zikolach</groupId>
    <artifactId>siglyph-terminal-jvm_3</artifactId>
    <version>0.11.0</version>
  </dependency>
</dependencies>
```

### Gradle for Java and Kotlin JVM apps

The Java/Kotlin facade is available in **0.11.0**.

In `build.gradle.kts` with your Java/Kotlin plugin configured:

```kotlin
repositories { mavenCentral() }

dependencies {
  implementation("io.github.zikolach:siglyph-core_3:0.11.0")
  implementation("io.github.zikolach:siglyph-terminal-jvm_3:0.11.0")
}
```

Scala JVM applications usually start with `siglyph-core` and `siglyph-terminal-jvm` through Scala build-tool syntax such as `%%` or Mill `::`. Java and Kotlin JVM applications use the concrete JVM artifact IDs `siglyph-core_3` and `siglyph-terminal-jvm_3`, and can use `scalatui.terminal.jvm.interop.SiglyphJvm` for a narrow Java-friendly facade. Scala Native applications use platform-aware coordinates such as `siglyph-core`, `siglyph-terminal-native`, `siglyph-markdown`, `siglyph-image`, and `siglyph-extras`; Mill resolves those to `_native0.5_3` artifacts from `ScalaNativeModule` projects for release **0.11.0**. The Java/Kotlin facade and `keyTester` are JVM-only; Scala Native artifacts remain Scala-focused.


## Scala Native

Use macOS or Linux with Scala CLI, JDK 21 for the build, and a working Clang/Clang++ toolchain and system development libraries. On macOS, install Xcode Command Line Tools (`xcode-select --install`). On Debian/Ubuntu, install `clang`, `libgc-dev` and `libunwind-dev`. See the [Scala Native setup guide](https://scala-native.org/en/stable/user/setup.html) for platform details.

From a checkout:

```bash
scala-cli run examples/scala-cli/hello-native.scala --jvm 21
```

The [complete Native example](../examples/scala-cli/hello-native.scala) pins Scala Native 0.5.12, uses platform-aware dependencies and selects `PosixTerminal`. Its resulting executable does not require a JVM. Like the JVM example, run it in a real terminal; Esc or Ctrl+C exits.

## Java and Kotlin

Use the concrete `_3` artifact names above, not Native artifacts. Follow the [Java/Kotlin examples](jvm-language-examples.md) for the `SiglyphJvm` facade and complete applications.
