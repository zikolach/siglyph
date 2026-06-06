## ADDED Requirements

### Requirement: Public API for advanced text-editing capabilities
The public component APIs SHALL expose configuration and callback points needed to use undo, kill-ring, yank/yank-pop, and large-paste marker behavior without accessing runtime internals.

#### Scenario: Input API exposes advanced command hooks
- **WHEN** an application constructs and uses an `Input`
- **THEN** it can observe or invoke advanced history behavior through documented callbacks/settings on the public `Input` API

#### Scenario: Editor API preserves backward compatibility
- **WHEN** existing editor construction code is compiled
- **THEN** new editing APIs are additive and do not break existing callback signatures or core module compatibility

### Requirement: Public image APIs are stable and dependency-light
The developer API SHALL expose image-related data types and renderer helpers in a way that does not require platform-specific dependencies at call sites.

#### Scenario: Applications can construct image options and helpers
- **WHEN** an application configures image rendering options
- **THEN** it can do so from `scalatui` public types only, without JVM/Native-specific import branches

#### Scenario: Public API does not force terminal backend coupling
- **WHEN** applications run on JVM or Scala Native
- **THEN** image types and markdown/autocomplete models remain usable without depending on concrete terminal backend classes

### Requirement: Public markdown/autocomplete contracts are composable
The markdown and autocomplete contracts SHALL remain composable with existing `Component`, `TUI`, and effect runtimes.

#### Scenario: Provider composition without effect runtime dependencies
- **WHEN** an application bridges a future/callback/file-system source into autocomplete
- **THEN** it can do so through the documented provider contract without adding runtime dependencies to `scala-tui`

#### Scenario: Markdown usage stays within component contract
- **WHEN** an application uses markdown rendering
- **THEN** it does so through the shared component-style output contract and standard width constraints
