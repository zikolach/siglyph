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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import scalatui.ansi.Ansi
import scalatui.core.*
import scalatui.syntax.Equality.*
import scalatui.terminal.*
import scalatui.terminal.jvm.SttyTerminal

// ----- Sonatype Central JSON models -----

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
    licenses: Vector[String] = Vector.empty,
    timestampUnixWithMS: Long = 0L
) derives CanEqual

final case class SonatypeComponentDetails(
    version: String = "",
    publishedEpochMillis: Long = 0L
) derives CanEqual

given JsonValueCodec[SonatypeComponentsResponse] = JsonCodecMaker.make
given JsonValueCodec[SonatypeComponentDetails]   = JsonCodecMaker.make
given JsonValueCodec[Vector[String]]             = JsonCodecMaker.make

// ----- Demo domain models -----

final case class Artifact(
    group: String,
    artifact: String,
    latestVersion: String,
    latestPublished: Option[String],
    description: String,
    classifiers: Vector[String],
    packaging: String
) derives CanEqual:
  def module: String     = s"$group:$artifact"
  def coordinate: String = s"$module:$latestVersion"

final case class VersionInfo(version: String, published: Option[String]) derives CanEqual:
  def label: String = published.fold(version)(date => s"$version  $date")

final case class BuildSnippet(label: String, value: String) derives CanEqual

enum LoadState derives CanEqual:
  case Idle, Loading, Ready, Failed

enum ExplorerStep derives CanEqual:
  case Search, Artifacts, Versions, BuildTool

// ----- Sonatype Central client -----

