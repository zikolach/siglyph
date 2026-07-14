package scalatui.demo

import scalatui.ansi.Ansi
import scalatui.components.*
import scalatui.core.{Component, Container}
import scalatui.terminal.{TerminalInput, TerminalKey}
import scalatui.unicode.Unicode

@main def asciinemaDemo(scenario: String): Unit =
  AsciinemaScenarios.run(scenario)

private object AsciinemaScenarios:
  private final case class TimedFrame(component: Component, hold: Double = 1.0)
  private final case class AgentScene(
      value: String,
      helper: String,
      suggestions: Vector[SelectItem],
      footer: String,
      hold: Double
  )

  private val Width       = 100
  private val PauseMillis = sys.env
    .get("SIGLYPH_ASCIINEMA_PAUSE_MS")
    .flatMap(_.toLongOption)
    .filter(_ >= 0L)
    .getOrElse(2000L)

  private val Reset   = Ansi.Reset
  private val Bold    = "\u001b[1m"
  private val Dim     = "\u001b[2m"
  private val Green   = "\u001b[32m"
  private val Cyan    = "\u001b[36m"
  private val Magenta = "\u001b[35m"
  private val Yellow  = "\u001b[33m"

  def run(scenario: String): Unit =
    scenario match
      case "agent-prompt"    => play(agentPromptFrames())
      case "command-palette" => play(commandPaletteFrames())
      case "unicode-input"   => play(unicodeInputFrames())
      case "all"             =>
        play(agentPromptFrames() ++ commandPaletteFrames() ++ unicodeInputFrames())
      case other             =>
        System.err.println(
          s"Unknown scenario '$other'. Expected agent-prompt, command-palette, unicode-input, or all."
        )
        sys.exit(2)

  private def play(frames: Vector[TimedFrame]): Unit =
    frames.foreach { frame =>
      print("\u001b[2J\u001b[H")
      frame.component.render(Width).lines.foreach(println)
      Console.out.flush()
      val pause = (PauseMillis * frame.hold).toLong
      if pause > 0L then Thread.sleep(pause)
    }
    print(Reset)
    Console.out.flush()

  private def agentPromptFrames(): Vector[TimedFrame] =
    val scenes =
      Vector(AgentScene(
        value = "",
        helper = "Start with an empty prompt.",
        suggestions = Vector.empty,
        footer = "The next frames type into a real siglyph Input component.",
        hold = 0.8
      )) ++
        typingScenes(
          base = "",
          typed = "/he",
          helper = "Typing command characters one by one.",
          suggestions = slashSuggestions(),
          footer = "Suggestions stay visible while the command prefix grows.",
          hold = 0.18,
          lastHold = 0.85
        ) ++
        Vector(AgentScene(
          value = "/help",
          helper = "Autocomplete accepted /help.",
          suggestions = Vector.empty,
          footer = "Now type the request text character by character.",
          hold = 0.8
        )) ++
        typingScenes(
          base = "/help",
          typed = " Please review ",
          helper = "Typing request text character by character.",
          suggestions = Vector.empty,
          footer = "This is plain text input, not autocomplete.",
          hold = 0.14,
          lastHold = 0.45
        ) ++
        typingScenes(
          base = "/help Please review ",
          typed = "@REA",
          helper = "Typing an @ attachment prefix.",
          suggestions = attachmentSuggestions(),
          footer = "Attachment suggestions wait until the typed prefix is visible.",
          hold = 0.18,
          lastHold = 0.9
        ) ++
        Vector(AgentScene(
          value = "/help Please review @README.md",
          helper = "Autocomplete accepted @README.md.",
          suggestions = Vector.empty,
          footer = "The accepted attachment becomes normal editor text.",
          hold = 0.8
        )) ++
        typingScenes(
          base = "/help Please review @README.md",
          typed = " and update ",
          helper = "Typing more request text character by character.",
          suggestions = Vector.empty,
          footer = "The prompt keeps growing like a normal editor buffer.",
          hold = 0.14,
          lastHold = 0.45
        ) ++
        typingScenes(
          base = "/help Please review @README.md and update ",
          typed = "#d",
          helper = "Typing a # tag prefix.",
          suggestions = tagSuggestions(),
          footer = "Tag suggestions stay separate from normal text typing.",
          hold = 0.18,
          lastHold = 0.85
        ) ++
        Vector(AgentScene(
          value = "/help Please review @README.md and update #docs",
          helper = "Autocomplete accepted #docs.",
          suggestions = Vector.empty,
          footer = "Continue typing another tag trigger.",
          hold = 0.75
        )) ++
        typingScenes(
          base = "/help Please review @README.md and update #docs ",
          typed = "#de",
          helper = "Typing a second # tag prefix.",
          suggestions = tagSuggestions(),
          footer = "The viewer can see typing before autocomplete acceptance.",
          hold = 0.18,
          lastHold = 0.85
        ) ++
        Vector(AgentScene(
          value = "/help Please review @README.md and update #docs #demo",
          helper = "Autocomplete accepted #demo.",
          suggestions = Vector.empty,
          footer = "The app controls what submitted text means.",
          hold = 0.9
        ))

    val sceneFrames = scenes.zipWithIndex.map { case (scene, index) =>
      TimedFrame(
        agentPromptFrame(
          step = s"${index + 1}/${scenes.length}",
          inputValue = scene.value,
          helper = scene.helper,
          suggestions = scene.suggestions,
          footer = scene.footer
        ),
        scene.hold
      )
    }

    Vector(
      TimedFrame(
        heroFrame(
          accent = Cyan,
          title = "siglyph agent composer",
          lines = Vector(
            "Build an AI prompt without leaving the terminal.",
            "Typing and autocomplete are separate visible steps."
          )
        ),
        1.4
      )
    ) ++ sceneFrames :+ TimedFrame(
      messageFrame(
        accent = Green,
        title = "siglyph agent composer",
        subtitle = "ready to send",
        body = Vector(
          s"${Green}✓${Reset} Command: /help",
          s"${Green}✓${Reset} Attachment: README.md",
          s"${Green}✓${Reset} Tags: #docs, #demo",
          "",
          s"${Bold}>${Reset} Please review README.md and update the demo docs.",
          "",
          s"${Dim}Typing happened before autocomplete acceptance.${Reset}"
        )
      ),
      1.6
    )

  private def typingScenes(
      base: String,
      typed: String,
      helper: String,
      suggestions: Vector[SelectItem],
      footer: String,
      hold: Double,
      lastHold: Double
  ): Vector[AgentScene] =
    val clusters = Unicode.graphemeClusters(typed)
    clusters.indices.toVector.map { index =>
      AgentScene(
        value = base + clusters.take(index + 1).mkString,
        helper = helper,
        suggestions = suggestions,
        footer = footer,
        hold = if index == clusters.length - 1 then lastHold else hold
      )
    }

  private def slashSuggestions(): Vector[SelectItem] =
    Vector(
      SelectItem("help", "/help", Some("show composer shortcuts")),
      SelectItem("handoff", "/handoff", Some("prepare session handoff")),
      SelectItem("history", "/history", Some("show recent prompts"))
    )

  private def attachmentSuggestions(): Vector[SelectItem] =
    Vector(
      SelectItem("readme", "@README.md", Some("project overview")),
      SelectItem("release", "@docs/release.md", Some("release checklist")),
      SelectItem("smoke", "@docs/interactive-smoke.md", Some("manual coverage"))
    )

  private def tagSuggestions(): Vector[SelectItem] =
    Vector(
      SelectItem("docs", "#docs", Some("documentation task")),
      SelectItem("demo", "#demo", Some("recording scenario")),
      SelectItem("debug", "#debug", Some("investigation"))
    )

  private def agentPromptFrame(
      step: String,
      inputValue: String,
      helper: String,
      suggestions: Vector[SelectItem],
      footer: String
  ): Component =
    val input = Input(inputValue)
    val body  = Vector.newBuilder[Component]
    body += text("Prompt", Bold)
    body += input
    body += spacer()
    if suggestions.nonEmpty then
      body += text("Autocomplete", Bold)
      body += selectList(Cyan, suggestions)
      body += spacer()
    body += text(footer, Dim)
    framed(
      accent = Cyan,
      title = "siglyph agent composer",
      subtitle = s"$step  $helper",
      body = body.result()
    )

  private def commandPaletteFrames(): Vector[TimedFrame] =
    val queryFrames = Vector("", "p", "pr", "prog").zipWithIndex.map { case (query, index) =>
      TimedFrame(
        framed(
          accent = Magenta,
          title = "command palette",
          subtitle = s"${index + 1}/4  Type to fuzzy-filter actions.",
          body = Vector(
            text(s"Palette query: ${if query.isEmpty then "_" else query}", Bold),
            commandPalette(query),
            spacer(),
            text("SelectList keeps the command list readable while the query changes.", Dim)
          )
        ),
        if query.isEmpty then 0.8 else 0.7
      )
    }

    val progressFrames = Vector(25, 50, 75, 100).zipWithIndex.map { case (percent, index) =>
      val loader =
        Loader(LoaderOptions(message = "Recording release demo", leadingBlankLine = false))
      loader.start()
      (0 to index).foreach(_ => loader.tick())
      TimedFrame(
        framed(
          accent = Magenta,
          title = "command palette",
          subtitle = s"Progress action selected. Rendering state changes now: $percent%.",
          body = Vector(
            text(s"${Green}✓${Reset} Progress on", Bold),
            loader,
            text(progressBar(percent), Yellow),
            text("No background thread is required; app code owns the ticks.", Dim)
          )
        ),
        0.7
      )
    }

    val settingsFrames = Vector(
      TimedFrame(
        settingsFrame(
          subtitle = "Open settings and start fuzzy filtering.",
          query = "",
          changed = false
        ),
        0.9
      ),
      TimedFrame(
        settingsFrame(
          subtitle = "Type 'rank' to isolate autocomplete behavior.",
          query = "rank",
          changed = false
        ),
        1.0
      ),
      TimedFrame(
        settingsFrame(
          subtitle = "Press Enter to cycle fuzzy ranking to stable.",
          query = "rank",
          changed = true
        ),
        1.4
      )
    )

    queryFrames ++ progressFrames ++ settingsFrames

  private def commandPalette(query: String): SelectList =
    val palette = selectList(
      Magenta,
      Vector(
        SelectItem("progress-on", "Progress on", Some("emit OSC 9;4 active")),
        SelectItem("progress-off", "Progress off", Some("clear terminal progress")),
        SelectItem("query-color", "Query color scheme", Some("runtime-owned terminal query")),
        SelectItem("title", "Set title", Some("optional backend capability"))
      ),
      filtering = SelectListFiltering.Fuzzy
    )
    typeText(palette, query)
    palette

  private def settingsFrame(subtitle: String, query: String, changed: Boolean): Component =
    val settings = SettingsList(
      Vector(
        SettingItem(
          id = "theme",
          label = "Theme",
          currentValue = "dark",
          description = Some("Cycles the demo palette label"),
          values = Vector("dark", "light")
        ),
        SettingItem(
          id = "ranking",
          label = "Autocomplete ranking",
          currentValue = "fuzzy",
          description = Some("Shows SettingsList fuzzy filtering"),
          values = Vector("fuzzy", "stable")
        ),
        SettingItem(
          id = "image-fallback",
          label = "Image fallback",
          currentValue = "text",
          values = Vector("text", "protocol")
        )
      ),
      SettingsListOptions(
        maxVisible = 3,
        filtering = SettingsListFiltering.Fuzzy,
        searchPrompt = "Settings filter: ",
        hintText = if changed then "Changed value is rendered immediately"
        else "Enter cycles the selected value"
      )
    )
    typeText(settings, query)
    if changed then settings.handleInput(TerminalInput.Key(TerminalKey.Enter))
    framed(
      accent = Magenta,
      title = "settings panel",
      subtitle = subtitle,
      body = Vector(
        settings,
        spacer(),
        text(
          if changed then s"${Green}✓${Reset} Autocomplete ranking changed to stable"
          else "SettingsList uses the same typed input model as other components.",
          if changed then Bold else Dim
        )
      )
    )

  private def unicodeInputFrames(): Vector[TimedFrame] =
    val typedValues  = Vector(
      "hello",
      "hello 世界",
      "hello 世界 👩🏽‍💻",
      "hello 世界 👩🏽‍💻 cafe\u0301"
    )
    val typingFrames = typedValues.zipWithIndex.map { case (value, index) =>
      TimedFrame(
        typedInputFrame(
          value = value,
          caption =
            s"${index + 1}/4  Type wide CJK cells, one emoji grapheme, and a combining mark.",
          events =
            Unicode.graphemeClusters(value).takeRight(5).map(cluster => s"key=Character($cluster)"),
          footer = "The input line is rendered by siglyph Input. Width stays stable."
        ),
        if index == 0 then 0.8 else 1.0
      )
    }

    val editedValue = editUnicodeInput()
    val editFrame   = TimedFrame(
      typedInputFrame(
        value = editedValue,
        caption = "Move left twice, then Backspace. One visible grapheme is removed.",
        events = Vector("key=Left", "key=Left", "key=Backspace removes one grapheme"),
        footer = "Backspace edits by grapheme cluster, not by UTF-16 code unit."
      ),
      1.3
    )

    val proofFrame = TimedFrame(
      typedInputFrame(
        value = editedValue,
        caption = "Typed terminal input is structured before app logic receives it.",
        events = Vector(
          "key=Insert",
          "key=Character(c) modifiers=ctrl",
          "key=Left",
          "paste 23 chars: release notes\\nchecklist"
        ),
        footer = "Insert, arrows, Ctrl keys, and paste arrive as typed TerminalInput values."
      ),
      1.4
    )

    Vector(TimedFrame(
      heroFrame(
        accent = Yellow,
        title = "Unicode + typed input proof",
        lines = Vector(
          "Human text is messy. Terminal input should still be precise.",
          "siglyph edits visible graphemes and reports typed keys."
        )
      ),
      1.2
    )) ++ typingFrames :+ editFrame :+ proofFrame

  private def editUnicodeInput(): String =
    val input = Input()
    Unicode.graphemeClusters("hello 世界 👩🏽‍💻 cafe\u0301").foreach { cluster =>
      input.handleInput(TerminalInput.Key(TerminalKey.Character(cluster)))
    }
    input.handleInput(TerminalInput.Key(TerminalKey.Left))
    input.handleInput(TerminalInput.Key(TerminalKey.Left))
    input.handleInput(TerminalInput.Key(TerminalKey.Backspace))
    input.value

  private def typedInputFrame(
      value: String,
      caption: String,
      events: Vector[String],
      footer: String
  ): Component =
    val eventText = events.map(line => s"${Dim}$line$Reset").mkString("\n")
    framed(
      accent = Yellow,
      title = "Unicode + typed input proof",
      subtitle = caption,
      body = Vector(
        text("Input buffer", Bold),
        Input(value),
        text(widthMeter(value), Dim),
        spacer(),
        text("Recent terminal input events", Bold),
        text(eventText),
        spacer(),
        text(footer, Dim)
      )
    )

  private def heroFrame(accent: String, title: String, lines: Vector[String]): Component =
    val root = Container()
    root.addChild(text(accent + borderTop(title) + Reset, Bold))
    root.addChild(text(borderedLine("", accent)))
    lines.foreach(line => root.addChild(text(borderedLine(s" ${Bold}$line$Reset", accent))))
    root.addChild(text(borderedLine("", accent)))
    root.addChild(text(accent + borderBottom() + Reset))
    root.addChild(spacer())
    root.addChild(
      text(s"${Dim}Recorded locally with asciinema. Rendered by siglyph components.${Reset}")
    )
    root

  private def messageFrame(
      accent: String,
      title: String,
      subtitle: String,
      body: Vector[String]
  ): Component =
    framed(
      accent = accent,
      title = title,
      subtitle = subtitle,
      body = Vector(text(body.mkString("\n")))
    )

  private def framed(
      accent: String,
      title: String,
      subtitle: String,
      body: Vector[Component]
  ): Component =
    val root = Container()
    root.addChild(text(accent + borderTop(title) + Reset, Bold))
    root.addChild(text(borderedLine(s" $subtitle", accent)))
    root.addChild(text(accent + borderBottom() + Reset))
    root.addChild(spacer())
    val box  = Box(paddingX = 2, paddingY = 1)
    body.foreach(box.addChild)
    root.addChild(box)
    root

  private def selectList(
      accent: String,
      items: Vector[SelectItem],
      filtering: SelectListFiltering = SelectListFiltering.Disabled
  ): SelectList =
    SelectList(
      items,
      SelectListOptions(
        maxVisible = 5,
        selectedPrefix = s"$accent▶ $Reset",
        normalPrefix = "  ",
        filtering = filtering,
        showScrollInfo = false
      )
    )

  private def progressBar(percent: Int): String =
    val filled = math.max(0, math.min(20, percent / 5))
    val empty  = 20 - filled
    s"Terminal progress: [${"█".repeat(filled)}${"░".repeat(empty)}] $percent%"

  private def widthMeter(value: String): String =
    val width = Unicode.stringWidth(value)
    s"visible width: $width columns  │  graphemes: ${Unicode.graphemeClusters(value).length}"

  private def borderedLine(content: String, accent: String): String =
    val contentWidth = Width - 2
    val bounded      = Ansi.truncateToWidth(content, contentWidth, "")
    s"$accent│$Reset" + Ansi.padRight(bounded, contentWidth) + s"$accent│$Reset"

  private def borderTop(title: String): String =
    val prefix = s"╭─ $title "
    val suffix = "╮"
    prefix + "─".repeat(math.max(
      1,
      Width - Ansi.visibleWidth(prefix) - Ansi.visibleWidth(suffix)
    )) + suffix

  private def borderBottom(): String =
    "╰" + "─".repeat(Width - 2) + "╯"

  private def typeText(component: Component, value: String): Unit =
    Unicode.graphemeClusters(value).foreach { cluster =>
      component.handleInput(TerminalInput.Key(TerminalKey.Character(cluster)))
    }

  private def text(value: String, style: String = ""): Text =
    val styled = if style.isEmpty then value else style + value + Reset
    Text(styled, paddingX = 0)

  private def spacer(): Spacer = Spacer(1)
