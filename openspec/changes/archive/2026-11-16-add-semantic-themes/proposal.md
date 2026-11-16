## Why
#97 needs a small consistent visual/keyboard vocabulary, not a new styling framework. Preserve current rendering by default while exercising opt-in themes in real components and an accessible-by-keyboard form.

## What Changes
- Add immutable semantic roles and closed SGR styles with plain/dark/light/mono/high-contrast presets.
- Add opt-in Input/DataTable themes and a SelectListTheme adapter preserving custom hooks.
- Demonstrate local/nested overrides, validation markers and overlay focus restoration in a reference form; apply monochrome semantics to the log explorer.

## Capabilities
### New Capabilities
- `semantic-themes`: explicit semantic styling and reference keyboard conventions.
### Modified Capabilities

## Impact
Shared core styles/components, demo and tests. No global mutable theme, inferred OS preference, RGB dependency or screen-reader compliance claim. Plain defaults and existing constructor signatures remain supported.
