package scalatui.demo

import scalatui.syntax.Equality.*

/** Deterministic, bounded stress content for the local streaming viewer; no external data. */
private[demo] object StreamingResponseSamples:
  val ChunkUnits: Int      = 256
  val IntervalMillis: Long = 160L

  def response(prompt: String): String =
    val overview = Vector(
      "# Streaming Markdown field report",
      "",
      s"You asked: ${prompt.take(256)}",
      "",
      "This local fixture is deliberately several screens long, even in a wide terminal. " +
        "Scroll up while output continues, select earlier text, then jump back to the latest output.",
      "",
      "It includes **emphasis**, `inline code`, [a complete link](https://example.org), " +
        "wide text 界, combining marks café and emoji 🙂. No network is accessed.",
      ""
    ).mkString("\n")
    val sections = (1 to 4).map { section =>
      val table     = Vector(
        s"## $section. Rendering matrix",
        "",
        "| Case | State | Unicode label | Notes |",
        "| :--- | :---: | --- | ---: |"
      ) ++ (1 to 12).map { row =>
        s"| $section.$row | ${if row % 3 === 0 then "pending" else "ready"} | 界 café 🙂 | " +
          s"A deliberately long cell for wrapping and narrow-window checks, sample $row. |"
      }
      val scalaCode = Vector(
        "",
        "### Scala: larger generated data set",
        "",
        "```scala",
        "final case class Sample(id: Int, label: String, enabled: Boolean)",
        "",
        "val samples = Vector("
      ) ++ (1 to 24).map { row =>
        val comma = if row < 24 then "," else ""
        s"  Sample($row, \"section-$section / 界 / café / item-$row\", ${row % 2 === 0})$comma"
      } ++ Vector(
        ")",
        "",
        "val enabled = samples.filter(_.enabled)",
        "enabled.foreach { sample =>",
        "  println(s\"${sample.id}: ${sample.label}\")",
        "}",
        "// Literal Markdown inside code must stay literal: **bold**, [link](url), | cell |",
        "```",
        ""
      )
      val jsonCode  = Vector(
        "### JSON: nested content and long strings",
        "",
        "```json",
        "{",
        s"  \"section\": $section,",
        "  \"samples\": ["
      ) ++ (1 to 8).map { row =>
        val comma = if row < 8 then "," else ""
        s"    {\"id\": $row, \"label\": \"界 / café / 🙂\", \"note\": \"Long code lines should remain bounded when the terminal becomes narrow.\"}$comma"
      } ++ Vector("  ]", "}", "```", "")
      (table ++ scalaCode ++ jsonCode ++ Vector(
        s"Section $section complete. Try resizing while the next table or code fence is still arriving.",
        "",
        "The renderer uses its documented baseline Markdown grammar. These cells are not a promise " +
          "of full CommonMark table alignment or horizontal code scrolling.",
        ""
      )).mkString("\n")
    }.mkString("\n")
    overview + "\n" + sections +
      "\n## Response complete\n\nAll four tables and eight code blocks have arrived.\n"
