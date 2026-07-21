## Context

The project now has all named `pi-tui` component categories and a broad JVM/Scala Native test suite, but the review identified several edge cases where matching names conceal different behavior. The most important are structural line endings being treated as ordinary C0 text, a typed key model whose shared parser does not emit several represented keys, and terminal cell dimensions stored in a process-global singleton. The same review found cross-thread cancellation, filesystem completion, repeated Markdown rendering, resize policy, diagnostics, terminal-fidelity validation, and project-status documentation that need hardening.

The implementation must preserve Siglyph's stronger typed-input and trusted-output boundaries, its dependency-light shared core, and source compatibility for existing terminal implementations and normal `TUIOptions` construction. JVM and Scala Native shared sources remain canonical. No production parser, terminal-emulator, effect-runtime, or filesystem-search dependency is approved by this change.

## Goals / Non-Goals

**Goals:**

- Close each concrete correctness and design finding with a testable contract.
- Improve behavioral parity without copying Node-specific architecture into shared Scala code.
- Make terminal-derived state and diagnostics instance-scoped.
- Keep public additions source-compatible and default behavior unchanged where practical.
- Add macOS and PTY coverage proportional to the documented platform scope.
- Publish a traceable, version-pinned compatibility matrix.

**Non-Goals:**

- Exact behavioral compatibility with every terminal emulator or every historical key encoding.
- Full Windows console support.
- Replacing the baseline Markdown parser with a mandatory CommonMark dependency.
- Adding a loader timer, effect system, background executor, or application task framework.
- Replacing all `VirtualTerminal` tests with slow integration tests.
- Changing the trusted ordinary-string output policy to execute CR, LF, or arbitrary controls.

## Decisions

### 1. Treat line endings as component structure before ANSI sanitization

Add a shared logical-line wrapper used by multiline text components. It recognizes CRLF as one boundary and lone CR or LF as equivalent boundaries, then runs each logical line through the existing forward ANSI scanner and width wrapper. Accepted SGR/OSC 8 state may be closed and replayed between output rows using the same bounded state already used for wrapping. Other C0 bytes remain subject to ordinary-string sanitization.

This belongs above `Ansi.wrapTextWithAnsi`: changing that low-level utility to make newlines executable would weaken its existing contract for callers that intentionally process a single ordinary terminal line. The implementation will begin with `Text`, route extras through the corrected primitive where applicable, and add fixtures for all three line endings, empty logical lines, wrapping, and ANSI spans crossing boundaries.

Alternative considered: allow CR/LF directly through the trusted-output compiler. Rejected because line structure should be represented in `ComponentRender.lines`, not smuggled through terminal bytes.

### 2. Use one table-driven shared key decoder and a parity corpus

Refactor the fixed sequence mapping into declarative entries containing byte sequence, typed key, modifiers, and optional terminal/protocol notes. Cover common CSI and SS3 F1-F12, SS3 cursors, legacy modifier variants, supported control bytes, and functional keypad code normalization. CSI-u decoding continues to handle event metadata. Platform-specific fallbacks remain behind backend compatibility boundaries and feed the same typed model.

Unknown complete sequences continue through the bounded raw representation; ambiguity is documented rather than guessed. A golden corpus runs against the shared parser and both backend input-buffer contract suites, with upstream sequence names recorded only as test provenance.

Alternative considered: add cases incrementally to the current pattern match. Rejected because the matrix is already large enough that omissions and inconsistent modifiers are difficult to audit.

### 3. Move terminal cell metrics into `TUIContext`

Each `TUI` stores its current `ImageCellDimensions`, initialized to the deterministic fallback and updated only by replies correlated in that runtime. `TUIContext` gains a concrete default cell-metrics accessor so existing external context implementations remain source-compatible. `Image` becomes contextual and resolves `Runtime` sizing from its attached context; fixed sizing remains completely pure.

