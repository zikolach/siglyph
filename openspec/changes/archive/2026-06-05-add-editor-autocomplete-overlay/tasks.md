## 1. Public Models and Context APIs

- [x] 1.1 Add typed overlay public models in core: anchors, size values, margins, options, resolved layout, overlay ids, and overlay handles with Scaladoc.
- [x] 1.2 Add a backend-independent TUI context or host capability exposing render requests, exit requests, focus operations, and overlay host access.
- [x] 1.3 Add autocomplete public models in core: request snapshots, items, suggestions, completion requests/results, callbacks, cancellable handles, and provider contracts with Scaladoc.
- [x] 1.4 Add synchronous autocomplete helper/adapter and no-op cancellable handle for simple providers.
- [x] 1.5 Add slash-command helper models/providers where applications supply command metadata and command execution remains outside the runtime.

## 2. Overlay Layout and Compositing

- [x] 2.1 Implement pure overlay layout resolution matching pi-tui precedence: width/minWidth/maxHeight, absolute/percentage/anchor position, offsets, margins, and bounds clamping.
- [x] 2.2 Implement ANSI-aware rectangular overlay compositing where overlay cells replace base cells and spaces are literal.
- [x] 2.3 Clip or truncate overlay lines to resolved overlay width before compositing while preserving ANSI escape correctness.
- [x] 2.4 Add unit tests for overlay layout resolution across anchors, absolute positions, percentage positions, offsets, margins, min width, max height, and narrow dimensions.
- [x] 2.5 Add unit tests for ANSI-styled overlay compositing, overlapping overlays, literal spaces, hidden overlays, and over-wide overlay lines.
- [x] 2.6 Preserve base render height when no visible overlays are composited.
- [x] 2.7 Preserve terminal scrollback on initial TUI start while retaining clear redraws for resize.
- [x] 2.8 Avoid padding every visible overlay frame to full terminal height; extend only to required overlay rows.

## 3. TUI Overlay Runtime

- [x] 3.1 Add an overlay stack to `TUI` with handle-based show, hide, hidden-state toggling, focus, unfocus, and stable internal ids.
- [x] 3.2 Integrate overlay compositing into `TUI.renderNow` after base component rendering and before final line resets/sanitization.
- [x] 3.3 Implement overlay visibility predicates and re-resolve overlay layout every render using positive current terminal dimensions.
- [x] 3.4 Route input to the topmost visible focus-capturing overlay, falling back to the focused base component when no capturing overlay is active.
- [x] 3.5 Implement focus restoration when focused overlays are hidden, removed, made invisible, or unfocused.
- [x] 3.6 Add virtual terminal tests for overlay rendering, overlay input routing, focus restoration, z-order, and resize redraw behavior.

## 4. Editor Autocomplete Integration

- [x] 4.1 Extend `EditorOptions` and `Editor` with autocomplete provider, maximum visible suggestions, trigger behavior, and context/overlay host attachment.
- [x] 4.2 Implement autocomplete request lifecycle: snapshot current text/cursor, cancel stale request handles, accept only current callbacks, and request renders through context.
- [x] 4.3 Reuse or extend `SelectList` for autocomplete suggestions with keyboard navigation, selected item access, width-safe rendering, and maximum visible rows.
- [x] 4.4 Show, update, and close editor autocomplete suggestions through the overlay stack rather than appending suggestions to editor text.
- [x] 4.5 Handle autocomplete keys: Tab to request/accept, Up/Down to navigate, Enter to accept, Escape to cancel, and text mutation to refresh or cancel stale suggestions.
- [x] 4.6 Apply selected completions through provider completion results while preserving editor callbacks and Unicode-safe cursor movement.
- [x] 4.7 Add editor tests for slash-command suggestions, async callback ordering, stale result rejection, cancellation, keyboard navigation, completion application, and narrow-width overlay safety.
- [x] 4.8 Add editor autocomplete overlay placement configuration so applications can update suggestion overlay options from layout code.

## 5. Demos and Documentation

- [x] 5.1 Update the shared interactive demo to include application-supplied slash-command autocomplete using the new overlay stack.
- [x] 5.2 Keep JVM and Scala Native demo launchers thin and verify both use the same shared autocomplete demo logic.
- [x] 5.3 Update README and interactive smoke docs with overlay/autocomplete behavior, key controls, demo commands, and resize expectations.
- [x] 5.4 Update porting notes to document pi-tui parity and intentional deviations, including callback-based autocomplete instead of whole-runtime tagless final.
- [x] 5.5 Position shared demo slash-command suggestions immediately after the rendered editor area instead of at terminal bottom.

## 6. Validation

- [x] 6.1 Run `mill scalafmtAll` if formatting changes require it.
- [x] 6.2 Run `mill scalafmtCheck`.
- [x] 6.3 Run `mill scalafixCheck`.
- [x] 6.4 Run `mill quality`.
- [x] 6.5 Run `mill __.compile`.
- [x] 6.6 Run `mill core.test` and `mill interactiveDemo.test`.
- [x] 6.7 Run `mill interactiveJvmDemo.compile` and `mill interactiveNativeDemo.nativeLink`.
- [x] 6.8 Run `openspec validate --all --strict`.
