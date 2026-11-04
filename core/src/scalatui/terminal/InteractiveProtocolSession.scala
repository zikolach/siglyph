package scalatui.terminal

import scalatui.syntax.Equality.*

/**
 * Shared interactive protocol obligations. Callers serialize access and retain ownership of cleanup
 * ordering, failure aggregation, input workers, and platform terminal state.
 */
private[terminal] final class InteractiveProtocolSession(write: String => Unit):
  private val negotiator   = KittyKeyboardProtocolNegotiator()
  private var pastePending = false
  private var mouseMode    = TerminalMouseTrackingMode.Disabled
  private var kittyPending = false

  def pasteCleanupPending: Boolean   = pastePending
  def mouseCleanupPending: Boolean   = mouseMode !== TerminalMouseTrackingMode.Disabled
  def kittyCleanupPending: Boolean   = kittyPending
  def hasCleanupObligations: Boolean = pastePending || mouseCleanupPending || kittyPending

  def enablePaste(): Unit =
    pastePending = true
    write("\u001b[?2004h")

  def enableMouse(mode: TerminalMouseTrackingMode): Unit =
    if mode !== TerminalMouseTrackingMode.Disabled then
      mouseMode = mode
      write(Terminal.MouseProtocol.enable(mode))

  def disablePaste(): Unit =
    if pastePending then
      write("\u001b[?2004l")
      pastePending = false

  def disableMouse(): Unit =
    if mouseCleanupPending then
      write(Terminal.MouseProtocol.disable(mouseMode))
      mouseMode = TerminalMouseTrackingMode.Disabled

  def keyboardProtocolState(nowMillis: Long): KittyKeyboardProtocolState =
    negotiator.expire(nowMillis)
    negotiator.state

  def requestKittyKeyboardProtocol(nowMillis: Long, timeoutMillis: Long): Unit =
    negotiator.begin(nowMillis, timeoutMillis)
    write(KittyKeyboardProtocol.QuerySequence)

  def acceptKittyKeyboardProtocolResponse(response: String, nowMillis: Long): Boolean =
    val accepted = negotiator.receiveResponse(response, nowMillis)
    if accepted then
      kittyPending = true
      write(KittyKeyboardProtocol.EnableSequence)
    accepted

  def disableKittyKeyboardProtocol(): Unit =
    if kittyPending then write(KittyKeyboardProtocol.DisableSequence)
    negotiator.disable()
    kittyPending = false

  def cancelPendingKittyNegotiation(): Unit =
    if !kittyPending then negotiator.disable()
