package scalatui.core

import scalatui.components.Box
import scalatui.terminal.{
  Base64ImagePayload,
  ImageProtocol,
  TerminalCapabilities,
  TerminalImageProtocol
}

import scala.compiletime.testing.typeCheckErrors

class ComponentRenderSuite extends munit.FunSuite:
  test("text-only construction has no controls"):
    assertEquals(
      ComponentRender.text(Vector("a", "b")),
      ComponentRender(Vector("a", "b"), Vector.empty, Vector.empty)
    )
    assertEquals(ComponentRender.text("line").controls, Vector.empty)

  test("applications cannot construct or extend terminal controls"):
    val constructionErrors = typeCheckErrors(
      """new scalatui.terminal.TerminalRenderControl(null)"""
    )
    val extensionErrors    = typeCheckErrors(
      """class CustomControl extends scalatui.terminal.TerminalRenderControl(null)"""
    )

    assert(constructionErrors.nonEmpty)
    assert(extensionErrors.nonEmpty)

  test("placements reject negative frame-relative coordinates"):
    val control = kittyControl("AAAA")
    intercept[IllegalArgumentException](TerminalControlPlacement(-1, 0, control))
    intercept[IllegalArgumentException](TerminalControlPlacement(0, -1, control))

  test("cursor placements reject negative coordinates and translate structurally"):
    intercept[IllegalArgumentException](CursorPlacement(-1, 0))
    intercept[IllegalArgumentException](CursorPlacement(0, -1))
    assertEquals(
      CursorPlacement(1, 2).translated(rowOffset = 3, columnOffset = 4),
      CursorPlacement(4, 6)
    )

  test("validation rejects cursor candidates outside rows or width with bounded diagnostics"):
    val sensitive = "application-secret-".repeat(1024)
    val below     = ComponentRender(
      Vector(sensitive),
      Vector.empty,
      Vector(CursorPlacement(1, 0))
    )
    val wide      = ComponentRender(
      Vector(sensitive),
      Vector.empty,
      Vector(CursorPlacement(0, 3))
    )

    assertEquals(
      below.validate(3),
      Left(ComponentRenderValidationError.CursorOutsideRows(1, 0, 1))
    )
    assertEquals(
      wide.validate(3),
      Left(ComponentRenderValidationError.CursorOutsideWidth(0, 3, 3))
    )
    Vector(below, wide).foreach { frame =>
      val diagnostic = intercept[IllegalArgumentException](frame.validated(3)).toString
      assert(diagnostic.length < 256, diagnostic)
      assert(!diagnostic.contains("application-secret-"), diagnostic)
    }

  test("semantic controls and placements compare by typed fields"):
    val first  = TerminalControlPlacement(0, 1, kittyControl("TQ=="))
    val second = TerminalControlPlacement(0, 1, kittyControl("TQ=="))

    assertEquals(first, second)
    assertEquals(
      ComponentRender(Vector(""), Vector(first), Vector.empty),
      ComponentRender(Vector(""), Vector(second), Vector.empty)
    )

  test("frame translation moves controls and cursors without copying controls"):
    val control = kittyControl("AAAA")
    val frame   = ComponentRender(
      Vector(""),
      Vector(TerminalControlPlacement(0, 1, control)),
      Vector(CursorPlacement(0, 2))
    )

    val translated = frame.translated(rowOffset = 3, columnOffset = 4)

    assertEquals(translated.controls.map(p => p.row -> p.column), Vector(3 -> 5))
    assertEquals(translated.cursorPlacements, Vector(CursorPlacement(3, 6)))
    assert(translated.controls.head.control eq control)

  test("validation rejects controls outside rows or requested width"):
    val control = kittyControl("AAAA", width = 2, rows = 2)
    val below   =
      ComponentRender(Vector(""), Vector(TerminalControlPlacement(0, 0, control)), Vector.empty)
    val wide    = ComponentRender(
      Vector("", ""),
      Vector(TerminalControlPlacement(0, 2, control)),
      Vector.empty
    )
    val valid   = ComponentRender(
      Vector("", ""),
      Vector(TerminalControlPlacement(0, 1, control)),
      Vector.empty
    )

    assert(below.validate(3).isLeft)
    assert(wide.validate(3).isLeft)
    assertEquals(valid.validate(3), Right(()))

  test("validation rejects duplicate active Kitty image IDs but not cleanup controls"):
    val first     = TerminalControlPlacement(0, 0, kittyControl("AAAA"))
    val duplicate = TerminalControlPlacement(1, 0, kittyControl("TQ=="))
    val frame     = ComponentRender(Vector("", ""), Vector(first, duplicate), Vector.empty)

    assertEquals(
      frame.validate(3),
      Left(ComponentRenderValidationError.DuplicateActiveKittyImageId(
        imageId = 7,
        ComponentRenderControlDiagnostic(
          ComponentRenderControlKind.KittyImage,
          Some(7),
          row = 0,
          column = 0,
          width = 1,
          rows = 1
        ),
        ComponentRenderControlDiagnostic(
          ComponentRenderControlKind.KittyImage,
          Some(7),
          row = 1,
          column = 0,
          width = 1,
          rows = 1
        )
      ))
    )

    val cleanup = TerminalImageProtocol
      .deleteImage(
        7,
        TerminalCapabilities(
          trueColor = true,
          hyperlinks = true,
          images = Some(ImageProtocol.Kitty)
        )
      )
      .get
    assertEquals(
      ComponentRender(
        Vector(""),
        Vector(
          TerminalControlPlacement(0, 0, cleanup),
          TerminalControlPlacement(0, 0, cleanup)
        ),
        Vector.empty
      ).validate(3),
      Right(())
    )

  test("validation failures and exceptions retain only bounded redacted diagnostics"):
    val sensitivePayload = "QUJD".repeat(2048)
    val sensitiveName    = "secret-filename-".repeat(512)
    val kitty            = kittyControl(sensitivePayload, width = 2, rows = 2)
    val iterm            = TerminalImageProtocol.encodeITerm2(
      Base64ImagePayload.from(sensitivePayload).toOption.get,
      Some(sensitiveName),
      widthCells = 2,
      heightCells = 2
    )
    val invalidFrames    = Vector(
      ComponentRender(Vector(""), Vector(TerminalControlPlacement(0, 0, kitty)), Vector.empty) -> 3,
      ComponentRender(
        Vector("", ""),
        Vector(TerminalControlPlacement(0, 1, iterm)),
        Vector.empty
      )                                                                                        -> 2,
      ComponentRender(
        Vector("", ""),
        Vector(
          TerminalControlPlacement(0, 0, kitty),
          TerminalControlPlacement(1, 0, kittyControl(sensitivePayload))
        ),
        Vector.empty
      )                                                                                        -> 3
    )

    invalidFrames.foreach { (frame, width) =>
      val error     = frame.validate(width).left.toOption.get
      val exception = intercept[IllegalArgumentException](frame.validated(width))
      Vector(error.toString, exception.toString, exception.getMessage).foreach { diagnostic =>
        assert(diagnostic.length < 512, diagnostic.length.toString)
        assert(!diagnostic.contains("QUJDQUJD"), diagnostic)
        assert(!diagnostic.contains("secret-filename-"), diagnostic)
      }
    }

  test("frame builder rebases controls by local rows and retains control identity"):
    val control = kittyControl("TQ==")
    val child   = ComponentRender(
      Vector(""),
      Vector(TerminalControlPlacement(row = 0, column = 2, control)),
      Vector(CursorPlacement(row = 0, column = 3))
    )
    var origin  = Option.empty[ComponentRenderOrigin]
    val aware   = new Component with RenderOriginAware:
      override def renderOrigin_=(value: Option[ComponentRenderOrigin]): Unit = origin = value
      override def render(width: Int): ComponentRender                        = child
    val builder = ComponentFrameBuilder(width = 10, startRow = 7, startCol = 4)

    builder.addLine("before")
    builder.addComponent(aware)
    val result = builder.result()

    assertEquals(origin, Some(ComponentRenderOrigin(row = 8, col = 4)))
    assertEquals(result.lines, Vector("before", ""))
    assertEquals(result.controls.head.row, 1)
    assertEquals(result.controls.head.column, 2)
    assertEquals(result.cursorPlacements, Vector(CursorPlacement(row = 1, column = 3)))
    assert(result.controls.head.control eq control)
    val originalPayload = control.details
      .asInstanceOf[scalatui.terminal.TerminalRenderControl.Details.KittyImage]
      .payload
    val rebasedPayload  = result.controls.head.control
      .details
      .asInstanceOf[scalatui.terminal.TerminalRenderControl.Details.KittyImage]
      .payload
    assert(rebasedPayload eq originalPayload)

  test("frame builder rejects child-local cursor and control rows before appending them"):
    val invalidCursor  = ComponentRender(
      Vector("application-secret-"),
      Vector.empty,
      Vector(CursorPlacement(1, 0))
    )
    val invalidControl = ComponentRender(
      Vector("application-secret-"),
      Vector(TerminalControlPlacement(1, 0, kittyControl("AAAA"))),
      Vector.empty
    )

    Vector(invalidCursor, invalidControl).foreach { invalid =>
      val builder = ComponentFrameBuilder(width = 4)
      val failure = intercept[IllegalArgumentException](builder.addRender(invalid))

      assertEquals(builder.result(), ComponentRender.empty)
      assert(failure.getMessage.length < 512, failure.getMessage)
      assert(!failure.getMessage.contains("application-secret-"), failure.getMessage)
    }

  test("box rejects child-local rows and width before padding translation"):
    val invalidCursorRow    = ComponentRender(
      Vector(""),
      Vector.empty,
      Vector(CursorPlacement(1, 0))
    )
    val invalidControlWidth = ComponentRender(
      Vector(""),
      Vector(TerminalControlPlacement(0, 1, kittyControl("AAAA", width = 2))),
      Vector.empty
    )

    Vector(invalidCursorRow, invalidControlWidth).foreach { invalid =>
      val box = Box(paddingX = 1, paddingY = 1)
      box.addChild(new Component:
        override def render(width: Int): ComponentRender = invalid)

      val failure = intercept[IllegalArgumentException](box.render(4))
      assertEquals(failure.getMessage, invalid.validate(2).left.toOption.get.toString)
    }

  test("nested vertical composition accumulates only local rows"):
    val control = kittyControl("AAAA")
    val image   = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector(""),
        Vector(TerminalControlPlacement(0, 0, control)),
        Vector(CursorPlacement(0, 1))
      )
    val inner   = Container()
    inner.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text("inner"))
    inner.addChild(image)
    val outer   = Container()
    outer.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text("outer"))
    outer.addChild(inner)

    val result = outer.render(10)

    assertEquals(result.lines, Vector("outer", "inner", ""))
    assertEquals(result.controls.map(_.row), Vector(2))
    assertEquals(result.cursorPlacements, Vector(CursorPlacement(2, 1)))
    assert(result.controls.head.control eq control)

  test("container and box apply explicit control and cursor offsets without rebuilding controls"):
    val control   = kittyControl("AAAA")
    val image     = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector(""),
        Vector(TerminalControlPlacement(0, 0, control)),
        Vector(CursorPlacement(0, 1))
      )
    val container = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text("before"))
    container.addChild(image)

    val containerFrame = container.render(10)
    assertEquals(containerFrame.controls.head.row, 1)
    assert(containerFrame.controls.head.control eq control)
    assertEquals(containerFrame.cursorPlacements, Vector(CursorPlacement(1, 1)))

    val box      = Box(paddingX = 2, paddingY = 1)
    box.addChild(image)
    val boxFrame = box.render(10)
    assertEquals(boxFrame.controls.head.row, 1)
    assertEquals(boxFrame.controls.head.column, 2)
    assertEquals(boxFrame.cursorPlacements, Vector(CursorPlacement(1, 3)))
    assert(boxFrame.controls.head.control eq control)

  test("box treats negative padding as zero for child width text controls and frame geometry"):
    val control       = kittyControl("AAAA")
    var renderedWidth = Int.MinValue
    val image         = new Component:
      override def render(width: Int): ComponentRender =
        renderedWidth = width
        ComponentRender(
          Vector("x"),
          Vector(TerminalControlPlacement(0, 0, control)),
          Vector.empty
        )
    val box           = Box(paddingX = -2, paddingY = -3)
    box.addChild(image)

    val frame = box.render(4)

    assertEquals(renderedWidth, 4)
    assertEquals(frame.lines, Vector("x   "))
    assertEquals(frame.controls.map(placement => placement.row -> placement.column), Vector(0 -> 0))
    assertEquals(frame.validate(4), Right(()))

  private def kittyControl(
      value: String,
      width: Int = 1,
      rows: Int = 1
  ): scalatui.terminal.TerminalRenderControl =
    TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from(value).toOption.get,
      imageId = 7,
      widthCells = width,
      heightCells = rows
    )
