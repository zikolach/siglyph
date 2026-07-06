## Context

Siglyph core already exposes the primitive component model, containers, boxes, text rendering, typed input, keybindings, overlays, and render scheduling. Upstream Pi keeps some useful UI behavior outside `pi-tui` and inside the coding-agent application, including compact and expanded render states for tool rows, summaries, startup help, and custom message renderers.

The expandable behavior is not agent-specific by itself. It is a reusable terminal UI pattern: render a compact view by default, render detail on demand, and let an application apply one expansion state across several components. Placing this in core would enlarge the primitive layer. Placing it in an optional extras module preserves the small core while giving applications a ready-made pattern.

## Goals / Non-Goals

**Goals:**
- Add an optional `siglyph-extras` module that depends on `siglyph-core` only.
- Provide dependency-free expandable UI helpers in package `scalatui.extras`.
- Keep the helpers usable in JVM and Scala Native code where the shared source pattern supports it.
- Preserve the existing `Component.render(width): Vector[String]` contract.
- Add tests, Scaladoc, README or docs coverage, and release metadata updates for the new public artifact.

**Non-Goals:**
- Do not add agent-session, LLM, tool execution, extension runtime, model selection, or message-history APIs.
- Do not add runtime dependencies.
- Do not change core component, container, box, text, keybinding, or terminal contracts.
- Do not add animation, persistence, or application-global state management.
- Do not make expansion state implicit in `TUI`.

## Decisions

### 1. Create `siglyph-extras` as an optional module

The module should live outside `core` and depend only on `core`. This mirrors the existing optional-module direction used for Markdown and images while keeping dependency-light helper widgets available to applications that opt in.

Alternatives considered:
- Add helpers to `core`. Rejected because core should remain the primitive TUI layer.
- Put helpers in demos only. Rejected because the pattern is reusable and would be copied by applications.
- Create an agent module. Rejected because expandable panels are not agent-specific.

### 2. Start with expandable widgets only

The first scope should include:
- `Expandable`: an opt-in protocol with `setExpanded(Boolean)`.
- `ExpandableText`: a component that switches between collapsed and expanded text providers.
- `ExpandableSection`: a section component with title, collapsed body, expanded body, and optional hint text.
- `ExpansionController`: a small state holder that applies one expansion state to registered `Expandable` instances and returns whether the state changed.

This keeps the first module useful without turning it into a broad widget collection.

Alternatives considered:
- Add a generic panel framework. Rejected because current evidence only supports compact/detail expansion.
- Add keybinding ownership to the controller. Rejected because applications own their command model and can already use `KeybindingManager`.
- Add direct `TUI` integration to the controller. Rejected because it would couple a pure state helper to runtime rendering.

### 3. Keep rendering based on existing components

`ExpandableText` and `ExpandableSection` should compose existing `Text`, `Box`, `Spacer`, and `Container` behavior where useful. They must invalidate cached output when expansion state or content changes. They must return width-safe lines through the same ANSI-aware wrapping and truncation utilities already used by core components.

Alternatives considered:
- Implement a separate rendering pipeline. Rejected because it duplicates existing width and ANSI logic.
- Require caller-supplied child components for every state. Deferred because text and section helpers are enough for the first public contract.

### 4. Publish JVM and Native variants only if shared-source publishing is wired cleanly

The implementation should prefer shared sources and compile the helpers for JVM and Scala Native because the helpers depend only on shared core APIs. If publishing both variants requires release workflow updates, those updates must be included in the same change.

Alternatives considered:
- Publish only a JVM artifact. Rejected as the initial target because the proposed helpers have no platform dependency.
- Add a Native variant later. Rejected as unnecessary split work unless the build exposes a blocker during implementation.

## Risks / Trade-offs

- Public API too broad → Keep the first module limited to expandable text, expandable sections, and expansion coordination.
- `extras` becomes a junk drawer → Document module scope and reject agent-specific APIs from this module.
- Native publishing adds release workflow work → Include publishing metadata and release documentation tasks in this change.
- Controller lifecycle leaks component references → Keep controller ownership explicit and provide unregister or clear operations.
- Width bugs in composite widgets → Test direct rendering at narrow and normal widths, including ANSI-styled text.

## Migration Plan

1. Add the `extras` shared-source module and, if required, the Native mirror module in `build.mill`.
2. Implement `scalatui.extras` APIs with tests.
3. Update README/docs and publishing/release metadata for the new artifact.
4. Validate compile, tests, formatting, lint, and OpenSpec.
5. If Native publishing cannot be wired cleanly without release workflow churn, stop and revise the design before implementation continues.

## Open Questions

None.
