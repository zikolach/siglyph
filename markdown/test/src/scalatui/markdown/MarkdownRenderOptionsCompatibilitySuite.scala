package scalatui.markdown

import scalatui.terminal.{TerminalCapabilities, TerminalCapabilitiesSource}

class MarkdownRenderOptionsCompatibilitySuite extends munit.FunSuite:
  test("legacy four-argument apply eta expansion and extractor retain their source shape"):
    val theme        = MarkdownTheme()
    val capabilities = TerminalCapabilities.Conservative
    val highlighter  = Option.empty[MarkdownCodeHighlighter]
    val constructor: (
        MarkdownTheme,
        TerminalCapabilities,
        Option[MarkdownCodeHighlighter],
        Boolean
    ) => MarkdownRenderOptions = MarkdownRenderOptions.apply
    val options      = constructor(theme, capabilities, highlighter, true)
    val extracted    = options match
      case MarkdownRenderOptions(a, b, c, d) => (a, b, c, d)

    assertEquals(extracted, (theme, capabilities, highlighter, true))
    assertEquals(options.capabilitySource, TerminalCapabilitiesSource.Fixed)

  test("new named capability source and copy remain available"):
    val options = MarkdownRenderOptions(capabilitySource = TerminalCapabilitiesSource.Session)

    assertEquals(
      options.copy(preserveSourceListMarkers = true).capabilitySource,
      TerminalCapabilitiesSource.Session
    )
