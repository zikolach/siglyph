package scalatui.core

import scalatui.components.ScrollView

class ViewportSnapshotSuite extends munit.FunSuite:
  private final class Lines extends Component:
    override def render(width: Int): ComponentRender = ComponentRender.text(Vector.fill(8)("row"))

  private def snapshot(child: Component, revision: Long, marker: Int): ViewportSnapshot =
    ViewportSnapshot(
      Vector.fill(8)("row"),
      0,
      true,
      DocumentMetadata(Vector(PromptStart(DocumentPosition(marker, 0)))),
      child,
      revision,
      8,
      2,
      10,
      LayoutViewport(10, 2),
      0
    )

  test("current publication commits geometry and semantic metadata together"):
    val child = Lines()
    val view  = ScrollView(child)
    view.commitViewportSnapshot(snapshot(child, view.viewportContentRevision, 3))
    assertEquals(view.contentExtent, 8)
    assertEquals(view.viewportExtent, 2)
    view.scrollToPrompt(1)
    assertEquals(view.offset, 3)

  test("replaced child cannot publish geometry or metadata"):
    val child       = Lines()
    val view        = ScrollView(child)
    val stale       = snapshot(child, view.viewportContentRevision, 3)
    val replacement = Lines()
    view.replaceContent(replacement)
    view.commitViewportSnapshot(snapshot(replacement, view.viewportContentRevision, 5))
    view.commitViewportSnapshot(stale.copy(contentExtent = 100, viewportExtent = 1))
    assertEquals(view.contentExtent, 8)
    assertEquals(view.viewportExtent, 2)
    view.scrollToPrompt(1)
    assertEquals(view.offset, 5)

  test("same-child invalidation rejects stale publication and early geometry"):
    val child = Lines()
    val view  = ScrollView(child)
    val stale = snapshot(child, view.viewportContentRevision, 3)
    view.invalidateSearchDocument()
    view.commitViewportSnapshot(snapshot(child, view.viewportContentRevision, 5))
    assertEquals(
      view.commitViewportGeometry(100, 1, 10, LayoutViewport(10, 1), child, stale.revision),
      0
    )
    view.commitViewportSnapshot(stale)
    assertEquals(view.contentExtent, 8)
    view.scrollToPrompt(1)
    assertEquals(view.offset, 5)

  test("replacement during measurement rejects early geometry"):
    var replace: () => Unit = () => ()
    val child               = new Component with ViewportRangeRenderer:
      override def contentExtent(width: Int): Int                                         =
        replace()
        100
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        ComponentRender.text(Vector.fill(rowCount)("old"))
      override def render(width: Int): ComponentRender                                    = ComponentRender.text(Vector("old"))
    val view                = ScrollView(child)
    replace = () => view.replaceContent(Lines())
    ViewportLayoutEngine.layout(view, 10, 2)
    assertEquals(view.contentExtent, 0)
    assertEquals(view.viewportExtent, 0)

  test("invalidation during row rendering rejects metadata publication"):
    var invalidateDocument: () => Unit = () => ()
    val child                          = new Component with ViewportRangeRenderer with ViewportDocumentMetadataProvider:
      override def contentExtent(width: Int): Int                                         = 8
      override def renderRange(width: Int, startRow: Int, rowCount: Int): ComponentRender =
        invalidateDocument()
        ComponentRender.text(Vector.fill(rowCount)("row"))
      override def viewportDocumentMetadata(width: Int): DocumentMetadata                 =
        DocumentMetadata(Vector(PromptStart(DocumentPosition(3, 0))))
      override def render(width: Int): ComponentRender                                    = ComponentRender.text(Vector("row"))
    val view                           = ScrollView(child)
    invalidateDocument = () => view.invalidateSearchDocument()
    ViewportLayoutEngine.layout(view, 10, 2)
    assertEquals(view.contentExtent, 8) // Early clamp remains available during rendering.
    view.scrollToPrompt(1)
    assertEquals(view.offset, 0)

  test("legacy provider defaults delegate geometry metadata and text callbacks"):
    val child = Lines()
    final class LegacyProvider extends Component with ViewportScrollProvider:
      var geometry                                                                                 = 0
      var metadata                                                                                 = DocumentMetadata.empty
      var rows                                                                                     = Vector.empty[String]
      override def render(width: Int): ComponentRender                                             = ComponentRender.text(Vector.empty)
      override private[scalatui] def commitViewportGeometry(
          contentExtent: Int,
          viewportExtent: Int,
          viewportWidth: Int,
          layoutViewport: LayoutViewport
      ): Int                                                                                       =
        geometry = contentExtent
        1
      override private[scalatui] def commitViewportDocumentMetadata(value: DocumentMetadata): Unit =
        metadata = value
      override private[scalatui] def commitViewportText(
          value: Vector[String],
          startRow: Int,
          complete: Boolean,
          child: Component,
          revision: Long
      ): Unit                                                                                      = rows = value
    val provider = LegacyProvider()
    val publication = snapshot(child, 0L, 3)
    assertEquals(provider.commitViewportGeometry(8, 2, 10, LayoutViewport(10, 2), child, 0L), 1)
    provider.commitViewportSnapshot(publication)
    assertEquals(provider.geometry, 8)
    assertEquals(provider.metadata, publication.metadata)
    assertEquals(provider.rows, publication.rows)
