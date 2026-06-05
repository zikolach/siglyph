package scalatui.core

import scalatui.ansi.Ansi

class OverlayRendererSuite extends munit.FunSuite:
  test("resolves centered default overlay like pi-tui"):
    val layout = OverlayRenderer.resolve(
      OverlayOptions(),
      overlayHeight = 4,
      terminalWidth = 100,
      terminalHeight = 20
    )

    assertEquals(layout, ResolvedOverlay(width = 80, row = 8, col = 10, maxHeight = None))

  test("resolves absolute and percentage positions before anchor"):
    val absolute = OverlayRenderer.resolve(
      OverlayOptions(
        width = Some(OverlaySize.Absolute(5)),
        row = Some(OverlaySize.Absolute(2)),
        col = Some(OverlaySize.Absolute(3)),
        anchor = OverlayAnchor.BottomRight
      ),
      overlayHeight = 2,
      terminalWidth = 20,
      terminalHeight = 10
    )
    assertEquals(absolute.row, 2)
    assertEquals(absolute.col, 3)

    val percent = OverlayRenderer.resolve(
      OverlayOptions(
        width = Some(OverlaySize.Percent(50)),
        row = Some(OverlaySize.Percent(100)),
        col = Some(OverlaySize.Percent(100))
      ),
      overlayHeight = 2,
      terminalWidth = 20,
      terminalHeight = 10
    )
    assertEquals(percent, ResolvedOverlay(width = 10, row = 8, col = 10, maxHeight = None))

  test("applies offsets margins min width max height and narrow clamping"):
    val layout = OverlayRenderer.resolve(
      OverlayOptions(
        width = Some(OverlaySize.Absolute(2)),
        minWidth = Some(8),
        maxHeight = Some(OverlaySize.Absolute(3)),
        anchor = OverlayAnchor.BottomRight,
        offsetX = 5,
        offsetY = 5,
        margin = OverlayMargin.all(1)
      ),
      overlayHeight = 10,
      terminalWidth = 12,
      terminalHeight = 8
    )

    assertEquals(layout, ResolvedOverlay(width = 8, row = 4, col = 3, maxHeight = Some(3)))

    val narrow =
      OverlayRenderer.resolve(OverlayOptions(width = Some(OverlaySize.Absolute(80))), 5, 1, 1)
    assertEquals(narrow.width, 1)
    assertEquals(narrow.row, 0)
    assertEquals(narrow.col, 0)

  test("composites overlay cells over base with literal spaces"):
    val first = OverlayRenderer.compositeLine("abcdef", "XY", 2, 2, 6)
    assertEquals(Ansi.strip(first), "abXYef")

    val spaces = OverlayRenderer.compositeLine("abcdef", "  ", 2, 2, 6)
    assertEquals(Ansi.strip(spaces), "ab  ef")

  test("clips over-wide styled overlay lines before compositing"):
    val line = OverlayRenderer.compositeLine("abcdef", "\u001b[31mXYZ\u001b[0m", 1, 2, 6)

    assertEquals(Ansi.strip(line), "aXYdef")
    assert(Ansi.visibleWidth(line) <= 6, line)

  test("overlapping overlays composite in provided order"):
    val lines = OverlayRenderer.composite(
      Vector("abcdef"),
      Vector(
        Vector("XX") -> ResolvedOverlay(width = 2, row = 0, col = 1, maxHeight = None),
        Vector("YY") -> ResolvedOverlay(width = 2, row = 0, col = 2, maxHeight = None)
      ),
      terminalWidth = 6,
      terminalHeight = 1
    )

    assertEquals(Ansi.strip(lines.head), "aXYYef")

  test("no overlays preserves base line count"):
    val lines = OverlayRenderer.composite(
      Vector("one", "two"),
      Vector.empty,
      terminalWidth = 20,
      terminalHeight = 10
    )

    assertEquals(lines, Vector("one", "two"))

  test("overlay frame extends only to deepest required overlay row"):
    val lines = OverlayRenderer.composite(
      Vector("base"),
      Vector(Vector("menu") -> ResolvedOverlay(width = 10, row = 2, col = 0, maxHeight = None)),
      terminalWidth = 20,
      terminalHeight = 10
    )

    assertEquals(lines.length, 3)
    assertEquals(Ansi.strip(lines(2)), "menu")

  test("bottom anchored overlay may still extend to terminal height"):
    val lines = OverlayRenderer.composite(
      Vector("base"),
      Vector(Vector("bottom") -> ResolvedOverlay(width = 10, row = 9, col = 0, maxHeight = None)),
      terminalWidth = 20,
      terminalHeight = 10
    )

    assertEquals(lines.length, 10)
    assertEquals(Ansi.strip(lines(9)), "bottom")
