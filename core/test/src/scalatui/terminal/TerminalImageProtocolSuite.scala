package scalatui.terminal

import scalatui.ansi.Ansi

class TerminalImageProtocolSuite extends munit.FunSuite:
  test("renders Kitty and iTerm2 protocol paths when capabilities allow"):
    val dimensions = ImageDimensions(100, 50)
    val kitty      = TerminalImageProtocol.renderBase64Image(
      "AAAA",
      dimensions,
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty)),
      terminalWidth = 20,
      ImageRenderOptions(imageId = Some(7))
    ).get
    assert(kitty.sequence.startsWith("\u001b_G"), kitty.sequence)
    assert(kitty.sequence.contains("i=7"), kitty.sequence)
    assertEquals(kitty.imageId, Some(7))
    assert(kitty.rows >= 1)

    val iterm = TerminalImageProtocol.renderBase64Image(
      "AAAA",
      dimensions,
      TerminalCapabilities(
        trueColor = true,
        hyperlinks = true,
        images = Some(ImageProtocol.ITerm2)
      ),
      terminalWidth = 20,
      ImageRenderOptions(filename = Some("pic.png"))
    ).get
    assert(iterm.sequence.startsWith("\u001b]1337;File="), iterm.sequence)
    assert(iterm.sequence.contains("name=pic.png"), iterm.sequence)

  test("unknown terminals return no protocol render and no cleanup"):
    val caps = TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)
    assertEquals(
      TerminalImageProtocol.renderBase64Image(
        "AAAA",
        ImageDimensions(10, 10),
        caps,
        terminalWidth = 10
      ),
      None
    )
    assertEquals(TerminalImageProtocol.deleteImage(1, caps), None)
    assertEquals(TerminalImageProtocol.deleteAllImages(caps), None)

  test("parses valid terminal cell-size reports"):
    assertEquals(
      TerminalImageProtocol.parseCellSizeResponse("\u001b[6;12;24t"),
      Some(ImageCellDimensions(widthPx = 24, heightPx = 12))
    )
    assertEquals(TerminalImageProtocol.isCellSizeResponse("\u001b[6;12;24t"), true)
    assertEquals(TerminalImageProtocol.isCellSizeResponse("\u001b[6;0;24t"), true)

  test("ignores invalid terminal cell-size reports"):
    assertEquals(TerminalImageProtocol.parseCellSizeResponse("\u001b[6;0;24t"), None)
    assertEquals(TerminalImageProtocol.parseCellSizeResponse("\u001b[6;-1;24t"), None)
    assertEquals(TerminalImageProtocol.parseCellSizeResponse("\u001b[6;abc;24t"), None)
    assertEquals(TerminalImageProtocol.isCellSizeResponse("\u001b[6;abc;24t"), false)

  test("cell-size query is reflected in image size calculation"):
    TerminalImageProtocol.resetCellDimensions()
    val dimensions  = ImageDimensions(100, 100)
    val defaultSize = TerminalImageProtocol.calculateCellSize(
      dimensions,
      ImageRenderOptions(
        maxWidthCells = Some(1),
        cellDimensionsSource = ImageCellDimensionsSource.Runtime
      ),
      terminalWidth = 20
    )
    TerminalImageProtocol.setCellDimensions(20, 10)
    val queriedSize = TerminalImageProtocol.calculateCellSize(
      dimensions,
      ImageRenderOptions(
        maxWidthCells = Some(1),
        cellDimensionsSource = ImageCellDimensionsSource.Runtime
      ),
      terminalWidth = 20
    )

    assertEquals(defaultSize.heightCells, 1)
    assertEquals(queriedSize.heightCells, 2)
    assert(queriedSize.heightCells > defaultSize.heightCells)

  test("explicit fallback cell dimensions stay deterministic after runtime query"):
    TerminalImageProtocol.resetCellDimensions()
    TerminalImageProtocol.setCellDimensions(20, 10)

    val fixedSize = TerminalImageProtocol.calculateCellSize(
      ImageDimensions(100, 100),
      ImageRenderOptions(
        maxWidthCells = Some(1),
        cellDimensions = ImageCellDimensions(widthPx = 9, heightPx = 18)
      ),
      terminalWidth = 20
    )

    assertEquals(fixedSize.heightCells, 1)

  test("invalid cell dimensions leave sizing fallback unchanged"):
    TerminalImageProtocol.resetCellDimensions()
    TerminalImageProtocol.setCellDimensions(0, 10)
    assertEquals(TerminalImageProtocol.cellDimensions, ImageCellDimensions())

  test("cell-size query sequence is deterministic"):
    assertEquals(TerminalImageProtocol.QueryCellDimensions, "\u001b[16t")

  test("fallback is readable and width-safe"):
    val fallback = TerminalImageProtocol.fallback(
      ImageDimensions(800, 600),
      "image/png",
      Some("very-long-file-name.png"),
      width = 16
    )
    assert(Ansi.visibleWidth(fallback) <= 16, fallback)
