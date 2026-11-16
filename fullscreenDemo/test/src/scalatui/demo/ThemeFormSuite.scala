package scalatui.demo

import scalatui.terminal.{KeyModifiers, TerminalKey}
import scalatui.testing.TUITestDriver
import scalatui.theme.SemanticTheme

class ThemeFormSuite extends munit.FunSuite:
  test(
    "keyboard validation, disabled-field skipping and overlay focus restore work in every palette"
  ):
    Vector(
      SemanticTheme.Dark,
      SemanticTheme.Light,
      SemanticTheme.Monochrome,
      SemanticTheme.HighContrast
    ).foreach { theme =>
      var form = Option.empty[ThemeFormDemo]
      TUITestDriver.withSession(80, 18)(terminal =>
        val built = ThemeFormDemo(terminal, theme)
        form = Some(built)
        built.tui
      ) { driver =>
        driver.key(TerminalKey.Enter)
        driver.assertContains("! Name is required")
        driver.typeText("Ada")
        driver.key(TerminalKey.Tab)
        assertEquals(form.get.focusedField, 1)
        driver.key(TerminalKey.Tab)
        assertEquals(form.get.focusedField, 2)
        driver.key(TerminalKey.Tab)
        assertEquals(form.get.focusedField, 0)
        driver.key(TerminalKey.Tab, KeyModifiers(shift = true))
        assertEquals(form.get.focusedField, 2)
        driver.key(TerminalKey.End)
        assertEquals(form.get.table.selection.map(_.index), Some(1))
        driver.key(TerminalKey.Character("s"), KeyModifiers(ctrl = true))
        assert(driver.tui.hasOverlay)
        driver.assertContains("Review Ada")
        driver.key(TerminalKey.Tab)
        assertEquals(form.get.focusedField, 2)
        driver.key(TerminalKey.Escape)
        assert(!driver.tui.hasOverlay)
        driver.key(TerminalKey.Home)
        assertEquals(form.get.table.selection.map(_.index), Some(0))
        driver.assertContains("Cancelled")
        driver.resize(12, 5)
        assertEquals(driver.screen.columns, 12)
      }
    }

  test("confirming a modal restores editable input focus"):
    var form = Option.empty[ThemeFormDemo]
    TUITestDriver.withSession(80, 18)(terminal =>
      val built = ThemeFormDemo(terminal)
      form = Some(built)
      built.tui
    ) { driver =>
      driver.typeText("Ada")
      driver.key(TerminalKey.Enter)
      driver.key(TerminalKey.Enter)
      driver.assertContains("Saved: Ada")
      driver.typeText("!")
      assertEquals(form.get.name.value, "Ada!")
    }
