## Context

siglyph currently delivers typed keyboard input, paste events, and raw protocol replies through `TerminalInput`. Interactive JVM and Native backends enable bracketed paste and restore it on stop. They do not enable terminal mouse reporting.

Rendering uses typed `ComponentRender` values that carry ordinary lines, semantic controls, and cursor placements. The runtime composites overlays after rendering base content. `ComponentFrameBuilder` can provide a component render origin, but the runtime does not retain a nested bounds tree after a frame is rendered.

Upstream `pi-tui` buffers mouse escape sequences but does not enable mouse reporting or expose typed mouse events. This change is a siglyph extension for coordinate-aware mouse wheel input and future pointer interactions.

## Goals / Non-Goals

**Goals:**
- Add opt-in xterm SGR mouse reporting for interactive JVM and Native backends.
- Add public typed mouse input through `TerminalInput.Mouse(...)`.
- Parse SGR mouse reports into zero-based terminal cell coordinates.
- Retain the latest rendered component bounds tree without changing the existing `render(width): ComponentRender` contract.
- Route mouse input by coordinates through visible overlays first and then through nested base components.
- Deliver mouse input only to components that explicitly opt into mouse handling.
- Add wheel scrolling to scrollable built-in components and autocomplete suggestion overlays.
- Keep keyboard focus routing unchanged for keyboard and paste input.
- Add tests and documentation for terminal mode lifecycle, parser behavior, routing, and component scroll behavior.

**Non-Goals:**
- Do not add runtime dependencies.
- Do not enable mouse reporting by default.
- Do not promise parity with upstream `pi-tui` for mouse input.
- Do not implement drag selection, hover tracking, or all-motion tracking in this change.
- Do not replace the existing typed component rendering contract.
- Do not require existing application components to handle mouse input.

## Decisions

### Decision 1: Mouse mode is opt-in through TUI options

Add mouse configuration to `TUIOptions`, with the default disabled. When enabled, interactive terminal backends write xterm normal mouse tracking and SGR extended-coordinate sequences on start, and write the matching disable sequences on stop.

The terminal protocol set is:
- Enable normal mouse tracking: `CSI ? 1000 h`
- Disable normal mouse tracking: `CSI ? 1000 l`
- Enable SGR mouse coordinates: `CSI ? 1006 h`
- Disable SGR mouse coordinates: `CSI ? 1006 l`

Rationale:
- Normal tracking reports button press, button release, and wheel events.
- SGR coordinates support larger terminal dimensions and explicit press/release markers.
- Opt-in preserves normal terminal text selection by default.

Alternatives considered:
- Always enable mouse reporting. Rejected because terminals capture mouse text selection while reporting is enabled.
- Enable all-motion tracking with `CSI ? 1003 h`. Rejected because scroll support does not require hover events and all-motion can produce high event volume.

### Decision 2: Public mouse input uses zero-based terminal cell coordinates

Add `TerminalInput.Mouse(...)` with these public values:
- Position: `row` and `col`, both zero-based terminal cell coordinates in the final frame.
- Action values: `Press(button)`, `Release(button)`, and `Wheel(direction)`.
- Button values: `Left`, `Middle`, `Right`, and `Other(code)`, where `code` contains only the SGR button identity bits rather than modifier, motion, or wheel flags.
- Wheel direction values: `Up`, `Down`, `Left`, and `Right`.
- Modifiers: reuse `KeyModifiers`; SGR mouse reports can set `shift`, `alt`, and `ctrl`; `superKey` remains false for parsed SGR mouse reports.

Rationale:
- Zero-based coordinates match Scala collection indexing and current render row accounting.
- Reusing `KeyModifiers` avoids a second modifier model.
- Keeping terminal coordinates in the typed event lets global input listeners observe raw terminal-relative mouse input before routing.

Alternatives considered:
- Use one-based terminal coordinates. Rejected because internal rendering and tests use zero-based row and column indexes.
- Store target-local coordinates in `TerminalInput.Mouse`. Rejected because parser output cannot know routing targets.

### Decision 3: Mouse delivery uses an explicit component mouse capability

Add an explicit mouse handling capability for components. The runtime routes `TerminalInput.Mouse` only to components that opt into this capability. Keyboard and paste input continue to use the existing focused-component route.

The mouse handling context contains:
- The target component bounds in terminal coordinates.
- The local row and column inside the target bounds.
- The original typed `TerminalInput.Mouse` event.

Rationale:
- Existing components inherit a default `handleInputResult` that reports handled input. Sending mouse input to every component would make old components accidentally consume mouse events.
- A mouse-specific capability lets built-in and application components opt in without changing existing keyboard behavior.

