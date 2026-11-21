# Documentation

## Getting started

- [First application](../README.md#quickstart): a complete Scala CLI app using published artifacts.
- [Installation](getting-started.md): Scala JVM, Scala Native, Maven and Gradle.
- [Java and Kotlin](jvm-language-examples.md): the JVM interop facade.
- [Unreleased language-native examples](../examples/language-dx/README.md): typed Scala composition, Java builders and optional Kotlin source DSL.
- [Language DX roadmap](language-dx-roadmap.md): approved scope and acceptance contract.
- [Runnable Scala CLI examples](../examples/scala-cli/README.md) and [recording gallery](asciinema-demos.md).

## Recipes

- [Semantic themes and keyboard form](semantic-themes.md): immutable palettes, local overrides and modal focus restoration.

- [Follow a log file](file-log-following.md): bounded UTF-8 input, rotation/truncation notices and visible I/O recovery.

- [Live log explorer](live-log-explorer.md): automatic generated batches with scoped producer cleanup.

- [Scope-owned timers](scoped-timers.md): delayed/recurring updates and retryable shutdown (since 0.12.0).

- [Executor scheduling](executor-scheduling.md): bounded one-shot tasks on an explicitly owned executor (since 0.12.0).

- [Virtual-time scheduling](virtual-time.md): cancellable deadlines and bounded test advancement (since 0.12.0).

- [Application update scopes](application-scopes.md): bounded, session-owned update delivery (since 0.12.0).

- [Application testing](application-testing.md): managed typed scripts and screen assertions (since 0.12.0).

- [Experimental data table](data-table.md): typed rows, stable selection and visible-range rendering (since 0.12.0).

- [Log explorer discovery prototype](log-explorer.md): bounded generated events, filtering and selection.

- [Normal-screen, fullscreen and scrollback output](screen-modes.md)
- [Input, editors, autocomplete, optional widgets and terminal helpers](input-and-terminal.md)
- [Editor visual navigation, history drafts and conversational composer](editor-interactions.md)
- [Streaming Markdown and live response viewer](streaming-markdown.md): bounded source, conservative parse reuse and provisional rendering (unreleased).
- [Scroll, wheel and selection policies](scroll-input-policies.md)
- [Terminal images and vertical cropping](kitty-vertical-cropping.md)
- [Optional terminal program status](terminal-program-status.md)
- [Repository demo applications](demos.md)

## Reference

- [Typed component output and controls](component-output.md)
- [Default keybindings](keybinding-defaults.md)
- [Unicode segmentation, width and safe text](unicode.md)
- [Runtime diagnostics](runtime-diagnostics.md)
- [Resize behavior and troubleshooting](terminal-resize-redraw-issue.md)
- [Terminal reply framing](terminal-reply-framing.md)
- [Terminal conformance boundaries](terminal-conformance.md)
- [Image lifecycle](pi-graphics-lifecycle.md) and [Markdown streaming cache](markdown-streaming-cache.md)
- [Version-pinned pi-tui compatibility](pi-tui-compatibility.md)

## Maintainers

- [0.12.0 release record](release-v0.12.0.md).

- [API stability and release gates](api-stability.md): scoped ABI checks and published/candidate consumers.

These pages concern building, testing and researching the library, not prerequisites for consuming it.

- [Contributing and validation](../CONTRIBUTING.md)
- [Repository layout and IDE setup](development.md)
- [Interactive smoke tests](interactive-smoke.md) and [performance benchmarks](performance-benchmarks.md)
- [Publishing](publishing.md) and [demo recording](asciinema-demos.md)
- [Roadmap](post-mvp-plan.md) and [loader follow-up](loader-components-follow-up.md)
- [Porting notes](porting-notes.md) and [Markdown parser research](markdown-parser-research.md)

## Historical records

These preserve decisions and release evidence; use Getting started for current installation instructions.

- Release records: [0.11.0](release-v0.11.0.md), [0.10.0](release-v0.10.0.md), [0.9.2](release-v0.9.2.md), [0.9.1](release-v0.9.1.md)
- [Early tautui test comparison](tautui-test-comparison.md)

[Changelog](../CHANGELOG.md) · [License](../LICENSE) · [Upstream attribution](../NOTICE)
