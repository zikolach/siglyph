## Context

The shared `core` and `interactiveDemo` source trees are currently reused by Scala Native modules through filesystem symlinks:

- `coreNative/src -> ../core/src`
- `interactiveDemoNative/src -> ../interactiveDemo/src`

This preserves shared implementations but makes the repository appear to contain duplicate source files. It also pushes source-sharing knowledge into the filesystem instead of the Mill build, where module structure is already defined.

## Goals / Non-Goals

**Goals:**

- Remove symlinked source directories for Native mirror modules.
- Configure `coreNative` to compile the canonical `core/src` source tree directly.
- Configure `interactiveDemoNative` to compile the canonical `interactiveDemo/src` source tree directly.
- Preserve the existing module graph, public APIs, package names, and validation targets.
- Keep JVM and Native source sharing explicit in `build.mill`.
- Update docs/agent notes that mention Native source symlink mirrors.

**Non-Goals:**

- No component, editor, terminal, or runtime behavior changes.
- No new modules or package namespaces.
- No runtime dependencies.
- No refactor of JVM/Native terminal backend implementations.
- No migration to a different build tool or cross-project abstraction beyond Mill source-root configuration.

## Decisions

### Decision: Keep canonical shared sources under the JVM-named modules

`core/src` and `interactiveDemo/src` remain the canonical shared source directories. Native modules point to those directories through Mill configuration.

Rationale: This avoids moving many source files and preserves existing imports, package names, docs, and mental model: `core` contains shared core APIs; `terminalNative` contains Native-specific backend code.

Alternative considered: Create new top-level shared directories such as `coreShared/src` and `interactiveDemoShared/src`. This would make sharing explicit in paths, but it creates more churn and would require updating documentation and possibly Mill module naming beyond the current cleanup goal.

### Decision: Express shared source roots in Mill, not via symlinks

Override or configure Native module source roots so `coreNative` and `interactiveDemoNative` compile the existing shared directories directly.

Rationale: Mill is the authoritative place for module structure. Filesystem symlinks obscure that structure for IDEs and humans.

Alternative considered: Keep symlinks and document them more clearly. This avoids build changes but preserves the source of confusion that prompted the change.

### Decision: Validate both shared and Native build paths

Run JVM compile/tests and Native linking after the source-root change.

Rationale: Source-root changes can affect incremental compilation, resource discovery, and Native module path assumptions even when source code is unchanged.

## Risks / Trade-offs

- **Risk: Mill source-root override syntax is wrong or version-sensitive** → Verify with `mill __.compile`, `mill core.test`, and `mill interactiveNativeDemo.nativeLink`.
- **Risk: IDE import behaves differently after removing symlinks** → Prefer explicit Mill configuration; document the source sharing model for contributors.
- **Risk: Scripts or docs assume `coreNative/src` exists** → Search the repository and update references.
- **Risk: Cleanup hides actual platform-specific duplication still present in terminal backends** → Keep this change scoped to shared source roots; consider a later backend-runtime refactor if needed.
