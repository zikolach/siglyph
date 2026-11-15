# Typed component output

[Documentation index](README.md)

## Typed component output

`Component.render(width)` now returns `ComponentRender` on JVM and Scala Native. This is a direct
source break. Text-only components return `ComponentRender.text`; there is no line-vector overload,
implicit conversion, adapter, or deprecation path:

```scala
import scalatui.ansi.Ansi
import scalatui.core.{Component, ComponentRender}

final class Greeting extends Component:
  override def render(width: Int): ComponentRender =
    ComponentRender.text(Vector("Hello", "world").map(line =>
      Ansi.truncateToWidth(line, math.max(0, width), "")
    ))
```

`ComponentRender.lines` contains ordinary text. `ComponentRender.controls` contains
`TerminalControlPlacement` values. `ComponentRender.cursorPlacements` contains `CursorPlacement`
candidates. Both metadata channels use zero-based, frame-relative row and display-cell columns.
Direct `ComponentRender` construction requires all three fields explicitly. This cursor migration is
a direct source break with no overload, default field, adapter, conversion, or deprecation path.
Text-only factories construct explicit empty metadata.

The public `CursorMarker` object, including its `Sequence`, `Position`, `ScanResult`, and
`stripAndLocate` APIs, was removed. Migrate directly to `CursorPlacement` values in
`ComponentRender.cursorPlacements`. No compatibility API exists.

Every cursor candidate must identify an existing frame row and use a column below the non-negative
requested width. Each control footprint must fit within the returned rows and requested width.
Invalid surviving geometry fails before synchronized terminal output with bounded diagnostics that
retain no application text. Frame builders and boxes translate cursor candidates with content
geometry. Higher opaque overlay rectangles remove covered lower candidates. The TUI selects the
first surviving row-major candidate and preserves vector order for equal coordinates. Hardware
cursor positioning remains optional. Cursor-only changes move the hardware cursor without repainting
unchanged lines or typed controls.

Validation errors retain only bounded control kind, optional image ID, coordinates, footprint, and
frame dimensions. Their default strings and thrown exception messages never retain image payloads,
filenames, controls, or application text. `TerminalRenderControl.toString` is also redacted to
bounded kind, geometry, and positive Kitty ID where applicable.

`TerminalRenderControl` is closed and read-only. Applications obtain supported image controls only
through typed `TerminalImageProtocol` helpers. The TUI preserves those controls through composition
and differential rendering, then encodes them only while assembling the final synchronized output.
There is no arbitrary trusted-string API. Ordinary text that contains Kitty or iTerm2-looking bytes
gains no semantic control authority and receives no image-specific rendering behavior. Before the
TUI retransmits a Kitty `a=T` control whose positive ID existed in the previous prepared frame, it
emits exactly one `a=d,d=I,i=<id>` cleanup before all replacement transmissions. Removed old IDs are
also cleaned. New IDs and unchanged IDs outside a partial redraw range are not cleaned. Delete-all
cleanup remains `a=d,d=A`.

This differs intentionally from `pi-tui`, which detects image output from string prefixes. siglyph
does not infer authority from prefixes because application text can reproduce them. Direct terminal
backend writes remain explicit application authority and are outside the component-output trust
boundary.

The JVM interop facade gives Java and Kotlin call sites the same basic path without Scala default-argument methods or Scala function types. Scala, Java, and Kotlin versions of the basic example are in [`docs/jvm-language-examples.md`](../docs/jvm-language-examples.md).
