# 0.14.0 release preparation

**Prepared 2026-11-22 (UTC). Publication authorized, pending artifact verification.**
This is a **MINOR feature release**, not a patch: it adds public Editor draft APIs.
Latest verified publication and primary installation examples remain **0.13.0**.
No tag, remote publication or verified 0.14.0 availability is implied by this record.

## Scope and compatibility

- Experimental `Editor.submittedText` projects committed logical text without mutating the editor,
  moving the cursor, invoking callbacks, consuming pending paste or submitting. Half-open paste is excluded.
- Opaque reusable `EditorDraftSnapshot`, `snapshotDraft()` and `restoreDraft(...)` preserve committed
  displayed text, compact-paste payloads/identities and exact logical cursor across JVM/Native editors.
  Restore discards pending paste, cancels autocomplete and resets editing/navigation session state;
  it is not a full checkpoint of undo/history stacks, callbacks, TUI state or asynchronous work.
- Compact-paste expansion now preserves dollar signs, backslashes and marker-looking payload text in a
  single nonrecursive pass. New IDs avoid existing literal references; oversized literal IDs do not crash.
- **Legacy active-marker alias limitation:** literal text spelling an already-active marker can still
  resolve to that marker's payload. Arbitrary marker provenance is not tracked. Copying displayed text
  to a fresh editor does not copy payload identity; use an opaque snapshot for lossless draft handoff.
- Scala **3.10.1**, JDK **21**, Scala Native **0.5.12**, Mill **1.3.1** and Unicode **17.0.0** are unchanged.
  Scala source consumers need a compatible compiler; Native consumers must rebuild. No new runtime dependency.
  Selected ABI/consumer checks are not a whole-core stability or Native binary-swap guarantee.

See [draft handoff](input-and-terminal.md#draft-handoff-unreleased), [editor interactions](editor-interactions.md),
[API policy](api-stability.md) and the pinned [pi-tui matrix](pi-tui-compatibility.md). No new parity claim is made.

## Local candidate evidence

Candidate: `0.14.0-prep-20261122212205`; repository: `file://$HOME/.m2/repository`.
The candidate includes the working tree's new Editor draft feature files, not just the pre-feature HEAD.
Local receipts and the standalone artifact-only `DraftConsumer.scala` are under `.empryo/release-0.14.0/`.
These are local evidence, not published assets or remote checksum/signature verification.
Compatibility policy remains at published baseline **0.11.0**, baseline Scala **3.9.0**, candidate Scala
**3.10.1**, Kotlin **2.4.21**, JDK **21** and Native **0.5.12**.

Commands (repository root, macOS arm64):

```bash
export SIGLYPH_VERSION=0.14.0-prep-20261122212205
mill --no-server -j 1 core.publishM2Local + terminalJvm.publishM2Local + markdown.publishM2Local + image.publishM2Local + extras.publishM2Local + coreNative.publishM2Local + terminalNative.publishM2Local + markdownNative.publishM2Local + imageNative.publishM2Local + extrasNative.publishM2Local
python3 scripts/check-local-release.py --version "$SIGLYPH_VERSION"
python3 scripts/check-compatibility.py --candidate "$SIGLYPH_VERSION" --repository "file://$HOME/.m2/repository"
python3 scripts/check-language-dx.py --version "$SIGLYPH_VERSION" --repository "file://$HOME/.m2/repository" --native
scala-cli run .empryo/release-0.14.0/DraftConsumer.scala --server=false --scala 3.10.1 --jvm 21 --repository "file://$HOME/.m2/repository" --dependency "io.github.zikolach::siglyph-core:$SIGLYPH_VERSION"
scala-cli run .empryo/release-0.14.0/DraftConsumer.scala --server=false --scala 3.10.1 --jvm 21 --native --native-version 0.5.12 --repository "file://$HOME/.m2/repository" --dependency "io.github.zikolach::siglyph-core::$SIGLYPH_VERSION"
python3 scripts/check-docs.py --compile
python3 -m unittest discover -s scripts -p 'test_check_*.py'
python3 scripts/check-compatibility.py --inventory-only
mill --no-server -j 1 quality
openspec validate --all --strict
```

- All ten modules staged successfully; **40 POM/binary/source/Javadoc payloads passed** coordinate,
  internal dependency-version, nonempty ZIP and Native IR checks. SHA-256 and sizes are recorded in
  `payloads.json`; these checks do not verify remote checksums or signature trust.
- **Selected JVM ABI passed** against 0.11.0. Baseline Scala 3.9.0/Java/Kotlin binaries ran against the
  candidate; Scala 3.10.1 JVM/Native, Java and Kotlin source consumers passed. Native was linked/run,
  not skipped (`compatibility.log`).
- **Language-DX consumers passed** on Java/Kotlin JVM and Scala JVM/Native; interactive Scala launcher
  compiled (`language-dx.log`, `published=False; native=True`).
- **New Editor API artifact consumers passed on JVM and Native** (`draft-jvm.log`, `draft-native.log`).
  Actual typed chunked Unicode paste checks cover literal dollar/backslash/marker-looking payloads,
  committed-only reads during pending paste, callback/cursor nonmutation, opaque restore/reuse across
  editors, fresh paste after restore, literal-ID collision avoidance and reset undo. Only staged core
  dependencies are used, not workspace library source roots.
- **Documentation passed:** 59 Markdown files, published version 0.13.0, three marked recipes, plus
  published JVM/Native quickstarts and guide compilation (`docs.log`).
- **29 offline tooling tests and API inventory passed** (`tooling.log`, `inventory.log`). Mocked output
  in tooling tests is not artifact-consumer evidence.
- **Strict OpenSpec validation passed: 31 specs, zero failures** (`openspec.log`); no change was
  archived by release preparation.
- **Fresh quality passed**, exit 0 (`quality-passed.log`). Earlier formatting failures in the ignored
  artifact consumer and `Editor.text` Scaladoc were corrected; the latter is comment-only and changes
  no runtime behavior. Failed-run logs are retained for traceability.

Full JVM core and focused Native tests were reported green by the implementation owner; this preparation
does not claim a fresh full runtime suite. Logs retain compiler/tool warnings without suppression.

## Remaining release gates

- **Sigma isolated candidate integration is pending**; record success only after its owner confirms it.
  Do not publish before this gate completes.
- Review the preparation diff, merge the authorized PR with green CI, then tag the exact verified merged tree.
- After authorized publication, verify ten remote coordinates/forty payloads, GitHub release assets and
  Central-only compatibility/new API consumers. Distinguish signature presence from signer-trust verification.
- Only after remote verification update `publishedVersion`, clear `preparedVersion` and advance primary examples.

Real-terminal visual/emulator testing is **not verified** here. Deterministic tests, artifact consumers and
any separately reported PTY smoke do not prove emulator pixels or cross-terminal appearance.
