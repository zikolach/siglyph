## Inventory and policy
Input previously used only its built-in cursor styling; DataTable had textual selection/focus markers; SelectList already exposed per-part styling functions; overlays restore focus through the runtime rather than owning colors. Add explicit immutable theme values instead of a new ambient TUI service.

Roles: normal, muted, focused, selected, disabled, warning and error. State resolution gives disabled precedence over focused over selected over normal. Disabled is visual only; applications own focus traversal/interaction rules. Local withOverrides values override defaults and do not mutate parents or siblings. Existing SelectList custom function hooks can override the semantic adapter. Explicit child SGR survives; a child reset resumes the chosen semantic default. Plain adds no SGR and remains the default for library components.

Use a closed SGR vocabulary, never arbitrary control strings. Styles add no cells, so cursor/control metadata geometry stays unchanged. Presets use basic ANSI colors only, not inferred true-color support or OS preferences. Monochrome/high-contrast use underline/reverse/bold; reference applications retain visible focus labels, selection markers and explicit error/warning text. No screen-reader compatibility claim is made.

Input and DataTable preserve their previous JVM constructor descriptors through auxiliary constructors. The form demonstrates Tab/Shift+Tab traversal, validation, and Escape dismissal restoring underlying focus. Theme selection is explicit. Palette changes are immutable replacements, not runtime global mutation.

## Verification
Shared tests cover plain-output compatibility, Unicode/zero-width geometry, role precedence, nested overrides, custom list styles, keyboard navigation and overlay restoration. Run on JVM/Native and keep real-terminal smoke distinct from virtual assertions.
