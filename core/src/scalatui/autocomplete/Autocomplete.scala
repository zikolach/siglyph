package scalatui.autocomplete

import scalatui.editing.EditorCursor
import scalatui.matching.FuzzyMatcher
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

/** Optional fuzzy-ranking configuration for built-in autocomplete helpers. */
enum AutocompleteFuzzyRanking derives CanEqual:
  case Disabled, Enabled

  def rankItems(query: String, items: Vector[AutocompleteItem]): Vector[AutocompleteItem] =
    this match
      case AutocompleteFuzzyRanking.Disabled => items
      case AutocompleteFuzzyRanking.Enabled  =>
        if query.isEmpty then items
        else FuzzyMatcher.filter(query, items)(item => rankText(item)).map(_.item)

  def rankSlashCommands(query: String, commands: Vector[SlashCommand]): Vector[SlashCommand] =
    this match
      case AutocompleteFuzzyRanking.Disabled => commands.filter(_.name.startsWith(query))
      case AutocompleteFuzzyRanking.Enabled  =>
        FuzzyMatcher.filter(query, commands)(_.name).map(_.item)

  private def rankText(item: AutocompleteItem): String =
    if item.label.nonEmpty then item.label else item.value

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

/** Application-owned trigger autocomplete source such as `#` mentions or tags. */
final class TriggerCompletionSource private (
    val prefix: TriggerPrefix,
    val completions: String => Option[Vector[AutocompleteItem]]
)

object TriggerCompletionSource:
  def fromPrefix(
      prefix: String,
      completions: String => Option[Vector[AutocompleteItem]]
  ): Either[TriggerPrefixError, TriggerCompletionSource] =
    TriggerPrefix.from(prefix).map(validPrefix =>
      new TriggerCompletionSource(validPrefix, completions)
    )

/** Validated natural autocomplete trigger prefix. */
final class TriggerPrefix private (val value: String):
  override def equals(other: Any): Boolean = other match
    case that: TriggerPrefix => value === that.value
    case _                   => false

  override def hashCode(): Int  = value.hashCode
  override def toString: String = value

object TriggerPrefix:
  def from(value: String): Either[TriggerPrefixError, TriggerPrefix] =
    if value.isEmpty then Left(TriggerPrefixError.Empty)
    else if value.exists(_.isWhitespace) then Left(TriggerPrefixError.ContainsWhitespace)
    else Right(new TriggerPrefix(value))

/** Reason a natural autocomplete trigger prefix was rejected. */
enum TriggerPrefixError derives CanEqual:
  case Empty, ContainsWhitespace

  def message: String = this match
    case Empty              => "Trigger prefix must be non-empty"
    case ContainsWhitespace => "Trigger prefix must not contain whitespace"

/** Active trigger token parsed from the cursor context. */
final case class TriggerCompletionPrefix(
    replacementPrefix: String,
    query: String,
    source: TriggerCompletionSource
)

/** Dependency-free slash-command autocomplete provider helper. */
final class SlashCommandAutocompleteProvider(
    commands: Vector[SlashCommand],
    fuzzyRanking: AutocompleteFuzzyRanking = AutocompleteFuzzyRanking.Disabled
) extends AutocompleteProvider:
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
    val items  = fuzzyRanking.rankSlashCommands(prefix, commands)
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
    ).flatMap(_.argumentCompletions(argument)).map(fuzzyRanking.rankItems(argument, _)).filter(
      _.nonEmpty
    ).map { items =>
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

/** Deterministic cursor-context parser for trigger, path, and attachment prefixes. */
object CompletionPrefixParser:
  private val TokenDelimiters = Set(' ', '\t', '=', ',', ';', '(', ')', '[', ']', '{', '}')

  def parseTriggerPrefix(
      request: AutocompleteRequest,
      sources: Vector[TriggerCompletionSource]
  ): Option[TriggerCompletionPrefix] =
    val line         = request.lines.lift(request.cursor.line).getOrElse("")
    val beforeCursor = Unicode.graphemeClusters(line).take(request.cursor.column).mkString
    parseTriggerPrefix(beforeCursor, sources)

  def parseTriggerPrefix(
      beforeCursor: String,
      sources: Vector[TriggerCompletionSource]
  ): Option[TriggerCompletionPrefix] =
    if sources.isEmpty then None
    else
      val start                 = lastTokenStart(beforeCursor)
      val token                 = beforeCursor.substring(start)
      var longestMatchingSource = Option.empty[TriggerCompletionSource]
      sources.foreach { source =>
        if token.startsWith(
            source.prefix.value
          ) && longestMatchingSource.forall(
            _.prefix.value.length < source.prefix.value.length
          )
        then longestMatchingSource = Some(source)
      }
      longestMatchingSource.map(source =>
        TriggerCompletionPrefix(token, token.drop(source.prefix.value.length), source)
      )

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

/** Provider that composes slash-command, trigger, and path/attachment completions. */
final class CombinedAutocompleteProvider(
    commands: Vector[SlashCommand] = Vector.empty,
    pathProvider: Option[PathCompletionProvider] = None,
    triggerSources: Vector[TriggerCompletionSource] = Vector.empty,
    fuzzyRanking: AutocompleteFuzzyRanking = AutocompleteFuzzyRanking.Disabled
) extends AutocompleteProvider:
  private val slashProvider = SlashCommandAutocompleteProvider(commands, fuzzyRanking)

  override def requestSuggestions(
      request: AutocompleteRequest,
      callback: AutocompleteCallback
  ): AutocompleteRequestHandle =
    try
      val base       = slashSuggestions(request).orElse(triggerSuggestions(request))
      val pathPrefix = CompletionPrefixParser.parsePathPrefix(request)
      pathPrefix match
        case Some(prefix) if pathProvider.nonEmpty =>
          pathProvider.get.requestPathSuggestions(
            PathCompletionRequest(prefix, request.force),
            new PathCompletionProvider.Callback:
              override def complete(result: Vector[PathCompletion]): Unit =
                try
                  val pathItems = fuzzyRanking.rankItems(
                    pathFuzzyQuery(prefix),
                    result.map(pathItem(_, prefix))
                  )
                  callback.complete(combine(base, pathItems, prefix.prefix))
                catch case e: Throwable => callback.fail(e)

              override def fail(error: Throwable): Unit = callback.fail(error)
          )
        case _                                     =>
          callback.complete(base)
          AutocompleteRequestHandle.Noop
    catch
      case e: Throwable =>
        callback.fail(e)
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

  private def triggerSuggestions(request: AutocompleteRequest): Option[AutocompleteSuggestions] =
    CompletionPrefixParser.parseTriggerPrefix(request, triggerSources).flatMap { trigger =>
      trigger.source.completions(trigger.query).map(fuzzyRanking.rankItems(
        trigger.query,
        _
      )).filter(
        _.nonEmpty
      ).map { items =>
        AutocompleteSuggestions(items, trigger.replacementPrefix)
      }
    }

  private def combine(
      base: Option[AutocompleteSuggestions],
      pathItems: Vector[AutocompleteItem],
      pathPrefix: String
  ): Option[AutocompleteSuggestions] =
    (base, pathItems.nonEmpty) match
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

  private def pathFuzzyQuery(prefix: PathCompletionPrefix): String =
    val raw       = prefix.rawPrefix
    val lastSlash = raw.lastIndexOf('/')
    if lastSlash >= 0 then raw.drop(lastSlash + 1) else raw
