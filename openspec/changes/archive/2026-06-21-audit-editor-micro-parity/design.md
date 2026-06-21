## Context

The editor has broad parity for multiline editing, history, autocomplete, Unicode layout, large paste, kill ring, and hardware cursor markers. Upstream `pi-tui` still has fine-grained editor tests that can expose small behavior drift. This change is an audit-first pass: it should produce evidence and targeted tests, not speculative behavior changes.

## Goals / Non-Goals

**Goals:**

- Compare upstream editor-related tests to current Scala behavior and tests.
- Classify every reviewed behavior as already covered, missing test only, behavior gap, or intentional deviation.
- Add tests only when current behavior already matches upstream.
- Create follow-up OpenSpec work for behavior gaps.
- Update porting notes with intentional deviations found during the audit.

**Non-Goals:**

- No editor behavior changes except adding tests for already-matching behavior.
- No public API changes.
- No keybinding redesign.
- No terminal backend changes unless the audit only documents them for follow-up.

## Decisions

### 1. Treat the audit as the deliverable

The main output is a checked audit matrix with evidence from upstream tests and local Scala tests. Implementation fixes are deferred to follow-up changes unless they are test-only additions for behavior that already exists.

Alternatives considered:

- Bundle audit and fixes together: rejected because it risks broad, hard-to-review behavior changes.

### 2. Use explicit classification values

Every reviewed behavior must use exactly one status: already covered, missing test only, behavior gap, or intentional deviation. This prevents ambiguous audit notes.

Alternatives considered:

- Use free-form notes only: rejected because it makes follow-up planning harder.

### 3. Preserve existing contracts during audit

The audit may identify issues, but it must not alter editor APIs, key models, or runtime behavior. Any needed behavior change becomes a separate proposal.

Alternatives considered:

- Fix simple behavior gaps opportunistically: rejected because the user requested specs and careful decisions before implementation.

## Risks / Trade-offs

- Audit scope can expand across terminal input, editor layout, and autocomplete → limit reviewed files to editor-related upstream tests and document excluded areas.
- Upstream tests can depend on TypeScript internals → map them to user-visible Scala behavior rather than private implementation details.
- Missing local test helpers can make parity hard to check → create small test helpers only if they do not change product code.
