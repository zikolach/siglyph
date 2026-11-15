# Repository demos and lifecycle

[Documentation index](README.md)

## Demos

The demos are the best starting point for real usage:

| Command | Source | What it shows |
| --- | --- | --- |
| `mill demo.run` | [`demo/src/scalatui/demo/MvpDemo.scala`](../demo/src/scalatui/demo/MvpDemo.scala) | non-interactive rendering through `StreamTerminal` |
| `mill asciinemaDemo.run agent-prompt` | [`asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala`](../asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala) | deterministic recording scenario for the agent prompt composer |
| `mill asciinemaDemo.run command-palette` | [`asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala`](../asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala) | deterministic recording scenario for command palette, loader, and settings behavior |
| `mill asciinemaDemo.run unicode-input` | [`asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala`](../asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala) | deterministic recording scenario for Unicode-safe editing and typed terminal input |
| `mill interactiveJvmDemo.run` | [`interactiveJvmDemo/src/scalatui/demo/InteractiveJvmDemo.scala`](../interactiveJvmDemo/src/scalatui/demo/InteractiveJvmDemo.scala) + [`interactiveDemo/src/scalatui/demo/InteractiveDemo.scala`](../interactiveDemo/src/scalatui/demo/InteractiveDemo.scala) | interactive JVM app, editor, autocomplete, rich SelectList/SettingsList behavior, file-manager mode, loaders, terminal integration helpers, resize-safe rendering |
| `mill interactiveNativeDemo.nativeLink && ./out/interactiveNativeDemo/nativeLink.dest/out` | [`interactiveNativeDemo/src/scalatui/demo/InteractiveNativeDemo.scala`](../interactiveNativeDemo/src/scalatui/demo/InteractiveNativeDemo.scala) | Scala Native launcher for the shared interactive demo |
| `mill fullscreenJvmDemo.run` | [`fullscreenDemo/src/scalatui/demo/FullscreenTranscriptDemo.scala`](../fullscreenDemo/src/scalatui/demo/FullscreenTranscriptDemo.scala) | shared growing transcript, follow-end, editor/footer layout, search, mouse gestures, selection, host copy, and safe shutdown |
| `mill fullscreenNativeDemo.nativeLink && ./out/fullscreenNativeDemo/nativeLink.dest/out` | [`fullscreenNativeDemo/src/scalatui/demo/FullscreenNativeDemo.scala`](../fullscreenNativeDemo/src/scalatui/demo/FullscreenNativeDemo.scala) | Scala Native launcher for the same fullscreen example |
| `mill keyTester.run` | [`keyTester/src/scalatui/demo/KeyTester.scala`](../keyTester/src/scalatui/demo/KeyTester.scala) | typed terminal key/input inspection |

For the Scala Native interactive demo, `nativeLink` builds the executable and the linked binary starts the app. Run both from an interactive terminal with `mill interactiveNativeDemo.nativeLink && ./out/interactiveNativeDemo/nativeLink.dest/out`. Optional flags go after the binary path, for example `mill interactiveNativeDemo.nativeLink && ./out/interactiveNativeDemo/nativeLink.dest/out --hardware-cursor`.

Asciinema recording scenarios are documented in [`docs/asciinema-demos.md`](../docs/asciinema-demos.md). Recording writes optional `.cast` publishing artifacts under `artifacts/asciinema`; normal build, test, formatting, lint, and OpenSpec validation commands do not require asciinema.

Interactive demo controls are also summarized in [`docs/interactive-smoke.md`](../docs/interactive-smoke.md). Default keybindings are listed in [`docs/keybinding-defaults.md`](../docs/keybinding-defaults.md).

TUI automatically requests Kitty keyboard negotiation on the JVM `stty` and Scala Native POSIX
backends. Startup continues without waiting for a reply. After a valid reply arrives within
100 ms, the backend enables key disambiguation. For example, `ESC[13;2u` reports Shift+Enter,
and ordinary Return reports Enter. Stream-only and unsupported backends keep basic input parsing.
If a terminal or multiplexer sends ordinary Return for Shift+Enter, Siglyph cannot recover Shift.
On shutdown, TUI cancels a pending query without popping a mode. After an enable attempt, the
backend restores the keyboard mode and retains failed cleanup for retry.

If backend stop fails, TUI keeps the current screen active, including the alternate screen in
width-only alternate and fullscreen modes. TUI rejects `start()` until restoration completes.
After output recovers, call `tui.stop()` explicitly to retry backend stop and then screen exit.
If only screen exit failed, TUI retries only screen exit. TUI preserves completed teardown and
callbacks across retries. Concurrent stops during an active attempt coalesce.
For example, after a failed keyboard pop, a successful later `tui.stop()` restores the keyboard
mode and exits the alternate screen without repeating completed callbacks.
`run()` reports the original startup or runtime failure with cleanup failures attached after
the first restoration attempt and its finite callback set. TUI never retries failed restoration
automatically. A successful explicit retry returns without rethrowing older attempt failures.
