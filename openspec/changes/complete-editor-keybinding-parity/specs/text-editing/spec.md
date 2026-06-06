## ADDED Requirements

### Requirement: Editor prompt history navigation parity
The editor SHALL support upstream-compatible prompt history navigation for submitted prompts using Up and Down when the editor is empty or already browsing history at visual boundaries.

#### Scenario: Submitted prompt can be added to history
- **WHEN** application code records a submitted prompt in editor history
- **THEN** empty or whitespace-only prompts are ignored, consecutive duplicate prompts are ignored after trimming, and retained history is capped at 100 entries

#### Scenario: Up enters history from empty editor
- **WHEN** the editor is empty and receives the `tui.editor.cursorUp` command while history contains prompts
- **THEN** it loads the most recent prompt, moves the cursor to the end of that prompt, invokes change callbacks for the new text, and records an undo snapshot for the pre-history state

#### Scenario: Down returns from history to current prompt state
- **WHEN** the editor is browsing history and receives the `tui.editor.cursorDown` command at the last visual line until it moves past the newest entry
- **THEN** it returns to the current prompt state according to upstream behavior and resets the history index

#### Scenario: Edits leave history browsing mode
- **WHEN** the user mutates editor text after loading a history entry
- **THEN** subsequent editing commands operate on the loaded text and the editor no longer treats Up/Down as continuing the previous history browse unless history navigation is re-entered

### Requirement: Editor page navigation parity
The editor SHALL handle PageUp and PageDown through the configured `tui.editor.pageUp` and `tui.editor.pageDown` commands by moving the logical cursor by a visible page while respecting wrapped visual lines.

#### Scenario: PageUp moves cursor by visible page
- **WHEN** the editor receives the page-up command with multiple visual lines above the cursor
- **THEN** it moves the cursor upward by the editor page size and clamps to the first visual line if fewer lines are available

#### Scenario: PageDown moves cursor by visible page
- **WHEN** the editor receives the page-down command with multiple visual lines below the cursor
- **THEN** it moves the cursor downward by the editor page size and clamps to the last visual line if fewer lines are available

#### Scenario: Page movement preserves desired column where possible
- **WHEN** PageUp or PageDown moves between wrapped visual lines of different widths
- **THEN** the cursor lands on the closest valid display column according to existing editor layout rules

### Requirement: Editor jump-to-character parity
The editor SHALL implement upstream-compatible jump-to-character mode through `tui.editor.jumpForward` and `tui.editor.jumpBackward` commands.

#### Scenario: Jump forward waits for printable character
- **WHEN** the editor receives the jump-forward command
- **THEN** it enters jump mode and the next printable character moves the cursor to the first matching occurrence after the current cursor across following lines

#### Scenario: Jump backward waits for printable character
- **WHEN** the editor receives the jump-backward command
- **THEN** it enters jump mode and the next printable character moves the cursor to the first matching occurrence before the current cursor across preceding lines

#### Scenario: Repeating jump command cancels jump mode
- **WHEN** the editor is waiting for a jump character and receives either jump command again
- **THEN** it cancels jump mode without moving the cursor or mutating text

#### Scenario: Missing jump target leaves cursor unchanged
- **WHEN** the editor receives a printable jump character that does not occur in the requested direction
- **THEN** jump mode exits and the cursor remains at its previous position

### Requirement: Input and editor use configurable keybindings
The `Input` and `Editor` components SHALL dispatch editing, submit, newline, tab/autocomplete, undo, kill-ring, and movement commands through the configured keybinding manager while preserving current `pi-tui` defaults.

#### Scenario: Custom submit binding affects editor
- **WHEN** an application configures submit to `Ctrl+Enter` and plain Enter is not in the resolved submit binding
- **THEN** the editor submits on `Ctrl+Enter` and does not submit on plain Enter unless another configured command maps plain Enter to submit

#### Scenario: Default editing aliases still work
- **WHEN** the default keybinding manager is used
- **THEN** existing aliases for movement, deletion, undo, yank, and yank-pop continue to invoke the same editing behavior as before this change

#### Scenario: Unsupported bindings are documented
- **WHEN** an upstream default key combination cannot be represented reliably by the typed terminal input model or common terminal encodings
- **THEN** the implementation documents the deviation and preserves the closest available editing command behavior
