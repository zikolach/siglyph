# Experimental data table

[Documentation index](README.md) · [Roadmap issue #95](https://github.com/zikolach/siglyph/issues/95)

**Available since 0.12.0; APIs remain experimental.** `DataTable` is an experimental shared-core component for JVM and Scala Native. It combines typed column formatting, stable row selection and the existing viewport-range contract. Use a source checkout for these examples.

## Run the generic example

```bash
mill fullscreenJvmDemo.run --table
```

For Native:

```bash
mill fullscreenNativeDemo.nativeLink
./out/fullscreenNativeDemo/nativeLink.dest/out --table
```

The example builds 100,000 indexed rows. Up/Down, PageUp/PageDown (ten rows), Home and End navigate. Esc/Ctrl+C exits. The [complete shared example](../fullscreenDemo/src/scalatui/demo/DataTableDemo.scala) shows focus, parent scrolling, layout and lifecycle. Use `--log-explorer` for the separate bounded ingestion/filtering example.

## Construct typed data and columns

This is a construction excerpt; the linked runnable example supplies the TUI and terminal:

```scala
import scalatui.components.{DataTable, TableColumn, TableData}

case class Job(id: String, name: String, state: String)

val data = TableData(
  Vector(Job("build", "Compile", "done"), Job("test", "Tests", "running")),
  (job: Job) => job.id
)
var selectedJob = Option.empty[Job]
val table = DataTable(
  data,
  Vector(
    TableColumn[Job]("Name", 24, _.name),
    TableColumn[Job]("State", 12, _.state)
  ),
  selected => selectedJob = selected.map(_.row)
)
```

Selection callbacks update application/component state, not terminal output directly. The standalone example uses its callback to reveal the selected row in the owning ScrollView.

- `TableData` captures immutable rows and stable String IDs. Null/duplicate IDs are rejected before publication. ID extraction runs once per row during construction, not when navigating or painting.
- Row values and IDs must remain immutable. Publish replacement values with `table.setData(nextData)`; filtering and sorting belong to your data model.
- A nonempty initial model selects its first row without calling the change callback; read `table.selection` when initializing a detail pane.
- `table.selection` is an optional `TableSelection` containing ID, current index and row value. `selectId` returns false without changes for an unknown ID; `selectIndex` clamps to available rows.
- Replacement preserves selection by ID, even across reordering. If the ID disappears, the old index is clamped to the replacement. Empty data clears selection; filling an empty table selects its first row.
- Replacement publishes the current selected row through the callback even if its ID is unchanged, so detail panes can refresh. No-op navigation does not duplicate callbacks.
- `revision` changes with data, selection or focus. It is a component revision, not a hash of mutable application objects.

## Rendering and layout

Wrap the table in `ScrollView` inside a fullscreen layout. Document row zero is the **non-sticky header**; data row index `i` is document row `i + 1`. A plain `render(width)` call intentionally renders the complete document and is not the large-data path.

Columns have positive preferred widths. Width is allocated left-to-right after reserving two cells for selection/focus (`>` when focused, `*` when not). Separators cost one cell; columns beyond available space are not formatted. Unused width is padded. At most 64 columns are accepted. Text is clipped on grapheme boundaries through the existing ANSI-safe slicing path; supported styles/hyperlinks are closed before the next cell, and ordinary controls gain no output authority.

`renderRange` formats only requested rows/visible columns. There is **zero overscan and no offscreen render cache**. Geometry with non-positive width/count emits no rows and invokes no cell formatter. Negative starts clamp to zero and requests beyond the document return no rows. The empty table has only its header at positive width.

Model construction/index storage scales with total rows; application filtering and sorting are separate costs. Rendering output scales with requested rows and visible columns, while formatting/slicing costs also depend on cell text length and your callbacks. Fullscreen search/selection may request additional bounded ranges. A visible-row counter test is not a universal timing or memory benchmark.

## Focus, callbacks and scroll ownership

Call `tui.setFocus(table)` to enable unmodified arrow/page/Home/End navigation. Press/repeat events are accepted; releases and modified keys are ignored. `pageSize` is a positive configurable number (default ten), not an automatically measured viewport height.

Mutations use the shared component state/effect boundary. Formatters receive a consistent per-call snapshot and run outside the lock; keep them side-effect free. Selection callbacks are ordered effects and may reenter the component. Attached callbacks/request-render effects use the existing TUI coordinator, not a new worker or scheduler.

The parent still owns scrolling. Reveal a selection using its document row and the current ScrollView offset/viewport extent; [DataTableDemo](../fullscreenDemo/src/scalatui/demo/DataTableDemo.scala) implements this explicitly. After replacing table data, invalidate any owning ScrollView search document before relying on its cached search/selection mapping. The log explorer deliberately keeps its own eviction/filter/follow policy and supplies the corresponding selected ID to the table.

## Validation

```bash
mill core.test.testOnly scalatui.components.DataTableSuite
mill coreNative.test.testOnly scalatui.components.DataTableSuite
mill fullscreenDemo.test
mill fullscreenDemoNative.test
```

The table suite checks 100,000-row viewport formatting counts, identity capture, model replacement, bounds, Unicode/control safety, keyboard input and callback/formatter lock isolation. Demo tests cover indexed-data reuse during navigation, Home/End visibility, narrow resizing and existing log interactions. Local verification passed the full JVM core suite (1,002 tests), the 12 table tests on Native, and 15 shared demo tests on each platform. Native linking, formatting/lint, all OpenSpec validation and documentation checks passed. Both table and log-explorer launchers passed JVM/Native PTY smoke, including terminal restoration. A resize-at-End issue in the standalone demo was fixed with explicit follow-end intent and a regression. PTY output cannot prove emulator pixel appearance.

## Deliberately deferred

Sticky headers, built-in sort/filter controls, remote/lazy fetching, cell editing, column dragging, trees and public theme tokens are not part of this first slice. No async-scope or file-following API is introduced by the table work.
