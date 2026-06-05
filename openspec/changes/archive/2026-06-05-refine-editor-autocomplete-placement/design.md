## Context

The previous overlay/autocomplete change added a generic overlay stack and made editor suggestions render through that stack. After manual smoke testing, the overlay renderer was corrected to avoid clearing scrollback, avoid full-window height padding when overlays are absent, and avoid scroll jumps when overlays appear.

One workaround remains: the shared interactive demo computes the editor's terminal row during `DemoRoot.render` and calls `editor.setAutocompleteOverlayOptions(...)` so slash suggestions appear near the editor. This makes the demo look correct, but it pushes editor placement responsibility into application layout code. In `pi-tui`, editor autocomplete is attached to the editor render output itself: the select list is appended below the editor area, so applications do not manually compute terminal rows.

This change refines placement while preserving the Scala overlay architecture. The goal is not to exactly copy `pi-tui`'s inline rendering implementation; it is to match the user-visible placement contract and remove application-side row arithmetic.

## Goals / Non-Goals

**Goals:**

- Make editor autocomplete suggestions appear adjacent to the rendered editor area by default.
- Remove manual autocomplete row calculation from the shared interactive demo.
- Keep suggestions overlay-backed so focus routing, z-order, clipping, and future overlay features remain reusable.
- Track editor height changes from wrapping, multiline input, and resize.
- Preserve existing application override hooks for custom autocomplete overlay placement.
- Keep JVM and Scala Native behavior identical and dependency-free.

**Non-Goals:**

- Do not implement a full retained layout tree for all components.
- Do not add hardware cursor or IME candidate-window positioning.
- Do not implement cursor-column-relative popovers for arbitrary inline spans.
- Do not change autocomplete provider async/cancellation contracts.
- Do not rework generic overlay positioning semantics beyond the minimum needed for editor-adjacent placement.
- Do not add runtime dependencies.

## Decisions

### 1. Use an editor-owned placement mode instead of a global layout tree

Add an editor-level placement mode that computes autocomplete overlay options from the editor's most recent render context. The editor already knows its visual line count after `EditorLayout.fromBuffer`; with a small render-origin hint, it can place suggestions directly after its rendered area without the parent application computing rows manually.

Alternatives considered:

- Full TUI layout tree with child regions. This is more general, but it is a larger architectural change than autocomplete placement needs.
- Inline suggestions appended to `Editor.render`, exactly like `pi-tui`. This would match upstream implementation more directly, but would bypass the overlay stack we intentionally added for focus and future UI.
- Cursor-relative anchoring. This is useful later, but `pi-tui` editor autocomplete is attached below the editor area rather than strictly at the cursor column.

### 2. Preserve explicit override options

Applications that need custom placement should still be able to supply or update explicit `OverlayOptions`. Built-in editor-adjacent placement should be the default path, not the only path.

Alternatives considered:

- Remove `setAutocompleteOverlayOptions`. This would simplify the API but remove a useful escape hatch for demos and applications.
- Always override custom options from editor render. This would surprise users who intentionally set fixed terminal placement.

### 3. Keep overlay output height minimal

The overlay compositor should continue to extend output only to the deepest required overlay row. Editor-adjacent placement must not reintroduce scroll jumps, full-window blank output, or startup scrollback clearing.

Alternatives considered:

- Pad to terminal height for all overlay frames. This supports screen-relative placement but caused visible scroll side effects and was already corrected.
- Render autocomplete inline. This avoids overlay height issues but gives up overlay focus behavior.

### 4. Treat pi-tui as a placement behavior reference, not an implementation mandate

The visible contract to preserve is that editor suggestions are near/under the editor and not at terminal-global bottom. The Scala implementation may use overlay handles and placement options as long as tests document the intentional architecture difference.

## Risks / Trade-offs

- [Risk] Without a full layout tree, editor-owned placement may only work when the parent tells the editor its render origin. → Mitigation: provide a small, explicit render-origin/placement API and keep manual override available.
- [Risk] Updating overlay options during render can cause re-render loops if it always schedules renders. → Mitigation: make render-time placement updates idempotent and avoid scheduling extra renders when only computed placement is refreshed for the current frame.
- [Risk] Placement can drift if the parent render order changes after the editor computes options. → Mitigation: test the shared demo and document that editor-adjacent placement requires the parent to use the placement helper or render-context API consistently.
- [Risk] Very narrow or short terminals can still clamp suggestions away from the editor. → Mitigation: rely on existing overlay bounds clamping and add narrow/resize tests.

## Migration Plan

1. Add editor placement mode/API with Scaladoc.
2. Teach the editor to compute default autocomplete overlay options from its rendered height and supplied render origin.
3. Update the shared demo to remove manual row arithmetic and use the editor placement API.
4. Add tests for default placement, custom override preservation, resize/wrap tracking, and no scroll-height regression.
5. Update README/docs/porting notes as needed.

The change is additive/refining. Existing applications that call `setAutocompleteOverlayOptions` should continue to work.

## Open Questions

- Should the placement API be named around `renderOrigin`, `placement`, or `layoutContext`?
- Should default editor-adjacent placement place suggestions below the full editor component or below the cursor visual line when future cursor-relative placement is added?
- Should a future change generalize this into component-region reporting for all child components?
