package scalatui.terminal

import scalatui.syntax.Equality.*

/**
 * Scopes for keybinding command IDs.
 */
enum KeybindingScope derives CanEqual:
  case Editor, Input, Select, Viewport

/**
 * Public backend-independent command identifiers shared by JVM and Scala Native.
 *
 * Viewport commands cover line, half-page, page, document edge, typed prompt, search, copy, and
 * clear-selection actions. A command with no default key remains registered for application
 * binding. Focused search, overlays, and components retain routing priority before primary
 * [[scalatui.components.ScrollView]] fallback.
 */
enum KeybindingCommand(val id: String, val scope: KeybindingScope) derives CanEqual:
  // Editor commands
  case EditorCursorUp    extends KeybindingCommand("tui.editor.cursorUp", KeybindingScope.Editor)
  case EditorCursorDown  extends KeybindingCommand("tui.editor.cursorDown", KeybindingScope.Editor)
  case EditorCursorLeft  extends KeybindingCommand("tui.editor.cursorLeft", KeybindingScope.Editor)
  case EditorCursorRight extends KeybindingCommand("tui.editor.cursorRight", KeybindingScope.Editor)
  case EditorCursorWordLeft
      extends KeybindingCommand("tui.editor.cursorWordLeft", KeybindingScope.Editor)
  case EditorCursorWordRight
      extends KeybindingCommand("tui.editor.cursorWordRight", KeybindingScope.Editor)
  case EditorCursorLineStart
      extends KeybindingCommand("tui.editor.cursorLineStart", KeybindingScope.Editor)
  case EditorCursorLineEnd
      extends KeybindingCommand("tui.editor.cursorLineEnd", KeybindingScope.Editor)
  case EditorJumpForward extends KeybindingCommand("tui.editor.jumpForward", KeybindingScope.Editor)
  case EditorJumpBackward
      extends KeybindingCommand("tui.editor.jumpBackward", KeybindingScope.Editor)
  case EditorPageUp      extends KeybindingCommand("tui.editor.pageUp", KeybindingScope.Editor)
  case EditorPageDown    extends KeybindingCommand("tui.editor.pageDown", KeybindingScope.Editor)
  case EditorDeleteCharBackward
      extends KeybindingCommand("tui.editor.deleteCharBackward", KeybindingScope.Editor)
  case EditorDeleteCharForward
      extends KeybindingCommand("tui.editor.deleteCharForward", KeybindingScope.Editor)
  case EditorDeleteWordBackward
      extends KeybindingCommand("tui.editor.deleteWordBackward", KeybindingScope.Editor)
  case EditorDeleteWordForward
      extends KeybindingCommand("tui.editor.deleteWordForward", KeybindingScope.Editor)
  case EditorDeleteToLineStart
      extends KeybindingCommand("tui.editor.deleteToLineStart", KeybindingScope.Editor)
  case EditorDeleteToLineEnd
      extends KeybindingCommand("tui.editor.deleteToLineEnd", KeybindingScope.Editor)
  case EditorYank        extends KeybindingCommand("tui.editor.yank", KeybindingScope.Editor)
  case EditorYankPop     extends KeybindingCommand("tui.editor.yankPop", KeybindingScope.Editor)
  case EditorUndo        extends KeybindingCommand("tui.editor.undo", KeybindingScope.Editor)

  // Input commands
  case InputNewLine extends KeybindingCommand("tui.input.newLine", KeybindingScope.Input)
  case InputSubmit  extends KeybindingCommand("tui.input.submit", KeybindingScope.Input)
  case InputTab     extends KeybindingCommand("tui.input.tab", KeybindingScope.Input)
  case InputCopy    extends KeybindingCommand("tui.input.copy", KeybindingScope.Input)

  // Select/autocomplete commands
  case SelectUp       extends KeybindingCommand("tui.select.up", KeybindingScope.Select)
  case SelectDown     extends KeybindingCommand("tui.select.down", KeybindingScope.Select)
  case SelectPageUp   extends KeybindingCommand("tui.select.pageUp", KeybindingScope.Select)
  case SelectPageDown extends KeybindingCommand("tui.select.pageDown", KeybindingScope.Select)
  case SelectConfirm  extends KeybindingCommand("tui.select.confirm", KeybindingScope.Select)
  case SelectCancel   extends KeybindingCommand("tui.select.cancel", KeybindingScope.Select)

  // Fullscreen viewport commands
  case ViewportPageUp
      extends KeybindingCommand("tui.altScreen.pageUp", KeybindingScope.Viewport)
  case ViewportPageDown
      extends KeybindingCommand("tui.altScreen.pageDown", KeybindingScope.Viewport)
  case ViewportHalfPageUp
      extends KeybindingCommand("tui.altScreen.halfPageUp", KeybindingScope.Viewport)
  case ViewportHalfPageDown
      extends KeybindingCommand("tui.altScreen.halfPageDown", KeybindingScope.Viewport)
  case ViewportLineUp
      extends KeybindingCommand("tui.altScreen.lineUp", KeybindingScope.Viewport)
  case ViewportLineDown
      extends KeybindingCommand("tui.altScreen.lineDown", KeybindingScope.Viewport)
  case ViewportDocumentStart
      extends KeybindingCommand("tui.altScreen.top", KeybindingScope.Viewport)
  case ViewportDocumentEnd
      extends KeybindingCommand("tui.altScreen.bottom", KeybindingScope.Viewport)
  case ViewportPreviousPrompt
      extends KeybindingCommand("tui.altScreen.previousPrompt", KeybindingScope.Viewport)
  case ViewportNextPrompt
      extends KeybindingCommand("tui.altScreen.nextPrompt", KeybindingScope.Viewport)
  case ViewportSearchToggle
      extends KeybindingCommand("tui.altScreen.search", KeybindingScope.Viewport)
  case ViewportSearchNext
      extends KeybindingCommand("tui.altScreen.searchNext", KeybindingScope.Viewport)
  case ViewportSearchPrevious
      extends KeybindingCommand("tui.altScreen.searchPrevious", KeybindingScope.Viewport)
  case ViewportSearchClose
      extends KeybindingCommand("tui.altScreen.searchClose", KeybindingScope.Viewport)
  case ViewportCopySelection
      extends KeybindingCommand("tui.altScreen.copySelection", KeybindingScope.Viewport)
  case ViewportClearSelection
      extends KeybindingCommand("tui.altScreen.clearSelection", KeybindingScope.Viewport)

