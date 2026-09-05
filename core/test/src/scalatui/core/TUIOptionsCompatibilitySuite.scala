package scalatui.core

class TUIOptionsCompatibilitySuite extends munit.FunSuite:
  test("legacy five-argument apply and extractor retain their source shape"):
    val observer  = Option.empty[TUIDiagnosticObserver]
    val direct    = TUIOptions(
      true,
      TUIScreenMode.Alternate,
      true,
      NormalResizeClearPolicy.PreserveScrollback,
      observer
    )
    val constructor: (
        Boolean,
        TUIScreenMode,
        Boolean,
        NormalResizeClearPolicy,
        Option[TUIDiagnosticObserver]
    ) => TUIOptions = TUIOptions.apply
    val expanded  = constructor(
      false,
      TUIScreenMode.Normal,
      false,
      NormalResizeClearPolicy.ClearScrollback,
      observer
    )
    val extracted = direct match
      case TUIOptions(a, b, c, d, e) => (a, b, c, d, e)

    assertEquals(
      extracted,
      (
        true,
        TUIScreenMode.Alternate,
        true,
        NormalResizeClearPolicy.PreserveScrollback,
        observer
      )
    )
    assertEquals(expanded.hardwareCursorPositioning, false)

  test("new named fields and case-class copy remain available"):
    val options = TUIOptions(
      mouseTracking = Some(scalatui.terminal.TerminalMouseTrackingOptions(
        scalatui.terminal.TerminalMouseTrackingMode.Drag
      )),
      kittyImageRetention = KittyImageRetentionOptions(maxEntries = 4)
    )
    val copied  = options.copy(mouseInput = true)

    assertEquals(copied.mouseInput, true)
    assertEquals(copied.kittyImageRetention.maxEntries, 4)
