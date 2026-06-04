package scalatui.components

import scalatui.terminal.KeyModifiers

/**
 * Configures how a multiline [[Editor]] interprets Enter key events.
 *
 * Terminal support for modified Enter depends on the active terminal and parser normalization. The
 * editor consumes typed `TerminalInput.Key(TerminalKey.Enter, modifiers)` events and never inspects
 * raw escape sequences.
 */
enum EditorEnterBehavior:
  /**
   * Plain Enter submits the current editor text.
   *
   * @param newlineModifiers
   *   modifier sets that insert a newline instead of submitting. The default mirrors prompt/chat
   *   editors where Shift+Enter inserts a newline.
   */
  case SubmitOnEnter(
      newlineModifiers: Set[KeyModifiers] = Set(KeyModifiers(shift = true))
  )

  /**
   * Plain Enter inserts a newline.
   *
   * @param submitModifiers
   *   modifier sets that submit instead of inserting a newline. The default uses Cmd/Super+Enter.
   */
  case NewlineOnEnter(
      submitModifiers: Set[KeyModifiers] = Set(KeyModifiers(superKey = true))
  )

object EditorEnterBehavior:
  /** Default prompt-like behavior: Enter submits and Shift+Enter inserts a newline. */
  val Default: EditorEnterBehavior = EditorEnterBehavior.SubmitOnEnter()
