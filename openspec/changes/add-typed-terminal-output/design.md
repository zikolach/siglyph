## Context

`Component.render(width)` currently returns `Vector[String]`. The optional `Image` component embeds Kitty APC or iTerm2 OSC 1337 bytes in those strings, and the TUI cannot prove whether such bytes came from the validated image API or arbitrary application text. The pending strict ANSI policy permits only validated SGR and OSC 8 in ordinary text, so line-only output cannot preserve both that policy and supported terminal images.

The change crosses public component APIs, shared frame composition, overlays, the differential renderer, image protocol helpers, and JVM/Scala Native modules. Core must remain dependency-free. No compatibility render path, arbitrary trusted string, protocol-prefix trust, or duplicated image parser is allowed.

## Goals / Non-Goals

**Goals:**
- Preserve provenance for library-generated terminal controls from construction through final output.
- Keep ordinary text and semantic terminal controls in separate typed channels.
- Encode only closed, known control variants at the final TUI write boundary.
- Preserve image row reservation, validated payload spelling, sizing, cleanup values, overlays, differential redraws, and JVM/Native parity.
- Migrate every repository component and consumer directly to one render contract.

**Non-Goals:**
- Accepting arbitrary trusted escape strings.
- Parsing Kitty/iTerm2 strings to infer trust.
- Changing base64 validation, image format validation, cell sizing, terminal capability detection, or Unicode width policy.
- Adding a compatibility overload, deprecated line-only path, fallback protocol, runtime dependency, or second renderer.
- Making terminal backend direct writes safe for untrusted application data; direct backend use remains explicit application authority.

## Decisions

### Replace line-only output with one typed frame value

`Component.render(width)` returns `ComponentRender`, containing ordered ordinary lines and zero or more `TerminalControlPlacement` values. A placement has a non-negative frame-relative row and display column plus a `TerminalRenderControl`. `ComponentRender` exposes direct constructors for text-only frames and validates basic placement shape. `ComponentFrameBuilder`, `Container`, boxes, demos, Markdown, extras, and tests use this one type; no old `Vector[String]` render method remains.

A parallel optional `renderControls` method was rejected because containers and third-party composers could forget it and erase provenance. Embedding private marker strings was rejected because application text could reproduce them. A line subtype or Image-specific TUI branch was rejected because it leaks one protocol into generic composition.

### Use a closed semantic terminal-control hierarchy

`TerminalRenderControl` is a public sealed read-only type whose concrete constructors and raw encoding remain inside shared core. Public protocol helpers create values from validated domain inputs. Initial variants cover Kitty image transmit/placement, iTerm2 inline image, and Kitty image cleanup. The model stores semantic fields such as `Base64ImagePayload`, image id, cell dimensions, and encoded filename input; it never stores an arbitrary raw escape string supplied by a component.

Applications may intentionally obtain a control through `TerminalImageProtocol` and place it in `ComponentRender`. That explicit typed call is authority to request the protocol. Ordinary strings that happen to match protocol grammar never enter this channel.

A validated string allowlist was rejected because it gives matching application text the same authority and requires a parser that duplicates each encoder. Disabling images was rejected because it violates the image-rendering contract.

### Give controls explicit geometry

Each control reports a non-negative display width and row height. `ImageRenderResult` carries a typed control, reserved rows, width, and optional image id. `Image.render` returns the reserved blank ordinary lines and one control placement at its relative anchor. Component output must keep every control footprint within its returned frame and the requested width.

The TUI validates final placement before output. An invalid surviving control fails rendering before terminal write and enters normal runtime cleanup; it is not silently moved, dropped, converted to text, or partially encoded. A final frame also fails validation when more than one active Kitty image control uses the same semantic integer ID. Kitty cleanup controls are not active placements and do not participate in frame uniqueness.

### Require encodable image geometry and identity

Kitty and iTerm2 image controls require positive cell width and height. Kitty transmit and targeted cleanup controls also require a positive image ID. Typed helper construction rejects invalid values, and the final encoder repeats these checks before producing bytes. Configured Kitty IDs follow the same positive constraint.

The Kitty allocator stores bounded positive state. It allocates `Int.MaxValue` once and then fails explicitly; it never wraps to zero or a negative ID and never substitutes another identity.

When `Image.render` receives zero or negative width, geometry clipping returns one empty ordinary line and no terminal control. This path does not run protocol selection, allocate an ID, emit readable unsupported-capability text, or select another protocol. Composition therefore has no zero-width control to translate.

### Preserve controls through vertical composition and overlays

`ComponentFrameBuilder` rebases child control rows only by the locally accumulated child-line count when appending a frame. Its `startRow` and `startCol` remain terminal-relative notifications for `RenderOriginAware` and do not change the frame-local coordinates returned by `result()`. Layout operations that actually add padding or placement, including Box and overlays, apply their own explicit row and column offsets. `Box` normalizes each padding input to a non-negative value once and uses those values for child width, line padding, control translation, and final frame geometry. `Container` and other vertical composers concatenate both channels. Overlay rendering rebases visible overlay controls by resolved row and column, clips controls that belong entirely outside the overlay viewport, and rejects a surviving partial control footprint rather than emitting a malformed command. A higher overlay suppresses lower controls whose declared footprint intersects the overlay rectangle, matching visible replacement semantics.

