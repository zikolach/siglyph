## Why

`coreNative/src` and `interactiveDemoNative/src` are symlinks to shared JVM source trees, which makes IDEs, file browsers, searches, diagnostics, and human review look like there are duplicate implementations. Mill can model shared source roots directly, so the build should express source sharing in `build.mill` instead of through filesystem symlinks.

## What Changes

- Remove native module source symlinks:
  - `coreNative/src -> ../core/src`
  - `interactiveDemoNative/src -> ../interactiveDemo/src`
- Configure the Scala Native modules in `build.mill` to compile the shared source roots directly.
- Keep the current module graph and public APIs unchanged.
- Keep `core` and `interactiveDemo` as the canonical shared source directories.
- Update project documentation/agent notes if they currently describe the symlink mirror layout.
- Validate JVM and Native builds continue to compile without adding runtime dependencies.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `developer-api`: Clarify that shared JVM/Native source reuse SHALL be modeled through Mill source configuration rather than symlinked source directories.

## Impact

- Affects `build.mill`, repository layout, and documentation.
- Does not change application APIs, component behavior, terminal behavior, or runtime dependencies.
- Improves maintainability and reduces confusion for contributors and tools.