/**
 * One terminal key combination.
 */
final case class KeyDescriptor(key: TerminalKey, modifiers: KeyModifiers = KeyModifiers.empty)
    derives CanEqual:
  def matches(input: TerminalInput): Boolean = input match
    case TerminalInput.KeyEvent(eventKey, eventModifiers, eventType) =>
      (eventType !== KeyEventType.Release) && eventKey === key && eventModifiers === modifiers
    case _                                                           => false

/**
 * Default command data.
 */
final case class KeybindingDefinition(defaultKeys: Vector[KeyDescriptor], description: String)
    derives CanEqual

/**
 * Conflict diagnostics for user-supplied key claims.
 */
final case class KeybindingConflict(key: KeyDescriptor, commands: Vector[KeybindingCommand])
    derives CanEqual

object KeybindingCommand:
  private val byId = KeybindingCommand.values.map(command => command.id -> command).toMap

  /** Resolve a string id to a command value. */
  def fromId(id: String): Option[KeybindingCommand] = byId.get(id)

/**
 * Backend-independent keybinding defaults and user override resolver for JVM and Scala Native.
 *
 * A supplied command entry replaces that command's defaults. An empty key vector intentionally
 * leaves the command unbound. Conflicts are reported by [[getConflicts]] and are not resolved by
 * silently changing either claim. The manager stores typed keys and emits no terminal sequences.
 */
