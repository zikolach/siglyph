## 1. Implementation

- [x] 1.1 Add bounded shared frame assembly and one locked multi-write emission path for normal, alternate, append, and fullscreen policies.
- [x] 1.2 Preserve exact concatenated output, Unicode boundaries, counters, diagnostics, and lifecycle failure propagation with best-effort repair.
- [x] 1.3 Preserve virtual-terminal interpretation when a control spans physical writes.

## 2. Evidence

- [x] 2.1 Prove normal/fullscreen multi-chunk output regressions red against the merged base and all shared byte-output, framing, Unicode, concurrency, and injected-failure regressions green on JVM and Native.
- [x] 2.2 Benchmark fixed realistic text and typed-image frames on JVM and Native, including UTF-8 and sink syscalls; report allocation/RSS and timing caveats.
- [x] 2.3 Run focused and aggregate JVM/Native tests, packaging, PTY, format, lint, and strict OpenSpec validation; compare existing all-item failures with the untouched base.
- [x] 2.4 Document observable multi-write and failure limits.

All-item strict OpenSpec remains red on the same five pre-existing overlong-spec warnings as the untouched base; the change-specific strict gate is green. Publication and final-head CI are reported on the PR, not treated as an implementation checkbox.
