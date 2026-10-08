## Why

Large typed-image frames currently assemble one large character buffer, copy it into a frame string, and ask the terminal backend to encode that complete string again. Actual renderer-path measurements show substantial transient allocation on the JVM and high Native resident memory for a 5.59 MB frame.

## What Changes

- Assemble synchronized normal, alternate, append, and fullscreen frames in bounded character chunks without changing concatenated terminal bytes.
- Hold the existing terminal write lock across every chunk of a frame, while reporting each attempted terminal write and each completed write diagnostic coherently.
- On a mid-frame write failure, attempt to terminate a partial string control, synchronized output, and disabled autowrap before propagating the original failure through normal lifecycle cleanup. A broken sink cannot be guaranteed repairable.
- Teach the virtual terminal to model escape/string controls fragmented across writes.
- Add shared JVM/Native exact-output, Unicode, concurrency, and failure regressions and compare fixed prebuilt workloads on both runtimes.

## Capabilities

### Modified Capabilities

- `terminal-runtime`: Bounded, serialized frame writes and best-effort mid-frame failure cleanup.

## Impact

- Shared renderer policy and virtual terminal test backend; no public API or new runtime dependency.
- Custom `Terminal` implementations can receive several bounded writes for one large frame. Write counters and diagnostic events reflect physical chunk writes.
