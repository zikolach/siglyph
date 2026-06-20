## Context

The renderer and ANSI utilities already handle Unicode width, ANSI escapes, overlays, and final width sanitization. Upstream `pi-tui` has targeted tests for edge cases that can corrupt terminal layout: tabs, wide CJK cells at overlay boundaries, shrinking to zero, and narrow render widths. This change hardens those behaviors without adding a new component feature.

## Goals / Non-Goals

**Goals:**

- Define tab display width as 3 unless existing project documentation proves another value.
- Make ANSI measuring, slicing, truncation, wrapping, and padding consistent for tabs and wide grapheme clusters.
- Keep overlay composition correct when overlays intersect wide cells.
- Add zero-width, one-column, and shrink regression tests.
- Keep changes internal unless public helper behavior must be documented.

**Non-Goals:**

- No new public renderer abstraction unless implementation proves it is required.
- No terminal cell-size image work.
- No Markdown-specific list wrapping changes.
- No change to the component width contract.

## Decisions

### 1. Match upstream tab width of 3

Use display width 3 for tabs across visible width, slicing, wrapping, and component output where tabs remain present. If existing project docs promise another tab width, update this design before implementation.

Alternatives considered:

- Treat tabs as width 8: rejected because upstream parity uses width 3.
- Treat tabs as width 1: rejected because it undercounts visual layout in terminal output.

### 2. Keep geometry hardening in shared utilities

Fix behavior in shared ANSI/Unicode and renderer composition paths so all components benefit. Component-specific work is limited to tests or local calls that currently bypass shared utilities.

Alternatives considered:

- Patch each component separately: rejected because duplicated geometry logic increases future drift.

### 3. Preserve public API by default

Do not expose new helpers unless a testable public need appears. If public helper behavior is clarified, add Scaladoc and docs under the developer API requirements.

Alternatives considered:

- Export every upstream utility name directly: rejected because it grows API surface before Scala use cases require it.

## Risks / Trade-offs

- Wide-cell clipping can create ambiguous visual output → tests must define replacement or clipping behavior for overlay boundaries.
- Changing tab width can alter existing snapshots → update snapshots only where they relied on undocumented behavior.
- Zero-width handling can hide component contract violations → keep direct component tests strict while runtime sanitization protects terminal sessions.
