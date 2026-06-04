## Why

The project now has a pure `EditorBuffer`, but applications still cannot render or interact with multiline text through a component. A rendered editor MVP and interactive demo will turn the buffer foundation into a visible, testable widget while keeping autocomplete, overlays, undo, and large-paste marker behavior for later changes.

## What Changes

- Add a rendered multiline `Editor` component in `core` that delegates text storage and mutation to `EditorBuffer`.
- Add configurable Enter behavior so prompt-like and editor-like applications can choose whether plain Enter submits or inserts a newline.
- Support MVP editor key handling for printable input, paste, Backspace/Delete, arrows, Home/End, Ctrl+A, Ctrl+E, Ctrl+K, Ctrl+W, Enter, Shift+Enter, and Cmd/Super+Enter when those events are normalized by the terminal parser.
- Add a pure editor layout helper that maps logical buffer lines and cursor positions to wrapped visual lines for rendering and tests.
- Add shared multiline editor demo construction used by JVM and Scala Native launchers.
- Update docs and Scaladoc for public editor APIs.
- Run Mill quality checks and existing JVM/Native validation.
- Do not add runtime dependencies.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `text-editing`: Add rendered multiline editor component behavior, configurable Enter semantics, and demo expectations.
- `component-rendering`: Add wrapped editor visual layout and fake cursor rendering requirements.
- `developer-api`: Add public editor API/docs expectations for `Editor`, editor options, and Enter behavior.

## Impact

- Affects `core` components and editing APIs.
- Adds tests for editor rendering, layout, input handling, callbacks, and Unicode/wrapping behavior.
- Updates shared interactive demo code and JVM/Native demo documentation.
- No new runtime dependencies; no Windows support; no autocomplete, overlay, undo/kill-ring, or large-paste marker implementation in this change.
