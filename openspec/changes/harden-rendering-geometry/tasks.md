## 1. Geometry audit

- [ ] 1.1 Check existing docs and tests for any promised tab width before changing behavior.
- [ ] 1.2 Audit ANSI/Unicode width, slice, wrap, truncate, and padding helpers for tab and wide-cell handling.
- [ ] 1.3 Audit overlay composition code for wide-cell boundary handling.

## 2. Implementation

- [ ] 2.1 Apply tab width 3 consistently where no documented conflicting behavior exists.
- [ ] 2.2 Harden ANSI slicing/truncation to avoid partial wide grapheme output.
- [ ] 2.3 Harden overlay composition for overlays that intersect wide base cells.
- [ ] 2.4 Preserve direct component width-contract tests while keeping runtime sanitization as a final safety net.

## 3. Tests and docs

- [ ] 3.1 Add utility tests for tab width, wrapping, slicing, truncation, and ANSI styling.
- [ ] 3.2 Add overlay regression tests for CJK boundary cases.
- [ ] 3.3 Add shrink-to-zero, width 1, and narrow resize rendering tests.
- [ ] 3.4 Update Scaladoc or docs only if public helper behavior changes.
- [ ] 3.5 Run `mill core.test`, `mill __.compile`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
