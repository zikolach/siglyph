## ADDED Requirements

### Requirement: SelectList reports precise handling results
The `SelectList` component SHALL return `Ignored` when no action matches an input event, `NoRender` when a recognized action requires no repaint, and `Render` when an action mutates repaint-requiring selection or filter state or invokes an existing callback whose current contract requires repaint. Result precision SHALL NOT change callback conditions or callback cardinality and SHALL NOT imply parent input bubbling.

#### Scenario: Unsupported input is ignored
- **WHEN** `SelectList` receives a terminal input event that matches no supported or configured action
- **THEN** it returns `Ignored` without invoking selection or activation callbacks

#### Scenario: Recognized boundary navigation needs no render
- **WHEN** `SelectList` recognizes a navigation action but selection and visible state remain unchanged at a boundary
- **THEN** it returns `NoRender` and does not add a callback invocation

#### Scenario: Selection mutation requests render
- **WHEN** `SelectList` navigation changes the selected item
- **THEN** it returns `Render` and invokes the existing selection-change callback exactly as before

#### Scenario: Filter mutation requests render
- **WHEN** `SelectList` handles input that changes its visible filter state
- **THEN** it returns `Render` while preserving existing filtering and callback conditions

#### Scenario: Activation preserves callback behavior
- **WHEN** `SelectList` receives its configured activation action
- **THEN** it returns the result required by the existing activation behavior and invokes the activation callback under exactly the existing conditions and number of times

#### Scenario: Ignored does not promise parent routing
- **WHEN** `SelectList` returns `Ignored`
- **THEN** the result states only that `SelectList` did not handle the event and does not claim that a parent component receives it