final class SonatypeCentralClient:
  private val http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(8))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  def search(query: String): Either[String, Vector[Artifact]] =
    val trimmed = query.trim
    if trimmed.isEmpty then Right(Vector.empty)
    else
      val body = s"""{"page":0,"size":20,"searchTerm":${jsonString(trimmed)}}"""
      post[SonatypeComponentsResponse](SonatypeCentralClient.ComponentsUrl, body)
        .map(_.components.map(toArtifact).filter(artifact =>
          artifact.group.nonEmpty && artifact.artifact.nonEmpty
        ))

  def versions(artifact: Artifact): Either[String, Vector[VersionInfo]] =
    val body =
      s"""{"sortField":"normalizedVersion","sortDirection":"desc","filter":[${jsonString(
          s"namespace:${artifact.group}"
        )},${jsonString(s"name:${artifact.artifact}")}]}"""
    post[Vector[String]](SonatypeCentralClient.VersionsUrl, body)
      .map(_.filter(_.nonEmpty).take(12).map(version =>
        VersionInfo(version, publishedDate(artifact, version).toOption.flatten)
      ))

  private def toArtifact(component: SonatypeComponent): Artifact =
    Artifact(
      group = component.namespace,
      artifact = component.name,
      latestVersion = component.latestVersionInfo.version,
      latestPublished = formatDate(component.latestVersionInfo.timestampUnixWithMS),
      description = component.description,
      classifiers = component.classifiers,
      packaging = component.packaging
    )

  private def publishedDate(artifact: Artifact, version: String): Either[String, Option[String]] =
    val entity = s"pkg:maven/${artifact.group}/${artifact.artifact}@$version"
    val url    = SonatypeCentralClient.DetailsUrl + "?id=" + URLEncoder.encode(
      entity,
      StandardCharsets.UTF_8
    )
    get[SonatypeComponentDetails](url).map(details => formatDate(details.publishedEpochMillis))

  private def post[A: JsonValueCodec](url: String, body: String): Either[String, A] =
    val request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(12))
      .header("Accept", "application/json")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    send[A](request)

  private def get[A: JsonValueCodec](url: String): Either[String, A] =
    val request = HttpRequest.newBuilder(URI.create(url))
      .timeout(Duration.ofSeconds(12))
      .header("Accept", "application/json")
      .GET()
      .build()
    send[A](request)

  private def send[A: JsonValueCodec](request: HttpRequest): Either[String, A] =
    try
      val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
      if response.statusCode >= 200 && response.statusCode < 300 then
        Right(readFromArray[A](response.body()))
      else Left(s"Sonatype Central returned HTTP ${response.statusCode}")
    catch
      case error: Exception => Left(error.getMessage.nn)

  private def formatDate(epochMillis: Long): Option[String] =
    if epochMillis <= 0L then None
    else
      val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate
      Some(DateTimeFormatter.ISO_LOCAL_DATE.format(date))

  private def jsonString(value: String): String =
    val escaped = value.flatMap {
      case '"'                => "\\\""
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

object SonatypeCentralClient:
  private val ComponentsUrl = "https://central.sonatype.com/api/internal/browse/components"
  private val VersionsUrl   = "https://central.sonatype.com/api/internal/browse/component/versions"
  private val DetailsUrl    = "https://central.sonatype.com/api/internal/component/details"

// ----- Clipboard helper -----

object TerminalClipboard:
  def copy(terminal: Terminal, text: String): Either[String, Unit] =
    val encoded = Base64.getEncoder.encodeToString(text.getBytes(StandardCharsets.UTF_8))
    try
      terminal.write(s"\u001b]52;c;$encoded\u0007")
      Right(())
    catch
      case error: Exception => Left(error.getMessage.nn)

// ----- Explorer component -----

final class MavenExplorer(tui: TUI) extends Component:
  private val client = SonatypeCentralClient()

  private var query             = "siglyph"
  private var step              = ExplorerStep.Search
  private var artifactIndex     = 0
  private var versionIndex      = 0
  private var snippetIndex      = 0
  private var artifacts         = Vector.empty[Artifact]
  private var versions          = Vector.empty[VersionInfo]
  private var searchState       = LoadState.Idle
  private var versionState      = LoadState.Idle
  private var message           = "Press Enter to search Sonatype Central. Esc exits cleanly."
  private var selectedSnippet   = Option.empty[BuildSnippet]
  private var clipboardAttempts = 0

  override def render(width: Int): Vector[String] =
    val w = math.max(1, width)
    if w < 32 then compactRender(w)
    else fullRender(w)

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

  private def advance(): Unit = step match
    case ExplorerStep.Search    => runSearch()
    case ExplorerStep.Artifacts => loadVersionsForSelection()
    case ExplorerStep.Versions  => openBuildToolStep()
    case ExplorerStep.BuildTool => copySelectedSnippet()

  private def goBack(): Unit = step match
    case ExplorerStep.Search    => ()
    case ExplorerStep.Artifacts => step = ExplorerStep.Search
    case ExplorerStep.Versions  => step = ExplorerStep.Artifacts
    case ExplorerStep.BuildTool => step = ExplorerStep.Versions

  private def moveSelection(delta: Int): Unit = step match
    case ExplorerStep.Search    => ()
    case ExplorerStep.Artifacts =>
      if artifacts.nonEmpty then
        artifactIndex = clamp(artifactIndex + delta, artifacts.size)
        resetVersionSelection()
        message = "Press Enter to inspect versions for the selected artifact."
    case ExplorerStep.Versions  =>
      if versions.nonEmpty then versionIndex = clamp(versionIndex + delta, versions.size)
    case ExplorerStep.BuildTool =>
      val snippets = buildSnippets
      if snippets.nonEmpty then snippetIndex = clamp(snippetIndex + delta, snippets.size)

  private def runSearch(): Unit =
    searchState = LoadState.Loading
    versionState = LoadState.Idle
    message = "Searching Sonatype Central..."
    client.search(query) match
      case Right(results) =>
        artifacts = results
        artifactIndex = 0
        resetVersionSelection()
        searchState = LoadState.Ready
        if results.isEmpty then message = "No artifacts found. Edit the query and press Enter."
        else
          step = ExplorerStep.Artifacts
          message = s"Found ${results.size} artifacts. Press Enter to inspect versions."
      case Left(error)    =>
        artifacts = Vector.empty
        resetVersionSelection()
        searchState = LoadState.Failed
        message = s"Search failed: $error"

  private def loadVersionsForSelection(): Unit =
    selectedArtifact match
      case Some(artifact) =>
        versionState = LoadState.Loading
        message = s"Loading versions for ${artifact.artifact}..."
        client.versions(artifact) match
          case Right(values) =>
            versions = values
            versionIndex = 0
            snippetIndex = 0
            versionState = LoadState.Ready
            if values.isEmpty then message = "No versions returned for this artifact."
            else
              step = ExplorerStep.Versions
              message = s"Loaded ${values.size} versions. Press Enter to choose a build tool."
          case Left(error)   =>
            resetVersionSelection()
            versionState = LoadState.Failed
            message = s"Version lookup failed: $error"
      case None           => message = "Select an artifact first."

  private def openBuildToolStep(): Unit =
    if versions.nonEmpty then
      step = ExplorerStep.BuildTool
      snippetIndex = 0
      message = "Choose a build-tool coordinate and press Enter to copy it."
    else loadVersionsForSelection()

  private def copySelectedSnippet(): Unit =
    buildSnippets.lift(snippetIndex).foreach { snippet =>
      clipboardAttempts += 1
      selectedSnippet = Some(snippet)
      TerminalClipboard.copy(tui.terminal, snippet.value) match
        case Right(())   =>
          message = s"Copied ${snippet.label} coordinate to the terminal clipboard."
        case Left(error) =>
          message = s"Selected ${snippet.label}, but copy failed: $error"
    }

  private def resetVersionSelection(): Unit =
    versions = Vector.empty
    versionIndex = 0
    snippetIndex = 0
    versionState = LoadState.Idle
    selectedSnippet = None

  // ----- Rendering -----

  private def fullRender(width: Int): Vector[String] =
    val paneInner       = math.max(0, width - 7)
    val leftWidth       = paneInner / 2
    val rightWidth      = paneInner - leftWidth
    val leftRows        = artifactRows(leftWidth)
    val rightRows       = detailRows(rightWidth)
    val contentRowCount = math.max(leftRows.size, rightRows.size).max(9)
    val lines           = Vector.newBuilder[String]

    lines += border("┌", "─", "┐", width)
    lines += framed("Sonatype Central Telescope — alternate screen demo", width)
    lines += framed(headerText, width)
    lines += border("├", "─", "┤", width)
    lines += twoPane("Artifacts", "Versions and build snippets", leftWidth, rightWidth)

    (0 until contentRowCount).foreach { index =>
      lines += twoPane(
        leftRows.lift(index).getOrElse(""),
        rightRows.lift(index).getOrElse(""),
        leftWidth,
        rightWidth
      )
    }

    lines += border("├", "─", "┤", width)
    selectedSnippet.foreach(snippet => lines += framed(s"Selected: ${snippet.value}", width))
    lines += framed(message, width)
    lines += framed(helpText, width)
    lines += border("└", "─", "┘", width)
    lines.result()

  private def compactRender(width: Int): Vector[String] =
    Vector(
      fit("Sonatype Central Telescope", width),
      fit(s"Search: $query", width),
      fit(message, width),
      fit("Use a wider terminal.", width)
    )

  private def artifactRows(width: Int): Vector[String] =
    if artifacts.isEmpty then emptyArtifactRows
    else
      artifacts.zipWithIndex.map { case (artifact, index) =>
        val marker = if step === ExplorerStep.Artifacts && index === artifactIndex then "›" else " "
        val date   = artifact.latestPublished.fold("")(published => s"  $published")
        fit(s"$marker ${artifact.module}  ${artifact.latestVersion}$date", width)
      }

  private def emptyArtifactRows: Vector[String] = searchState match
    case LoadState.Idle    => Vector("Default query: siglyph", "Type another query or press Enter.")
    case LoadState.Loading => Vector("Searching...")
    case LoadState.Failed  => Vector("Search failed.")
    case LoadState.Ready   => Vector("No results.")

  private def detailRows(width: Int): Vector[String] = selectedArtifact match
    case None           => Vector("Search first, then select an artifact.")
    case Some(artifact) =>
      artifactSummary(artifact, width) ++ versionRows(width) ++ snippetRows(width)

  private def artifactSummary(artifact: Artifact, width: Int): Vector[String] =
    Vector(
      fit(s"Group:    ${artifact.group}", width),
      fit(s"Artifact: ${artifact.artifact}", width),
      fit(
        s"Latest:   ${artifact.latestVersion}${artifact.latestPublished.fold("")(d => s"  $d")}",
        width
      ),
      fit(s"Package:  ${artifact.packaging}", width),
      ""
    )

  private def versionRows(width: Int): Vector[String] = versionState match
    case LoadState.Idle    => Vector("Press Enter to load versions.")
    case LoadState.Loading => Vector("Loading versions and dates...")
    case LoadState.Failed  => Vector("Version lookup failed.")
    case LoadState.Ready   =>
      if versions.isEmpty then Vector("No versions returned.")
      else
        Vector("Versions:") ++ versions.take(7).zipWithIndex.map { case (version, index) =>
          val marker = if step === ExplorerStep.Versions && index === versionIndex then "›" else " "
          fit(s"$marker ${version.label}", width)
        }

  private def snippetRows(width: Int): Vector[String] =
    if step !== ExplorerStep.BuildTool then Vector.empty
    else
      Vector("", "Build tool:") ++ buildSnippets.zipWithIndex.map { case (snippet, index) =>
        val marker = if index === snippetIndex then "›" else " "
        fit(s"$marker ${snippet.label}: ${snippet.value}", width)
      }

  // ----- Derived state -----

  private def selectedArtifact: Option[Artifact] = artifacts.lift(artifactIndex)

  private def selectedVersion: String =
    versions.lift(
      versionIndex
    ).map(_.version).orElse(selectedArtifact.map(_.latestVersion)).getOrElse("")

  private def buildSnippets: Vector[BuildSnippet] = selectedArtifact match
    case None           => Vector.empty
    case Some(artifact) =>
      val version = selectedVersion
      val module  = artifact.module
      Vector(
        BuildSnippet("Scala CLI", s"//> using dep $module:$version"),
        BuildSnippet("SBT", s"\"${artifact.group}\" % \"${artifact.artifact}\" % \"$version\""),
        BuildSnippet("Mill", s"mvn\"${artifact.group}:${artifact.artifact}:$version\""),
        BuildSnippet("Maven", s"${artifact.group}:${artifact.artifact}:$version")
      )

  private def headerText: String =
    s"Step: ${stepLabel}    Search: $query${if step === ExplorerStep.Search then "_" else ""}"

  private def stepLabel: String = step match
    case ExplorerStep.Search    => "1 Search"
    case ExplorerStep.Artifacts => "2 Artifact"
    case ExplorerStep.Versions  => "3 Version"
    case ExplorerStep.BuildTool => "4 Build tool"

  private def helpText: String = step match
    case ExplorerStep.Search    => "Type query · Enter search · Esc exit"
    case ExplorerStep.Artifacts => "↑/↓ artifact · Enter versions · ← search · Esc exit"
    case ExplorerStep.Versions  => "↑/↓ version · Enter build tool · ← artifacts · Esc exit"
    case ExplorerStep.BuildTool => "↑/↓ build tool · Enter copy · ← versions · Esc exit"

  // ----- Text layout helpers -----

  private def twoPane(left: String, right: String, leftWidth: Int, rightWidth: Int): String =
    s"│ ${fit(left, leftWidth)} │ ${fit(right, rightWidth)} │"

  private def framed(text: String, width: Int): String =
    if width < 4 then fit(text, width) else s"│ ${fit(text, width - 4)} │"

  private def border(left: String, fill: String, right: String, width: Int): String =
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
