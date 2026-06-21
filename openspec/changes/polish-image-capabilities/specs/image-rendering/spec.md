## ADDED Requirements

### Requirement: Warp Kitty image capability detection
Terminal capability detection SHALL treat Warp as Kitty-image capable when not running inside tmux, screen, or another known unsupported multiplexer path.

#### Scenario: Warp TERM_PROGRAM enables Kitty images
- **WHEN** environment detection sees Warp terminal indicators outside a multiplexer
- **THEN** terminal capabilities report Kitty image support

#### Scenario: Warp session variable enables Kitty images
- **WHEN** environment detection sees `WARP_SESSION_ID` or `WARP_TERMINAL_SESSION_UUID` outside a multiplexer
- **THEN** terminal capabilities report Kitty image support

#### Scenario: Multiplexer disables Warp image support
- **WHEN** Warp indicators are present but the environment is inside tmux or screen without supported forwarding
- **THEN** terminal capabilities do not report image support

### Requirement: Cell-size-aware image sizing
Image sizing that opts into runtime terminal cell dimensions SHALL use queried dimensions when valid data is available and SHALL fall back to deterministic default cell dimensions when unavailable. Image sizing with fixed `ImageRenderOptions.cellDimensions` SHALL use those fixed dimensions exactly. The high-level `Image` component SHALL opt into runtime sizing by default because it renders inside a `TUI` lifecycle that owns terminal cell-size queries.

#### Scenario: Valid cell dimensions affect image rows
- **WHEN** image cell-size calculation receives valid terminal cell pixel dimensions
- **THEN** calculated image cell rows preserve image aspect ratio using those dimensions

#### Scenario: Missing cell dimensions use default
- **WHEN** runtime image sizing has no valid terminal cell dimensions available
- **THEN** image sizing uses the documented default cell dimensions

#### Scenario: Fixed cell dimensions remain deterministic
- **WHEN** image sizing receives fixed `ImageCellDimensions`
- **THEN** image sizing uses those fixed dimensions without reading cached runtime dimensions

#### Scenario: Invalid cell dimensions are ignored
- **WHEN** terminal cell dimensions are zero, negative, or malformed
- **THEN** image sizing ignores them and uses default dimensions

### Requirement: Image row reservation remains cursor-safe
Image rendering SHALL reserve terminal rows consistently so text rendered after terminal-owned image output appears below the image area.

#### Scenario: Kitty image reserves reported rows
- **WHEN** a Kitty image render result reports a positive row count
- **THEN** the component output reserves that many terminal rows before later content is rendered

#### Scenario: iTerm2 image reserves reported rows
- **WHEN** an iTerm2 image render result reports a positive row count
- **THEN** the component output reserves that many terminal rows before later content is rendered

#### Scenario: Fallback image row is width-safe
- **WHEN** image protocol support is unavailable
- **THEN** fallback text renders as readable width-safe terminal output
