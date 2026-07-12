package scalatui.core

/** Result delivered by a terminal protocol query. */
enum TerminalQueryResult[+A]:
  /** The terminal returned a valid matching response. */
  case Success(value: A)

  /** The terminal returned a recognized matching response with an invalid payload. */
  case InvalidResponse

  /** The query could not continue because the TUI was not running or stopped. */
  case Stopped

  /** Request emission or runtime processing failed. */
  case Failed(cause: Throwable)
