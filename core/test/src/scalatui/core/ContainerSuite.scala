package scalatui.core

import scalatui.terminal.{Base64ImagePayload, TerminalImageProtocol}

class ContainerSuite extends munit.FunSuite:
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
