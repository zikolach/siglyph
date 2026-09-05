package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  ComponentRender,
  ContextualComponent,
  DocumentMetadata,
  InputResult,
  MouseCaptureIntent,
  MouseEvent,
  MouseEventHandler,
  MouseHandlerResult,
  MouseInputHandler,
  MouseRenderIntent,
  PromptStart,
  TUIContext,
  ViewportDecoration,
  ViewportLayout,
  ViewportSelection,
  ViewportLayoutEngine,
  ViewportLayoutProvider,
  ViewportRangeRenderer,
  ViewportScrollLayout,
  ViewportScrollProvider
}
import scalatui.syntax.Equality.*
import scalatui.unicode.Unicode
import scalatui.terminal.{
  MouseAction,
  MouseButton,
  MouseInputContext,
  MouseWheelDirection
}

import java.nio.charset.StandardCharsets

/** Controls whether unused wheel delta can continue to an ancestor scroll view. */
enum OverscrollPolicy derives CanEqual:
  /** Offer unused delta to the nearest eligible ancestor scroll view. */
  case Chain

  /** Consume wheel input at this scroll view even when it is already at its bound. */
  case Contain

/** Controls scrollbar rendering in a fullscreen viewport. */
enum ScrollbarMode derives CanEqual:
  /** Never paint or target a scrollbar. */
  case Hidden

  /** Paint a scrollbar only while the document exceeds the viewport. */
  case Automatic

  /** Reserve and paint the final viewport column even when the document fits. */
  case Always

/** Proportional scrollbar geometry in local viewport cells. */
final case class ScrollbarGeometry(
    column: Int,
    trackHeight: Int,
    thumbTop: Int,
    thumbHeight: Int,
    maximumOffset: Int
) derives CanEqual

/** Local bounds of a rendered scroll affordance. */
final case class ScrollAffordanceBounds(row: Int, column: Int, width: Int, height: Int)
    derives CanEqual:
  /** Return whether a local viewport cell is inside these bounds. */
  def contains(localRow: Int, localCol: Int): Boolean =
    localRow >= row && localRow < row + height && localCol >= column && localCol < column + width

private enum SelectionGranularity:
  case Character, Word, Line

/**
 * A one-child vertical viewport shared by JVM and Scala Native.
 *
 * Direct [[render]] returns the complete width-constrained child document. Height clipping and
 * offset application occur only under the fullscreen viewport layout engine. Offset is always in
 * the inclusive range from zero through `max(0, contentExtent - viewportExtent)`. Public movement
 * methods request a render through the attached [[TUIContext]]. A vertical wheel gesture moves one
 * row. Holding Alt applies the documented five-row acceleration on JVM and Scala Native. Style and
 * indicator callbacks run during rendering after the state snapshot is released. They should be
 * side-effect free. Search retains at most [[ScrollView.MaxRetainedSearchRows]] document rows and
 * the configured match and query limits. Selection and search request range-rendered documents in
 * bounded chunks from row zero and stop when their limits are reached. An individual selectable
 * grapheme over 4096 UTF-8 source bytes stops indexing before partial retention. The view does not
 * schedule growth, animation, or edge-scroll timers.
 *
 * @param child
 *   Content owned by this view. Context and invalidation propagate to this child.
 * @param followEnd
 *   Keep the final content row visible while content grows. Manual movement away from the end
 *   suppresses following. [[jumpToEnd]] restores it.
 * @param primary
 *   Designate this view as the application transcript for later viewport command fallbacks.
 * @param overscrollPolicy
 *   Whether unconsumed wheel delta chains to an ancestor scroll view.
 * @param scrollbar
 *   Hidden, overflow-dependent, or always-visible proportional scrollbar behavior.
 * @param scrollbarTrackStyle
 *   Style callback for each one-cell scrollbar track glyph.
 * @param scrollbarThumbStyle
 *   Style callback for each one-cell scrollbar thumb glyph.
 * @param jumpToEndIndicator
 *   Optional label centered on the final viewport row while a primary follow-end view is away from
 *   the end. A primary semantic click on the label calls [[jumpToEnd]].
 * @param maxSelectionGraphemes
 *   Maximum complete grapheme clusters retained for selection mapping and host copy.
 * @param maxSelectionUtf8Bytes
 *   Maximum UTF-8 bytes retained for selection mapping and passed to a host clipboard callback.
 * @param maxSearchQueryGraphemes
 *   Maximum complete grapheme clusters retained in the fullscreen search query.
 * @param maxSearchQueryUtf8Bytes
 *   Maximum UTF-8 bytes retained in the fullscreen search query.
 * @param maxSearchMatches
 *   Maximum matches retained by the fullscreen search index.
 */
