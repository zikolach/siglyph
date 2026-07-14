package scalatui.terminal

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*

class TerminalImageProtocolSuite extends munit.FunSuite:
  test("renders Kitty and iTerm2 protocol paths when capabilities allow"):
    val dimensions = ImageDimensions(100, 50)
    val kitty      = TerminalImageProtocol.renderBase64Image(
      payload("AAAA"),
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
      payload("AAAA"),
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
    assert(iterm.sequence.contains("name=cGljLnBuZw=="), iterm.sequence)

  test("preserves valid payload spelling and emits only the intended frame terminator"):
    val padded   = payload("TQ==")
    val unpadded = payload("TQ")
    val empty    = payload("")

    Vector(padded, unpadded, empty).foreach { value =>
      val sequence = TerminalImageProtocol.encodeKitty(value, 1, 2, 3)
      assertEquals(sequence, s"\u001b_Ga=T,f=100,i=1,c=2,r=3,C=1;${value.value}\u001b\\")
      assertEquals(sequence.sliding(2).count(_ === "\u001b\\"), 1)
    }

    Vector(padded, unpadded, empty).foreach { value =>
      val sequence = TerminalImageProtocol.encodeITerm2(value, None, 2, 3)
      assert(sequence.endsWith(s":${value.value}\u0007"), sequence)
      assertEquals(sequence.count(_ === '\u0007'), 1)
      assert(!sequence.contains("name="), sequence)
    }

  test("encodes Unicode and hostile iTerm2 filenames as UTF-8 standard base64"):
    val unicode = TerminalImageProtocol.encodeITerm2(payload("AAAA"), Some("猫.png"), 2, 3)
    assert(unicode.contains("name=54yrLnBuZw==;"), unicode)

    val hostile = TerminalImageProtocol.encodeITerm2(
      payload("AAAA"),
      Some("pic\u0007\u001b\\.png"),
      2,
      3
    )
    assertEquals(hostile.count(_ === '\u001b'), 1)
    assertEquals(hostile.count(_ === '\u0007'), 1)
    assert(!hostile.contains("\u001b\\"), hostile)
    assert(hostile.contains("name=cGljBxtcLnBuZw==;"), hostile)

  test("unknown terminals return no protocol render and no cleanup"):
    val caps = TerminalCapabilities(trueColor = false, hyperlinks = false, images = None)
    assertEquals(
      TerminalImageProtocol.renderBase64Image(
        payload("AAAA"),
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

  test("fallback makes C0, DEL, and C1 metadata controls visible and stays width-safe"):
    val fallback = TerminalImageProtocol.fallback(
      ImageDimensions(8, 6),
      "image/\u0000png\u0085",
      Some("pic\u001b\u007f.png"),
      width = 80
    )
    assert(fallback.contains("pic\\u001B\\u007F.png"), fallback)
    assert(fallback.contains("image/\\u0000png\\u0085"), fallback)
    assert(!fallback.exists(char =>
      char === '\u0000' || char === '\u001b' || char === '\u007f' || char === '\u0085'
    ))
    assert(Ansi.visibleWidth(fallback) <= 80, fallback)

    val exactFit = TerminalImageProtocol.fallback(
      ImageDimensions(8, 6),
      "image/png",
      Some("\u001b"),
      width = 14
    )
    assertEquals(exactFit, s"[image: \\u001B${Ansi.Reset}")
    assert(!exactFit.stripSuffix(Ansi.Reset).exists(_ === '\u001b'), exactFit)

  test("fallback is readable and width-safe"):
    val fallback = TerminalImageProtocol.fallback(
      ImageDimensions(800, 600),
      "image/png",
      Some("very-long-file-name.png"),
      width = 16
    )
    assert(Ansi.visibleWidth(fallback) <= 16, fallback)

  private def payload(value: String): Base64ImagePayload =
    Base64ImagePayload.from(value).toOption.get
