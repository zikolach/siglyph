# Scroll and selection input policies

SelectList and SettingsList recognize the existing default SelectPageUp/SelectPageDown commands. Pages move by `max(1, maxVisible)` items, matching Editor autocomplete, clamp to filtered results, and consume even empty/boundary pages. Arrows, filtering and effect ownership are unchanged. TUI focused-first key routing and Editor are unchanged.

## Wheel input

Default automatic runtime routing remains one row, or five with Alt. Set `tui.wheelScrollPolicy = WheelScrollPolicy(fixedLines = 3)` or an accelerating policy to configure ordinary terminal input. The runtime samples its existing injected monotonic mouse clock and normalizes once before routing nested remainders; changing policy or starting a new session resets gesture history. `WheelScrollPolicy()` preserves that behavior. `WheelScrollController(WheelScrollPolicy(fixedLines = 3))` opts into a fixed step; `accelerate = true` opts into bounded velocity scaling (default cap six, configuration cap 100, Alt multiplier five).

Time is an explicit monotonic millisecond value, not wall time. One controller belongs to a serialized input stream. The first event, a pause over 200 ms, direction change or backwards timestamp resets velocity. Events less than 5 ms apart move one row and reset the average to avoid burst/high-resolution double acceleration. Otherwise a smoothed interval scales 100 ms per row, capped deterministically; there are no timers or retained event queues.

Set `terminalAccelerated = true` for local macOS/trackpads/already-scaled streams; this bypasses acceleration. Core deliberately does not guess the client OS (especially across SSH), probe preferences, or read/mutate global environment.

At a custom routing boundary call `controller.normalize(originalWheel, monotonicMillis)` **once**, then route that event and its signed remainder normally. For direct ScrollView dispatch, `view.handleWheelAt(originalWheel, monotonicMillis, controller)` performs that normalization and returns the ordinary MouseHandlerResult. Route its remainder to ancestor `handleMouseEvent`, never `handleWheelAt`. Semantic `lineDelta` is already normalized and ScrollView never accelerates it again. Custom dispatch remains opt-in; ordinary TUI dispatch uses the policy installed on that TUI. Contain discards remainder; Chain preserves it.

## Selection

Pass `wordSelectionPolicy = WordSelectionPolicy.PathFriendly` to ScrollView to include dots, backslashes, tildes and plus signs in double-click selection and word dragging. `WordSelectionPolicy(extraCharacters = ".+")` customizes joiners. Default selection still joins letters/digits/underscore and slash/hyphen. Extra characters are complete grapheme clusters, bounded to 256 UTF-16 units; whitespace and controls are rejected. Existing row, byte, grapheme, clipboard and scan bounds remain unchanged. Selection never crosses a row or splits a grapheme.

## Provenance and verification

Inspired by pi-tui `wheel-scroll.ts`, `word-navigation.ts`, and list paging at upstream pin `8636e211e7834e4ca46a22aec1d65dfd2eac8677`. This is not a wholesale parity claim: no platform subprocess, Intl segmenter, fractional carry, or default acceleration is introduced. Shared deterministic regressions live in `ViewportInputPoliciesSuite`, with existing `ViewportAffordanceSuite` and `UtilityComponentsSuite` guarding routing and component behavior.
