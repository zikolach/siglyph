## 1. TruncatedText Component

- [x] 1.1 Add public `TruncatedText` component in core with Scaladoc covering first-line behavior, padding, ANSI-aware truncation, and width contract.
- [x] 1.2 Implement rendering that takes only the first logical line, applies horizontal/vertical padding, truncates by visible width, and returns width-safe lines.
- [x] 1.3 Add unit tests for long text truncation, ANSI styling, Unicode width, newline handling, padding, exact/narrow widths, and cache invalidation if used.

## 2. SettingsList Public API

- [x] 2.1 Add public `SettingItem`, `SettingsListOptions`, and any theme/style model needed for labels, values, descriptions, cursor, hints, callbacks, and optional filtering.
- [x] 2.2 Add public `SettingsList` component with Scaladoc covering rendering, input behavior, callbacks, dependency constraints, and non-goals.
- [x] 2.3 Keep APIs backend-independent and dependency-free for JVM and Scala Native core.

## 3. SettingsList Rendering

- [x] 3.1 Render setting labels and current values in width-safe rows with a visible selected cursor/style.
- [x] 3.2 Render empty state and no-match state as width-safe hint lines.
- [x] 3.3 Render selected item descriptions using ANSI-aware wrapping or truncation within the requested width.
- [x] 3.4 Render bounded visible slices with scroll indicators when item count exceeds `maxVisible`.
- [x] 3.5 Add rendering tests for normal rows, long labels/values, descriptions, empty/no-match states, scrolling, ANSI text, Unicode text, and widths including 1.

## 4. SettingsList Input Behavior

- [x] 4.1 Handle Up/Down typed key input to move selection and keep it visible.
- [x] 4.2 Handle Enter and Space to cycle selected item values and invoke change callbacks with setting id and new value.
- [x] 4.3 Handle Escape to invoke cancel callback without changing values.
- [x] 4.4 Implement optional dependency-free filtering if scope remains simple; otherwise document filtering as deferred and adjust specs before continuing.
- [x] 4.5 Add input tests for navigation, scroll adjustment, value cycling, callback invocation, cancellation, filtering if included, and ignored inputs.

## 5. Docs and Demo Touchpoints

- [x] 5.1 Update README component documentation for `TruncatedText` and `SettingsList` usage and controls.
- [x] 5.2 Update porting notes to record `pi-tui` parity and intentional deviations such as simple filtering or deferred submenus.
- [x] 5.3 Update smoke/demo docs if the shared demo is extended to showcase the new widgets; otherwise document why no demo change is needed.

## 6. Loader Follow-up Exploration

- [x] 6.1 Re-read upstream `Loader` and `CancellableLoader` behavior and compare it with current Scala `TUIContext` and JVM/Native constraints.
- [x] 6.2 Add a concise follow-up note for `add-loader-components` covering timer lifecycle, cancellation token/API options, render scheduling, and Scala Native compatibility questions.
- [x] 6.3 At completion, recommend whether to propose `add-loader-components` next and summarize the suggested scope.

## 7. Demo Showcase

- [x] 7.1 Add `TruncatedText` and `SettingsList` examples to the runnable non-interactive demo.
- [x] 7.2 Update docs to mention that utility components are now shown by `mill demo.run`.

## 8. Validation

- [x] 8.1 Run `mill scalafmtAll` if formatting changes require it.
- [x] 8.2 Run `mill scalafmtCheck`.
- [x] 8.3 Run `mill scalafixCheck`.
- [x] 8.4 Run `mill quality`.
- [x] 8.5 Run `mill __.compile`.
- [x] 8.6 Run `mill core.test`.
- [x] 8.7 Run `mill interactiveDemo.test` if demo or shared component behavior affects the interactive demo.
- [x] 8.8 Run `mill interactiveJvmDemo.compile` and `mill interactiveNativeDemo.nativeLink`.
- [x] 8.9 Run `openspec validate --all --strict`.
