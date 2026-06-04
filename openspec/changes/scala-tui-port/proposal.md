## Why

We want a Node-free Scala TUI library that preserves the most valuable `pi-tui` behavior while being usable from Scala applications without a JavaScript runtime. `pi-tui` and TauTUI show the feature set and architecture are feasible, but Scala needs a native specification to guide an incremental port.

## What Changes

- Introduce a Scala 3 TUI library direction with a shared core and platform terminal backends, built with Mill.
- Define the initial requirements for component rendering, differential terminal output, input normalization, editing, Markdown integration, and developer-facing APIs.
- Establish Scala Native and JVM as day-one targets behind a compatibility layer: Native uses POSIX terminal APIs, while JVM uses an `stty`-based Unix backend initially.
- Keep the core Node-free and dependency-light; third-party runtime dependencies are allowed only after explicit confirmation.
- Provide an implementation plan that uses current `pi-tui` as the behavior source and TauTUI as a Node-free porting reference.

## Capabilities

### New Capabilities
- `component-rendering`: Component model, layout rules, ANSI-safe rendering helpers, and differential output behavior.
- `terminal-runtime`: Raw terminal lifecycle, terminal protocol negotiation, input normalization, resize handling, and backend strategy.
- `text-editing`: Input and multiline editor behavior including cursor movement, paste handling, undo/delete helpers, and autocomplete integration.
- `developer-api`: Public Scala API shape, modules, test harness, and compatibility goals for dependency-light usage.
- `markdown-rendering`: Separate, pluggable Markdown rendering module with optional platform-appropriate parser dependencies.

### Modified Capabilities

## Impact

- Creates the initial OpenSpec baseline for a Scala port of `pi-tui` concepts.
- Guides future implementation under a Mill/Scala 3 project layout with Native and JVM targets from the start.
- Does not introduce production code yet; this change defines the contract and implementation tasks.
