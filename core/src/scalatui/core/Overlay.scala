package scalatui.core

/** Anchor point used when an overlay does not specify an absolute or percentage position. */
enum OverlayAnchor derives CanEqual:
  case Center
  case TopLeft
  case TopRight
  case BottomLeft
  case BottomRight
  case TopCenter
  case BottomCenter
  case LeftCenter
  case RightCenter

/** Absolute or percentage size/position value for overlay layout. */
sealed trait OverlaySize derives CanEqual

object OverlaySize:
  /** Absolute terminal cells. */
  final case class Absolute(value: Int) extends OverlaySize

  /** Percentage of the current terminal dimension. */
  final case class Percent(value: Double) extends OverlaySize

/** Terminal-edge margins used to clamp overlay placement. */
final case class OverlayMargin(
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    left: Int = 0
) derives CanEqual:
  def normalized: OverlayMargin = OverlayMargin(
    top = math.max(0, top),
    right = math.max(0, right),
    bottom = math.max(0, bottom),
    left = math.max(0, left)
  )

object OverlayMargin:
  /** Create the same non-negative margin for every terminal edge. */
  def all(value: Int): OverlayMargin =
    val normalizedValue = math.max(0, value)
    OverlayMargin(normalizedValue, normalizedValue, normalizedValue, normalizedValue)

/** Public options controlling overlay sizing, positioning, visibility, and focus behavior. */
final case class OverlayOptions(
    width: Option[OverlaySize] = None,
    minWidth: Option[Int] = None,
    maxHeight: Option[OverlaySize] = None,
    anchor: OverlayAnchor = OverlayAnchor.Center,
    offsetX: Int = 0,
    offsetY: Int = 0,
    row: Option[OverlaySize] = None,
    col: Option[OverlaySize] = None,
    margin: OverlayMargin = OverlayMargin(),
    visible: (Int, Int) => Boolean = (_, _) => true,
    focusCapturing: Boolean = true
)

/** Stable identifier assigned to an overlay stack entry. */
final case class OverlayId(value: Long) extends AnyVal derives CanEqual

/** Resolved overlay geometry for a specific terminal size and rendered overlay height. */
final case class ResolvedOverlay(
    width: Int,
    row: Int,
    col: Int,
    maxHeight: Option[Int]
) derives CanEqual

/** Options for releasing focus from an overlay handle. */
final case class OverlayUnfocusOptions(target: Component | Null)

/** Handle returned by [[OverlayHost.showOverlay]] for controlling one overlay stack entry. */
trait OverlayHandle:
  def id: OverlayId
  def hide(): Unit
  def setHidden(hidden: Boolean): Unit
  def isHidden: Boolean
  def focus(): Unit
  def unfocus(options: Option[OverlayUnfocusOptions] = None): Unit
  def isFocused: Boolean
  def update(
      component: Component,
      options: Option[OverlayOptions] = None,
      requestRender: Boolean = true
  ): Unit

/** Capability for showing and managing overlays without depending on a concrete TUI runtime. */
trait OverlayHost:
  def showOverlay(component: Component, options: OverlayOptions = OverlayOptions()): OverlayHandle
  def hideOverlay(): Unit

  /**
   * Return the latest overlay visibility computed by the work-drain owner. This read never waits
   * for active application work and may be briefly stale while another drain action is running.
   */
  def hasOverlay: Boolean

/** Runtime services that components can use without depending on terminal backends. */
trait TUIContext:
  /** Queue a coalesced render intent. Force intent is preserved when requests merge. */
  def requestRender(force: Boolean = false): Unit

  /**
   * Drain synchronously when uncontended. A reentrant or concurrent call records follow-up work and
   * returns without recursive rendering or waiting for the active application callback.
   */
  def flushRender(): Unit
  def requestExit(): Unit
  def setFocus(component: Component | Null): Unit
  def overlays: OverlayHost

/** Component mix-in for receiving or clearing a TUI context when attached to a runtime. */
trait ContextualComponent:
  def tuiContext_=(value: Option[TUIContext]): Unit

/** Render origin supplied to components that need to place owned overlays near their output. */
final case class ComponentRenderOrigin(row: Int, col: Int = 0) derives CanEqual

/** Component mix-in for receiving its current terminal-relative render origin. */
trait RenderOriginAware:
  def renderOrigin_=(value: Option[ComponentRenderOrigin]): Unit

/**
 * Small shared JVM/Native vertical frame builder that tracks render origins for origin-aware
 * components.
 *
 * This is intentionally lightweight rather than a retained layout tree: it helps application and
 * demo code compose line-oriented frames while allowing components such as
 * [[scalatui.components.Editor]] to place owned overlays adjacent to their rendered area.
 * `startRow` and `startCol` notify [[RenderOriginAware]] components only. Returned control
 * coordinates remain frame-local and are rebased only by rows accumulated in this builder.
 */
final class ComponentFrameBuilder(width: Int, startRow: Int = 0, startCol: Int = 0):
  private val renderedLines    = Vector.newBuilder[String]
  private val renderedControls = Vector.newBuilder[TerminalControlPlacement]
  private var localRow         = 0

  /** Append ordinary lines and advance the local row without adding semantic controls. */
  def addLines(lines: Vector[String]): Unit =
    renderedLines ++= lines
    localRow += lines.length

  /** Append one ordinary line and advance the local row without adding semantic controls. */
  def addLine(line: String): Unit = addLines(Vector(line))

  /** Append a typed child frame and rebase controls only by accumulated local rows. */
  def addRender(frame: ComponentRender): Unit =
    renderedLines ++= frame.lines
    frame.controls.foreach(placement =>
      renderedControls += placement.translated(rowOffset = localRow)
    )
    localRow += frame.lines.length

  /**
   * Render and append a component, supplying its render origin when it supports that capability.
   */
  def addComponent(component: Component): Unit =
    component match
      case aware: RenderOriginAware =>
        aware.renderOrigin_=(Some(ComponentRenderOrigin(startRow + localRow, startCol)))
      case _                        => ()
    addRender(component.render(width))

  /**
   * Return accumulated ordinary lines and frame-relative controls.
   *
   * The caller or TUI must validate the returned control footprints against the final frame width.
   */
  def result(): ComponentRender = ComponentRender(renderedLines.result(), renderedControls.result())
