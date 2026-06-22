package scalatui.terminal

class KeybindingManagerSuite extends munit.FunSuite:
  test("matches typed input to default command definitions") {
    val manager = KeybindingManager()
    assert(manager.matches(TerminalInput.Key(TerminalKey.Up), KeybindingCommand.EditorCursorUp))
    assert(manager.matches(TerminalInput.Key(TerminalKey.Enter), KeybindingCommand.InputSubmit))
    assert(
      manager.matches(
        TerminalInput.Key(TerminalKey.Character("]"), KeyModifiers(ctrl = true)),
        KeybindingCommand.EditorJumpForward
      )
    )
  }

  test("ignores key-release events when matching keybindings") {
    val manager = KeybindingManager()
    assert(!manager.matches(
      TerminalInput.KeyEvent(
        key = TerminalKey.Enter,
        eventType = KeyEventType.Release
      ),
      KeybindingCommand.InputSubmit
    ))
  }

  test("supports override keybindings from typed command map") {
    val manager = KeybindingManager().withUserBindings(
      Map(
        KeybindingCommand.InputSubmit -> Vector(
          KeyDescriptor(TerminalKey.Character("s"), KeyModifiers(ctrl = true))
        )
      )
    )

    assert(!manager.matches(TerminalInput.Key(TerminalKey.Enter), KeybindingCommand.InputSubmit))
    assert(manager.matches(
      TerminalInput.Key(TerminalKey.Character("s"), KeyModifiers(ctrl = true)),
      KeybindingCommand.InputSubmit
    ))
  }

  test("ignores unknown command ids in raw binding configuration") {
    val manager = KeybindingManager.fromRawBindings(
      Map(
        "tui.unknown.command"           -> Vector(KeyDescriptor(
          TerminalKey.Character("x"),
          KeyModifiers(ctrl = true)
        )),
        KeybindingCommand.EditorUndo.id -> Vector(KeyDescriptor(
          TerminalKey.Character("z"),
          KeyModifiers(ctrl = true)
        ))
      )
    )

    assert(manager.matches(
      TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(ctrl = true)),
      KeybindingCommand.EditorUndo
    ))
    assertEquals(manager.getResolvedKeys("tui.unknown.command"), None)
  }

  test("detects command binding conflicts") {
    val manager = KeybindingManager.fromRawBindings(
      Map(
        KeybindingCommand.EditorCursorUp.id -> Vector(
          KeyDescriptor(TerminalKey.Character("x"), KeyModifiers(ctrl = true)),
          KeyDescriptor(TerminalKey.PageUp)
        ),
        KeybindingCommand.EditorPageUp.id   -> Vector(
          KeyDescriptor(TerminalKey.PageUp),
          KeyDescriptor(TerminalKey.Character("p"), KeyModifiers(ctrl = true))
        )
      )
    )

    val conflict = manager.getConflicts.find(_.key == KeyDescriptor(TerminalKey.PageUp)).get
    assert(conflict.commands.toSet.contains(KeybindingCommand.EditorCursorUp))
    assert(conflict.commands.toSet.contains(KeybindingCommand.EditorPageUp))
  }

  test("allows disabling a command by binding an empty vector") {
    val manager = KeybindingManager().withRawBindings(
      Map("tui.editor.undo" -> Vector.empty)
    )
    assert(!manager.matches(
      TerminalInput.Key(TerminalKey.Character("-"), KeyModifiers(ctrl = true)),
      KeybindingCommand.EditorUndo
    ))
  }

  test("matches typed insert key descriptors") {
    val manager = KeybindingManager().withUserBindings(
      Map(
        KeybindingCommand.EditorUndo -> Vector(
          KeyDescriptor(TerminalKey.Insert),
          KeyDescriptor(TerminalKey.Insert, KeyModifiers(ctrl = true))
        )
      )
    )

    assert(manager.matches(TerminalInput.Key(TerminalKey.Insert), KeybindingCommand.EditorUndo))
    assert(manager.matches(
      TerminalInput.Key(TerminalKey.Insert, KeyModifiers(ctrl = true)),
      KeybindingCommand.EditorUndo
    ))
    assert(!manager.matches(
      TerminalInput.Key(TerminalKey.Unknown("insert")),
      KeybindingCommand.EditorUndo
    ))
  }

  test("keeps defaults for unspecified commands") {
    val manager = KeybindingManager.fromRawBindings(
      Map(
        "tui.editor.deleteToLineStart" -> Vector(
          KeyDescriptor(TerminalKey.Character("z"), KeyModifiers(ctrl = true))
        )
      )
    )

    assert(manager.matches(
      TerminalInput.Key(TerminalKey.Character("a"), KeyModifiers(ctrl = true)),
      KeybindingCommand.EditorCursorLineStart
    ))
    assert(!manager.matches(
      TerminalInput.Key(TerminalKey.Character("u"), KeyModifiers(ctrl = true)),
      KeybindingCommand.EditorDeleteToLineStart
    ))
    assert(manager.matches(
      TerminalInput.Key(TerminalKey.Character("z"), KeyModifiers(ctrl = true)),
      KeybindingCommand.EditorDeleteToLineStart
    ))
  }
