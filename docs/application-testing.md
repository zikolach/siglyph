# Application testing

[Documentation index](README.md) · [Roadmap #96](https://github.com/zikolach/siglyph/issues/96)

**Experimental and unreleased; not in 0.11.0.** `scalatui.testing.TUITestDriver` is a framework-independent shared-core helper over `VirtualTerminal`. It removes repeated start/flush/input/cleanup code from synchronous application tests on JVM and Scala Native. It is not an emulator or an asynchronous test runtime.

## Run a standalone consumer test

From a checkout, with Scala CLI and the [supported toolchain](getting-started.md#toolchain-compatibility):

```bash
scala-cli run examples/scala-cli/testing.scala --jvm 21
```

With a [Native toolchain](getting-started.md#scala-native):

```bash
scala-cli run examples/scala-cli/testing.scala --native --jvm 21
```

The [complete example](../examples/scala-cli/testing.scala) imports local `core/src`, needs no real TTY or test framework, and prints a success message after managed cleanup. Published dependency instructions will follow an actual release of these APIs; do not substitute 0.11.0 artifacts.

## Managed fixtures

Use `TUITestDriver.withSession(columns, rows, limits)(create)(body)`. The factory receives a VirtualTerminal and must return an **unstarted TUI built against that terminal**. Returning a TUI transfers cleanup ownership to the fixture, including on contract violations: never return a shared or already-running instance. The driver owns start and stop, including startup/body failures. A primary failure remains primary; an independent cleanup error is suppressed onto it. Stop failure is not silently swallowed.

Inside the body:

- `key(key, modifiers, eventType)` and `input(typedEvent)` deliver typed events, then flush available TUI work.
- `typeText(value)` sends one Character event per grapheme. Controls are rejected; use explicit keys or paste.
- `paste(value, chunkBytes)` sends paste start/chunks/end. A chunk size of one exercises UTF-8 fragments.
- `resize(columns, rows)` changes modeled geometry and flushes; `flush()` explicitly processes currently available work after direct application changes.
- `screen` captures immutable geometry and plain modeled rows. `assertScreen(ScreenSnapshot(...))` compares full rows, ignoring trailing ASCII blanks. `assertContains(text)` checks a literal row substring.
- `lastOutput` is a capped prefix of the last action's raw writes, for separate protocol assertions; check `outputTruncated` before treating it as complete. Raw output is not a screen snapshot.
- `cleanupOutput` and `cleanupOutputTruncated` retain a separate capped cleanup trace, so closing does not overwrite a failing action's output.
- `close()` explicitly restores the terminal; managed scope exit does this automatically. Later actions are rejected. Failed cleanup may be retried explicitly.

For example, a complete two-row expectation is `ScreenSnapshot(8, 2, Vector("界é", ""))`. Snapshots validate visible width and preserve Unicode text. Assertion failures include dimensions and up to six differing rows, with bounded escaped previews rather than an entire transcript.

## Ownership and limits

The driver is **single-owner-thread and non-reentrant**. Do not call its action methods from application callbacks or workers. Application callbacks may use ordinary TUI/component APIs. Direct TUI calls bypass driver budgets; flush explicitly before inspecting their rendered effects. Captured snapshots can be returned from the fixture as immutable values.

Default `TUITestLimits`:

| Limit | Default |
| --- | --- |
| Script steps | 10,000 |
| Input bytes per text/paste/event payload | 65,536 |
| Viewport cells | 1,000,000 |
| Dimension ceiling | 10,000 per axis |
| Last-action output | 4,096 UTF-16 units |

Each typed event, resize or explicit flush consumes one step. A text/paste batch checks its entire input and step budget **before delivering its first event**. Invalid geometry likewise fails before delivery. A failed admitted action rejects subsequent actions but still permits inspection/cleanup. Recorded failures from direct runtime/update-scope work are surfaced before a generic inactive-session error on the next action.

Write history is cleared between actions; only the capped last-action prefix remains. These are limits on the driver, **not a sandbox**: application callbacks can block or allocate arbitrary data, and one application call can produce more output before it returns. No timeout thread, forced interruption or hidden sleep is introduced.

## What this does not prove

- The helper uses `TUI.start`, not the blocking `TUI.run` loop. Escape/requestExit does not itself make this helper wait for run-loop completion; explicitly close or leave the fixture to assert restoration.
- `flush()` processes available synchronous work once; it is **not** a global idle barrier. Fixtures must own any external resources and must not assume background work has completed.
- Virtual time, scheduler advancement, pending-task assertions and late async-result scenarios await the #94 execution contract. The terminal parser's internal clock is not exposed as a public application scheduler.
- Modeled rows do not prove emulator fonts, image pixels, screen-reader accessibility or full protocol conformance. Keep PTY/real-terminal smoke tests separate.

## Verification

```bash
mill core.test.testOnly scalatui.testing.TUITestDriverSuite
mill coreNative.test.testOnly scalatui.testing.TUITestDriverSuite
```

The shared suite covers deterministic repeated scripts, grapheme typing, fragmented paste, resize/focus, preflight limits, bounded mismatch output, output retention, thread/reentrancy guards, runtime failures and cleanup failure propagation/retry. The log-explorer and generic-table journey tests now use this driver. Log filtering deliberately uses one-byte paste chunks; table Home/End and resize checks use managed snapshots. The log model still owns its cancellation policy, while the driver owns TUI teardown. Low-level tests retain direct VirtualTerminal control where they inspect backend/run semantics. Virtual-time/async scenarios remain deferred.
