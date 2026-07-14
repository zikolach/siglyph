## Why

Components currently return untyped strings, so the runtime cannot distinguish ordinary application text from library-generated terminal protocols. Strict output sanitization therefore either permits attacker-controlled strings to execute image controls or disables the supported Kitty/iTerm2 image feature.

## What Changes

- **BREAKING**: Replace `Component.render(width): Vector[String]` with a typed render result containing ordinary lines and positioned semantic terminal controls.
- Add a closed shared-core terminal-control model whose raw encodings are not application-constructible strings.
- Preserve strict sanitization for every ordinary rendered string while encoding typed controls only at the final TUI output boundary.
- Change image protocol results and the optional `Image` component to carry typed Kitty/iTerm2 controls plus reserved rows instead of embedding raw protocol sequences in text lines.
- Preserve typed controls through containers, frame builders, overlay composition, differential rendering, resize redraws, and JVM/Scala Native shared sources.
- Require positive image geometry and positive Kitty IDs at construction and final encoding, with bounded ID allocation that fails before integer wraparound.
- Treat non-positive image render width as geometry clipping that returns empty ordinary output with no protocol control.
- Reject a final frame containing more than one active Kitty image control with the same semantic integer ID before output; cleanup controls do not participate in this uniqueness rule.
- Treat control-vector order as semantic differential state so a pure reorder redraws from the earliest affected row in the new order.
- Require uppercase-I targeted Kitty deletion before every retransmission of an ID from the previous frame, and when removing an old ID without replacement.
- Keep validation failures and terminal-control string diagnostics bounded and free of payloads, filenames, controls, placements, and application text.
- Normalize negative `Box` padding to zero once and use the normalized geometry for child rendering, text, controls, and frame size.
- Execute typed TUI and overlay suites from the shared `coreNative` test target.
- Remove every string-prefix trust decision, arbitrary trusted-string path, compatibility render path, and image-protocol parser from ordinary text handling.
- Add no runtime dependency.

## Capabilities

### New Capabilities
- `trusted-terminal-output`: Closed semantic terminal-control values, positioned render output, provenance preservation, final-boundary encoding, and strict separation from ordinary strings.

### Modified Capabilities
- `component-rendering`: Replace line-only component output with typed frame composition and define overlay/differential behavior for positioned controls.
- `developer-api`: Define the source-breaking shared JVM/Native component render result and document migration and non-goals.
- `image-rendering`: Require image components to emit typed semantic controls while preserving validated payloads, row reservation, cleanup, and fallback behavior.
- `terminal-runtime`: Require final output to sanitize ordinary text separately from encoding validated typed controls.

## Impact

- Affected public APIs: `Component.render`, component implementations, `ComponentFrameBuilder.result`, and image protocol render results.
- Affected runtime paths: container/frame composition, overlays, cursor-marker preparation, differential frame state, full/partial redraws, resize handling, image cleanup, and virtual-terminal tests.
- Affected modules: shared `core`/`coreNative`, `image`/`imageNative`, Markdown, extras, demos, examples, and JVM interop smoke code where component rendering is used.
- Existing third-party component implementations must migrate directly; no compatibility overload or deprecated string-only render path is provided.
