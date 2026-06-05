## MODIFIED Requirements

### Requirement: Editor autocomplete overlay placement
The editor autocomplete API SHALL position suggestion overlays adjacent to the rendered editor input area by default, without requiring application code to compute terminal-global rows manually. Applications MUST still be able to override autocomplete overlay placement when they need custom positioning.

#### Scenario: Demo suggestions appear next to editor
- **WHEN** slash-command suggestions are shown in the shared interactive demo
- **THEN** the suggestion overlay appears immediately after the rendered editor area, clamped by normal overlay bounds, instead of at the terminal bottom by default

#### Scenario: Application updates autocomplete overlay placement
- **WHEN** an application can compute the editor's current rendered row during layout
- **THEN** it can update editor autocomplete overlay options before compositing so suggestions track the editor position on resize

#### Scenario: Editor default placement tracks rendered height
- **WHEN** the editor renders multiple wrapped or logical lines and autocomplete suggestions are visible
- **THEN** the editor positions the suggestion overlay after the rendered editor area using its current visual height

#### Scenario: Demo does not compute autocomplete row manually
- **WHEN** the shared interactive demo renders the editor with autocomplete enabled
- **THEN** demo layout code does not manually calculate terminal row values for the editor's suggestion overlay

#### Scenario: Custom placement override is preserved
- **WHEN** an application configures explicit autocomplete overlay options
- **THEN** the editor respects those options instead of replacing them with default adjacent placement

#### Scenario: Resize updates adjacent placement
- **WHEN** terminal width changes and editor wrapping changes while autocomplete is visible
- **THEN** the suggestion overlay is repositioned next to the editor's newly rendered visual area
