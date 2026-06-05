## Why

Manual smoke testing showed that resizing to a narrow terminal can trigger over-wide rendered lines and crash an interactive backend thread. A terminal UI library should be a solid building block: resize and narrow-width rendering must not crash or leave terminal state broken, especially before porting richer `pi-tui` features such as overlays, autocomplete, and Markdown.

## What Changes

- Clamp runtime render dimensions to positive minimums so transient or invalid terminal dimensions do not produce zero-width rendering.
- Make `TUI` track both width and height changes and perform full redraws when either dimension changes.
- Sanitize final rendered lines before terminal writes so over-wide output is ANSI-safely truncated instead of throwing during normal rendering.
- Preserve the component width contract for tests and diagnostics while making the runtime robust against violations.
- Add optional/debug diagnostics for sanitized over-wide lines so component bugs remain discoverable without crashing users.
- Add live resize notifications for JVM and Scala Native interactive backends, using dependency-light platform mechanisms.
- Ensure resize/input rendering does not crash backend input or resize threads and restores terminal state predictably if an unrecoverable render failure occurs.
- Harden project interactive demo rendering for narrow widths.
- Add regression tests for narrow widths, width resize, height resize, and over-wide component output.
- Update docs and OpenSpec specs for the stronger runtime behavior.
- Do not add runtime dependencies.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `terminal-runtime`: Add positive dimension clamping, live resize notifications, height-aware redraws, robust render sanitization, and no-crash interactive rendering behavior.
- `component-rendering`: Clarify that components still SHALL obey width contracts while the TUI runtime SHALL sanitize final output to protect terminal sessions.
- `developer-api`: Document the public/runtime expectation that interactive TUI sessions remain robust under resize and narrow terminal conditions without new runtime dependencies.

## Impact

- Affects `core` TUI rendering and tests.
- Affects JVM and Scala Native terminal backends.
- Affects shared interactive demo rendering and smoke docs.
- May add internal/runtime configuration or diagnostics for sanitized render lines.
- No new runtime dependencies; no Windows support; no overlay/autocomplete implementation in this change.