final class KeybindingManager(
    private val userBindings: Map[KeybindingCommand, Vector[KeyDescriptor]] = Map.empty
) derives CanEqual:
  private val resolved: Map[KeybindingCommand, Vector[KeyDescriptor]] =
    KeybindingCommand.values.map { command =>
      command ->
        userBindings
          .get(command)
          .map(_.distinct)
          .getOrElse(KeybindingManager.Defaults(command).defaultKeys)
    }.toMap

  private val conflicts: Vector[KeybindingConflict] = KeybindingManager.findConflicts(userBindings)

  /** All known default definitions. */
  def definitions: Map[KeybindingCommand, KeybindingDefinition] = KeybindingManager.Defaults

  /** Return the resolved key descriptors for a command. */
  def getResolvedKeys(command: KeybindingCommand): Vector[KeyDescriptor] = resolved(command)

  /** Return the resolved key descriptors for a known command id, if any. */
  def getResolvedKeys(commandId: String): Option[Vector[KeyDescriptor]] =
    KeybindingCommand.fromId(commandId).map(getResolvedKeys)

  /** Whether `input` matches a command. */
  def matches(input: TerminalInput, command: KeybindingCommand): Boolean =
    getResolvedKeys(command).exists(_.matches(input))

  /** Whether `input` matches a command id, if known. */
  def matches(input: TerminalInput, commandId: String): Boolean =
    KeybindingCommand.fromId(commandId).exists(matches(input, _))

  /** Return conflicts among user-supplied bindings. */
  def getConflicts: Vector[KeybindingConflict] = conflicts

  /**
   * Return resolved bindings as a command -> keys map.
   */
  def getResolvedBindings: Map[KeybindingCommand, Vector[KeyDescriptor]] = resolved

  /** Resolve metadata for a known command. */
  def getDefinition(command: KeybindingCommand): KeybindingDefinition =
    KeybindingManager.Defaults(command)

  /**
   * Return a manager with additional user bindings merged into this manager.
   *
   * If a command is present in `bindings`, it replaces the default for that command.
   */
  def withUserBindings(bindings: Map[KeybindingCommand, Vector[KeyDescriptor]]): KeybindingManager =
    KeybindingManager(userBindings ++ bindings.view.mapValues(_.distinct).toMap)

  /**
   * Return a manager with user bindings provided as raw command-id keys.
   *
   * Unknown command ids are ignored.
   */
  def withRawBindings(bindings: Map[String, Vector[KeyDescriptor]]): KeybindingManager =
    val merged = bindings.toVector.collect {
      case (commandId, keys) if KeybindingCommand.fromId(commandId).isDefined =>
        (KeybindingCommand.fromId(commandId).get, keys.distinct)
    }.toMap
    withUserBindings(merged)

