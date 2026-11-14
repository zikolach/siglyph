## Context

Priority 5 of the pinned pi-tui review identifies environment detection and optional OSC 7501 status. Existing TUI admission/drain/output ownership and retryable backend restoration must remain intact.

## Goals / Non-Goals

Goals: optional source-compatible capability, positive negotiation, bounded encoding, no unsupported reports, portable lifecycle cleanup. Non-goals: job trees, background timers, partial graphics clipping, changing OSC 9;4.

## Decisions

A separate optional mixin owns a deterministic shared ProgramStatusSession. TUI automatically queries capable backends; valid raw replies enter typed internal work and execute only in the existing drain. OSC correlation remains enabled without an outstanding color query so late status responses cannot leak to application input. Arrival timestamps, rather than drain time, govern handshake acceptance. Messages scan only a bounded UTF-8 prefix and are base64 encoded. State and reason use typed enums; invalid app names are omitted. Reports mark cleanup before writing, so partial failures remain recoverable. Both backend stop and TUI restoration reset; successful resets are idempotent. TUI reset failure retains the backend restoration stage for explicit retry.

Herdr and Orca are evaluated before leaked Kitty hints, while multiplexers remain earlier still. No capability override behavior changes.

## Risks / Trade-offs

There is no protocol request identifier; unsolicited valid echoes during a pending query cannot be distinguished from responses. No response means unsupported; timeout is evaluated on arrival and reports remain disabled without a positive reply. A broken output sink cannot guarantee clear delivery. Runtime tests use deterministic fakes rather than physical emulator certification.

## Migration Plan

Existing Terminal implementors require no changes. Supporting interactive backends opt in; applications may add typed status calls alongside existing progress calls.
