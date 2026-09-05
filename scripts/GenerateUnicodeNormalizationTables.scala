//> using scala "3.7.4"

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object GenerateUnicodeNormalizationTables:
  private val Version = "17.0.0"
  private val Source  = s"https://www.unicode.org/Public/$Version/ucd/UnicodeData.txt"

  @main def main(outputs: String*): Unit =
    require(outputs.length <= 1, "expected an optional runtime output path")
    val output           = outputs.headOption.getOrElse(
      "core/src/scalatui/unicode/UnicodeNormalizationTables.scala"
    )
    val text             = scala.io.Source.fromURL(URI(Source).toURL, "UTF-8").mkString
    require(text.nonEmpty, "UnicodeData.txt is empty")
    val records          = text.linesIterator.map(_.split(";", -1)).toVector
    val decompositions   = records.flatMap { fields =>
      val value = fields(5)
      Option.when(value.nonEmpty && !value.startsWith("<")) {
        Integer.parseInt(fields(0), 16) -> value.split(" ").toVector.map(Integer.parseInt(_, 16))
      }
    }
    val combiningClasses = records.flatMap { fields =>
      val value = fields(3).toInt
      Option.when(value > 0)(Integer.parseInt(fields(0), 16) -> value)
    }
    val values           = decompositions.flatMap(_._2)
    val offsets          = decompositions.scanLeft(0)((offset, entry) => offset + entry._2.length)
    val rendered         = s"""package scalatui.unicode

/** Generated Unicode $Version canonical decomposition and combining-class data. */
private[unicode] object UnicodeNormalizationTables:
  val version: String = "$Version"

${array("decompositionKeys", decompositions.map(_._1))}
${array("decompositionOffsets", offsets)}
${array("decompositionValues", values)}
${array("combiningKeys", combiningClasses.map(_._1))}
${array("combiningValues", combiningClasses.map(_._2))}

  def decomposition(codePoint: Int): Option[Array[Int]] =
    val index = java.util.Arrays.binarySearch(decompositionKeys, codePoint)
    Option.when(index >= 0)(java.util.Arrays.copyOfRange(
      decompositionValues,
      decompositionOffsets(index),
      decompositionOffsets(index + 1)
    ))

  def combiningClass(codePoint: Int): Int =
    val index = java.util.Arrays.binarySearch(combiningKeys, codePoint)
    if index >= 0 then combiningValues(index) else 0
"""
    val path             = Path.of(output)
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, rendered, StandardCharsets.UTF_8)
    println(s"Generated $output for Unicode $Version")

  private def array(name: String, values: Vector[Int]): String =
    val chunks  = values.grouped(500).toVector
    val methods = chunks.zipWithIndex.map { case (chunk, index) =>
      val rows = chunk.grouped(12)
        .map(row => "      " + row.map(value => f"0x$value%x").mkString(", "))
        .mkString(",\n")
      s"  private def ${name}Chunk$index: Array[Int] = Array(\n$rows\n    )"
    }.mkString("\n")
    val calls   = chunks.indices.map(index => s"${name}Chunk$index").mkString(", ")
    s"$methods\n  private val $name: Array[Int] = Array.concat($calls)"
