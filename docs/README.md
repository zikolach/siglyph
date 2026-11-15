# Documentation

## Getting started

- [First application](../README.md#quickstart): a complete Scala CLI app using published artifacts.
- [Installation](getting-started.md): Scala JVM, Scala Native, Maven and Gradle.
- [Java and Kotlin](jvm-language-examples.md): the JVM interop facade.
- [Runnable Scala CLI examples](../examples/scala-cli/README.md) and [recording gallery](asciinema-demos.md).

## Recipes

- [Executor scheduling](executor-scheduling.md): bounded one-shot tasks on an explicitly owned executor (unreleased).

- [Virtual-time scheduling](virtual-time.md): cancellable deadlines and bounded test advancement (unreleased).

- [Application update scopes](application-scopes.md): bounded, session-owned update delivery (unreleased).

- [Application testing](application-testing.md): managed typed scripts and screen assertions (unreleased).

- [Experimental data table](data-table.md): typed rows, stable selection and visible-range rendering (unreleased).

- [Log explorer discovery prototype](log-explorer.md): bounded generated events, filtering and selection.

- [Normal-screen, fullscreen and scrollback output](screen-modes.md)
- [Input, editors, autocomplete, optional widgets and terminal helpers](input-and-terminal.md)
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