Controls are compared semantically as ordered prepared-frame state. A control addition, removal, move, field change, or pure vector reorder marks the earliest row affected by the first ordered difference as changed. Full and partial renders emit controls in their new vector order within each rendered row. Kitty cleanup remains a typed control; image lifecycle behavior is not inferred from raw strings.

Before emitting any Kitty `a=T` control whose positive semantic ID appeared in the previous prepared frame, the renderer emits exactly one targeted `a=d,d=I,i=<id>` cleanup. It emits all required cleanups before replacement transmissions, in previous-frame control order. The same rule applies to partial redraws, pure reorder, forced full redraws, resize redraws, moves, and semantic replacements. An old ID absent from the new frame is also cleaned once. A new ID is not cleaned, and an unchanged ID above a partial redraw boundary is neither cleaned nor retransmitted. Cleanup selection compares integer IDs and reuses the old semantic controls without reconstructing or copying payload strings. Delete-all encoding remains `a=d,d=A`.

Lowercase `d=i` was rejected because Kitty may retain image data after deleting placements. Uppercase `d=I` removes the targeted image data when unreferenced and satisfies Kitty's retransmission requirement that the old image and placements be deleted first.

### Keep diagnostics bounded and confidential

`ComponentRenderValidationError` snapshots only a bounded control kind, optional positive image ID, coordinates, footprint, frame rows or width, and duplicate coordinates. It never retains `TerminalControlPlacement`, `TerminalRenderControl`, `Base64ImagePayload`, filename content, or application text. Its default string form and the TUI's `IllegalArgumentException` message therefore remain bounded independently of payload and filename size.

`TerminalRenderControl.toString` reports only bounded kind, geometry, and a positive Kitty ID where applicable. Raw payload and filename values remain available only through the explicit typed details API and are never included in the control's default diagnostic string.

### Validate unique active Kitty identities in each final frame

One final frame may contain at most one active Kitty image control for each positive image ID. Validation reads the semantic integer ID directly and retains no reconstructed or copied payload string. Duplicate active IDs fail before synchronized frame output. Kitty cleanup controls are lifecycle commands rather than active image placements, so they do not participate in this uniqueness rule.

Allowing duplicate placements was rejected because Kitty cleanup is image-wide and cannot remove or replace one placement without affecting another placement that shares its ID. Separate transmit and placement variants were rejected because this change does not introduce a second placement protocol.

### Encode controls only while assembling the final terminal buffer

Prepared frame state stores sanitized ordinary lines, cursor position, and typed controls. Full and partial buffer builders walk rows in order, position the cursor to each control anchor, encode known controls through the shared core encoder, and then restore the expected row/column before ordinary line output. Synchronized-output, autowrap, resize generation checks, write locking, differential state mutation, and cleanup ownership remain unchanged.

The encoder is exhaustive over the sealed hierarchy and validates protocol field ranges before bytes are appended. No generic `encode(String)` or public raw-value constructor exists.

### Keep strict ordinary-text policy in its owning stacked change

This prerequisite establishes provenance and removes image controls from ordinary lines. The stacked Unicode/ANSI change applies its approved strict SGR/OSC 8 allowlist to the ordinary-line channel. Until that stacked change lands, ordinary lines retain the current ANSI behavior, but they cannot acquire typed-control identity through prefix detection.

### Make the source break direct and documented

Every in-repository `Component`, frame builder, overlay helper, test fake, example, and interop compile fixture migrates in one change. Public Scaladoc and README/porting notes show `ComponentRender.text(...)` and explain typed controls. No implicit conversion, overloaded legacy `render`, adapter component, or deprecation period is added.

## Risks / Trade-offs

- [Every external component implementation must migrate] → Mark the change breaking, provide a concise mechanical migration example, and compile every repository module and example.
- [Overlay geometry can produce partially visible controls] → Carry explicit footprints and reject surviving partial controls before output; test clipping and overlap deterministically.
- [Differential rendering can duplicate or orphan image state] → Include controls in frame equality/change-row calculation and test add, move, replace, remove, resize, full redraw, and partial redraw.
- [Encoding or placement diverges between JVM and Native] → Keep models and encoders in canonical shared core and run the same protocol/frame tests in `coreNative` and `imageNative`.
- [Large image payload equality has linear cost] → Compare only when frames are compared, preserve current payload size non-goal, and avoid copying payload strings in placement/rebasing.
- [Typed authority grows into a generic escape bypass] → Keep the hierarchy sealed, semantic, and exhaustively encoded; reject arbitrary raw controls and generic trusted strings.

## Migration Plan

1. Add shared render and semantic-control types with focused JVM/Native tests.
2. Change `Component.render` and frame composition APIs directly.
3. Migrate built-in components, tests, demos, Markdown, extras, and examples to `ComponentRender`.
4. Change image protocol results and `Image` to typed controls.
5. Integrate control placement, overlays, differential state, and final encoding in TUI.
6. Update documentation and porting notes for the source break.
7. Run full JVM/Native compile, tests, examples, formatting, Scalafix, and strict OpenSpec validation.
8. Stack the Unicode/ANSI implementation on this change and remove its temporary image-inert expectation.
