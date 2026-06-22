package scalatui.components

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import scalatui.autocomplete.AutocompleteProvider
import scalatui.autocomplete.AutocompleteRequestHandle
import scalatui.core.{OverlayAnchor, OverlayOptions, OverlaySize}
import scalatui.terminal.KeybindingManager

/** Schedules refresh-triggered autocomplete work so rapid editor edits can be coalesced. */
trait EditorAutocompleteDebouncer:
  def schedule(action: () => Unit): EditorAutocompleteDebouncer.ScheduleResult

object EditorAutocompleteDebouncer:
  sealed trait ScheduleResult:
    def handle: AutocompleteRequestHandle
    def ranSynchronously: Boolean

  final case class Pending(handle: AutocompleteRequestHandle) extends ScheduleResult:
    override val ranSynchronously: Boolean = false

  case object RanSynchronously extends ScheduleResult:
    override val handle: AutocompleteRequestHandle = AutocompleteRequestHandle.Noop
    override val ranSynchronously: Boolean         = true

  /**
   * Immediate scheduler useful for deterministic tests and applications that do their own debounce.
   */
  val Immediate: EditorAutocompleteDebouncer = action =>
    action()
    RanSynchronously

  /** Executor-backed debounce scheduler for applications that choose to provide a scheduler. */
  final case class Delayed(
      executor: ScheduledExecutorService,
      delayMillis: Long = 30L
  ) extends EditorAutocompleteDebouncer:
    override def schedule(action: () => Unit): ScheduleResult =
      val future = executor.schedule(
        new Runnable:
          override def run(): Unit = action()
        ,
        math.max(0L, delayMillis),
        TimeUnit.MILLISECONDS
      )
      Pending(() => future.cancel(false))

/**
 * Public configuration for [[Editor]].
 *
 * @param enterBehavior
 *   how Enter and modified Enter events choose between newline insertion and submit callbacks
 * @param onChange
 *   callback invoked after input changes the editor text
 * @param onSubmit
 *   callback invoked when the configured submit key is received
 * @param autocompleteProvider
 *   optional provider used to populate overlay-backed editor suggestions
 * @param autocompleteMaxVisible
 *   maximum number of suggestion rows shown by the autocomplete overlay
 * @param autocompleteTrigger
 *   automatic trigger policy for autocomplete requests
 * @param autocompletePlacement
 *   placement strategy used for autocomplete suggestions; defaults to editor-adjacent placement
 * @param autocompleteDebouncer
 *   scheduler used to coalesce refresh-triggered autocomplete after edits; explicit Tab requests
 *   are immediate. The default is immediate; applications can inject
 *   [[EditorAutocompleteDebouncer.Delayed]] with a scheduler they own.
 * @param autoApplySingleForcedCompletion
 *   when true, an explicit forced autocomplete request with exactly one suggestion applies that
 *   suggestion immediately through the provider completion contract. The default keeps explicit
 *   selection behavior.
 * @param keybindings
 *   command to input mapping resolved through the shared keybinding manager
 */
final case class EditorOptions(
    enterBehavior: EditorEnterBehavior = EditorEnterBehavior.Default,
    onChange: String => Unit = _ => (),
    onSubmit: String => Unit = _ => (),
    autocompleteProvider: Option[AutocompleteProvider] = None,
    autocompleteMaxVisible: Int = 5,
    autocompleteTrigger: EditorAutocompleteTrigger = EditorAutocompleteTrigger.Default,
    autocompletePlacement: EditorAutocompletePlacement = EditorAutocompletePlacement.Default,
    autocompleteDebouncer: EditorAutocompleteDebouncer = EditorAutocompleteDebouncer.Immediate,
    autoApplySingleForcedCompletion: Boolean = false,
    keybindings: KeybindingManager = KeybindingManager()
)

object EditorOptions:
  val FallbackAutocompleteOverlayOptions: OverlayOptions = OverlayOptions(
    width = Some(OverlaySize.Percent(100)),
    anchor = OverlayAnchor.BottomLeft,
    focusCapturing = true
  )
