# Design

## Geometry and authority
Existing KittyImage and KittyPlacement details keep their constructor/extractor arity. TerminalRenderControl remains privately constructed, with optional immutable read-only Kitty geometry. A legacy four-argument encodeKitty call remains uncroppable and byte-compatible; a new overload accepts positive declared ImageDimensions. High-level image rendering already receives dimensions and attaches them when positive. These validate declared bounds, not decoded pixels; optional ImageSource sniffing supplies dimensions from supported file headers.

## Vertical mapping
Keep original full display rows and cumulative clipped top/visible rows. Map top with floor(sourceHeight * top / fullRows) and bottom with ceil(sourceHeight * end / fullRows), using Long products. Derive nested crops from original coordinates, not previously rounded pixel rectangles. Horizontal clipping and iTerm2 stay conservative. A fully clipped control is absent; a cropped control has a complete visible footprint.

## Rendering and retention
Only the final encoder emits validated numeric x/y/w/h fields, preserving existing uncropped byte sequences. Include geometry in control equality/hash and preserve it across runtime ID remapping and placement-only controls. Payload/source identity is distinct from placement/crop geometry, so scrolling or resizing can issue placement-only updates without retransmitting unchanged data. Keep per-TUI bounded retention, graphics-last ordering, cleanup and full-redraw safety.

## Verification
Cover top, bottom, both edges, nested clips, rounding, Int-boundary arithmetic, invalid dimensions, unknown geometry/iTerm2/horizontal fallbacks, metadata inspection, remapping and unchanged legacy bytes. Exercise high-level Image in ScrollView with scrolling and resize, then retained upload counts and cleanup on JVM/Native. Run an isolated real Ghostty smoke where available; distinguish actual emulator observation from byte-stream/PTY tests.

## Integration findings

The baseline high-level ImageViewportCropSuite reproduced both partial-image omission cases. Initial protocol-only implementation handled bottom clipping but layout still discarded negative translated anchors before clipping. Layout now intersects signed coordinates first and constructs only validated non-negative public placements afterward; original placement validation is unchanged. Full high-level coverage includes nested scroll offsets and a sticky footer.

## Emulator validation limitation

Ghostty1.4.0 is installed, but the isolated launch did not start the smoke program and desktop control attempts produced no observable window change. Further desktop attempts were stopped. The generated PNG, standalone smoke program and command-file driver are retained for a manual visual pass. No actual pixel-rendering success is claimed.
