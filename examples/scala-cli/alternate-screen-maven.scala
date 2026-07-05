#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using file ../../core/src
//> using file ../../terminalJvm/src
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.38.17
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.38.17

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import scalatui.ansi.Ansi
import scalatui.core.*
import scalatui.syntax.Equality.*
import scalatui.terminal.*
import scalatui.terminal.jvm.SttyTerminal

final case class MavenSearchResponse(response: MavenResponse = MavenResponse()) derives CanEqual
final case class MavenResponse(numFound: Int = 0, docs: Vector[MavenDoc] = Vector.empty)
    derives CanEqual
final case class MavenDoc(
    id: String = "",
    g: String = "",
    a: String = "",
    latestVersion: String = "",
    v: String = "",
    versionCount: Int = 0,
    timestamp: Long = 0L,
    ec: Vector[String] = Vector.empty
) derives CanEqual

given JsonValueCodec[MavenSearchResponse] = JsonCodecMaker.make

final case class Artifact(group: String, artifact: String, latestVersion: String, versionCount: Int)
    derives CanEqual:
  def coordinate: String = s"$group:$artifact:$latestVersion"

final case class ArtifactVersion(version: String, extensions: Vector[String], timestamp: Long)
    derives CanEqual

enum LoadState derives CanEqual:
  case Idle, Loading, Ready, Failed

final class MavenCentralClient:
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(8))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  def search(query: String): Either[String, Vector[Artifact]] =
    val trimmed = query.trim
    if trimmed.isEmpty then Right(Vector.empty)
    else
      request(
        "https://search.maven.org/solrsearch/select",
        Map("q" -> trimmed, "rows" -> "20", "wt" -> "json")
      ).map(_.response.docs.map(doc =>
        Artifact(doc.g, doc.a, doc.latestVersion, doc.versionCount)
      ).filter(artifact => artifact.group.nonEmpty && artifact.artifact.nonEmpty))

  def versions(artifact: Artifact): Either[String, Vector[ArtifactVersion]] =
    request(
      "https://search.maven.org/solrsearch/select",
      Map(
        "q"    -> s"g:\"${artifact.group}\" AND a:\"${artifact.artifact}\"",
        "core" -> "gav",
        "rows" -> "12",
        "wt"   -> "json"
      )
    ).map(_.response.docs.map(doc =>
      ArtifactVersion(doc.v, doc.ec.filter(_.nonEmpty), doc.timestamp)
    ).filter(_.version.nonEmpty))

  private def request(
      baseUrl: String,
      params: Map[String, String]
  ): Either[String, MavenSearchResponse] =
    val query   = params.map { case (key, value) =>
      s"${encode(key)}=${encode(value)}"
    }.mkString("&")
    val request = HttpRequest.newBuilder(URI.create(s"$baseUrl?$query"))
      .timeout(Duration.ofSeconds(12))
      .header("Accept", "application/json")
      .GET()
      .build()
    try
      val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
      if response.statusCode >= 200 && response.statusCode < 300 then
        Right(readFromArray[MavenSearchResponse](response.body()))
      else Left(s"Maven Central returned HTTP ${response.statusCode}")
    catch
      case error: Exception => Left(error.getMessage.nn)

  private def encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

