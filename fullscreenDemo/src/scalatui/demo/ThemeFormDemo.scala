package scalatui.demo

import scalatui.components.*
import scalatui.syntax.Equality.*
import scalatui.core.{Component, InputResult, OverlayHandle, OverlayOptions, OverlaySize, TUI}
import scalatui.terminal.{KeyEventType, Terminal, TerminalInput, TerminalKey}
import scalatui.theme.{SemanticRole, SemanticTheme}

final class ThemeFormDemo private (
    val tui: TUI,
    val name: Input,
    private[demo] val table: DataTable[String],
    focus: () => Int
):
  def focusedField: Int = focus()

/** Explicit local palettes and keyboard conventions, not a global theme or accessibility claim. */
object ThemeFormDemo:
  def apply(terminal: Terminal, theme: SemanticTheme = SemanticTheme.Monochrome): ThemeFormDemo =
    var index          = 0
    val name           = Input(theme = theme)
    val choices        = SelectList(
      Vector(SelectItem("info", "Info"), SelectItem("warn", "Warning")),
      SelectListOptions(maxVisible = 2, theme = SelectListTheme.semantic(theme, () => index === 1))
    )
    val table          = DataTable(
      TableData(Vector("Preview", "Details"), identity[String]),
      Vector(TableColumn[String]("Panel", 20, identity[String])),
      theme = theme
    )
    val focusLabel     = Text("[focus: name]", paddingX = 0)
    val status         = Text("Ctrl+S reviews; Tab/Shift+Tab move; Esc exits", paddingX = 0)
    val targets        = Vector[Component](name, choices, table)
    val labels         = Vector("name", "level", "table")
    var modal          = Option.empty[OverlayHandle]
    val root           = VStack(Seq(
      StackEntry(focusLabel, StackEntryOptions(basis = Some(1))),
      StackEntry(Text("Name (required)", paddingX = 0), StackEntryOptions(basis = Some(1))),
      StackEntry(name, StackEntryOptions(basis = Some(1))),
      StackEntry(choices, StackEntryOptions(basis = Some(2))),
      StackEntry(ScrollView(table), StackEntryOptions(grow = 1, minSize = 2)),
      StackEntry(
        Text(theme.paint(SemanticRole.Disabled, "[disabled] Delete: read-only demo"), paddingX = 0),
        StackEntryOptions(basis = Some(1))
      ),
      StackEntry(status, StackEntryOptions(basis = Some(2)))
    ))
    val tui            = TUI.fullscreen(terminal, root)
    def review(): Unit =
      if name.value.trim.isEmpty then
        status.text = theme.paint(SemanticRole.Error, "! Name is required")
        index = 0
        focusLabel.text = "[focus: name]"
        tui.setFocus(name)
      else
        val overlayTheme =
          theme.withOverrides(Map(SemanticRole.Normal -> theme.style(SemanticRole.Focused)))
        modal = Some(tui.showOverlay(
          Text(
            overlayTheme.paint(
              SemanticRole.Normal,
              s"Review ${name.value}: Enter confirms, Esc cancels"
            ),
            paddingX = 0
          ),
          OverlayOptions(width = Some(OverlaySize.Absolute(48)))
        ))
    name.onSubmit = _ => review()
    choices.onSelect =
      item => status.text = theme.paint(SemanticRole.Normal, s"Level: ${item.label}")
    tui.addInputListener {
      case TerminalInput.KeyEvent(_, _, KeyEventType.Release)                                     => InputResult.Ignored
      case TerminalInput.Key(TerminalKey.Escape, modifiers) if modifiers.isEmpty                  =>
        modal match
          case Some(handle) =>
            handle.hide()
            modal = None
            status.text = theme.paint(SemanticRole.Muted, "Cancelled; previous focus restored")
            InputResult.Render
          case None         => InputResult.Exit
      case TerminalInput.Key(TerminalKey.Enter, modifiers) if modifiers.isEmpty && modal.nonEmpty =>
        modal.foreach(_.hide())
        modal = None
        status.text = theme.paint(SemanticRole.Normal, s"Saved: ${name.value}")
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Tab, modifiers)
          if !modifiers.ctrl && !modifiers.alt && !modifiers.superKey =>
        if modal.isEmpty then
          index = (index + (if modifiers.shift then targets.size - 1 else 1)) % targets.size
          focusLabel.text = s"[focus: ${labels(index)}]"
          tui.setFocus(targets(index))
        InputResult.Render
      case TerminalInput.Key(TerminalKey.Character("s"), modifiers)
          if modifiers.ctrl && !modifiers.alt =>
        if modal.isEmpty then review()
        InputResult.Render
      case _                                                                                      => InputResult.Ignored
    }
    tui.setFocus(name)
    new ThemeFormDemo(tui, name, table, () => index)
