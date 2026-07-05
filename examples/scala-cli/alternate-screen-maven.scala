#!/usr/bin/env -S scala-cli shebang
//> using scala 3.7.4
//> using file ../../core/src
//> using file ../../terminalJvm/src
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.38.17
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-macros:2.38.17

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import scalatui.ansi.Ansi
import scalatui.core.*
import scalatui.syntax.Equality.*
import scalatui.terminal.*
import scalatui.terminal.jvm.SttyTerminal

final case class SonatypeComponentsResponse(components: Vector[SonatypeComponent] = Vector.empty)
    derives CanEqual
final case class SonatypeComponent(
    namespace: String = "",
    name: String = "",
    description: String = "",
    latestVersionInfo: SonatypeLatestVersion = SonatypeLatestVersion(),
    classifiers: Vector[String] = Vector.empty,
    packaging: String = ""
) derives CanEqual
final case class SonatypeLatestVersion(
    version: String = "",
    licenses: Vector[String] = Vector.empty
) derives CanEqual

given JsonValueCodec[SonatypeComponentsResponse] = JsonCodecMaker.make
given JsonValueCodec[Vector[String]]             = JsonCodecMaker.make

final case class Artifact(
    group: String,
    artifact: String,
    latestVersion: String,
    description: String,
    classifiers: Vector[String],
    packaging: String
) derives CanEqual:
  def module: String     = s"$group:$artifact"
  def coordinate: String = s"$module:$latestVersion"

final case class BuildSnippet(label: String, value: String) derives CanEqual

enum LoadState derives CanEqual:
  case Idle, Loading, Ready, Failed

enum ExplorerStep derives CanEqual:
  case Search, Artifacts, Versions, BuildTool

final class SonatypeCentralClient:
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(8))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  def search(query: String): Either[String, Vector[Artifact]] =
    val trimmed = query.trim
    if trimmed.isEmpty then Right(Vector.empty)
    else
      val body = s"""{"page":0,"size":20,"searchTerm":${jsonString(trimmed)}}"""
      post[SonatypeComponentsResponse](
        "https://central.sonatype.com/api/internal/browse/components",
        body
      ).map(_.components.map(component =>
        Artifact(
          component.namespace,
          component.name,
          component.latestVersionInfo.version,
          component.description,
          component.classifiers,
          component.packaging
        )
      ).filter(artifact => artifact.group.nonEmpty && artifact.artifact.nonEmpty))

  def versions(artifact: Artifact): Either[String, Vector[String]] =
    val body =
      s"""{"sortField":"normalizedVersion","sortDirection":"desc","filter":[${jsonString(
          s"namespace:${artifact.group}"
        )},${jsonString(s"name:${artifact.artifact}")}]}"""
    post[Vector[String]](
      "https://central.sonatype.com/api/internal/browse/component/versions",
      body
    ).map(_.filter(_.nonEmpty).take(12))

  private def post[A: JsonValueCodec](url: String, body: String): Either[String, A] =
    val request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(12))
      .header("Accept", "application/json")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    try
      val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
      if response.statusCode >= 200 && response.statusCode < 300 then
        Right(readFromArray[A](response.body()))
      else Left(s"Sonatype Central returned HTTP ${response.statusCode}")
    catch
      case error: Exception => Left(error.getMessage.nn)

  private def jsonString(value: String): String =
    val escaped = value.flatMap {
      case '\"'               => "\\\""
      case '\\'               => "\\\\"
      case '\b'               => "\\b"
      case '\f'               => "\\f"
      case '\n'               => "\\n"
      case '\r'               => "\\r"
      case '\t'               => "\\t"
      case char if char < ' ' => f"\\u${char.toInt}%04x"
      case char               => char.toString
    }
    s"\"$escaped\""

