package scalatui.core

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*
import scalatui.terminal.{
  Terminal,
  TerminalRenderControl,
  TerminalRenderControlDetails,
  TerminalRenderControlEncoder
}

private[core] final case class PreparedFrame(
    lines: Vector[String],
    position: Option[CursorPlacement],
    controls: Vector[TerminalControlPlacement],
    documentMetadata: DocumentMetadata
) derives CanEqual

/** Shared serialized terminal-write boundary used by runtime services and renderer policies. */
private[core] final class RuntimeTerminalServices(
    terminal: Terminal,
    counters: RuntimeCounters,
    onWrite: (TUIDiagnosticWriteKind, String) => Unit
):
  private val terminalWriteLock = Object()

  def write(action: => Unit): Unit = terminalWriteLock.synchronized(action)

  def writeData(value: String, kind: TUIDiagnosticWriteKind): Unit =
    counters.recordTerminalWrite()
    write(terminal.write(value))
    onWrite(kind, value)

  def writeRenderBuffer(buffer: String): Unit =
    counters.recordTerminalWrite()
    write(terminal.write(buffer))
    onWrite(TUIDiagnosticWriteKind.Render, buffer)

/** Internal rendering boundary shared by the current width-only screen modes. */
private[core] trait RendererPolicy:
  def isAlternateScreen: Boolean
  def isFullscreenViewport: Boolean = false
  def retainedFrame: Option[PreparedFrame]
  def retainedWidth: Int
  def retainedHeight: Int
  def frameStartRow: Option[Int]
  def frameStartRow_=(value: Option[Int]): Unit
  def sanitizationCount: Int
  def lastSanitization: Option[TUI.RenderSanitization]
  def start(): Unit
  def prepareFrame(frame: ComponentRender, width: Int): PreparedFrame
  def prepareResizeRecovery(lines: Vector[String], width: Int): Vector[String]
  def render(
      frame: PreparedFrame,
      width: Int,
      height: Int,
      force: Boolean,
      clear: Boolean,
      recovery: Option[TUI.PreparedResizeRecovery]
  ): Unit
  def publishAppend(appended: PreparedFrame, retained: PreparedFrame, terminalHeight: Int): Unit
  def parkCursorForCleanup(): Unit
  def restoreTypedControls(): Unit
  def exitScreen(): Unit

/**
 * Preserves the unbounded normal-screen renderer and its width-only alternate-buffer option.
 * Alternate mode changes buffer lifecycle and clear sequences only. It does not create a viewport.
 */
