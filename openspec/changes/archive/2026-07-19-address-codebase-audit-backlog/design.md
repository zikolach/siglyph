## Context

The retained audit backlog contains nine independent findings across rendering, CI, Native test coverage, current product identity, Markdown failure handling, image metadata validation, input-result precision, and concurrency-test synchronization. They share final repository validation but do not share implementation dependencies. Existing ANSI-aware width utilities, typed `InvalidImage`, `InputResult`, canonical shared source roots, and standard Scala concurrency primitives provide the required mechanisms without new dependencies.

## Goals / Non-Goals

**Goals:**
- Implement each retained finding as an independently executable workstream.
- Preserve existing public APIs, callback conditions, callback cardinality, package names, and dependency boundaries. Extend image fallback behavior only for render geometry above the approved 10,000-frame-row limit.
- Reuse canonical source and test suites across JVM and Scala Native where portability classification permits.
- Finish with one integration group that depends on all nine workstreams.

**Non-Goals:**
- Generic refactoring, TUI decomposition, or performance optimization beyond preventing the approved oversized image frame allocation.
- Renaming the `scalatui` package or imports.
- Editing archives, changelog history, historical notes, other changes, example dependency versions, `AGENTS.md`, `CLAUDE.md`, or similar agent-rule files. Agent-rule files remain unchanged unless their modification is separately and explicitly approved.
- Adding dependencies, compatibility layers, fallback paths beyond the approved oversized image geometry use of the existing fallback, duplicated Native suites, or broad formatting changes.
- Implementing any rejected or unretained audit idea.

## Decisions

### 1. Extras final themed width safety

Apply each existing theme callback first, then pass every final themed line through the existing ANSI-aware truncation utility at the requested width. Focused tests use themes that add visible prefixes or suffixes and verify visible width, including narrow and zero widths where the component contract permits them.

This order is required because truncating before a callback cannot constrain visible characters introduced by that callback. Reimplementing ANSI truncation inside extras is rejected because the shared utility already owns escape and grapheme handling.

### 2. Mandatory CI quality gates and complete Scalafix roots

Update the existing CI workflow so `mill scalafmtCheck` and `mill scalafixCheck` are mandatory commands whose non-zero status fails the workflow. Audit Mill Scalafix inputs against canonical current Scala production and test roots, including shared roots compiled by mirror modules, and wire missing canonical roots once rather than checking generated or duplicated views. OpenSpec validation is not a CI requirement and does not require CI provisioning.

A permissive or advisory CI step is rejected because it does not enforce the quality contract. New lint tooling is rejected because the repository already has Scalafmt and Scalafix.

#### Scalafix source-root audit evidence

- Canonical production roots: `core/src`, `terminalJvm/src`, `terminalNative/src`, `markdown/src`, `image/src`, `extras/src`, `demo/src`, `asciinemaDemo/src`, `keyTester/src`, `interactiveDemo/src`, `interactiveJvmDemo/src`, and `interactiveNativeDemo/src`.
- Canonical test roots: `core/test`, `terminalJvm/test`, `terminalNative/test`, `markdown/test`, `image/test`, `extras/test`, and `interactiveDemo/test`.
- The audit adds the previously missing `terminalJvm/test`, `terminalNative/test`, and `interactiveDemo/test` roots. Native modules reuse canonical roots and do not add duplicate mirror roots.
- `scripts/GenerateUnicodeTables.scala` remains a direct Scalafix input. Scalafix excludes generated `core/src/scalatui/unicode/UnicodeTables.scala` and `core/test/src/scalatui/unicode/UnicodeGraphemeBreakFixtures.scala` through its `--exclude` option.
- Scala files under `examples` are outside Mill production and test source roots and remain outside this audit.

#### Complete-scope Scalafix correction evidence

