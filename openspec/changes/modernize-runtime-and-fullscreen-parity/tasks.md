## 1. Baseline and characterization

- [x] 1.1 Pin and record the reviewed `earendil-works/pi` revision, inventory current fullscreen exports and tests, and update comparison fixtures without changing Siglyph behavior.
- [x] 1.2 Add normal-screen characterization tests for first render, differential tail render, resize policies, overlays, cursor placement, typed controls, append-only output, stop positioning, and failure cleanup.
- [x] 1.3 Add width-only alternate-screen characterization tests for entry, full redraw, resize, overlays, typed controls, and cleanup so later policy extraction cannot silently change existing output.
- [x] 1.4 Add test-only runtime counters needed to compare component renders, painted rows, terminal writes, control encodes, and search scans without exposing application text.

## 2. Terminal worker failure propagation

- [x] 2.1 Add the source-compatible three-callback `Terminal.start` overload and document expected failure, EOF, interruption, generation, and callback-separation behavior.
- [x] 2.2 Extend `StreamTerminal` to report each unexpected reader and fragment-flush worker failure at most once per worker and active generation while preserving independent later failures, finite EOF, and explicit-stop behavior.
- [x] 2.3 Extend `SttyTerminal` to report each unexpected input, fragment-flush, and resize-worker failure at most once per worker and active generation without losing independent later failures or cleanup obligations.
- [x] 2.4 Extend `PosixTerminal` to classify native read errors, report each unexpected input, fragment-flush, and resize-worker failure at most once per worker and active generation, and preserve independent later failures, interruption, and stale-generation behavior.
- [x] 2.5 Route backend failure into TUI lifecycle control so `run()` wakes, first failure wins, later failures are suppressed, ordinary work is discarded, retained completions finish, and cleanup does not join the reporting worker.
- [x] 2.6 Add portable and backend-specific tests for reader, flush, and resize failures, concurrent failures, stop races, stale generations, EOF, callback separation, and terminal restoration on JVM and Native.

## 3. Component threading and callback isolation

- [x] 3.1 Define the shared built-in component state-boundary helper or pattern and add contract tests for mutation, rendering, callback order, reentrancy, and failure.
- [x] 3.2 Make `Loader` state snapshots and mutations consistent across scheduler ticks, message and indicator updates, start, stop, cancellation, context changes, and rendering on JVM and Native.
- [x] 3.3 Refactor Editor input, mutation, submit, undo, history, programmatic insertion, and change callbacks into snapshot, effect, and commit phases without changing visible editing behavior.
- [x] 3.4 Refactor autocomplete provider invocation, synchronous completion, asynchronous completion, cancellation handles, overlay effects, and render requests so application code runs outside Editor state locks and stale generations remain rejected.
- [x] 3.5 Audit `Input`, `SelectList`, `SettingsList`, loaders, composites, overlay callbacks, theme hooks, and context propagation for application code called under component or runtime state locks and apply the same isolation rule.
- [x] 3.6 Add lock-inversion, slow-callback, synchronous-provider, concurrent-completion, callback-failure, and concurrent Loader mutation tests with deterministic gates rather than elapsed-time success assertions.

## 4. Session capabilities

- [x] 4.1 Add typed tri-state overrides for true color, hyperlinks, and image protocol and resolve one immutable effective capability set per TUI session.
- [x] 4.2 Expose effective capabilities through `TUIContext`, preserve explicit component-level capabilities, and verify concurrent runtime isolation.
- [x] 4.3 Add documented Zed detection fixtures and retain conservative multiplexer precedence unless an instance override is explicit.
- [x] 4.4 Update image, Markdown hyperlink, and other capability-aware components to use their attached session without adding process-global mutable state.
- [x] 4.5 Add JVM and Native tests for detected, partially overridden, disabled, forced, multiplexer, detached-component, and concurrent-session behavior.

## 5. Renderer policy extraction

- [x] 5.1 Define internal shared runtime service and renderer-policy boundaries without replacing the public `TUI` facade or existing constructors.
- [x] 5.2 Move current normal-screen frame preparation, differential output, resize clear, append-only output, cursor parking, and control cleanup into `NormalScreenPolicy` while preserving characterization output.
- [x] 5.3 Move current width-only alternate-buffer behavior behind its existing option without reinterpreting it as a fullscreen viewport.
- [x] 5.4 Keep lifecycle, ingress, queries, focus, overlays, diagnostics, capability session state, typed validation, failure handling, and synchronized terminal writes in shared ownership.
- [x] 5.5 Add policy contract tests proving identical cleanup, query ordering, callback isolation, overlay behavior, typed-control authority, and default normal-screen behavior.

## 6. Height-aware layout foundation

