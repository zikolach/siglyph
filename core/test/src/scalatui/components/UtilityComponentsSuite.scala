package scalatui.components

import scalatui.ansi.Ansi
import scalatui.core.{
  Component,
  InputResult,
  OverlayHandle,
  OverlayHost,
  OverlayId,
  OverlayOptions,
  OverlayUnfocusOptions,
  TUIContext
}
import scalatui.terminal.{TerminalInput, TerminalKey}

class UtilityComponentsSuite extends munit.FunSuite:
  private final class TestOverlayHandle(
      override val id: OverlayId,
      var component: Component,
      var options: OverlayOptions
  ) extends OverlayHandle:
    var hidden         = false
    var focused        = false
    var unfocusOptions = Option.empty[OverlayUnfocusOptions]
    var calls          = Vector.empty[String]

    override def hide(): Unit                                          =
      calls :+= "hide"
      hidden = true
    override def setHidden(value: Boolean): Unit                       = hidden = value
    override def isHidden: Boolean                                     = hidden
    override def focus(): Unit                                         = focused = options.focusCapturing
    override def unfocus(options: Option[OverlayUnfocusOptions]): Unit =
      calls :+= "unfocus"
      focused = false
      unfocusOptions = options
    override def isFocused: Boolean                                    = focused
    override def update(
        component: Component,
        options: Option[OverlayOptions],
        requestRender: Boolean
    ): Unit =
      this.component = component
      options.foreach(value => this.options = value)

  private final class TestOverlayHost extends OverlayHost:
    var shown                                                                              = Vector.empty[TestOverlayHandle]
    override def showOverlay(component: Component, options: OverlayOptions): OverlayHandle =
      val handle = TestOverlayHandle(OverlayId(shown.length.toLong + 1), component, options)
      shown :+= handle
      handle
    override def hideOverlay(): Unit                                                       = shown.lastOption.foreach(_.hide())
    override def hasOverlay: Boolean                                                       = shown.exists(!_.hidden)

  private final class TestContext extends TUIContext:
    val overlayHost               = TestOverlayHost()
    var focused: Component | Null = null
    var renderRequests            = 0
    var flushes                   = 0
    var exits                     = 0

    override def requestRender(force: Boolean): Unit         = renderRequests += 1
    override def flushRender(): Unit                         = flushes += 1
    override def requestExit(): Unit                         = exits += 1
    override def setFocus(component: Component | Null): Unit = focused = component
    override def overlays: OverlayHost                       = overlayHost

  private final class TestSubmenu extends Component:
    var controller                                                    = Option.empty[SettingsSubmenuController]
    override def render(width: Int): Vector[String]                   = Vector("submenu")
    override def handleInputResult(input: TerminalInput): InputResult = input match
      case TerminalInput.Key(TerminalKey.Escape, _) =>
        controller.foreach(_.cancel())
        InputResult.Render
      case _                                        => InputResult.Ignored

  test("truncated text uses first line and pads to width"):
    val text  = TruncatedText("hello\nworld", paddingX = 1, paddingY = 1)
    val lines = text.render(8)

    assertEquals(lines.length, 3)
    assertEquals(Ansi.visibleWidth(lines.head), 8)
    assertEquals(Ansi.strip(lines(1)).trim, "hello")
    assert(!Ansi.strip(lines.mkString("\n")).contains("world"))
    assert(lines.forall(Ansi.visibleWidth(_) <= 8), lines.toString)

  test("truncated text truncates ANSI and Unicode content within narrow widths"):
    val text = TruncatedText("\u001b[31mabcdef\u001b[0m🙂")

    val five = text.render(5).head
    assert(Ansi.visibleWidth(five) <= 5, five)
    assert(Ansi.strip(five).startsWith("abcde"), Ansi.strip(five))

    val one = text.render(1).head
    assert(Ansi.visibleWidth(one) <= 1, one)

  test("truncated text can be mutated"):
    val text = TruncatedText("old", paddingX = 0)
    assertEquals(Ansi.strip(text.render(10).head).trim, "old")

    text.text = "new value"
    assertEquals(Ansi.strip(text.render(10).head).trim, "new value")

  test("settings list renders labels, values, descriptions, and hints width-safely"):
    val list = SettingsList(
      Vector(
        SettingItem(
          "theme",
          "Theme",
          "dark",
          Some("Choose the color theme"),
          Vector("dark", "light")
        ),
        SettingItem("mode", "Mode", "safe")
      ),
      SettingsListOptions(maxVisible = 5)
    )

    val lines = list.render(28)
    assert(lines.exists(line => Ansi.strip(line).contains("Theme")), lines.toString)
    assert(lines.exists(line => Ansi.strip(line).contains("dark")), lines.toString)
    assert(lines.exists(line => Ansi.strip(line).contains("Choose")), lines.toString)
    assert(lines.exists(line => Ansi.strip(line).contains("Enter/Space")), lines.toString)
    assert(lines.forall(Ansi.visibleWidth(_) <= 28), lines.toString)

  test("settings list renders empty and no-match states"):
    val empty = SettingsList(Vector.empty, SettingsListOptions(showHints = false))
    assertEquals(Ansi.strip(empty.render(20).head), "No settings")

    val filtered = SettingsList(
      Vector(SettingItem("theme", "Theme", "dark")),
      SettingsListOptions(filteringEnabled = true, showHints = false)
    )
    filtered.handleInput(TerminalInput.Key(TerminalKey.Character("z")))

    assert(filtered.render(20).exists(line => Ansi.strip(line).contains("No matching")))

  test("settings list scrolls visible rows and reports scroll range"):
    val list = SettingsList(
      Vector(
        SettingItem("a", "Alpha", "1"),
        SettingItem("b", "Beta", "2"),
        SettingItem("c", "Gamma", "3"),
        SettingItem("d", "Delta", "4")
      ),
      SettingsListOptions(maxVisible = 2, showHints = false)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    list.handleInput(TerminalInput.Key(TerminalKey.Down))

    val stripped = list.render(20).map(Ansi.strip)
    assert(stripped.exists(_.contains("Beta")), stripped.toString)
    assert(stripped.exists(_.contains("Gamma")), stripped.toString)
    assert(stripped.exists(_.contains("2-3 of 4")), stripped.toString)
    assert(stripped.forall(Ansi.visibleWidth(_) <= 20), stripped.toString)

  test("settings list handles narrow widths"):
    val list = SettingsList(
      Vector(SettingItem(
        "long",
        "Very long setting label",
        "very long value",
        Some("Long description")
      )),
      SettingsListOptions(maxVisible = 1)
    )

    val lines = list.render(1)
    assert(lines.nonEmpty)
    assert(lines.forall(Ansi.visibleWidth(_) <= 1), lines.toString)

  test("settings list navigates, cycles values, and invokes callbacks"):
    val list    = SettingsList(
      Vector(
        SettingItem("theme", "Theme", "dark", values = Vector("dark", "light")),
        SettingItem("mode", "Mode", "safe", values = Vector("safe", "fast"))
      ),
      SettingsListOptions(showHints = false)
    )
    var changes = Vector.empty[(String, String)]
    list.onChange = (id, value) => changes :+= (id, value)

    assertEquals(list.handleInputResult(TerminalInput.Key(TerminalKey.Down)), InputResult.Render)
    assertEquals(list.selected.map(_.id), Some("mode"))
    assertEquals(list.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)
    assertEquals(list.selected.map(_.currentValue), Some("fast"))
    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Character(" "))),
      InputResult.Render
    )
    assertEquals(list.selected.map(_.currentValue), Some("safe"))
    assertEquals(changes, Vector(("mode", "fast"), ("mode", "safe")))

  test("settings list cancels and ignores unsupported input"):
    val list      = SettingsList(Vector(SettingItem("theme", "Theme", "dark")))
    var cancelled = false
    list.onCancel = () => cancelled = true

    assertEquals(list.handleInputResult(TerminalInput.Key(TerminalKey.Tab)), InputResult.Ignored)
    assertEquals(list.handleInputResult(TerminalInput.Key(TerminalKey.Escape)), InputResult.Render)
    assertEquals(cancelled, true)

  test("settings list enter opens submenu through overlay host"):
    val context        = TestContext()
    val submenu        = TestSubmenu()
    val overlayOptions = OverlayOptions(offsetX = 2, offsetY = 3, focusCapturing = false)
    val list           = SettingsList(Vector(SettingItem(
      "theme",
      "Theme",
      "dark",
      submenu = Some(SettingsSubmenu(
        submenu,
        options = overlayOptions,
        onOpen = controller => submenu.controller = Some(controller)
      ))
    )))
    list.tuiContext_=(Some(context))

    assertEquals(list.handleInputResult(TerminalInput.Key(TerminalKey.Enter)), InputResult.Render)

    assertEquals(context.overlayHost.shown.map(_.component), Vector(submenu))
    assertEquals(context.overlayHost.shown.head.options.offsetX, 2)
    assertEquals(context.overlayHost.shown.head.options.offsetY, 3)
    assertEquals(context.overlayHost.shown.head.options.focusCapturing, false)
    assertEquals(context.overlayHost.shown.head.isFocused, false)
    assertEquals(context.focused, null)
    assert(submenu.controller.nonEmpty)

  test("settings list detaching context closes active submenu without restoring focus"):
    val context = TestContext()
    val submenu = TestSubmenu()
    val list    = SettingsList(Vector(SettingItem(
      "theme",
      "Theme",
      "dark",
      submenu =
        Some(SettingsSubmenu(submenu, onOpen = controller => submenu.controller = Some(controller)))
    )))
    list.tuiContext_=(Some(context))

    list.handleInput(TerminalInput.Key(TerminalKey.Enter))
    list.tuiContext_=(None)

    assertEquals(context.overlayHost.shown.head.calls, Vector("unfocus", "hide"))
    assertEquals(context.overlayHost.shown.head.unfocusOptions.map(_.target), Some(null))
    assertEquals(context.overlayHost.shown.head.isHidden, true)
    assertEquals(context.focused, null)

  test("settings list submenu commit changes value invokes callback and restores focus"):
    val context = TestContext()
    val submenu = TestSubmenu()
    val list    = SettingsList(Vector(SettingItem(
      "theme",
      "Theme",
      "dark",
      submenu =
        Some(SettingsSubmenu(submenu, onOpen = controller => submenu.controller = Some(controller)))
    )))
    var changes = Vector.empty[(String, String)]
    list.onChange = (id, value) => changes :+= (id, value)
    list.tuiContext_=(Some(context))

    list.handleInput(TerminalInput.Key(TerminalKey.Enter))
    submenu.controller.foreach(_.commitValue("light"))

    assertEquals(list.items.head.currentValue, "light")
    assertEquals(changes, Vector(("theme", "light")))
    assertEquals(context.overlayHost.shown.head.isHidden, true)
    assertEquals(context.focused, list)

  test("settings list submenu escape cancels and restores focus"):
    val context = TestContext()
    val submenu = TestSubmenu()
    val list    = SettingsList(Vector(SettingItem(
      "theme",
      "Theme",
      "dark",
      submenu =
        Some(SettingsSubmenu(submenu, onOpen = controller => submenu.controller = Some(controller)))
    )))
    var changes = Vector.empty[(String, String)]
    list.onChange = (id, value) => changes :+= (id, value)
    list.tuiContext_=(Some(context))

    list.handleInput(TerminalInput.Key(TerminalKey.Enter))
    assertEquals(
      submenu.handleInputResult(TerminalInput.Key(TerminalKey.Escape)),
      InputResult.Render
    )
    submenu.controller.foreach(_.commitValue("light"))

    assertEquals(list.items.head.currentValue, "dark")
    assertEquals(changes, Vector.empty)
    assertEquals(context.overlayHost.shown.head.isHidden, true)
    assertEquals(context.focused, list)

  test("settings list submenu rows do not cycle scalar values"):
    val context = TestContext()
    val submenu = TestSubmenu()
    val list    = SettingsList(Vector(SettingItem(
      "mode",
      "Mode",
      "safe",
      values = Vector("safe", "fast"),
      submenu =
        Some(SettingsSubmenu(submenu, onOpen = controller => submenu.controller = Some(controller)))
    )))
    var changes = Vector.empty[(String, String)]
    list.onChange = (id, value) => changes :+= (id, value)
    list.tuiContext_=(Some(context))

    assertEquals(
      list.handleInputResult(TerminalInput.Key(TerminalKey.Character(" "))),
      InputResult.Render
    )

    assertEquals(list.items.head.currentValue, "safe")
    assertEquals(changes, Vector.empty)
    assertEquals(context.overlayHost.shown.map(_.component), Vector(submenu))

  test("settings list filters with dependency-free containment search"):
    val list = SettingsList(
      Vector(
        SettingItem("theme", "Theme", "dark"),
        SettingItem("color", "Color", "blue"),
        SettingItem("mode", "Mode", "safe")
      ),
      SettingsListOptions(filteringEnabled = true, showHints = false)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("c")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("o")))

    assertEquals(list.query, "co")
    assertEquals(list.selected.map(_.id), Some("color"))
    val stripped = list.render(20).map(Ansi.strip).mkString("\n")
    assert(stripped.contains("Search: co"), stripped)
    assert(stripped.contains("Color"), stripped)
    assert(!stripped.contains("Theme"), stripped)

    list.handleInput(TerminalInput.Key(TerminalKey.Backspace))
    assertEquals(list.query, "c")

  test("settings list fuzzy filtering ranks rows width-safely"):
    val list = SettingsList(
      Vector(
        SettingItem("fuzzy-boilerplate", "Fuzzy Boilerplate", "off"),
        SettingItem("fast-boat", "Fast Boat", "off"),
        SettingItem("foo-bar", "Foo Bar", "on")
      ),
      SettingsListOptions(
        filtering = SettingsListFiltering.Fuzzy,
        showHints = false,
        showScrollIndicators = false
      )
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("f")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("b")))

    assertEquals(list.selected.map(_.id), Some("foo-bar"))
    val lines                 = list.render(24)
    val rendered              = lines.map(Ansi.strip).mkString("\n")
    val fooBarIndex           = rendered.indexOf("Foo Bar")
    val fastBoatIndex         = rendered.indexOf("Fast Boat")
    val fuzzyBoilerplateIndex = rendered.indexOf("Fuzzy Boilerplate")
    assert(fooBarIndex >= 0, rendered)
    assert(fastBoatIndex >= 0, rendered)
    assert(fuzzyBoilerplateIndex >= 0, rendered)
    assert(fooBarIndex < fastBoatIndex, rendered)
    assert(fastBoatIndex < fuzzyBoilerplateIndex, rendered)
    assert(lines.forall(Ansi.visibleWidth(_) <= 24), lines.toString)

  test("settings list fuzzy filtering searches id label current value and description"):
    def renderedFor(query: String): String =
      val list = SettingsList(
        Vector(
          SettingItem("id-only-token", "Alpha", "one"),
          SettingItem("beta", "label-only-token", "two"),
          SettingItem("gamma", "Gamma", "value-only-token"),
          SettingItem("delta", "Delta", "four", Some("description-only-token"))
        ),
        SettingsListOptions(filtering = SettingsListFiltering.Fuzzy, showHints = false)
      )
      query.foreach(ch => list.handleInput(TerminalInput.Key(TerminalKey.Character(ch.toString))))
      Ansi.strip(list.render(80).mkString("\n"))

    assert(renderedFor("iot").contains("Alpha"))
    assert(renderedFor("lot").contains("label-only-token"))
    assert(renderedFor("vot").contains("value-only-token"))
    assert(renderedFor("dot").contains("Delta"))

  test("settings list containment filtering remains available without fuzzy ranking"):
    val list = SettingsList(
      Vector(SettingItem("foo", "fooBar", "on"), SettingItem("fast", "fast-boat", "off")),
      SettingsListOptions(filteringEnabled = true, showHints = false)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("f")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("b")))

    val rendered = Ansi.strip(list.render(40).mkString("\n"))
    assert(rendered.contains("No matching settings"), rendered)
    assert(!rendered.contains("fooBar"), rendered)
    assertEquals(list.selected, None)

  test("settings list explicit containment filtering does not fuzzy-rank"):
    val list = SettingsList(
      Vector(SettingItem("foo", "fooBar", "on"), SettingItem("fast", "fast-boat", "off")),
      SettingsListOptions(filtering = SettingsListFiltering.Containment, showHints = false)
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("f")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("b")))

    val rendered = Ansi.strip(list.render(40).mkString("\n"))
    assert(rendered.contains("No matching settings"), rendered)
    assert(!rendered.contains("fooBar"), rendered)
    assertEquals(list.selected, None)

  test("settings list explicit fuzzy filtering takes precedence over legacy containment flag"):
    val list = SettingsList(
      Vector(
        SettingItem("fuzzy-boilerplate", "Fuzzy Boilerplate", "off"),
        SettingItem("fast-boat", "Fast Boat", "off"),
        SettingItem("foo-bar", "Foo Bar", "on")
      ),
      SettingsListOptions(
        filteringEnabled = true,
        filtering = SettingsListFiltering.Fuzzy,
        showHints = false,
        showScrollIndicators = false
      )
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("f")))
    list.handleInput(TerminalInput.Key(TerminalKey.Character("b")))

    val rendered = Ansi.strip(list.render(40).mkString("\n"))
    assert(rendered.contains("Foo Bar"), rendered)
    assertEquals(list.selected.map(_.id), Some("foo-bar"))

  test("settings list fuzzy filtering renders no-match text for empty matches"):
    val list = SettingsList(
      Vector(SettingItem("alpha", "Alpha", "one")),
      SettingsListOptions(
        filtering = SettingsListFiltering.Fuzzy,
        noMatchesText = "No fuzzy settings",
        showHints = false
      )
    )

    list.handleInput(TerminalInput.Key(TerminalKey.Character("z")))

    val lines    = list.render(18)
    val rendered = Ansi.strip(lines.mkString("\n"))
    assert(rendered.contains("No fuzzy settings"), rendered)
    assert(!rendered.contains("Alpha"), rendered)
    assert(lines.forall(Ansi.visibleWidth(_) <= 18), lines.toString)

  test("loader renders default message and indicator width-safely"):
    val loader = Loader()
    val lines  = loader.render(20)

    assertEquals(lines.length, 2)
    assertEquals(lines.head, "")
    assert(lines.exists(line => Ansi.strip(line).contains("Loading")), lines.toString)
    assert(lines.exists(line => Ansi.strip(line).contains("⠋")), lines.toString)
    assert(lines.forall(Ansi.visibleWidth(_) <= 20), lines.toString)

  test("loader hides empty indicator"):
    val loader = Loader(LoaderOptions(indicator = LoaderIndicatorOptions(frames = Vector.empty)))
    val line   = Ansi.strip(loader.render(20).last).trim

    assertEquals(line, "Loading...")

  test("loader preserves ANSI styling and handles Unicode narrow widths"):
    val loader = Loader(LoaderOptions(
      message = "Working 🙂",
      indicator = LoaderIndicatorOptions(frames = Vector("*")),
      indicatorStyle = value => s"\u001b[31m$value\u001b[0m",
      messageStyle = value => s"\u001b[2m$value\u001b[0m"
    ))

    val wide = loader.render(20).last
    assert(wide.contains("\u001b[31m"), wide)
    assert(wide.contains("\u001b[2m"), wide)
    assert(Ansi.visibleWidth(wide) <= 20, wide)

    val narrow = loader.render(1)
    assert(narrow.forall(Ansi.visibleWidth(_) <= 1), narrow.toString)

  test("loader lifecycle and tick behavior are deterministic"):
    val loader =
      Loader(LoaderOptions(indicator = LoaderIndicatorOptions(Vector("a", "b"), intervalMs = 25)))

    assertEquals(loader.running, false)
    assertEquals(loader.frame, "a")
    assertEquals(loader.tick(), false)
    assertEquals(loader.frame, "a")

    loader.start()
    loader.start()
    assertEquals(loader.running, true)
    assertEquals(loader.tick(), true)
    assertEquals(loader.frame, "b")
    assertEquals(loader.tick(), true)
    assertEquals(loader.frame, "a")

    loader.stop()
    loader.stop()
    assertEquals(loader.running, false)
    assertEquals(loader.tick(), false)
    assertEquals(loader.frame, "a")
    assertEquals(loader.intervalMs, 25)

  test("loader mutations update message and reset indicator frames"):
    val loader = Loader(LoaderOptions(indicator = LoaderIndicatorOptions(Vector("a", "b"))))
    loader.start()
    loader.tick()
    assertEquals(loader.frame, "b")

    loader.setMessage("Done")
    assert(Ansi.strip(loader.render(20).last).contains("Done"))

    loader.setIndicator(LoaderIndicatorOptions(Vector("x", "y"), intervalMs = -1))
    assertEquals(loader.frame, "x")
    assertEquals(loader.intervalMs, Loader.DefaultIntervalMs)
    assert(Ansi.strip(loader.render(20).last).contains("x"))

  test("cancellable loader handles escape and exposes cancellation token"):
    val loader = CancellableLoader()
    var calls  = 0
    loader.onCancel = () => calls += 1

    assertEquals(loader.cancelled, false)
    assertEquals(loader.token.isCancelled, false)
    assertEquals(
      loader.handleInputResult(TerminalInput.Key(TerminalKey.Escape)),
      InputResult.Render
    )
    assertEquals(loader.cancelled, true)
    assertEquals(loader.aborted, true)
    assertEquals(loader.token.cancelled, true)
    assertEquals(calls, 1)

    assertEquals(
      loader.handleInputResult(TerminalInput.Key(TerminalKey.Escape)),
      InputResult.NoRender
    )
    assertEquals(loader.cancel(), false)
    assertEquals(calls, 1)

  test("cancellable loader ignores non-cancel input and keeps inherited rendering width-safe"):
    val loader = CancellableLoader(LoaderOptions(
      message = "Cancel me",
      indicator = LoaderIndicatorOptions(Vector("!"))
    ))

    assertEquals(
      loader.handleInputResult(TerminalInput.Key(TerminalKey.Enter)),
      InputResult.Ignored
    )
    assertEquals(loader.cancelled, false)
    assert(loader.render(8).forall(Ansi.visibleWidth(_) <= 8))
