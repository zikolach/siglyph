## Context

The project already has dependency-free Kitty/iTerm2 protocol helpers, terminal capability detection, optional image helpers, and bounded cell-size calculation. Upstream `pi-tui` treats Warp as Kitty-capable and queries terminal cell dimensions for image sizing. The Scala implementation needs this behavior while keeping capability detection deterministic and safe under multiplexers such as tmux and screen.

## Goals / Non-Goals

**Goals:**

- Detect Warp as Kitty-image capable when not inside a known unsupported multiplexer.
- Keep tmux and screen constraints explicit and higher priority than terminal image hints.
- Add terminal cell-size query response handling for image sizing decisions.
- Consume cell-size protocol replies before component input routing.
- Harden image row reservation and cursor behavior around terminal-owned image output.

**Non-Goals:**

- No background color or color-scheme query work.
- No scaling or transcoding dependency.
- No full terminal media protocol abstraction beyond current Kitty/iTerm2 scope.
- No unsafe live terminal probing in tests.

## Decisions

### 1. Treat Warp as Kitty-capable outside unsupported multiplexers

Warp reports Kitty graphics protocol support upstream. Detection should use `TERM_PROGRAM=WarpTerminal`, `WARP_SESSION_ID`, or `WARP_TERMINAL_SESSION_UUID` as positive hints only after tmux/screen checks have ruled out unsupported forwarding behavior.

Alternatives considered:

- Keep Warp unsupported: rejected because it leaves a known upstream parity gap.
- Mark Warp capable even inside tmux: rejected because multiplexer image forwarding is not guaranteed.

### 2. Keep cell-size query in image capability work

Cell-size data exists to improve image sizing. The query and parser live in terminal-runtime support, but the user-visible behavior belongs to image rendering and capability decisions.

Alternatives considered:

- Include cell-size query with color queries: rejected because it serves image layout, not application color integration.

### 3. Use fallback cell dimensions when query data is absent

Existing deterministic default cell dimensions remain the fallback. A valid terminal response updates image sizing. Missing, invalid, or timed-out responses do not block rendering.

Alternatives considered:

- Require a live cell-size response before image rendering: rejected because many terminals do not answer and image fallback must remain usable.

## Risks / Trade-offs

- Terminal capability detection can overclaim support → multiplexer checks must run before Warp and Kitty-positive checks.
- Cell-size replies can interleave with typed input → runtime must consume protocol replies before component routing.
- Different terminals use different pixel reports → parser must accept only documented valid forms and ignore invalid payloads.
- Image row reservation can desynchronize cursor position → add virtual-terminal tests around image output rows and following text.
