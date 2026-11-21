# Verification

Local macOS validation of this unreleased, bounded streaming slice:

- `mill markdown.test` and `mill markdownNative.test` passed, including existing renderer/cache/public-consumer regressions and 14 new streaming tests.
- `mill fullscreenDemo.test.testOnly scalatui.demo.StreamingResponseViewerSuite` and `mill fullscreenDemoNative.test.testOnly scalatui.demo.StreamingResponseViewerSuite` passed the three shared viewer scenarios.
- `mill fullscreenJvmDemo.compile + fullscreenNativeDemo.compile` passed.
- `mill scalafmtCheck`, `mill scalafixCheck`, documentation checks, API inventory and strict OpenSpec validation passed.
- The final streaming tests include custom parser callbacks outside locks, explicit invalidation, complete inline/structural forms, split CRLF/surrogates, conservative ambiguous-suffix behavior, completion, bounds and counter evidence.

The parent review corrected provisional handling to operate on parsed paragraph/heading text instead of raw Markdown grammar, retained ordinary bracket text, and moved custom parser callbacks outside checkpoint locks. Existing complete-document rendering and parser contracts are unchanged.

## Expanded demo and selection follow-up

- The local fixture now includes four 12-row data tables and eight larger Scala/JSON fences, Unicode labels and long lines. It remains below the viewer's 64 Ki UTF-16 source bound with a maximum-length echoed prompt.
- All 10 viewer tests pass on JVM and Native. Tests split every fence delimiter, check rendered widths at 20/80/160 columns, compare final output to a cold baseline and establish more than three viewports of content at 80×24, 120×40 and 160×50.
- Initial fine-grained delivery repainted the larger document too often and exceeded the Native test timeout. Delivery now batches 256 UTF-16 units every 160 ms; no test timeout was increased. Fine chunk and split-delimiter semantics remain independently exercised.
- Quality and documentation checks passed after the fixture expansion. The separate plain-click selection fix also passed full JVM core and focused Native selection/mouse tests.

## Interaction performance follow-up

- Range rendering now hands only visible rows to viewport validation. In the counted regression, 20 scroll frames hand over 160 rows instead of 2,380 through the equivalent full-render fallback. Repeated drag frames likewise return only 160 rows and perform no additional parsing/highlighting.
- Append refresh retains stable highlights; explicit no-argument invalidation still refreshes mutable rendering state. Width changes invalidate selection mapping. Selection decorations visit visible rows and selected-text lookup locates its start row by binary search.
- Viewer selection-prefix guards now use the same normalized, newline-free offsets as the selection document and stop at the selected endpoint. A multirow regression protects against clearing or retaining the wrong range.
- JVM/Native Markdown, viewer and selection regressions, quality, documentation and inventory checks passed. Fixture size, delivery cadence and input timing were not reduced to obtain these improvements. These counters are not a wall-clock speedup or frame-rate claim.

## Active drag during streaming follow-up

Append invalidation previously discarded the gesture's initial range, so subsequent drag motion could not extend selection. ScrollView now retains the last complete bounded mapping across content refreshes and validates normalized text plus cell positions through the original range and both endpoints. Motion can refresh mapping before repaint; reversing direction keeps the original origin. Unsafe prefix changes cancel selection. Redundant viewer prefix scans were removed in favor of this core validation.

Typed-pointer/manual-scheduler regressions cover stable content at nonzero scroll offset, multiple appends before painting, reverse drags, unsafe-prefix cancellation and lifecycle resets. Full JVM core, focused Native selection, JVM/Native viewer suites, quality and documentation checks passed. The stream, fixture size and delivery cadence were not changed to hide this bug.

## Boundaries

No real-terminal visual smoke, public artifact publication or universal performance claim. Selection retention is limited to an unchanged rendered prefix; changed prefixes clear it. The viewer now refreshes ScrollView mappings without invalidating already-updated child output, preserving renderer LRUs and parser checkpoints. Changed-source layout and active-selection mapping still have document-wide costs. Lists/quotes/indented code conservatively prevent further checkpoint advancement in their suffix. Source copying, normalization, assembly and rendering still process retained document data. No full all-module test sweep was performed; the full JVM core suite was additionally run for the selection fix. No new external runtime dependency was added.
