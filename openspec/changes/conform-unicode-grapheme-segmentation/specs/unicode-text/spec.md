## ADDED Requirements

### Requirement: Unicode 17.0.0 extended grapheme segmentation
The shared core SHALL segment text according to Unicode 17.0.0 UAX #29 default extended grapheme clusters and SHALL return a lossless ordered partition of the input without a runtime dependency.

#### Scenario: Official conformance cases
- **WHEN** each case from the official Unicode 17.0.0 GraphemeBreakTest is segmented
- **THEN** every expected boundary and non-boundary matches the fixture and concatenating the resulting clusters reproduces the exact input

#### Scenario: Hangul clusters
- **WHEN** text contains Unicode 17.0.0 Hangul L, V, T, LV, or LVT sequences
- **THEN** boundaries follow the UAX #29 Hangul syllable rules

#### Scenario: Indic conjunct clusters
- **WHEN** text contains Unicode 17.0.0 Indic_Conjunct_Break consonant, linker, and extend sequences
- **THEN** boundaries follow the UAX #29 Indic conjunct rule

#### Scenario: Combining and spacing sequences
- **WHEN** text contains prepend characters, combining extensions, ZWJ, or spacing marks
- **THEN** boundaries follow the corresponding Unicode 17.0.0 UAX #29 default extended grapheme rules

#### Scenario: Extended pictographic ZWJ sequences
- **WHEN** an extended pictographic sequence satisfies Unicode 17.0.0 UAX #29 rule GB11
- **THEN** the sequence is emitted as one extended grapheme cluster

#### Scenario: Regional indicators
- **WHEN** text contains a run of regional-indicator code points
- **THEN** boundaries pair regional indicators according to Unicode 17.0.0 UAX #29 default extended grapheme rules

### Requirement: Whole-string and incremental segmentation equivalence
The shared core SHALL use one package-private segmentation engine for whole-string and incremental Unicode 17.0.0 UAX #29 default extended grapheme segmentation, and incremental state SHALL remain bounded independently of input or retained application content.

#### Scenario: Every official case at every single split
- **WHEN** each official Unicode 17.0.0 GraphemeBreakTest input is divided at every single code-point split and fed incrementally
- **THEN** each incremental partition equals the whole-string partition and remains lossless

#### Scenario: One-code-point chunks
- **WHEN** each official Unicode 17.0.0 GraphemeBreakTest input is fed one code point at a time
- **THEN** incremental segmentation produces exactly the whole-string boundaries

#### Scenario: Reset clears prior context
- **WHEN** an incremental segmenter processes a prefix, is reset, and then receives another input
- **THEN** the second input is segmented exactly as a fresh whole-string segmentation with no state from the prefix

#### Scenario: Fragmented UTF-8 input
- **WHEN** UTF-8 bytes for official and focused grapheme cases are divided at every byte boundary and decoded incrementally before segmentation
- **THEN** the resulting cluster partition equals segmentation of the decoded whole string

#### Scenario: State remains bounded
- **WHEN** arbitrarily long text or streamed paste content is processed incrementally
- **THEN** segmentation state does not retain the processed content and its storage does not grow with content length

#### Scenario: Application content remains unlimited
- **WHEN** Input or Editor retains text processed by the segmenter
- **THEN** segmentation imposes no fixed application content limit and does not truncate or drop text

### Requirement: Deterministic versioned Unicode segmentation data
Runtime grapheme properties and official test vectors SHALL be generated deterministically from immutable versioned Unicode 17.0.0 sources, committed to the repository, and usable by both JVM and Scala Native.

#### Scenario: Runtime data records source identity
- **WHEN** generated runtime grapheme property data is inspected
- **THEN** it records Unicode version 17.0.0 and the exact immutable versioned source URLs used

#### Scenario: Test data records source identity
- **WHEN** generated GraphemeBreakTest-derived vectors are inspected
- **THEN** they record Unicode version 17.0.0 and the exact immutable versioned GraphemeBreakTest source URL used

#### Scenario: Generation is byte-for-byte deterministic
- **WHEN** generation is run twice with the same Unicode 17.0.0 inputs and generator revision
- **THEN** each generated runtime and test-data file is byte-for-byte identical across runs

#### Scenario: Source versions must match
- **WHEN** a generator input or declared source version does not match Unicode 17.0.0
- **THEN** generation fails instead of producing mixed-version data

#### Scenario: JVM executes official fixtures
- **WHEN** the JVM shared-core test target runs
- **THEN** it executes every committed official Unicode 17.0.0 GraphemeBreakTest-derived vector

#### Scenario: Native executes official fixtures
- **WHEN** the Scala Native shared-core test target runs
- **THEN** it executes every committed official Unicode 17.0.0 GraphemeBreakTest-derived vector
