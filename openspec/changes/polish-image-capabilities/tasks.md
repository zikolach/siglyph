## 1. Capability detection

- [ ] 1.1 Add Warp detection from `TERM_PROGRAM=WarpTerminal`, `WARP_SESSION_ID`, and `WARP_TERMINAL_SESSION_UUID`.
- [ ] 1.2 Preserve tmux and screen checks ahead of Warp image capability decisions.
- [ ] 1.3 Add capability tests for Warp outside multiplexers and Warp inside tmux/screen.

## 2. Cell-size query and parsing

- [ ] 2.1 Add shared terminal cell-dimension model and parser tests for valid and invalid responses.
- [ ] 2.2 Add runtime handling that consumes cell-size replies before focused component input routing.
- [ ] 2.3 Wire valid queried cell dimensions into image sizing while preserving default fallback dimensions.

## 3. Image row and cursor behavior

- [ ] 3.1 Add tests for image row reservation for Kitty and iTerm2 render results.
- [ ] 3.2 Add tests that content following image output appears below reserved rows.
- [ ] 3.3 Add fallback rendering tests for unsupported image protocols with width-safe output.

## 4. Documentation and validation

- [ ] 4.1 Update docs and porting notes for Warp support, cell-size query behavior, and fallback dimensions.
- [ ] 4.2 Run `mill core.test`, `mill image.test`, `mill __.compile`, `mill scalafmtCheck`, `mill scalafixCheck`, and `openspec validate --all --strict`.
