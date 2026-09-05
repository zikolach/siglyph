# Performance benchmarks

Performance workloads are separate from ordinary tests. `mill __.test`, `mill __.compile`, `mill scalafmtCheck`, and `mill scalafixCheck` do not run them.

## JVM report

Run the bounded quick report:

```bash
mill benchmarkJvm.run -- --quick --warmup 2 --samples 5
```

Run the standard report:

```bash
mill benchmarkJvm.run
```

Each scenario reports its exact workload metadata, median wall time after warmup, deterministic runtime counters, and a numeric checksum. The JVM runner reports median current-thread allocated bytes when `com.sun.management.ThreadMXBean` supports and permits allocation tracking. It prints `unsupported` on a JDK without that API. The project does not add JMH, a profiler agent, or another allocation dependency, so allocation counts exclude work on other threads and cannot identify allocation sites.

The fixed scenarios cover large transcript layout, append-only output, a differential tail change, Unicode wrapping and width reflow, overlays, nested scrolling, search indexing, selection mapping, and image-heavy typed frames.

## Controlled comparison

Timing and allocation ratios are opt-in. First save a report from the same controlled host and JDK as a Java properties file, then pass it back with `--compare`:

```bash
mill benchmarkJvm.run -- --quick --warmup 2 --samples 5 > /tmp/siglyph-benchmark.properties
mill benchmarkJvm.run -- --quick --warmup 2 --samples 5 --compare /tmp/siglyph-benchmark.properties
```

Only comparison mode emits `comparison.wallRatio` and `comparison.allocationRatio`. Ordinary tests assert checked-in counters and have no wall-time or machine-speed threshold.

## Scala Native smoke

Run the representative dependency-free Native smoke:

```bash
mill benchmarkNative.run -- --warmup 1 --samples 3
```

The Native target runs the fixed quick workload, reports exact metadata and median wall time, and checks deterministic counters between samples. Scala Native does not expose a portable standard-library API equivalent to JVM thread allocation accounting. The report therefore prints `allocation=unsupported`. The target does not add a profiler or benchmark dependency. Native wall times are informational and are not compared with a checked-in machine-speed threshold.

## Checked-in counter baseline

`PerformanceCounterBaselineSuite` records the reviewed quick-workload algorithmic baseline. It checks visible-row painting, same-frame render reuse, bounded search scanning, image encoding, and terminal writes on JVM and Scala Native. Runtime counters contain numbers only. They retain no rendered rows, image payloads, or application text.
