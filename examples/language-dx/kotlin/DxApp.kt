import scalatui.kotlin.*
import scalatui.scheduling.ManualTaskScheduler
import scalatui.terminal.jvm.interop.JvmUi
import scalatui.terminal.jvm.interop.SiglyphJvm

private data class Row(val id: String, val name: String)

fun main(args: Array<String>) {
    val dx = JvmUi()
    val basic = SiglyphJvm()
    var rows = listOf(Row("a", "alpha"), Row("b", "界beta"))
    val model = dx.table<Row>({ it.id }) {
        rows(rows)
        column("Name", 16) { it.name }
        theme(dx.theme().preset("mono").build())
    }
    val table = model.build()
    val filter = basic.createInput()
    var submitted = ""
    basic.onSubmit(filter) { query ->
        submitted = query
        model.replace(table, rows.filter { it.name.contains(query) })
    }
    val app = dx.app {
        alternateScreen(true)
        add(filter)
        add(table)
        focus(filter)
    }
    if (args.firstOrNull() == "--interactive") {
        app.add(basic.createText("Enter: filter | Tab: focus | arrows: select | Esc: exit"))
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().use { executor ->
            scalatui.terminal.jvm.interop.JvmScheduler(executor, 8) { error ->
                throw IllegalStateException("scheduler failed", error)
            }.use { scheduler ->
                dx.run(app) { tui ->
                    dx.navigation(tui, filter, table)
                    val scope = dx.openScope(tui, 8, 1)
                    var tick = 0
                    dx.scheduleEvery(scope, scheduler, 1000L, 1000L) {
                        rows = listOf(rows[0], Row("b", "界beta ${++tick}"))
                        model.replace(table, rows.filter { it.name.contains(submitted) })
                    }
                }
            }
        }
        return
    }
    val clock = ManualTaskScheduler(8, 0L)
    dx.test(app, 40, 8, clock) { driver ->
        table.selectId("b")
        driver.paste("beta")
        driver.key("Enter")
        check(model.selectedOrNull(table)?.id() == "b")
        model.replace(table, emptyList())
        check(model.selectedOrNull(table) == null)
        val scope = driver.openScope(1, 1)
        filter.setValue("")
        driver.key("Enter")
        driver.typeText("absent")
        val task = dx.schedule(scope, clock, 1L) { model.replace(table, rows.filter { it.name.contains(submitted) }) }
        driver.advanceTimeBy(1L, 8)
        check(table.data().size() == 2) { "unsubmitted filter changed rows" }
        driver.key("Enter")
        check(table.data().size() == 0) { "submitted filter ignored" }
        driver.resize(12, 8)
        driver.assertContains("Name")
        scope.close()
        check(dx.admission(dx.update(scope) { error("stale update") }) == "Closed")
        check(task.isDone())
    }
    check(clock.isClosed())
    println("Kotlin DX consumer passed")
}
