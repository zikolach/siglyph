# siglyph Scala CLI demos

Small single-file demos intended for `scala-cli` and GitHub Gist.

These examples use jar assets from the `v0.1.0` GitHub Release instead of
GitHub Packages, so once the repository/release is public they can run without
Maven credentials or cloning this repository.

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
