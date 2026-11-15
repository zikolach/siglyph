## ADDED Requirements

### Requirement: Validated crop controls preserve layout boundaries
Layout SHALL treat a protocol-validated cropped image as a complete control with its visible cell footprint. Source geometry SHALL participate in control identity so crop-only changes invalidate retained output. Overlay occlusion SHALL continue suppressing intersecting images as whole controls.

#### Scenario: Crop changes without cell movement
- **WHEN** the source crop changes but its visible row, column, width and height do not
- **THEN** the retained renderer detects a changed control and repaints its placement

#### Scenario: Overlay covers a cropped image
- **WHEN** an overlay intersects the cropped visible footprint
- **THEN** the image is suppressed rather than cropped around the overlay
