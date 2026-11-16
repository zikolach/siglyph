## Why
The #93 prototype materializes every retained row and manually aligns columns. Continue roadmap #99 through the independent widget lane (#95), without blocking on the separate async-scope design.

## What Changes
- Add an experimental typed DataTable and immutable keyed TableData to shared core components.
- Use existing ViewportRangeRenderer to format only requested rows, with no offscreen cache or overscan.
- Provide column headers, bounded preferred widths, keyboard selection, stable identity across model replacement, and ordered selection callbacks.
- Integrate column rendering into the log explorer while preserving its application-owned source and selection policy.
- Add shared JVM/Native tests, a 100,000-row rendering-counter regression, and usage documentation.

## Capabilities
### New Capabilities
- `data-table`: typed keyed data, range rendering and keyboard selection.
### Modified Capabilities

## Impact
Shared core components and tests, the existing fullscreen demo and docs. No runtime dependency, build module or lifecycle API is added. This is a minimal first table slice, not a spreadsheet, sticky-header system, lazy remote data source or theme rewrite. APIs remain pre-1.0/experimental.