- The shell command builds positional parameters with `set --` and appends one `--files` argument per source path. The direct `scalafix` and `cs launch` branches receive the same argument list without `eval` or string reconstruction.
- The command retains exactly two exclusions: generated `UnicodeTables.scala` and `UnicodeGraphemeBreakFixtures.scala`.
- The first complete verbose run processed 117 Scala files and reported 46 violations: 39 universal-equality diagnostics and seven `return` diagnostics.
- The 39 equality diagnostics were in `TUISuite.scala`, `TUIConcurrencySuite.scala`, `KeybindingManagerSuite.scala`, `EditorSuite.scala`, `SttyTerminalSuite.scala`, `SiglyphJvmInteropCompileSuite.scala`, `PosixTerminal.scala`, `PosixTerminalSuite.scala`, and `AsciinemaDemo.scala`. Together with the preserved equality corrections in `scripts/GenerateUnicodeTables.scala`, the workstream verifies ten equality-cleanup files.
- The seven `return` diagnostics were the six JPEG exits in `Image.scala` and the missing-path exit in `InteractiveDemo.scala`.
- Focused JVM and Native tests cover JPEG marker-fill scanning, segment advancement, exact failures and precedence, successful dimensions, and terminal missing-path dispatch. The final verbose run processed the same 117 files with zero violations.

### 3. Portable Native test expansion

Classify existing core, extras, and interactive-demo suites before changing Mill wiring. Each suite is either portable unchanged, portable after a legitimate shared portability correction, or platform-specific. Reuse portable canonical test files through Mill source-root configuration; never copy suites into Native modules. Platform-specific suites remain on their current platform and are documented in the classification.

Blindly attaching every JVM suite is rejected because JVM-only test APIs can make Native targets invalid. Copying tests is rejected because canonical test behavior would diverge.

### 4. Current product identity normalization

Replace current product-facing `scala-tui` identity with `siglyph` in active metadata, current documentation, build-facing labels, and source comments or Scaladoc that describe the present product. Keep the public package and imports as `scalatui`. Exclude `openspec/changes/archive/**`, changelog history, historical notes, text that intentionally names an upstream or prior state, example dependency versions, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files. Identity searches classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved.

Before archive, classify exactly three promoted `openspec/specs/developer-api/spec.md` occurrences as pending synchronization: the Purpose metadata phrase that identifies the product as `scala-tui`; the `Mill Scala 3 modular architecture` requirement phrase that names the library `scala-tui`, targeted by this change's complete modified requirement; and the `Public markdown/autocomplete contracts are composable` scenario phrase `runtime dependencies to scala-tui`, targeted by this change's complete modified requirement. Every other in-scope current-product occurrence must be resolved before the identity workstream completes.

A package rename is rejected because it is outside scope and breaking. A blind repository-wide replacement is rejected because it would corrupt history and technical identifiers.

### 5. Basic Markdown parser failure boundary

Change only the dependency-free basic parser recovery boundary to match `scala.util.control.NonFatal`. Recoverable parser failures continue through the existing readable rendering behavior. Fatal JVM and runtime failures are not intercepted.

A custom fatal-error list is rejected because Scala already defines the standard boundary. Catching `Throwable` is rejected because it can hide process-level failures.

### 6. Typed image dimension validation

Decode encoded dimensions into a wide intermediate type wherever the source format can exceed the public representation. Route all sniffed dimensions through one existing or consolidated validation point that requires both values to be positive and representable by `ImageDimensions`. Return the existing typed `InvalidImage` for zero, negative, overflowed, or otherwise non-representable values, and construct `ImageDimensions` only after validation.

Scattered format-specific checks are rejected because they can drift. Clamping, defaults, and new failure types are rejected because they would add fallback behavior or change the typed contract.

JPEG marker scanning uses subtraction-based available-byte checks. Loop continuation never evaluates `offset + constant`, and segment validation never evaluates `offset + segmentLength` before proving the requested bytes fit. The existing typed messages and their precedence remain unchanged.

Image metadata validation remains independent from rendering: every positive dimension representable by `Int` remains valid metadata. At render time, the image component calculates the supported protocol geometry before protocol control creation. Geometry through exactly 10,000 frame rows retains normal image-control behavior. Geometry above 10,000 rows uses the same readable fallback path as unsupported capability, then applies the existing theme callback and final ANSI-aware width truncation. The guard runs before protocol rendering, frame-row `Vector` allocation, and image-id allocation or update. The limit is fixed and has no configuration.

