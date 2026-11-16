import scalatui.components.Input;
import scalatui.terminal.jvm.interop.SiglyphJvm;

public final class JavaConsumer {
  public static void main(String[] args) {
    SiglyphJvm api = new SiglyphJvm();
    Input input = api.createInput("seed");
    if (!"seed".equals(input.value())) throw new AssertionError("initial value");
    input.setValue("updated");
    if (!"updated".equals(input.value())) throw new AssertionError("updated value");
    if (api.onSubmit(input, value -> {}) != input) throw new AssertionError("facade identity");
    if (api.createText("text", 0, 0).render(20).lines().isEmpty()) throw new AssertionError("render");
    System.out.println("Java consumer passed");
  }
}