Built-in composites that own child components propagate context attach/detach operations to contextual descendants, including children added after attachment. A small shared propagation helper avoids each composite inventing lifecycle semantics. Tests cover direct, nested, overlay, detached, and two-concurrent-runtime images.

The runtime stops calling the process-global `TerminalImageProtocol` cache. Existing public global accessors may remain temporarily as deprecated low-level compatibility helpers, but high-level images and TUI query handling neither read nor mutate them. A follow-up major release may remove those deprecated helpers.

Alternative considered: use thread-local geometry. Rejected because rendering and query callbacks can move between threads and multiple TUI instances can legitimately share one thread.

### 4. Make cancellation an atomic state transition

Implement `CancellationToken` with the shared JVM/Native atomic Boolean support. `cancel()` performs one compare-and-set; only its winner invokes `onCancel` and requests the cancellation render. Reads use the atomic value, providing the documented visibility guarantee without an executor or effect type.

The callback still runs synchronously on the winning caller. This preserves existing semantics and avoids broadening the loader into an asynchronous runtime. Callback exceptions keep their current propagation behavior, while the cancelled state remains committed.

Alternative considered: `@volatile` plus a check/set pair. Rejected because visibility alone does not make the winner or callback invocation atomic.

### 5. Separate completion context from containment policy

Extend filesystem options without removing the legacy base-directory field:

- `currentDirectory` controls how relative display tokens are resolved.
- `containmentRoots` controls which canonical targets may be disclosed.
- parent traversal is allowed only while canonical paths remain contained.
- home and absolute paths remain explicit opt-ins and still require an allowed canonical root.
- symlinks are checked before suggestion or recursive descent.

When only the legacy base directory is supplied, it becomes both current directory and sole containment root, preserving existing callers. The direct provider checks cancellation between directory entries, applies its scan bound, sorts the evaluated candidate set with the documented stable comparator, then applies the result bound. Documentation will no longer claim cross-filesystem determinism for which entries appear when enumeration reaches the scan bound.

Recursive attachment search is a separate opt-in provider/mode using an iterative NIO traversal with depth, visited-entry, and result limits plus cancellation checkpoints. It requires no `fd` executable and never follows an escaping symlink.

Alternative considered: match upstream by invoking `fd`. Rejected because an external executable would make behavior and installation platform-dependent.

### 6. Cache one Markdown component render

Each `Markdown` component keeps at most one successful `ComponentRender`. The cache key contains text, effective width, padding, renderer identity, and a renderer cache generation. `MarkdownRenderer` receives a source-compatible default generation member; immutable renderers retain generation zero, while a mutable custom renderer must advance it or explicitly invalidate the component. The text setter and `invalidate()` clear the cache.

One-entry caching captures the common static-message case without retaining an unbounded width/document map. Parser fallback output is cacheable because it is a successful visible result; fatal exceptions are not cached. The same shared implementation runs on JVM and Native.

Alternative considered: a global LRU keyed by document text. Rejected because it retains application content across components and complicates renderer identity and privacy.

### 7. Add structured diagnostics and a two-value resize policy

Add backend-independent diagnostic event ADTs and an observer to `TUIOptions`. Events contain operation type, rows/columns, byte counts, clear reason, screen mode, and lifecycle state as applicable; they contain no rendered text, image payload, or raw protocol reply. Events are emitted in runtime order outside lifecycle and terminal-write locks. A throwing observer is disabled for that TUI instance and cannot prevent terminal cleanup or trigger recursive diagnostic reporting.

Add `NormalResizeClearPolicy.ClearScrollback` and `PreserveScrollback`. `ClearScrollback` remains the default and emits the existing `CSI 2 J`, `CSI H`, `CSI 3 J` sequence. `PreserveScrollback` emits the viewport clear/home sequence but omits `CSI 3 J`. Alternate-screen behavior is unchanged because alternate buffers have separate scrollback semantics.

Alternative considered: environment-variable debug logging. Rejected because process-global configuration and raw write capture conflict with session isolation and redaction goals.