final class MavenExplorer(tui: TUI) extends Component:
  private val client                 = SonatypeCentralClient()
  private var query                  = "siglyph"
  private var step: ExplorerStep     = ExplorerStep.Search
  private var artifactIndex          = 0
  private var versionIndex           = 0
  private var snippetIndex           = 0
  private var artifacts              = Vector.empty[Artifact]
  private var versions               = Vector.empty[String]
  private var state: LoadState       = LoadState.Idle
  private var detailState: LoadState = LoadState.Idle
  private var message                = "Press Enter to search Sonatype Central. Esc exits cleanly."
  private var selectedCoordinate     = Option.empty[String]

  override def render(width: Int): Vector[String] =
    val w = math.max(1, width)
    if w < 32 then compactRender(w)
    else
      val paneInner       = math.max(0, w - 7)
      val leftWidth       = paneInner / 2
      val rightWidth      = paneInner - leftWidth
      val currentArtifact = artifacts.lift(artifactIndex)
      val title           = "Sonatype Central Telescope — alternate screen demo"
      val lines           = Vector.newBuilder[String]

      lines += boxLine("┌", "─", "┐", w)
      lines += framed(title, w)
      lines += framed(
        s"Step: ${stepLabel}    Search: $query${if step === ExplorerStep.Search then "_" else ""}",
        w
      )
      lines += boxLine("├", "─", "┤", w)
      lines += twoPaneHeader("Artifacts", "Versions and build snippets", leftWidth, rightWidth)

      val resultRows = visibleArtifacts(leftWidth)
      val detailRows = visibleDetails(currentArtifact, rightWidth)
      val rowCount   = math.max(resultRows.size, detailRows.size).max(9)
      (0 until rowCount).foreach { index =>
        lines += twoPane(
          resultRows.lift(index).getOrElse(""),
          detailRows.lift(index).getOrElse(""),
          leftWidth,
          rightWidth
        )
      }

      lines += boxLine("├", "─", "┤", w)
      selectedCoordinate.foreach(value => lines += framed(s"Selected: $value", w))
      lines += framed(message, w)
      lines += framed(helpText, w)
      lines += boxLine("└", "─", "┘", w)
      lines.result()

  override def handleInputResult(input: TerminalInput): InputResult =
    input match
      case TerminalInput.Key(TerminalKey.Character("c"), modifiers) if modifiers.ctrl  =>
        InputResult.Exit
      case TerminalInput.Key(TerminalKey.Escape, _)                                    =>
        InputResult.Exit
      case TerminalInput.Key(TerminalKey.Enter, _)                                     =>
        advance()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Left, _)                                      =>
        goBack()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Up, _)                                        =>
        moveSelection(-1)
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Down, _)                                      =>
        moveSelection(1)
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Backspace, _) if step === ExplorerStep.Search =>
        if query.nonEmpty then query = query.dropRight(1)
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Character(value), modifiers)
          if modifiers.isEmpty && step === ExplorerStep.Search =>
        query += value
        message = "Press Enter to search Sonatype Central."
        InputResult.Render
      case _                                                                           =>
        InputResult.Render

  private def advance(): Unit =
    step match
      case ExplorerStep.Search    => runSearch()
      case ExplorerStep.Artifacts => loadVersionsForSelection()
      case ExplorerStep.Versions  =>
        if versions.nonEmpty then
          step = ExplorerStep.BuildTool
          snippetIndex = 0
          message = "Choose a build-tool coordinate and press Enter to select it."
        else loadVersionsForSelection()
      case ExplorerStep.BuildTool => pinSelectedSnippet()

  private def goBack(): Unit =
    step match
      case ExplorerStep.Search    => ()
      case ExplorerStep.Artifacts => step = ExplorerStep.Search
      case ExplorerStep.Versions  => step = ExplorerStep.Artifacts
      case ExplorerStep.BuildTool => step = ExplorerStep.Versions

  private def moveSelection(delta: Int): Unit =
    step match
      case ExplorerStep.Search    => ()
      case ExplorerStep.Artifacts =>
        if artifacts.nonEmpty then
          artifactIndex = clamp(artifactIndex + delta, artifacts.size)
          versions = Vector.empty
          versionIndex = 0
          snippetIndex = 0
          detailState = LoadState.Idle
          message = "Press Enter to inspect versions for the selected artifact."
      case ExplorerStep.Versions  =>
        if versions.nonEmpty then versionIndex = clamp(versionIndex + delta, versions.size)
      case ExplorerStep.BuildTool =>
        val snippets = buildSnippets
        if snippets.nonEmpty then snippetIndex = clamp(snippetIndex + delta, snippets.size)

  private def runSearch(): Unit =
    state = LoadState.Loading
    detailState = LoadState.Idle
    message = "Searching Sonatype Central..."
    client.search(query) match
      case Right(results) =>
        artifacts = results
        artifactIndex = 0
        versionIndex = 0
        snippetIndex = 0
        versions = Vector.empty
        state = LoadState.Ready
        if results.isEmpty then message = "No artifacts found. Edit the query and press Enter."
        else
          step = ExplorerStep.Artifacts
          message = s"Found ${results.size} artifacts. Press Enter to inspect versions."
      case Left(error)    =>
        artifacts = Vector.empty
        versions = Vector.empty
        state = LoadState.Failed
        detailState = LoadState.Idle
        message = s"Search failed: $error"

  private def loadVersionsForSelection(): Unit =
    artifacts.lift(artifactIndex) match
      case Some(artifact) =>
        detailState = LoadState.Loading
        message = s"Loading versions for ${artifact.artifact}..."
        client.versions(artifact) match
          case Right(values) =>
            versions = values
            versionIndex = 0
            snippetIndex = 0
            detailState = LoadState.Ready
            if values.isEmpty then message = "No versions returned for this artifact."
            else
              step = ExplorerStep.Versions
              message = s"Loaded ${values.size} versions. Press Enter to choose a build tool."
          case Left(error)   =>
            versions = Vector.empty
            detailState = LoadState.Failed
            message = s"Version lookup failed: $error"
      case None           =>
        versions = Vector.empty
        detailState = LoadState.Idle
        message = "Select an artifact first."

  private def pinSelectedSnippet(): Unit =
    buildSnippets.lift(snippetIndex).foreach { snippet =>
      selectedCoordinate = Some(snippet.value)
      message = s"Selected ${snippet.label}. Esc exits and restores the normal screen."
    }

  private def visibleArtifacts(width: Int): Vector[String] =
    if artifacts.isEmpty then
      state match
        case LoadState.Idle    =>
          Vector("Default query: siglyph", "Type another query or press Enter.")
        case LoadState.Loading => Vector("Searching...")
        case LoadState.Failed  => Vector("Search failed.")
        case LoadState.Ready   => Vector("No results.")
    else
      artifacts.zipWithIndex.map { case (artifact, index) =>
        val marker = if step === ExplorerStep.Artifacts && index === artifactIndex then "›" else " "
        fit(s"$marker ${artifact.group}:${artifact.artifact}  ${artifact.latestVersion}", width)
      }

  private def visibleDetails(currentArtifact: Option[Artifact], width: Int): Vector[String] =
    currentArtifact match
      case None           => Vector("Search first, then select an artifact.")
      case Some(artifact) =>
        val base        = Vector(
          fit(s"Group:    ${artifact.group}", width),
          fit(s"Artifact: ${artifact.artifact}", width),
          fit(s"Latest:   ${artifact.latestVersion}", width),
          fit(s"Package:  ${artifact.packaging}", width),
          ""
        )
        val versionRows = detailState match
          case LoadState.Idle    => Vector("Press Enter to load versions.")
          case LoadState.Loading => Vector("Loading versions...")
          case LoadState.Failed  => Vector("Version lookup failed.")
          case LoadState.Ready   =>
            if versions.isEmpty then Vector("No versions returned.")
            else
              Vector("Versions:") ++ versions.take(7).zipWithIndex.map { case (version, index) =>
                val marker =
                  if step === ExplorerStep.Versions && index === versionIndex then "›" else " "
                fit(s"$marker $version", width)
              }
        val snippetRows = if step === ExplorerStep.BuildTool then
          Vector("", "Build tool:") ++ buildSnippets.zipWithIndex.map { case (snippet, index) =>
            val marker = if index === snippetIndex then "›" else " "
            fit(s"$marker ${snippet.label}: ${snippet.value}", width)
          }
        else Vector.empty
        base ++ versionRows ++ snippetRows

  private def buildSnippets: Vector[BuildSnippet] =
    artifacts.lift(artifactIndex) match
      case None           => Vector.empty
      case Some(artifact) =>
        val version = versions.lift(versionIndex).getOrElse(artifact.latestVersion)
        val module  = artifact.module
        Vector(
          BuildSnippet("Scala CLI", s"//> using dep $module:$version"),
          BuildSnippet("SBT", s"\"${artifact.group}\" % \"${artifact.artifact}\" % \"$version\""),
          BuildSnippet("Mill", s"mvn\"${artifact.group}:${artifact.artifact}:$version\""),
          BuildSnippet("Maven", s"${artifact.group}:${artifact.artifact}:$version")
        )

  private def compactRender(width: Int): Vector[String] =
    Vector(
      fit("Sonatype Central Telescope", width),
      fit(s"Search: $query", width),
      fit(message, width),
      fit("Use a wider terminal.", width)
    )

  private def stepLabel: String = step match
    case ExplorerStep.Search    => "1 Search"
    case ExplorerStep.Artifacts => "2 Artifact"
    case ExplorerStep.Versions  => "3 Version"
    case ExplorerStep.BuildTool => "4 Build tool"

  private def helpText: String = step match
    case ExplorerStep.Search    => "Type query · Enter search · Esc exit"
    case ExplorerStep.Artifacts => "↑/↓ artifact · Enter versions · ← search · Esc exit"
    case ExplorerStep.Versions  => "↑/↓ version · Enter build tool · ← artifacts · Esc exit"
    case ExplorerStep.BuildTool => "↑/↓ build tool · Enter select · ← versions · Esc exit"

  private def twoPaneHeader(left: String, right: String, leftWidth: Int, rightWidth: Int): String =
    s"│ ${fit(left, leftWidth)} │ ${fit(right, rightWidth)} │"

  private def twoPane(left: String, right: String, leftWidth: Int, rightWidth: Int): String =
    s"│ ${fit(left, leftWidth)} │ ${fit(right, rightWidth)} │"

  private def framed(text: String, width: Int): String =
    if width < 4 then fit(text, width)
    else s"│ ${fit(text, width - 4)} │"

  private def boxLine(left: String, fill: String, right: String, width: Int): String =
    if width <= 2 then fill * width else left + (fill * (width - 2)) + right

  private def fit(text: String, width: Int): String =
    if width <= 0 then ""
    else Ansi.padRight(Ansi.truncateToWidth(text, width, ellipsis = "…"), width)

  private def clamp(value: Int, size: Int): Int =
    math.max(0, math.min(value, math.max(0, size - 1)))

@main def mavenCentralTelescope(): Unit =
  val tui      = TUI(SttyTerminal(), TUIOptions(screenMode = TUIScreenMode.Alternate))
  val explorer = MavenExplorer(tui)
  tui.addChild(explorer)
  tui.setFocus(explorer)
  tui.exitsOnEscape = true
  tui.run()