This is the approved application-content limit and fallback extension. Clamping geometry, returning silent empty output, catching allocation errors, adding an error type or configuration option, and changing dimension sniffing are rejected.

### 7. Direct Markdown width-zero safety

Handle width zero at the public direct render path before block rendering can create visible markers, prefixes, or separators. The result contains no line with positive visible width. Positive-width behavior remains unchanged and retains final ANSI-aware width enforcement.

Relying only on parent TUI sanitization is rejected because direct renderer calls are part of the existing contract.

### 8. Precise `Input` and `SelectList` results

Audit each recognized command and unsupported input branch against actual effects:
- Return `Ignored` when no command or action matches.
- Return `NoRender` when a recognized action is handled but causes no repaint-requiring visible change or callback.
- Return `Render` when mutation, visible state, or an existing callback condition requires repaint.

Preserve every current callback condition and invocation count. Do not claim or add parent bubbling; `Ignored` only reports that the component did not handle the event. Implement and test `Input` and `SelectList` separately within this workstream so their command tables remain local.

Returning `Render` for every recognized or unsupported event is rejected because it causes redundant repainting. Changing callbacks to fit result values is rejected because result precision must describe existing behavior rather than redefine it.

#### Input result classification evidence

`Input` keeps this dispatch order: undo; delete word backward; delete to line start; delete to line end; delete character forward; delete word forward; delete character backward; yank; yank-pop; word left; word right; left; right; line start; line end; submit; newline; printable fallback. A custom binding that matches multiple commands uses the first command in this list.

| Input action | Result |
| --- | --- |
| Any editing or cursor command above | `Render` when exposed value or cursor changes; otherwise `NoRender` |
| Submit | Invoke `onSubmit` once and return `Render` |
| Newline | `Render` |
| Printable fallback without Control, Alt, or Super | `Render` for non-empty text; `NoRender` for empty text |
| Unsupported manager command, key, or raw event | `Ignored` |
| Paste start | `Render` only if finalizing an active decoder exposes flushed text; otherwise `NoRender` |
| Active paste chunk | `Render` when decoding accepts text and changes exposed value or cursor; otherwise `NoRender` |
| Orphan paste chunk | `Ignored` |
| Paste end | `Render` only if decoder flush exposes text; otherwise `NoRender`, including an orphan end |
| Normal input during paste | Combine local finalization and normal-event results; decoder-flush output or a normal `Render` wins, and unsupported input after a no-visible-change finalization is `NoRender` |

#### SelectList result classification evidence

| SelectList action | Result |
| --- | --- |
| Up or Down | `Render` when index or scroll changes; otherwise `NoRender` |
| Enter with a selected item | Invoke `onSelect` once and return `Render` |
| Enter without a selected item | `NoRender` |
| Escape | Invoke `onCancel` once and return `Render` |
| Backspace with filtering enabled and a non-empty query | Update the filter and return `Render` |
| Non-empty printable text without Control, Alt, or Super and with filtering enabled | Update the filter and return `Render` |
| Unsupported key or raw event | `Ignored` |
| Any paste frame with filtering disabled | `Ignored` |
| Paste start with filtering enabled | `Render` when it commits accepted prior paste text; otherwise `NoRender` |
| Active paste chunk with filtering enabled | `NoRender` until commit |
| Orphan paste chunk with filtering enabled | `Ignored` |
| Paste end with filtering enabled | `Render` when it commits accepted text; otherwise `NoRender` |
| Normal input during paste | Combine local commit and normal-event results; a filter commit or normal `Render` wins, and unsupported input after an empty finalization is `NoRender` |

Selection callbacks retain structural equality semantics. Moving between equal duplicate `SelectItem` values suppresses `onSelectionChange`, while index or scroll movement still returns `Render`.

### 9. Deterministic concurrency-test synchronization

Classify each sleep in affected tests by intent. Replace sleeps used only to wait for readiness or ordering with explicit gates, latches, barriers, or observable conditions. Every wait has a bounded timeout and failure message. Retain sleeps whose duration is itself the behavior under test, such as timeout or polling semantics.

Unbounded waits are rejected because failures could hang CI. Shorter sleeps are rejected because they remain scheduler-dependent.

