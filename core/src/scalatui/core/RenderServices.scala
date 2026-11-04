package scalatui.core

import scalatui.terminal.{ImageCellDimensions, TerminalCapabilities, TerminalCapabilityOverrides}

/**
 * Read-only rendering environment shared by JVM and Scala Native components.
 *
 * Unlike [[TUIContext]], this capability grants no render scheduling, focus, exit, or overlay
 * authority. Values may reflect the current session; capture them when preparing a frame.
 */
trait RenderServices:
  /** Terminal cell geometry, with the deterministic standalone fallback. */
  def imageCellDimensions: ImageCellDimensions = ImageCellDimensions()

  /** Effective capabilities of the owning terminal session. */
  def terminalCapabilities: TerminalCapabilities = TerminalCapabilities.Conservative

  /** Session overrides, including hard-disabled capability ceilings. */
  def terminalCapabilityOverrides: TerminalCapabilityOverrides = TerminalCapabilityOverrides()
