package scalatui.benchmark

import scalatui.syntax.Equality.*

/**
 * Opt-in Scala Native functional timing smoke for the shared fixed benchmark workloads.
 *
 * Wall times are informational and machine-dependent. Scala Native allocation reporting remains
 * unsupported, and this runner is not part of ordinary compile, test, format, or lint targets.
 */
object NativeBenchmarkMain:
  private final case class Config(warmup: Int, samples: Int)

  def main(arguments: Array[String]): Unit =
    val config = parse(arguments.toList)
    println("benchmark.runtime=scala-native")
    println("benchmark.scale=quick")
    println(s"benchmark.warmup=${config.warmup}")
    println(s"benchmark.samples=${config.samples}")
    println("benchmark.allocation=unsupported")
    BenchmarkWorkloads.scenarios(BenchmarkWorkloads.Scale.Quick).foreach { scenario =>
      (0 until config.warmup).foreach(_ => scenario.execute())
      val samples     = Vector.fill(config.samples) {
        val started     = System.nanoTime()
        val observation = scenario.execute()
        System.nanoTime() - started -> observation
      }
      require(
        samples.map(_._2.counters).distinct.length === 1,
        s"Non-deterministic counters for ${scenario.name}"
      )
      val prefix      = s"benchmark.${scenario.name}"
      scenario.metadata.foreach { case (key, value) => println(s"$prefix.metadata.$key=$value") }
      println(s"$prefix.wallMedianNs=${median(samples.map(_._1))}")
      println(s"$prefix.allocationMedianBytes=unsupported")
      val observation = samples.head._2
      val counters    = observation.counters
      println(s"$prefix.counters.componentRenders=${counters.componentRenders}")
      println(s"$prefix.counters.paintedRows=${counters.paintedRows}")
      println(s"$prefix.counters.terminalWrites=${counters.terminalWrites}")
      println(s"$prefix.counters.imageEncodes=${counters.controlEncodes}")
      println(s"$prefix.counters.searchScans=${counters.searchScans}")
      println(s"$prefix.checksum=${observation.checksum}")
    }

  private def median(values: Vector[Long]): Long = values.sorted.apply(values.length / 2)

  private def parse(arguments: List[String]): Config = arguments.dropWhile(_ === "--") match
    case Nil                                                   => Config(warmup = 1, samples = 3)
    case "--warmup" :: warmup :: "--samples" :: samples :: Nil =>
      Config(positive(warmup, "warmup"), positive(samples, "samples"))
    case _                                                     =>
      throw IllegalArgumentException("Expected no arguments or --warmup N --samples N")

  private def positive(value: String, name: String): Int =
    val parsed = value.toInt
    require(parsed > 0, s"Benchmark $name must be positive")
    parsed
