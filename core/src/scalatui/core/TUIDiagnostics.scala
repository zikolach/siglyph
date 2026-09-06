package scalatui.core

/** Normal-screen clearing applied when terminal dimensions change. */
enum NormalResizeClearPolicy derives CanEqual:
  /**
   * Clear the active viewport, home the cursor, and clear terminal scrollback. This is the default.
   */
  case ClearScrollback

  /**
   * Clear and home only the active viewport, preserving normal-screen scrollback where supported.
   */
  case PreserveScrollback

/** Public lifecycle states exposed as diagnostic metadata. */
enum TUIDiagnosticLifecycleState derives CanEqual:
  case Starting, Running, Stopping, Cleaning, Stopped

/** Kind of terminal write represented by a diagnostic event. */
enum TUIDiagnosticWriteKind derives CanEqual:
  case Render, Protocol, Control, Cleanup

/** Kind of redraw represented by a diagnostic event. */
enum TUIDiagnosticRedrawKind derives CanEqual:
  case Full, Partial

/** Why a full redraw cleared the active terminal buffer. */
enum TUIDiagnosticClearReason derives CanEqual:
  case Initial, Resize

/** Bounded append-only output outcome used by redacted diagnostics. */
enum TUIDiagnosticAppendOutcome derives CanEqual:
  case Published, Rejected, Failed

/** Bounded append-only failure category that never retains exception text. */
enum TUIDiagnosticAppendFailure derives CanEqual:
  case Lifecycle, ScreenMode, ResizePolicy, FrameUnavailable, AttachedComponent, Capacity,
    RetainedITerm2, Stopped, Context, Render, Validation, Identity, Planning, Write, Callback

/** Bounded outcome for one optional normal-screen resize recovery attempt. */
enum TUIDiagnosticResizeRecoveryOutcome derives CanEqual:
  case Completed, Discarded, Failed

/** Bounded failure category for normal-screen resize recovery diagnostics. */
enum TUIDiagnosticResizeRecoveryFailure derives CanEqual:
  case StaleGeometry, Provider, RowBudget, Write

/**
 * Redacted, backend-independent terminal runtime diagnostics.
 *
 * Events contain only bounded enums, dimensions, row indexes, generations, row budgets/counts, and
 * byte counts. They never contain rendered application text, image payloads, raw query replies, or
 * terminal output bytes. Events from one observer belong only to its owning TUI instance.
 */
enum TUIDiagnosticEvent derives CanEqual:
  case Lifecycle(state: TUIDiagnosticLifecycleState, screenMode: TUIScreenMode)
  case Resize(columns: Int, rows: Int, generation: Long, screenMode: TUIScreenMode)
  case Redraw(
      kind: TUIDiagnosticRedrawKind,
      columns: Int,
      rows: Int,
      frameRows: Int,
      firstRow: Int,
      clearReason: Option[TUIDiagnosticClearReason],
      screenMode: TUIScreenMode
  )
  case Write(kind: TUIDiagnosticWriteKind, byteCount: Int)
  case Append(
      outcome: TUIDiagnosticAppendOutcome,
      failure: Option[TUIDiagnosticAppendFailure],
      rowCount: Int,
      controlCount: Int,
      screenMode: TUIScreenMode,
      resizeGeneration: Long
  )
  case ResizeRecovery(
      outcome: TUIDiagnosticResizeRecoveryOutcome,
      failure: Option[TUIDiagnosticResizeRecoveryFailure],
      maxRows: Int,
      rowCount: Int,
      resizeGeneration: Long
  )

/**
 * Opt-in observer for one TUI runtime's redacted diagnostic events.
 *
 * Callbacks run synchronously in runtime order but outside lifecycle and terminal-write locks. If a
 * callback throws, that failure is swallowed and this observer is permanently disabled for the
 * owning TUI so rendering and terminal restoration can continue. Diagnostic failure does not
 * recursively produce another event.
 */
trait TUIDiagnosticObserver:
  def onEvent(event: TUIDiagnosticEvent): Unit

object TUIDiagnosticObserver:
  /** Build an observer from a callback. */
  def apply(callback: TUIDiagnosticEvent => Unit): TUIDiagnosticObserver =
    new TUIDiagnosticObserver:
      override def onEvent(event: TUIDiagnosticEvent): Unit = callback(event)
