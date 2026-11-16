import scalatui.terminal.jvm.interop.SiglyphJvm

fun main() {
    val api = SiglyphJvm()
    val input = api.createInput("seed")
    check(input.value() == "seed")
    input.setValue("updated")
    check(input.value() == "updated")
    check(api.onSubmit(input) { _ -> } === input)
    check(!api.createText("text", 0, 0).render(20).lines().isEmpty())
    println("Kotlin consumer passed")
}
