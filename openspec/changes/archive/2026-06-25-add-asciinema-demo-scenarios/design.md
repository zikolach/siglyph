## Context

The existing demos are useful for manual verification, but raw recordings of the full interactive app are hard to understand in asciinema playback. The best recording path is a small set of purpose-built scenarios that tell a short story, use visible scripted input, and avoid build-tool output.

Current local experiments produced valid `.cast` files, but they did not create a strong visual demo. Future recordings need deterministic terminal output that works in headless asciinema recording and stays readable after upload.

## Goals / Non-Goals

**Goals:**

- Add repeatable recording scenarios for README and release demos.
- Keep each scenario short, focused, and visually clear.
- Prefer real siglyph components for rendered frames instead of static terminal screenshots.
- Make local recording and playback commands documented and reproducible.
- Keep asciinema optional for contributors who only build or test the library.

**Non-Goals:**

- No public API changes.
- No runtime dependency on asciinema, expect, Node.js, npm, `agg`, `svg-term`, or browser tooling.
- No CI requirement to record or upload casts.
- No automatic upload to asciinema.org.
- No generated GIF or SVG rendering pipeline in this change.

## Decisions

### 1. Use purpose-built recording scenarios instead of recording the full interactive demo

Create small scenarios that render scripted frames with captions and visible keystroke progression. This is more readable than recording the whole interactive app, where focus changes and autocomplete overlays can be too subtle.

Alternatives considered:

- Record `mill interactiveJvmDemo.run` directly: rejected because raw playback is visually noisy and hard to follow.
- Record only existing non-interactive examples: rejected because they show output but do not tell a compelling feature story.

### 2. Keep generated casts outside required source and build outputs

Recording scripts should write `.cast` files to a local artifact directory such as `artifacts/asciinema`. The repository can keep scripts and documentation, while generated recordings remain publish-time artifacts unless a maintainer explicitly decides to commit a specific cast.

Alternatives considered:

- Commit every generated `.cast`: rejected because recordings are binary-like text artifacts with timestamps and can churn often.
- Generate casts during CI: rejected because asciinema is not a project build dependency and CI terminal behavior is not the feature under test.

### 3. Provide three first-class scenarios

Implement three scenarios first:

1. Agent prompt composer: editor text, slash commands, file attachment completion, and tag completion.
2. Command palette and settings: fuzzy filtering, selected action, loader/progress state, and settings value changes.
3. Unicode and typed input proof: CJK, emoji, combining marks, paste summary, Insert, arrow keys, and Ctrl-key display.

This set balances marketing clarity with technical proof.

Alternatives considered:

- Add image protocol recording first: rejected because image protocol replay can vary across asciinema viewers.
- Add Native recording first: rejected because the JVM path is enough to demonstrate the shared UI behavior and is simpler to run locally.

### 4. Document upload links, not inline playback

README usage should document asciinema SVG preview links in this form:

```markdown
[![asciicast](https://asciinema.org/a/<id>.svg)](https://asciinema.org/a/<id>)
```

GitHub README pages do not run the asciinema JavaScript player inline, so the clickable preview is the reliable README format.

## Risks / Trade-offs

- Scenario scripts can drift from real component behavior → keep them backed by siglyph rendering code where practical and document the command used to regenerate each cast.
- Asciinema headless mode may not behave like a real TTY → avoid relying on live terminal input capture for the hero scenarios.
- Generated casts contain timing and environment metadata → keep them out of required validation and source review by default.
- Highly polished scenarios can become misleading → keep captions and output tied to implemented features only.
