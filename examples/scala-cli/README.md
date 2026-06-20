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
```

Run from a raw Gist URL:

```bash
curl -L -o hello.scala https://gist.githubusercontent.com/zikolach/<gist-id>/raw/hello.scala
chmod +x hello.scala
./hello.scala
```

`hello.scala` and `editor-autocomplete.scala` require a real macOS/Linux TTY.
`markdown.scala` is non-interactive and renders to standard output.

`editor-autocomplete.scala` demonstrates the built-in `CombinedAutocompleteProvider` with:

- slash commands (`/help`, `/clear`, `/quit`),
- dependency-free Java/NIO filesystem path completion (`./`, `../`, quoted paths),
- attachment completion with `@` / `@"...` preserving required quoting,
- optional fuzzy ranking,
- injectable debounce/cancellation via an application-owned scheduler, and
- an application-owned `#` trigger source for tags.

No external shell tools such as `fd`, `find`, or `bash` are used for completion.
