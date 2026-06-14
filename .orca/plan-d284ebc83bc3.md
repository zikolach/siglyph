# Plan: close-pi-tui-parity-gaps

Close the remaining user-visible pi-tui parity gaps while preserving siglyph/scala-tui’s Scala-first, dependency-light architecture. The epic focuses on richer autocomplete, selector/settings behavior, Markdown rendering, image convenience helpers, and advanced keyboard input without introducing mandatory shell tools, parser libraries, image decoders, or runtime dependencies into baseline modules.

Implementation should favor additive, testable APIs: pure shared-core utilities where possible, optional adapters for richer behavior, conservative terminal negotiation, and explicit platform boundaries for JVM/Native differences. Each slice should land with focused unit or virtual-terminal tests and update docs or demos when it exposes public behavior.

## Task: Add shared fuzzy matching utility
Status: [x]

Implement a dependency-free fuzzy matching/filtering utility with ordered-character, boundary, consecutive, exact, swapped alphanumeric, and tokenized multi-word scoring. Include tests for ranking quality, all-token matching, stable ordering for equal scores, and no-match behavior.

## Task: Wire optional fuzzy ranking into autocomplete providers
Status: [ ]

Add configurable fuzzy ranking to slash, path, and trigger autocomplete flows without changing default deterministic ordering unless enabled. Include tests for equal-score stability and preservation of existing provider output when fuzzy mode is disabled.

## Task: Add bounded filesystem path completion helper
Status: [ ]

Implement an optional PathCompletionProvider helper that enumerates filesystem entries deterministically, enforces max-results, and filters hidden/git entries according to configuration. Include fake-temp-directory tests for result bounds, sort order, directory/file handling, and unreadable or missing paths.

## Task: Support path completion quoting and attachment prefixes
Status: [ ]

Extend the filesystem path helper acceptance logic to preserve attachment prefixes such as @ and @" and apply required quoting/escaping rules. Include tests for spaces, existing quotes, attachment syntax, and plain path completions.

## Task: Make autocomplete refresh cancellation and debouncing robust
Status: [ ]

Ensure rapid editor changes cancel stale autocomplete work, ignore late results, and keep visible suggestions until replacement or explicit cancellation. Include deterministic tests using controlled providers for rapid typing, stale callbacks, empty replacement, and visible suggestion refresh behavior.

## Task: Add configurable stacked trigger prefix autocomplete
Status: [ ]

Support application-owned natural trigger prefixes including # through the combined autocomplete provider, with completion insertion only and no command execution semantics. Include tests for active-token detection, configured source dispatch, accepted replacement text, and non-trigger text fallback.

## Task: Document and demo richer autocomplete
Status: [ ]

Update editor/demo documentation and examples to show built-in filesystem completion, attachment completion, fuzzy ranking, debounce/cancellation expectations, and # trigger usage. Include example coverage that can be exercised without external shell tools.

## Task: Add SelectList rich options and theme hooks
Status: [ ]

Extend SelectList with options/theme hooks for selected and unselected prefixes, selected text, descriptions, no-match text, scroll info, and label truncation. Include width-safety tests for styled selected rows, descriptions, truncation, and narrow render widths.

## Task: Add SelectList filtering and selection-change callbacks
Status: [ ]

Implement SelectList filtering with configurable no-match rendering and invoke a selection-change callback when keyboard navigation changes the highlighted item. Include tests for filter results, empty results, callback payloads, and selection stability after filtering.

## Task: Add optional SelectList fuzzy filtering
Status: [ ]

Use the shared fuzzy utility as an optional SelectList filtering/ranking mode while preserving containment or unfiltered behavior when selected. Include tests for fuzzy rank ordering, stable equal-score ordering, and disabled-fuzzy behavior.

## Task: Add SettingsList optional fuzzy ranking
Status: [ ]

Extend SettingsList filtering to optionally fuzzy-rank rows across id, label, current value, and description while keeping existing case-insensitive containment filtering available. Include tests for ranking, width-safe rendering, disabled-fuzzy containment, and empty matches.

## Task: Add SettingsList submenu support
Status: [ ]

Support settings rows that open application-provided submenu components through existing component/overlay contracts, including update and cancel flows. Include tests for Enter opening a submenu, value update callbacks, Escape cancellation, focus restoration, and avoiding scalar cycling for submenu rows.

## Task: Document and demo richer selector/settings behavior
Status: [ ]

Update demos and docs to exercise SelectList theming/filtering/callbacks and SettingsList fuzzy filtering/submenus. Include concise migration notes explaining how these map to pi-tui parity behavior.

## Task: Add Markdown theme hooks
Status: [ ]

