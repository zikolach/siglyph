package scalatui.components

import java.nio.charset.StandardCharsets

import scalatui.ansi.Ansi
import scalatui.core.InputResult
import scalatui.terminal.{TerminalInput, TerminalInputChunk, TerminalKey}

class FilterPasteSessionSuite extends munit.FunSuite:
  test("filter paste retains a large initial query and counts only appended storage"):
    val initial = new String(("committed-query-" * 8192).toCharArray)
    val session = FilterPasteSession(initial)

    session.append(TerminalInputChunk("new".getBytes(StandardCharsets.UTF_8)))

    assert(session.initialQueryReference.eq(initial))
    assertEquals(session.acceptedCharacterCount, 3L)
    assertEquals(session.appendCount, 1L)
    assertEquals(session.query, initial + "new")

  test("filter paste caches one combined query snapshot per accepted mutation"):
    val initial = new String("initial".toCharArray)
    val session = FilterPasteSession(initial)

    val clean      = session.query
    val cleanAgain = session.query
    assert(clean.eq(initial))
    assert(cleanAgain.eq(initial))
    assert(cleanAgain.eq(clean))
    assertEquals(session.queryMaterializationCount, 0L)

    session.append(TerminalInputChunk("-one".getBytes(StandardCharsets.UTF_8)))
    val first = session.query
    assertEquals(first, "initial-one")
    assert(first.eq(session.query))
    assertEquals(session.queryMaterializationCount, 1L)

    session.append(TerminalInputChunk("-two".getBytes(StandardCharsets.UTF_8)))
    val second = session.query
    assertEquals(second, "initial-one-two")
    assert(!second.eq(first))
    assert(second.eq(session.query))
    assertEquals(session.queryMaterializationCount, 2L)

  test("filter paste releases a stale combined snapshot without eager rematerialization"):
    val initial = new String("initial".toCharArray)
    val session = FilterPasteSession(initial)

    session.append(TerminalInputChunk("-one".getBytes(StandardCharsets.UTF_8)))
    val stale = session.query
    assert(!stale.eq(initial))

    session.append(TerminalInputChunk("-two".getBytes(StandardCharsets.UTF_8)))
    assert(session.retainedCachedQueryReference.eq(initial))
    assertEquals(session.queryMaterializationCount, 1L)

    val replacement = session.query
    assertEquals(replacement, "initial-one-two")
    assert(!replacement.eq(stale))
    assert(replacement.eq(session.query))
    assertEquals(session.queryMaterializationCount, 2L)

  test("filter paste accepts multiple segments before materializing once"):
    val initial = new String("initial".toCharArray)
    val session = FilterPasteSession(initial)

    session.append(TerminalInputChunk("-one".getBytes(StandardCharsets.UTF_8)))
    session.append(TerminalInputChunk("-two".getBytes(StandardCharsets.UTF_8)))
    assert(session.retainedCachedQueryReference.eq(initial))
    assertEquals(session.queryMaterializationCount, 0L)

    val combined = session.query
    assertEquals(combined, "initial-one-two")
    assert(combined.eq(session.query))
    assertEquals(session.queryMaterializationCount, 1L)

  test("filter paste finish invalidates only when decoder flush accepts text"):
    val unchanged = FilterPasteSession("query")
    unchanged.append(TerminalInputChunk("-done".getBytes(StandardCharsets.UTF_8)))
    val snapshot  = unchanged.query
    unchanged.finish()
    assert(unchanged.query.eq(snapshot))
    assertEquals(unchanged.queryMaterializationCount, 1L)

    val flushedInitial = new String("query".toCharArray)
    val flushed        = FilterPasteSession(flushedInitial)
    flushed.append(TerminalInputChunk("-done".getBytes(StandardCharsets.UTF_8)))
    val stale          = flushed.query
    flushed.append(TerminalInputChunk(Array(0xe2.toByte)))
    flushed.finish()
    assert(flushed.retainedCachedQueryReference.eq(flushedInitial))
    assertEquals(flushed.queryMaterializationCount, 1L)
    val replacement    = flushed.query
    assertEquals(replacement, "query-done\uFFFD")
    assert(!replacement.eq(stale))
    assert(replacement.eq(flushed.query))
    assertEquals(flushed.queryMaterializationCount, 2L)

  test("canonical filter paste session appends decoded segments once for a multi-megabyte query"):
    val session     = FilterPasteSession("prefix-")
    val block       = "0123456789abcdef" * 256
    val blockBytes  = block.getBytes(StandardCharsets.UTF_8)
    val repetitions = 512

    (0 until repetitions).foreach(_ => session.append(TerminalInputChunk(blockBytes)))
    session.finish()

    assertEquals(session.appendCount, repetitions.toLong)
    assertEquals(session.acceptedCharacterCount, block.length.toLong * repetitions)
    assertEquals(session.query, "prefix-" + block * repetitions)

  test(
    "canonical filter paste session decodes fragmented UTF-8 and normalizes fragmented newlines"
  ):
    val session = FilterPasteSession("find ")
    val bytes   = "界\r\n🙂".getBytes(StandardCharsets.UTF_8)

    bytes.foreach(byte => session.append(TerminalInputChunk(Array(byte))))
    session.finish()

    assertEquals(session.query, "find 界  🙂")
    assertEquals(session.appendCount, 4L)

  test("select list defers fuzzy filtering rendering and selection callback until paste commit"):
    var changes = Vector.empty[Option[SelectItem]]
    val list    = SelectList(
      Vector(
        SelectItem("alpha", "Alpha"),
        SelectItem("beta", "Beta"),
        SelectItem("gamma", "Gamma")
      ),
      SelectListOptions(filtering = SelectListFiltering.Fuzzy)
    )
    list.onSelectionChange = item => changes :+= item
    list.handleInput(TerminalInput.Key(TerminalKey.Down))
    changes = Vector.empty

    assertEquals(list.handleInputResult(TerminalInput.PasteStart), InputResult.NoRender)
    assertEquals(list.handleInputResult(pasteChunk("g")), InputResult.NoRender)
    assertEquals(list.handleInputResult(pasteChunk("a")), InputResult.NoRender)
    assertEquals(list.query, "ga")
    assertEquals(list.selected.map(_.value), Some("beta"))
    assertEquals(changes, Vector.empty)
    assert(Ansi.strip(list.render(40).mkString("\n")).contains("Gamma"))
    assertEquals(changes, Vector.empty)

    assertEquals(list.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(list.selected.map(_.value), Some("gamma"))
    assertEquals(changes.map(_.map(_.value)), Vector(Some("gamma")))

  test("settings list defers fuzzy filtering clamp and render until paste commit"):
    val list = SettingsList(
      Vector(
        SettingItem("alpha", "Alpha", "on"),
        SettingItem("beta", "Beta", "on"),
        SettingItem("gamma", "Gamma", "on")
      ),
      SettingsListOptions(filtering = SettingsListFiltering.Fuzzy, showHints = false)
    )
    list.handleInput(TerminalInput.Key(TerminalKey.Down))

    assertEquals(list.handleInputResult(TerminalInput.PasteStart), InputResult.NoRender)
    assertEquals(list.handleInputResult(pasteChunk("g")), InputResult.NoRender)
    assertEquals(list.handleInputResult(pasteChunk("a")), InputResult.NoRender)
    assertEquals(list.query, "ga")
    assertEquals(list.selected.map(_.id), Some("beta"))
    val active = Ansi.strip(list.render(40).mkString("\n"))
    assert(active.contains("Search: ga"), active)
    assert(active.contains("Gamma"), active)
    assertEquals(list.selected.map(_.id), Some("beta"))

    assertEquals(list.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(list.selected.map(_.id), Some("gamma"))

  test("explicit renders keep committed candidates until one final filter commit"):
    var selectionChanges = Vector.empty[Option[String]]
    val select           = SelectList(
      Vector(SelectItem("alpha", "Alpha"), SelectItem("beta", "Beta")),
      SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    val settings         = SettingsList(
      Vector(SettingItem("alpha", "Alpha", "on"), SettingItem("beta", "Beta", "on")),
      SettingsListOptions(filtering = SettingsListFiltering.Containment, showHints = false)
    )
    select.onSelectionChange = item => selectionChanges :+= item.map(_.value)
    "alpha".foreach { character =>
      select.handleInput(TerminalInput.Key(TerminalKey.Character(character.toString)))
      settings.handleInput(TerminalInput.Key(TerminalKey.Character(character.toString)))
    }
    selectionChanges = Vector.empty

    select.handleInputResult(TerminalInput.PasteStart)
    settings.handleInputResult(TerminalInput.PasteStart)
    (0 until 64).foreach { _ =>
      select.handleInputResult(pasteChunk("z"))
      settings.handleInputResult(pasteChunk("z"))

      assert(Ansi.strip(select.render(40).mkString("\n")).contains("Alpha"))
      val settingsRender = Ansi.strip(settings.render(200).mkString("\n"))
      assert(settingsRender.contains("Search: " + settings.query), settingsRender)
      assert(settingsRender.contains("Alpha"), settingsRender)
      assertEquals(select.selected.map(_.value), Some("alpha"))
      assertEquals(settings.selected.map(_.id), Some("alpha"))
      assertEquals(selectionChanges, Vector.empty)
    }

    assertEquals(select.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(settings.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    assertEquals(select.selected, None)
    assertEquals(settings.selected, None)
    assertEquals(selectionChanges, Vector(None))
    assert(Ansi.strip(select.render(40).mkString("\n")).contains("No items"))
    assert(Ansi.strip(settings.render(200).mkString("\n")).contains("No matching settings"))

  test("filter paste interruption commits before later input for both lists"):
    val select   = SelectList(
      Vector(SelectItem("gamma", "Gamma"), SelectItem("gazette", "Gazette")),
      SelectListOptions(filtering = SelectListFiltering.Fuzzy)
    )
    val settings = SettingsList(
      Vector(SettingItem("gamma", "Gamma", "on"), SettingItem("gazette", "Gazette", "on")),
      SettingsListOptions(filtering = SettingsListFiltering.Fuzzy, showHints = false)
    )

    Vector(select, settings).foreach { component =>
      component.handleInputResult(TerminalInput.PasteStart)
      component.handleInputResult(pasteChunk("g"))
      component.handleInputResult(pasteChunk("a"))
      assertEquals(
        component.handleInputResult(TerminalInput.Key(TerminalKey.Character("m"))),
        InputResult.Render
      )
    }
    assertEquals(select.query, "gam")
    assertEquals(settings.query, "gam")

  test("repeated start commits prior text while empty and orphan events are no-ops"):
    val select   = SelectList(
      Vector(SelectItem("ab", "AB")),
      SelectListOptions(filtering = SelectListFiltering.Containment)
    )
    val settings = SettingsList(
      Vector(SettingItem("ab", "AB", "on")),
      SettingsListOptions(filtering = SettingsListFiltering.Containment, showHints = false)
    )

    Vector(select, settings).foreach { component =>
      assertEquals(component.handleInputResult(pasteChunk("orphan")), InputResult.Ignored)
      assertEquals(component.handleInputResult(TerminalInput.PasteEnd), InputResult.NoRender)
      assertEquals(component.handleInputResult(TerminalInput.PasteStart), InputResult.NoRender)
      assertEquals(component.handleInputResult(TerminalInput.PasteEnd), InputResult.NoRender)
      assertEquals(component.handleInputResult(TerminalInput.PasteStart), InputResult.NoRender)
      assertEquals(component.handleInputResult(pasteChunk("a")), InputResult.NoRender)
      assertEquals(component.handleInputResult(TerminalInput.PasteStart), InputResult.Render)
      assertEquals(component.handleInputResult(pasteChunk("b")), InputResult.NoRender)
      assertEquals(component.handleInputResult(TerminalInput.PasteEnd), InputResult.Render)
    }
    assertEquals(select.query, "ab")
    assertEquals(settings.query, "ab")

  test("settings public filter mutations clear or commit active paste state"):
    val list = SettingsList(
      Vector(SettingItem("alpha", "Alpha", "on")),
      SettingsListOptions(filtering = SettingsListFiltering.Containment, showHints = false)
    )
    list.handleInputResult(TerminalInput.PasteStart)
    list.handleInputResult(pasteChunk("a"))
    list.clearFilter()
    assertEquals(list.query, "")
    assertEquals(list.handleInputResult(TerminalInput.PasteEnd), InputResult.NoRender)

    list.handleInputResult(TerminalInput.PasteStart)
    list.handleInputResult(pasteChunk("a"))
    list.items = Vector(SettingItem("amber", "Amber", "on"))
    assertEquals(list.query, "a")
    assertEquals(list.handleInputResult(TerminalInput.PasteEnd), InputResult.NoRender)

  private def pasteChunk(value: String): TerminalInput =
    TerminalInput.PasteChunk(TerminalInputChunk(value.getBytes(StandardCharsets.UTF_8)))
