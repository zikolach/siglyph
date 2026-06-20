## 1. Parser and model updates

- [ ] 1.1 Add list item metadata for source marker text without changing default rendered output.
- [ ] 1.2 Preserve task marker text in parsed list items for `[ ]`, `[x]`, and `[X]` markers.
- [ ] 1.3 Add baseline loose-list parsing support for blank lines between list items.

## 2. Renderer behavior

- [ ] 2.1 Add a Markdown render option for source marker preservation with default normalized behavior.
- [ ] 2.2 Render preserved unordered and ordered markers when the option is enabled.
- [ ] 2.3 Render task-list markers as text and align wrapped task content.
- [ ] 2.4 Improve wrapped continuation indentation for ordered and unordered list items.

## 3. Tests and docs

- [ ] 3.1 Add tests for default normalized list markers.
- [ ] 3.2 Add tests for preserved `-`, `*`, `+`, and ordered source markers.
- [ ] 3.3 Add tests for task markers, loose-list spacing, wrapped indentation, and width safety.
- [ ] 3.4 Update Markdown docs and porting notes with the new option and remaining limitations.
- [ ] 3.5 Run `mill markdown.test`, `mill __.compile`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
