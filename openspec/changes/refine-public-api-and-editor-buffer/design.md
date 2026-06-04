## Context

The project now has a real interactive runtime, typed terminal input, focus routing, and JVM/Native interactive demos. The next richer widgets—multiline editor, autocomplete, overlays, Markdown preview, and eventually image/status components—will stress the current simple `Component.handleInput(input): Unit` model and the lack of a pure multiline text model.

The existing `Input` component proves single-line editing and Unicode-aware grapheme helpers, but multiline editing needs a reusable buffer that can be tested independently from rendering, terminal quirks, and backend differences.

## Goals / Non-Goals

**Goals:**

- Introduce a pure `EditorBuffer` foundation for multiline text editing.
- Keep buffer operations platform-independent and dependency-free.
- Make cursor movement and mutation grapheme-aware enough for current Unicode utilities.
- Refine component input handling only as much as needed to support richer widgets without forcing overlays/autocomplete into this change.
- Establish follow-up sequencing for rendered editor UI, autocomplete/overlays, terminal polish, Markdown, and broader API cleanup.

**Non-Goals:**

- No full rendered multiline `Editor` component in this change.
- No autocomplete provider or suggestion UI implementation.
- No overlay composition implementation.
- No Markdown parser selection or rendering work.
- No new runtime dependencies.
- No Windows support.

## Decisions

### Decision: Add pure buffer before rendered editor

Add an `EditorBuffer` model first, then build the rendered `Editor` component in a follow-up.

Rationale: most hard editing behavior—cursor positions, grapheme deletion, line split/merge, paste insertion, and submit extraction—can be tested without a terminal. This keeps failures small and avoids coupling terminal rendering bugs to text mutation bugs.

Alternative considered: implement the visual `Editor` directly. That would produce a more visible demo sooner, but it would mix buffer correctness, wrapping, cursor rendering, submit behavior, and terminal redraw concerns in one change.

### Decision: Represent cursor in logical buffer coordinates

Use logical coordinates such as `(lineIndex, columnCluster)` rather than terminal row/column coordinates.

Rationale: terminal coordinates depend on wrapping, viewport height, ANSI rendering, and wide characters. The buffer should know text structure, not display layout. A future `Editor` renderer can map logical cursor positions to visual positions.

Alternative considered: store byte offsets or UTF-16 offsets. Those are compact but error-prone for grapheme-aware editing and less Scala-idiomatic for the existing Unicode utilities.

### Decision: Keep input-result API small and conservative

If `Component.handleInput` is refined, prefer a minimal result/command shape that can express handled/ignored, render request, and exit request. Defer overlay/focus command richness until overlay/autocomplete work creates concrete pressure.

Rationale: a large command algebra too early may become wrong once overlays and autocomplete are designed. The current runtime already supports focus and exit imperatively, so the safest move is to reduce ambiguity without over-modeling.

Alternative considered: introduce a comprehensive UI command model (`Focus`, `ShowOverlay`, `HideOverlay`, `RequestExit`, `Batch`, etc.). This is attractive, but it is better suited to the overlay/autocomplete change.

### Decision: Keep large paste markers out of the initial buffer slice unless cheap

The current text-editing spec includes large paste marker behavior. This change may define the extension point, but full large-paste marker substitution can remain for the rendered editor follow-up if it would expand scope.

Rationale: normal paste insertion is needed now; compact paste markers are mostly useful once a visible editor renders large pasted content.

## Risks / Trade-offs

- **Risk: API churn before widgets use it** → Keep public API refinements minimal and driven by editor-buffer needs.
- **Risk: Unicode correctness gaps** → Add focused tests for CJK, combining marks, emoji, and multi-codepoint grapheme deletion.
- **Risk: Buffer API duplicates future editor API** → Treat `EditorBuffer` as the model; rendered `Editor` delegates to it rather than reimplementing text mutation.
- **Risk: Scope creep into autocomplete/overlays** → Capture those as explicit follow-ups rather than implementing them here.

## Follow-up Chain

1. **Rendered multiline editor demo**: Build `Editor` component on top of `EditorBuffer`, including visual wrapping, cursor rendering, submit/newline behavior, and JVM/Native demo coverage.
2. **Overlay and autocomplete components**: Add overlay composition, focus capture policies, autocomplete providers, and selectable suggestion UI using the editor as the concrete anchor.
3. **Terminal runtime polish**: Add resize signal handling, PTY/manual smoke coverage, and lifecycle documentation for interactive backends.
4. **Markdown module**: Choose a parser strategy and implement JVM/Native-compatible Markdown rendering as a separate module.
5. **Public API stabilization pass**: Revisit naming, command/result algebra, focus APIs, and package organization after editor and overlay pressure is real.
