package scalatui.benchmark

import scalatui.syntax.Equality.*

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path}
import java.util.Properties

/**
 * Opt-in JVM timing and current-thread allocation report.
 *
 * The runner covers fixed transcript, redraw, Unicode, overlay, scrolling, search, selection, and
 * image workloads after explicit warmup. Results are machine-dependent and are not correctness
 * gates. Allocation is reported only when the running JDK supports thread allocation counters.
 */
object JvmBenchmarkMain:
  private final case class Config(
      scale: BenchmarkWorkloads.Scale,
      scaleName: String,
      warmup: Int,
      samples: Int,
      comparison: Option[Path]
  )

  private final case class Sample(
      wallNanos: Long,
      allocatedBytes: Option[Long],
      observation: BenchmarkWorkloads.Observation
  )

  def main(arguments: Array[String]): Unit =
    val config     = parse(arguments.toList)
    val allocation = ThreadAllocation.current
    val baseline   = config.comparison.map(loadProperties)
    println("benchmark.runtime=jvm")
    println(s"benchmark.scale=${config.scaleName}")
    println(s"benchmark.warmup=${config.warmup}")
    println(s"benchmark.samples=${config.samples}")
    println(s"benchmark.allocation=${
        if allocation.isDefined then "thread-allocated-bytes" else "unsupported"
      }")
    config.comparison.foreach(path => println(s"benchmark.comparison=${path.toAbsolutePath}"))

    BenchmarkWorkloads.scenarios(config.scale).foreach { scenario =>
      (0 until config.warmup).foreach(_ => scenario.execute())
      val samples          = Vector.fill(config.samples)(measure(scenario, allocation))
      require(
        samples.map(_.observation.counters).distinct.length === 1,
        s"Non-deterministic counters for ${scenario.name}"
      )
      val wallMedian       = median(samples.map(_.wallNanos))
      val allocationMedian = sequence(samples.map(_.allocatedBytes)).map(median)
      val prefix           = s"benchmark.${scenario.name}"
      scenario.metadata.foreach { case (key, value) => println(s"$prefix.metadata.$key=$value") }
      println(s"$prefix.wallMedianNs=$wallMedian")
      println(s"$prefix.allocationMedianBytes=${allocationMedian.fold("unsupported")(_.toString)}")
      printCounters(prefix, samples.head.observation)
      baseline.foreach(properties => printRatios(prefix, wallMedian, allocationMedian, properties))
    }

  private def measure(
      scenario: BenchmarkWorkloads.Scenario,
      allocation: Option[ThreadAllocation]
  ): Sample =
    val allocatedBefore = allocation.map(_.allocatedBytes)
    val started         = System.nanoTime()
    val observation     = scenario.execute()
    val elapsed         = System.nanoTime() - started
    val allocated       = allocation.zip(allocatedBefore).map { case (counter, before) =>
      counter.allocatedBytes - before
    }
    Sample(elapsed, allocated, observation)

  private def printCounters(prefix: String, observation: BenchmarkWorkloads.Observation): Unit =
    val counters = observation.counters
    println(s"$prefix.counters.componentRenders=${counters.componentRenders}")
    println(s"$prefix.counters.paintedRows=${counters.paintedRows}")
    println(s"$prefix.counters.terminalWrites=${counters.terminalWrites}")
    println(s"$prefix.counters.imageEncodes=${counters.controlEncodes}")
    println(s"$prefix.counters.searchScans=${counters.searchScans}")
    println(s"$prefix.checksum=${observation.checksum}")

  private def printRatios(
      prefix: String,
      wallMedian: Long,
      allocationMedian: Option[Long],
      properties: Properties
  ): Unit =
    baselineLong(properties, s"$prefix.wallMedianNs").foreach(value =>
      println(s"$prefix.comparison.wallRatio=${ratio(wallMedian, value)}")
    )
    allocationMedian.foreach(current =>
      baselineLong(properties, s"$prefix.allocationMedianBytes").foreach(value =>
        println(s"$prefix.comparison.allocationRatio=${ratio(current, value)}")
      )
    )

  private def ratio(current: Long, baseline: Long): String =
    require(baseline > 0L, "Comparison baseline must be positive")
    f"${current.toDouble / baseline.toDouble}%.4f"

  private def baselineLong(properties: Properties, key: String): Option[Long] =
    Option(properties.getProperty(key)).filter(_ !== "unsupported").map(_.toLong)

  private def loadProperties(path: Path): Properties =
    val properties = Properties()
    val input      = Files.newInputStream(path)
    try properties.load(input)
    finally input.close()
    properties

  private def sequence(values: Vector[Option[Long]]): Option[Vector[Long]] =
    Option.when(values.forall(_.nonEmpty))(values.flatten)

  private def median(values: Vector[Long]): Long =
    require(values.nonEmpty, "Median requires at least one sample")
    values.sorted.apply(values.length / 2)

  private def parse(arguments: List[String]): Config =
    def loop(remaining: List[String], config: Config): Config = remaining match
      case Nil                          => config
      case "--" :: tail                 => loop(tail, config)
      case "--quick" :: tail            =>
        loop(tail, config.copy(scale = BenchmarkWorkloads.Scale.Quick, scaleName = "quick"))
      case "--warmup" :: value :: tail  =>
        loop(tail, config.copy(warmup = positive(value, "warmup")))
      case "--samples" :: value :: tail =>
        loop(tail, config.copy(samples = positive(value, "samples")))
      case "--compare" :: value :: tail =>
        loop(tail, config.copy(comparison = Some(Path.of(value))))
      case option :: _                  => throw IllegalArgumentException(s"Unknown benchmark option: $option")

    loop(
      arguments,
      Config(BenchmarkWorkloads.Scale.Standard, "standard", warmup = 3, samples = 7, None)
    )

  private def positive(value: String, name: String): Int =
    val parsed = value.toInt
    require(parsed > 0, s"Benchmark $name must be positive")
    parsed

  private trait ThreadAllocation:
    def allocatedBytes: Long

  private object ThreadAllocation:
    def current: Option[ThreadAllocation] =
      ManagementFactory.getThreadMXBean match
        case bean: com.sun.management.ThreadMXBean if bean.isThreadAllocatedMemorySupported =>
          if !bean.isThreadAllocatedMemoryEnabled then bean.setThreadAllocatedMemoryEnabled(true)
          val threadId = Thread.currentThread().threadId()
          Some(new ThreadAllocation:
            override def allocatedBytes: Long = bean.getThreadAllocatedBytes(threadId))
        case _                                                                              => None
