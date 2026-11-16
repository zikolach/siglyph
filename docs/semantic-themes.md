# Semantic themes and keyboard conventions

[Documentation index](README.md)

Experimental/unreleased. `SemanticTheme` is an immutable value, not a global mutable service. Normal, muted, focused, selected, disabled, warning and error roles share a closed, cell-free SGR vocabulary.

## Try the form

```bash
mill fullscreenJvmDemo.run --form mono
mill fullscreenJvmDemo.run --form dark
mill fullscreenJvmDemo.run --form light
mill fullscreenJvmDemo.run --form contrast
mill fullscreenNativeDemo.nativeLink
./out/fullscreenNativeDemo/nativeLink.dest/out --form mono
```

Tab/Shift+Tab traverse name, level and table. Disabled explanatory text is not a focus target. Enter on the name or Ctrl+S validates and opens review; Enter confirms and Escape dismisses the overlay, restoring the previous field. Escape without a modal exits. Focus labels, selection markers and `!` validation text remain visible without color.

## API and precedence

- `Input(theme = palette)` and `DataTable(..., theme = palette)` opt in. Their default is `SemanticTheme.Plain`, preserving existing output; prior constructor descriptors are retained.
- `SelectListTheme.semantic(palette, focused = () => isFocused)` adapts existing hooks. The focus predicate is explicit because SelectList does not own a focused-state property. Existing `.copy(selectedText = custom)` hooks still win.
- `palette.withOverrides(Map(role -> style))` creates a local child value; parent/sibling values remain unchanged. Pass the resulting palette to an overlay component for local overrides.
- Explicit child SGR remains effective. A reset resumes the semantic default; styles close at the end of the value and do not change cursor/control geometry.
- State-role precedence is disabled, focused, selected, normal. Disabled is **styling**, not permission or input suppression; the application owns traversal and interaction rules.

Presets are plain, dark, light, monochrome and high-contrast. They use basic ANSI colors, never assume RGB support or infer OS preferences. Choose a palette explicitly; use mono/contrast for color-independent presentation and plain when no extra styling is desired. No screen-reader compliance or universal emulator claim is made.

The log explorer uses monochrome input/table semantics while retaining textual state/error labels. The reference form demonstrates application-owned Tab/Shift+Tab and Ctrl+S policy. Other key mappings remain configurable through existing KeybindingManager APIs or typed listeners; see [keybinding defaults](keybinding-defaults.md). A table's pageSize remains explicit rather than silently tied to terminal dimensions.

## Validation

Shared JVM/Native tests cover default output compatibility, old constructors, Unicode/narrow geometry, immutable nested overrides, disabled/selected precedence, custom list hooks, keyboard traversal and actual modal focus restoration. PTY smoke checks representative palettes and terminal restoration; it is not pixel-level verification. No new dependency or hosted matrix is introduced.
