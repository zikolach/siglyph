package scalatui.core

import scalatui.ansi.Ansi
import scalatui.components.{HStack, StackEntry, StackEntryOptions, VStack}
import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol}

class LayoutSuite extends munit.FunSuite:
  final class Lines(value: Vector[String]) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(value)

  test("viewport rectangles intersect and use half-open cell containment"):
    val rect = ViewportRect(row = 2, col = 3, width = 4, height = 5)
    val clip = ClipRect(row = 0, col = 5, width = 3, height = 4)

    assertEquals(clip.intersect(rect), ClipRect(row = 2, col = 5, width = 2, height = 2))
    assertEquals(rect.contains(2, 3), true)
    assertEquals(rect.contains(7, 3), false)

  test("nested stacks retain rectangles clips parents and stable grow remainder"):
    val first  = Lines(Vector("a"))
    val second = Lines(Vector("b"))
    val third  = Lines(Vector("c"))
    val row    = HStack(Seq(
      StackEntry(first, StackEntryOptions(basis = Some(1), grow = 1)),
      StackEntry(second, StackEntryOptions(basis = Some(1), grow = 1)),
      StackEntry(third, StackEntryOptions(basis = Some(1), grow = 1))
    ))
    val root   = VStack(Seq(StackEntry(row)))

    val frame = ViewportLayoutEngine.layout(root, width = 5, height = 1)
    val boxes = frame.root.children.head.children

    assertEquals(boxes.map(_.rect.width), Vector(2, 2, 1))
    assertEquals(boxes.map(_.rect.col), Vector(0, 2, 4))
    assertEquals(boxes.map(_.parent.exists(_ eq row)), Vector(true, true, true))
    assertEquals(boxes.map(_.clip), boxes.map(box => ClipRect(0, box.rect.col, box.rect.width, 1)))
    assertEquals(Ansi.strip(frame.lines.head), "a b c")

  test("shrink respects minimums and clips later over-constrained children deterministically"):
    val stack = HStack(
      Seq(
        StackEntry(Lines(Vector("AB")), StackEntryOptions(basis = Some(4), minSize = 2)),
        StackEntry(Lines(Vector("CD")), StackEntryOptions(basis = Some(4), minSize = 2))
      ),
      gap = 1
    )

    val frame = ViewportLayoutEngine.layout(stack, width = 3, height = 1)

    assertEquals(frame.root.children.map(_.rect.width), Vector(2, 2))
    assertEquals(frame.root.children.map(_.rect.col), Vector(0, 3))
    assertEquals(frame.root.children.map(_.clip.width), Vector(2, 0))
    assertEquals(Ansi.strip(frame.lines.head), "AB")

  test("basis grow shrink maximum gap and alignment share one geometry model"):
    val top    = Lines(Vector("x"))
    val bottom = Lines(Vector("yy"))
    val stack  = VStack(
      Seq(
        StackEntry(
          top,
          StackEntryOptions(basis = Some(1), grow = 1, minSize = 1, maxSize = Some(2))
        ),
        StackEntry(bottom, StackEntryOptions(basis = Some(3), shrink = 1, minSize = 1))
      ),
      gap = 1,
      alignment = StackAlignment.End
    )

    val frame = ViewportLayoutEngine.layout(stack, width = 4, height = 4)

    assertEquals(frame.root.children.map(_.rect.height), Vector(1, 2))
    assertEquals(frame.root.children.map(_.rect.col), Vector(3, 2))
    assertEquals(frame.root.children.map(_.rect.width), Vector(1, 2))

  test("grow honors maximums and shrink assigns odd remainder in insertion order"):
    val growing   = VStack(Seq(
      StackEntry(
        Lines(Vector("a")),
        StackEntryOptions(basis = Some(1), grow = 1, maxSize = Some(2))
      ),
      StackEntry(Lines(Vector("b")), StackEntryOptions(basis = Some(1), grow = 1))
    ))
    val shrinking = HStack(Seq(
      StackEntry(Lines(Vector("a")), StackEntryOptions(basis = Some(4))),
      StackEntry(Lines(Vector("b")), StackEntryOptions(basis = Some(4)))
    ))

    val grown  = ViewportLayoutEngine.layout(growing, width = 1, height = 5)
    val shrunk = ViewportLayoutEngine.layout(shrinking, width = 7, height = 1)

    assertEquals(grown.root.children.map(_.rect.height), Vector(2, 3))
    assertEquals(shrunk.root.children.map(_.rect.width), Vector(3, 4))

  test("responsive visibility removes hidden rectangles and gaps"):
    val stack = VStack(
      Seq(
        StackEntry(Lines(Vector("wide")), StackEntryOptions(visible = _.width >= 5)),
        StackEntry(Lines(Vector("always")))
      ),
      gap = 2
    )

    val narrow = ViewportLayoutEngine.layout(stack, width = 4, height = 3)
    val wide   = ViewportLayoutEngine.layout(stack, width = 5, height = 4)

    assertEquals(narrow.root.children.length, 1)
    assertEquals(narrow.root.children.head.rect.row, 0)
    assertEquals(wide.root.children.length, 2)
    assertEquals(wide.root.children(1).rect.row, 3)

  test("zero and narrow dimensions never split wide graphemes"):
    val stack = HStack(Seq(StackEntry(Lines(Vector("界A")), StackEntryOptions(minSize = 2))))

    val zero   = ViewportLayoutEngine.layout(stack, width = 0, height = 0)
    val narrow = ViewportLayoutEngine.layout(stack, width = 1, height = 1)

    assertEquals(zero.lines, Vector.empty)
    assertEquals(narrow.lines, Vector(""))
    assertEquals(Ansi.strip(narrow.lines.head).contains("界"), false)

  test("width-only leaves render once per identity and width in one frame"):
    var renders = 0
    val shared  = new Component:
      override def render(width: Int): ComponentRender =
        renders += 1
        ComponentRender.text("x")
    val stack   = VStack(Seq(StackEntry(shared), StackEntry(shared)))

    ViewportLayoutEngine.layout(stack, width = 4, height = 2)

    assertEquals(renders, 1)

  test("invalid child metadata fails before translation or later child rendering"):
    var siblingRendered = false
    val invalid         = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("x"),
        Vector.empty,
        Vector(CursorPlacement(row = 1, column = 0))
      )
    val sibling         = new Component:
      override def render(width: Int): ComponentRender =
        siblingRendered = true
        ComponentRender.text("sibling")
    val stack           = VStack(Seq(StackEntry(invalid), StackEntry(sibling)))

    intercept[IllegalArgumentException](ViewportLayoutEngine.layout(stack, width = 4, height = 2))
    assertEquals(siblingRendered, false)

  test("invalid typed document metadata fails before layout translation"):
    val invalid = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("x"),
        Vector.empty,
        Vector.empty,
        DocumentMetadata(Vector(PromptStart(DocumentPosition(row = 1, column = 0))))
      )

    val error = intercept[IllegalArgumentException](
      ViewportLayoutEngine.layout(VStack(Seq(StackEntry(invalid))), width = 4, height = 1)
    )

    assertEquals(
      error.getMessage,
      ComponentRenderValidationError.DocumentPositionOutsideRows(1, 0, 1).toString
    )

  test("cursor controls and typed prompt markers translate and clip together"):
    val control = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("AAAA").toOption.get,
      imageId = 41,
      widthCells = 1,
      heightCells = 1
    )
    val child   = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("ab"),
        Vector(TerminalControlPlacement(0, 1, control)),
        Vector(CursorPlacement(0, 0)),
        DocumentMetadata(Vector(PromptStart(DocumentPosition(0, 1))))
      )
    val stack   = HStack(Seq(
      StackEntry(Lines(Vector("x")), StackEntryOptions(basis = Some(1))),
      StackEntry(child, StackEntryOptions(basis = Some(2)))
    ))

    val frame = ViewportLayoutEngine.layout(stack, width = 3, height = 1)

    assertEquals(frame.render.cursorPlacements, Vector(CursorPlacement(0, 1)))
    assertEquals(
      frame.render.controls.map(placement => placement.row -> placement.column),
      Vector(0 -> 2)
    )
    assertEquals(
      frame.documentMetadata,
      DocumentMetadata(Vector(PromptStart(DocumentPosition(0, 2))))
    )
    assertEquals(frame.render.documentMetadata, frame.documentMetadata)

  test("offscreen typed markers remain in layout metadata but not painted metadata"):
    val child = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("one", "two"),
        Vector.empty,
        Vector.empty,
        DocumentMetadata(Vector(PromptStart(DocumentPosition(1))))
      )
    val stack = VStack(Seq(StackEntry(child, StackEntryOptions(minSize = 1))))

    val frame = ViewportLayoutEngine.layout(stack, width = 4, height = 1)

    assertEquals(frame.documentMetadata.markers, Vector(PromptStart(DocumentPosition(1))))
    assertEquals(frame.render.documentMetadata, DocumentMetadata.empty)

  test("partially clipped typed controls are omitted rather than partially executed"):
    val control = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("AAAA").toOption.get,
      imageId = 42,
      widthCells = 2,
      heightCells = 1
    )
    val child   = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("ab"),
        Vector(TerminalControlPlacement(0, 0, control)),
        Vector.empty
      )
    val stack   = HStack(Seq(
      StackEntry(child, StackEntryOptions(basis = Some(2), minSize = 2))
    ))

    val frame = ViewportLayoutEngine.layout(stack, width = 1, height = 1)

    assertEquals(frame.render.controls, Vector.empty)

  test("typed prompt markers remain authoritative while OSC 133-looking strings stay inert"):
    val typed = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("prompt"),
        Vector.empty,
        Vector.empty,
        DocumentMetadata(Vector(PromptStart(DocumentPosition(0))))
      )
    val inert = Lines(Vector("\u001b]133;A\u0007prompt"))
    val stack = VStack(Seq(StackEntry(inert), StackEntry(typed)))

    val frame = ViewportLayoutEngine.layout(stack, width = 20, height = 2)

    assertEquals(frame.documentMetadata.markers, Vector(PromptStart(DocumentPosition(1))))
    assertEquals(frame.render.documentMetadata, frame.documentMetadata)

  test("direct stack rendering is unbounded by basis and includes responsive children"):
    val stack = VStack(
      Seq(
        StackEntry(
          Lines(Vector("one", "two")),
          StackEntryOptions(basis = Some(1), visible = _ => false)
        ),
        StackEntry(Lines(Vector("three")))
      ),
      gap = 1
    )

    assertEquals(stack.render(8).lines.map(Ansi.strip), Vector("one", "two", "", "three"))

  test("direct horizontal rendering remains width-constrained without viewport height clipping"):
    val stack = HStack(
      Seq(
        StackEntry(Lines(Vector("a", "c")), StackEntryOptions(basis = Some(2))),
        StackEntry(Lines(Vector("b")), StackEntryOptions(basis = Some(2)))
      ),
      gap = 1,
      alignment = StackAlignment.Start
    )

    assertEquals(stack.render(5).lines.map(Ansi.strip), Vector("a  b", "c"))

  test("layout providers propagate optional scroll ownership to descendants"):
    val child = Lines(Vector("owned"))
    val owner = new Component with ViewportLayoutProvider:
      override def render(width: Int): ComponentRender    = ComponentRender.text("fallback")
      override def viewportLayout: ViewportLayout         = ViewportStackLayout(
        StackAxis.Vertical,
        Vector(ViewportStackEntry(child))
      )
      override def viewportScrollOwner: Option[Component] = Some(this)

    val frame = ViewportLayoutEngine.layout(owner, width = 5, height = 1)

    assertEquals(frame.root.scrollOwner.exists(_ eq owner), true)
    assertEquals(frame.root.children.head.scrollOwner.exists(_ eq owner), true)

  test("stacks propagate context and invalidation across child ownership"):
    val tui         = TUI(scalatui.terminal.VirtualTerminal(5, 2))
    var contexts    = Vector.empty[Option[TUIContext]]
    var invalidated = 0
    val child       = new Component with ContextualComponent:
      override def render(width: Int): ComponentRender           = ComponentRender.text("x")
      override def tuiContext_=(value: Option[TUIContext]): Unit = contexts :+= value
      override def invalidate(): Unit                            = invalidated += 1
    val stack       = VStack()

    stack.tuiContext_=(Some(tui))
    stack.addChild(child)
    stack.invalidate()
    stack.clear()

    assertEquals(contexts, Vector(Some(tui), None))
    assertEquals(invalidated, 1)

  test("overlay composition translates typed markers and confines them to overlay geometry"):
    val base    = ComponentRender(
      Vector("base"),
      Vector.empty,
      Vector.empty,
      DocumentMetadata(Vector(PromptStart(DocumentPosition(0, 0))))
    )
    val overlay = ComponentRender(
      Vector("xx"),
      Vector.empty,
      Vector.empty,
      DocumentMetadata(Vector(PromptStart(DocumentPosition(0, 1))))
    )

    val frame = OverlayRenderer.composite(
      base,
      Vector(overlay -> ResolvedOverlay(2, 0, 1, None)),
      terminalWidth = 4,
      terminalHeight = 1
    )

    assertEquals(
      frame.documentMetadata.markers,
      Vector(
        PromptStart(DocumentPosition(0, 0)),
        PromptStart(DocumentPosition(0, 2))
      )
    )
