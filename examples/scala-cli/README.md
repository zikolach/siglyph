# siglyph Scala CLI demos

Small single-file demos intended for `scala-cli` and GitHub Gist.

These examples use Maven Central dependencies, so they can run without Maven
credentials or cloning this repository once the Central sync for the release has
completed.

Run from this repository:

```bash
./examples/scala-cli/markdown.scala
./examples/scala-cli/hello.scala
./examples/scala-cli/editor-autocomplete.scala
./examples/scala-cli/image.scala /path/to/image.png
./examples/scala-cli/alternate-screen-maven.scala
```

Run from a raw Gist URL:

```bash
curl -L -o hello.scala https://gist.githubusercontent.com/zikolach/<gist-id>/raw/hello.scala
chmod +x hello.scala
./hello.scala
```

`hello.scala`, `editor-autocomplete.scala`, `image.scala`, and `alternate-screen-maven.scala` require a real macOS/Linux TTY.
`markdown.scala` is non-interactive and renders to standard output.


`alternate-screen-maven.scala` demonstrates opt-in alternate-screen mode with a practical Sonatype Central explorer. It searches the Sonatype Central browse API, uses the full alternate-screen height and shows built-in loader states, artifacts, versions, published dates, and build-tool snippets in a temporary full-screen workspace, copies the selected snippet through terminal clipboard escape sequences with an inline `Copied!` badge, and exits back to the normal shell without leaving the search UI in scrollback. It uses `jsoniter-scala` only inside the example for JSON parsing.

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
    controls = Vector(TerminalControlPlacement(row = 0, column = 0, image.control))
  )
}
```

The TUI validates the control geometry and encodes the typed control only while assembling final
synchronized output. Image-looking bytes in ordinary text gain no semantic control authority.

Validation has the same lexical contract on JVM and Scala Native. It accepts standard padded base64, empty input, and decoder-valid unpadded lengths with modulo-four remainder zero, two, or three; remainder one is rejected. Validation transiently allocates and discards decoded bytes. iTerm2 filenames use standard base64 over UTF-8, and fallback metadata controls become visible `\\uXXXX` text before width truncation. Protocol helpers and `Image` require typed payloads, and `ImageSource.payload` replaces the old `ImageSource.base64Data` field. These are direct source breaks with no compatibility path. There is no API that promotes an arbitrary protocol escape string to a semantic control.

To test unreleased changes from this checkout before the next Maven Central release, run the image example against local sources:

```bash
scala-cli run --workspace /tmp/siglyph-image-demo \
  examples/scala-cli/image.scala core/src terminalJvm/src image/src -- \
  /path/to/image.png
```