Alternatives considered:
- Deliver mouse events through `handleInputResult` for every hit component. Rejected because the default implementation cannot distinguish ignored mouse input.
- Add target-local coordinates directly to `TerminalInput.Mouse`. Rejected because global listeners and parser tests need terminal-relative events.

### Decision 4: Rendering records a retained bounds tree alongside frame lines

Add a rendered-frame data model that carries both typed `ComponentRender` output and component layout nodes. Keep `Component.render(width): ComponentRender` as the stable rendering method, and add a default layout-aware render path that wraps existing output as one leaf node.

The retained node records:
- Component reference.
- Bounds: row, column, width, and height.
- Child nodes in visual order.

`Container` and `ComponentFrameBuilder` become layout-aware so nested children produce nested bounds. Overlay rendering records one layout subtree per visible overlay after the overlay's final row, column, width, and clipped height are known.

Rationale:
- Application components continue rendering through the current typed contract without a second output representation.
- Built-in containers can produce nested hit-test data without asking every component to manually report bounds.
- Runtime stores only the latest frame layout, so input routing uses the same layout the user sees.

Alternatives considered:
- Change `Component.render` to return a layout object. Rejected because that breaks the public component contract.
- Infer bounds from final text only. Rejected because text lines do not identify which component produced each cell.

### Decision 5: Mouse routing uses overlay z-order, then deepest base hit

Mouse routing maps terminal mouse coordinates to the current TUI frame before hit testing. On mouse-enabled startup, the runtime queries the cursor position and records the visible frame start row. If the first frame scrolls the terminal viewport, the stored frame start row is adjusted by the number of rows scrolled. This preserves initial scrollback while keeping coordinate routing aligned with the visible frame.


Routing order for mouse input is:
1. Terminal protocol replies are consumed before user input routing.
2. Global input listeners receive the typed `TerminalInput.Mouse` before component routing.
3. If no listener handles the event, visible overlays are searched from topmost to bottommost visual order.
4. Inside a matching overlay, the deepest layout node containing the coordinate is tried first, then its ancestors.
5. If no overlay handles the event, the base layout tree is searched using the same deepest-first rule.
6. If no eligible component handles the event, the event is ignored and focus is unchanged.

Keyboard focus changes only when a mouse-capable component explicitly requests focus through the TUI context.

Rationale:
- The user sees overlays above base content, so hit testing follows the same order.
- Deepest-first routing lets nested scrollable components handle events before parent containers.
- Focus remains predictable for keyboard users and existing applications.

Alternatives considered:
- Route all mouse input to the focused component. Rejected because wheel scrolling must affect the component under the pointer.
- Route only to focus-capturing overlays. Rejected because non-capturing visual overlays can cover cells and need a chance to consume mouse input when their component opts in.

### Decision 6: Wheel scrolling reuses component-owned navigation state

Built-in scrollable components implement mouse wheel handling with their existing selection, cursor, and scroll state:
- `SelectList`: wheel up/down moves selection by one item and keeps selected row visible.
- `SettingsList`: wheel up/down moves selection by one row and keeps selected row visible.
- Autocomplete suggestion overlays: wheel up/down moves suggestion selection by one row and refreshes overlay placement.
- `Editor`: wheel up/down invokes the same page movement semantics as PageUp and PageDown.

Rationale:
- Scroll behavior stays inside the component that owns the state.
- Keyboard and mouse paths share movement semantics where practical.

Alternatives considered:
- Add a generic scroll offset to all components. Rejected because components do not share one visual viewport model.
- Make the runtime mutate component internals. Rejected because it would leak component state into the runtime.

## Risks / Trade-offs

- Mouse mode can interfere with terminal text selection and wheel scrollback → Keep disabled by default and document how to opt in.
- SGR mouse support is terminal-dependent → Parse SGR reports when present and leave unsupported terminals on existing keyboard behavior.
- Layout capture can diverge from final overlay composition if recorded before clipping → Record overlay bounds after clamping and clipping.
- Existing custom containers may not provide nested layout → Default layout treats them as leaf nodes until they opt into layout-aware rendering.
- Mouse wheel events can arrive before the first render → Ignore coordinate routing until a latest layout tree exists.
- Runtime callbacks can race with rendering → Update the retained layout in the same synchronized render path that updates previous frame lines.

## Migration Plan

1. Add public mouse input and layout data types in shared core.
2. Add parser and buffer coverage for complete SGR mouse sequences.
3. Add opt-in terminal mouse lifecycle support to JVM, Native, and virtual terminal backends.
4. Add layout-aware rendered-frame support while preserving `Component.render(width)`.
5. Add coordinate-aware mouse routing in `TUI` using the latest retained frame layout.
6. Add mouse wheel handling to built-in scrollable components.
7. Update docs and Scaladoc.

Rollback is straightforward before release: remove the opt-in mouse option, parser cases, layout-routing support, and docs. No data migration is required.