private[core] final class NormalScreenPolicy(
    terminal: Terminal,
    options: TUIOptions,
    counters: RuntimeCounters,
    terminalServices: RuntimeTerminalServices,
    emitRedraw: (
        TUIDiagnosticRedrawKind,
        Int,
        Int,
        Int,
        Int,
        Option[TUIDiagnosticClearReason]
    ) => Unit
) extends RendererPolicy:
  private var previousFrame           = Option.empty[PreparedFrame]
  private var previousWidth           = 0
  private var previousHeight          = 0
  private var cursorRow               = 0
  private var latestFrameStartRow     = Option.empty[Int]
  private var autoWrapRestoreNeeded   = false
  private var alternateScreenEntered  = false
  private var sanitizedLineCount      = 0
  private var mostRecentSanitizedLine = Option.empty[TUI.RenderSanitization]

  override def isAlternateScreen: Boolean = options.screenMode match
    case TUIScreenMode.Alternate => true
    case TUIScreenMode.Normal    => false

  override def retainedFrame: Option[PreparedFrame] = previousFrame

  override def retainedWidth: Int = previousWidth

  override def retainedHeight: Int = previousHeight

  override def frameStartRow: Option[Int] = latestFrameStartRow

  override def frameStartRow_=(value: Option[Int]): Unit = latestFrameStartRow = value

  override def sanitizationCount: Int = sanitizedLineCount

  override def lastSanitization: Option[TUI.RenderSanitization] = mostRecentSanitizedLine

  override def start(): Unit =
    if isAlternateScreen && !alternateScreenEntered then
      terminalServices.writeData(TUI.AlternateScreenEnter, TUIDiagnosticWriteKind.Control)
      alternateScreenEntered = true

  override def prepareFrame(frame: ComponentRender, width: Int): PreparedFrame =
    val selectedCursor = frame.cursorPlacements.zipWithIndex
      .minByOption { case (placement, index) => (placement.row, placement.column, index) }
      .map(_._1)
    PreparedFrame(
      applyLineResets(sanitizeLines(frame.lines, width)),
      selectedCursor,
      frame.controls,
      frame.documentMetadata
    )

  override def prepareResizeRecovery(lines: Vector[String], width: Int): Vector[String] =
    applyLineResets(sanitizeLines(lines, width, retainDiagnosticContent = false))

  override def render(
      frame: PreparedFrame,
      width: Int,
      height: Int,
      force: Boolean,
      clear: Boolean,
      recovery: Option[TUI.PreparedResizeRecovery]
  ): Unit =
    val widthChanged  = (previousWidth !== 0) && (previousWidth !== width)
    val heightChanged = (previousHeight !== 0) && (previousHeight !== height)
    if previousFrame.isEmpty || force then
      val clearReason = Option.when(clear) {
        if previousFrame.isEmpty then TUIDiagnosticClearReason.Initial
        else TUIDiagnosticClearReason.Resize
      }
      emitRedraw(
        TUIDiagnosticRedrawKind.Full,
        width,
        height,
        frame.lines.length,
        0,
        clearReason
      )
      fullRender(frame, width, height, clearReason, recovery)
    else if widthChanged || heightChanged then
      val clearReason = Some(TUIDiagnosticClearReason.Resize)
      emitRedraw(
        TUIDiagnosticRedrawKind.Full,
        width,
        height,
        frame.lines.length,
        0,
        clearReason
      )
      fullRender(frame, width, height, clearReason, recovery)
    else
      val firstChanged = firstChangedRow(previousFrame.get, frame)
      if firstChanged >= 0 then
        emitRedraw(
          TUIDiagnosticRedrawKind.Partial,
          width,
          height,
          frame.lines.length,
          firstChanged,
          None
        )
        partialRender(frame, firstChanged)
      else positionHardwareCursorOnly(frame.position)
      previousFrame = Some(frame)
      previousWidth = width
      previousHeight = height

  override def publishAppend(
      appended: PreparedFrame,
      retained: PreparedFrame,
      terminalHeight: Int
  ): Unit =
    if appended.lines.nonEmpty then
      val builder            = StringBuilder()
      appendRenderStart(builder)
      appendVerticalMove(builder, fromRow = cursorRow, toRow = 0)
      builder.append("\r\u001b[J")
      kittyLifecycleCleanup(Some(retained), retained, fromRow = 0).foreach { control =>
        builder.append(encodeControl(control))
        builder.append("\r")
      }
      appendFrameContent(builder, appended, fromRow = 0, Vector.empty)
      builder.append("\r\n")
      val retainedPaintedRow = appendFrameContent(builder, retained, fromRow = 0, Vector.empty)
      appendHardwareCursorMove(builder, retained, retainedPaintedRow)
      appendRenderEnd(builder)
      writeRenderBuffer(builder.result())
      val totalRows          = appended.lines.length + math.max(1, retained.lines.length)
      latestFrameStartRow = latestFrameStartRow.map { start =>
        val appendStart = scrolledFrameStart(start, 0, totalRows, terminalHeight)
        appendStart + appended.lines.length
      }
      cursorRow = finalCursorRow(retained, retainedPaintedRow)

  override def parkCursorForCleanup(): Unit = parkCursorBelowContentIfNeeded()

  override def restoreTypedControls(): Unit = restoreAutoWrapIfNeeded()

  override def exitScreen(): Unit =
    if alternateScreenEntered then
      try terminalServices.writeData(TUI.AlternateScreenExit, TUIDiagnosticWriteKind.Cleanup)
      finally alternateScreenEntered = false

  private def fullRender(
      frame: PreparedFrame,
      width: Int,
      height: Int,
      clearReason: Option[TUIDiagnosticClearReason],
      recovery: Option[TUI.PreparedResizeRecovery]
  ): Unit =
    val clear                = clearReason.nonEmpty
    val startRowBeforeRender = if clear then Some(0) else latestFrameStartRow
    val builder              = StringBuilder()
    appendRenderStart(builder)
    if clear then builder.append(clearSequence(clearReason.get))
    else
      previousFrame.foreach { _ =>
        appendVerticalMove(builder, fromRow = cursorRow, toRow = 0)
        builder.append("\r")
      }
    val cleanupControls      = kittyLifecycleCleanup(previousFrame, frame, fromRow = 0)
    val paintedRow           = recovery match
      case Some(value) =>
        val recoveryFrame = PreparedFrame(
          value.lines,
          None,
          Vector.empty,
          DocumentMetadata.empty
        )
        appendFrameContent(builder, recoveryFrame, fromRow = 0, cleanupControls)
        if value.lines.nonEmpty then builder.append("\r\n")
        appendFrameContent(builder, frame, fromRow = 0, Vector.empty)
      case None        =>
        appendFrameContent(builder, frame, fromRow = 0, cleanupControls)
    appendHardwareCursorMove(builder, frame, paintedRow)
    appendRenderEnd(builder)
    writeRenderBuffer(builder.result())
    latestFrameStartRow = recovery match
      case Some(value) =>
        val liveFootprint = math.max(1, frame.lines.length)
        val combinedStart = scrolledFrameStart(
          frameStartRow = 0,
          writeStartFrameRow = 0,
          writtenLineCount = value.lines.length + liveFootprint,
          terminalHeight = height
        )
        Some(combinedStart + value.lines.length)
      case None        =>
        startRowBeforeRender.map(scrolledFrameStart(_, 0, frame.lines.length, height))
    previousFrame = Some(frame)
    previousWidth = width
    previousHeight = height
    cursorRow = finalCursorRow(frame, paintedRow)

  private def partialRender(frame: PreparedFrame, firstChanged: Int): Unit =
    val builder    = StringBuilder()
    appendRenderStart(builder)
    appendVerticalMove(builder, fromRow = cursorRow, toRow = firstChanged)
    builder.append("\r\u001b[J")
    val paintedRow = appendFrameContent(
      builder,
      frame,
      firstChanged,
      kittyLifecycleCleanup(previousFrame, frame, fromRow = firstChanged)
    )
    appendHardwareCursorMove(builder, frame, paintedRow)
    appendRenderEnd(builder)
    writeRenderBuffer(builder.result())
    cursorRow = finalCursorRow(frame, paintedRow)
    latestFrameStartRow = latestFrameStartRow.map(start =>
      scrolledFrameStart(start, firstChanged, frame.lines.length - firstChanged, terminal.rows)
    )

  private def appendFrameContent(
      builder: StringBuilder,
      frame: PreparedFrame,
      fromRow: Int,
      cleanupControls: Vector[TerminalRenderControl]
  ): Int =
    cleanupControls.foreach { control =>
      builder.append(encodeControl(control))
      builder.append("\r")
    }
    val controlsByRow = frame.controls.filter(_.row >= fromRow).groupBy(_.row)
    var row           = fromRow
    counters.recordPaintedRows(frame.lines.length - fromRow)
    while row < frame.lines.length do
      controlsByRow.getOrElse(row, Vector.empty).foreach { placement =>
        appendMoveRight(builder, placement.column)
        builder.append(encodeControl(placement.control))
        builder.append("\r")
      }
      builder.append(frame.lines(row))
      if row < frame.lines.length - 1 then builder.append("\r\n")
      row += 1
    if frame.lines.length > fromRow then frame.lines.length - 1 else fromRow

  private def kittyLifecycleCleanup(
      oldFrame: Option[PreparedFrame],
      newFrame: PreparedFrame,
      fromRow: Int
  ): Vector[TerminalRenderControl] =
    val newActiveIds = newFrame.controls.iterator.flatMap(kittyImageId).toSet
    val emittedIds   = newFrame.controls.iterator
      .filter(_.row >= fromRow)
      .flatMap(kittyImageId)
      .toSet
    oldFrame.toVector
      .flatMap(_.controls)
      .filter(placement =>
        kittyImageId(placement).exists(imageId => !newActiveIds(imageId) || emittedIds(imageId))
      )
      .flatMap(placement => TerminalRenderControl.cleanupForReplacement(placement.control))

  private def kittyImageId(placement: TerminalControlPlacement): Option[Int] =
    placement.control.details match
      case kitty: TerminalRenderControlDetails.KittyImage => Some(kitty.imageId)
      case _                                              => None

  private def encodeControl(control: TerminalRenderControl): String =
    counters.recordControlEncode()
    TerminalRenderControlEncoder.encode(control)

  private def writeRenderBuffer(buffer: String): Unit =
    autoWrapRestoreNeeded = true
    terminalServices.writeRenderBuffer(buffer)
    autoWrapRestoreNeeded = false

  private def parkCursorBelowContentIfNeeded(): Unit =
    if previousFrame.exists(_.lines.nonEmpty) && !alternateScreenEntered then
      val builder = StringBuilder()
      appendVerticalMove(
        builder,
        fromRow = cursorRow,
        toRow = math.max(0, previousFrame.fold(0)(_.lines.length) - 1)
      )
      builder.append("\r\n")
      terminalServices.writeData(builder.result(), TUIDiagnosticWriteKind.Cleanup)

  private def clearSequence(reason: TUIDiagnosticClearReason): String =
    if alternateScreenEntered then TUI.AlternateScreenClear
    else
      (reason, options.normalResizeClearPolicy) match
        case (
              TUIDiagnosticClearReason.Resize,
              NormalResizeClearPolicy.PreserveScrollback
            ) => TUI.NormalScreenViewportClear
        case _ => TUI.NormalScreenClear

  private def restoreAutoWrapIfNeeded(): Unit =
    if autoWrapRestoreNeeded then
      autoWrapRestoreNeeded = false
      terminalServices.writeData(TUI.AutoWrapOn, TUIDiagnosticWriteKind.Cleanup)

  private def appendRenderStart(builder: StringBuilder): Unit =
    builder.append(TUI.SyncStart)
    // Full-width terminal lines can be marked as soft-wrapped by real terminal emulators. Those
    // soft-wrap markers may be reflowed on resize, invalidating the logical cursor row used by the
    // differential redraw path. Disable autowrap while painting each frame.
    builder.append(TUI.AutoWrapOff)

  private def appendRenderEnd(builder: StringBuilder): Unit =
    builder.append(TUI.SyncEnd)
    builder.append(TUI.AutoWrapOn)

  private def positionHardwareCursorOnly(position: Option[CursorPlacement]): Unit =
    if options.hardwareCursorPositioning then
      position.foreach { target =>
        val builder = StringBuilder()
        appendVerticalMove(builder, fromRow = cursorRow, toRow = target.row)
        builder.append("\r")
        appendMoveRight(builder, target.column)
        if builder.nonEmpty then
          terminalServices.writeData(builder.result(), TUIDiagnosticWriteKind.Control)
          cursorRow = target.row
      }

  private def appendHardwareCursorMove(
      builder: StringBuilder,
      frame: PreparedFrame,
      fromRow: Int
  ): Unit =
    if options.hardwareCursorPositioning then
      frame.position.foreach { target =>
        appendVerticalMove(builder, fromRow = fromRow, toRow = target.row)
        builder.append("\r")
        appendMoveRight(builder, target.column)
      }

  private def scrolledFrameStart(
      frameStartRow: Int,
      writeStartFrameRow: Int,
      writtenLineCount: Int,
      terminalHeight: Int
  ): Int =
    if writtenLineCount <= 0 then frameStartRow
    else
      val writeStartScreenRow = frameStartRow + writeStartFrameRow
      val overflow            = math.max(0, writeStartScreenRow + writtenLineCount - terminalHeight)
      frameStartRow - overflow

  private def finalCursorRow(frame: PreparedFrame, paintedRow: Int): Int =
    if options.hardwareCursorPositioning then frame.position.map(_.row).getOrElse(paintedRow)
    else paintedRow

  private def appendVerticalMove(builder: StringBuilder, fromRow: Int, toRow: Int): Unit =
    val delta = toRow - fromRow
    if delta > 0 then builder.append(s"\u001b[${delta}B")
    else if delta < 0 then builder.append(s"\u001b[${-delta}A")

  private def appendMoveRight(builder: StringBuilder, columns: Int): Unit =
    if columns > 0 then builder.append(s"\u001b[${columns}C")

  private def sanitizeLines(
      lines: Vector[String],
      width: Int,
      retainDiagnosticContent: Boolean = true
  ): Vector[String] =
    lines.zipWithIndex.map { (line, index) =>
      val lineWidth = Ansi.visibleWidth(line)
      if lineWidth <= width then Ansi.sanitize(line)
      else
        val sanitized = Ansi.truncateToWidth(line, width, "")
        sanitizedLineCount += 1
        if retainDiagnosticContent then
          mostRecentSanitizedLine = Some(TUI.RenderSanitization(
            lineIndex = index,
            originalWidth = lineWidth,
            targetWidth = width,
            original = line,
            sanitized = sanitized
          ))
        sanitized
    }

  private def firstChangedLine(oldLines: Vector[String], newLines: Vector[String]): Int =
    val firstDifferent = oldLines.zip(newLines).indexWhere { case (oldLine, newLine) =>
      oldLine !== newLine
    }
    if firstDifferent >= 0 then firstDifferent
    else if oldLines.length === newLines.length then -1
    else math.min(oldLines.length, newLines.length)

  private def firstChangedRow(oldFrame: PreparedFrame, newFrame: PreparedFrame): Int =
    val removedControls = oldFrame.controls.diff(newFrame.controls)
    val addedControls   = newFrame.controls.diff(oldFrame.controls)
    val lineRow         = firstChangedLine(oldFrame.lines, newFrame.lines)
    val orderedRow      = firstOrderedControlDifferenceRow(oldFrame.controls, newFrame.controls)
    val controlRow      = removedControls.iterator
      .map(_.row)
      .concat(addedControls.iterator.map(_.row))
      .concat(Option.when(orderedRow >= 0)(orderedRow).iterator)
      .minOption
      .getOrElse(-1)
    if lineRow < 0 then controlRow
    else if controlRow < 0 then lineRow
    else math.min(lineRow, controlRow)

  private def firstOrderedControlDifferenceRow(
      oldControls: Vector[TerminalControlPlacement],
      newControls: Vector[TerminalControlPlacement]
  ): Int =
    val commonLength    = math.min(oldControls.length, newControls.length)
    val firstDifference = (0 until commonLength).find(index =>
      oldControls(index) !== newControls(index)
    )
    val changedFrom     = firstDifference.orElse(
      Option.when(oldControls.length !== newControls.length)(commonLength)
    )
    changedFrom
      .flatMap { index =>
        oldControls.iterator
          .drop(index)
          .map(_.row)
          .concat(newControls.iterator.drop(index).map(_.row))
          .minOption
      }
      .getOrElse(-1)

  private def applyLineResets(lines: Vector[String]): Vector[String] =
    lines.map(_ + TUI.LineReset)

