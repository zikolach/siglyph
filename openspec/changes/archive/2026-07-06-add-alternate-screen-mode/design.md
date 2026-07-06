## Context

Siglyph currently runs in the normal terminal screen. First render writes the frame without clearing scrollback, resize redraws use pi-tui-style full clear output, and shutdown moves the cursor below the rendered content before returning control to the shell. This matches the transcript-oriented behavior needed by chat-like and agent-like applications.

Upstream `pi-tui` does not provide a built-in alternate-screen runtime mode. Its resize path clears the viewport and scrollback with `ESC[2J ESC[H ESC[3J` on width and most height changes. Pi application code only mentions alternate screen around external editors: it stops the TUI, launches the external editor, restarts the TUI, and forces a full render because the external editor may have used alternate screen.

Alternate screen is a different capability from resize redraw. Resize redraw prevents stale cells after terminal geometry changes. Alternate screen lets an application temporarily own a separate terminal buffer and then return to the previous normal-screen content.

The first change should add whole-TUI alternate-screen mode. Temporary full-screen modal sessions and scrollable height-aware editor APIs are related but larger follow-up work.

## Goals / Non-Goals

**Goals:**

- Add an opt-in runtime mode that enters alternate screen on `TUI.start()` and exits alternate screen during `TUI.stop()`.
- Keep normal-screen mode as the default for existing code.
- Keep existing component rendering APIs unchanged.
- Keep normal-screen resize redraw behavior unchanged.
- Define terminal cleanup order for normal exits and failures.
- Make behavior testable with `VirtualTerminal` raw escape assertions.
- Document the relation between alternate screen, scrollback preservation, and future full-screen editor work.

**Non-Goals:**

- Do not make alternate screen the default.
- Do not use alternate screen as the resize redraw strategy in normal-screen mode.
- Do not add temporary alternate-screen modal sessions in this change.
- Do not add a full-screen editor or height-aware component API in this change.
- Do not add runtime dependencies.
- Do not change `Component.render(width): Vector[String]`.

## Decisions

### 1. Add a typed screen-mode option to `TUIOptions`

Add a public screen-mode setting with `Normal` as the default and `Alternate` as the opt-in value. The exact naming can be `TUIScreenMode.Normal` and `TUIScreenMode.Alternate`, or a similar Scala-idiomatic enum.

Rationale:

- The behavior belongs to the TUI lifecycle, not to components.
- A typed enum is clearer than a Boolean when future modes or modal APIs are possible.
- The default preserves source and behavior compatibility for existing applications.

Alternatives considered:

- Boolean option such as `alternateScreen: Boolean`. Rejected because it is less descriptive and makes future expansion harder.
- Backend-specific methods only. Rejected because alternate-screen mode is a shared runtime policy and does not require platform-specific APIs.
- Component-level control. Rejected because components do not own terminal lifecycle or cleanup.

### 2. Let `TUI` own alternate-screen enter and exit sequences

The TUI runtime should write `ESC[?1049h` when starting in alternate-screen mode and `ESC[?1049l` when stopping after alternate-screen mode was entered.

Rationale:

- `TUI` already owns start, stop, render, cursor visibility, and recovery from runtime failures.
- The existing `Terminal.write` path can emit backend-independent ANSI sequences.
- Avoiding a new terminal trait method keeps backend contracts stable.
- `VirtualTerminal` can assert exact sequence ordering without real terminal behavior.

Alternatives considered:

- Add `enterAlternateScreen()` and `exitAlternateScreen()` to `Terminal`. Rejected for the first change because it widens the backend interface for a standard escape sequence and forces all terminal implementations to grow methods.
- Add optional `TerminalAlternateScreenSupport`. Rejected for the first change because support detection is not required: applications opt in and unsupported terminals will receive standard VT-compatible sequences like other terminal protocol output.

### 3. Enter alternate screen before the first TUI-owned render

Startup order should be:

1. Start the terminal backend.
2. Enter alternate screen when configured.
3. Enable any TUI-owned terminal protocols or queries already performed by `TUI`.
4. Hide the cursor.
5. Render the first frame.

The first render in alternate-screen mode should force a clean alternate buffer before drawing. A clear-screen and cursor-home sequence is sufficient for the first change. Clearing normal scrollback is not the purpose of alternate screen.

Rationale:

- Entering alternate screen before drawing prevents normal scrollback pollution.
- A clean alternate buffer avoids leftover alternate-buffer cells from prior terminal state.
- This keeps normal-screen first render unchanged.

Alternatives considered:

- Enter alternate screen inside terminal backends before bracketed paste. Rejected because it makes behavior backend-owned and harder to keep identical for JVM, Native, stream, and virtual terminals.
- Draw first, then enter alternate screen. Rejected because it pollutes normal scrollback and makes the option fail its main purpose.

