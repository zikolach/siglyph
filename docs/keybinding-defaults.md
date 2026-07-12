# Keybinding defaults and parser notes

This table summarizes the default keybinding commands used by the editor/input/key selection stack and their Scala-typed defaults.

| Command ID | Scope | Default typed key(s) |
| --- | --- | --- |
| `tui.editor.cursorUp` | Editor | `Up` |
| `tui.editor.cursorDown` | Editor | `Down` |
| `tui.editor.cursorLeft` | Editor | `Left`, `Ctrl+B` |
| `tui.editor.cursorRight` | Editor | `Right`, `Ctrl+F` |
| `tui.editor.cursorWordLeft` | Editor | `Alt+Left`, `Ctrl+Left`, `Alt+B` |
| `tui.editor.cursorWordRight` | Editor | `Alt+Right`, `Ctrl+Right`, `Alt+F` |
| `tui.editor.cursorLineStart` | Editor | `Home`, `Ctrl+A` |
| `tui.editor.cursorLineEnd` | Editor | `End`, `Ctrl+E` |
| `tui.editor.jumpForward` | Editor | `Ctrl+]` |
| `tui.editor.jumpBackward` | Editor | `Ctrl+Alt+]` |
| `tui.editor.pageUp` | Editor | `PageUp` |
| `tui.editor.pageDown` | Editor | `PageDown` |
| `tui.editor.deleteCharBackward` | Editor | `Backspace` |
| `tui.editor.deleteCharForward` | Editor | `Delete`, `Ctrl+D` |
| `tui.editor.deleteWordBackward` | Editor | `Ctrl+W`, `Alt+Backspace` |
| `tui.editor.deleteWordForward` | Editor | `Alt+D`, `Alt+Delete` |
| `tui.editor.deleteToLineStart` | Editor | `Ctrl+U` |
| `tui.editor.deleteToLineEnd` | Editor | `Ctrl+K` |
| `tui.editor.yank` | Editor | `Ctrl+Y` |
| `tui.editor.yankPop` | Editor | `Alt+Y` |
| `tui.editor.undo` | Editor | `Ctrl+-` |
| `tui.input.newLine` | Input | `Shift+Enter`, `Ctrl+J` when reported as typed Ctrl+J |
| `tui.input.submit` | Input | `Enter` |
| `tui.input.tab` | Input | `Tab` |
| `tui.input.copy` | Input | `Ctrl+C` |
| `tui.select.up` | Select | `Up` |
| `tui.select.down` | Select | `Down` |
| `tui.select.pageUp` | Select | `PageUp` |
| `tui.select.pageDown` | Select | `PageDown` |
| `tui.select.confirm` | Select | `Enter` |
| `tui.select.cancel` | Select | `Escape`, `Ctrl+C` |

## Parser limitations / closest supported behavior

- Standard and supported modified Insert-key sequences parse to `TerminalKey.Insert`. Insert is not bound to a default command, but applications can use it in custom keybindings without matching `TerminalKey.Unknown("insert")`.
- The parser exposes `TerminalKey.Enter` and can mark modifiers for printable control-like sequences from known terminal escape forms. `Ctrl+J` matches `InputNewLine` when the terminal reports a distinguishable typed Ctrl+J event, such as CSI-u `ESC[106;5u`. A bare line-feed byte (`\n`) remains plain Enter because many terminals use it for Return.
- `Esc` and `Ctrl+C` both map to cancel selection/autocomplete by command model; by default, `TUI` exits on `Ctrl+C` before component handling unless `handlesControlC` is disabled for that runtime.
- Additional raw terminal encodings and IME variants arrive as bounded `RawStart`, `RawChunk`, and `RawEnd` streams; these are not part of the command model and are intentionally not promoted into stable public API commands.
