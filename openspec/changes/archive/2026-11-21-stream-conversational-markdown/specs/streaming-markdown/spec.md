## ADDED Requirements

### Requirement: Bounded opt-in streaming
The library SHALL expose opt-in append, finish and reset behavior without changing complete-document Markdown or parser plugin contracts. Oversized chunks SHALL be rejected atomically against a configurable UTF-16 source limit.

#### Scenario: Admission and completion
- **WHEN** a chunk exceeds capacity or arrives after finish
- **THEN** stored source remains unchanged and admission returns false

### Requirement: Conservative parsing and provisional display
The baseline stream SHALL reuse only proven complete prefixes, retain ambiguous grammar in its reparsed suffix, and expose deterministic parser and scan work counters. Custom parsers SHALL receive whole original source. Live baseline rendering MAY project unfinished inline syntax; finishing SHALL restore ordinary complete-document output.

#### Scenario: Ambiguous continuation
- **WHEN** a list is followed by blank lines and another same-kind item
- **THEN** the list remains part of the reparsed suffix

#### Scenario: Incomplete link
- **WHEN** the baseline tail contains an unfinished inline link destination
- **THEN** live output shows the label without publishing an incomplete hyperlink

### Requirement: Application-owned streaming viewer
The example SHALL retain one ScrollView and child, follow output only while already following the end, and leave transport/provider integration application-owned.

#### Scenario: Manual scrolling
- **WHEN** new output arrives while the user has scrolled away from the end
- **THEN** the viewport retains its bounded offset rather than jumping to the new end
