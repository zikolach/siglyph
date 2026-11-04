# Prevent self-interruption during terminal cleanup

## Why
Fullscreen JVM Ctrl+C can report InterruptedException even when terminal modes restore. Cleanup may run on a backend callback worker; stopping currently interrupts that same worker before the stty process wait.

## What Changes
- Stop other backend workers without interrupting the cleanup owner itself.
- Preserve existing external interrupt state and genuine failure/retry semantics.
- Cover input, periodic flush and resize callback cleanup with deterministic shared and real-PTY regressions.

## Capabilities
### New Capabilities
None.
### Modified Capabilities
- terminal-runtime: worker-owned cleanup does not introduce interruption.

## Impact
Shared stream input shutdown, JVM resize shutdown and matching Native workers; no public API or dependency changes.