final class ScrollView(
    @scala.deprecatedName("child", "0.1.0") initialChild: Component,
    val followEnd: Boolean = false,
    val primary: Boolean = false,
    val overscrollPolicy: OverscrollPolicy = OverscrollPolicy.Chain,
    val scrollbar: ScrollbarMode = ScrollbarMode.Hidden,
    val scrollbarTrackStyle: String => String = identity,
    val scrollbarThumbStyle: String => String = identity,
    val jumpToEndIndicator: Option[() => String] = None,
    val maxSelectionGraphemes: Int = ScrollView.MaxRetainedSelectionGraphemes,
    val maxSelectionUtf8Bytes: Int = ScrollView.MaxRetainedSelectionUtf8Bytes,
    val maxSearchQueryGraphemes: Int = ScrollView.MaxSearchQueryGraphemes,
    val maxSearchQueryUtf8Bytes: Int = ScrollView.MaxSearchQueryUtf8Bytes,
    val maxSearchMatches: Int = ScrollView.MaxRetainedSearchMatches
) extends Component,
      ContextualComponent,
      ViewportLayoutProvider,
      ViewportScrollProvider,
      MouseInputHandler,
      MouseEventHandler:
  require(maxSelectionGraphemes > 0, "Selection grapheme bound must be positive")
  require(maxSelectionUtf8Bytes > 0, "Selection UTF-8 byte bound must be positive")
  require(maxSearchQueryGraphemes > 0, "Search query grapheme bound must be positive")
  require(maxSearchQueryUtf8Bytes > 0, "Search query UTF-8 byte bound must be positive")
  require(maxSearchMatches > 0, "Search match bound must be positive")

  private val stateBoundary        = ComponentStateBoundary()
  private val searchIndex          = TranscriptSearchIndex(maxSearchMatches)
  private var currentChild         = initialChild
  private var currentOffset        = 0
  private var currentContent       = 0
  private var currentViewport      = 0
  private var currentWidth         = 0
  private var currentMetadata      = DocumentMetadata.empty
  private var currentIndicator     = Option.empty[ScrollAffordanceBounds]
  private var scrollbarDragOffset  = Option.empty[Int]
  private var following            = followEnd
  private var context              = Option.empty[TUIContext]
  private var contentRevision      = 0L
  private var searchActive         = false
  private var searchQuery          = ""
  private var searchMatches        = Vector.empty[TranscriptSearchMatch]
  private var currentSearchMatch   = Option.empty[Int]
  private var selectionDocument    = Option.empty[ViewportSelectionDocument]
  private var selectionComplete    = false
  private var selectionScanCount   = 0
  private var selectionAnchor      = Option.empty[Int]
  private var selectionFocus       = Option.empty[Int]
  private var selectionInitial     = Option.empty[SelectionCellRange]
  private var selectionGranularity = SelectionGranularity.Character

  /** Content component currently owned by this view. */
  def child: Component = stateBoundary(currentChild)

  /**
   * Replace the complete scroll document and clear all retained search text and query state.
   *
   * The old child is detached before the new child receives this view's current session context.
   */
  def replaceContent(component: Component): Unit = stateBoundary.transition { effects =>
    val oldChild        = currentChild
    val attachedContext = context
    currentChild = component
    contentRevision += 1
    clearSearchLocked()
    clearSelectionLocked(clearDocument = true)
    effects.add(() => searchIndex.clear())
    attachedContext.foreach(value => effects.add(() => value.clearViewportSearchInput()))
    if oldChild ne component then
      oldChild match
        case contextual: ContextualComponent => effects.add(() => contextual.tuiContext_=(None))
        case _                               => ()
      component match
        case contextual: ContextualComponent =>
          effects.add(() => contextual.tuiContext_=(attachedContext))
        case _                               => ()
    attachedContext.foreach(value => effects.add(() => value.requestRender()))
  }

  /**
   * Invalidate cached rendered search text after mutating the current child document.
   *
   * The current query and match ordinal remain selected when they still exist after re-rendering.
   */
  def invalidateSearchDocument(): Unit = stateBoundary.transition { effects =>
    val target = currentChild
    contentRevision += 1
    searchMatches = Vector.empty
    selectionDocument = None
    selectionComplete = false
    selectionInitial = None
    selectionGranularity = SelectionGranularity.Character
    effects.add(() => target.invalidate())
    effects.add(() => searchIndex.clear())
    context.foreach(value => effects.add(() => value.requestRender()))
  }

  /** Current selection as bounded normalized document offsets and plain text. */
  def selection: Option[ViewportSelection] = stateBoundary {
    for
      document <- selectionDocument
      anchor   <- selectionAnchor
      focus    <- selectionFocus
      selected <- document.selection(anchor, focus)
    yield selected
  }

  private[scalatui] def lastSelectionGraphemeScans: Int = stateBoundary(selectionScanCount)

  /** Clear the current selection while retaining the current bounded cell mapping. */
  def clearSelection(): Unit = stateBoundary.transition { effects =>
    val changed = selectionAnchor.nonEmpty || selectionFocus.nonEmpty
    clearSelectionLocked(clearDocument = false)
    if changed then context.foreach(value => effects.add(() => value.requestRender()))
  }

  /** Current fullscreen search state. Match position is one-based when present. */
  def searchState: ViewportSearchState = stateBoundary {
    ViewportSearchState(
      searchActive,
      searchQuery,
      searchMatches.length,
      currentSearchMatch.map(_ + 1)
    )
  }

  /** Current bounded zero-based document row at the top of the viewport. */
  def offset: Int = stateBoundary(currentOffset)

  /** Last committed child document height in rows. */
  def contentExtent: Int = stateBoundary(currentContent)

  /** Last committed viewport height in rows. */
  def viewportExtent: Int = stateBoundary(currentViewport)

  /** Whether content growth currently keeps this view aligned to the content end. */
  def isFollowingEnd: Boolean = stateBoundary(following)

  /** Latest proportional scrollbar geometry, if this mode currently paints one. */
  def scrollbarGeometry: Option[ScrollbarGeometry] = stateBoundary(scrollbarGeometryLocked)

  /** Latest clickable jump-to-end label bounds in local viewport cells. */
  def jumpToEndIndicatorBounds: Option[ScrollAffordanceBounds] = stateBoundary(currentIndicator)

  /** Move by document rows and return the unconsumed signed delta. */
  def scrollBy(delta: Int): Int = stateBoundary.transition { effects =>
    val maximum  = maximumOffset
    val target   = clamp(currentOffset.toLong + delta.toLong, maximum)
    val consumed = target - currentOffset
    currentOffset = target
    if followEnd && (delta !== 0) then following = target === maximum
    if consumed !== 0 then context.foreach(value => effects.add(() => value.requestRender()))
    delta - consumed
  }

  /** Move to one bounded document row. */
  def scrollTo(row: Int): Unit =
    val delta = stateBoundary(row - currentOffset)
    scrollBy(delta)
    ()

  /** Move to the nearest typed prompt marker in the requested direction. */
  def scrollToPrompt(direction: Int): Unit =
    val target = stateBoundary {
      val rows = currentMetadata.markers.collect { case PromptStart(position) => position.row }
        .distinct.sorted
      if direction < 0 then rows.reverse.find(_ < currentOffset)
      else if direction > 0 then rows.find(_ > currentOffset)
      else None
    }
    target.foreach(scrollTo)

  /** Move to the first document row and suppress follow-end until the end is reached again. */
  def jumpToStart(): Unit = stateBoundary.transition { effects =>
    val changed = (currentOffset !== 0) || (followEnd && following)
    currentOffset = 0
    if followEnd then following = maximumOffset === 0
    if changed then context.foreach(value => effects.add(() => value.requestRender()))
  }

  /** Move to the final viewport and restore configured follow-end behavior. */
  def jumpToEnd(): Unit = stateBoundary.transition { effects =>
    val target  = maximumOffset
    val changed = (currentOffset !== target) || (followEnd && !following)
    currentOffset = target
    if followEnd then following = true
    if changed then context.foreach(value => effects.add(() => value.requestRender()))
  }

  override def viewportLayout: ViewportLayout = ViewportScrollLayout(child)

  override def viewportScrollOwner: Option[Component] = Some(this)

  override private[scalatui] def viewportContentWidth(width: Int): Int =
    if scrollbar === ScrollbarMode.Always && width > 1 then width - 1 else width

  override private[scalatui] def commitViewportGeometry(
      contentExtent: Int,
      viewportExtent: Int,
      viewportWidth: Int
  ): Int = stateBoundary {
    currentContent = math.max(0, contentExtent)
    currentViewport = math.max(0, viewportExtent)
    currentWidth = math.max(0, viewportWidth)
    val maximum = maximumOffset
    currentOffset = if followEnd && following then maximum else math.min(currentOffset, maximum)
    currentOffset
  }

  override private[scalatui] def viewportContentRevision: Long = stateBoundary(contentRevision)

  override private[scalatui] def commitViewportText(
      rows: Vector[String],
      complete: Boolean,
      child: Component,
      revision: Long
  ): Unit =
    if complete then
      commitSelectionDocument(
        ViewportSelectionDocument(rows, maxSelectionGraphemes, maxSelectionUtf8Bytes),
        complete = true,
        child,
        revision,
        scanned = None
      )
    else
      val selectedNeedsRefresh = stateBoundary {
        if (currentChild eq child) && contentRevision === revision && !selectionComplete then
          selectionDocument = None
        selectionAnchor.nonEmpty && !selectionComplete
      }
      if selectedNeedsRefresh then refreshCompleteSelectionDocument()

  override private[scalatui] def commitViewportDocumentMetadata(
      metadata: DocumentMetadata
  ): Unit = stateBoundary {
    currentMetadata = metadata
  }

  override private[scalatui] def viewportDecorations: Vector[ViewportDecoration] =
    val snapshot             = stateBoundary {
      (
        scrollbarGeometryLocked,
        currentWidth,
        currentViewport,
        following,
        scrollbarTrackStyle,
        scrollbarThumbStyle,
        jumpToEndIndicator,
        for
          document <- selectionDocument
          anchor   <- selectionAnchor
          focus    <- selectionFocus
        yield document.decorations(anchor, focus, currentOffset)
      )
    }
    val (
      geometry,
      width,
      viewport,
      isFollowing,
      trackStyle,
      thumbStyle,
      indicator,
      selectionDecorations
    ) = snapshot
    val scrollbarDecorations = geometry.toVector.flatMap { value =>
      Vector.tabulate(value.trackHeight) { row =>
        val thumb  = row >= value.thumbTop && row < value.thumbTop + value.thumbHeight
        val glyph  = if thumb then "█" else "│"
        val styled = Ansi.truncateToWidth(
          if thumb then thumbStyle(glyph) else trackStyle(glyph),
          1,
          ""
        )
        ViewportDecoration(row, value.column, styled, 1)
      }
    }
    val indicatorDecoration  =
      if primary && followEnd && !isFollowing && viewport > 0 then
        indicator.flatMap { renderIndicator =>
          val available = geometry.fold(width)(_.column)
          val text      = Ansi.truncateToWidth(renderIndicator(), math.max(0, available), "")
          val textWidth = Ansi.visibleWidth(text)
          Option.when(textWidth > 0) {
            val bounds = ScrollAffordanceBounds(
              viewport - 1,
              math.max(0, (available - textWidth) / 2),
              textWidth,
              1
            )
            stateBoundary { currentIndicator = Some(bounds) }
            ViewportDecoration(bounds.row, bounds.column, text, bounds.width)
          }
        }
      else None
    if indicatorDecoration.isEmpty then stateBoundary { currentIndicator = None }
    val searchDecorations    = stateBoundary {
      if !searchActive then Vector.empty
      else
        searchMatches.zipWithIndex.flatMap { case (matched, index) =>
          val localRow = matched.row - currentOffset
          Option.when(localRow >= 0 && localRow < currentViewport && matched.width > 0) {
            val style = if currentSearchMatch.contains(index) then "\u001b[1;7m" else "\u001b[7m"
            ViewportDecoration(
              localRow,
              matched.column,
              style + matched.text + Ansi.Reset,
              matched.width
            )
          }
        }
    }
    scrollbarDecorations ++ indicatorDecoration ++ selectionDecorations.toVector.flatten ++ searchDecorations

  override def render(width: Int): ComponentRender =
    child match
      case _: ViewportLayoutProvider => ViewportLayoutEngine.renderUnbounded(child, width).render
      case _                         => child.render(width)

  override def handleMouse(mouse: MouseInputContext): InputResult = mouse.input.action match
    case MouseAction.Wheel(MouseWheelDirection.Up)   =>
      wheel(-wheelMultiplier(mouse.input.modifiers.alt))
    case MouseAction.Wheel(MouseWheelDirection.Down) =>
      wheel(wheelMultiplier(mouse.input.modifiers.alt))
    case _                                           => InputResult.Ignored

  override def handleMouseEvent(event: MouseEvent): MouseHandlerResult = event match
    case wheelEvent: MouseEvent.Wheel                                                     =>
      if wheelEvent.lineDelta === 0 then MouseHandlerResult.Ignored
      else
        val remaining = scrollBy(wheelEvent.lineDelta)
        MouseHandlerResult(
          handled = (overscrollPolicy === OverscrollPolicy.Contain) ||
            (remaining !== wheelEvent.lineDelta),
          renderIntent =
            if remaining !== wheelEvent.lineDelta then MouseRenderIntent.Render
            else MouseRenderIntent.Preserve,
          wheelRemainder = Some(
            if overscrollPolicy === OverscrollPolicy.Contain then 0 else remaining
          )
        )
    case press: MouseEvent.Press
        if press.button === MouseButton.Left && onScrollbar(
          press.location.localRow,
          press.location.localCol
        ) =>
      beginScrollbarDrag(press.location.localRow)
      MouseHandlerResult(
        handled = true,
        renderIntent = MouseRenderIntent.Render,
        captureIntent = MouseCaptureIntent.Capture
      )
    case drag: MouseEvent.Drag if drag.button === MouseButton.Left && scrollbarDragActive =>
      dragScrollbar(drag.location.localRow)
      MouseHandlerResult(handled = true, renderIntent = MouseRenderIntent.Render)
    case release: MouseEvent.Release
        if release.button === MouseButton.Left && scrollbarDragActive =>
      stateBoundary { scrollbarDragOffset = None }
      MouseHandlerResult(handled = true, captureIntent = MouseCaptureIntent.Release)
    case press: MouseEvent.Press if primary && press.button === MouseButton.Left          =>
      beginSelection(press.location.localRow, press.location.localCol)
    case drag: MouseEvent.Drag
        if primary && drag.button === MouseButton.Left && selectionActive =>
      extendSelection(drag.location.localRow, drag.location.localCol)
    case release: MouseEvent.Release
        if primary && release.button === MouseButton.Left && selectionActive =>
      MouseHandlerResult(
        handled = true,
        renderIntent = MouseRenderIntent.Render,
        captureIntent = MouseCaptureIntent.Release
      )
    case click: MouseEvent.Click
        if click.button === MouseButton.Left && stateBoundary(
          currentIndicator.exists(_.contains(click.location.localRow, click.location.localCol))
        ) =>
      jumpToEnd()
      MouseHandlerResult(handled = true, renderIntent = MouseRenderIntent.Render)
    case click: MouseEvent.Click
        if primary && click.button === MouseButton.Left && click.clickCount >= 2 =>
      selectClickGranularity(click.location.localRow, click.location.localCol, click.clickCount)
    case _                                                                                => MouseHandlerResult.Ignored

  override def tuiContext_=(value: Option[TUIContext]): Unit =
    stateBoundary.transitionContext(context, value) { effects =>
      if !sameContext(context, value) then
        context = value
        currentChild match
          case contextual: ContextualComponent => effects.add(() => contextual.tuiContext_=(value))
          case _                               => ()
    }

  override def invalidate(): Unit = child.invalidate()

  private[scalatui] def openSearch(): Unit = stateBoundary { searchActive = true }

  private[scalatui] def closeSearch(): Unit = stateBoundary { searchActive = false }

  private[scalatui] def clearSearch(): Unit =
    stateBoundary(clearSearchLocked())
    searchIndex.clear()

  private[scalatui] def clearSelectionRetained(): Unit =
    stateBoundary(clearSelectionLocked(clearDocument = true))

  private[scalatui] def boundSearchQuery(value: String): String =
    val result    = StringBuilder()
    var graphemes = 0
    var bytes     = 0
    Unicode.foreachGraphemeWhile(value) { grapheme =>
      val graphemeBytes = grapheme.getBytes(StandardCharsets.UTF_8).length
      if graphemes >= maxSearchQueryGraphemes || bytes + graphemeBytes > maxSearchQueryUtf8Bytes
      then false
      else
        result.append(grapheme)
        graphemes += 1
        bytes += graphemeBytes
        true
    }
    result.result()

  private[scalatui] def updateSearchQuery(
      value: String,
      width: Int,
      recordScans: Int => Unit
  ): Unit =
    val bounded = boundSearchQuery(value)
    stateBoundary {
      searchQuery = bounded
      currentSearchMatch = None
    }
    refreshSearch(width, recordScans, reveal = true)

  private[scalatui] def moveSearchMatch(
      delta: Int,
      width: Int,
      recordScans: Int => Unit
  ): Unit =
    refreshSearch(width, recordScans)
    stateBoundary {
      if searchMatches.nonEmpty then
        val current = currentSearchMatch.getOrElse(if delta < 0 then 0 else -1)
        currentSearchMatch = Some(Math.floorMod(current + delta, searchMatches.length))
        revealCurrentSearchMatchLocked()
    }

  private[scalatui] def refreshSearch(
      width: Int,
      recordScans: Int => Unit,
      reveal: Boolean = false
  ): Unit =
    val contentWidth                      = viewportContentWidth(math.max(0, width))
    val snapshot                          = stateBoundary {
      (searchActive, searchQuery, contentRevision, currentChild)
    }
    val (active, query, revision, target) = snapshot
    if active then
      val documentChanged = searchIndex.requiresDocument(revision, contentWidth)
      val rendered        = Option.when(documentChanged)(
        renderSearchRows(target, contentWidth)
      )
      val matches         = searchIndex.matches(revision, contentWidth, query, rendered, recordScans)
      val stale           = stateBoundary {
        if contentRevision === revision && searchQuery === query then
          searchMatches = matches
          currentSearchMatch =
            if matches.isEmpty then None
            else Some(math.min(currentSearchMatch.getOrElse(0), matches.length - 1))
          if reveal || documentChanged then revealCurrentSearchMatchLocked()
          false
        else true
      }
      if stale then searchIndex.clear()

  private def renderSearchRows(target: Component, width: Int): Iterator[String] = target match
    case ranged: ViewportRangeRenderer =>
      new Iterator[String]:
        private val maximum = math.min(
          math.max(0, ranged.contentExtent(width)),
          TranscriptSearchIndex.MaxIndexedRows
        )
        private var start   = 0
        private var current = Iterator.empty[String]

        override def hasNext: Boolean =
          while !current.hasNext && start < maximum do
            val count = math.min(ScrollView.RangeWorkChunkRows, maximum - start)
            val range = ranged.renderRange(width, start, count).validated(width)
            require(
              range.lines.length <= count,
              "Viewport range render returned more rows than requested"
            )
            current = range.lines.iterator
            if range.lines.isEmpty then start = maximum else start += range.lines.length
          current.hasNext

        override def next(): String =
          if hasNext then current.next()
          else throw new java.util.NoSuchElementException("next on empty iterator")
    case _: ViewportLayoutProvider     =>
      ViewportLayoutEngine.renderUnbounded(target, width).render.lines.iterator
        .take(TranscriptSearchIndex.MaxIndexedRows)
    case _                             =>
      target.render(width).validated(width).lines.iterator
        .take(TranscriptSearchIndex.MaxIndexedRows)

  private def clearSearchLocked(): Unit =
    searchActive = false
    searchQuery = ""
    searchMatches = Vector.empty
    currentSearchMatch = None

  private def revealCurrentSearchMatchLocked(): Unit =
    currentSearchMatch.flatMap(searchMatches.lift).foreach { matched =>
      val visibleRows = math.max(1, currentViewport - 1)
      if matched.row < currentOffset then currentOffset = matched.row
      else if matched.row >= currentOffset + visibleRows then
        currentOffset = math.max(0, matched.row - visibleRows + 1)
      currentOffset = math.min(currentOffset, maximumOffset)
      if followEnd then following = currentOffset === maximumOffset
    }

  private def beginSelection(localRow: Int, localCol: Int): MouseHandlerResult =
    ensureCompleteSelectionDocument()
    val range = stateBoundary {
      selectionDocument.flatMap(
        _.cellRange(currentOffset + clampLocalRow(localRow), math.max(0, localCol))
      ).filter(value => value.end > value.start)
    }
    range.fold(MouseHandlerResult.Ignored) { value =>
      stateBoundary {
        selectionAnchor = Some(value.start)
        selectionFocus = Some(value.end)
        selectionInitial = Some(value)
        selectionGranularity = SelectionGranularity.Character
      }
      MouseHandlerResult(
        handled = true,
        renderIntent = MouseRenderIntent.Render,
        captureIntent = MouseCaptureIntent.Capture
      )
    }

  private def extendSelection(localRow: Int, localCol: Int): MouseHandlerResult =
    val direction = stateBoundary {
      if localRow <= 0 && currentOffset > 0 then -1
      else if localRow >= currentViewport - 1 && currentOffset < maximumOffset then 1
      else 0
    }
    if direction !== 0 then scrollBy(direction)
    val changed   = stateBoundary {
      val documentRow = currentOffset + clampLocalRow(localRow)
      val target      = selectionDocument.flatMap { document =>
        selectionGranularity match
          case SelectionGranularity.Character =>
            document.cellRange(documentRow, math.max(0, localCol))
          case SelectionGranularity.Word      =>
            document.wordRange(documentRow, math.max(0, localCol))
          case SelectionGranularity.Line      => document.rowRange(documentRow)
      }
      for
        initial <- selectionInitial
        range   <- target
      yield
        if range.start < initial.start then
          selectionAnchor = Some(initial.end)
          selectionFocus = Some(range.start)
        else
          selectionAnchor = Some(initial.start)
          selectionFocus = Some(range.end)
    }.nonEmpty
    MouseHandlerResult(
      handled = changed,
      renderIntent = if changed || (direction !== 0) then MouseRenderIntent.Render
      else MouseRenderIntent.Preserve
    )

  private def selectClickGranularity(
      localRow: Int,
      localCol: Int,
      clickCount: Int
  ): MouseHandlerResult =
    ensureCompleteSelectionDocument()
    val changed = stateBoundary {
      val documentRow = currentOffset + clampLocalRow(localRow)
      val line        = clickCount === 3
      val range       = selectionDocument.flatMap { document =>
        if line then document.rowRange(documentRow)
        else document.wordRange(documentRow, math.max(0, localCol))
      }
      range.foreach { value =>
        selectionAnchor = Some(value.start)
        selectionFocus = Some(value.end)
        selectionInitial = Some(value)
        selectionGranularity = if line then SelectionGranularity.Line else SelectionGranularity.Word
      }
      range.nonEmpty
    }
    MouseHandlerResult(
      handled = changed,
      renderIntent = if changed then MouseRenderIntent.Render else MouseRenderIntent.Preserve
    )

  private def selectionActive: Boolean = stateBoundary(selectionAnchor.nonEmpty)

  private def clampLocalRow(localRow: Int): Int =
    math.max(0, math.min(math.max(0, currentViewport - 1), localRow))

  private def ensureCompleteSelectionDocument(): Unit =
    if !stateBoundary(selectionComplete) then refreshCompleteSelectionDocument()

  private def refreshCompleteSelectionDocument(): Unit =
    val (target, width, revision) = stateBoundary {
      (currentChild, viewportContentWidth(currentWidth), contentRevision)
    }
    val builder                   = ViewportSelectionDocument.Builder(
      maxSelectionGraphemes,
      maxSelectionUtf8Bytes
    )
    target match
      case ranged: ViewportRangeRenderer =>
        val extent = math.max(0, ranged.contentExtent(width))
        var start  = 0
        while start < extent && !builder.isFull do
          val count    = math.min(ScrollView.RangeWorkChunkRows, extent - start)
          val range    = ranged.renderRange(width, start, count).validated(width)
          require(
            range.lines.length <= count,
            "Viewport range render returned more rows than requested"
          )
          val iterator = range.lines.iterator
          while iterator.hasNext && !builder.isFull do builder.appendRow(iterator.next())
          if range.lines.isEmpty then start = extent else start += range.lines.length
      case _: ViewportLayoutProvider     =>
        val rows = ViewportLayoutEngine.renderUnbounded(target, width).render.lines.iterator
        while rows.hasNext && !builder.isFull do builder.appendRow(rows.next())
      case _                             =>
        val rows = target.render(width).validated(width).lines.iterator
        while rows.hasNext && !builder.isFull do builder.appendRow(rows.next())
    commitSelectionDocument(
      builder.result(),
      complete = true,
      target,
      revision,
      scanned = Some(builder.scannedGraphemes)
    )

  private def commitSelectionDocument(
      document: ViewportSelectionDocument,
      complete: Boolean,
      child: Component,
      revision: Long,
      scanned: Option[Int]
  ): Unit = stateBoundary {
    if (currentChild eq child) && contentRevision === revision then
      scanned.foreach(selectionScanCount = _)
      val changed = selectionDocument.exists(previous => !previous.sameText(document))
      selectionDocument = Some(document)
      selectionComplete = complete
      if document.length === 0 then clearSelectionLocked(clearDocument = false)
      else
        selectionAnchor = selectionAnchor.map(math.min(_, document.length))
        selectionFocus = selectionFocus.map(math.min(_, document.length))
        if changed then
          selectionInitial = None
          selectionGranularity = SelectionGranularity.Character
  }

  private def clearSelectionLocked(clearDocument: Boolean): Unit =
    selectionAnchor = None
    selectionFocus = None
    selectionInitial = None
    selectionGranularity = SelectionGranularity.Character
    if clearDocument then
      selectionDocument = None
      selectionComplete = false

  private def wheel(delta: Int): InputResult =
    val remaining = scrollBy(delta)
    if remaining === delta && overscrollPolicy === OverscrollPolicy.Chain then InputResult.Ignored
    else InputResult.Handled(requestRender = remaining !== delta)

  private def wheelMultiplier(alt: Boolean): Int = if alt then 5 else 1

  private def onScrollbar(localRow: Int, localCol: Int): Boolean = stateBoundary {
    scrollbarGeometryLocked.exists(value =>
      localCol === value.column && localRow >= 0 && localRow < value.trackHeight
    )
  }

  private def beginScrollbarDrag(localRow: Int): Unit = stateBoundary {
    scrollbarGeometryLocked.foreach { geometry =>
      val onThumb =
        localRow >= geometry.thumbTop && localRow < geometry.thumbTop + geometry.thumbHeight
      val grab    = if onThumb then localRow - geometry.thumbTop else geometry.thumbHeight / 2
      scrollbarDragOffset = Some(grab)
      if !onThumb then scrollScrollbarToLocked(localRow, geometry, grab)
    }
  }

  private def dragScrollbar(localRow: Int): Unit = stateBoundary {
    for
      grab     <- scrollbarDragOffset
      geometry <- scrollbarGeometryLocked
    do scrollScrollbarToLocked(localRow, geometry, grab)
  }

  private def scrollbarDragActive: Boolean = stateBoundary(scrollbarDragOffset.nonEmpty)

  private def scrollScrollbarToLocked(
      localRow: Int,
      geometry: ScrollbarGeometry,
      grabOffset: Int
  ): Unit =
    val maximumThumbTop = geometry.trackHeight - geometry.thumbHeight
    val thumbTop        = math.max(0, math.min(maximumThumbTop, localRow - grabOffset))
    currentOffset =
      if maximumThumbTop === 0 then 0
      else
        math.round(
          thumbTop.toDouble / maximumThumbTop.toDouble * geometry.maximumOffset.toDouble
        ).toInt
    if followEnd then following = currentOffset === geometry.maximumOffset

  private def scrollbarGeometryLocked: Option[ScrollbarGeometry] =
    val visible = scrollbar match
      case ScrollbarMode.Hidden    => false
      case ScrollbarMode.Automatic => currentContent > currentViewport
      case ScrollbarMode.Always    => currentViewport > 0
    Option.when(visible && currentWidth > 0 && currentViewport > 0) {
      val minimumThumb    = math.min(2, currentViewport)
      val thumbHeight     =
        if currentContent <= 0 then currentViewport
        else
          math.max(
            minimumThumb,
            math.min(
              currentViewport,
              math.round(
                currentViewport.toDouble * currentViewport.toDouble / currentContent.toDouble
              ).toInt
            )
          )
      val maximumThumbTop = currentViewport - thumbHeight
      val thumbTop        =
        if maximumOffset === 0 then 0
        else math.round(currentOffset.toDouble / maximumOffset.toDouble * maximumThumbTop).toInt
      ScrollbarGeometry(
        currentWidth - 1,
        currentViewport,
        thumbTop,
        thumbHeight,
        maximumOffset
      )
    }

  private def maximumOffset: Int = math.max(0, currentContent - currentViewport)

  private def clamp(value: Long, maximum: Int): Int =
    math.max(0L, math.min(maximum.toLong, value)).toInt

  private def sameContext(left: Option[TUIContext], right: Option[TUIContext]): Boolean =
    (left, right) match
      case (None, None)                      => true
      case (Some(a), Some(b))                => a eq b
      case (None, Some(_)) | (Some(_), None) => false

object ScrollView:
  private final val RangeWorkChunkRows = 64

  /** Maximum complete grapheme clusters retained in one search query. */
  val MaxSearchQueryGraphemes: Int = 256

  /** Maximum UTF-8 bytes retained in one search query. */
  val MaxSearchQueryUtf8Bytes: Int = 4096

  /** Maximum search matches retained for one indexed document revision. */
  val MaxRetainedSearchMatches: Int = 10000

  /** Maximum rendered rows retained by the search index. */
  val MaxRetainedSearchRows: Int = TranscriptSearchIndex.MaxIndexedRows

  /** Maximum complete grapheme clusters retained for selection mapping and copy. */
  val MaxRetainedSelectionGraphemes: Int = ViewportSelectionDocument.MaxRetainedGraphemes

  /** Maximum UTF-8 bytes retained for selection mapping and passed to a clipboard callback. */
  val MaxRetainedSelectionUtf8Bytes: Int = ViewportSelectionDocument.MaxRetainedUtf8Bytes

  /** Maximum rendered rows retained as selection cell metadata. */
  val MaxRetainedSelectionRows: Int = ViewportSelectionDocument.MaxRetainedRows
