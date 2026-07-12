## Why

Terminal image payload and metadata strings can currently contain protocol terminators or executable terminal controls. The image API must validate payloads before output and encode or escape metadata so untrusted values cannot break Kitty, iTerm2, or fallback framing.

## What Changes

- Add shared-core `scalatui.terminal.Base64ImagePayload` with typed `scalatui.terminal.Base64ImagePayloadError` construction failure and byte encoding, without a runtime dependency.
- Accept only the standard base64 alphabet and valid terminal padding. Accept decoder-valid unpadded lengths whose remainder modulo four is two or three, reject remainder one, accept empty input, and preserve accepted original text unchanged on JVM and Scala Native.
- **BREAKING**: Replace raw base64 `String` parameters and stored fields in image protocol helpers, `ImageSource`, and `Image` construction paths with `scalatui.terminal.Base64ImagePayload`; provide no compatibility overload or deprecation path.
- Add `scalatui.image.Image.fromBase64` as the high-level raw-string image factory returning `Either[scalatui.terminal.Base64ImagePayloadError, Image]`; invalid input produces no component or protocol sequence.
- Encode an iTerm2 filename from its UTF-8 bytes using standard base64, omit `name=` when no filename exists, and preserve validated payload text unchanged in Kitty and iTerm2 output.
- Escape filename and MIME controls into visible text before ANSI-aware fallback truncation.
- Preserve JVM and Scala Native behavior parity and wire the core Native protocol suite into validation.
- Update source/API documentation, examples, and call sites for the breaking typed-payload contract.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `image-rendering`: Require validated base64 payloads, typed no-output rejection, protocol-safe iTerm2 filename metadata, safe fallback metadata, and JVM/Native parity.
- `developer-api`: Define the breaking public typed image payload and failure API, raw-string factory result, documentation obligations, and dependency constraints.

## Impact

Affected areas include shared core image payload types and protocol helpers, the optional image component API and `ImageSource`, image call sites and examples, fallback rendering, protocol tests on JVM and Native, and public documentation. Existing callers that pass or read raw base64 strings must migrate explicitly to `scalatui.terminal.Base64ImagePayload` or `scalatui.image.Image.fromBase64`; no runtime dependency is added.
