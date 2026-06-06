## Context

The current Scala implementation has typed terminal input, undo-only history, kill-ring/yank support, word navigation/deletion, large paste markers, and autocomplete overlays. Upstream `pi-tui` centralizes default bindings in `keybindings.ts` and routes editor input through a keybinding manager. Remaining parity gaps include configurable command matching, prompt history navigation, PageUp/PageDown, jump-to-character mode, and precise autocomplete precedence.

This change must preserve Scala/JVM/Native portability: command matching belongs in shared core, while parser support should normalize only terminal encodings that can be represented by the current typed input model.

## Goals / Non-Goals

**Goals:**
- Add shared keybinding definitions and a manager API modeled on current `pi-tui` defaults.
- Complete editor/input command handling for remaining upstream defaults: PageUp/PageDown, Ctrl+] and Ctrl+Alt+] jump mode, history navigation, delete/move/yank/undo aliases, selection/autocomplete commands, and submit/newline behavior.
- Keep autocomplete overlays higher priority than ordinary editor commands where upstream does.
- Document and test terminal/parser limitations for bindings that are not portable in all terminals.
- Preserve public API compatibility and avoid runtime dependencies.

**Non-Goals:**
- Adding redo or behavior not present in current upstream `pi-tui`.
- Adding global application hotkey routing beyond editor/input/select command matching.
- Guaranteeing every physical keyboard can emit every upstream combination through every terminal emulator.
- Replacing typed `TerminalInput` with raw string matching.

## Decisions

1. **Model keybindings as typed command identifiers plus typed key descriptors.**
   - Rationale: Scala should not expose raw terminal escape strings as the primary API. Key descriptors can map to `TerminalInput.Key` values and modifiers while preserving upstream command names semantically.
   - Alternative considered: string-compatible `KeyId` only. That mirrors TypeScript more closely but weakens type safety and parser integration.

2. **Provide upstream default bindings as data.**
   - Rationale: defaults should be inspectable, overridable, and covered by tests. This also makes future upstream parity updates easier to diff.
   - Alternative considered: keep defaults hard-coded in `Input`/`Editor`. That already caused scattered parity gaps.

3. **Let user bindings replace default keys for a command.**
   - Rationale: upstream `KeybindingsManager` treats an explicitly provided binding as the resolved set for that command, while `undefined`/absence means defaults. Scala should follow that unless documented otherwise.
   - Alternative considered: merge user keys with defaults. This is convenient but not upstream-compatible.

4. **Record conflicts without making manager construction fail.**
   - Rationale: upstream reports user key conflicts but still resolves bindings. Scala can expose conflict data for diagnostics without breaking applications at startup.
   - Alternative considered: reject conflicts eagerly. That is safer but stricter than `pi-tui`.

5. **Route autocomplete/select commands before normal editor commands while suggestions are visible.**
   - Rationale: upstream lets Escape/Ctrl+C cancel, Up/Down navigate, Tab accept, and Enter accept or submit slash-command completions before falling back to normal editor behavior.

6. **Store editor history in the component/model that owns prompt submission.**
   - Rationale: upstream history is editor-owned, trims submitted text, ignores empty/consecutive duplicates, caps at 100 entries, and resets browsing on edits/submission.
   - Alternative considered: put history in the TUI runtime. That would mix application-level prompt semantics into terminal infrastructure.

## Risks / Trade-offs

- [Risk] Typed input parser cannot distinguish some upstream raw encodings everywhere. → Mitigation: add best-effort normalization tests for known sequences and document unsupported/ambiguous bindings.
- [Risk] Configurable keybindings complicate existing component tests. → Mitigation: keep defaults identical to current behavior where already implemented and add focused tests for custom overrides.
- [Risk] History navigation may surprise applications that use Up/Down only for cursor movement. → Mitigation: follow upstream conditions: use history when empty or browsing history at visual boundaries; otherwise preserve cursor movement.
- [Risk] Jump mode introduces transient editor state. → Mitigation: make it explicit, cancel on repeated jump key/control input, and cover no-match behavior.

## Migration Plan

- Add keybinding data types and default manager in shared core.
- Update `Input`, `Editor`, and autocomplete/select components to use the manager while keeping existing default behavior.
- Add parser/key model coverage for PageUp/PageDown and jump-key combinations where possible.
- Add docs and smoke-test notes listing default bindings and known terminal deviations.
- Existing applications continue using defaults unless they opt into custom bindings.

## Open Questions

- Should Scala expose `pi-tui` command ids verbatim as stable strings, Scala enum values, or both?
- Should keybinding manager state be global, per-`TUI`, or per-component? The implementation should prefer explicit per-runtime/per-component configuration while preserving ergonomic defaults.
