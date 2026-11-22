# Language-native developer experience

This follow-up to the completed application-building roadmap (#99) targets idiomatic Scala,
Java and Kotlin application development. The user approved this direction after 0.12.0.
It is an implementation roadmap, not a release announcement or a 1.0 stability commitment.
New APIs must be described as unreleased until their published artifacts are verified.

## Outcome

Build the same small table/filter/async application in three languages without asking Java or
Kotlin callers to construct Scala collections, functions, options or compiler-generated defaults.
Keep one rendering and lifecycle implementation: language adapters must not create independent
scheduling, selection or cleanup semantics.

## Execution order

1. **Consumer contract and friction inventory.** Specify the same immutable row model, stable IDs,
   filtering, selection, scheduled updates, cancellation and empty/error states in each language.
   Record gaps against the existing `SiglyphJvm` facade and shared Scala APIs before adding wrappers.
2. **Scala composition.** Add the smallest typed composition/configuration conveniences demonstrated
   by that application. Preserve direct access to existing components and backend injection. Verify
   shared code on JVM and Scala Native; do not introduce a mandatory effect runtime or DSL.
3. **Java application boundary.** Provide explicit builders/factories, copied Java collections,
   JDK functional interfaces and Java-readable selection/admission results. Cover tables, semantic
   themes, bounded scoped updates and managed testing. Validate errors at the boundary; preserve
   old facade descriptors and document ownership instead of silently owning executors.
4. **Optional Kotlin ergonomics.** Layer receiver-lambda configuration/extensions over that same
   boundary. Define nullability and avoid callback overload ambiguity. Keep Kotlin out of the
   shared core dependency graph. Coroutine adapters are a separate decision, not this milestone.
5. **Consumer acceptance.** Run equivalent behavior scenarios from actual Scala, Java and Kotlin
   consumers. Include standalone Maven/Gradle or Scala CLI instructions appropriate to each language,
   local candidate-artifact verification and explicit post-publication verification commands.
6. **Review and release separately.** Update the API inventory and migration/limitations docs, verify
   existing binary consumers, and review the adapters against measured example friction. Commit,
   PR creation and publication require their own authorization.

## Shared application and acceptance contract

- Typed immutable rows have unique stable IDs. Filtering is application-owned; unchanged IDs retain
  selection according to the existing table contract, including empty and removed-selection cases.
- A text filter and table compose in a usable layout. Keyboard focus/navigation, narrow widths,
  Unicode cells and resize behavior are tested; virtual assertions do not prove emulator pixels.
- A bounded scheduled source updates the model through session-owned application scopes. Show
  Accepted/Full/Closed outcomes explicitly. Accepted admission is not a completion guarantee.
- Stop/restart cannot let stale callbacks update a new session. Cancellation and resource teardown
  preserve failures. Borrowed executors remain caller-owned; callbacks must be finite/nonblocking.
- The three examples implement equivalent semantics, not necessarily identical source structure.
  Tests exercise filtering, selection, updates, cancellation, teardown and useful assertion failures.
- Shared Scala conveniences run on JVM and Native. Java/Kotlin support is JVM-only; a Kotlin DSL
  does not imply Kotlin/Native support.
- Candidate consumer tests resolve staged artifacts rather than source roots. Published tests must
  not claim success for an unreleased version; retain 0.12.0 installation instructions until release.
- New runtime dependencies, hosting/CI expansion and toolchain changes are separate decisions.

## Completion evidence

Implementation checklists and verification evidence belong to the
[archived language-DX implementation](../openspec/changes/archive/2026-11-22-add-language-dx/tasks.md).
The still-deferred publication check is tracked in the [0.13.0 preparation record](release-v0.13.0.md#remaining-release-gates).
A milestone is complete only after its code, examples, tests and documentation pass their checks;
a roadmap document alone does not establish delivery. Full publication verification happens only
after an independently authorized release.

## Deferred

Coroutine/effect-runtime adapters, Windows/browser backends, a reactive framework, new ingestion
infrastructure, full-core ABI guarantees and graduation of every experimental API to stable.
