## Context

`scala-tui` currently provides foundational rendering/runtime APIs, ANSI and Unicode utilities, basic components (`Text`, `Box`, `Spacer`, `Input`, `SelectList`), overlays, autocomplete, and a multiline editor. Upstream `pi-tui` also includes small utility widgets that are useful in real applications: `TruncatedText`, `Loader`, `CancellableLoader`, and `SettingsList`.

This change intentionally ports `TruncatedText` and `SettingsList` first. Both fit the existing component model and can be implemented as deterministic render/input components with no new runtime dependencies. Loader components are deferred because animation introduces timer lifecycle, render scheduling, cancellation semantics, and Scala Native compatibility questions that deserve a separate design.

## Goals / Non-Goals

**Goals:**

- Add a `TruncatedText` component matching `pi-tui`'s visible behavior: first-line-only text, ANSI-aware truncation, padding, and exact width-safe output.
- Add a `SettingsList` component for rendering and editing application settings using typed terminal input.
- Support settings rows with id, label, current value, optional description, and optional cycle values.
- Support keyboard navigation, Enter/Space activation, Escape cancellation, callbacks, scrolling by `maxVisible`, and width-safe rendering.
- Optionally support simple search/filter if it remains dependency-free and testable; otherwise document it as a follow-up.
- Keep all APIs in shared `core` and compatible with JVM and Scala Native.
- Explore and plan the follow-up `add-loader-components` change after implementation decisions are grounded.

**Non-Goals:**

- Do not implement animated `Loader` or `CancellableLoader` in this change.
- Do not add new runtime dependencies or fuzzy-search dependencies.
- Do not implement complex settings submenus unless they can be built from existing components without expanding scope.
- Do not introduce a global component scheduler or timer abstraction in this change.
- Do not change existing `Text`, `Input`, `SelectList`, editor, overlay, or terminal APIs except for small reusable helpers if needed.

## Decisions

### 1. Implement `TruncatedText` as a dedicated component, not a `Text` mode

`Text` wraps content, while `TruncatedText` deliberately renders only the first logical line and truncates it to fit. Keeping a separate class mirrors `pi-tui`, preserves simple public semantics, and avoids complicating `Text` with a mode flag.

Alternatives considered:

- Add `wrap = false` or `truncate = true` to `Text`. This reduces classes but blurs distinct behavior and makes `Text` harder to reason about.
- Use helper functions only. Applications would have to repeat padding and exact-width logic.

### 2. Implement `SettingsList` as a typed stateful component

`SettingsList` should own selected index, scroll offset, optional filter query, and optional active submenu state. It receives typed `TerminalInput` and reports `InputResult` through the standard component contract where useful.

Alternatives considered:

- Reuse `SelectList` directly. Current `SelectList` lacks setting values, descriptions, activation/cycling, search, and submenu behavior.
- Implement only static settings rendering. This would miss the most useful `pi-tui` interactions.

### 3. Start with prefix/case-insensitive search if search is included

Upstream uses `fuzzyFilter`. To avoid dependencies and keep scope focused, search should use a deterministic dependency-free matcher such as case-insensitive containment or prefix matching. A stronger fuzzy matcher can be a later utility if needed.

Alternatives considered:

- Port fuzzy matching now. This adds behavior complexity not central to settings-list parity.
- Skip search entirely. This is simpler and acceptable if value cycling/descriptions ship first, but search is useful and can be lightweight.

### 4. Defer loader components to `add-loader-components`

Loader animation introduces component-owned time. A faithful port needs answers for tick scheduling, JVM/Native lifecycle, cancellation tokens, and render-request coalescing. This change will finish with an exploration/planning task for loader components instead of implementing them prematurely.

Alternatives considered:

- Include `Loader` now with a JVM `Timer`. This risks Native incompatibility and creates lifecycle obligations before the API is designed.
- Implement manual `tick()` loader now. This is safer but less like `pi-tui` and still deserves a focused proposal.

## Risks / Trade-offs

- [Risk] `SettingsList` scope can expand into search, fuzzy matching, and submenus. → Mitigation: prioritize value cycling, descriptions, cancellation, and width-safe rendering; keep search simple or defer.
- [Risk] Settings values and labels can overflow at narrow widths. → Mitigation: use existing ANSI-visible-width utilities and add narrow-width tests.
- [Risk] Search input may duplicate existing `Input` behavior or create focus ambiguity. → Mitigation: treat search as internal settings-list state and delegate only simple printable input, or defer search if complexity grows.
- [Risk] Public API shape may be too theme-heavy for the current dependency-light library. → Mitigation: start with simple string style functions/options and document them with Scaladoc.
- [Risk] Loader follow-up can be forgotten. → Mitigation: include a final explicit task to summarize loader design choices and propose/prepare `add-loader-components`.

## Migration Plan

1. Add public `TruncatedText` and tests.
2. Add settings-list public models/options and tests for rendering.
3. Add typed input handling for navigation, cycling, cancellation, and optional filtering.
4. Update docs and porting notes.
5. Explore and capture loader-component follow-up scope.
6. Validate formatting, compile, tests, Native link, and OpenSpec.

Existing components remain source-compatible. No data migration is required.

## Open Questions

- Should first-pass `SettingsList` include search, or should search be a follow-up after value cycling and descriptions land?
- Should settings submenus be included in v1, or deferred until overlays/modals can provide richer nested UI?
- Should setting item values be plain strings only, or should a typed value model be introduced later?
- Should the loader follow-up use a manual tick API first, or introduce a cross-platform scheduler abstraction?