- [x] 6.1 Add immutable viewport rectangles, clip rectangles, layout boxes, layout frames, and the optional viewport layout-provider contract while keeping `Component.render(width)` unchanged.
- [x] 6.2 Implement deterministic stack allocation for basis, grow, shrink, minimum, maximum, gap, alignment, visibility, and stable integer remainder distribution.
- [x] 6.3 Implement `VStack` with direct unbounded rendering, height-aware child allocation, typed metadata translation, context propagation, invalidation, and retained bounds.
- [x] 6.4 Implement `HStack` with ANSI-safe and wide-grapheme-safe horizontal composition, height alignment, typed metadata translation, and retained bounds.
- [x] 6.5 Adapt width-only components as measured viewport leaves with one render per identity and width per frame, local validation before translation, and deterministic row clipping.
- [x] 6.6 Add typed optional document metadata with prompt-start markers, normalized document positions, layout translation, and ordinary-string authority isolation.
- [x] 6.7 Add JVM and Native layout tests for nested stacks, over-constrained minimums, zero and narrow dimensions, responsive visibility, metadata confinement, typed prompt markers, wide cells, overlays, and direct-render fallback.

## 7. Fullscreen viewport and basic scrolling

- [x] 7.1 Add source-compatible opt-in fullscreen viewport construction and `FullscreenViewportPolicy` with fixed terminal-height painting and alternate-screen lifecycle.
- [x] 7.2 Implement `ScrollView` as a one-child vertical viewport with bounded offset, content extent, viewport extent, primary designation, and direct unbounded rendering.
- [x] 7.3 Implement follow-end behavior, manual follow suppression, jump-to-start, jump-to-end, content-shrink clamp, and resize clamp.
- [x] 7.4 Implement nested pointer-targeted scroll routing and chain or contain overscroll behavior against committed layout geometry.
- [x] 7.5 Add optional range-rendering support for large components while preserving complete-render and clip fallback for ordinary width-only components.
- [x] 7.6 Integrate fullscreen overlays, cursor placement, typed controls, resize invalidation, focus routing, and cleanup with shared runtime services.
- [x] 7.7 Add VirtualTerminal and Native-compatible tests for fixed-height frames, sticky transcript and editor layouts, nested scrolling, follow-end, resize, overlays, rejected stale frames, and normal-screen isolation.

## 8. Rich typed mouse foundation

- [x] 8.1 Extend SGR parsing and typed input for pointer motion and pressed-button state without losing existing press, release, wheel, modifiers, raw fallback, or fragmentation behavior.
- [x] 8.2 Add semantic move, press, release, click, multi-click, drag, and wheel events with committed absolute and component-local geometry.
- [x] 8.3 Add typed handler results for handled state, render intent, pointer capture, and focus intent and integrate them with the serialized focus and render paths.
- [x] 8.4 Implement capture cleanup on release, structural removal, overlay removal, hidden state, stop, failure, and committed-layout replacement.
- [x] 8.5 Add `MouseRegion` with transparent child rendering, metadata preservation, context propagation, and semantic-event callbacks.
- [x] 8.6 Select and lifecycle-manage xterm tracking modes `1000`, `1002`, and `1003` with SGR coordinates in interactive JVM and Native backends, including conservative multiplexer behavior.
- [x] 8.7 Add parser, gesture-state, capture, focus, overlay, nested-layout, removal, multi-click clock, tracking-mode lifecycle, and JVM and Native backend protocol tests before adding pointer-driven viewport widgets.

## 9. Viewport keybindings and affordances

- [x] 9.1 Add typed commands and configurable defaults for line, half-page, page, document-edge, previous-prompt, next-prompt, search, copy-selection, and clear-selection actions.
- [x] 9.2 Implement previous-prompt and next-prompt navigation from typed document markers without recognizing OSC-looking ordinary strings.
- [x] 9.3 Implement routing precedence from focused search or overlay to focused component, viewport handler, and primary scroll-view fallback.
- [x] 9.4 Implement hidden, automatic, and always-visible proportional scrollbar geometry and rendering without temporary raw mouse handling.
- [x] 9.5 Integrate scrollbar track clicks and thumb dragging through the completed semantic mouse and capture contracts.
- [x] 9.6 Implement the configurable jump-to-end indicator and route pointer activation through semantic mouse clicks before resuming follow-end behavior.
- [x] 9.7 Add pointer-targeted viewport scrolling, Alt-wheel acceleration, component click handling, and hover behavior that does not silently move selector state.
- [x] 9.8 Add keybinding conflict, unbound-command, editor-precedence, overlay-precedence, typed prompt marker, scrollbar keyboard and pointer interaction, jump-to-end, selector hover, narrow-width, and resize tests.

## 10. Transcript search

