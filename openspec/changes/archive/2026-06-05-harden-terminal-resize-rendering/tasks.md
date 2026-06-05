## 1. Runtime Render Safety

- [x] 1.1 Clamp `TUI` render width and height to at least 1 before rendering
- [x] 1.2 Track previous terminal height alongside previous width
- [x] 1.3 Full-redraw when terminal height changes
- [x] 1.4 Add ANSI-safe final-line sanitization for over-wide rendered output
- [x] 1.5 Add runtime diagnostics for sanitized lines without adding runtime dependencies
- [x] 1.6 Ensure over-wide rendered output from input handling does not throw an uncaught backend-thread exception
- [x] 1.7 Preserve normal terminal restoration path for unrecoverable render failures

## 2. Core Runtime Tests

- [x] 2.1 Add tests for zero/invalid terminal dimensions clamping to positive render dimensions
- [x] 2.2 Add tests for height resize triggering full redraw
- [x] 2.3 Add tests for over-wide line sanitization before terminal write
- [x] 2.4 Add tests for ANSI-styled and Unicode over-wide line sanitization
- [x] 2.5 Add tests that input-triggered over-wide rendering remains handled without uncaught exception
- [x] 2.6 Update existing line-overflow test expectations to match no-crash runtime behavior

## 3. JVM Resize Notifications

- [x] 3.1 Add mutable/current dimension tracking to the JVM interactive terminal backend
- [x] 3.2 Add dependency-free resize polling using `stty size < /dev/tty`
- [x] 3.3 Invoke `onResize` when JVM terminal dimensions change
- [x] 3.4 Stop JVM resize polling idempotently during backend stop
- [x] 3.5 Add JVM backend tests or test seams for resize polling/dimension update behavior where practical

## 4. Native Resize Notifications

- [x] 4.1 Add or reuse mutable/current dimension tracking in the Native POSIX backend
- [x] 4.2 Add dependency-free resize polling using `ioctl(TIOCGWINSZ)`
- [x] 4.3 Invoke `onResize` when Native terminal dimensions change
- [x] 4.4 Stop Native resize polling idempotently during backend stop
- [x] 4.5 Validate Native resize polling compiles and links

## 5. Demo Narrow-Width Hardening

- [x] 5.1 Make shared interactive demo headings and section labels width-safe
- [x] 5.2 Add tests or testable demo rendering coverage for widths 1, 10, 22, 40, and 80
- [x] 5.3 Ensure demo remains interactive after narrow-width resize in virtual-terminal coverage where practical

## 6. Documentation

- [x] 6.1 Update README or docs to describe resize and narrow-width behavior
- [x] 6.2 Update interactive smoke docs with JVM and Native resize checks
- [x] 6.3 Document that components still must obey width contracts even though `TUI` sanitizes final output
- [x] 6.4 Update porting notes with the intentional improvement over `pi-tui` stop-then-throw overflow behavior

## 7. Validation

- [x] 7.1 Run `mill scalafmtCheck`
- [x] 7.2 Run `mill scalafixCheck`
- [x] 7.3 Run `mill quality`
- [x] 7.4 Run `mill __.compile`
- [x] 7.5 Run `mill core.test`
- [x] 7.6 Run `mill interactiveJvmDemo.compile`
- [x] 7.7 Run `mill interactiveNativeDemo.nativeLink`
- [x] 7.8 Run `openspec validate --all --strict`
