package scalatui.components

import scalatui.ansi.Ansi
import scalatui.terminal.KeybindingManager
import scalatui.theme.*

class SemanticThemeSuite extends munit.FunSuite:
  test("plain defaults preserve input and table output and old constructors"):
    val input   = new Input("hello", KeybindingManager())
    val themed  = Input("hello", theme = SemanticTheme.Plain)
    input.focused = true
    themed.focused = true
    assertEquals(input.render(20), themed.render(20))
    val data    = TableData(Vector("one"), identity[String])
    val columns = Vector(TableColumn[String]("Value", 8, identity[String]))
    val old     = new DataTable(data, columns, (_: Option[TableSelection[String]]) => (), 10)
    val plain   = DataTable(data, columns, theme = SemanticTheme.Plain)
    assertEquals(old.render(20), plain.render(20))

  test("nested overrides are immutable and disabled wins over selected/focused"):
    val parent = SemanticTheme.Dark
    val child  =
      parent.withOverrides(Map(SemanticRole.Error -> SemanticStyle(Some(ThemeColor.Blue))))
    val nested = child.withOverrides(Map(SemanticRole.Focused -> SemanticStyle(underline = true)))
    assertEquals(parent.style(SemanticRole.Error).color, Some(ThemeColor.Red))
    assertEquals(child.style(SemanticRole.Error).color, Some(ThemeColor.Blue))
    assertEquals(nested.style(SemanticRole.Error), child.style(SemanticRole.Error))
    assertEquals(
      parent.role(focused = true, selected = true, disabled = true),
      SemanticRole.Disabled
    )
    assertEquals(parent.role(focused = true, selected = true), SemanticRole.Focused)

  test("all palettes preserve Unicode width and cursor geometry at narrow sizes"):
    Vector(
      SemanticTheme.Dark,
      SemanticTheme.Light,
      SemanticTheme.Monochrome,
      SemanticTheme.HighContrast
    ).foreach { theme =>
      val input = Input("界e\u0301🐱", theme = theme)
      input.focused = true
      (0 to 12).foreach { width =>
        val result = input.render(width)
        assert(result.lines.forall(line => Ansi.visibleWidth(line) <= width))
        assert(result.cursorPlacements.forall(cursor => cursor.column < width))
      }
      val table = DataTable(
        TableData(Vector("界🐱"), identity[String]),
        Vector(TableColumn[String]("Value", 8, identity[String])),
        theme = theme
      )
      table.focused = true
      (0 to 12).foreach(width =>
        assert(table.renderRange(width, 0, 2).lines.forall(line =>
          Ansi.visibleWidth(line) <= width
        ))
      )
    }

  test("semantic list adapter preserves explicit custom hook precedence"):
    val custom =
      SelectListTheme.semantic(SemanticTheme.Dark).copy(selectedText = text => "[" + text + "]")
    val list   = SelectList(Vector(SelectItem("a", "alpha")), SelectListOptions(theme = custom))
    assert(Ansi.strip(list.render(30).lines.mkString).contains("[alpha]"))
    assertEquals(SemanticTheme.Plain.paint(SemanticRole.Error, "literal"), "literal")
    assert(SemanticTheme.Monochrome.style(SemanticRole.Focused).underline)
    assert(SemanticTheme.Monochrome.style(SemanticRole.Selected).reverse)
    assert(SemanticTheme.Monochrome.styles.values.forall(_.color.isEmpty))
