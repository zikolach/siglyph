## 1. Renderer capability extraction

- [x] Extract AppendPublisher and ResizeRecoveryRenderer; implement both only on NormalScreenPolicy.
- [x] Introduce renderer-neutral PreparedResizeRecovery and use it in policy render signatures.
- [x] Remove unsupported fullscreen preparation/publishing stubs.
- [x] Add tests covering supported capabilities in normal, width-only alternate, and fullscreen modes.
- [x] Synchronize lifecycle-failure test startup after CI exposed a pre-existing runner-admission race; reproduce the exact failure on unchanged base and retain all assertions (four fixture tests pass on JVM and Native).

## 2. Parent runtime and component integration

- [x] Remove the TUI companion recovery type and dispatch preparation/publishing through capabilities after query-worker integration.
- [x] Add read-only RenderServices and migrate the image consumer without changing public APIs.

## 3. Verification

- [x] Run capability, render-service, image and existing renderer suites after TUI integration; full JVM/Native tests and six dedicated PTY lifecycle tests passed. Initial intermediate compilation errors were resolved by runtime dispatch migration.
- [x] Validate OpenSpec changes strictly (`openspec validate narrow-render-capabilities --strict`).

Full-tree formatting/lint and all-module compilation pass. All five new changes validate strictly; global validation retains five pre-existing overlong-requirement warnings.
