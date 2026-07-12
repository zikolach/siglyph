## MODIFIED Requirements

### Requirement: Unicode table generation
The library SHALL use a Scala CLI script to generate committed Unicode display-width data, Unicode 17.0.0 grapheme-segmentation runtime properties, and official Unicode 17.0.0 GraphemeBreakTest-derived test vectors from immutable versioned source URLs.

#### Scenario: Generated Unicode version is recorded
- **WHEN** Unicode tables or grapheme test vectors are generated
- **THEN** each generated output records Unicode version 17.0.0 and the exact immutable versioned source URLs used

#### Scenario: Unicode tables are reproducible
- **WHEN** the Unicode generation script is run repeatedly with the same Unicode 17.0.0 inputs and generator revision
- **THEN** it reproduces every committed runtime and test-data file byte-for-byte

#### Scenario: Generated properties cover segmentation rules
- **WHEN** Unicode 17.0.0 grapheme runtime data is generated
- **THEN** it includes the Grapheme_Cluster_Break, Extended_Pictographic, and Indic_Conjunct_Break properties required for UAX #29 default extended grapheme clusters

#### Scenario: Official fixtures are version matched
- **WHEN** GraphemeBreakTest-derived vectors are generated
- **THEN** the generator uses the immutable Unicode 17.0.0 GraphemeBreakTest source and fails if its version does not match the runtime property inputs
