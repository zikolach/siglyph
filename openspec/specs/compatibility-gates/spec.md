# compatibility-gates Specification

## Purpose
Defines the explicitly selected compatibility surface, artifact-based consumer validation and separate local-candidate and published-release gates.

## Requirements

### Requirement: Explicit compatibility scope
The project SHALL identify its baseline-preserved, experimental and internal surfaces, and SHALL not claim full-core or Native binary compatibility from selected JVM checks.

#### Scenario: Experimental API change
- **WHEN** an experimental API changes
- **THEN** migration notes and graduation criteria remain explicit rather than implying 1.0 stability

### Requirement: Artifact-based compatibility evidence
The local gate SHALL compare selected JVM descriptors and execute baseline-compiled consumers against candidate artifacts. Native consumers SHALL be rebuilt against their artifact set.

#### Scenario: Removed method
- **WHEN** a selected method disappears from a candidate fixture
- **THEN** the descriptor gate fails before the candidate is accepted

### Requirement: Separate candidate and release validation
Candidate validation SHALL use a unique local/staged version. Published smoke SHALL resolve actual published artifacts separately and SHALL not publish or tag a release itself.

#### Scenario: Candidate checks pass
- **WHEN** local compatibility checks succeed
- **THEN** publication still requires the explicit release-readiness process and authorization
