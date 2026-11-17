import java.util.List;
import scalatui.components.Input;
import scalatui.scheduling.ManualTaskScheduler;
import scalatui.terminal.jvm.interop.JvmUi;
import scalatui.terminal.jvm.interop.SiglyphJvm;

/** Candidate-only deterministic table/filter/async app; no real TTY required. */
public final class DxApp {
  record Row(String id, String name) {}
  public static void main(String[] args) {
    var dx = new JvmUi();
    var basic = new SiglyphJvm();
    var rows = List.of(new Row("a", "alpha"), new Row("b", "界beta"));
    var current = new java.util.concurrent.atomic.AtomicReference<>(rows);
    var model = dx.<Row>table(Row::id).rows(rows).column("Name", 16, Row::name)
        .theme(dx.theme().preset("mono").build());
    var table = model.build();
    Input filter = basic.createInput();
    final String[] submitted = {""};
    basic.onSubmit(filter, query -> {
      submitted[0] = query;
      model.replace(table, current.get().stream().filter(row -> row.name().contains(query)).toList());
    });
    var app = dx.app().alternateScreen(true).add(filter).add(table).focus(filter);
    if (args.length > 0 && args[0].equals("--interactive")) {
      app.add(basic.createText("Enter: filter | Tab: focus | arrows: select | Esc: exit"));
      try (var executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
           var scheduler = new scalatui.terminal.jvm.interop.JvmScheduler(executor, 8,
               error -> { throw new IllegalStateException("scheduler failed", error); })) {
        dx.run(app, tui -> {
          dx.navigation(tui, filter, table);
          var scope = dx.openScope(tui, 8, 1);
          final int[] tick = {0};
          dx.scheduleEvery(scope, scheduler, 1000L, 1000L, task -> {
            var updated = List.of(rows.get(0), new Row("b", "界beta " + ++tick[0]));
            current.set(updated);
            model.replace(table, updated.stream().filter(row -> row.name().contains(submitted[0])).toList());
          });
        });
      }
      return;
    }
    var clock = new ManualTaskScheduler(8, 0L);
    dx.test(app, 40, 8, clock, driver -> {
      table.selectId("b");
      driver.paste("beta");
      driver.key("Enter");
      if (!model.selection(table).orElseThrow().id().equals("b")) throw new AssertionError("stable ID");
      model.replace(table, List.of());
      if (model.selection(table).isPresent()) throw new AssertionError("empty selection");
      var scope = driver.openScope(1, 1);
      filter.setValue("");
      driver.key("Enter");
      driver.typeText("absent");
      var task = dx.schedule(scope, clock, 1L, () -> model.replace(table,
          rows.stream().filter(row -> row.name().contains(submitted[0])).toList()));
      driver.advanceTimeBy(1L, 8);
      if (table.data().size() != 2) throw new AssertionError("unsubmitted filter changed rows");
      driver.key("Enter");
      if (table.data().size() != 0) throw new AssertionError("submitted filter ignored");
      driver.resize(12, 8);
      driver.assertContains("Name");
      scope.close();
      if (!dx.admission(dx.submit(scope, () -> { throw new AssertionError("stale update"); })).equals("Closed"))
        throw new AssertionError("scope admission");
      if (!task.isDone()) throw new AssertionError("task completion");
    });
    if (!clock.isClosed()) throw new AssertionError("clock cleanup");
    System.out.println("Java DX consumer passed");
  }
}