object KeybindingManager:
  /** Build a manager with fully specified user bindings. */
  def apply(bindings: Map[KeybindingCommand, Vector[KeyDescriptor]]): KeybindingManager =
    new KeybindingManager(bindings)

  /** Default manager with upstream key defaults. */
  def apply(): KeybindingManager = new KeybindingManager(Map.empty)

  /** Build from raw command-id maps, ignoring unknown ids. */
  def fromRawBindings(bindings: Map[String, Vector[KeyDescriptor]]): KeybindingManager =
    val typed = bindings.toVector.collect {
      case (id, keys) if KeybindingCommand.fromId(id).isDefined =>
        KeybindingCommand.fromId(id).get -> keys.distinct
    }.toMap
    new KeybindingManager(typed)

  private def findConflicts(
      bindings: Map[KeybindingCommand, Vector[KeyDescriptor]]
  ): Vector[KeybindingConflict] =
    val claims =
      for
        (command, keys) <- bindings.toVector
        key             <- keys.distinct
      yield key -> command

    claims
      .groupBy(_._1)
      .collect {
        case (key, claimsByCommand) if claimsByCommand.map(_._2).distinct.size > 1 =>
          val uniqueCommands = claimsByCommand.map(_._2).distinct.sortBy(_.id)
          KeybindingConflict(key, uniqueCommands)
      }
      .toVector
      .sortBy(_.key.toString)

  /**
   * Upstream-style defaults for commands that can be represented by this runtime's typed input
   * model.
   */
  private val Defaults: Map[KeybindingCommand, KeybindingDefinition] = Map(
    KeybindingCommand.EditorCursorUp           ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Up)),
        "Move cursor up"
      ),
    KeybindingCommand.EditorCursorDown         ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Down)),
        "Move cursor down"
      ),
    KeybindingCommand.EditorCursorLeft         ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Left),
          KeyDescriptor(TerminalKey.Character("b"), KeyModifiers(ctrl = true))
        ),
        "Move cursor left"
      ),
    KeybindingCommand.EditorCursorRight        ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Right),
          KeyDescriptor(TerminalKey.Character("f"), KeyModifiers(ctrl = true))
        ),
        "Move cursor right"
      ),
    KeybindingCommand.EditorCursorWordLeft     ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Left, KeyModifiers(alt = true)),
          KeyDescriptor(TerminalKey.Left, KeyModifiers(ctrl = true)),
          KeyDescriptor(TerminalKey.Character("b"), KeyModifiers(alt = true))
        ),
        "Move by word to the left"
      ),
    KeybindingCommand.EditorCursorWordRight    ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Right, KeyModifiers(alt = true)),
          KeyDescriptor(TerminalKey.Right, KeyModifiers(ctrl = true)),
          KeyDescriptor(TerminalKey.Character("f"), KeyModifiers(alt = true))
        ),
        "Move by word to the right"
      ),
    KeybindingCommand.EditorCursorLineStart    ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Home),
          KeyDescriptor(TerminalKey.Character("a"), KeyModifiers(ctrl = true))
        ),
        "Move to line start"
      ),
    KeybindingCommand.EditorCursorLineEnd      ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.End),
          KeyDescriptor(TerminalKey.Character("e"), KeyModifiers(ctrl = true))
        ),
        "Move to line end"
      ),
    KeybindingCommand.EditorJumpForward        ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("]"), KeyModifiers(ctrl = true))),
        "Jump forward to next character"
      ),
    KeybindingCommand.EditorJumpBackward       ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(
            TerminalKey.Character("]"),
            KeyModifiers(ctrl = true, alt = true)
          )
        ),
        "Jump backward to previous character"
      ),
    KeybindingCommand.EditorPageUp             ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.PageUp)),
        "Move page up"
      ),
    KeybindingCommand.EditorPageDown           ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.PageDown)),
        "Move page down"
      ),
    KeybindingCommand.EditorDeleteCharBackward ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Backspace)),
        "Delete character before cursor"
      ),
    KeybindingCommand.EditorDeleteCharForward  ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Delete),
          KeyDescriptor(TerminalKey.Character("d"), KeyModifiers(ctrl = true))
        ),
        "Delete character after cursor"
      ),
    KeybindingCommand.EditorDeleteWordBackward ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Character("w"), KeyModifiers(ctrl = true)),
          KeyDescriptor(TerminalKey.Backspace, KeyModifiers(alt = true)),
          KeyDescriptor(TerminalKey.Backspace, KeyModifiers(ctrl = true))
        ),
        "Delete previous word"
      ),
    KeybindingCommand.EditorDeleteWordForward  ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Character("d"), KeyModifiers(alt = true)),
          KeyDescriptor(TerminalKey.Delete, KeyModifiers(alt = true))
        ),
        "Delete next word"
      ),
    KeybindingCommand.EditorDeleteToLineStart  ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("u"), KeyModifiers(ctrl = true))),
        "Delete to line start"
      ),
    KeybindingCommand.EditorDeleteToLineEnd    ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("k"), KeyModifiers(ctrl = true))),
        "Delete to line end"
      ),
    KeybindingCommand.EditorYank               ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("y"), KeyModifiers(ctrl = true))),
        "Yank"
      ),
    KeybindingCommand.EditorYankPop            ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("y"), KeyModifiers(alt = true))),
        "Yank-pop"
      ),
    KeybindingCommand.EditorUndo               ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("-"), KeyModifiers(ctrl = true))),
        "Undo"
      ),
    KeybindingCommand.InputNewLine             ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Enter, KeyModifiers(shift = true)),
          KeyDescriptor(TerminalKey.Character("j"), KeyModifiers(ctrl = true))
        ),
        "Insert newline"
      ),
    KeybindingCommand.InputSubmit              ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Enter)),
        "Submit"
      ),
    KeybindingCommand.InputTab                 ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Tab)),
        "Trigger autocomplete / tab completion"
      ),
    KeybindingCommand.InputCopy                ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("c"), KeyModifiers(ctrl = true))),
        "Copy/cancel action"
      ),
    KeybindingCommand.SelectUp                 ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Up)),
        "Select previous row"
      ),
    KeybindingCommand.SelectDown               ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Down)),
        "Select next row"
      ),
    KeybindingCommand.SelectPageUp             ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.PageUp)),
        "Page-select up"
      ),
    KeybindingCommand.SelectPageDown           ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.PageDown)),
        "Page-select down"
      ),
    KeybindingCommand.SelectConfirm            ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Enter)),
        "Select current row"
      ),
    KeybindingCommand.SelectCancel             ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Escape),
          KeyDescriptor(TerminalKey.Character("c"), KeyModifiers(ctrl = true))
        ),
        "Cancel selection"
      ),
    KeybindingCommand.ViewportPageUp           ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.PageUp)),
        "Scroll viewport up one page"
      ),
    KeybindingCommand.ViewportPageDown         ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.PageDown)),
        "Scroll viewport down one page"
      ),
    KeybindingCommand.ViewportHalfPageUp       ->
      KeybindingDefinition(Vector.empty, "Scroll viewport up half a page"),
    KeybindingCommand.ViewportHalfPageDown     ->
      KeybindingDefinition(Vector.empty, "Scroll viewport down half a page"),
    KeybindingCommand.ViewportLineUp           ->
      KeybindingDefinition(Vector.empty, "Scroll viewport up one line"),
    KeybindingCommand.ViewportLineDown         ->
      KeybindingDefinition(Vector.empty, "Scroll viewport down one line"),
    KeybindingCommand.ViewportDocumentStart    ->
      KeybindingDefinition(Vector(KeyDescriptor(TerminalKey.Home)), "Scroll viewport to start"),
    KeybindingCommand.ViewportDocumentEnd      ->
      KeybindingDefinition(Vector(KeyDescriptor(TerminalKey.End)), "Scroll viewport to end"),
    KeybindingCommand.ViewportPreviousPrompt   ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Up, KeyModifiers(ctrl = true, shift = true)),
          KeyDescriptor(TerminalKey.Up, KeyModifiers(ctrl = true))
        ),
        "Jump to previous semantic prompt"
      ),
    KeybindingCommand.ViewportNextPrompt       ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Down, KeyModifiers(ctrl = true, shift = true)),
          KeyDescriptor(TerminalKey.Down, KeyModifiers(ctrl = true))
        ),
        "Jump to next semantic prompt"
      ),
    KeybindingCommand.ViewportSearchToggle     ->
      KeybindingDefinition(
        Vector(KeyDescriptor(TerminalKey.Character("f"), KeyModifiers(ctrl = true, shift = true))),
        "Toggle primary viewport search"
      ),
    KeybindingCommand.ViewportSearchNext       ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Enter),
          KeyDescriptor(TerminalKey.Character("g"), KeyModifiers(ctrl = true))
        ),
        "Select next viewport search match"
      ),
    KeybindingCommand.ViewportSearchPrevious   ->
      KeybindingDefinition(
        Vector(
          KeyDescriptor(TerminalKey.Enter, KeyModifiers(shift = true)),
          KeyDescriptor(
            TerminalKey.Character("g"),
            KeyModifiers(ctrl = true, shift = true)
          )
        ),
        "Select previous viewport search match"
      ),
    KeybindingCommand.ViewportSearchClose      ->
      KeybindingDefinition(Vector(KeyDescriptor(TerminalKey.Escape)), "Close viewport search"),
    KeybindingCommand.ViewportCopySelection    ->
      KeybindingDefinition(Vector.empty, "Copy viewport selection"),
    KeybindingCommand.ViewportClearSelection   ->
      KeybindingDefinition(Vector.empty, "Clear viewport selection")
  )
