# Live log explorer

[Documentation index](README.md) · [Step mode](log-explorer.md) · [Scope-owned timers](scoped-timers.md)

**Unreleased; run from a source checkout.** Live mode automatically generates the same deterministic records as the discovery prototype. It is not file or network ingestion. For actual files, use [file-follow mode](file-log-following.md).

## Run

Use a real macOS/Linux terminal with the [contributor toolchains](../CONTRIBUTING.md#development-setup):

```bash
mill fullscreenJvmDemo.run --live-log-explorer
```

For Scala Native:

```bash
mill fullscreenNativeDemo.nativeLink
./out/fullscreenNativeDemo/nativeLink.dest/out --live-log-explorer
```

The existing `--log-explorer` flag remains step-driven; `--table` and the default transcript demo are unchanged.

## Behavior

- Starts with 40 generated records, then adds **20 per 250 ms fixed-delay tick**, retaining at most 200 by default. The header identifies live/paused/cancelled state and evictions.
- Type a filter and press Enter. Up/Down/PageUp/PageDown browse; Ctrl+G resumes following the newest matching row. Scrolling away from the tail disables following; automatic batches preserve the top retained row across eviction instead of snapping the viewport back.
- **Ctrl+P** pauses/resumes generation. Paused ticks consume no IDs or new table index; the timer remains registered.
- If update admission fills up, the recurring timer stops rather than silently retrying; the header reports `overloaded` on subsequent rendering.
- **Ctrl+X** permanently stops the source and cancels its timer immediately, while retained rows remain inspectable. It cannot be resumed in the same demo instance. The application still owns its idle executor until exit; a scope does not shut down a borrowed executor.
- **Ctrl+N** still requests a manual batch; it obeys pause/cancellation too.
- **Esc/Ctrl+C** exits and restores the terminal.

The same bounded retention and filter limits documented for [step mode](log-explorer.md#limits-and-behavior) apply. Batch insertion preserves selected row identity until eviction. Rendering and model projection costs remain distinct; no throughput/FPS guarantee is claimed.

## Ownership and cleanup

The application explicitly creates one ScheduledThreadPoolExecutor and borrows it through ExecutorTaskScheduler. An ApplicationScope owns the recurring registration; updates and table refresh run through the serialized TUI boundary. No ad hoc producer thread mutates the component tree directly.

Source installation and cancellation are coordinated without holding an application monitor while calling providers. Early Ctrl+X/Escape prevents later installation; pending publication is disarmed and completed by its owner. The source instance is single-use.

On normal exit, startup/setup/runtime failure, cleanup attempts source cancellation, terminal restoration, adapter closure and executor shutdown/join independently. It preserves the original error and suppresses independent cleanup failures. An explicit final TUI stop can complete retryable restoration without repeating successful backend cleanup. Unexpected adapter failures wake the run loop and are checked after worker cleanup as well.

The remaining application-owned wiring is concrete: choose the provider/batch policy, handle pause/cancel, close/join the borrowed executor, and report errors. Scoped APIs supply bounded admission, serialization, timer cancellation and recurrence. File following, EOF/truncation/rotation policies and I/O-error UX remain the next #93 slice—not functionality implied by generated streaming.

## Validation

```bash
mill fullscreenDemo.test
mill fullscreenDemoNative.test
```

Manual-clock tests cover exact batch counts, retention/eviction, selection and index reuse, filtering, pause/resume, immediate and startup-racing cancellation, registration/startup failures and cleanup-error precedence. Real launcher PTY checks exercise autonomous growth and terminal-mode restoration separately; they are not pixel-level emulator inspection or timing-accuracy tests.
