# Getting started

[Documentation index](README.md) · [Runnable first app](../README.md#quickstart)

## Install

Version **0.13.0** is published on Maven Central. No GitHub credentials or local publishing are needed. Maintainers can find release details in [Publishing](publishing.md).

### GitHub Release fallback

For **0.14.0**, use the Maven repository ZIP from the [GitHub Release](https://github.com/zikolach/siglyph/releases/tag/v0.14.0). It contains the ten JVM and Native artifacts and their POMs. The individual JAR downloads do not include dependency metadata. Run these commands in a directory you can keep for as long as your build uses it:

```bash
base=https://github.com/zikolach/siglyph/releases/download/v0.14.0
bundle=siglyph-maven-0.14.0.zip
curl -fLO "$base/$bundle" && curl -fLO "$base/$bundle.sha256" &&
  shasum -a 256 -c "$bundle.sha256" && unzip -q "$bundle"
```

Keep Central enabled for third-party dependencies. In the quickstart source, change the Siglyph dependency versions to `0.14.0`, then point Scala CLI at the extracted repository (replace the source path with yours):

```bash
repo=$(python3 -c 'from pathlib import Path; print(Path("maven").resolve().as_uri())')
scala-cli run /path/to/hello.scala --jvm 21 --repository "$repo"
```

For sbt, add `resolvers += "Siglyph release" at file("/absolute/path/to/maven").toURI.toString` and use the same dependency coordinates with version `0.14.0`. No GitHub Packages credentials or local publication are needed.

### Toolchain compatibility

Published **0.13.0** artifacts need **Scala 3.10.1 or a compatible newer compiler** to read their TASTy; the examples below use **Scala 3.10.1**. JVM applications and repository builds retain **JDK 21**. Scala Native uses **0.5.12**. Java/Kotlin applications use the JVM facade and do not need to install a Scala compiler.

Mill is needed only if you choose Mill as your build tool or build this repository. Contributor toolchain pins live in [CONTRIBUTING](../CONTRIBUTING.md#development-setup).

Scala 3.9.x and older source consumers must upgrade to read the published artifacts' TASTy. See [release history](../CHANGELOG.md) for older baselines.

### SBT

Add to `build.sbt`:

```scala
scalaVersion := "3.10.1"

libraryDependencies ++= Seq(
  "io.github.zikolach" %% "siglyph-core" % "0.13.0",
  "io.github.zikolach" %% "siglyph-terminal-jvm" % "0.13.0"
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
  def scalaVersion = "3.10.1"

  def mvnDeps = Seq(
    mvn"io.github.zikolach::siglyph-core::0.13.0",
    mvn"io.github.zikolach::siglyph-terminal-jvm::0.13.0"
  )
}
```

### Optional modules

Add `siglyph-markdown`, `siglyph-image` or `siglyph-extras` only when needed. For example, extras uses:

- **SBT:** `"io.github.zikolach" %% "siglyph-extras" % "0.13.0"`
- **Mill:** `mvn"io.github.zikolach::siglyph-extras::0.13.0"`

Optional Native modules have been published since **0.12.0**. Add these platform-aware Mill coordinates from a `ScalaNativeModule` in addition to `siglyph-core` and `siglyph-terminal-native`:

```scala
mvn"io.github.zikolach::siglyph-markdown::0.13.0"
mvn"io.github.zikolach::siglyph-image::0.13.0"
mvn"io.github.zikolach::siglyph-extras::0.13.0"
```

### Maven for Java and Kotlin JVM apps

The JVM-only Java/Kotlin facade and builders are available in **0.13.0**; see [language-native examples](../examples/language-dx/README.md).

```xml
<dependencies>
  <dependency>
    <groupId>io.github.zikolach</groupId>
    <artifactId>siglyph-core_3</artifactId>
    <version>0.13.0</version>
  </dependency>
  <dependency>
    <groupId>io.github.zikolach</groupId>
    <artifactId>siglyph-terminal-jvm_3</artifactId>
    <version>0.13.0</version>
  </dependency>
</dependencies>
```

### Gradle for Java and Kotlin JVM apps

The JVM-only Java/Kotlin facade and builders are available in **0.13.0**; see [language-native examples](../examples/language-dx/README.md).

In `build.gradle.kts` with your Java/Kotlin plugin configured:

```kotlin
repositories { mavenCentral() }

dependencies {
  implementation("io.github.zikolach:siglyph-core_3:0.13.0")
  implementation("io.github.zikolach:siglyph-terminal-jvm_3:0.13.0")
}
```

Scala JVM applications usually start with `siglyph-core` and `siglyph-terminal-jvm` through Scala build-tool syntax such as `%%` or Mill `::`. Java and Kotlin JVM applications use the concrete JVM artifact IDs `siglyph-core_3` and `siglyph-terminal-jvm_3`, and can use `scalatui.terminal.jvm.interop.SiglyphJvm` for a narrow Java-friendly facade. Scala Native applications use platform-aware coordinates such as `siglyph-core`, `siglyph-terminal-native`, `siglyph-markdown`, `siglyph-image`, and `siglyph-extras`; Mill resolves those to `_native0.5_3` artifacts from `ScalaNativeModule` projects for release **0.13.0**. The Java/Kotlin facade and `keyTester` are JVM-only; Scala Native artifacts remain Scala-focused.


## Scala Native

Use macOS or Linux with Scala CLI, JDK 21 for the build, and a working Clang/Clang++ toolchain and system development libraries. On macOS, install Xcode Command Line Tools (`xcode-select --install`). On Debian/Ubuntu, install `clang`, `libgc-dev` and `libunwind-dev`. See the [Scala Native setup guide](https://scala-native.org/en/stable/user/setup.html) for platform details.

From a checkout:

```bash
scala-cli run examples/scala-cli/hello-native.scala --jvm 21
```

The [complete Native example](../examples/scala-cli/hello-native.scala) pins Scala Native 0.5.12, uses platform-aware dependencies and selects `PosixTerminal`. Its resulting executable does not require a JVM. Like the JVM example, run it in a real terminal; Esc or Ctrl+C exits.

## Java and Kotlin

Use the concrete `_3` artifact names above, not Native artifacts. Follow the [Java/Kotlin examples](jvm-language-examples.md) for the `SiglyphJvm` facade and complete applications.
