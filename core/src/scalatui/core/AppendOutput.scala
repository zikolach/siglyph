package scalatui.core

/** Typed result delivered exactly once for one [[TUI.appendToScrollback]] request. */
enum AppendResult derives CanEqual:
  /** Append output and retained-frame restoration were published as one owned terminal write. */
  case Published(rowCount: Int, controlCount: Int)

  /** The request was incompatible with current runtime state and emitted no append bytes. */
  case Rejected(reason: AppendRejection)

  /** Rendering or publication failed and normal fail-fast terminal cleanup began. */
  case Failed(cause: Throwable)

/** Bounded typed reasons for rejecting append-only output before publication. */
enum AppendRejection derives CanEqual:
  /** The runtime lifecycle cannot admit append work. */
  case LifecycleUnavailable(state: TUIDiagnosticLifecycleState)

  /** Append-only shell scrollback is unavailable in alternate-screen mode. */
  case AlternateScreen

  /** The configured normal-screen resize policy may erase scrollback. */
  case ScrollbackClearingResizePolicy

  /** No retained live frame has yet been committed. */
  case NoCommittedFrame

  /** The submitted component already belongs to this TUI's retained tree or overlays. */
  case AttachedComponent

  /** Stop discarded an accepted operation before the owner claimed its component. */
  case StoppedBeforeClaim

  /** Stop invalidated a claimed candidate before its synchronized publication boundary. */
  case StoppedBeforePublication

  /** This TUI already has 64 accepted incomplete append operations. */
  case QueueCapacityExceeded

  /** The retained frame contains an iTerm2 control that cannot be relocated safely. */
  case RetainedITerm2Control