/**
 * Fixed-height alternate-screen policy used only by explicit [[TUI.fullscreen]] construction.
 * Lifecycle, input, overlays, failure handling, and terminal writes remain owned by [[TUI]].
 */
private[core] final class FullscreenViewportPolicy(
    terminal: Terminal,
    options: TUIOptions,
    counters: RuntimeCounters,
    terminalServices: RuntimeTerminalServices,
    emitRedraw: (
        TUIDiagnosticRedrawKind,
        Int,
        Int,
        Int,
        Int,
        Option[TUIDiagnosticClearReason]
    ) => Unit
) extends RendererPolicy:
  private var previousFrame           = Option.empty[PreparedFrame]
  private var previousWidth           = 0
  private var previousHeight          = 0
  private var alternateScreenEntered  = false
  private var autoWrapRestoreNeeded   = false
  private var sanitizedLineCount      = 0
  private var mostRecentSanitizedLine = Option.empty[TUI.RenderSanitization]
  private val kittyRetention          = FullscreenKittyRetention(options.kittyImageRetention)

  override def isAlternateScreen: Boolean                       = true
  override def isFullscreenViewport: Boolean                    = true
  override def retainedFrame: Option[PreparedFrame]             = previousFrame
  override def retainedWidth: Int                               = previousWidth
  override def retainedHeight: Int                              = previousHeight
  override def frameStartRow: Option[Int]                       = Some(0)
  override def frameStartRow_=(value: Option[Int]): Unit        = ()
  override def sanitizationCount: Int                           = sanitizedLineCount
  override def lastSanitization: Option[TUI.RenderSanitization] = mostRecentSanitizedLine

  override def start(): Unit =
    if !alternateScreenEntered then
      terminalServices.writeData(TUI.AlternateScreenEnter, TUIDiagnosticWriteKind.Control)
      alternateScreenEntered = true

  override def prepareFrame(frame: ComponentRender, width: Int): PreparedFrame =
    val lines  = frame.lines.zipWithIndex.map { (line, index) =>
      val lineWidth = Ansi.visibleWidth(line)
      val sanitized =
        if lineWidth <= width then Ansi.sanitize(line)
        else
          val value = Ansi.truncateToWidth(line, width, "")
          sanitizedLineCount += 1
          mostRecentSanitizedLine = Some(TUI.RenderSanitization(
            index,
            lineWidth,
            width,
            line,
            value
          ))
          value
      sanitized + TUI.LineReset
    }
    val cursor = frame.cursorPlacements.zipWithIndex
      .minByOption { case (placement, index) => (placement.row, placement.column, index) }
      .map(_._1)
    PreparedFrame(lines, cursor, frame.controls, frame.documentMetadata)

  override def prepareResizeRecovery(lines: Vector[String], width: Int): Vector[String] =
    throw IllegalStateException("Fullscreen viewport does not support normal resize recovery")

  override def render(
      frame: PreparedFrame,
      width: Int,
      height: Int,
      force: Boolean,
      clear: Boolean,
      recovery: Option[TUI.PreparedResizeRecovery]
  ): Unit =
    require(recovery.isEmpty, "Fullscreen viewport does not support normal resize recovery")
    require(frame.lines.length === height, "Fullscreen frame must match terminal height")
    val resized      = (previousWidth !== width) || (previousHeight !== height)
    val retained     = kittyRetention.update(
      frame,
      previousFrame,
      repaintAll = force || clear || resized
    )
    val currentFrame = retained.frame
    val firstChanged = previousFrame.fold(0)(firstChangedRow(_, currentFrame))
    if force || clear || resized || previousFrame.isEmpty then
      emitRedraw(
        TUIDiagnosticRedrawKind.Full,
        width,
        height,
        height,
        0,
        Some(if previousFrame.isEmpty then TUIDiagnosticClearReason.Initial
        else TUIDiagnosticClearReason.Resize)
      )
      paint(currentFrame, fromRow = 0, clearScreen = true, retained.cleanup)
    else if firstChanged >= 0 || retained.cleanup.nonEmpty then
      val paintFrom = if firstChanged >= 0 then firstChanged else 0
      emitRedraw(TUIDiagnosticRedrawKind.Partial, width, height, height, paintFrom, None)
      paint(currentFrame, paintFrom, clearScreen = false, retained.cleanup)
    else positionCursor(currentFrame.position)
    previousFrame = Some(currentFrame)
    previousWidth = width
    previousHeight = height

  override def publishAppend(
      appended: PreparedFrame,
      retained: PreparedFrame,
      terminalHeight: Int
  ): Unit = throw IllegalStateException("Fullscreen viewport does not support append-only output")

  override def parkCursorForCleanup(): Unit = ()

  override def restoreTypedControls(): Unit =
    var failure = Option.empty[Throwable]
    val cleanup = kittyRetention.stopCleanup()
    if cleanup.nonEmpty then
      kittyRetention.recordStopCleanupAttempt()
      try
        val encoded = cleanup.map { control =>
          counters.recordControlEncode()
          TerminalRenderControlEncoder.encode(control)
        }.mkString
        terminalServices.writeData(encoded, TUIDiagnosticWriteKind.Cleanup)
        kittyRetention.acknowledgeStopCleanup()
      catch case error: Throwable => failure = Some(error)
    if autoWrapRestoreNeeded then
      autoWrapRestoreNeeded = false
      try terminalServices.writeData(TUI.AutoWrapOn, TUIDiagnosticWriteKind.Cleanup)
      catch
        case error: Throwable => failure match
            case Some(first) => first.addSuppressed(error)
            case None        => failure = Some(error)
    failure.foreach(throw _)

  override def exitScreen(): Unit =
    if alternateScreenEntered then
      try terminalServices.writeData(TUI.AlternateScreenExit, TUIDiagnosticWriteKind.Cleanup)
      finally alternateScreenEntered = false

  private def paint(
      frame: PreparedFrame,
      fromRow: Int,
      clearScreen: Boolean,
      cleanup: Vector[TerminalRenderControl]
  ): Unit =
    val builder       = StringBuilder(TUI.SyncStart).append(TUI.AutoWrapOff)
    if clearScreen then builder.append(TUI.AlternateScreenClear)
    cleanup.foreach { control =>
      builder.append(TerminalRenderControlEncoder.encode(control))
      counters.recordControlEncode()
    }
    val controlsByRow = frame.controls.filter(_.row >= fromRow).groupBy(_.row)
    var row           = fromRow
    counters.recordPaintedRows(frame.lines.length - fromRow)
    while row < frame.lines.length do
      appendPosition(builder, row, 0)
      builder.append("\u001b[2K")
      controlsByRow.getOrElse(row, Vector.empty).foreach { placement =>
        appendPosition(builder, row, placement.column)
        builder.append(TerminalRenderControlEncoder.encode(placement.control))
        counters.recordControlEncode()
      }
      appendPosition(builder, row, 0)
      builder.append(frame.lines(row))
      row += 1
    appendCursor(builder, frame.position)
    builder.append(TUI.SyncEnd).append(TUI.AutoWrapOn)
    autoWrapRestoreNeeded = true
    terminalServices.writeRenderBuffer(builder.result())
    kittyRetention.acknowledgeCleanup(cleanup)
    autoWrapRestoreNeeded = false

  private def positionCursor(position: Option[CursorPlacement]): Unit =
    if options.hardwareCursorPositioning then
      position.foreach { placement =>
        val builder = StringBuilder()
        appendPosition(builder, placement.row, placement.column)
        terminalServices.writeData(builder.result(), TUIDiagnosticWriteKind.Control)
      }

  private def appendCursor(builder: StringBuilder, position: Option[CursorPlacement]): Unit =
    if options.hardwareCursorPositioning then
      position.foreach(placement => appendPosition(builder, placement.row, placement.column))

  private def appendPosition(builder: StringBuilder, row: Int, column: Int): Unit =
    builder.append(s"\u001b[${row + 1};${column + 1}H")

  private def firstChangedRow(oldFrame: PreparedFrame, newFrame: PreparedFrame): Int =
    val visibleEqual =
      oldFrame.lines === newFrame.lines && oldFrame.controls === newFrame.controls &&
        oldFrame.position === newFrame.position
    if visibleEqual then -1
    else
      val line         = oldFrame.lines.zip(newFrame.lines).indexWhere { case (oldLine, newLine) =>
        oldLine !== newLine
      }
      val lineRow      =
        if line >= 0 then line else math.min(oldFrame.lines.length, newFrame.lines.length)
      val metadataRows = oldFrame.controls.diff(newFrame.controls).map(_.row) ++
        newFrame.controls.diff(oldFrame.controls).map(_.row) ++
        oldFrame.position.toVector.map(_.row) ++ newFrame.position.toVector.map(_.row)
      metadataRows.minOption.fold(lineRow)(math.min(lineRow, _))
