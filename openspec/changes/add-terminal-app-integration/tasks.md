## 1. Public terminal integration API

- [x] 1.1 Add optional title and progress capability APIs without adding abstract methods to `Terminal`.
- [x] 1.2 Add shared models for RGB color and terminal color scheme with values `dark` and `light`.
- [x] 1.3 Add Scaladoc for title, progress, background color, and color-scheme APIs.

## 2. Runtime protocol handling

- [x] 2.1 Implement OSC 11 background color request emission, parsing, timeout handling, and tests.
- [x] 2.2 Implement terminal color-scheme query parsing, timeout handling, and tests.
- [x] 2.3 Implement color-scheme notification enable/disable and listener unsubscribe behavior.
- [x] 2.4 Intercept OSC 11 and color-scheme protocol replies before focused component input routing.

## 3. Backend and test support

- [x] 3.1 Implement title/progress support for interactive JVM and Native backends where the output protocol is available.
- [x] 3.2 Add virtual terminal support for asserting emitted title, progress, and query sequences.
- [x] 3.3 Verify stream or unsupported terminals report unsupported capability results without emitting unsupported escapes.

## 4. Documentation and validation

- [x] 4.1 Update README or runtime docs with title, progress, and terminal color query examples.
- [x] 4.2 Update porting notes with upstream parity and intentional unsupported-terminal behavior.
- [x] 4.3 Run `mill __.compile`, relevant unit tests, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
