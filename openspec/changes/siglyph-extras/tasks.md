## 1. Module and Build Setup

- [x] 1.1 Add the `extras` module to `build.mill` with artifact name `siglyph-extras` and dependency on `core` only.
- [x] 1.2 Add a Scala Native extras mirror module using shared `extras/src` sources if the build can publish it cleanly.
- [x] 1.3 Add `extras/test` with MUnit as a test-only dependency.
- [x] 1.4 Update compile, formatting, lint, publishing, and release metadata paths that enumerate publishable modules or source roots.

## 2. Extras Public APIs

- [x] 2.1 Add `scalatui.extras.Expandable` with `setExpanded(expanded: Boolean): Unit` and Scaladoc.
- [x] 2.2 Add `ExpandableText` with collapsed and expanded text providers, initial expansion state, padding, styling, cache invalidation, and width-safe rendering.
- [x] 2.3 Add `ExpandableSection` with title, collapsed body, expanded body, optional hint text, initial expansion state, styling hooks, and width-safe rendering.
- [x] 2.4 Add `ExpansionController` with explicit registration, unregistration, clear, current state access, state mutation, and no direct `TUI` dependency.
- [x] 2.5 Add `scalatui.extras` package exports so application code can import the public helpers consistently.

## 3. Tests

- [x] 3.1 Add tests proving `ExpandableText` renders collapsed and expanded states within the requested width.
- [x] 3.2 Add tests proving `ExpandableText` invalidates cached output when expansion state or text provider output changes.
- [x] 3.3 Add tests proving `ExpandableSection` renders title, collapsed body, expanded body, and configured hint text within narrow and normal widths.
- [x] 3.4 Add tests proving `ExpansionController` applies state to registered expandables, stops mutating unregistered expandables, and does not require a `TUI` instance.
- [x] 3.5 Add tests or compile checks proving extras does not depend on terminal backend, Markdown, image, demo, or agent-specific modules.

## 4. Documentation

- [x] 4.1 Update README install snippets and module overview to include `siglyph-extras`.
- [x] 4.2 Add docs or README usage examples for `ExpandableText`, `ExpandableSection`, and `ExpansionController`.
- [x] 4.3 Document module scope and non-goals, including exclusion of agent sessions, LLM messages, tool execution, extension runtimes, and model selection.
- [x] 4.4 Update publishing documentation and release metadata for the extras artifact and any Native variant that the build publishes.
- [x] 4.5 Update `docs/post-mvp-plan.md` or related roadmap notes to reflect the optional extras module boundary.

## 5. Validation

- [x] 5.1 Run `mill extras.test`.
- [x] 5.2 Run `mill __.compile`.
- [x] 5.3 Run `mill scalafmtCheck`.
- [x] 5.4 Run `mill scalafixCheck`.
- [x] 5.5 Run `openspec validate --all --strict`.
- [x] 5.6 Run `git diff --check`.
