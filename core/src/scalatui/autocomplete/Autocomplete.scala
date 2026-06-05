package scalatui.autocomplete

import scalatui.editing.EditorCursor
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

/** Selectable autocomplete item rendered by editor suggestion UI. */
final case class AutocompleteItem(
    value: String,
    label: String,
    description: Option[String] = None
) derives CanEqual

/** Snapshot of editor state used for an autocomplete lookup. */
final case class AutocompleteRequest(
    lines: Vector[String],
    cursor: EditorCursor,
    force: Boolean = false
) derives CanEqual:
  def text: String = lines.mkString("\n")

/** Suggestions returned for a matched prefix at the request cursor. */
final case class AutocompleteSuggestions(
    items: Vector[AutocompleteItem],
    prefix: String
) derives CanEqual

/** Request to apply a selected completion to a previous editor snapshot. */
final case class CompletionRequest(
    lines: Vector[String],
    cursor: EditorCursor,
    item: AutocompleteItem,
    prefix: String
) derives CanEqual

/** Resulting editor state after applying a completion. */
final case class CompletionResult(lines: Vector[String], cursor: EditorCursor) derives CanEqual

/** Cancellable handle for an in-flight autocomplete lookup. */
trait AutocompleteRequestHandle:
  def cancel(): Unit

object AutocompleteRequestHandle:
  /** Handle for providers that complete synchronously or cannot cancel underlying work. */
  val Noop: AutocompleteRequestHandle = () => ()

/** Callback invoked by autocomplete providers when lookup finishes or fails. */
trait AutocompleteCallback:
  def complete(result: Option[AutocompleteSuggestions]): Unit
  def fail(error: Throwable): Unit

/** Autocomplete provider contract with an async-capable cancellable callback boundary. */
trait AutocompleteProvider:
  def requestSuggestions(
      request: AutocompleteRequest,
      callback: AutocompleteCallback
  ): AutocompleteRequestHandle

  def applyCompletion(request: CompletionRequest): CompletionResult

object AutocompleteProvider:
  /** Build a provider whose suggestion lookup completes during the request call. */
  def sync(
      suggestions: AutocompleteRequest => Option[AutocompleteSuggestions],
      complete: CompletionRequest => CompletionResult = AutocompleteProvider.defaultCompletion
  ): AutocompleteProvider = new AutocompleteProvider:
    override def requestSuggestions(
        request: AutocompleteRequest,
        callback: AutocompleteCallback
    ): AutocompleteRequestHandle =
      try callback.complete(suggestions(request))
      catch case e: Throwable => callback.fail(e)
      AutocompleteRequestHandle.Noop

    override def applyCompletion(request: CompletionRequest): CompletionResult = complete(request)

  /** Replace the matched prefix before the cursor with the selected value. */
  def defaultCompletion(request: CompletionRequest): CompletionResult =
    val line          = request.lines.lift(request.cursor.line).getOrElse("")
    val clusters      = Unicode.graphemeClusters(line)
    val prefixColumns = Unicode.graphemeClusters(request.prefix).length
    val prefixStart   = math.max(0, request.cursor.column - prefixColumns)
    val beforePrefix  = clusters.take(prefixStart).mkString
    val afterCursor   = clusters.drop(request.cursor.column).mkString
    val inserted      = completionText(request)
    val newLine       = beforePrefix + inserted + afterCursor
    val newLines      = request.lines.updated(request.cursor.line, newLine)
    val cursorColumn  = prefixStart + Unicode.graphemeClusters(inserted).length
    CompletionResult(newLines, EditorCursor(request.cursor.line, cursorColumn))

  private def completionText(request: CompletionRequest): String =
    if isSlashCommandPrefix(request.prefix) then s"/${request.item.value} " else request.item.value

  private def isSlashCommandPrefix(prefix: String): Boolean =
    prefix.startsWith("/") && !prefix.drop(1).contains("/")

/** Application-supplied slash command metadata used by autocomplete helpers. */
final case class SlashCommand(
    name: String,
    description: Option[String] = None,
    argumentHint: Option[String] = None,
    argumentCompletions: String => Option[Vector[AutocompleteItem]] = _ => None
)

/** Dependency-free slash-command autocomplete provider helper. */
final class SlashCommandAutocompleteProvider(commands: Vector[SlashCommand])
    extends AutocompleteProvider:
  override def requestSuggestions(
      request: AutocompleteRequest,
      callback: AutocompleteCallback
  ): AutocompleteRequestHandle =
    try callback.complete(suggestionsFor(request))
    catch case e: Throwable => callback.fail(e)
    AutocompleteRequestHandle.Noop

  override def applyCompletion(request: CompletionRequest): CompletionResult =
    AutocompleteProvider.defaultCompletion(request)

  private def suggestionsFor(request: AutocompleteRequest): Option[AutocompleteSuggestions] =
    val line         = request.lines.lift(request.cursor.line).getOrElse("")
    val beforeCursor = Unicode.graphemeClusters(line).take(request.cursor.column).mkString
    if !beforeCursor.startsWith("/") then None
    else
      val spaceIndex = beforeCursor.indexOf(' ')
      if spaceIndex < 0 then commandSuggestions(beforeCursor)
      else argumentSuggestions(beforeCursor, spaceIndex)

  private def commandSuggestions(prefixWithSlash: String): Option[AutocompleteSuggestions] =
    val prefix = prefixWithSlash.drop(1)
    val items  = commands
      .filter(command => command.name.startsWith(prefix))
      .map { command =>
        val hint        = command.argumentHint
        val description = (hint, command.description) match
          case (Some(h), Some(d)) => Some(s"$h — $d")
          case (Some(h), None)    => Some(h)
          case (None, d)          => d
        AutocompleteItem(command.name, command.name, description)
      }
    Option.when(items.nonEmpty)(AutocompleteSuggestions(items, prefixWithSlash))

  private def argumentSuggestions(
      beforeCursor: String,
      spaceIndex: Int
  ): Option[AutocompleteSuggestions] =
    val commandName = beforeCursor.slice(1, spaceIndex)
    val argument    = beforeCursor.drop(spaceIndex + 1)
    commands.find(
      _.name === commandName
    ).flatMap(_.argumentCompletions(argument)).filter(_.nonEmpty).map { items =>
      AutocompleteSuggestions(items, argument)
    }
