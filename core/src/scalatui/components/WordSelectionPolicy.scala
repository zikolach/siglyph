package scalatui.components

import scalatui.unicode.Unicode

/**
 * Additional complete graphemes joined to words during viewport double-click and word drag.
 * Defaults preserve existing letter/digit/underscore and slash/hyphen behavior. Whitespace and
 * controls cannot be joiners. Configuration is bounded independently of document retention.
 */
final case class WordSelectionPolicy(extraCharacters: String = ""):
  require(extraCharacters.length <= 256, "Word selection configuration must be bounded")
  private val extras = Unicode.graphemeClusters(extraCharacters).toSet
  require(
    !extras.exists(g =>
      Unicode.codePoints(g).exists(cp =>
        Character.isWhitespace(cp) || Character.isSpaceChar(cp) || Character.isISOControl(cp)
      )
    ),
    "Word selection extras cannot contain whitespace or controls"
  )

  private[components] def joins(grapheme: String): Boolean = extras.contains(grapheme)

object WordSelectionPolicy:
  /** Useful path punctuation, explicitly opt-in (including Windows paths). */
  val PathFriendly: WordSelectionPolicy = WordSelectionPolicy(".\\~+")
