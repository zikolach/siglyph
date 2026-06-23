## Context

siglyph already covers most high-value `pi-tui` parity areas: component rendering, differential output, overlays, typed input, Unicode-aware editing, autocomplete, terminal integrations, Markdown, images, JVM backend, Scala Native backend, stream backend, and virtual backend.

The remaining gaps in this change are small but public. They affect application porting and integration points:

- `pi-tui` exposes global raw input listeners and a debug hook before focused component routing.
- `pi-tui` exposes `Editor.insertTextAtCursor`; the upstream coding agent uses it in `packages/coding-agent/src/modes/interactive/interactive-mode.ts` to insert selected file paths.
- `pi-tui` can use forced autocomplete to apply an unambiguous file completion.
- `pi-tui` exposes terminal input draining before shutdown.
- `pi-tui` treats Insert as a named key; siglyph currently parses CSI `2~` as `TerminalKey.Unknown("insert")`.

Constraints:

- Keep siglyph's typed `TerminalInput` model.
- Do not add runtime dependencies.
- Keep Windows, browser, and Scala.js support out of scope.
- Preserve current default behavior unless a parity behavior is explicitly opt-in.

## Goals / Non-Goals

**Goals:**

- Add typed global input listeners that run before focused component input.
- Add a public editor insertion API that inserts text at the current cursor using existing editor-buffer semantics.
- Add opt-in forced autocomplete single-result auto-apply behavior.
- Add an optional input-drain terminal capability without widening the base `Terminal` trait.
- Add `TerminalKey.Insert` and parse known insert-key escape sequences to it.
- Add tests, Scaladoc, and docs for each public API or behavior change.

**Non-Goals:**

- Do not add raw-string input routing to components.
- Do not replace the typed keybinding model with `pi-tui`'s TypeScript key helper API.
- Do not add Windows VT input support.
- Do not add Darwin native modifier probing.
- Do not add browser or Scala.js backends.
- Do not add runtime dependencies.
- Do not make forced autocomplete auto-apply the default behavior.

## Decisions

### Typed global input listeners return `InputResult`

Add global listeners to `TUI` as typed callbacks that receive `TerminalInput` and return `InputResult`. The runtime invokes listeners before terminal protocol handling that belongs to components and before focused component routing. If a listener returns handled or exit behavior, focused routing does not receive that input.

Rationale:

- Reuses the existing result model.
- Avoids introducing a second handled/ignored type.
- Keeps listener behavior testable with `VirtualTerminal`.

Alternative considered: callbacks returning `Boolean`. Rejected because it cannot request render or exit without a second side channel.

### Editor insertion is a public mutation API, not a terminal input escape hatch

Add a Scala-idiomatic method such as `insertAtCursor(text: String): Unit` to `Editor`. It uses the same normalization and editor-buffer insertion path as paste and typed input, creates one undo snapshot for the call, invokes `onChange` when text changes, refreshes autocomplete consistently with existing mutation rules, and requests render through the existing context when attached.

Rationale:

- It supports upstream agent use cases that programmatically insert a file path.
- It avoids asking applications to synthesize fake terminal input.
- It keeps the mutation inside the editor abstraction.

Alternative considered: expose `EditorBuffer` mutation directly. Rejected because it leaks editor internals and bypasses callbacks, undo, autocomplete state, and render requests.

### Forced autocomplete auto-apply is opt-in

Add an editor autocomplete option for single-result forced completion auto-apply. When explicit forced autocomplete returns exactly one valid suggestion and the option is enabled, the editor applies that completion through the existing provider completion contract instead of opening the overlay. Empty results and multiple results keep current behavior.

Rationale:

- Matches the useful `pi-tui` behavior for unambiguous file completion.
- Preserves siglyph's safer explicit-selection default.
- Reuses stale-request checks and completion application logic.

Alternative considered: make auto-apply the default for forced autocomplete. Rejected because it changes current behavior for existing applications.

### Terminal input drain is an optional capability

Add an optional terminal capability, for example `TerminalInputDrainSupport`, instead of adding `drainInput` to the base `Terminal` trait. `TUI.stop()` or backend-specific shutdown paths can call it when present and skip it when absent.

Rationale:

- Backends differ in whether draining is meaningful.
- `StreamTerminal` and `VirtualTerminal` can remain simple.
- The base terminal contract remains stable.

Alternative considered: add `drainInput` to `Terminal`. Rejected because it would force a no-op method onto every backend.

### Insert becomes a first-class typed key

Add `TerminalKey.Insert` and update the parser to emit it for CSI `2~` and any existing supported modified insert sequence. Keybindings can then match Insert without using `Unknown("insert")`.

Rationale:

- It closes a concrete typed-key parity gap.
- It removes stringly typed handling for a standard key.
- It is small and testable.

Alternative considered: keep `Unknown("insert")`. Rejected because the key is standard and already has a known identity.

## Risks / Trade-offs

- **Listener ordering ambiguity** → Document that global listeners run before focused components and that handled or exit results stop further routing.
- **Listeners can starve focused components** → Tests must cover ignored listener results and handled listener results.
- **Editor insertion can bypass expected user input paths** → The method must share existing mutation, undo, callback, paste-marker, autocomplete, and render code paths.
- **Auto-apply can surprise users** → Keep it opt-in and limit it to forced requests with exactly one valid suggestion.
- **Drain can block shutdown** → Capability implementations must use bounded timeouts or non-blocking behavior and tests must verify stop remains idempotent.
- **Insert key changes matching behavior** → Update parser and keybinding tests so the old `Unknown("insert")` path is not required for standard Insert input.

## Migration Plan

1. Add typed API surfaces and tests in core.
2. Add optional drain support where backends can implement it safely.
3. Update docs, porting notes, and Scaladoc.
4. Preserve existing defaults: no global listeners by default, no forced auto-apply by default, no required drain capability.
5. Validate with `mill __.compile`, focused tests, `mill core.test`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.

Rollback is source-level: remove the new APIs and delta specs before release if implementation shows an unacceptable public API shape. No persisted data migration is required.

## Open Questions

None.
