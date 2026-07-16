package scalatui.extras

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*

class ExtrasSuite extends munit.FunSuite:
  test("expandable text renders collapsed and expanded states within width"):
    val text = ExpandableText("short", "expanded value wraps", paddingX = 1)

    val collapsed = text.render(8).lines
    assert(collapsed.exists(line => Ansi.strip(line).contains("short")), collapsed.toString)
    assert(collapsed.forall(Ansi.visibleWidth(_) <= 8), collapsed.toString)

    text.setExpanded(true)
    val expanded = text.render(24).lines
    assert(
      expanded.exists(line => Ansi.strip(line).contains("expanded value wraps")),
      expanded.toString
    )
    assert(expanded.forall(Ansi.visibleWidth(_) <= 24), expanded.toString)

    val narrow = text.render(8).lines
    assert(narrow.forall(Ansi.visibleWidth(_) <= 8), narrow.toString)

  test("expandable text constrains final plain and ANSI-themed lines at all widths"):
    val red  = "\u001b[31m"
    val text = ExpandableText(
      "collapsed",
      "expanded",
      initiallyExpanded = false,
      paddingY = 1,
      theme = ExpandableTextTheme(
        collapsed = line => s">$line<",
        expanded = line => s"$red+$line-$red${Ansi.Reset}"
      )
    )

    val normal = text.render(12).lines
    assert(normal.exists(line => Ansi.strip(line).startsWith(">")), normal.toString)
    assert(normal.forall(Ansi.visibleWidth(_) <= 12), normal.toString)

    val narrow = text.render(1).lines
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)

    val zero = text.render(0).lines
    assert(zero.forall(Ansi.visibleWidth(_) === 0), zero.toString)

    text.setExpanded(true)
    val styled = text.render(12).lines
    assert(styled.exists(_.startsWith(red)), styled.toString)
    assert(styled.filter(_.startsWith(red)).forall(_.endsWith(Ansi.Reset)), styled.toString)
    assert(styled.forall(Ansi.visibleWidth(_) <= 12), styled.toString)

    val styledNarrow = text.render(1).lines
    assert(styledNarrow.forall(Ansi.visibleWidth(_) <= 1), styledNarrow.toString)

    val styledZero = text.render(0).lines
    assert(styledZero.forall(Ansi.visibleWidth(_) === 0), styledZero.toString)

  test("expandable text keeps theme callback invocation count and order"):
    var callbacks = Vector.empty[String]
    val text      = ExpandableText(
      "collapsed",
      "expanded",
      paddingY = 1,
      theme = ExpandableTextTheme(
        collapsed = line =>
          callbacks :+= s"collapsed:$line"
          line
        ,
        expanded = line =>
          callbacks :+= s"expanded:$line"
          line
      )
    )

    text.render(12)
    assertEquals(callbacks.map(_.takeWhile(_ !== ':')), Vector("collapsed", "collapsed"))

    text.render(12)
    assertEquals(callbacks.map(_.takeWhile(_ !== ':')), Vector("collapsed", "collapsed"))

    text.setExpanded(true)
    text.render(12)
    assertEquals(
      callbacks.map(_.takeWhile(_ !== ':')),
      Vector("collapsed", "collapsed", "expanded", "expanded")
    )

  test("expandable text updates cached output when state or provider output changes"):
    var collapsedValue = "one"
    var expandedValue  = "two"
    val text           = new ExpandableText(() => collapsedValue, () => expandedValue)

    assertEquals(text.render(20).lines.map(line => Ansi.strip(line).trim), Vector("one"))

    collapsedValue = "changed"
    assertEquals(text.render(20).lines.map(line => Ansi.strip(line).trim), Vector("changed"))

    text.setExpanded(true)
    assertEquals(text.render(20).lines.map(line => Ansi.strip(line).trim), Vector("two"))

    expandedValue = "changed expanded"
    assertEquals(
      text.render(20).lines.map(line => Ansi.strip(line).trim),
      Vector("changed expanded")
    )

  test("expandable section renders title, body, hint, and narrow widths safely"):
    val section = ExpandableSection(
      "Details",
      "short body",
      "long expanded body",
      hintText = Some("press ctrl-o"),
      paddingX = 1
    )

    val collapsed = section.render(12).lines
    assert(collapsed.exists(line => Ansi.strip(line).contains("Details")), collapsed.toString)
    assert(collapsed.exists(line => Ansi.strip(line).contains("short")), collapsed.toString)
    assert(collapsed.exists(line => Ansi.strip(line).contains("press")), collapsed.toString)
    assert(!collapsed.exists(line => Ansi.strip(line).contains("expanded")), collapsed.toString)
    assert(collapsed.forall(Ansi.visibleWidth(_) <= 12), collapsed.toString)

    section.setExpanded(true)
    val expanded = section.render(24).lines
    assert(expanded.exists(line => Ansi.strip(line).contains("Details")), expanded.toString)
    assert(
      expanded.exists(line => Ansi.strip(line).contains("long expanded body")),
      expanded.toString
    )
    assert(!expanded.exists(line => Ansi.strip(line).contains("press")), expanded.toString)
    assert(expanded.forall(Ansi.visibleWidth(_) <= 24), expanded.toString)

    val narrow = section.render(1).lines
    assert(narrow.nonEmpty)
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)

  test("expandable section constrains every final themed block at all widths"):
    val cyan    = "\u001b[36m"
    val section = ExpandableSection(
      "Title",
      "collapsed",
      "expanded",
      hintText = Some("hint"),
      hintVisibility = ExpansionHintVisibility.Always,
      theme = ExpandableSectionTheme(
        title = line => s">$line",
        collapsedBody = line => s"$line<",
        expandedBody = line => s"$cyan+$line-${Ansi.Reset}",
        hint = line => s"$cyan!$line?${Ansi.Reset}"
      )
    )

    val normal = section.render(10).lines
    assert(normal.exists(line => Ansi.strip(line).startsWith(">")), normal.toString)
    assert(normal.exists(_.startsWith(cyan)), normal.toString)
    assert(normal.filter(_.startsWith(cyan)).forall(_.endsWith(Ansi.Reset)), normal.toString)
    assert(normal.forall(Ansi.visibleWidth(_) <= 10), normal.toString)

    val narrow = section.render(1).lines
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)

    val zero = section.render(0).lines
    assert(zero.forall(Ansi.visibleWidth(_) === 0), zero.toString)

    section.setExpanded(true)
    val expanded = section.render(10).lines
    assert(expanded.count(_.startsWith(cyan)) >= 2, expanded.toString)
    assert(expanded.filter(_.startsWith(cyan)).forall(_.endsWith(Ansi.Reset)), expanded.toString)
    assert(expanded.forall(Ansi.visibleWidth(_) <= 10), expanded.toString)

    val expandedNarrow = section.render(1).lines
    assert(expandedNarrow.forall(Ansi.visibleWidth(_) <= 1), expandedNarrow.toString)

    val expandedZero = section.render(0).lines
    assert(expandedZero.forall(Ansi.visibleWidth(_) === 0), expandedZero.toString)

  test("expandable section keeps theme callback invocation order"):
    var callbacks = Vector.empty[String]
    val section   = ExpandableSection(
      "Title",
      "collapsed",
      "expanded",
      hintText = Some("hint"),
      hintVisibility = ExpansionHintVisibility.Always,
      theme = ExpandableSectionTheme(
        title = line =>
          callbacks :+= "title"
          line
        ,
        collapsedBody = line =>
          callbacks :+= "collapsedBody"
          line
        ,
        expandedBody = line =>
          callbacks :+= "expandedBody"
          line
        ,
        hint = line =>
          callbacks :+= "hint"
          line
      )
    )

    section.render(20)
    assertEquals(callbacks, Vector("title", "collapsedBody", "hint"))

    section.render(20)
    assertEquals(callbacks, Vector("title", "collapsedBody", "hint"))

    section.setExpanded(true)
    section.render(20)
    assertEquals(
      callbacks,
      Vector("title", "collapsedBody", "hint", "title", "expandedBody", "hint")
    )

  test("expandable section supports configured hint visibility"):
    val section = ExpandableSection(
      "More",
      "collapsed",
      "expanded",
      hintText = Some("shown while expanded"),
      hintVisibility = ExpansionHintVisibility.ExpandedOnly
    )

    assert(!section.render(40).lines.exists(line => Ansi.strip(line).contains("shown")))

    section.setExpanded(true)
    assert(section.render(40).lines.exists(line => Ansi.strip(line).contains("shown")))

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
    assert(text.render(20).lines.exists(line => Ansi.strip(line).contains("expanded")))
    assert(section.render(20).lines.exists(line => Ansi.strip(line).contains("expanded")))

private final class RecordingExpandable extends Expandable:
  var states = Vector.empty[Boolean]

  override def setExpanded(expanded: Boolean): Unit = states :+= expanded
