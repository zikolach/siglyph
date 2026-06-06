package scalatui.demo

import scalatui.components.*
import scalatui.core.TUI
import scalatui.terminal.StreamTerminal

@main def mvpDemo(): Unit =
  val tui = TUI(StreamTerminal(output = System.out, initialColumns = 80, initialRows = 24))

  tui.addChild(TruncatedText(
    "scala-tui component showcase — this deliberately long title is truncated to fit",
    paddingX = 1,
    paddingY = 1
  ))

  val box = Box(paddingX = 2, paddingY = 1)
  box.addChild(Text(
    "Core components now include Text, Box, Spacer, SelectList, Input, TruncatedText, and SettingsList. Loader components are shown in the interactive demo where ticks/cancellation can change the UI.",
    paddingX = 0
  ))
  tui.addChild(box)

  tui.addChild(Spacer(1))
  tui.addChild(Text("SettingsList preview:", paddingX = 0))
  tui.addChild(SettingsList(
    Vector(
      SettingItem(
        id = "theme",
        label = "Theme",
        currentValue = "dark",
        description = Some("Rows can include descriptions and cycle values."),
        values = Vector("dark", "light")
      ),
      SettingItem(
        id = "density",
        label = "Density",
        currentValue = "comfortable",
        values = Vector("compact", "comfortable", "spacious")
      ),
      SettingItem(
        id = "telemetry",
        label = "Telemetry",
        currentValue = "off",
        values = Vector("off", "on")
      )
    ),
    SettingsListOptions(maxVisible = 3, showHints = true)
  ))

  tui.addChild(Spacer(1))
  tui.addChild(SelectList(
    Vector(
      SelectItem("text", "Text"),
      SelectItem("settings", "SettingsList"),
      SelectItem("input", "Input")
    ),
    maxVisible = 3
  ))

  tui.addChild(Spacer(1))
  val input = Input("type here")
  input.focused = true
  tui.addChild(input)

  tui.start()
  tui.stop()