#### Native test classification evidence

- Portable unchanged: every canonical `*Suite.scala` under `core/test/src` except the three files listed below, plus `extras/test/src/scalatui/extras/ExtrasSuite.scala` and `interactiveDemo/test/src/scalatui/demo/InteractiveDemoSuite.scala`.
- Portable after a valid shared portability correction: `core/test/src/scalatui/core/TUISuite.scala` and `core/test/src/scalatui/terminal/StreamTerminalSuite.scala` use standard bounded `java.util.concurrent.CountDownLatch`, `TimeUnit`, and direct `Thread.join`; `core/test/src/scalatui/terminal/VirtualTerminalSuite.scala` uses the Native-compatible private CSI regex in `core/src/scalatui/terminal/VirtualTerminal.scala`.
- Platform-specific: no suite in the canonical core, extras, or interactive-demo roots. `terminalJvm/test/src/scalatui/terminal/jvm/SttyTerminalSuite.scala` and `terminalNative/test/src/scalatui/terminal/native/PosixTerminalSuite.scala` remain in their platform modules.
- Mill reuses `core/test/src`, `extras/test/src`, and `interactiveDemo/test/src` directly in the corresponding Native test modules. No test suite is copied.

#### Concurrency sleep inventory

- Readiness or ordering only: seven 50 ms sleeps in `core/test/src/scalatui/core/TUISuite.scala`, one 100 ms sleep in `core/test/src/scalatui/terminal/StreamTerminalSuite.scala`, and one 1 ms polling sleep in `terminalNative/test/src/scalatui/terminal/native/PosixTerminalSuite.scala`. Deterministic bounded synchronization replaces all nine sleeps.
- Intentional timeout or polling behavior: `core/test/src/scalatui/terminal/StreamTerminalSuite.scala` retains one 150 ms sleep to span more than one 75 ms flush period. `terminalJvm/test/src/scalatui/terminal/jvm/SttyTerminalSuite.scala` retains one 1 ms sleep to poll the real clock until a strict zero-length deadline expires.
- The affected suites contain exactly these two documented sleeps after the synchronization changes. All waits and joins are bounded.

### Final integration dependency

The integration group starts only after all nine workstream completion tasks. It checks the scoped diff, compiles all modules, runs focused JVM and Native tests, runs PTY-backed full tests where terminal behavior requires a pseudo-terminal, and executes formatting, Scalafix, strict OpenSpec, dependency, and stale-name checks. Stale-name searches explicitly exclude archives, changelog history, historical notes, package/import identifier `scalatui`, and approved upstream or prior-state references.

### Promoted specification synchronization

Application implementation does not directly edit promoted specs. Normal archive synchronization applies both complete `developer-api` modified requirements, including `Public markdown/autocomplete contracts are composable`. Because delta syntax cannot modify capability Purpose metadata, the archive handoff must also change the Purpose in `openspec/specs/developer-api/spec.md` from identifying the product as `scala-tui` to identifying it as `siglyph`. After synchronization, none of the three pending occurrences remains.

## Risks / Trade-offs

- [Theme callbacks can emit malformed ANSI] → Use the existing ANSI-aware truncation contract and add focused callback-output tests; do not invent recovery behavior.
- [A canonical test suite may use a hidden JVM-only API] → Complete portability classification before wiring and keep genuinely platform-specific suites excluded with evidence.
- [Identity search can flag intentional historical or package references] → Use explicit path and identifier exclusions, then review each remaining match rather than bulk replacing.
- [Large positive image metadata can produce excessive render geometry] → Keep metadata valid, but route geometry above 10,000 frame rows through the existing themed, width-safe fallback before image-id or frame-row allocation.
- [Wide image decoding can be format-sensitive] → Test boundary encodings for every supported sniffer and centralize validation before construction.
- [Input-result changes can accidentally alter callbacks] → Assert both result value and callback count for mutation, recognized no-op, and unsupported input cases.
- [Latch-based tests can deadlock after a failure] → Bound every wait and release blocked workers in test cleanup where required.
- [Nine workstreams increase integration surface] → Keep them independent and require the final integration group to run only after every group completion task.
