package scalatui.autocomplete

import scalatui.editing.EditorCursor
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode

/** Selectable autocomplete item rendered by editor suggestion UI. */
final case class AutocompleteItem(
    value: String,
    label: String,
    description: Option[String] = None,
    kind: AutocompleteItemKind = AutocompleteItemKind.Generic
) derives CanEqual

/** Origin/type metadata used by composed providers to apply completions correctly. */
enum AutocompleteItemKind derives CanEqual:
  case Generic, SlashCommand, FilePath, Attachment

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

  private def completionText(request: CompletionRequest): String = request.item.kind match
    case AutocompleteItemKind.SlashCommand => s"/${request.item.value} "
    case _                                 => request.item.value

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
        AutocompleteItem(command.name, command.name, description, AutocompleteItemKind.SlashCommand)
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

/** Parsed active path token in an autocomplete cursor context. */
final case class PathCompletionPrefix(
    prefix: String,
    rawPrefix: String,
    isAttachment: Boolean,
    isQuoted: Boolean
) derives CanEqual

/** Path candidate before prefix quoting/attachment syntax is reapplied. */
final case class PathCompletion(
    path: String,
    label: String,
    isDirectory: Boolean = false,
    description: Option[String] = None
) derives CanEqual

/** Request passed to path/attachment completion sources. */
final case class PathCompletionRequest(
    prefix: PathCompletionPrefix,
    force: Boolean
) derives CanEqual

/** Dependency-free async-capable path completion source. */
trait PathCompletionProvider:
  def requestPathSuggestions(
      request: PathCompletionRequest,
      callback: PathCompletionProvider.Callback
  ): AutocompleteRequestHandle

object PathCompletionProvider:
  trait Callback:
    def complete(result: Vector[PathCompletion]): Unit
    def fail(error: Throwable): Unit

  def sync(suggestions: PathCompletionRequest => Vector[PathCompletion]): PathCompletionProvider =
    new PathCompletionProvider:
      override def requestPathSuggestions(
          request: PathCompletionRequest,
          callback: Callback
      ): AutocompleteRequestHandle =
        try callback.complete(suggestions(request))
        catch case e: Throwable => callback.fail(e)
        AutocompleteRequestHandle.Noop

/** Deterministic cursor-context parser for path and attachment completion prefixes. */
object CompletionPrefixParser:
  private val TokenDelimiters = Set(' ', '\t', '=', ',', ';', '(', ')', '[', ']', '{', '}')

  def parsePathPrefix(request: AutocompleteRequest): Option[PathCompletionPrefix] =
    val line         = request.lines.lift(request.cursor.line).getOrElse("")
    val beforeCursor = Unicode.graphemeClusters(line).take(request.cursor.column).mkString
    parsePathPrefix(beforeCursor, request.force)

  def parsePathPrefix(beforeCursor: String, force: Boolean): Option[PathCompletionPrefix] =
    extractQuotedPrefix(beforeCursor).orElse {
      val start = lastTokenStart(beforeCursor)
      val token = beforeCursor.substring(start)
      Option.when(shouldUseToken(token, beforeCursor, force))(prefixFromToken(token))
    }

  private def extractQuotedPrefix(text: String): Option[PathCompletionPrefix] =
    findUnclosedQuoteStart(text).flatMap { quoteStart =>
      if quoteStart > 0 && text.charAt(quoteStart - 1) === '@' && isTokenStart(text, quoteStart - 1)
      then Some(prefixFromToken(text.substring(quoteStart - 1)))
      else if isTokenStart(text, quoteStart) then Some(prefixFromToken(text.substring(quoteStart)))
      else None
    }

  private def findUnclosedQuoteStart(text: String): Option[Int] =
    var inQuotes = false
    var quote    = -1
    var i        = 0
    while i < text.length do
      if text.charAt(i) === '"' then
        inQuotes = !inQuotes
        if inQuotes then quote = i
      i += 1
    Option.when(inQuotes)(quote)

  private def lastTokenStart(text: String): Int =
    var i = text.length - 1
    while i >= 0 && !TokenDelimiters.contains(text.charAt(i)) do i -= 1
    i + 1

  private def isTokenStart(text: String, index: Int): Boolean =
    index === 0 || TokenDelimiters.contains(text.charAt(index - 1))

  private def shouldUseToken(token: String, fullText: String, force: Boolean): Boolean =
    if force then true
    else
      token.startsWith("@") ||
      token.startsWith("/") ||
      token.startsWith("./") ||
      token.startsWith("../") ||
      token.startsWith("~/") ||
      token.contains("/") ||
      (token.isEmpty && fullText.nonEmpty && TokenDelimiters.contains(fullText.last))

  private def prefixFromToken(token: String): PathCompletionPrefix =
    if token.startsWith("@\"") then
      PathCompletionPrefix(token, token.drop(2), isAttachment = true, isQuoted = true)
    else if token.startsWith("\"") then
      PathCompletionPrefix(token, token.drop(1), isAttachment = false, isQuoted = true)
    else if token.startsWith("@") then
      PathCompletionPrefix(token, token.drop(1), isAttachment = true, isQuoted = false)
    else PathCompletionPrefix(token, token, isAttachment = false, isQuoted = false)

