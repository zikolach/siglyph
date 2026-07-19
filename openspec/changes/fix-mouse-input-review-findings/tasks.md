## 1. Box Layout Retention

- [ ] 1.1 Make `Box.renderFrame` the canonical composition path, retaining child layout trees with normalized padding and accumulated body-row translations while preserving typed render validation and metadata behavior.
- [ ] 1.2 Add focused layout tests for padded direct children, multiple vertical children, nested descendants, normalized negative padding, and unchanged rendered output and metadata geometry.
- [ ] 1.3 Add an end-to-end TUI test proving a mouse-capable child wrapped in `Box` receives the event at its translated local coordinates.

## 2. Committed Layout Publication

- [ ] 2.1 Keep candidate base and overlay layouts local until `renderNow` accepts the resize generation and completes the full, partial, or no-text-difference commit path.
- [ ] 2.2 Add a deterministic routing regression that invalidates a render with a resize, queues mouse ingress before the replacement render, and verifies routing uses the previously committed layout.
- [ ] 2.3 Cover successful no-output commits so changed logical ownership with identical rendered lines still publishes the accepted layout tree.

## 3. Extended Mouse Button Parsing

- [ ] 3.1 Derive press and release button identity by removing supported modifier, motion, and wheel flags before mapping exact primary identities or constructing `MouseButton.Other`.
- [ ] 3.2 Add shared parser regressions for extended press and release identities, including modifier flags, while retaining existing primary-button and wheel behavior.

## 4. Specification and Documentation Cleanup

- [ ] 4.1 Replace the promoted mouse-input specification's generated `Purpose` placeholder with a concise capability description.
- [ ] 4.2 Remove the extra end-of-file blank lines introduced in the promoted mouse-input and terminal-runtime specifications.

## 5. Validation

- [ ] 5.1 Run focused shared-core tests for Box layout, TUI routing, and terminal input parsing on the JVM.
- [ ] 5.2 Run `mill __.test` to verify all JVM and Scala Native modules.
- [ ] 5.3 Run `mill scalafmtCheck`, `mill scalafixCheck`, `openspec validate --all --strict`, and `git diff --check`.
