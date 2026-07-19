# `pi-tui` compatibility matrix

This matrix records the review baseline rather than claiming source compatibility. The pinned
upstream reference is [`earendil-works/pi` commit
`8479bd84743e8889f728acb21a62794102db0529`](https://github.com/earendil-works/pi/tree/8479bd84743e8889f728acb21a62794102db0529/packages/tui)
from 2026-07-11. Status describes user-visible behavior available in Siglyph at this repository
revision; Scala APIs remain Scala-idiomatic and are not drop-in TypeScript APIs.

Status meanings:

- **Full**: the reviewed high-level behavior is present for Siglyph's documented platform scope.
- **Partial**: the principal behavior is present, with a named gap below.
- **Deviation**: Siglyph deliberately uses a different contract.
- **Extension**: behavior has no direct reviewed `pi-tui` counterpart.

## API-surface coverage

| Upstream area | Siglyph surface | Coverage | Local evidence |
| --- | --- | --- | --- |
| Component/runtime foundation | `Component`, `ComponentRender`, `TUI`, `TUIContext`, containers and focus | Full conceptual surface; typed render/input APIs are a deviation | [runtime tests](../core/test/src/scalatui/core/TUISuite.scala), [render tests](../core/test/src/scalatui/core/ComponentRenderSuite.scala) |
| Basic components | `Text`, `Box`, `Spacer`, `TruncatedText`, `Input` | Full | [component tests](../core/test/src/scalatui/components/ComponentsSuite.scala), [utility tests](../core/test/src/scalatui/components/UtilityComponentsSuite.scala) |
| Rich components | `Editor`, `SelectList`, `SettingsList`, `Loader`, `CancellableLoader` | Full named surface; selected behaviors remain partial below | [editor tests](../core/test/src/scalatui/components/EditorSuite.scala), [select tests](../core/test/src/scalatui/components/SelectListSuite.scala), [loader tests](../core/test/src/scalatui/components/LoaderConcurrencySuite.scala) |
| Autocomplete and fuzzy matching | slash commands, paths, attachments, trigger sources, combined providers, fuzzy ranking | Partial: async integration is callback/cancellation based; bounded NIO search replaces upstream process-based discovery | [autocomplete tests](../core/test/src/scalatui/autocomplete/AutocompleteSuite.scala), [filesystem tests](../core/test/src/scalatui/autocomplete/FileSystemPathCompletionProviderSuite.scala) |
| Markdown | pluggable `MarkdownParser`, `MarkdownRenderer`, `Markdown` component | Partial: baseline parser is intentionally smaller than `marked`/CommonMark | [Markdown tests](../markdown/test/src/scalatui/markdown/MarkdownRendererSuite.scala), [parser research](markdown-parser-research.md) |
| Images | typed Kitty/iTerm2 controls, validated payloads, file/byte loading, `Image` component | Partial/deviation: no string-prefix authority, scaling, or transcoding | [protocol tests](../core/test/src/scalatui/terminal/TerminalImageProtocolSuite.scala), [image tests](../image/test/src/scalatui/image/ImageSuite.scala) |
| Terminal and input | terminal abstraction, streaming parser, keybindings, JVM `stty`, Native POSIX | Full for macOS/Linux scope; terminal-specific modifier reports remain partial | [parser tests](../core/test/src/scalatui/terminal/TerminalInputParserSuite.scala), [JVM tests](../terminalJvm/test/src/scalatui/terminal/jvm/SttyTerminalSuite.scala), [Native tests](../terminalNative/test/src/scalatui/terminal/native/PosixTerminalSuite.scala) |

Every high-level component category present in the pinned upstream `packages/tui/src/components`
directory has a Siglyph counterpart. This is component-name completeness, not a claim that every
constructor, TypeScript helper, or Node integration API is reproduced.

## Behavioral parity

| Behavior | Status | Scope or remaining gap | Local evidence |
| --- | --- | --- | --- |
| Differential rendering, synchronized output, resize repaint, overlays and focus | Full | Normal-screen full clear remains the default; scrollback preservation is opt-in | [TUI tests](../core/test/src/scalatui/core/TUISuite.scala), [overlay tests](../core/test/src/scalatui/core/OverlayRendererSuite.scala) |
| ANSI-aware text layout and Unicode editing | Full | Unicode 17 UAX #29 segmentation is stronger and version-pinned; terminal width is still a policy | [ANSI tests](../core/test/src/scalatui/ansi/AnsiSuite.scala), [Unicode tests](../core/test/src/scalatui/unicode/UnicodeSuite.scala) |
| Editor movement, history, undo, kill/yank, paste, callbacks and autocomplete overlays | Full for documented commands | Raw standalone-backslash Enter workaround is intentionally absent | [editor tests](../core/test/src/scalatui/components/EditorSuite.scala), [keybinding defaults](keybinding-defaults.md) |
| Typed keyboard coverage | Partial | Common CSI/SS3/function/keypad/control forms are typed; unknown and cursor-report-ambiguous forms stay raw | [parser tests](../core/test/src/scalatui/terminal/TerminalInputParserSuite.scala), [limitations](keybinding-defaults.md) |
| Select/settings filtering, navigation, descriptions, scrolling and cancellation | Full core behavior | Application-provided submenus use the general overlay contract | [select tests](../core/test/src/scalatui/components/SelectListSuite.scala), [component tests](../core/test/src/scalatui/components/ComponentsSuite.scala) |
| Loader rendering and cancellation | Partial/deviation | Ticking is application-owned; cancellation is atomic and dependency-free instead of timer/`AbortSignal` based | [loader tests](../core/test/src/scalatui/components/LoaderConcurrencySuite.scala), [loader notes](loader-components-follow-up.md) |
| Filesystem and attachment completion | Partial/deviation | Canonically contained, bounded NIO traversal replaces `fd`; recursive attachment search is opt-in | [filesystem tests](../core/test/src/scalatui/autocomplete/FileSystemPathCompletionProviderSuite.scala), [smoke contract](interactive-smoke.md) |
| Markdown rendering | Partial | Headings, emphasis, code, lists, links, quotes, rules and simple tables are readable; nested/full CommonMark needs an adapter | [Markdown tests](../markdown/test/src/scalatui/markdown/MarkdownRendererSuite.scala), [research](markdown-parser-research.md) |
| Terminal images | Partial/deviation | Kitty/iTerm2 output and runtime geometry are present; image decoding/scaling remains optional future work | [image geometry tests](../core/test/src/scalatui/core/ImageGeometrySuite.scala), [image tests](../image/test/src/scalatui/image/ImageSuite.scala) |
| Raw-mode lifecycle and cleanup | Full for macOS/Linux | Windows is excluded; emulator-specific visual conformance is not claimed | [PTY tests](../terminalJvm/test/src/scalatui/terminal/jvm/SttyTerminalPtySuite.scala), [terminal conformance](terminal-conformance.md) |

## Intentional deviations

| Area | Siglyph contract | Reason/evidence |
| --- | --- | --- |
| Input | Components consume typed, bounded `TerminalInput`; genuinely ambiguous or unknown sequences remain raw | [input parser tests](../core/test/src/scalatui/terminal/TerminalInputParserSuite.scala) |
| Output authority | Ordinary strings cannot acquire image/cursor/protocol authority; controls and cursor placements are typed metadata | [typed-control tests](../core/test/src/scalatui/core/TUITypedControlSuite.scala) |
| Runtime architecture | Shared core has no Node event loop, mandatory executor, `Promise`, `AbortSignal`, or effect type | [porting notes](porting-notes.md) |
| Loaders | Applications call `tick()` or adapt their scheduler; the component owns no interval | [loader notes](loader-components-follow-up.md) |
| Autocomplete | Filesystem discovery is dependency-free, bounded, and containment checked; forced single-result application is opt-in | [filesystem tests](../core/test/src/scalatui/autocomplete/FileSystemPathCompletionProviderSuite.scala), [README](../README.md) |
| Markdown | The baseline parser is dependency-free and pluggable rather than a mandatory `marked` dependency | [parser research](markdown-parser-research.md) |
| Images | Payloads are validated types, high-level geometry is session-owned, and string-prefix detection is absent | [image tests](../image/test/src/scalatui/image/ImageSuite.scala), [geometry tests](../core/test/src/scalatui/core/ImageGeometrySuite.scala) |
| Backends | JVM uses `stty`; Scala Native uses POSIX termios/ioctl; Windows is out of scope | [terminal conformance](terminal-conformance.md) |

## Siglyph extensions

| Extension | Contract | Local evidence |
| --- | --- | --- |
| Scala Native support | Shared core and optional modules compile/test with a POSIX Native backend | [Native terminal tests](../terminalNative/test/src/scalatui/terminal/native/PosixTerminalSuite.scala), [CI](../.github/workflows/ci.yml) |
| Coordinate-aware mouse routing | Opt-in SGR mouse parsing and committed-layout hit testing | [TUI mouse tests](../core/test/src/scalatui/core/TUISuite.scala) |
| Structured runtime diagnostics | Redacted per-TUI lifecycle, resize, redraw and write metadata; throwing observers are isolated | [diagnostic tests](../core/test/src/scalatui/core/TUIDiagnosticsSuite.scala), [runtime documentation](runtime-diagnostics.md) |
| Resize scrollback policy | Normal-screen redraw can preserve scrollback without changing the legacy default | [diagnostic/resize tests](../core/test/src/scalatui/core/TUIDiagnosticsSuite.scala) |
| Unicode conformance | Committed Unicode 17.0.0 tables and official GraphemeBreak fixtures on JVM and Native | [Unicode tests](../core/test/src/scalatui/unicode/UnicodeSuite.scala) |
| Safe recursive attachment completion | Iterative traversal with containment, depth, visited-entry, result and cancellation bounds | [filesystem tests](../core/test/src/scalatui/autocomplete/FileSystemPathCompletionProviderSuite.scala) |
| Extras module | Dependency-light expandable text/sections and shared expansion state | [extras tests](../extras/test/src/scalatui/extras/ExtrasSuite.scala) |

When the upstream baseline changes, update the pinned commit first, rerun the comparison against
that exact tree, and then revise only rows whose evidence or status changed.