final class MavenExplorer(tui: TUI) extends Component:
  private val client                 = MavenCentralClient()
  private var query                  = "zio-http_3"
  private var selected               = 0
  private var artifacts              = Vector.empty[Artifact]
  private var versions               = Vector.empty[ArtifactVersion]
  private var state: LoadState       = LoadState.Idle
  private var detailState: LoadState = LoadState.Idle
  private var message                = "Press Enter to search Maven Central. Esc exits cleanly."
  private var selectedCoordinate     = Option.empty[String]

  override def render(width: Int): Vector[String] =
    val w               = math.max(20, width)
    val leftWidth       = math.max(28, w / 2 - 2)
    val rightWidth      = math.max(24, w - leftWidth - 5)
    val currentArtifact = artifacts.lift(selected)
    val title           = "Maven Central Telescope — alternate screen demo"
    val lines           = Vector.newBuilder[String]

    lines += boxLine("┌", "─", "┐", w)
    lines += framed(title, w)
    lines += framed(s"Search: $query${cursorPulse}", w)
    lines += boxLine("├", "─", "┤", w)
    lines += twoPaneHeader("Results", "Versions / snippets", leftWidth, rightWidth, w)

    val resultRows = visibleArtifacts(leftWidth)
    val detailRows = visibleDetails(currentArtifact, rightWidth)
    val rowCount   = math.max(resultRows.size, detailRows.size).max(8)
    (0 until rowCount).foreach { index =>
      lines += twoPane(
        resultRows.lift(index).getOrElse(""),
        detailRows.lift(index).getOrElse(""),
        leftWidth,
        rightWidth,
        w
      )
    }

    lines += boxLine("├", "─", "┤", w)
    selectedCoordinate.foreach(value => lines += framed(s"Pinned: $value", w))
    lines += framed(message, w)
    lines += framed("Type query · Enter search · ↑/↓ select · Tab versions · c pin · Esc exit", w)
    lines += boxLine("└", "─", "┘", w)
    lines.result()

  override def handleInputResult(input: TerminalInput): InputResult =
    input match
      case TerminalInput.Key(TerminalKey.Character("c"), modifiers) if modifiers.ctrl      =>
        InputResult.Exit
      case TerminalInput.Key(TerminalKey.Character("c"), modifiers) if modifiers.isEmpty   =>
        pinSelected()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Character(value), modifiers) if modifiers.isEmpty =>
        query += value
        message = "Press Enter to search Maven Central."
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Backspace, _)                                     =>
        if query.nonEmpty then query = query.dropRight(1)
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Enter, _)                                         =>
        runSearch()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Tab, _) | TerminalInput.Key(TerminalKey.Right, _) =>
        loadVersionsForSelection()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Up, _)                                            =>
        if selected > 0 then
          selected -= 1
          resetDetails()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Down, _)                                          =>
        if selected + 1 < artifacts.size then
          selected += 1
          resetDetails()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Escape, _)                                        =>
        InputResult.Exit
      case _                                                                               =>
        InputResult.Render

  private def runSearch(): Unit =
    state = LoadState.Loading
    detailState = LoadState.Idle
    message = "Searching Maven Central..."
    client.search(query) match
      case Right(results) =>
        artifacts = results
        selected = 0
        versions = Vector.empty
        state = LoadState.Ready
        message = if results.isEmpty then "No artifacts found."
        else s"Found ${results.size} artifacts. Press Tab to load versions."
      case Left(error)    =>
        artifacts = Vector.empty
        versions = Vector.empty
        state = LoadState.Failed
        detailState = LoadState.Idle
        message = s"Search failed: $error"

  private def loadVersionsForSelection(): Unit =
    artifacts.lift(selected) match
      case Some(artifact) =>
        detailState = LoadState.Loading
        client.versions(artifact) match
          case Right(values) =>
            versions = values
            detailState = LoadState.Ready
            if values.nonEmpty then
              message = s"Loaded ${values.size} versions for ${artifact.artifact}."
          case Left(error)   =>
            versions = Vector.empty
            detailState = LoadState.Failed
            message = s"Version lookup failed: $error"
      case None           =>
        versions = Vector.empty
        detailState = LoadState.Idle

  private def resetDetails(): Unit =
    versions = Vector.empty
    detailState = LoadState.Idle

  private def pinSelected(): Unit =
    artifacts.lift(selected).foreach { artifact =>
      val version =
        versions.headOption.map(_.version).filter(_.nonEmpty).getOrElse(artifact.latestVersion)
      selectedCoordinate = Some(s"${artifact.group}:${artifact.artifact}:$version")
      message = "Pinned coordinate. It will stay visible until you pin another artifact."
    }

  private def visibleArtifacts(width: Int): Vector[String] =
    if artifacts.isEmpty then
      state match
        case LoadState.Idle    =>
          Vector("Enter a query such as zio-http_3", "or org.typelevel cats-effect_3.")
        case LoadState.Loading => Vector("Searching...")
        case LoadState.Failed  => Vector("Search failed.")
        case LoadState.Ready   => Vector("No results.")
    else
      artifacts.zipWithIndex.map { case (artifact, index) =>
        val marker = if index === selected then "›" else " "
        fit(s"$marker ${artifact.group}:${artifact.artifact}  ${artifact.latestVersion}", width)
      }

  private def visibleDetails(currentArtifact: Option[Artifact], width: Int): Vector[String] =
    currentArtifact match
      case None           => Vector("Select an artifact to inspect versions.")
      case Some(artifact) =>
        val base           = Vector(
          fit(s"Group:    ${artifact.group}", width),
          fit(s"Artifact: ${artifact.artifact}", width),
          fit(s"Latest:   ${artifact.latestVersion}", width),
          fit(s"Versions: ${artifact.versionCount}", width),
          ""
        )
        val versionRows    = detailState match
          case LoadState.Idle    => Vector("Press Tab to load versions.")
          case LoadState.Loading => Vector("Loading versions...")
          case LoadState.Failed  => Vector("Version lookup failed.")
          case LoadState.Ready   =>
            if versions.isEmpty then Vector("No versions returned.")
            else
              versions.take(6).map { version =>
                val files =
                  version.extensions.map(_.stripPrefix("-").stripPrefix(".")).take(4).mkString(" ")
                fit(s"${version.version}  $files", width)
              }
        val snippetVersion =
          versions.headOption.map(_.version).filter(_.nonEmpty).getOrElse(artifact.latestVersion)
        val snippets       = Vector(
          "",
          fit(
            s"Scala CLI: //> using dep ${artifact.group}:${artifact.artifact}:$snippetVersion",
            width
          ),
          fit(
            s"SBT: \"${artifact.group}\" % \"${artifact.artifact}\" % \"$snippetVersion\"",
            width
          ),
          fit(s"Maven: ${artifact.group}:${artifact.artifact}:$snippetVersion", width)
        )
        base ++ versionRows ++ snippets

  private def twoPaneHeader(
      left: String,
      right: String,
      leftWidth: Int,
      rightWidth: Int,
      total: Int
  ): String =
    fit(s"│ ${fit(left, leftWidth)} │ ${fit(right, rightWidth)} │", total)

  private def twoPane(
      left: String,
      right: String,
      leftWidth: Int,
      rightWidth: Int,
      total: Int
  ): String =
    fit(s"│ ${fit(left, leftWidth)} │ ${fit(right, rightWidth)} │", total)

  private def framed(text: String, width: Int): String =
    val inner = math.max(0, width - 4)
    s"│ ${fit(text, inner)} │"

  private def boxLine(left: String, fill: String, right: String, width: Int): String =
    if width <= 2 then fill * width else left + (fill * (width - 2)) + right

  private def fit(text: String, width: Int): String =
    if width <= 0 then ""
    else Ansi.padRight(Ansi.truncateToWidth(text, width, ellipsis = "…"), width)

  private def cursorPulse: String = "_"

@main def mavenCentralTelescope(): Unit =
  val tui      = TUI(SttyTerminal(), TUIOptions(screenMode = TUIScreenMode.Alternate))
  val explorer = MavenExplorer(tui)
  tui.addChild(explorer)
  tui.setFocus(explorer)
  tui.exitsOnEscape = true
  tui.run()
