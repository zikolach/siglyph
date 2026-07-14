## Context

The public image path currently accepts raw base64 `String` values in shared protocol helpers and the optional image component. Those strings are interpolated into Kitty and iTerm2 control sequences. iTerm2 filename metadata is also interpolated without the protocol-required base64 encoding, while fallback filename and MIME text can carry terminal controls.

The fix crosses shared core, the JVM/Native image module, call sites, documentation, and protocol suites. It must reject unsafe payloads before a sequence or component exists, retain accepted payload spelling, preserve JVM/Native parity, and add no runtime dependency.

## Goals / Non-Goals

**Goals:**

- Make `scalatui.terminal.Base64ImagePayload` the only payload type accepted by protocol emitters and stored by `ImageSource`.
- Make `scalatui.image.Image.fromBase64` return `Either[scalatui.terminal.Base64ImagePayloadError, Image]` before protocol or component output for invalid raw strings.
- Accept only the standard base64 alphabet and valid terminal padding. Accept decoder-valid unpadded lengths with remainder two or three modulo four, reject remainder one, accept empty input, and preserve accepted source text exactly.
- Encode iTerm2 filenames as standard base64 over UTF-8 and omit absent filename metadata.
- Render filename and MIME controls as visible escaped text before ANSI-aware truncation.
- Keep implementation and security tests shared across JVM and Scala Native where possible.

**Non-Goals:**

- Image chunking or size limits.
- Terminal capability detection changes.
- Kitty image identifiers.
- Image row calculation.
- Image format decoding or validation.
- Metadata dimension validation.

## Decisions

### 1. Put the validated payload boundary in shared core

`scalatui.terminal.Base64ImagePayload` is a public shared-core value with a non-public raw constructor. `scalatui.terminal.Base64ImagePayload.from(String)` returns `Either[scalatui.terminal.Base64ImagePayloadError, scalatui.terminal.Base64ImagePayload]`; `scalatui.terminal.Base64ImagePayload.encode(Array[Byte])` always returns a valid value. `scalatui.terminal.Base64ImagePayloadError` is typed and includes `InvalidStandardBase64`.

Protocol helpers accept only `scalatui.terminal.Base64ImagePayload`. `ImageSource` stores that type. The low-level `Image` construction path also accepts that type. `scalatui.image.Image.fromBase64` is the high-level raw-string factory; it validates and returns `Either[scalatui.terminal.Base64ImagePayloadError, Image]`. Invalid raw input therefore cannot create a component or reach sequence construction.

Alternative considered: validate inside each encoder or throw from constructors. This duplicates the security boundary and permits exception-only failure, so it is rejected.

### 2. Validate lexical safety and standard-base64 decoding

`scalatui.terminal.Base64ImagePayload.from` first enforces one lexical contract on JVM and Scala Native. Input may contain only the standard base64 alphabet (`A-Z`, `a-z`, `0-9`, `+`, `/`) plus optional terminal `=` padding. When padding is present, it must be valid terminal padding. Empty input is valid. Unpadded input with length remainder two or three modulo four is accepted only when decoder-valid; remainder one is rejected with `InvalidStandardBase64`. This excludes whitespace, control characters, URL-safe `-` and `_`, BEL, ESC/C1 framing controls, and other protocol delimiters. Decoder validation then rejects any remaining malformed input. On success, the value stores the original string without normalization, padding, or re-encoding.

Lexical validation makes one indexed pass over the original string with O(1) state and no payload-sized substring allocation. Decoder validation then allocates a decoded byte array proportional to payload size and discards it. This transient decoder memory cost is accepted because no size limit is in scope. Shared lexical checks fix padding and modulo-length behavior before decoding, so JVM and Scala Native expose the same acceptance boundary. A custom base64 decoder was considered, but it would duplicate mature decoding logic and increase parity risk.

### 3. Encode metadata at its output boundary

For iTerm2, a present filename is converted to UTF-8 bytes and standard-base64 encoded for the `name=` field. An absent filename emits no `name=` field. MIME and other existing protocol fields retain their current protocol treatment unless this change explicitly covers them. Validated image payload text is appended unchanged for both Kitty and iTerm2.

Alternative considered: filter unsafe filename characters. Filtering changes metadata silently and does not implement the iTerm2 protocol requirement, so it is rejected.

### 4. Escape fallback controls before width handling

Fallback rendering converts control code points in filename and MIME metadata to visible escaped text before composing the fallback row. Existing ANSI-aware visible-width truncation then runs on the escaped row. No executable control from those metadata values reaches terminal output.

Alternative considered: strip controls or replace the entire fallback. Both silently lose information and violate the required visible representation, so they are rejected.

### 5. Make the API break explicit and migrate all repository callers

Raw-string protocol/component signatures and the `ImageSource` payload field are replaced directly. There are no compatibility overloads, adapters, deprecated members, or fallback rendering for invalid payloads. Repository call sites, examples, Scaladoc, and user documentation migrate to the validated type or typed factory in the same change.

### 6. Validate shared behavior on JVM and Native

Security cases cover padded and empty input; unpadded lengths in every modulo-four class, including decoder-valid remainder-two and remainder-three input and rejected remainder-one input; malformed padding; whitespace; controls; URL-safe alphabet; Kitty ST and iTerm2 BEL injection attempts; unchanged valid payload output; UTF-8 filename encoding; omitted filename; and escaped fallback metadata. The same fixtures run on JVM and Scala Native. The core Native test module is wired to run the shared terminal image protocol suite; image-module tests run for JVM and Native.

## Risks / Trade-offs

- [Transient decoded-byte allocation can approach the encoded payload size] → Keep lexical validation to an indexed O(1)-state scan, document the separate decoder allocation, and keep chunking or size limits explicitly out of scope.
- [JVM and Native decoder behavior could diverge] → Use the same shared implementation and fixtures, and run the core protocol suite on both targets.
- [The direct signature and field replacement breaks source compatibility] → Mark the change as breaking and update all in-repository call sites and documentation; do not add compatibility work.
- [Escaped control text is wider than the source metadata] → Escape before existing ANSI-aware truncation so final fallback width remains bounded.
