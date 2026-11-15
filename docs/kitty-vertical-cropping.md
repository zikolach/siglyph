# Vertical Kitty image cropping

Available since **0.11.0**; published **0.10.0** does not include this extension.

Images with known positive source dimensions remain visible when their top or bottom crosses a
scroll viewport. Layout intersects signed translated anchors before constructing non-negative typed
placements, so an image anchored above the viewport can still contribute its visible rows.

## Try it

From a checkout, the [complete image example](../examples/scala-cli/image.scala) loads the project logo and chooses a supported terminal protocol or text fallback. Replace the filename with your own image if desired:

```bash
scala-cli run examples/scala-cli/image.scala -- docs/assets/siglyph-logo.png
```

For fullscreen scrolling, place that Image component inside a ScrollView as described in [screen modes](screen-modes.md#height-aware-fullscreen). Known image dimensions enable vertical Kitty cropping automatically; there is no separate crop toggle. Other clipping limits are listed below.

## Metadata and compatible helpers

`TerminalRenderControl.kittyGeometry` exposes optional immutable `KittyImageGeometry`. It records
original display width/rows, cumulative top row and visible rows; `sourceRect` returns a validated
`ImagePixelRect`. Existing control-details constructors/extractors keep their arity. Control
equality/hash include geometry, so a crop-only change invalidates retained output. A dimensionless
legacy control and a geometry-aware control need not compare equal even if full-image bytes match.

The existing four-argument `TerminalImageProtocol.encodeKitty(payload, id, columns, rows)` is
unchanged and remains uncroppable. Its overload accepting `sourceDimensions: ImageDimensions`
attaches validated declared geometry. `renderBase64Image` and high-level `Image` automatically
attach positive dimensions already supplied to them. Invalid legacy dimensions retain conservative
behavior. `ImageSource` obtains dimensions from supported file headers; core does not decode or
verify that arbitrary caller-supplied dimensions match payload contents.

For custom controls, load validated source metadata before encoding (requires the optional `siglyph-image` dependency):

<!-- compile-example -->
```scala
import java.nio.file.Paths
import scalatui.image.ImageSource
import scalatui.terminal.{TerminalImageProtocol, TerminalRenderControl}

def imageControl(path: String): TerminalRenderControl =
  val source = ImageSource.fromFile(Paths.get(path)) match
    case Right(value) => value
    case Left(error) => throw IllegalArgumentException(error.message)
  TerminalImageProtocol.encodeKitty(
    source.payload, imageId = 42, widthCells = 20, heightCells = 40,
    sourceDimensions = source.dimensions
  )
```

## Clipping and retention

- Source top is floored and source bottom is ceiled from ORIGINAL row geometry with Long arithmetic.
  Nested clips compose row offsets before rounding, rather than repeatedly rounding pixel crops.
- Cropped upload and placement controls encode numeric `x`, `y`, `w`, `h`; full-image output bytes
  remain unchanged. Pixel rounding is outward; terminal scaling/letterboxing remains terminal-owned.
- Data identity is independent of known-source crop/display geometry. Scroll and resize changes use
  placement-only output while retaining the same upload. ID remapping preserves geometry. Existing
  retention budgets, graphics-last painting and cleanup behavior remain.
- Fully hidden images, partial images with unknown source geometry, horizontal partial clips and
  iTerm2 partial images remain omitted. Intersecting overlays still suppress whole images; this is
  not partial-overlay-occlusion cropping.

The wire fields follow the [Kitty graphics protocol](https://sw.kovidgoyal.net/kitty/graphics-protocol/#controlling-displayed-image-layout).

## Verification

Shared protocol/retention tests and high-level ImageViewportCropSuite cover rounding, composition,
integer bounds, unchanged legacy output, negative scroll anchors, sticky/nested layouts, resize,
upload reuse and overlays. They run on JVM and Scala Native. A generated PNG and standalone smoke
program are retained in the local verification artifacts. Real-emulator visual validation is not
claimed when desktop launch/control is unavailable; PTY or byte-stream checks are not pixel proof.
