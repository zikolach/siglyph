package scalatui.core

import scalatui.ansi.Ansi
import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol}

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

  test("composites overlay that begins inside a base wide cell"):
    val line = OverlayRenderer.compositeLine("界AB", "Z", 1, 1, 4)

    assertEquals(Ansi.strip(line), " ZAB")
    assert(!Ansi.strip(line).contains("界"), line)
    assert(Ansi.visibleWidth(line) <= 4, line)

  test("composites overlay that ends inside a base wide cell"):
    val line = OverlayRenderer.compositeLine("A界B", "Z", 0, 2, 4)

    assertEquals(Ansi.strip(line), "Z  B")
    assert(!Ansi.strip(line).contains("界"), line)
    assertEquals(Ansi.visibleWidth(line), 4)

  test("styled overlay over wide base remains ANSI and width safe"):
    val line = OverlayRenderer.compositeLine("\u001b[34m界AB", "\u001b[31mZZ\u001b[0m", 0, 2, 4)

    assertEquals(Ansi.strip(line), "ZZAB")
    assert(line.contains("\u001b[31m"), line)
    assert(Ansi.visibleWidth(line) <= 4, line)

  test("overlapping overlays composite in provided order"):
    val lines = OverlayRenderer.composite(
      ComponentRender.text("abcdef"),
      Vector(
        ComponentRender.text("XX") -> ResolvedOverlay(
          width = 2,
          row = 0,
          col = 1,
          maxHeight = None
        ),
        ComponentRender.text("YY") -> ResolvedOverlay(
          width = 2,
          row = 0,
          col = 2,
          maxHeight = None
        )
      ),
      terminalWidth = 6,
      terminalHeight = 1
    )

    assertEquals(Ansi.strip(lines.lines.head), "aXYYef")

  test("overlay rectangles occlude covered cursors and preserve half-open boundaries"):
    val base    = ComponentRender(
      Vector("base", "next"),
      Vector.empty,
      Vector(
        CursorPlacement(0, 1),
        CursorPlacement(0, 0),
        CursorPlacement(0, 3),
        CursorPlacement(1, 1)
      )
    )
    val overlay = ComponentRender(
      Vector("xx"),
      Vector.empty,
      Vector(CursorPlacement(0, 0), CursorPlacement(0, 1))
    )

    val result = OverlayRenderer.composite(
      base,
      Vector(overlay -> ResolvedOverlay(2, 0, 1, None)),
      terminalWidth = 6,
      terminalHeight = 2
    )

    assertEquals(
      result.cursorPlacements,
      Vector(
        CursorPlacement(0, 0),
        CursorPlacement(0, 3),
        CursorPlacement(1, 1),
        CursorPlacement(0, 1),
        CursorPlacement(0, 2)
      )
    )

  test("higher overlays replace lower winners while uncovered later candidates remain fallbacks"):
    val base   = ComponentRender(
      Vector("abcd"),
      Vector.empty,
      Vector(CursorPlacement(0, 0), CursorPlacement(0, 2))
    )
    val lower  = ComponentRender(
      Vector("xx"),
      Vector.empty,
      Vector(CursorPlacement(0, 0))
    )
    val higher = ComponentRender(
      Vector("y"),
      Vector.empty,
      Vector(CursorPlacement(0, 0))
    )

    val withWinner = OverlayRenderer.composite(
      base,
      Vector(
        lower  -> ResolvedOverlay(2, 0, 0, None),
        higher -> ResolvedOverlay(1, 0, 0, None)
      ),
      terminalWidth = 4,
      terminalHeight = 1
    )
    assertEquals(
      withWinner.cursorPlacements,
      Vector(CursorPlacement(0, 2), CursorPlacement(0, 0))
    )

    val fallback = OverlayRenderer.composite(
      base,
      Vector(ComponentRender.text("x") -> ResolvedOverlay(1, 0, 0, None)),
      terminalWidth = 4,
      terminalHeight = 1
    )
    assertEquals(fallback.cursorPlacements, Vector(CursorPlacement(0, 2)))

  test("empty overlays still reject invalid base metadata"):
    val invalidBase = ComponentRender(
      Vector("application-secret-"),
      Vector.empty,
      Vector(CursorPlacement(0, 4))
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(invalidBase, Vector.empty, terminalWidth = 4, terminalHeight = 1)
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.CursorOutsideWidth(0, 4, 4).toString
    )
    assert(!error.toString.contains("application-secret-"), error.toString)

  test("empty overlays still reject invalid base controls with redacted diagnostics"):
    val control = kittyControl("c2Vuc2l0aXZl", imageId = 42)
    val base    = ComponentRender(
      Vector("application-secret-"),
      Vector(TerminalControlPlacement(1, 0, control)),
      Vector.empty
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(base, Vector.empty, terminalWidth = 4, terminalHeight = 1)
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.ControlOutsideRows(
        ComponentRenderControlDiagnostic(
          ComponentRenderControlKind.KittyImage,
          Some(42),
          row = 1,
          column = 0,
          width = 1,
          rows = 1
        ),
        frameRows = 1
      ).toString
    )
    assert(!error.toString.contains("application-secret-"), error.toString)
    assert(!error.toString.contains("c2Vuc2l0aXZl"), error.toString)

  test("rejects overlay-local cursor width before translation"):
    val overlay = ComponentRender(
      Vector("x"),
      Vector.empty,
      Vector(CursorPlacement(0, 1))
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        ComponentRender.text("base"),
        Vector(overlay -> ResolvedOverlay(1, 0, 5, None)),
        terminalWidth = 10,
        terminalHeight = 1
      )
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.CursorOutsideWidth(0, 1, 1).toString
    )

  test("rejects overlay-local control width before translation"):
    val control = kittyControl("AAAA", imageId = 40, width = 2)
    val overlay = ComponentRender(
      Vector("x"),
      Vector(TerminalControlPlacement(0, 0, control)),
      Vector.empty
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        ComponentRender.text("base"),
        Vector(overlay -> ResolvedOverlay(1, 0, 5, None)),
        terminalWidth = 10,
        terminalHeight = 1
      )
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.ControlOutsideWidth(
        ComponentRenderControlDiagnostic(
          ComponentRenderControlKind.KittyImage,
          Some(40),
          row = 0,
          column = 0,
          width = 2,
          rows = 1
        ),
        frameWidth = 1
      ).toString
    )

  test("rejects overlay-local rows before translation"):
    val overlay = ComponentRender(
      Vector("x"),
      Vector.empty,
      Vector(CursorPlacement(1, 0))
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        ComponentRender.text(Vector("base", "parent row")),
        Vector(overlay -> ResolvedOverlay(1, 0, 0, None)),
        terminalWidth = 10,
        terminalHeight = 2
      )
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.CursorOutsideRows(1, 0, 1).toString
    )

  test("rejects overlay-local control rows before translation"):
    val control = kittyControl("c2Vuc2l0aXZl", imageId = 43)
    val overlay = ComponentRender(
      Vector("x"),
      Vector(TerminalControlPlacement(1, 0, control)),
      Vector.empty
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        ComponentRender.text(Vector("base", "parent row")),
        Vector(overlay -> ResolvedOverlay(1, 0, 0, None)),
        terminalWidth = 10,
        terminalHeight = 2
      )
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.ControlOutsideRows(
        ComponentRenderControlDiagnostic(
          ComponentRenderControlKind.KittyImage,
          Some(43),
          row = 1,
          column = 0,
          width = 1,
          rows = 1
        ),
        frameRows = 1
      ).toString
    )
    assert(!error.toString.contains("c2Vuc2l0aXZl"), error.toString)

  test("rejects cursor geometry that becomes invalid after translation"):
    val overlay = ComponentRender(
      Vector("overlay-secret-"),
      Vector.empty,
      Vector(CursorPlacement(0, 0))
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        ComponentRender.text("base"),
        Vector(overlay -> ResolvedOverlay(1, 1, 0, None)),
        terminalWidth = 2,
        terminalHeight = 1
      )
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.CursorOutsideRows(2, 0, 2).toString
    )
    assert(!error.toString.contains("overlay-secret-"), error.toString)

  test("rejects control geometry that becomes invalid after translation"):
    val control = kittyControl("c2Vuc2l0aXZl", imageId = 44)
    val overlay = ComponentRender(
      Vector("overlay-secret-"),
      Vector(TerminalControlPlacement(0, 0, control)),
      Vector.empty
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        ComponentRender.text("base"),
        Vector(overlay -> ResolvedOverlay(1, 0, 2, None)),
        terminalWidth = 2,
        terminalHeight = 1
      )
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.ControlOutsideWidth(
        ComponentRenderControlDiagnostic(
          ComponentRenderControlKind.KittyImage,
          Some(44),
          row = 0,
          column = 2,
          width = 1,
          rows = 1
        ),
        frameWidth = 2
      ).toString
    )
    assert(!error.toString.contains("overlay-secret-"), error.toString)
    assert(!error.toString.contains("c2Vuc2l0aXZl"), error.toString)

  test("validates final metadata after composition"):
    val baseControl    = kittyControl("AAAA", imageId = 41)
    val overlayControl = kittyControl("TQ==", imageId = 41)
    val base           = ComponentRender(
      Vector("", ""),
      Vector(TerminalControlPlacement(0, 0, baseControl)),
      Vector.empty
    )
    val overlay        = ComponentRender(
      Vector(""),
      Vector(TerminalControlPlacement(0, 0, overlayControl)),
      Vector.empty
    )

    val error = intercept[IllegalArgumentException](
      OverlayRenderer.composite(
        base,
        Vector(overlay -> ResolvedOverlay(1, 1, 0, None)),
        terminalWidth = 2,
        terminalHeight = 2
      )
    )

    assert(error.getMessage.contains("DuplicateActiveKittyImageId"), error.getMessage)

  test("no overlays preserves base line count"):
    val lines = OverlayRenderer.composite(
      ComponentRender.text(Vector("one", "two")),
      Vector.empty,
      terminalWidth = 20,
      terminalHeight = 10
    )

    assertEquals(lines, ComponentRender.text(Vector("one", "two")))

  test("overlay frame extends only to deepest required overlay row"):
    val lines = OverlayRenderer.composite(
      ComponentRender.text("base"),
      Vector(
        ComponentRender.text("menu") -> ResolvedOverlay(
          width = 10,
          row = 2,
          col = 0,
          maxHeight = None
        )
      ),
      terminalWidth = 20,
      terminalHeight = 10
    )

    assertEquals(lines.lines.length, 3)
    assertEquals(Ansi.strip(lines.lines(2)).trim, "menu")

  test("bottom anchored overlay may still extend to terminal height"):
    val lines = OverlayRenderer.composite(
      ComponentRender.text("base"),
      Vector(
        ComponentRender.text("bottom") -> ResolvedOverlay(
          width = 10,
          row = 9,
          col = 0,
          maxHeight = None
        )
      ),
      terminalWidth = 20,
      terminalHeight = 10
    )

    assertEquals(lines.lines.length, 10)
    assertEquals(Ansi.strip(lines.lines(9)).trim, "bottom")

  private def kittyControl(
      value: String,
      imageId: Int,
      width: Int = 1,
      rows: Int = 1
  ): scalatui.terminal.TerminalRenderControl =
    TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from(value).toOption.get,
      imageId,
      width,
      rows
    )
