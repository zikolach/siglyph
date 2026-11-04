# Design

## Decisions
Use one internal InputTiming abstraction for monotonic nanoTime and monitor waits. Production delegates to System.nanoTime and Object.wait under the existing parser lock. StreamTerminal captures one timing instance per session; OrderedInputDelivery uses the same instance. Keep the public StreamTerminal constructor unchanged and the serialized parser/delivery ownership intact.

## Verification
Control time explicitly in split-CSI tests; exercise exact incomplete-sequence expiry and delivery after a wakeup. Run shared JVM/Native terminal suites and real PTY lifecycle tests. No production deadline increases and no test retries.

## Stack
Dependency upgrades -> timing -> backend protocol session -> viewport snapshots -> query state -> capabilities. Each implementation PR carries its own OpenSpec change.
