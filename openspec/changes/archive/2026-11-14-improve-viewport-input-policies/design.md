## Context

Priority four of the pinned pi-tui review identifies list paging and optional input policies. Existing focused-first TUI routing, Editor paging, bounded selection documents and nested wheel remainders are already correct.

## Goals / Non-Goals

Preserve defaults and source compatibility; provide deterministic opt-in behavior on JVM and Native. Keep Editor and focused-first key routing unchanged; do not add dependencies, background timers, process probes or implicit OS heuristics.

## Decisions

1. ListPaging resolves existing default select commands. Each list applies page movement within its existing state/effect boundary. Empty and clamped page input is consumed, preventing transcript fallthrough. Page size is maxVisible (minimum one), consistent with Editor autocomplete.
2. WheelScrollController retains only last time, direction and average gap. Explicit monotonic milliseconds permit deterministic tests. Gaps below 5 ms reset acceleration; gaps above 200 ms, reversal or backwards time start a new gesture. Caps are validated. Terminal-accelerated sources explicitly bypass scaling.
3. Normalize original wheel events once through TUI.wheelScrollPolicy before the runtime remainder loop. Use the existing injected monotonic mouse clock, sampled outside locks. Changing policy or starting a session resets gesture history. Custom dispatch may still use WheelScrollController or ScrollView.handleWheelAt; semantic handleMouseEvent receives authoritative lineDelta and never accelerates an ancestor remainder again. Default runtime behavior stays one row / Alt-five rows.
4. WordSelectionPolicy supplies bounded additional complete grapheme joiners to existing word range logic. Both double-click and drag use the policy. Builders and retained-document bounds are unchanged.

## Risks / Trade-offs

Hosts must opt in on the TUI or at custom original-event dispatch and mark already-accelerated sources correctly. No automatic macOS inference is possible without expanding scope. Page size denotes configured items rather than physical decoration rows. Existing constructor calls compile; binary compatibility is not promised. The existing word-class algorithm is preserved rather than importing Intl segmentation.

## Validation

VirtualTerminal focused list vs transcript paging; filtered/empty bounds; deterministic wheel burst, pause, direction, backwards time and disablement; nested remainder/containment; grapheme/path selection and bounds. Run focused shared suites on JVM and Native and strict OpenSpec validation.
