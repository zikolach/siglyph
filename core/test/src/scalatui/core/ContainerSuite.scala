package scalatui.core

import scalatui.components.Box
import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol}

class ContainerSuite extends munit.FunSuite:
  final class Lines(value: Vector[String]) extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(value)

  test("component default render frame records leaf bounds"):
    val component = Lines(Vector("\u001b[31mwide\u001b[0m", "界"))
    val frame     = component.renderFrame(width = 6, row = 2, col = 3)

    assertEquals(frame.lines, component.render(6).lines)
    assertEquals(frame.layout.component, component)
    assertEquals(frame.layout.bounds, LayoutBounds(row = 2, col = 3, width = 6, height = 2))
    assertEquals(frame.layout.children, Vector.empty)

  test("container render frame records vertical child offsets"):
    val first     = Lines(Vector("a", "b"))
    val second    = Lines(Vector("c", "d", "e"))
    val container = Container()
    container.addChild(first)
    container.addChild(second)

    val frame = container.renderFrame(width = 10)

    assertEquals(frame.lines, Vector("a", "b", "c", "d", "e"))
    assertEquals(
      frame.layout.children.map(_.bounds),
      Vector(
        LayoutBounds(row = 0, col = 0, width = 10, height = 2),
        LayoutBounds(row = 2, col = 0, width = 10, height = 3)
      )
    )

  test("nested container render frame records descendant bounds"):
    val leaf  = Lines(Vector("leaf"))
    val inner = Container()
    val outer = Container()
    inner.addChild(leaf)
    outer.addChild(Lines(Vector("top")))
    outer.addChild(inner)

    val frame = outer.renderFrame(width = 12)

    val innerNode = frame.layout.children(1)
    assertEquals(innerNode.bounds, LayoutBounds(row = 1, col = 0, width = 12, height = 1))
    assertEquals(
      innerNode.children.head.bounds,
      LayoutBounds(row = 1, col = 0, width = 12, height = 1)
    )

  test("box render frame retains padded children and nested descendants in visual order"):
    val first  = Lines(Vector("a", "b"))
    val leaf   = Lines(Vector("leaf"))
    val nested = Container()
    nested.addChild(leaf)
    val box    = Box(paddingX = 2, paddingY = 1)
    box.addChild(first)
    box.addChild(nested)

    val frame = box.renderFrame(width = 10, row = 3, col = 4)

    assertEquals(
      frame.lines,
      Vector("          ", "  a       ", "  b       ", "  leaf    ", "          ")
    )
    assertEquals(frame.render, box.render(10))
    assertEquals(frame.layout.bounds, LayoutBounds(row = 3, col = 4, width = 10, height = 5))
    assertEquals(
      frame.layout.children.map(_.bounds),
      Vector(
        LayoutBounds(row = 4, col = 6, width = 6, height = 2),
        LayoutBounds(row = 6, col = 6, width = 6, height = 1)
      )
    )
    assertEquals(
      frame.layout.children(1).children.head.bounds,
      LayoutBounds(row = 6, col = 6, width = 6, height = 1)
    )

  test("box render frame normalizes negative padding for layout and cursor geometry"):
    val child = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("x"),
        Vector.empty,
        Vector(CursorPlacement(row = 0, column = 0))
      )
    val box   = Box(paddingX = -2, paddingY = -3)
    box.addChild(child)

    val frame = box.renderFrame(width = 4, row = 5, col = 7)

    assertEquals(frame.render, box.render(4))
    assertEquals(frame.lines, Vector("x   "))
    assertEquals(frame.render.cursorPlacements, Vector(CursorPlacement(row = 0, column = 0)))
    assertEquals(frame.layout.bounds, LayoutBounds(row = 5, col = 7, width = 4, height = 1))
    assertEquals(
      frame.layout.children.map(_.bounds),
      Vector(LayoutBounds(row = 5, col = 7, width = 4, height = 1))
    )

  test("container renders children in insertion order"):
    val container = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text(Vector(s"a:$width")))
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text(Vector("b")))

    assertEquals(container.render(12).lines, Vector("a:12", "b"))

  test("container rejects a malformed cursor child before rendering a sibling"):
    var siblingRendered = false
    val container       = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("first"),
        Vector.empty,
        Vector(CursorPlacement(1, 0))
      ))
    container.addChild(new Component:
      override def render(width: Int): ComponentRender =
        siblingRendered = true
        ComponentRender.text("sibling"))

    intercept[IllegalArgumentException](container.render(10))

    assertEquals(siblingRendered, false)

  test("container rejects a malformed control child before rendering a sibling"):
    val control         = TerminalImageProtocol.encodeKitty(
      Base64ImagePayload.from("AAAA").toOption.get,
      imageId = 1,
      widthCells = 1,
      heightCells = 1
    )
    var siblingRendered = false
    val container       = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector("first"),
        Vector(TerminalControlPlacement(1, 0, control)),
        Vector.empty
      ))
    container.addChild(new Component:
      override def render(width: Int): ComponentRender =
        siblingRendered = true
        ComponentRender.text("sibling"))

    intercept[IllegalArgumentException](container.render(10))

    assertEquals(siblingRendered, false)

  test("container invalidates children"):
    var invalidated = 0
    val container   = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.empty
      override def invalidate(): Unit                  = invalidated += 1)

    container.invalidate()
    assertEquals(invalidated, 1)

  test("container and box propagate context idempotently across child mutation and detach"):
    val firstTui  = TUI(scalatui.terminal.VirtualTerminal(10, 5))
    val secondTui = TUI(scalatui.terminal.VirtualTerminal(10, 5))
    var contexts  = Vector.empty[Option[TUIContext]]
    val leaf      = new Component with ContextualComponent:
      override def render(width: Int): ComponentRender           = ComponentRender.empty
      override def tuiContext_=(value: Option[TUIContext]): Unit = contexts :+= value
    val inner     = Container()
    val outer     = Box(paddingX = 0)
    outer.addChild(inner)
    outer.tuiContext_=(Some(firstTui))
    outer.tuiContext_=(Some(firstTui))
    inner.addChild(leaf)
    inner.addChild(leaf)

    assertEquals(contexts, Vector(Some(firstTui)))
    assertEquals(inner.removeChild(leaf), true)
    assertEquals(contexts, Vector(Some(firstTui)))
    assertEquals(inner.removeChild(leaf), true)
    assertEquals(contexts, Vector(Some(firstTui), None))

    inner.addChild(leaf)
    outer.tuiContext_=(Some(secondTui))
    outer.clear()

    assertEquals(
      contexts,
      Vector(Some(firstTui), None, Some(firstTui), Some(secondTui), None)
    )