- [x] 10.1 Build a bounded search index over normalized rendered primary-scroll-view text keyed by content revision and width without retaining stale revisions.
- [x] 10.2 Add a focus-capturing search layer with query editing, result count, current match, close, next, and previous actions.
- [x] 10.3 Reveal the current match, preserve search state during manual scrolling, and highlight only visible matches without leaking ANSI style across cells or rows.
- [x] 10.4 Add tests for large unchanged transcripts, Unicode and wide graphemes, fragmented input, resize and reflow, no matches, repeated matches, overlays, manual scrolling, and cache invalidation.

## 11. Selection and clipboard behavior

- [x] 11.1 Map committed viewport cells to normalized plain-text grapheme offsets without copying ANSI metadata, terminal controls, or partial wide cells.
- [x] 11.2 Implement primary-button drag selection, bounded edge auto-scroll, deterministic clamp or clear behavior after content replacement, and projection after resize.
- [x] 11.3 Implement tested double-click word selection and triple-click normalized-line selection with injected monotonic time and configured distance bounds.
- [x] 11.4 Add typed explicit copy targets with a portable host callback and clear supported, unsupported, success, and failure results.
- [x] 11.5 If OSC 52 support is retained after security review, encode it only through a size-bounded typed control-output path with standard base64 and explicit per-session capability; otherwise document host-only copy as the supported baseline.
- [x] 11.6 Add selection tests for wrapping, Unicode clusters, paths and kebab-case tokens, scrolling, resize, content replacement, overlays, unsupported copy, callback failure, payload bounds, and terminal-control injection.

## 12. Viewport image integration

- [x] 12.1 Apply viewport, scroll-view, stack, and overlay clip rectangles to typed image footprints before protocol encoding.
- [x] 12.2 Define safe protocol-specific behavior for partially visible Kitty and iTerm2 images without raw escape rewriting or partially executing a typed control.
- [x] 12.3 Add bounded per-TUI Kitty retention metadata for recently offscreen images with deterministic count and generation-age eviction.
- [x] 12.4 Preserve image ID ownership, payload redaction, append-only output isolation, retransmission cleanup, eviction cleanup, and stop cleanup.
- [x] 12.5 Add image viewport tests for sticky-region clipping, nested scrolling, overlays, offscreen return, cache hit, eviction, content update, resize, iTerm2 fallback, concurrent sessions, and cleanup failure.

## 13. Performance workloads

- [x] 13.1 Add dedicated deterministic benchmark targets and fixed workloads for large transcript layout, append, differential tail changes, Unicode reflow, overlays, nested scrolling, search, selection mapping, and image-heavy frames.
- [x] 13.2 Assert stable algorithmic counters in ordinary regression tests for visible-row painting, render reuse, search scanning, image encoding, and terminal writes.
- [x] 13.3 Add JVM timing and allocation reports with warmup, repeated medians, exact workload metadata, and documented invocation outside the normal unit-test path.
- [x] 13.4 Add representative Scala Native functional performance smoke coverage and record any tooling gap without introducing a runtime dependency.
- [x] 13.5 Establish reviewed baseline results and regression ratios only in a controlled benchmark job; keep machine-speed timing out of ordinary correctness gates.

## 14. Documentation and examples

- [x] 14.1 Refresh `docs/pi-tui-compatibility.md` and `docs/porting-notes.md` against the pinned current upstream revision with full, partial, deviation, and extension evidence.
- [x] 14.2 Correct README component and parity claims and document normal-screen, width-only alternate-screen, and fullscreen viewport construction separately.
- [x] 14.3 Add public Scaladoc for terminal failure reporting, component threading, renderer policies, layout types, scroll state, keybindings, mouse gestures, selection, clipboard targets, capability overrides, image clipping, and benchmark scope.
- [x] 14.4 Add JVM and Scala Native examples for a growing transcript with primary follow-end scrolling, editor and footer regions, search, mouse interaction, selection, and safe shutdown.
- [x] 14.5 Update interactive smoke and terminal conformance documentation for resize, nested scrolling, search, selection, clipboard support, image clipping, worker failure, and terminal restoration.
- [x] 14.6 Reconcile publishing and version documentation and avoid repeating current upstream completeness claims without a pinned matrix.

## 15. Final validation and review

- [x] 15.1 Run focused JVM core, terminal, Markdown, image, extras, demo, viewport, mouse, search, selection, and benchmark-counter tests.
- [x] 15.2 Run focused Scala Native core, terminal, image, layout, viewport, mouse, and demo tests and build the Native interactive demo.
- [ ] 15.3 Run PTY lifecycle, resize, protocol, selection-copy, and restoration tests on supported Linux and macOS environments and state any exact platform gap.
- [x] 15.4 Run `mill __.compile`, the complete test targets, `mill scalafmtCheck`, `mill scalafixCheck`, `openspec validate --all --strict`, and `git diff --check`.
- [x] 15.5 Perform independent correctness, concurrency, terminal-protocol security, public API, and compatibility review and resolve every blocking finding before completion.
