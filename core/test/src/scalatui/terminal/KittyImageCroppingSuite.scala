package scalatui.terminal

class KittyImageCroppingSuite extends munit.FunSuite:
  private val payload                                                   = Base64ImagePayload.from("AAAA").toOption.get
  private def image                                                     = TerminalImageProtocol.encodeKitty(payload, 7, 4, 6, ImageDimensions(13, 17))
  private def clip(control: TerminalRenderControl, top: Int, rows: Int) =
    TerminalImageProtocol.clipImage(control, top, 0, control.width, rows).get

  test("top bottom and both clips emit floor-top ceil-bottom source rectangles"):
    for (top, rows, y, height) <- Vector((2, 4, 5, 12), (0, 4, 0, 12), (2, 2, 5, 7)) do
      val cropped = clip(image, top, rows)
      assertEquals(cropped.kittyGeometry.get.sourceRect, ImagePixelRect(0, y, 13, height))
      assertEquals(
        TerminalRenderControlEncoder.encode(cropped),
        s"\u001b_Ga=T,f=100,i=7,c=4,r=$rows,C=1,x=0,y=$y,w=13,h=$height;AAAA\u001b\\"
      )

  test("nested clips and remapping preserve original geometry without rounding drift"):
    val nested = clip(clip(image, 1, 5), 1, 2)
    assertEquals(nested, clip(image, 2, 2))
    assertEquals(
      TerminalRenderControl.remapKittyImage(nested, 9).kittyGeometry,
      nested.kittyGeometry
    )
    val other  = clip(image, 3, 2)
    assertEquals(nested.details, other.details)
    assertNotEquals(nested, other)
    assertNotEquals(nested.hashCode(), other.hashCode())

  test("full and legacy bytes remain exact and unsupported clips stay omitted"):
    val legacy   = TerminalImageProtocol.encodeKitty(payload, 7, 4, 6)
    assertEquals(
      TerminalRenderControlEncoder.encode(image),
      TerminalRenderControlEncoder.encode(legacy)
    )
    assertEquals(
      TerminalRenderControlEncoder.encode(legacy),
      "\u001b_Ga=T,f=100,i=7,c=4,r=6,C=1;AAAA\u001b\\"
    )
    assertEquals(TerminalImageProtocol.clipImage(legacy, 1, 0, 4, 5), None)
    assertEquals(TerminalImageProtocol.clipImage(image, 0, 1, 3, 6), None)
    assertEquals(TerminalImageProtocol.clipImage(image, 6, 0, 4, 0), None)
    val iterm    = TerminalImageProtocol.encodeITerm2(payload, None, 4, 6)
    assertEquals(TerminalImageProtocol.clipImage(iterm, 1, 0, 4, 5), None)
    val fallback = TerminalImageProtocol.renderBase64Image(
      payload,
      ImageDimensions(0, -1),
      TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(ImageProtocol.Kitty)),
      4
    ).get
    assertEquals(fallback.control.kittyGeometry, None)

  test("geometry validates bounds and uses overflow-safe products"):
    val max      = Int.MaxValue
    val geometry = KittyImageGeometry(ImageDimensions(max, max), max, max, max - 1, 1)
    assertEquals(geometry.sourceRect, ImagePixelRect(0, max - 1, max, 1))
    intercept[IllegalArgumentException](ImagePixelRect(max, 0, 1, 1))
    intercept[IllegalArgumentException](ImagePixelRect(0, 0, 0, 1))
    intercept[IllegalArgumentException](KittyImageGeometry(ImageDimensions(0, 1), 1, 1, 0, 1))
    intercept[IllegalArgumentException](geometry.copy(topRow = max))
    intercept[IllegalArgumentException](geometry.clip(1, 1))
    intercept[IllegalArgumentException](TerminalImageProtocol.clipImage(image, 5, 0, 4, 2))
