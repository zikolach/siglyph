## Why

The baseline Markdown renderer is readable and dependency-free, but list rendering loses source details that upstream `pi-tui` preserves in common chat and documentation output. Improving list fidelity gives users better Markdown migration behavior without committing to a mandatory parser dependency.

## What Changes

- Preserve current normalized list rendering by default.
- Add an option to preserve ordered and unordered source markers where the baseline parser can identify them.
- Preserve task list markers as text rather than adding a semantic task model.
- Preserve loose-list blank lines where supported by the baseline parser.
- Improve wrapped list continuation indentation for ordered, unordered, and task-list rows.
- Document remaining parser limitations and keep richer CommonMark behavior behind optional parser adapters.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `markdown-rendering`: list marker preservation option, task-list text preservation, loose-list spacing, and wrapped list indentation.

## Impact

- Affected modules: `markdown`, Markdown tests, README/docs, porting notes.
- Public API impact: likely a new Markdown rendering option; default behavior remains normalized for compatibility.
- Dependency impact: no new runtime dependencies.
- Platform impact: baseline behavior remains shared across JVM and Scala Native-compatible source paths.
