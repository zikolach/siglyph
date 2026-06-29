# siglyph Scala CLI demos

Small single-file demos intended for `scala-cli`.

These examples currently use the local `0.2.6-SNAPSHOT` artifacts so they can
exercise unreleased mouse-input APIs. Run `./scripts/publish-local-snapshot.sh`
from the repository root before running them.

Run from this repository:

```bash
./examples/scala-cli/markdown.scala
./examples/scala-cli/hello.scala
./examples/scala-cli/editor-autocomplete.scala
./examples/scala-cli/image.scala /path/to/image.png
./examples/scala-cli/mouse.scala
```

After replacing the dependency versions with a released Maven Central version, a script can also run from a raw Gist URL:

```bash
curl -L -o hello.scala https://gist.githubusercontent.com/zikolach/<gist-id>/raw/hello.scala
chmod +x hello.scala
./hello.scala
```

`hello.scala`, `editor-autocomplete.scala`, `image.scala`, and `mouse.scala` require a real macOS/Linux TTY.
`markdown.scala` is non-interactive and renders to standard output.

`editor-autocomplete.scala` demonstrates the built-in `CombinedAutocompleteProvider` with:

- slash commands (`/help`, `/clear`, `/quit`),
- dependency-free Java/NIO filesystem path completion (`./`, `../`, quoted paths),
- attachment completion with `@` / `@"...` preserving required quoting,
- optional fuzzy ranking,
- injectable debounce/cancellation via an application-owned scheduler, and
- an application-owned `#` trigger source for tags.

No external shell tools such as `fd`, `find`, or `bash` are used for completion.

`mouse.scala` demonstrates opt-in mouse reporting with `TUIOptions(mouseInput = true)`, typed `TerminalInput.Mouse` logging, coordinate-routed mouse events in a custom component, and wheel scrolling over `SelectList` and `Editor`. While the demo runs, terminal wheel scrollback is captured by mouse reporting; unhandled wheel events are logged but cannot be passed back to the terminal reliably.

`image.scala` demonstrates `ImageSource.fromFile`, terminal image capability detection, protocol rendering when Kitty/iTerm2-style images are supported, and readable fallback text otherwise. It accepts PNG, JPEG, GIF, and WebP files. In siglyph versions with runtime cell-size support, the image component uses terminal cell-size replies by default for row sizing and keeps following text below the image area. Applications that need deterministic fixed sizing can pass `ImageRenderOptions(cellDimensionsSource = ImageCellDimensionsSource.Fixed, cellDimensions = ...)`.

To run an example against local sources instead of the published local snapshot, pass the source roots to Scala CLI:

```bash
scala-cli run --workspace /tmp/siglyph-mouse-demo \
  examples/scala-cli/mouse.scala core/src terminalJvm/src
```

For the image example, include the image module and pass the image path after `--`:

```bash
scala-cli run --workspace /tmp/siglyph-image-demo \
  examples/scala-cli/image.scala core/src terminalJvm/src image/src -- \
  /path/to/image.png
```