/** Provider that composes slash-command and path/attachment completions. */
final class CombinedAutocompleteProvider(
    commands: Vector[SlashCommand] = Vector.empty,
    pathProvider: Option[PathCompletionProvider] = None
) extends AutocompleteProvider:
  private val slashProvider = SlashCommandAutocompleteProvider(commands)

  override def requestSuggestions(
      request: AutocompleteRequest,
      callback: AutocompleteCallback
  ): AutocompleteRequestHandle =
    val slash      = slashSuggestions(request)
    val pathPrefix = CompletionPrefixParser.parsePathPrefix(request)
    pathPrefix match
      case Some(prefix) if pathProvider.nonEmpty =>
        pathProvider.get.requestPathSuggestions(
          PathCompletionRequest(prefix, request.force),
          new PathCompletionProvider.Callback:
            override def complete(result: Vector[PathCompletion]): Unit =
              val pathItems = result.map(pathItem(_, prefix))
              callback.complete(combine(slash, pathItems, prefix.prefix))

            override def fail(error: Throwable): Unit = callback.fail(error)
        )
      case _                                     =>
        callback.complete(slash)
        AutocompleteRequestHandle.Noop

  override def applyCompletion(request: CompletionRequest): CompletionResult =
    AutocompleteProvider.defaultCompletion(request)

  private def slashSuggestions(request: AutocompleteRequest): Option[AutocompleteSuggestions] =
    var result = Option.empty[AutocompleteSuggestions]
    slashProvider.requestSuggestions(
      request,
      new AutocompleteCallback:
        override def complete(value: Option[AutocompleteSuggestions]): Unit = result = value
        override def fail(error: Throwable): Unit                           = throw error
    )
    result

  private def combine(
      slash: Option[AutocompleteSuggestions],
      pathItems: Vector[AutocompleteItem],
      pathPrefix: String
  ): Option[AutocompleteSuggestions] =
    (slash, pathItems.nonEmpty) match
      case (Some(suggestions), true) if suggestions.prefix === pathPrefix =>
        Some(AutocompleteSuggestions(suggestions.items ++ pathItems, suggestions.prefix))
      case (Some(suggestions), _)                                         => Some(suggestions)
      case (None, true)                                                   => Some(AutocompleteSuggestions(pathItems, pathPrefix))
      case (None, false)                                                  => None

  private def pathItem(completion: PathCompletion, prefix: PathCompletionPrefix): AutocompleteItem =
    val value = completionValue(completion.path, prefix)
    AutocompleteItem(
      value,
      completion.label,
      completion.description,
      if prefix.isAttachment then AutocompleteItemKind.Attachment else AutocompleteItemKind.FilePath
    )

  private def completionValue(path: String, prefix: PathCompletionPrefix): String =
    val needsQuotes = prefix.isQuoted || path.exists(_.isWhitespace)
    val marker      = if prefix.isAttachment then "@" else ""
    if needsQuotes then s"$marker\"$path\"" else s"$marker$path"