Expose Markdown theme hooks for supported block and inline constructs including headings, links, inline code, fenced code blocks, quotes, horizontal rules, lists, tables, and emphasis. Include width-safety and style-reset tests for headings, code blocks, inline styling, and narrow output.

## Task: Add OSC 8 Markdown link rendering
Status: [ ]

Render Markdown links as OSC 8 hyperlinks when terminal capabilities indicate support, while preserving readable label-plus-URL fallback otherwise. Include tests for hyperlink-capable output, unsupported fallback, width accounting, and style reset behavior.

## Task: Add optional Markdown syntax highlighting hook
Status: [ ]

Introduce an optional fenced-code highlighter hook that returns styled lines while keeping plain code readable when no highlighter is configured. Include tests for language dispatch, styled output width safety, highlighter absence, highlighter failure, and no style leakage.

## Task: Define optional Markdown parser adapter boundary
Status: [ ]

Add an adapter contract allowing richer Markdown parsers to feed the existing MarkdownRenderer/Markdown component without adding baseline dependencies. Include tests for adapter success, parser error fallback, readable fallback rendering, and unchanged baseline behavior.

## Task: Document Markdown parity and deferred behavior
Status: [ ]

Update Markdown docs to distinguish baseline-supported constructs, adapter-supported constructs, optional highlighting, OSC 8 links, and intentional pi-tui deviations. Include examples for fallback readability and adapter-dependent features.

## Task: Add image file loading helper API
Status: [ ]

Provide optional helper APIs that load supported image files into the existing image source contract by returning base64 data, MIME type, and dimensions without changing low-level Image construction. Include tests for PNG success, unreadable files, unsupported files, and typed failures without terminal output.

## Task: Implement dependency-light image dimension sniffing
Status: [ ]

Add byte-stream dimension sniffing for PNG, JPEG, GIF, and WebP where practical, returning typed failures for invalid or unsupported bytes. Include fixture-based tests for each supported format, truncated data, invalid data, and no rendering dependency.

## Task: Add image cell-size bounding helpers
Status: [ ]

Implement helpers that calculate terminal cell dimensions from source pixels, max width/height, and terminal cell metrics before protocol output. Include tests for landscape, portrait height caps, width caps, both caps, zero/unknown dimensions, and aspect preservation.

## Task: Document image helper dependency boundaries
Status: [ ]

Update image examples and docs to show file loading, dimension sniffing, cell bounding, terminal fallback behavior, and where richer parsing/scaling dependencies must live if added later. Include notes that core image protocol output remains dependency-light.

## Task: Extend terminal input model for key event kind
Status: [ ]

Add typed metadata for key press, repeat, and release while preserving existing press-only component behavior and source compatibility where possible. Include parser/model tests for default press behavior, repeat metadata, release metadata, and equality/rendering expectations.

## Task: Add Kitty CSI-u event metadata parsing
Status: [ ]

Expand terminal input parsing for Kitty CSI-u keyboard events including press/repeat/release metadata, super modifiers, keypad edge cases, and malformed sequences. Include parser tests for valid events, unknown modifiers, stale-looking input, fallback parsing, and bracketed paste non-regression.

## Task: Add conservative Kitty keyboard negotiation hooks
Status: [ ]

Implement testable negotiation state for JVM and Native interactive terminal backends with timeout, stale/mismatched-response handling, and fallback to existing parsing. Include stream or fake-backend tests for success, timeout, stale response, unsupported terminal, and exposed protocol state.

## Task: Route key-release events only to interested components
Status: [ ]

Honor wantsKeyRelease or equivalent opt-in so release events reach only components that request them, while repeat/press events preserve normal routing. Include virtual terminal tests for focused interested components, uninterested components, focus changes, and release-event suppression.

## Task: Investigate and implement safe platform modifier fallbacks
Status: [ ]

Document platform-specific modifier fallback options such as Apple Terminal modified Enter, and implement only safe/testable fallback paths that do not block or corrupt normal input. Include tests or documented manual smoke coverage for fallback available and unavailable cases.

## Task: Refresh top-level parity documentation
Status: [ ]

Update README feature tables, porting notes, post-MVP plan, and interactive smoke notes to reflect implemented autocomplete, selector/settings, Markdown, image, and keyboard parity behavior plus remaining intentional deviations.

## Task: Perform final validation pass
Status: [ ]

After implementation tasks land, run the project validation suite expected by the repo: full tests, formatting, Scalafix, and strict OpenSpec validation. Fix any failures and ensure the close-pi-tui-parity-gaps task checklist and spec artifacts accurately reflect completed work.
