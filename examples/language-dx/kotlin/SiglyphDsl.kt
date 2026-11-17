package scalatui.kotlin

import java.util.function.Function
import scalatui.components.DataTable
import scalatui.components.TableSelection
import scalatui.core.ApplicationScope
import scalatui.core.ApplicationSubmission
import scalatui.terminal.jvm.interop.AppBuilder
import scalatui.terminal.jvm.interop.JvmUi
import scalatui.terminal.jvm.interop.TableBuilder

/** Unreleased optional source DSL. JVM only; no coroutine or scheduler ownership. */
fun JvmUi.app(configure: AppBuilder.() -> Unit): AppBuilder = app().apply(configure)

fun <A : Any> JvmUi.table(id: (A) -> String, configure: TableBuilder<A>.() -> Unit): TableBuilder<A> =
    table(Function<A, String> { id(it) }).apply(configure)

/** Empty selection is explicitly nullable; rows themselves are non-null. */
fun <A : Any> TableBuilder<A>.selectedOrNull(table: DataTable<A>): TableSelection<A>? =
    selection(table).orElse(null)

/** Named separately to avoid ambiguity with Java SAM overloads. */
fun JvmUi.update(scope: ApplicationScope, action: () -> Unit): ApplicationSubmission =
    submit(scope, Runnable { action() })
