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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import scalatui.ansi.Ansi
import scalatui.components.{Input, Loader, LoaderOptions, SelectItem, SelectList, SelectListOptions}
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
    publishedEpochMillis: Long = 0L,
    url: Option[String] = None,
    scmUrl: Option[String] = None
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

final case class ArtifactMetadata(homepage: Option[String], scm: Option[String]) derives CanEqual:
  def homepageLink: Option[String] = homepage.orElse(scm)

object ArtifactMetadata:
  val Empty: ArtifactMetadata = ArtifactMetadata(None, None)

final case class VersionLookup(versions: Vector[VersionInfo], metadata: ArtifactMetadata)
    derives CanEqual

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

  def versions(artifact: Artifact): Either[String, VersionLookup] =
    val body =
      s"""{"sortField":"normalizedVersion","sortDirection":"desc","filter":[${jsonString(
          s"namespace:${artifact.group}"
        )},${jsonString(s"name:${artifact.artifact}")}]}"""
    post[Vector[String]](SonatypeCentralClient.VersionsUrl, body)
      .map { values =>
        val latestVersion  = artifact.latestVersion
        val latestDetails  = componentDetails(artifact, latestVersion).toOption
        val versionDetails = values.filter(_.nonEmpty).take(12).map { version =>
          val details =
            if version === latestVersion then latestDetails
            else componentDetails(artifact, version).toOption
          VersionInfo(version, details.flatMap(details => formatDate(details.publishedEpochMillis)))
        }
        VersionLookup(
          versions = versionDetails,
          metadata = latestDetails.map(metadataFromDetails).getOrElse(ArtifactMetadata.Empty)
        )
      }

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

  private def metadataFromDetails(details: SonatypeComponentDetails): ArtifactMetadata =
    ArtifactMetadata(
      homepage = details.url.filter(_.nonEmpty),
      scm = details.scmUrl.filter(_.nonEmpty)
    )

  private def componentDetails(
      artifact: Artifact,
      version: String
  ): Either[String, SonatypeComponentDetails] =
    val entity = s"pkg:maven/${artifact.group}/${artifact.artifact}@$version"
    val url    = SonatypeCentralClient.DetailsUrl + "?id=" + URLEncoder.encode(
      entity,
      StandardCharsets.UTF_8
    )
    get[SonatypeComponentDetails](url)

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

