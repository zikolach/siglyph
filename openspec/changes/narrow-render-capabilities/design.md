## Context

NormalScreenPolicy implements both normal and width-only alternate-buffer rendering. FullscreenViewportPolicy implements fixed-height alternate rendering and cannot append transcripts or prepare normal resize recovery. These distinctions should be expressed by capabilities rather than throwing stubs.

## Goals / Non-Goals

Goals: narrow internal dependencies, preserve lifecycle and terminal byte streams, and retain public source compatibility.

Non-goals: new rendering modes, public renderer APIs, output changes, or dependencies.

## Decisions

- Keep shared lifecycle, frame preparation, and rendering in RendererPolicy.
- Add internal AppendPublisher and ResizeRecoveryRenderer traits. NormalScreenPolicy implements both; fullscreen implements neither.
- Keep the common render signature with optional renderer-neutral PreparedResizeRecovery data. Runtime capability dispatch ensures fullscreen never receives recovery data; fullscreen does not advertise unsupported operations.
- Move PreparedResizeRecovery out of the TUI companion, retaining its lines, maxRows, and generation fields unchanged. Scheduling and generation checks remain runtime-owned.
- Parent integration updates TUI dispatch after concurrent query-coordinator work completes. Existing public rejection guards remain unchanged.
- Parent integration introduces a read-only RenderServices interface and migrates the image consumer to that interface. Mutation and scheduling stay in TUI; public entry points remain compatible.

## Risks / Trade-offs

Optional recovery remains present in the shared render signature, but capability dispatch controls preparation. Temporary compile errors are expected between the renderer extraction and parent TUI integration; they are not accepted as final verification.

## Migration Plan

Land internal capabilities and mode coverage, integrate TUI dispatch and read-only rendering services, then run existing renderer suites and OpenSpec validation. No persistent-state migration is required.
