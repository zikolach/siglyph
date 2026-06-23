## 1. Scenario implementation

- [x] 1.1 Add a deterministic agent prompt composer recording scenario that shows visible typing, slash completion, attachment completion, tag completion, and submitted prompt summary.
- [x] 1.2 Add a deterministic command palette and settings recording scenario that shows fuzzy filtering, action selection, loader/progress state, and settings value changes.
- [x] 1.3 Add a deterministic Unicode typed input recording scenario that shows CJK, emoji, combining marks, paste summary, Insert, arrow keys, and Ctrl-key display.
- [x] 1.4 Ensure the scenarios render through siglyph components where practical and do not require an interactive TTY to show meaningful output.

## 2. Recording workflow

- [x] 2.1 Add local recording commands or scripts that write `.cast` files under `artifacts/asciinema` or another documented local artifact path.
- [x] 2.2 Ensure documented recording commands hide Mill progress output, Scala CLI resolver output, and compiler progress output from visible cast content.
- [x] 2.3 Ensure normal compile, test, formatting, lint, and OpenSpec validation commands do not require asciinema, expect, Node.js, npm, `agg`, `svg-term`, or browser tooling.

## 3. Documentation

- [x] 3.1 Document each scenario purpose, recording command, and generated cast path in README or a dedicated docs page linked from README.
- [x] 3.2 Document local playback with `asciinema play` for each generated `.cast` file.
- [x] 3.3 Document README publishing with clickable asciinema SVG preview links in the form `[![asciicast](https://asciinema.org/a/<id>.svg)](https://asciinema.org/a/<id>)`.
- [x] 3.4 State that generated `.cast` files are optional publishing artifacts and are not required build outputs.

## 4. Validation

- [x] 4.1 Generate the three local `.cast` files and inspect their content for scenario clarity and absence of build-tool noise.
- [x] 4.2 Run focused compile/test checks for changed demo code.
- [x] 4.3 Run `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
