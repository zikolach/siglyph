## ADDED Requirements

### Requirement: Fullscreen graphics follow destructive row output

The fullscreen renderer SHALL paint typed graphics after all text writes and row
erases for the frame. It SHALL restore graphics whose cell footprint intersects
the painted rows even when the graphic anchor is unchanged and precedes them.

#### Scenario: Initial multirow graphic
- **WHEN** a fullscreen frame contains a multirow graphic and subsequent text rows
- **THEN** graphic output follows all painted text and row erases

#### Scenario: Changed row below an unchanged anchor
- **WHEN** text below an unchanged Kitty anchor changes within the graphic footprint
- **THEN** the matching retained upload is placed after the row output without retransmission

### Requirement: Fullscreen clearing preserves retained Kitty uploads

The fullscreen renderer SHALL NOT emit ED2 while reusable Kitty uploads remain
retained. Full redraws SHALL still erase all current viewport rows before placing
images. Retention limits and typed-control authority SHALL remain in effect.

#### Scenario: Forced full redraw
- **WHEN** a forced redraw reuses an uploaded Kitty image
- **THEN** it uses placement-only output without ED2 or payload retransmission

#### Scenario: Resize full redraw
- **WHEN** a resized viewport reuses an uploaded Kitty image with unchanged geometry
- **THEN** it clears viewport rows without ED2 and restores the image after text

#### Scenario: Overlay uncover
- **WHEN** an overlay hides a retained image and is subsequently hidden
- **THEN** the image placement is removed while covered and restored without retransmission when uncovered
