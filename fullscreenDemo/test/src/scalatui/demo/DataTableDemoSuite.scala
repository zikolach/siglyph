package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.terminal.TerminalKey
import scalatui.testing.TUITestDriver

class DataTableDemoSuite extends munit.FunSuite:
  test("generic table reveals Home and End selection and survives narrow resize"):
    val completed = TUITestDriver.withSession(80, 12)(DataTableDemo.apply) { driver =>
      driver.key(TerminalKey.End)
      driver.assertContains("item-99999")
      driver.resize(70, 8)
      driver.assertContains("item-99999")
      driver.key(TerminalKey.Home)
      driver.assertContains("item-0")
      driver.assertContains("Name")
      driver.resize(5, 3)
      val screen = driver.screen
      assertEquals(screen.rows, 3)
      assert(screen.lines.forall(line => Ansi.visibleWidth(line) <= 5))
      driver
    }
    assert(completed.isClosed)
