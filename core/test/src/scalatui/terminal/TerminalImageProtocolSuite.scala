package scalatui.terminal

import scalatui.ansi.Ansi
import scalatui.syntax.Equality.*

class TerminalImageProtocolSuite extends munit.FunSuite:
  test("renders Kitty and iTerm2 protocol paths when capabilities allow"):
    val dimensions   = ImageDimensions(100, 50)
    val kitty        = TerminalImageProtocol.renderBase64Image(
      payload("AAAA"),
      dimensions,
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty)),
      terminalWidth = 20,
      ImageRenderOptions(imageId = Some(7))
    ).get
    val kittyControl = kitty.control.details.asInstanceOf[TerminalRenderControl.Details.KittyImage]
    assertEquals(kittyControl.payload, payload("AAAA"))
    assertEquals(kittyControl.imageId, 7)
    assertEquals(kittyControl.widthCells, kitty.width)
    assertEquals(kittyControl.heightCells, kitty.rows)
    assertEquals(
      TerminalRenderControlEncoder.encode(kitty.control),
      s"\u001b_Ga=T,f=100,i=7,c=${kitty.width},r=${kitty.rows},C=1;AAAA\u001b\\"
    )
    assertEquals(kitty.imageId, Some(7))
    assert(kitty.rows >= 1)

    val iterm         = TerminalImageProtocol.renderBase64Image(
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
    val itermControl  = iterm.control.details.asInstanceOf[TerminalRenderControl.Details.ITerm2Image]
    assertEquals(itermControl.payload, payload("AAAA"))
    assertEquals(itermControl.filename, Some("pic.png"))
    assertEquals(itermControl.widthCells, iterm.width)
    assertEquals(itermControl.heightCells, iterm.rows)
    val itermEncoding = TerminalRenderControlEncoder.encode(iterm.control)
    assertEquals(
      itermEncoding,
      s"\u001b]1337;File=name=cGljLnBuZw==;inline=1;width=${iterm.width};height=${iterm.rows}:AAAA\u0007"
    )

  test("preserves valid payload spelling and emits only the intended frame terminator"):
    val padded   = payload("TQ==")
    val unpadded = payload("TQ")
    val empty    = payload("")

    Vector(padded, unpadded, empty).foreach { value =>
      val sequence = TerminalRenderControlEncoder.encode(
        TerminalImageProtocol.encodeKitty(value, 1, 2, 3)
      )
      assertEquals(sequence, s"\u001b_Ga=T,f=100,i=1,c=2,r=3,C=1;${value.value}\u001b\\")
      assertEquals(sequence.sliding(2).count(_ === "\u001b\\"), 1)
    }

    Vector(padded, unpadded, empty).foreach { value =>
      val sequence = TerminalRenderControlEncoder.encode(
        TerminalImageProtocol.encodeITerm2(value, None, 2, 3)
      )
      assertEquals(sequence, s"\u001b]1337;File=inline=1;width=2;height=3:${value.value}\u0007")
      assertEquals(sequence.count(_ === '\u0007'), 1)
      assert(!sequence.contains("name="), sequence)
    }

  test("encodes Unicode and hostile iTerm2 filenames as UTF-8 standard base64"):
    val unicode = TerminalRenderControlEncoder.encode(
      TerminalImageProtocol.encodeITerm2(payload("AAAA"), Some("猫.png"), 2, 3)
    )
    assert(unicode.contains("name=54yrLnBuZw==;"), unicode)

    val hostile = TerminalRenderControlEncoder.encode(
      TerminalImageProtocol.encodeITerm2(
        payload("AAAA"),
        Some("pic\u0007\u001b\\.png"),
        2,
        3
      )
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

  test("Kitty cleanup is typed and uses the shared encoder"):
    val caps = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(ImageProtocol.Kitty)
    )
    val one  = TerminalImageProtocol.deleteImage(9, caps).get
    val all  = TerminalImageProtocol.deleteAllImages(caps).get

    assertEquals(
      one.details.asInstanceOf[TerminalRenderControl.Details.KittyCleanup].imageId,
      Some(9)
    )
    assertEquals(
      all.details.asInstanceOf[TerminalRenderControl.Details.KittyCleanup].imageId,
      None
    )
    assertEquals(TerminalRenderControlEncoder.encode(one), "\u001b_Ga=d,d=I,i=9\u001b\\")
    assertEquals(TerminalRenderControlEncoder.encode(all), "\u001b_Ga=d,d=A\u001b\\")

  test("terminal control string diagnostics redact payloads and filenames"):
    val sensitivePayload = payload("QUJD".repeat(2048))
    val sensitiveName    = "secret-filename-".repeat(512)
    val kitty            = TerminalImageProtocol.encodeKitty(sensitivePayload, 47, 2, 3)
    val iterm            = TerminalImageProtocol.encodeITerm2(
      sensitivePayload,
      Some(sensitiveName),
      4,
      5
    )
    val targetedCleanup  = TerminalRenderControl.kittyCleanup(Some(47))
    val deleteAllCleanup = TerminalRenderControl.kittyCleanup(None)

    assertEquals(
      kitty.toString,
      "TerminalRenderControl(kind=KittyImage,imageId=47,width=2,rows=3)"
    )
    assertEquals(iterm.toString, "TerminalRenderControl(kind=ITerm2Image,width=4,rows=5)")
    assertEquals(
      targetedCleanup.toString,
      "TerminalRenderControl(kind=KittyCleanup,imageId=47,width=0,rows=1)"
    )
    assertEquals(
      deleteAllCleanup.toString,
      "TerminalRenderControl(kind=KittyCleanup,width=0,rows=1)"
    )
    Vector(kitty, iterm, targetedCleanup, deleteAllCleanup).map(_.toString).foreach { diagnostic =>
      assert(diagnostic.length < 128, diagnostic)
      assert(!diagnostic.contains("QUJDQUJD"), diagnostic)
      assert(!diagnostic.contains("secret-filename-"), diagnostic)
    }

  test("image controls reject non-positive geometry and Kitty transmit IDs"):
    Vector(0, -1).foreach { invalid =>
      intercept[IllegalArgumentException](
        TerminalImageProtocol.encodeKitty(payload("AAAA"), invalid, 1, 1)
      )
      intercept[IllegalArgumentException](
        TerminalImageProtocol.encodeKitty(payload("AAAA"), 1, invalid, 1)
      )
      intercept[IllegalArgumentException](
        TerminalImageProtocol.encodeKitty(payload("AAAA"), 1, 1, invalid)
      )
      intercept[IllegalArgumentException](
        TerminalImageProtocol.encodeITerm2(payload("AAAA"), None, invalid, 1)
      )
      intercept[IllegalArgumentException](
        TerminalImageProtocol.encodeITerm2(payload("AAAA"), None, 1, invalid)
      )
    }

  test("configured and cleanup Kitty IDs reject zero and negative values"):
    val kitty = TerminalCapabilities(
      trueColor = true,
      hyperlinks = true,
      images = Some(ImageProtocol.Kitty)
    )

    Vector(0, -1).foreach { invalid =>
      intercept[IllegalArgumentException](ImageRenderOptions(imageId = Some(invalid)))
      intercept[IllegalArgumentException](TerminalImageProtocol.deleteImage(invalid, kitty))
    }

  test("Kitty image ID allocator reaches Int.MaxValue once and then fails explicitly"):
    val allocator = KittyImageIdAllocator(Int.MaxValue)

    assertEquals(allocator.allocate(), Int.MaxValue)
    val failure = intercept[IllegalStateException](allocator.allocate())
    assertEquals(failure.getMessage, "Kitty image ID allocator exhausted")
    intercept[IllegalArgumentException](KittyImageIdAllocator(0))
    intercept[IllegalArgumentException](KittyImageIdAllocator(-1))

  test("final encoder revalidates image geometry and Kitty IDs"):
    Vector(0, -1).foreach { invalid =>
      intercept[IllegalArgumentException](TerminalRenderControlEncoder.encodeDetails(
        TerminalRenderControl.Details.KittyImage(payload("AAAA"), invalid, 1, 1)
      ))
      intercept[IllegalArgumentException](TerminalRenderControlEncoder.encodeDetails(
        TerminalRenderControl.Details.KittyImage(payload("AAAA"), 1, invalid, 1)
      ))
      intercept[IllegalArgumentException](TerminalRenderControlEncoder.encodeDetails(
        TerminalRenderControl.Details.KittyImage(payload("AAAA"), 1, 1, invalid)
      ))
      intercept[IllegalArgumentException](TerminalRenderControlEncoder.encodeDetails(
        TerminalRenderControl.Details.ITerm2Image(payload("AAAA"), None, invalid, 1)
      ))
      intercept[IllegalArgumentException](TerminalRenderControlEncoder.encodeDetails(
        TerminalRenderControl.Details.ITerm2Image(payload("AAAA"), None, 1, invalid)
      ))
      intercept[IllegalArgumentException](TerminalRenderControlEncoder.encodeDetails(
        TerminalRenderControl.Details.KittyCleanup(Some(invalid))
      ))
    }

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

  test("low-level runtime sizing uses its supplied deterministic fallback"):
    val dimensions   = ImageDimensions(100, 100)
    val defaultSize  = TerminalImageProtocol.calculateCellSize(
      dimensions,
      ImageRenderOptions(
        maxWidthCells = Some(1),
        cellDimensionsSource = ImageCellDimensionsSource.Runtime
      ),
      terminalWidth = 20
    )
    val suppliedSize = TerminalImageProtocol.calculateCellSize(
      dimensions,
      ImageRenderOptions(
        maxWidthCells = Some(1),
        cellDimensions = ImageCellDimensions(20, 10),
        cellDimensionsSource = ImageCellDimensionsSource.Runtime
      ),
      terminalWidth = 20
    )

    assertEquals(defaultSize.heightCells, 1)
    assertEquals(suppliedSize.heightCells, 2)
    assert(suppliedSize.heightCells > defaultSize.heightCells)

  test("explicit fixed cell dimensions stay deterministic"):
    val fixedSize = TerminalImageProtocol.calculateCellSize(
      ImageDimensions(100, 100),
      ImageRenderOptions(
        maxWidthCells = Some(1),
        cellDimensions = ImageCellDimensions(widthPx = 9, heightPx = 18)
      ),
      terminalWidth = 20
    )

    assertEquals(fixedSize.heightCells, 1)

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
