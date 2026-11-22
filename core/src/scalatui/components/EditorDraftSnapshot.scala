package scalatui.components

import scalatui.editing.EditorBuffer

/**
 * Opaque, detached committed editor draft for JVM and Scala Native.
 *
 * Obtain values with [[Editor.snapshotDraft]] and reuse them with [[Editor.restoreDraft]]. A draft
 * preserves displayed text, compact paste payloads and identities, and the exact logical cursor. It
 * does not capture undo/history stacks, pending paste bytes, autocomplete, callbacks or TUI state.
 * Copying an editor's displayed marker text does not create an equivalent draft. The public
 * interface has no state-bearing members; internal JVM bridges are not a security boundary.
 */
sealed trait EditorDraftSnapshot

private[components] object EditorDraftSnapshots:
  private final class Captured(savedBuffer: EditorBuffer.Snapshot) extends EditorDraftSnapshot:
    def restore(buffer: EditorBuffer): Unit = buffer.restore(savedBuffer)

  def capture(buffer: EditorBuffer): EditorDraftSnapshot = new Captured(buffer.snapshot)

  private def captured(snapshot: EditorDraftSnapshot): Captured =
    // JVM callers can supply implementations outside Scala's sealed hierarchy.
    val handle: AnyRef = snapshot
    handle match
      case value: Captured => value
      case _               => throw new IllegalArgumentException("invalid draft snapshot")

  def validate(snapshot: EditorDraftSnapshot): Unit =
    captured(snapshot)
    ()

  def restore(snapshot: EditorDraftSnapshot, buffer: EditorBuffer): Unit =
    captured(snapshot).restore(buffer)
