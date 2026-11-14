package scalatui.core

import scalatui.terminal.*

class KittyCropRetentionSuite extends munit.FunSuite:
  private val payload                                                   = Base64ImagePayload.from("AAAA").toOption.get
  private def image(rows: Int = 6, pixels: Int = 17)                    =
    TerminalImageProtocol.encodeKitty(payload, 7, 4, rows, ImageDimensions(13, pixels))
  private def crop(control: TerminalRenderControl, top: Int, rows: Int) =
    TypedControlClipping.clipPlacement(
      TerminalControlPlacement(0, 0, control),
      ClipRect(top, 0, 4, rows)
    ).get.copy(row = 0)
  private def frame(placement: TerminalControlPlacement)                =
    PreparedFrame(Vector.fill(6)(""), None, Vector(placement), DocumentMetadata.empty)

  test("scroll crop changes at identical cell geometry reuse upload and replace placement"):
    val retention = FullscreenKittyRetention(KittyImageRetentionOptions())
    val first     = retention.update(frame(crop(image(), 1, 2)), None, false).frame
    val next      = retention.update(frame(crop(image(), 2, 2)), Some(first), false)
    assertEquals(
      next.cleanup.map(_.details),
      Vector(TerminalRenderControlDetails.KittyPlacementCleanup(7))
    )
    assertEquals(
      next.frame.controls.head.control.details,
      TerminalRenderControlDetails.KittyPlacement(7, 4, 2)
    )
    assertEquals(
      TerminalRenderControlEncoder.encode(next.frame.controls.head.control),
      "\u001b_Ga=p,i=7,c=4,r=2,C=1,x=0,y=5,w=13,h=7\u001b\\"
    )
    assert(retention.hasRetainedUploads)
    assertEquals(
      retention.stopCleanup().map(_.details),
      Vector(TerminalRenderControlDetails.KittyCleanup(Some(7)))
    )

  test("known source survives resize and offscreen return but changed dimensions force upload"):
    val retention = FullscreenKittyRetention(KittyImageRetentionOptions())
    val first     = retention.update(frame(crop(image(), 1, 2)), None, false).frame
    val resized   = retention.update(frame(crop(image(8), 2, 3)), Some(first), true)
    assert(resized.frame.controls.head.control.details.isInstanceOf[
      TerminalRenderControlDetails.KittyPlacement
    ])
    val empty     = resized.frame.copy(controls = Vector.empty)
    val hidden    = retention.update(empty, Some(resized.frame), false)
    assertEquals(
      hidden.cleanup.map(_.details),
      Vector(TerminalRenderControlDetails.KittyPlacementCleanup(7))
    )
    val returned  = retention.update(frame(crop(image(), 2, 2)), Some(hidden.frame), false)
    assert(returned.frame.controls.head.control.details.isInstanceOf[
      TerminalRenderControlDetails.KittyPlacement
    ])
    val changed   =
      retention.update(frame(crop(image(pixels = 18), 2, 2)), Some(returned.frame), false)
    assertEquals(
      changed.cleanup.map(_.details),
      Vector(TerminalRenderControlDetails.KittyCleanup(Some(7)))
    )
    assert(changed.frame.controls.head.control.details.isInstanceOf[
      TerminalRenderControlDetails.KittyImage
    ])

  test("unknown geometry retains legacy size matching and clipping conservatism"):
    val retention = FullscreenKittyRetention(KittyImageRetentionOptions())
    val legacy    = TerminalImageProtocol.encodeKitty(payload, 7, 4, 6)
    assertEquals(
      TypedControlClipping.clipPlacement(
        TerminalControlPlacement(0, 0, legacy),
        ClipRect(1, 0, 4, 2)
      ),
      None
    )
    val first     = retention.update(frame(TerminalControlPlacement(0, 0, legacy)), None, false).frame
    val smaller   = TerminalImageProtocol.encodeKitty(payload, 7, 4, 3)
    val next      = retention.update(frame(TerminalControlPlacement(0, 0, smaller)), Some(first), false)
    assertEquals(
      next.cleanup.map(_.details),
      Vector(TerminalRenderControlDetails.KittyCleanup(Some(7)))
    )
    assert(
      next.frame.controls.head.control.details.isInstanceOf[TerminalRenderControlDetails.KittyImage]
    )

  test("signed layout anchors are clipped before public placement construction"):
    val placement = TerminalControlPlacement(0, 0, image())
    val clip      = ClipRect(0, 0, 4, 3)
    val visible   = TypedControlClipping.clipTranslatedPlacement(placement, -2L, 0L, clip).get
    assertEquals(visible.row, 0)
    assertEquals(visible.control.rows, 3)
    assertEquals(visible.control.kittyGeometry.get.sourceRect, ImagePixelRect(0, 5, 13, 10))
    assertEquals(
      TypedControlClipping.clipTranslatedPlacement(placement, Long.MinValue, 0L, clip),
      None
    )
    assertEquals(
      TypedControlClipping.clipTranslatedPlacement(placement, Long.MaxValue, 0L, clip),
      None
    )
    assertEquals(TypedControlClipping.clipTranslatedPlacement(placement, 0L, -1L, clip), None)