### 4. Exit alternate screen late enough to restore the normal screen cleanly

Shutdown order should restore modes before returning to the shell. The TUI should not move the cursor below rendered content when alternate-screen mode is active, because exiting alternate screen restores the previous normal-screen buffer and cursor context.

A safe order is:

1. Restore autowrap if a render failed while autowrap was disabled.
2. Show the cursor.
3. Disable TUI-owned terminal notifications if enabled.
4. Drain pending input when supported.
5. Exit alternate screen if it was entered by this `TUI` lifecycle.
6. Stop the terminal backend, which disables backend-owned protocols and restores raw mode.

Implementation can adjust steps 3-5 if tests prove a better ordering, but it must guarantee that alternate screen is exited and cursor visibility is restored on normal exit and runtime failure.

Rationale:

- The normal-screen cursor placement behavior does not apply to an alternate buffer that disappears on exit.
- Exiting alternate screen before backend stop returns the visible screen before raw-mode restoration completes.
- Tracking whether the TUI entered alternate screen prevents duplicate exit sequences after failed startup.

Alternatives considered:

- Keep the current cursor-below-content behavior in alternate-screen mode. Rejected because it writes into the alternate buffer immediately before discarding it and can cause unnecessary cursor movement artifacts.
- Exit alternate screen only in the backend. Rejected because the backend does not know which `TUIOptions` were active.

### 5. Keep normal-screen resize behavior unchanged

Normal-screen resize redraws must keep using synchronized output, autowrap disabled, `ESC[2J ESC[H ESC[3J`, and a recomputed frame. Tests that prove no alternate-screen sequences in normal mode should remain.

Alternate-screen mode resize redraws should remain full redraws on width and height changes. They should clear the active alternate-screen viewport and home the cursor, but they should not emit `ESC[3J`. Preserving normal scrollback is the main reason to use alternate screen, so alternate-screen resize should avoid scrollback-clear output.

Rationale:

- Normal-screen resize behavior was recently aligned with pi-tui and fixes real stale-cell artifacts.
- Alternate-screen mode has a different scrollback contract.
- Avoiding `ESC[3J` in alternate-screen mode reduces the risk of clearing normal shell scrollback in terminals with different buffer semantics.

Alternatives considered:

- Always use current resize clear sequence in both modes. Possible, but it risks erasing scrollback in terminals that apply `CSI 3 J` outside the alternate buffer.
- Never full-redraw on resize in alternate-screen mode. Rejected because wrapping and viewport alignment problems still exist inside alternate screen.

### 6. Keep temporary full-screen modal sessions out of this change

A later change can add an API that lets a normal-screen TUI temporarily switch to alternate screen for a full-screen editor and then return to the original TUI frame. That API needs separate decisions about nested lifecycle, input routing, height-aware rendering, state restoration, and failure recovery.

Rationale:

- Whole-TUI alternate-screen mode is small and testable.
- Temporary modal alternate-screen sessions are more complex because the normal-screen TUI must pause and resume without losing state.
- Full-screen editors also need height-aware rendering and scroll state, which are not part of the current component contract.

Alternatives considered:

- Implement modal sessions immediately. Rejected because it expands scope and may force premature height-aware component API decisions.
- Avoid alternate-screen support until a full-screen editor exists. Rejected because whole-TUI alternate-screen mode is independently useful and establishes the lifecycle foundation.

## Risks / Trade-offs

- Alternate-screen exit is skipped after startup failure → Track whether enter was emitted and exit only when needed during cleanup.
- Cursor stays hidden after failure → Keep cursor restoration in the existing `stop()` cleanup path and add failure-path tests.
- Autowrap remains disabled after failed render → Reuse existing autowrap restoration and include alternate-screen failure coverage.
- Normal-screen behavior regresses → Keep default-mode tests that assert no `ESC[?1049h` or `ESC[?1049l`.
- Resize in alternate screen clears normal scrollback on some terminals → Prefer a no-`CSI 3 J` resize clear for alternate mode unless testing proves otherwise.
- Unsupported terminals ignore alternate-screen sequences → Treat this like other terminal protocol output; unsupported behavior is a terminal limitation and not a runtime dependency concern.
- Users expect full-screen editor support immediately → Document that this change enables whole-TUI alternate screen only; modal full-screen sessions and height-aware editor APIs remain future work.

## Migration Plan

No migration is required for existing applications. Normal-screen mode remains the default.

Applications that want full-screen ownership opt in through `TUIOptions`. If problems occur, they can remove the option and return to normal-screen behavior without changing component code.

## Open Questions

- Should documentation include a small alternate-screen demo command in this change, or is Scaladoc plus README/API documentation enough?
- Should the JVM Java/Kotlin interop API expose alternate-screen mode now, or should it remain Scala-first until a later interop-focused change?
