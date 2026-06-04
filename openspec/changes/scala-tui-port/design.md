## Context

The target is a Scala 3 implementation of a `pi-tui`-style terminal UI library built with Mill and usable without Node.js. The TypeScript `pi-tui` package is the canonical behavior source, while TauTUI demonstrates a working Node-free porting strategy for macOS/Linux using a terminal abstraction, native process terminal, virtual terminal, renderer, and component set.

Current `pi-tui` complexity is concentrated in terminal protocol handling, Unicode-aware width/editing, differential rendering, overlays, markdown, images, and autocomplete. The Scala project should avoid trying to port everything at once; it should start with a small shared core and a production-quality Unix-like terminal backend.

## Goals / Non-Goals

**Goals:**

- Define a Scala-first architecture for a Node-free, dependency-light TUI library.
- Preserve `pi-tui` behavior for core rendering, terminal lifecycle, input normalization, and editor semantics.
- Support Scala Native and JVM from day one behind a compatibility layer.
- Use Scala Native POSIX APIs for Unix-like raw terminal control and an `stty`-based Unix backend on JVM initially.
- Keep component/rendering logic pure enough to test through a virtual terminal.

**Non-Goals:**

- Do not implement a browser/Scala.js terminal UI in the initial scope.
- Do not require Windows console parity in the first production milestone.
- Do not claim full current `pi-tui` parity until tests cover the corresponding behavior.
- Do not introduce JLine, ICU4J, flexmark, npm packages, or other third-party runtime dependencies without explicit confirmation.

## Decisions

### Decision: Use current pi-tui as behavior source

`pi-tui` remains the source of truth for requirements and parity. Its tests should be mirrored where practical, especially renderer, key handling, editor, autocomplete, width, and wrapping tests.

Alternative considered: port only from TauTUI. TauTUI is useful, but it may lag current `pi-tui`, so relying on it alone risks preserving outdated behavior.

### Decision: Use TauTUI as the Node-free architecture reference

TauTUI maps `pi-tui` into a native terminal architecture and provides useful seams: terminal protocol, process terminal, virtual terminal, core renderer, component modules, editor buffer, and porting docs. The Scala implementation should borrow these seams but use Scala idioms.

Alternative considered: line-by-line TypeScript port. This would preserve behavior in the short term but would embed Node-centric assumptions and produce a weaker Scala API.

### Decision: Split the library into pure core and backend modules

The initial module layout should separate responsibilities:

- `scala-tui-core`: components, renderer, ANSI utilities, key model, width/wrap helpers, virtual-terminal-friendly APIs.
- `scala-tui-terminal-native`: Scala Native POSIX backend using termios, ioctl, signals, and stdin/stdout.
- `scala-tui-terminal-jvm`: day-one JVM Unix backend using `stty` for raw mode and terminal restoration.
- `scala-tui-markdown`: separate pluggable Markdown rendering module, allowed to use explicitly approved platform-appropriate parser dependencies.

Alternative considered: a single monolithic module. This simplifies bootstrapping but makes JVM/Native backend trade-offs leak into core APIs.

### Decision: Target Native and JVM from day one

Scala Native can call POSIX APIs directly and is the cleanest backend for raw terminal support. The JVM backend is also required from day one, but it will use a compatibility layer and an `stty`-based Unix implementation rather than depending on JLine initially.

Alternative considered: Native-only MVP. This would simplify implementation but would delay validation of the shared API across Scala targets.

### Decision: Implement Unicode support with generated latest-version tables plus tests

Without ICU or npm width packages in the core, Unicode width and segmentation need explicit implementation. Start with minimal wcwidth-style display width plus full grapheme cluster handling backed by latest Unicode data generated and committed to the repository, then harden through fixtures from `pi-tui` and terminal observations.

Alternative considered: use ICU4J or platform ICU. This reduces risk but requires explicit dependency approval and would not belong in the dependency-light core by default.

### Decision: Keep public APIs typed and Scala-idiomatic

Expose typed key events, modifiers, terminal events, components, and theme/style functions rather than stringly raw escape input where typed input makes sense. Preserve escape-level access for diagnostics and compatibility tests, and document the corresponding `pi-tui` API for each Scala-facing abstraction.

Alternative considered: mirror TypeScript raw string input. That would simplify parity but make Scala applications less safe and shift terminal parsing burden to components.

## Risks / Trade-offs

- **Unicode correctness is hard** → Mitigate with generated Unicode tables, focused width/grapheme tests, and documented known deviations.
- **Markdown dependencies need careful approval** → Mitigate by keeping Markdown in a separate pluggable module and confirming parser dependencies before adding them.
- **JVM raw mode through `stty` is less robust than JLine** → Mitigate by documenting Unix-only JVM scope for v0 and keeping the backend replaceable.
- **Current pi-tui continues to evolve** → Mitigate by tracking upstream tests and maintaining a porting/sync log similar to TauTUI.
- **Terminal behavior varies across emulators** → Mitigate with a key tester/demo, virtual terminal tests, and explicit capability detection.
- **Full Windows parity is expensive** → Mitigate by documenting Windows as out of initial scope and not blocking Unix/macOS delivery.

## Migration Plan

1. Create the Mill/Scala 3 project skeleton and module boundaries.
2. Implement the pure core: component contract, container, ANSI parsing, visible width/truncation/wrapping, virtual terminal, and basic renderer.
3. Implement compatibility-layer terminal APIs with Native POSIX and JVM `stty` Unix backends.
4. Port MVP components: Text, Box, Spacer, SelectList, and Input.
5. Add virtual-terminal tests and parity fixtures for the MVP.
6. Plan but postpone Editor, autocomplete, images, and Markdown implementation until after the first usable milestone.
7. Implement Markdown as a separate pluggable module in v0 after dependency choices are confirmed.
8. Continuously mirror relevant `pi-tui` and TauTUI tests.

## Open Questions

- Which Markdown parser dependency, if any, should be approved for JVM and Native?
- What exact raw ANSI snapshots from `pi-tui` should be treated as compatibility fixtures rather than virtual-viewport tests?
- Which Unicode data generation script/tool should be used to produce latest-version committed tables?
- What user-facing name should the library and Mill modules use?
