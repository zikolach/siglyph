## Context

The retained audit backlog contains nine independent findings across rendering, CI, Native test coverage, current product identity, Markdown failure handling, image metadata validation, input-result precision, and concurrency-test synchronization. They share final repository validation but do not share implementation dependencies. Existing ANSI-aware width utilities, typed `InvalidImage`, `InputResult`, canonical shared source roots, and standard Scala concurrency primitives provide the required mechanisms without new dependencies.

## Goals / Non-Goals

**Goals:**
- Implement each retained finding as an independently executable workstream.
- Preserve existing public APIs, callback conditions, callback cardinality, package names, dependency boundaries, and supported fallback behavior.
- Reuse canonical source and test suites across JVM and Scala Native where portability classification permits.
- Finish with one integration group that depends on all nine workstreams.

**Non-Goals:**
- Generic refactoring, TUI decomposition, or performance optimization.
- Renaming the `scalatui` package or imports.
- Editing archives, changelog history, historical notes, other changes, example dependency versions, `AGENTS.md`, `CLAUDE.md`, or similar agent-rule files. Agent-rule files remain unchanged unless their modification is separately and explicitly approved.
- Adding dependencies, compatibility layers, fallback paths, duplicated Native suites, or broad formatting changes.
- Implementing any rejected or unretained audit idea.

## Decisions

### 1. Extras final themed width safety

Apply each existing theme callback first, then pass every final themed line through the existing ANSI-aware truncation utility at the requested width. Focused tests use themes that add visible prefixes or suffixes and verify visible width, including narrow and zero widths where the component contract permits them.

This order is required because truncating before a callback cannot constrain visible characters introduced by that callback. Reimplementing ANSI truncation inside extras is rejected because the shared utility already owns escape and grapheme handling.

### 2. Mandatory CI quality gates and complete Scalafix roots

Update the existing CI workflow so Scalafmt checking, Scalafix checking, and `openspec validate --all --strict` are mandatory commands whose non-zero status fails the workflow. Audit Mill Scalafix inputs against canonical current Scala production and test roots, including shared roots compiled by mirror modules, and wire missing canonical roots once rather than checking generated or duplicated views.

A permissive or advisory CI step is rejected because it does not enforce the quality contract. New lint tooling is rejected because the repository already has Scalafmt, Scalafix, and OpenSpec.

### 3. Portable Native test expansion

Classify existing core, extras, and interactive-demo suites before changing Mill wiring. Each suite is either portable unchanged, portable after a legitimate shared portability correction, or platform-specific. Reuse portable canonical test files through Mill source-root configuration; never copy suites into Native modules. Platform-specific suites remain on their current platform and are documented in the classification.

Blindly attaching every JVM suite is rejected because JVM-only test APIs can make Native targets invalid. Copying tests is rejected because canonical test behavior would diverge.

### 4. Current product identity normalization

Replace current product-facing `scala-tui` identity with `siglyph` in active metadata, current documentation, build-facing labels, and source comments or Scaladoc that describe the present product. Keep the public package and imports as `scalatui`. Exclude `openspec/changes/archive/**`, changelog history, historical notes, text that intentionally names an upstream or prior state, example dependency versions, `AGENTS.md`, `CLAUDE.md`, and similar agent-rule files. Identity searches classify agent-rule files as excluded rather than stale identity work unless their modification is separately and explicitly approved.

Before archive, classify only two promoted `openspec/specs/developer-api/spec.md` occurrences as pending synchronization: the Purpose metadata phrase that identifies the product as `scala-tui`, and the `Public markdown/autocomplete contracts are composable` scenario phrase `runtime dependencies to scala-tui` targeted by this change's complete modified requirement. Every other in-scope current-product occurrence must be resolved before the identity workstream completes.

A package rename is rejected because it is outside scope and breaking. A blind repository-wide replacement is rejected because it would corrupt history and technical identifiers.

### 5. Basic Markdown parser failure boundary

Change only the dependency-free basic parser recovery boundary to match `scala.util.control.NonFatal`. Recoverable parser failures continue through the existing readable rendering behavior. Fatal JVM and runtime failures are not intercepted.

A custom fatal-error list is rejected because Scala already defines the standard boundary. Catching `Throwable` is rejected because it can hide process-level failures.

### 6. Typed image dimension validation

Decode encoded dimensions into a wide intermediate type wherever the source format can exceed the public representation. Route all sniffed dimensions through one existing or consolidated validation point that requires both values to be positive and representable by `ImageDimensions`. Return the existing typed `InvalidImage` for zero, negative, overflowed, or otherwise non-representable values, and construct `ImageDimensions` only after validation.

Scattered format-specific checks are rejected because they can drift. Clamping, defaults, and new failure types are rejected because they would add fallback behavior or change the typed contract.

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

### 9. Deterministic concurrency-test synchronization

Classify each sleep in affected tests by intent. Replace sleeps used only to wait for readiness or ordering with explicit gates, latches, barriers, or observable conditions. Every wait has a bounded timeout and failure message. Retain sleeps whose duration is itself the behavior under test, such as timeout or polling semantics.

Unbounded waits are rejected because failures could hang CI. Shorter sleeps are rejected because they remain scheduler-dependent.

### Final integration dependency

The integration group starts only after all nine workstream completion tasks. It checks the scoped diff, compiles all modules, runs focused JVM and Native tests, runs PTY-backed full tests where terminal behavior requires a pseudo-terminal, and executes formatting, Scalafix, strict OpenSpec, dependency, and stale-name checks. Stale-name searches explicitly exclude archives, changelog history, historical notes, package/import identifier `scalatui`, and approved upstream or prior-state references.

### Promoted specification synchronization

Application implementation does not directly edit promoted specs. Normal archive synchronization applies both complete `developer-api` modified requirements, including `Public markdown/autocomplete contracts are composable`. Because delta syntax cannot modify capability Purpose metadata, the archive handoff must also change the Purpose in `openspec/specs/developer-api/spec.md` from identifying the product as `scala-tui` to identifying it as `siglyph`. After synchronization, neither pending occurrence remains.

## Risks / Trade-offs

- [Theme callbacks can emit malformed ANSI] → Use the existing ANSI-aware truncation contract and add focused callback-output tests; do not invent recovery behavior.
- [A canonical test suite may use a hidden JVM-only API] → Complete portability classification before wiring and keep genuinely platform-specific suites excluded with evidence.
- [Identity search can flag intentional historical or package references] → Use explicit path and identifier exclusions, then review each remaining match rather than bulk replacing.
- [Wide image decoding can be format-sensitive] → Test boundary encodings for every supported sniffer and centralize validation before construction.
- [Input-result changes can accidentally alter callbacks] → Assert both result value and callback count for mutation, recognized no-op, and unsupported input cases.
- [Latch-based tests can deadlock after a failure] → Bound every wait and release blocked workers in test cleanup where required.
- [Nine workstreams increase integration surface] → Keep them independent and require the final integration group to run only after every group completion task.
