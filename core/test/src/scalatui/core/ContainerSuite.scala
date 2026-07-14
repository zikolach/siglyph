package scalatui.core

class ContainerSuite extends munit.FunSuite:
  test("container renders children in insertion order"):
    val container = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text(Vector(s"a:$width")))
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text(Vector("b")))

    assertEquals(container.render(12).lines, Vector("a:12", "b"))

  test("container invalidates children"):
    var invalidated = 0
    val container   = Container()
    container.addChild(new Component:
      override def render(width: Int): ComponentRender = ComponentRender.empty
      override def invalidate(): Unit                  = invalidated += 1)

    container.invalidate()
    assertEquals(invalidated, 1)
