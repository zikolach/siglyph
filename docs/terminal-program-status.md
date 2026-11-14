# Negotiated terminal program status

Inspired by pi-tui `8636e211e7834e4ca46a22aec1d65dfd2eac8677` (`program-status.ts` and its tests). This is not a claim of emulator-wide verification.

`TerminalProgramStatusSupport` is an optional mixin, implemented by SttyTerminal and PosixTerminal. The base Terminal API and OSC 9;4 progress are unchanged. TUI starts a bounded OSC 7501 support query; only a positive echo arriving within 100 ms enables reports. Missing, malformed and expired replies do not enable output. Complete valid late replies are consumed internally even without the optional capability. No application callback runs on ingress, and no executor or timer is added.

Call `tui.setTerminalProgramStatus(ProgramStatus(ProgramStatusState.Working, app = Some("my-app"), message = "Building"))`. False means unsupported or lifecycle rejection; true means serialized output accepted, not display acknowledgement. Applications should use the TUI method rather than bypass its output boundary. Direct backend consumers own serialization and may explicitly request negotiation with a timeout bounded to 60 seconds.

States are Idle, Working, Blocked, Done, Error and Clear. Blocked reasons are Permission, Question and Auth. App identifiers must match `[A-Za-z0-9_.+-]{1,32}` or are omitted. Messages replace C0/C1 controls and line separators with spaces, replace unpaired surrogates, trim, and encode at most 2048 UTF-8 bytes in base64 without splitting code points. Root records only; no nested jobs or implicit queued reports.

Cleanup cancels negotiation and clears a possibly written report. A failed report or clear retains the cleanup obligation; explicit stop retries it before completing restoration. Restart requires a fresh handshake. Backend standalone stop also clears. Original lifecycle exceptions remain primary.

Herdr takes priority over leaked outer terminal variables and retains conservative true-color hints. Rex enables Kitty images. Orca requires exactly `ORCA_IMAGE_PROTOCOL=kitty` even when outer Kitty variables leak. Alacritty is recognized by TERM_PROGRAM, an alacritty TERM prefix, or ALACRITTY_WINDOW_ID. TMUX/screen conservatism and session hard-disabled overrides remain authoritative. No partial image clipping is introduced.