### 8. Use layered terminal validation

Keep `VirtualTerminal` as the default fast fake and improve only the semantics needed by new conformance cases, including grapheme display width, wide-cell cursor advance, autowrap, and the erase operations exercised by the renderer. Add a small PTY-backed terminal lifecycle suite for raw mode, resize notification, write ordering, and restoration on Linux and macOS. The suite uses platform-aware repository scripts and OS facilities rather than a production dependency.

CI gains a macOS job that compiles shared/JVM/Native terminal paths and runs the relevant tests and quality gates. Linux remains the packaging job. Platform-specific exclusions must be named in the workflow rather than silently skipped.

Alternative considered: introduce xterm-headless solely for tests. Deferred because it brings Node into the build and is unnecessary for the first targeted conformance layer.

### 9. Make compatibility status a maintained project artifact

Add a version-pinned matrix to porting documentation with major areas marked full, partial, intentional deviation, or extension. Each partial area names its limitation and local spec/test evidence. Correct the `../` smoke example to match the new configured policy, update stale post-MVP references, and archive the completed mouse-review change once this change no longer depends on its active location.

The compatibility estimate is descriptive, not a release gate or computed percentage. Component-name coverage is reported separately from behavioral parity.

## Risks / Trade-offs

- **[Context propagation changes component attachment behavior]** -> Centralize propagation, make attach/detach idempotent, and test nested mutation plus duplicate references before removing runtime use of global metrics.
- **[The expanded key table can misclassify ambiguous bytes]** -> Admit only documented complete sequences, retain raw fallback, and add negative-prefix and fragmentation tests.
- **[Preserving scrollback can leave stale terminal rows on some emulators]** -> Keep full clearing as the default and document the visual trade-off of the opt-in policy.
- **[Recursive completion can scan large trees]** -> Require independent depth, visited-entry, and result bounds with frequent cancellation and containment checks.
- **[Caching can return stale custom-renderer output]** -> Include renderer generation, clear on explicit invalidation, and document the mutable-renderer responsibility.
- **[Diagnostic callbacks add hot-path overhead or fail]** -> Perform no event allocation when disabled, expose metadata only, invoke outside locks, and disable a failing observer.
- **[macOS CI increases latency and can expose environment-specific failures]** -> Keep packaging on Linux, scope macOS to platform-sensitive compile/tests, and cache Mill/Coursier artifacts per OS.
- **[PTY tests can be flaky]** -> Use deterministic time bounds, no interactive human input, guaranteed `stty` restoration, and narrow platform-specific assertions.
- **[Deprecated global image helpers retain a low-level footgun]** -> Remove all runtime/high-level use now, mark the helpers clearly, and schedule removal only through a separately documented breaking change.

## Migration Plan

1. Add source-compatible public ADTs/default methods and regression tests before switching behavior.
2. Implement logical-line rendering, the key table, atomic cancellation, and Markdown caching behind the existing component APIs.
3. Introduce session metrics and context propagation, migrate `TUI` and `Image`, then deprecate unused global geometry helpers.
4. Extend filesystem completion policies and recursive search, then align demo and smoke examples.
5. Add diagnostics and resize policy with legacy defaults, followed by PTY and macOS CI coverage.
6. Update the compatibility matrix and roadmap, validate all specs and modules, and archive superseded completed OpenSpec work.

Rollback is componentized: each behavior can be reverted independently while the new option defaults preserve old runtime behavior. If session-context migration must be rolled back, fixed image dimensions remain the safe deterministic fallback; the process-global cache must not be silently reintroduced into high-level rendering.

## Open Questions

- Which Apple-specific modifier probes can be implemented safely with the existing JVM and Native compatibility layers will be decided from focused platform tests; unsupported probes will remain documented raw/plain-key fallbacks rather than expanding this change to a new native dependency.
- A full CommonMark-compatible adapter remains a separately approved optional dependency decision. This change only prepares correct cache invalidation and updates the parity documentation.
