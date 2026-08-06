## MODIFIED Requirements

### Requirement: Height-aware resize redraws
The TUI runtime SHALL track both terminal width and terminal height changes across renders and SHALL repaint after dimension changes according to the active screen mode and configured normal-screen resize clear policy. Existing callers that do not configure the policy SHALL retain full-clear behavior. A configured normal resize recovery provider SHALL add a bounded recovery prefix only to committed normal-screen preserve-scrollback geometry-change redraws.

#### Scenario: Normal-screen width resize redraws with full clear
- **WHEN** terminal width changes after a previous render in normal-screen mode with default options
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Normal-screen height resize redraws with full clear
- **WHEN** terminal height changes after a previous render in normal-screen mode with default options
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the viewport and scrollback with `CSI 2 J`, `CSI H`, and `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Preserve-scrollback resize omits scrollback clearing
- **WHEN** terminal dimensions change in normal-screen mode with the preserve-scrollback policy configured and no recovery provider
- **THEN** the TUI clears and homes the active viewport, omits `CSI 3 J`, and writes the recomputed frame without entering alternate screen

#### Scenario: Preserve-scrollback resize includes configured recovery
- **WHEN** terminal dimensions change after a committed frame in normal-screen mode with preserve-scrollback policy and a recovery provider configured
- **THEN** the TUI clears and homes the active viewport, omits `CSI 3 J`, writes at most the provider's strict recovery row budget, then writes the recomputed retained frame in the same synchronized output without entering alternate screen

#### Scenario: Resize with overlay recomputes layout
- **WHEN** terminal dimensions change while an autocomplete overlay is visible in normal-screen mode
- **THEN** the overlay is re-resolved and composited into the resize redraw using the configured normal-screen clear policy, its final rows reduce any recovery budget, and output does not enter alternate-screen mode

#### Scenario: Alternate-screen resize redraw clears active viewport
- **WHEN** terminal dimensions change after a previous render while alternate-screen mode is active
- **THEN** the TUI emits synchronized output with autowrap disabled, clears the active alternate-screen viewport, homes the cursor, and writes the recomputed frame without emitting another alternate-screen enter sequence, `CSI 3 J`, or normal-screen recovery output

#### Scenario: Redundant resize notification preserves the viewport
- **WHEN** a backend resize callback reports dimensions equal to the committed width and height
- **THEN** the TUI SHALL NOT invoke recovery or destructively clear the active viewport

#### Scenario: Generic forced redraw does not recover
- **WHEN** application or runtime work requests a forced or cleared redraw without a terminal width or height change
- **THEN** the TUI SHALL preserve existing redraw behavior and SHALL NOT invoke the normal resize recovery provider

#### Scenario: Coalesced resizes recover latest geometry
- **WHEN** multiple terminal resize notifications invalidate an unpublished recovery/live candidate
- **THEN** the TUI SHALL emit no stale candidate, preserve the prior committed baseline, and perform recovery for the latest coalesced positive dimensions and generation
