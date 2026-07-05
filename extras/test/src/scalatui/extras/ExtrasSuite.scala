package scalatui.extras

import scalatui.ansi.Ansi

class ExtrasSuite extends munit.FunSuite:
  test("expandable text renders collapsed and expanded states within width"):
    val text = ExpandableText("short", "expanded value wraps", paddingX = 1)

    val collapsed = text.render(8)
    assert(collapsed.exists(line => Ansi.strip(line).contains("short")), collapsed.toString)
    assert(collapsed.forall(Ansi.visibleWidth(_) <= 8), collapsed.toString)

    text.setExpanded(true)
    val expanded = text.render(24)
    assert(
      expanded.exists(line => Ansi.strip(line).contains("expanded value wraps")),
      expanded.toString
    )
    assert(expanded.forall(Ansi.visibleWidth(_) <= 24), expanded.toString)

    val narrow = text.render(8)
    assert(narrow.forall(Ansi.visibleWidth(_) <= 8), narrow.toString)

  test("expandable text updates cached output when state or provider output changes"):
    var collapsedValue = "one"
    var expandedValue  = "two"
    val text           = new ExpandableText(() => collapsedValue, () => expandedValue)

    assertEquals(text.render(20).map(line => Ansi.strip(line).trim), Vector("one"))

    collapsedValue = "changed"
    assertEquals(text.render(20).map(line => Ansi.strip(line).trim), Vector("changed"))

    text.setExpanded(true)
    assertEquals(text.render(20).map(line => Ansi.strip(line).trim), Vector("two"))

    expandedValue = "changed expanded"
    assertEquals(text.render(20).map(line => Ansi.strip(line).trim), Vector("changed expanded"))

  test("expandable section renders title, body, hint, and narrow widths safely"):
    val section = ExpandableSection(
      "Details",
      "short body",
      "long expanded body",
      hintText = Some("press ctrl-o"),
      paddingX = 1
    )

    val collapsed = section.render(12)
    assert(collapsed.exists(line => Ansi.strip(line).contains("Details")), collapsed.toString)
    assert(collapsed.exists(line => Ansi.strip(line).contains("short")), collapsed.toString)
    assert(collapsed.exists(line => Ansi.strip(line).contains("press")), collapsed.toString)
    assert(!collapsed.exists(line => Ansi.strip(line).contains("expanded")), collapsed.toString)
    assert(collapsed.forall(Ansi.visibleWidth(_) <= 12), collapsed.toString)

    section.setExpanded(true)
    val expanded = section.render(24)
    assert(expanded.exists(line => Ansi.strip(line).contains("Details")), expanded.toString)
    assert(
      expanded.exists(line => Ansi.strip(line).contains("long expanded body")),
      expanded.toString
    )
    assert(!expanded.exists(line => Ansi.strip(line).contains("press")), expanded.toString)
    assert(expanded.forall(Ansi.visibleWidth(_) <= 24), expanded.toString)

    val narrow = section.render(1)
    assert(narrow.nonEmpty)
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)

  test("expandable section supports configured hint visibility"):
    val section = ExpandableSection(
      "More",
      "collapsed",
      "expanded",
      hintText = Some("shown while expanded"),
      hintVisibility = ExpansionHintVisibility.ExpandedOnly
    )

    assert(!section.render(40).exists(line => Ansi.strip(line).contains("shown")))

    section.setExpanded(true)
    assert(section.render(40).exists(line => Ansi.strip(line).contains("shown")))

  test("expansion controller applies state and stops mutating unregistered expandables"):
    val first  = RecordingExpandable()
    val second = RecordingExpandable()
    val ctl    = ExpansionController(initiallyExpanded = true)

    ctl.register(first)
    ctl.register(second)

    assertEquals(first.states, Vector(true))
    assertEquals(second.states, Vector(true))
    assertEquals(ctl.size, 2)

    assertEquals(ctl.setExpanded(false), true)
    assertEquals(first.states, Vector(true, false))
    assertEquals(second.states, Vector(true, false))

    assertEquals(ctl.setExpanded(false), false)
    assertEquals(first.states, Vector(true, false))

    assertEquals(ctl.unregister(first), true)
    assertEquals(ctl.toggle(), true)
    assertEquals(first.states, Vector(true, false))
    assertEquals(second.states, Vector(true, false, true))

    ctl.clear()
    assertEquals(ctl.size, 0)
    assertEquals(ctl.setExpanded(false), true)
    assertEquals(second.states, Vector(true, false, true))

  test("expansion controller snapshots registered expandables before state callbacks"):
    val controller = ExpansionController()
    val second     = RecordingExpandable()
    val first      = new Expandable:
      override def setExpanded(expanded: Boolean): Unit =
        controller.unregister(second)

    controller.register(first)
    controller.register(second)

    assertEquals(controller.setExpanded(true), true)
    assertEquals(second.states, Vector(false, true))

  test("extras compile without importing terminal, markdown, image, demo, or agent APIs"):
    val text: scalatui.core.Component    = ExpandableText("collapsed", "expanded")
    val section: scalatui.core.Component = ExpandableSection("title", "collapsed", "expanded")
    val controller                       = ExpansionController()

    controller.register(text.asInstanceOf[Expandable])
    controller.register(section.asInstanceOf[Expandable])
    assertEquals(controller.setExpanded(true), true)
    assert(text.render(20).exists(line => Ansi.strip(line).contains("expanded")))
    assert(section.render(20).exists(line => Ansi.strip(line).contains("expanded")))

private final class RecordingExpandable extends Expandable:
  var states = Vector.empty[Boolean]

  override def setExpanded(expanded: Boolean): Unit = states :+= expanded
