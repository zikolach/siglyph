# siglyph Scala CLI demos

Small single-file demos intended for `scala-cli`.

The released examples use Maven Central `0.11.0` artifacts. Exceptions: [testing.scala](testing.scala), [application-scope.scala](application-scope.scala) [virtual-time.scala](virtual-time.scala) [executor-scheduling.scala](executor-scheduling.scala) and [scoped-timers.scala](scoped-timers.scala) exercise unreleased APIs against local `core/src` and require a source checkout; see [application testing](../../docs/application-testing.md) and [update scopes](../../docs/application-scopes.md).

Run from this repository:

```bash
./examples/scala-cli/markdown.scala
./examples/scala-cli/hello.scala
./examples/scala-cli/editor-autocomplete.scala
./examples/scala-cli/image.scala docs/assets/siglyph-logo.png
./examples/scala-cli/alternate-screen-maven.scala
./examples/scala-cli/mouse.scala
```

No checkout is needed for the [copy-pastable first application](../../README.md#quickstart). It includes the same published dependencies as `hello.scala`; save it as `hello.scala` and run `scala-cli run hello.scala --jvm 21`.

For Scala Native, follow the [toolchain setup](../../docs/getting-started.md#scala-native), then run:

```bash
scala-cli run examples/scala-cli/hello-native.scala --jvm 21
```

Run each example separately; these files have independent entry points and platform directives.

`hello.scala`, `editor-autocomplete.scala`, `image.scala`, `alternate-screen-maven.scala`, and `mouse.scala` require a real macOS/Linux TTY.
`markdown.scala` is non-interactive and renders to standard output.


`alternate-screen-maven.scala` demonstrates the legacy width-only alternate-screen mode with a practical Sonatype Central explorer. The application reads terminal height itself and writes OSC 52 directly to copy snippets. That raw helper is outside TUI fullscreen selection, `HostClipboard`, and typed control authority. The example exits without leaving its UI in normal shell scrollback and uses `jsoniter-scala` only for JSON parsing. Use `mill fullscreenJvmDemo.run` for height-aware `TUI.fullscreen`, `ScrollView`, search, mouse gestures, selection, and host-only copy.

`editor-autocomplete.scala` demonstrates the built-in `CombinedAutocompleteProvider` with:

- slash commands (`/help`, `/clear`, `/quit`),
- dependency-free Java/NIO filesystem path completion (`./`, `../`, quoted paths),
- attachment completion with `@` / `@"...` preserving required quoting,
- optional fuzzy ranking,
- injectable debounce/cancellation via an application-owned scheduler, and
- an application-owned `#` trigger source for tags.

No external shell tools such as `fd`, `find`, or `bash` are used for completion.

Custom text-only components use the typed render result directly:

```scala
import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender}

final class Label(value: String) extends Component:
  override def render(width: Int): ComponentRender =
    ComponentRender.text(Ansi.truncateToWidth(value, math.max(0, width), ""))
```

`image.scala` demonstrates `ImageSource.fromFile`, its validated `scalatui.terminal.Base64ImagePayload` in `ImageSource.payload`, terminal image capability detection, protocol rendering when Kitty/iTerm2-style images are supported, and readable fallback text otherwise. It accepts PNG, JPEG, GIF, and WebP files. Raw base64 text can instead be validated with `Base64ImagePayload.from` or the typed `scalatui.image.Image.fromBase64` factory, which returns `Either[scalatui.terminal.Base64ImagePayloadError, Image]`. Invalid input creates no component or output. In siglyph versions with runtime cell-size support, the image component uses terminal cell-size replies by default for row sizing and keeps following text below the image area. Applications that need deterministic fixed sizing can pass `ImageRenderOptions(cellDimensionsSource = ImageCellDimensionsSource.Fixed, cellDimensions = ...)`.

The high-level `Image` component returns reserved ordinary rows plus a positioned typed control. A
lower-level component can compose the same result without handling protocol strings:

```scala
import scalatui.core.{ComponentRender, TerminalControlPlacement}
import scalatui.terminal.{TerminalCapabilities, TerminalImageProtocol}

val rendered = TerminalImageProtocol.renderBase64Image(
  source.payload,
  source.dimensions,
  TerminalCapabilities.detect(),
  terminalWidth = 80
)

val frame = rendered.fold(ComponentRender.text("Image output is unavailable")) { image =>
  ComponentRender(
    lines = Vector.fill(image.rows)(""),
    controls = Vector(TerminalControlPlacement(row = 0, column = 0, image.control)),
    cursorPlacements = Vector.empty
  )
}
```

The TUI validates the control geometry and encodes the typed control only while assembling final
synchronized output. Image-looking bytes in ordinary text gain no semantic control authority.

Validation has the same lexical contract on JVM and Scala Native. It accepts standard padded base64, empty input, and decoder-valid unpadded lengths with modulo-four remainder zero, two, or three; remainder one is rejected. Validation transiently allocates and discards decoded bytes. iTerm2 filenames use standard base64 over UTF-8, and fallback metadata controls become visible `\\uXXXX` text before width truncation. Protocol helpers and `Image` require typed payloads, and `ImageSource.payload` replaces the old `ImageSource.base64Data` field. These are direct source breaks with no compatibility path. There is no API that promotes an arbitrary protocol escape string to a semantic control.

`mouse.scala` demonstrates opt-in mouse reporting with `TUIOptions(mouseInput = true)`, typed `TerminalInput.Mouse` logging, coordinate-routed mouse events in a custom component, and wheel scrolling over `SelectList` and `Editor`. While the demo runs, terminal wheel scrollback is captured by mouse reporting; unhandled wheel events are logged but cannot be passed back to the terminal reliably.

To run an example against local sources instead of the published release, pass the source roots to Scala CLI:

```bash
scala-cli run --workspace /tmp/siglyph-mouse-demo \
  examples/scala-cli/mouse.scala core/src terminalJvm/src
```

For the image example, include the image module and pass the image path after `--`:

```bash
scala-cli run --workspace /tmp/siglyph-image-demo \
  examples/scala-cli/image.scala core/src terminalJvm/src image/src -- \
  docs/assets/siglyph-logo.png
```
