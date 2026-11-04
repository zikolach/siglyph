# Design

## Root cause
StreamTerminal.stop interrupts both recorded worker references, including the calling input or flush worker. SttyTerminal then waits on a restoration subprocess with that interrupt flag, which can raise InterruptedException even though stty applies the saved state. Resize callback shutdown has the same ownership issue. The full demo outcome is scheduling-sensitive; shared and real-PTY callback regressions fail deterministically before the fix.

## Decision
Skip interruption only for Thread.currentThread when signaling worker shutdown. Still stop the generation and interrupt other workers. Do not clear pre-existing flags, swallow errors, retry subprocess commands, add locks, or change cleanup ordering.

## Verification
Require failing-before/passing-after shared input/flush tests and real PTY input/flush/resize callback tests. Repeat the real fullscreen JVM Ctrl+C process oracle and assert exit 0, terminal-mode restoration, cursor visibility, paste disable and alternate-screen exit. Run full JVM/Native tests, quality and strict OpenSpec validation before archiving.
