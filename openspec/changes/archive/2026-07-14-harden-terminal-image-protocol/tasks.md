## 1. Validated Payload Boundary

- [x] 1.1 Add public shared-core `scalatui.terminal.Base64ImagePayload` and `scalatui.terminal.Base64ImagePayloadError.InvalidStandardBase64` with a non-public unchecked construction path and Scaladoc covering JVM/Native scope and image-format non-goals.
- [x] 1.2 Implement `scalatui.terminal.Base64ImagePayload.from(String)` with the same lexical contract on JVM and Scala Native: accept only the standard alphabet and valid terminal padding; accept empty input and decoder-valid unpadded lengths with remainder two or three modulo four unchanged; reject remainder one, whitespace, controls, URL-safe alphabet, framing delimiters, and invalid padding with `InvalidStandardBase64`.
- [x] 1.3 Implement `scalatui.terminal.Base64ImagePayload.encode(Array[Byte])` as standard padded base64 without adding a runtime dependency.
- [x] 1.4 Add focused shared JVM/Native tests for original-text preservation; padded and empty input; every unpadded modulo-four length class, including decoder-valid remainder-two and remainder-three acceptance and remainder-one rejection; byte encoding; malformed padding; whitespace; controls; URL-safe alphabet; Kitty ST injection; and iTerm2 BEL injection.

## 2. Breaking Protocol API and Metadata Safety

- [x] 2.1 Replace raw base64 `String` parameters in Kitty/iTerm2 protocol helpers with `scalatui.terminal.Base64ImagePayload`, append valid payload text unchanged, and add no compatibility overload, adapter, fallback, or deprecation path.
- [x] 2.2 Encode each present iTerm2 filename from UTF-8 bytes to standard base64 in `name=` and omit the complete name field when filename is absent.
- [x] 2.3 Add protocol tests proving typed invalid input cannot reach emission, valid padded/unpadded/empty payload text remains unchanged, Unicode filenames are encoded correctly, absent filenames omit `name=`, and framing injection cannot terminate output.
- [x] 2.4 Wire `TerminalImageProtocolSuite` into `coreNative.test` and run the same protocol security cases on JVM and Scala Native.

## 3. Breaking Image API and Fallback Safety

- [x] 3.1 Change `ImageSource` to store `scalatui.terminal.Base64ImagePayload` and update file loading to use `scalatui.terminal.Base64ImagePayload.encode`.
- [x] 3.2 Change the low-level `Image` construction path to require `scalatui.terminal.Base64ImagePayload` and add `scalatui.image.Image.fromBase64` as the high-level raw-string factory returning `Either[scalatui.terminal.Base64ImagePayloadError, Image]` before component construction; add no compatibility overload, adapter, exception-only rejection, invalid-payload fallback, or deprecation path.
- [x] 3.3 Escape controls in fallback filename and MIME values to visible text before existing ANSI-aware truncation, without silently filtering metadata.
- [x] 3.4 Update all repository image call sites and tests for the breaking payload field and signature changes.
- [x] 3.5 Add JVM and Native image tests for typed no-component rejection, validated payload rendering, control-safe visible fallback metadata, width truncation after escaping, and unchanged supported-protocol output.

## 4. Public Documentation and Examples

- [x] 4.1 Add or update Scaladoc for `scalatui.terminal.Base64ImagePayload`, `scalatui.terminal.Base64ImagePayloadError`, their factories, protocol helpers, `ImageSource`, and `scalatui.image.Image.fromBase64`, including exact packages, the transient decoded-byte validation allocation, JVM/Native lexical parity, and explicit non-goals.
- [x] 4.2 Update README and relevant project documentation with the exact public names `scalatui.terminal.Base64ImagePayload`, `scalatui.terminal.Base64ImagePayloadError`, and `scalatui.image.Image.fromBase64`; typed payload construction and failure; modulo-four acceptance rules; JVM/Native parity; iTerm2 filename behavior; fallback escaping; and source-breaking migration.
- [x] 4.3 Update image examples to use `scalatui.terminal.Base64ImagePayload` or `scalatui.image.Image.fromBase64` and remove every documented direct raw-string protocol/component call shape.
- [x] 4.4 Verify documentation contains no compatibility overload, adapter, fallback rendering for invalid payloads, or deprecation work.

## 5. Validation

- [x] 5.1 Run focused JVM tests with `mill core.test.testOnly scalatui.terminal.TerminalImageProtocolSuite` and `mill image.test`.
- [x] 5.2 Run Native parity tests with `mill coreNative.test` and `mill imageNative.test`.
- [x] 5.3 Run full compile and test validation with `mill __.compile` and `mill __.test`.
- [x] 5.4 Run quality checks with `mill scalafmtCheck` and `mill scalafixCheck`.
- [x] 5.5 Inspect `build.mill` and resolved module changes to confirm no third-party runtime dependency was added.
- [x] 5.6 Run `openspec validate --all --strict` and resolve every validation error.
- [x] 5.7 Verify security review repairs: private emitted JVM construction and typed null rejection.
- [x] 5.8 Strengthen Kitty/escape ordering tests and compile the image example against current local sources.
- [x] 5.9 Replace payload-slicing lexical checks with one indexed O(1)-state scan, add large padded and unpadded parity tests, and retain the separately accepted decoder output allocation.
