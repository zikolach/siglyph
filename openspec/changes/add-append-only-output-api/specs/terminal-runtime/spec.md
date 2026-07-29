## ADDED Requirements

### Requirement: Append completions are retained and cleaning-bounded
Every append request that linearizes before the applicable Cleaning cutoff SHALL complete exactly once through the single callback owner. Cleanup SHALL discard every unclaimed append body while retaining only the bounded callback records required before terminal restoration or in the finite post-restoration set.

#### Scenario: Stop discards an unclaimed append body
- **WHEN** stop wins after append admission but before the owner claims that append
- **THEN** its component and payload references are released, no append render or output occurs, and its callback receives `StoppedBeforeClaim` exactly once before cleanup

#### Scenario: Runtime failure discards unclaimed append bodies
- **WHEN** fail-fast progression discards accepted unclaimed append operations
- **THEN** their retained callbacks receive `StoppedBeforeClaim` in accepted FIFO order before terminal restoration

#### Scenario: Stop invalidates a claimed append
- **WHEN** stop wins after append claim but before its synchronized publication boundary
- **THEN** the owner discards the body after active component code returns and invokes `StoppedBeforePublication` exactly once before terminal restoration

#### Scenario: Pending capacity rejection races stop
- **WHEN** an external capacity rejection uses bounded ingress accounting and its serialized callback has not run before stop
- **THEN** stop treats that record as a retained completion rather than ordinary work, promotes it before discarding ordinary ingress, and completes it exactly once without retaining the rejected component or payload

#### Scenario: Retained append callback fails
- **WHEN** one retained append completion callback throws during shutdown
- **THEN** runtime failure is recorded, every remaining retained append and query callback is attempted, and cleanup proceeds

#### Scenario: Append is requested during Cleaning
- **WHEN** append is requested after cleanup commit but before the post-restoration cutoff
- **THEN** its lifecycle-rejection callback joins the finite post-restoration completion set and cannot postpone restoration

#### Scenario: Restoration seals append completions
- **WHEN** restoration completion detaches the post-restoration callback set
- **THEN** later append requests use stopped behavior without extending either finite Cleaning callback set or overlapping an owner callback
