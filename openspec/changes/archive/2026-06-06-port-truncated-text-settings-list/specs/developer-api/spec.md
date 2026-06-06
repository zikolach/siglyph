## ADDED Requirements

### Requirement: Public utility component APIs
The public core API SHALL expose Scala-idiomatic `TruncatedText` and `SettingsList` component APIs without adding third-party runtime dependencies.

#### Scenario: Application creates TruncatedText
- **WHEN** an application creates `TruncatedText` with text and padding options
- **THEN** it can add it to a component tree and render it without platform-specific code

#### Scenario: Application creates SettingsList
- **WHEN** an application creates `SettingsList` with setting item models, options, and callbacks
- **THEN** it can add it to a component tree and handle settings changes through typed Scala callbacks

#### Scenario: Utility components compile for JVM and Native core
- **WHEN** the utility component APIs are compiled by JVM core and Scala Native core modules
- **THEN** they compile without JVM-only, Native-only, Node.js, or third-party runtime dependencies

### Requirement: SettingsList public models
The public API SHALL expose backend-independent settings-list item, options, and callback models for labels, current values, optional descriptions, optional cycle values, search/filter configuration, change callbacks, and cancel callbacks.

#### Scenario: Setting item models are typed
- **WHEN** an application defines setting items
- **THEN** ids, labels, current values, descriptions, and cycle values are represented by public Scala types rather than raw terminal escape strings

#### Scenario: Change callback receives setting id and value
- **WHEN** a settings item value changes through user input
- **THEN** application code receives the setting id and new value through a typed callback

### Requirement: Loader follow-up planning
This change SHALL include a final exploration/planning step for a future `add-loader-components` change and SHALL NOT implement animated loader components in this change.

#### Scenario: Loader implementation remains deferred
- **WHEN** this change is complete
- **THEN** no new animated loader or cancellable loader component has been added to core

#### Scenario: Loader follow-up is captured
- **WHEN** this change is complete
- **THEN** the implementation notes, tasks, or follow-up artifacts summarize recommended scope for `add-loader-components`, including timer lifecycle, cancellation, and JVM/Native compatibility questions

### Requirement: Utility component documentation
New public utility component APIs SHALL include Scaladoc and project documentation covering behavior, keyboard controls, width contracts, dependency constraints, and loader deferral.

#### Scenario: Scaladoc documents utility components
- **WHEN** public `TruncatedText`, `SettingsList`, or settings model types are added
- **THEN** their Scaladoc explains rendering behavior, input behavior, callbacks, platform scope, and non-goals

#### Scenario: Project docs explain loader deferral
- **WHEN** the project documentation is updated for this change
- **THEN** it explains that loader components are planned separately because animation and cancellation require a focused runtime design
