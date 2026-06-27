## Context

Upstream `pi-tui` has added `Ctrl+J` as a default newline alias and has recent fixes around Markdown streaming code fences. The local parity audit still identifies small editor gaps around submit clearing undo state and fullwidth punctuation word boundaries. Larger layout work, such as word-boundary wrapping and sticky visual-column movement, remains separate because it changes editor layout behavior more broadly.

The current Scala runtime uses typed terminal input. Bare line feed can be parsed as plain Enter, so this design must not silently reinterpret every `\n` byte as Ctrl+J.

## Goals / Non-Goals

**Goals:**

- Add a safe upstream-aligned `Ctrl+J` newline default when the input is distinguishable as Ctrl+J.
- Preserve plain Enter semantics for terminals that emit bare line feed ambiguously.
- Clear editor undo state after submit.
- Add focused parity tests for fullwidth punctuation word boundaries.
- Add focused parity tests for Markdown streaming fenced-code stability.
- Update docs only where behavior or known terminal ambiguity changes.

**Non-Goals:**

- Do not implement editor word-boundary wrapping.
- Do not implement sticky visual-column movement.
- Do not add Markdown parser or highlighter dependencies.
- Do not change public APIs unless implementation proves a public documentation clarification is needed.
- Do not add runtime dependencies.

## Decisions

### Treat Ctrl+J as a typed-key parity change, not a raw-byte shortcut

Use a typed `KeyDescriptor(TerminalKey.Character("j"), KeyModifiers(ctrl = true))` for `tui.input.newLine`. If the parser can distinguish CSI-u `Ctrl+J`, it should match this binding. Bare `\n` remains plain Enter unless a terminal path provides reliable Ctrl+J identity.

Alternative considered: map bare `\n` to `Ctrl+J`. That would risk changing Enter behavior on terminals that emit LF for Return, so it is rejected.

### Clear undo at the submit boundary

After a successful editor submit, clear pending undo snapshots so `Ctrl+-` does not restore the submitted draft. This matches upstream prompt behavior and avoids confusing post-submit state restoration.

Alternative considered: keep undo history because submit does not mutate visible text. That preserves local behavior but leaves a known parity gap.

### Add tests before expanding behavior scope

Fullwidth punctuation and Markdown streaming code fences should receive focused tests. If current behavior already matches upstream, the task should remain test-only. If a test exposes a real gap, implementation should be limited to the affected boundary logic.

Alternative considered: include word-boundary wrapping and sticky visual-column behavior in the same change. That is rejected because it expands scope into editor layout model changes.

## Risks / Trade-offs

- Bare line-feed ambiguity can make user expectations differ by terminal. Mitigation: document the exact behavior and test the reliable typed encoding path.
- Clearing undo on submit may surprise callers relying on undo after submit. Mitigation: submit is an application boundary, and upstream parity treats it as clearing draft undo state.
- Markdown streaming code-fence fixes may reveal parser edge cases beyond partial closing fences. Mitigation: keep this change focused on fenced-code stability and defer broader Markdown parser changes.
- Fullwidth punctuation behavior may differ across Unicode versions. Mitigation: add explicit fixtures for the upstream-reviewed cases and avoid broad Unicode segmentation rewrites.
