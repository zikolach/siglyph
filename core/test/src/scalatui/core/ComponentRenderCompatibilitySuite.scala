package scalatui.core

class ComponentRenderCompatibilitySuite extends munit.FunSuite:
  test("legacy three-argument apply eta expansion and extractor retain their source shape"):
    val lines     = Vector("text")
    val controls  = Vector.empty[TerminalControlPlacement]
    val cursors   = Vector(CursorPlacement(0, 0))
    val constructor: (
        Vector[String],
        Vector[TerminalControlPlacement],
        Vector[CursorPlacement]
    ) => ComponentRender = ComponentRender.apply
    val render    = constructor(lines, controls, cursors)
    val extracted = render match
      case ComponentRender(a, b, c) => (a, b, c)

    assertEquals(extracted, (lines, controls, cursors))
    assertEquals(render.documentMetadata, DocumentMetadata.empty)

  test("new named metadata construction and copy remain available"):
    val metadata = DocumentMetadata(Vector(PromptStart(DocumentPosition(0))))
    val render   = ComponentRender(
      lines = Vector("text"),
      controls = Vector.empty,
      cursorPlacements = Vector.empty,
      documentMetadata = metadata
    )

    assertEquals(render.copy(lines = Vector("copy")).documentMetadata, metadata)
