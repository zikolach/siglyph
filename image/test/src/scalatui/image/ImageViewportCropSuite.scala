package scalatui.image

import scalatui.components.{ScrollView, StackEntry, StackEntryOptions, VStack}
import scalatui.core.*
import scalatui.terminal.*

class ImageViewportCropSuite extends munit.FunSuite:
  private val payload                                                     = Base64ImagePayload.from("AAAA").toOption.get
  private def image(protocol: ImageProtocol = ImageProtocol.Kitty): Image = Image(
    payload,
    ImageDimensions(100, 100),
    TerminalCapabilities(trueColor = true, hyperlinks = true, images = Some(protocol)),
    ImageRenderOptions(imageId = Some(9100), cellDimensions = ImageCellDimensions(10, 10))
  )

  test("Image remains visible at top and bottom scroll boundaries"):
    val view   = ScrollView(image())
    val top    = ViewportLayoutEngine.layout(view, 10, 4).render
    assertEquals(top.controls.size, 1)
    assertEquals(top.controls.head.row, 0)
    assertEquals(top.controls.head.control.rows, 4)
    assert(TerminalRenderControlEncoder.encode(top.controls.head.control).contains("h=40"))
    view.scrollTo(3)
    val middle = ViewportLayoutEngine.layout(view, 10, 4).render
    assertEquals(middle.controls.size, 1)
    assertEquals(middle.controls.head.row, 0)
    assertEquals(middle.controls.head.control.rows, 4)
    assert(TerminalRenderControlEncoder.encode(middle.controls.head.control).contains(",y=30,"))

  test("scroll and resize reuse the Image upload while source crop changes"):
    val view     = ScrollView(image(), primary = true)
    val terminal = VirtualTerminal(10, 4)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()
    try
      assert(terminal.output.contains("\u001b_Ga=T"), terminal.output)
      terminal.clearWrites()
      view.scrollTo(1)
      tui.flushRender()
      assert(terminal.output.contains("\u001b_Ga=p"), terminal.output)
      assert(terminal.output.contains(",y=10,"), terminal.output)
      assert(!terminal.output.contains("\u001b_Ga=T"), terminal.output)
      terminal.clearWrites()
      terminal.resize(10, 5)
      assert(terminal.output.contains("\u001b_Ga=p"), terminal.output)
      assert(terminal.output.contains("h=50"), terminal.output)
      assert(!terminal.output.contains("\u001b_Ga=T"), terminal.output)
    finally tui.stop()

  test("unknown source geometry and iTerm2 retain partial-image omission"):
    val legacy = new Component:
      override def render(width: Int): ComponentRender = ComponentRender(
        Vector.fill(10)(""),
        Vector(TerminalControlPlacement(
          0,
          0,
          TerminalImageProtocol.encodeKitty(payload, 9101, 10, 10)
        )),
        Vector.empty
      )
    assert(ViewportLayoutEngine.layout(ScrollView(legacy), 10, 4).render.controls.isEmpty)
    assert(ViewportLayoutEngine.layout(
      ScrollView(image(ImageProtocol.ITerm2)),
      10,
      4
    ).render.controls.isEmpty)

  test("nested scroll clips compose source offsets and preserve a sticky footer"):
    val inner     = ScrollView(image())
    val filler    = new Component:
      override def render(width: Int): ComponentRender =
        ComponentRender.text(Vector.fill(8)("tail"))
    val heading   = new Component:
      override def render(width: Int): ComponentRender =
        ComponentRender.text(Vector("head", "head"))
    val outer     = ScrollView(VStack(Seq(
      StackEntry(heading, StackEntryOptions(basis = Some(2), minSize = 2, maxSize = Some(2))),
      StackEntry(inner, StackEntryOptions(basis = Some(4), minSize = 4, maxSize = Some(4))),
      StackEntry(filler)
    )))
    val footer    = new Component:
      override def render(width: Int): ComponentRender = ComponentRender.text("FOOTER")
    val root      = VStack(Seq(
      StackEntry(outer, StackEntryOptions(grow = 1, minSize = 1)),
      StackEntry(footer, StackEntryOptions(basis = Some(1), minSize = 1, maxSize = Some(1)))
    ))
    ViewportLayoutEngine.layout(root, 10, 5)
    inner.scrollTo(2)
    outer.scrollTo(3)
    val frame     = ViewportLayoutEngine.layout(root, 10, 5).render
    assertEquals(frame.controls.size, 1)
    val placement = frame.controls.head
    assertEquals(placement.row, 0)
    assertEquals(placement.control.rows, 3)
    assert(TerminalRenderControlEncoder.encode(placement.control).contains(",y=30,"))
    assert(frame.lines(4).contains("FOOTER"))

  test("overlay cover and uncover preserve a cropped upload"):
    val view     = ScrollView(image(), primary = true)
    val terminal = VirtualTerminal(10, 4)
    val tui      = TUI.fullscreen(terminal, view)
    tui.start()
    try
      view.scrollTo(2)
      tui.flushRender()
      terminal.clearWrites()
      val panel   = new Component:
        override def render(width: Int): ComponentRender = ComponentRender.text("XX")
      val overlay = tui.showOverlay(
        panel,
        OverlayOptions(
          width = Some(OverlaySize.Absolute(2)),
          row = Some(OverlaySize.Absolute(1)),
          col = Some(OverlaySize.Absolute(2)),
          focusCapturing = false
        )
      )
      tui.flushRender()
      assert(terminal.output.contains("\u001b_Ga=d"))
      assert(!terminal.output.contains("\u001b_Ga=p"))
      terminal.clearWrites()
      overlay.hide()
      tui.flushRender()
      assert(terminal.output.contains("\u001b_Ga=p"))
      assert(terminal.output.contains(",y=20,"))
      assert(!terminal.output.contains("\u001b_Ga=T"))
    finally tui.stop()
