## ADDED Requirements

### Requirement: Input reports precise handling results
The `Input` component SHALL return `Ignored` when no action matches an input event, `NoRender` when a recognized action requires no repaint, and `Render` when an action mutates repaint-requiring state or invokes an existing callback whose current contract requires repaint. Result precision SHALL NOT change callback conditions or callback cardinality and SHALL NOT imply parent input bubbling.

#### Scenario: Unsupported input is ignored
- **WHEN** `Input` receives a terminal input event that matches no supported or configured action
- **THEN** it returns `Ignored` without invoking an editing or submit callback

#### Scenario: Recognized no-op needs no render
- **WHEN** `Input` recognizes an action but the action causes no repaint-requiring mutation or callback
- **THEN** it returns `NoRender`

#### Scenario: Mutation requests render
- **WHEN** `Input` handles an action that mutates visible value, cursor, selection, or other repaint-requiring state
- **THEN** it returns `Render`

#### Scenario: Submit preserves callback behavior
- **WHEN** `Input` receives its configured submit action
- **THEN** it returns the result required by the existing submit behavior and invokes the submit callback under exactly the existing conditions and number of times

#### Scenario: Ignored does not promise parent routing
- **WHEN** `Input` returns `Ignored`
- **THEN** the result states only that `Input` did not handle the event and does not claim that a parent component receives it