final class MavenExplorer(tui: TUI, executor: ScheduledExecutorService) extends Component:
  private val client     = SonatypeCentralClient()
  private val stateLock  = Object()
  private val loader     =
    Loader(LoaderOptions(message = "Loading...", leadingBlankLine = false, paddingX = 0))
  private var loaderTick = Option.empty[ScheduledFuture[?]]

  private val searchInput = Input("siglyph")
  searchInput.focused = true
  searchInput.onSubmit = _ => runSearch()

  private var step               = ExplorerStep.Search
  private var artifacts          = Vector.empty[Artifact]
  private var versions           = Vector.empty[VersionInfo]
  private var artifactList       = Option.empty[SelectList]
  private var versionList        = Option.empty[SelectList]
  private var buildToolList      = Option.empty[SelectList]
  private var artifactMetadata   = ArtifactMetadata.Empty
  private var searchState        = LoadState.Idle
  private var versionState       = LoadState.Idle
  private var message            = "Press Enter to search Sonatype Central. Esc exits cleanly."
  private var copiedSnippetIndex = Option.empty[Int]

  override def render(width: Int): Vector[String] = stateLock.synchronized {
    val w = math.max(1, width)
    if w < 32 then compactRender(w)
    else fullRender(w)
  }

  override def handleInputResult(input: TerminalInput): InputResult = stateLock.synchronized {
    input match
      case TerminalInput.Key(TerminalKey.Character("c"), modifiers) if modifiers.ctrl =>
        InputResult.Exit
      case TerminalInput.Key(TerminalKey.Escape, _)                                   =>
        InputResult.Exit
      case TerminalInput.Key(TerminalKey.Tab, modifiers) if modifiers.shift           =>
        goBack()
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Tab, modifiers) if modifiers.isEmpty         =>
        advanceStep()
        InputResult.Render
      case _ if step === ExplorerStep.Search                                          =>
        val submitting = input match
          case TerminalInput.Key(TerminalKey.Enter, _) => true
          case _                                       => false
        val result     = searchInput.handleInputResult(input)
        if !submitting && (searchState !== LoadState.Loading) then
          message = "Press Enter to search Sonatype Central."
        result
      case _                                                                          =>
        activeSelectList.foreach(_.handleInput(input))
        InputResult.Render
  }

  private def activeSelectList: Option[SelectList] = step match
    case ExplorerStep.Search    => None
    case ExplorerStep.Artifacts => artifactList
    case ExplorerStep.Versions  => versionList
    case ExplorerStep.BuildTool => buildToolList

  private def advanceStep(): Unit = step match
    case ExplorerStep.Search    => runSearch()
    case ExplorerStep.Artifacts => loadVersionsForSelection()
    case ExplorerStep.Versions  => openBuildToolStep()
    case ExplorerStep.BuildTool => copySelectedSnippet()

  private def goBack(): Unit = step match
    case ExplorerStep.Search    => ()
    case ExplorerStep.Artifacts => enterStep(ExplorerStep.Search)
    case ExplorerStep.Versions  => enterStep(ExplorerStep.Artifacts)
    case ExplorerStep.BuildTool => enterStep(ExplorerStep.Versions)

  private def enterStep(nextStep: ExplorerStep): Unit =
    step = nextStep
    searchInput.focused = nextStep === ExplorerStep.Search

  private def runSearch(): Unit =
    val requestedQuery = searchInput.value
    searchState = LoadState.Loading
    versionState = LoadState.Idle
    message = "Searching Sonatype Central..."
    startLoading("Searching Sonatype Central...")
    runAsync(client.search(requestedQuery)) {
      case Right(results) =>
        artifacts = results
        updateArtifactList()
        resetVersionSelection()
        searchState = LoadState.Ready
        if results.isEmpty then message = "No artifacts found. Edit the query and press Enter."
        else
          enterStep(ExplorerStep.Artifacts)
          message = s"Found ${results.size} artifacts. Press Enter to inspect versions."
      case Left(error)    =>
        artifacts = Vector.empty
        artifactList = None
        resetVersionSelection()
        searchState = LoadState.Failed
        message = s"Search failed: $error"
    }

  private def loadVersionsForSelection(): Unit =
    selectedArtifact match
      case Some(artifact) =>
        versionState = LoadState.Loading
        message = s"Loading versions for ${artifact.artifact}..."
        startLoading("Loading versions and dates...")
        runAsync(client.versions(artifact)) {
          case Right(lookup) =>
            versions = lookup.versions
            artifactMetadata = lookup.metadata
            updateVersionList()
            buildToolList = None
            copiedSnippetIndex = None
            versionState = LoadState.Ready
            if lookup.versions.isEmpty then message = "No versions returned for this artifact."
            else
              enterStep(ExplorerStep.Versions)
              message =
                s"Loaded ${lookup.versions.size} versions. Press Enter to choose a build tool."
          case Left(error)   =>
            resetVersionSelection()
            versionState = LoadState.Failed
            message = s"Version lookup failed: $error"
        }
      case None           => message = "Select an artifact first."

  private def runAsync[A](work: => A)(complete: A => Unit): Unit =
    executor.execute { () =>
      val result = work
      stateLock.synchronized {
        stopLoading()
        complete(result)
      }
      tui.requestRender()
      tui.flushRender()
    }

  private def startLoading(text: String): Unit =
    loader.setMessage(text)
    loader.start()
    loaderTick.foreach(_.cancel(false))
    loaderTick = Some(executor.scheduleAtFixedRate(
      () => tickLoaderIfLoading(),
      loader.intervalMs.toLong,
      loader.intervalMs.toLong,
      TimeUnit.MILLISECONDS
    ))

  private def stopLoading(): Unit =
    loaderTick.foreach(_.cancel(false))
    loaderTick = None
    loader.stop()

  private def tickLoaderIfLoading(): Unit =
    val shouldRender = stateLock.synchronized {
      if searchState === LoadState.Loading || versionState === LoadState.Loading then loader.tick()
      else
        stopLoading()
        false
    }
    if shouldRender then
      tui.requestRender()
      tui.flushRender()

  private def openBuildToolStep(): Unit =
    if versions.nonEmpty then
      copiedSnippetIndex = None
      updateBuildToolList(selectedIndex = 0)
      enterStep(ExplorerStep.BuildTool)
      message = "Choose a build-tool coordinate and press Enter to copy it."
    else loadVersionsForSelection()

  private def copySelectedSnippet(): Unit =
    val index = selectedIndex(buildToolList)
    buildSnippets.lift(index).foreach { snippet =>
      TerminalClipboard.copy(tui.terminal, snippet.value) match
        case Right(())   =>
          copiedSnippetIndex = Some(index)
          updateBuildToolList(selectedIndex = index)
          message = "Esc exits and restores the normal screen."
        case Left(error) =>
          message = s"Selected ${snippet.label}, but copy failed: $error"
    }

  private def resetVersionSelection(): Unit =
    versions = Vector.empty
    versionList = None
    buildToolList = None
    artifactMetadata = ArtifactMetadata.Empty
    versionState = LoadState.Idle
    copiedSnippetIndex = None

  private def updateArtifactList(): Unit =
    artifactList =
      if artifacts.isEmpty then None
      else
        val list = SelectList(
          artifacts.zipWithIndex.map { case (artifact, index) =>
            val date        = artifact.latestPublished.fold("")(published => s"  $published")
            val description = Option.when(artifact.description.nonEmpty)(artifact.description)
            SelectItem(
              value = index.toString,
              label = s"${artifact.module}  ${artifact.latestVersion}$date",
              description = description
            )
          },
          selectOptions(maxVisible = 20, showDescriptions = true)
        )
        list.onSelect = _ => loadVersionsForSelection()
        list.onSelectionChange = _ =>
          resetVersionSelection()
          message = "Press Enter to inspect versions for the selected artifact."
        Some(list)

  private def updateVersionList(): Unit =
    versionList =
      if versions.isEmpty then None
      else
        val list = SelectList(
          versions.zipWithIndex.map { case (version, index) =>
            SelectItem(value = index.toString, label = version.label)
          },
          selectOptions(maxVisible = 8, showDescriptions = false)
        )
        list.onSelect = _ => openBuildToolStep()
        list.onSelectionChange = _ =>
          buildToolList = None
          copiedSnippetIndex = None
        Some(list)

  private def updateBuildToolList(selectedIndex: Int): Unit =
    val snippets = buildSnippets
    buildToolList =
      if snippets.isEmpty then None
      else
        val list = SelectList(
          snippets.zipWithIndex.map { case (snippet, index) =>
            val copied = copiedSnippetIndex.contains(index)
            val badge  = if copied then "  [32mCopied![0m" else ""
            SelectItem(value = index.toString, label = s"${snippet.label}: ${snippet.value}$badge")
          },
          selectOptions(maxVisible = snippets.size, showDescriptions = false)
        )
        list.moveSelectionBy(selectedIndex)
        list.onSelect = _ => copySelectedSnippet()
        list.onSelectionChange = _ => copiedSnippetIndex = None
        Some(list)

  private def selectOptions(maxVisible: Int, showDescriptions: Boolean): SelectListOptions =
    SelectListOptions(
      maxVisible = math.max(1, maxVisible),
      selectedPrefix = "› ",
      normalPrefix = "  ",
      showDescriptions = showDescriptions,
      showScrollInfo = true,
      noMatchText = "No items"
    )

  // ----- Rendering -----

  private def fullRender(width: Int): Vector[String] =
    val paneInner       = math.max(0, width - 7)
    val leftWidth       = paneInner / 2
    val rightWidth      = paneInner - leftWidth
    val leftRows        = artifactRows(leftWidth)
    val rightRows       = detailRows(rightWidth)
    val targetRows      = math.max(1, tui.terminal.rows)
    val contentRowCount = mainPaneRows(targetRows, math.max(leftRows.size, rightRows.size))
    val lines           = Vector.newBuilder[String]

    lines += border("┌", "─", "┐", width)
    lines += framed("Sonatype Central Telescope — alternate screen demo", width)
    lines += framed(headerText(width - 4), width)
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
    lines += framed(message, width)
    lines += framed(helpText, width)
    lines += border("└", "─", "┘", width)
    lines.result()

  private def mainPaneRows(targetRows: Int, naturalRows: Int): Int =
    val fixedRows = 9
    val fillRows  = math.max(1, targetRows - fixedRows)
    fillRows

  private def compactRender(width: Int): Vector[String] =
    Vector(
      fit("Sonatype Central Telescope", width),
      fit(headerText(width), width),
      fit(message, width),
      fit("Use a wider terminal.", width)
    )

  private def artifactRows(width: Int): Vector[String] =
    if artifacts.isEmpty then emptyArtifactRows(width)
    else artifactList.fold(Vector.empty[String])(_.render(width))

  private def emptyArtifactRows(width: Int): Vector[String] = searchState match
    case LoadState.Idle    =>
      Vector("Default query: siglyph", "Type or paste another query, then press Enter.")
    case LoadState.Loading => loadingRows("Searching Sonatype Central...", width)
    case LoadState.Failed  => Vector("Search failed.")
    case LoadState.Ready   => Vector("No results.")

  private def loadingRows(text: String, width: Int): Vector[String] =
    if loader.message !== text then loader.setMessage(text)
    loader.render(width).map(line => fit(line, width))

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
      fit(s"Package:  ${artifact.packaging}", width)
    ) ++ artifactMetadata.homepageLink.map(link => fit(s"Homepage: $link", width)).toVector ++
      Vector("")

  private def versionRows(width: Int): Vector[String] = versionState match
    case LoadState.Idle    => Vector("Press Enter to load versions.")
    case LoadState.Loading => loadingRows("Loading versions and dates...", width)
    case LoadState.Failed  => Vector("Version lookup failed.")
    case LoadState.Ready   =>
      if versions.isEmpty then Vector("No versions returned.")
      else Vector("Versions:") ++ versionList.fold(Vector.empty[String])(_.render(width))

  private def snippetRows(width: Int): Vector[String] =
    if step !== ExplorerStep.BuildTool then Vector.empty
    else Vector("", "Build tool:") ++ buildToolList.fold(Vector.empty[String])(_.render(width))

  // ----- Derived state -----

  private def selectedArtifact: Option[Artifact] =
    selectedIndexOption(artifactList).flatMap(artifacts.lift)

  private def selectedVersion: String =
    selectedIndexOption(versionList)
      .flatMap(versions.lift)
      .map(_.version)
      .orElse(selectedArtifact.map(_.latestVersion))
      .getOrElse("")

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

  private def headerText(width: Int): String = step match
    case ExplorerStep.Search =>
      val label      = "Search: "
      val inputWidth = math.max(1, width - Ansi.visibleWidth(label))
      label + searchInput.render(inputWidth).headOption.getOrElse("")
    case _                   => s"${stepLabel} · Query: ${searchInput.value}"

  private def stepLabel: String = step match
    case ExplorerStep.Search    => "1 Search"
    case ExplorerStep.Artifacts => "2 Artifact"
    case ExplorerStep.Versions  => "3 Version"
    case ExplorerStep.BuildTool => "4 Build tool"

  private def helpText: String = step match
    case ExplorerStep.Search    => "Type/paste query · Enter/Tab search · Esc exit"
    case ExplorerStep.Artifacts => "↑/↓ artifact · Enter/Tab versions · Shift+Tab search · Esc exit"
    case ExplorerStep.Versions  =>
      "↑/↓ version · Enter/Tab build tool · Shift+Tab artifacts · Esc exit"
    case ExplorerStep.BuildTool => "↑/↓ build tool · Enter/Tab copy · Shift+Tab versions · Esc exit"

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

  private def selectedIndex(list: Option[SelectList]): Int =
    selectedIndexOption(list).getOrElse(0)

  private def selectedIndexOption(list: Option[SelectList]): Option[Int] =
    list.flatMap(_.selected.flatMap(item => item.value.toIntOption))

@main def mavenCentralTelescope(): Unit =
  val executor = Executors.newScheduledThreadPool(
    2,
    { runnable =>
      val thread = Thread(runnable, "siglyph-sonatype-demo")
      thread.setDaemon(true)
      thread
    }
  )
  val tui      = TUI(SttyTerminal(), TUIOptions(screenMode = TUIScreenMode.Alternate))
  val explorer = MavenExplorer(tui, executor)
  tui.addChild(explorer)
  tui.setFocus(explorer)
  tui.exitsOnEscape = true
  try tui.run()
  finally executor.shutdownNow()
