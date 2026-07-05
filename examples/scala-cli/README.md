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


`alternate-screen-maven.scala` demonstrates opt-in alternate-screen mode with a practical Sonatype Central explorer. It searches the Sonatype Central browse API, shows artifacts and versions in a temporary full-screen workspace, and exits back to the normal shell without leaving the search UI in scrollback. It uses `jsoniter-scala` only inside the example for JSON parsing. Until alternate-screen mode is available in a published release, this demo uses local source directives for `core/src` and `terminalJvm/src`, so run it from this checkout.

`editor-autocomplete.scala` demonstrates the built-in `CombinedAutocompleteProvider` with:

- slash commands (`/help`, `/clear`, `/quit`),
- dependency-free Java/NIO filesystem path completion (`./`, `../`, quoted paths),
- attachment completion with `@` / `@"...` preserving required quoting,
- optional fuzzy ranking,
- injectable debounce/cancellation via an application-owned scheduler, and
- an application-owned `#` trigger source for tags.

No external shell tools such as `fd`, `find`, or `bash` are used for completion.

`image.scala` demonstrates `ImageSource.fromFile`, terminal image capability detection, protocol rendering when Kitty/iTerm2-style images are supported, and readable fallback text otherwise. It accepts PNG, JPEG, GIF, and WebP files. In siglyph versions with runtime cell-size support, the image component uses terminal cell-size replies by default for row sizing and keeps following text below the image area. Applications that need deterministic fixed sizing can pass `ImageRenderOptions(cellDimensionsSource = ImageCellDimensionsSource.Fixed, cellDimensions = ...)`.

To test unreleased changes from this checkout before the next Maven Central release, run the image example against local sources:

```bash
scala-cli run --workspace /tmp/siglyph-image-demo \
  examples/scala-cli/image.scala core/src terminalJvm/src image/src -- \
  /path/to/image.png
```
