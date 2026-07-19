---
name: create-project-demo
description: Create verified Siglyph feature demos for releases, including deterministic asciinema casts and optional promo videos, narration, subtitles, and publication text.
license: MIT
compatibility: Requires Mill and asciinema for casts. Optional MP4 production requires agg and ffmpeg.
metadata:
  author: siglyph
  version: "1.0"
---

Create a release-ready Siglyph feature demo from verified behavior. Use this skill when the user asks to demo, showcase, record, or promote a new Siglyph feature or release.

## Principles

- Demonstrate one user-visible outcome at a time.
- Use deterministic scenarios instead of live interactive terminal input.
- Treat generated casts and videos as publishing artifacts, not build-test outputs.
- Keep narration, subtitles, descriptions, and on-screen claims consistent with verified repository behavior.
- Do not claim that an AI system built the project. State the specific assistance it provided.
- Do not publish a generated voice or third-party asset until its license and required attribution are verified.

## Required inputs

Before recording, establish:

1. The feature or release to demonstrate.
2. The target audience and publication channel.
3. The verified user-visible claims to make.
4. Whether the output is a cast only, a narrated video, or both.
5. Whether generated artifacts should remain untracked, be committed, or be uploaded elsewhere.

If a required fact is missing, inspect the implementation, tests, promoted specs, and current user documentation before drafting claims. Do not use old tests or documentation as acceptance criteria.

## Existing Siglyph recording path

Read these files before changing a demo:

```text
asciinemaDemo/src/scalatui/demo/AsciinemaDemo.scala
docs/asciinema-demos.md
scripts/asciinema/record-demo.sh
README.md
```

The current deterministic scenarios are:

| Scenario | Command | Demonstrates |
| --- | --- | --- |
| `agent-prompt` | `mill asciinemaDemo.run agent-prompt` | Prompt composition, slash completion, attachment completion, and tag completion |
| `command-palette` | `mill asciinemaDemo.run command-palette` | Filtering, settings, loader, and progress state |
| `unicode-input` | `mill asciinemaDemo.run unicode-input` | Unicode-safe editing and typed input |

Preview without deliberate pauses:

```bash
SIGLYPH_ASCIINEMA_PAUSE_MS=0 mill asciinemaDemo.run <scenario>
```

Record with deliberate pacing:

```bash
scripts/asciinema/record-demo.sh <scenario>
scripts/asciinema/record-demo.sh all
```

The recording script captures Mill output in `artifacts/asciinema/*.stderr.log` so generated casts contain only the demo.

## Create or update a scenario

1. Read the relevant feature implementation and current demo scenarios.
2. Define a short user story with a visible start state, action, and result.
3. Make state transitions deterministic. Do not record network access, timing races, local filesystem contents, or manual terminal input unless the user explicitly needs them.
4. Use real public APIs and user-visible text.
5. Keep demo-only code isolated in `asciinemaDemo/`.
6. Add or update tests for the feature behavior when the feature itself changes.
7. Preview the scenario with zero pauses before recording it.

A good release clip shows one feature in 10–30 seconds. Split unrelated features into separate clips.

## Produce an optional narrated video

Use this only when the user requests a video rather than a cast.

1. Confirm available tools:

```bash
command -v asciinema
command -v agg
command -v ffmpeg
```

2. Do not install a missing renderer, editor, or TTS dependency without user approval.
3. Render casts with `agg` using a stable theme, font size, frame rate, and terminal size.
4. Use `ffmpeg` to pad terminal footage into a 1920×1080 frame, join clips with short fades, and add a logo-based intro and outro.
5. Keep a title card, each feature segment, and the closing card visually distinct.
6. Verify the final MP4 with `ffprobe` and inspect frames from the intro, every feature segment, and the outro.
7. Keep the source narration beside the video artifact.

Do not use raw terminal casts as a video source without rendering them first.

## Narration and AI disclosure

1. Draft the narration from verified claims only.
2. State AI involvement precisely. For example: “OpenAI GPT-5.6 and Codex assisted specification, implementation exploration, code review, and testing.”
3. Do not say an AI model built the project unless that claim is literally accurate.
4. Review the final spoken delivery. Automatic speech recognition can verify coverage, but it cannot verify naturalness, pronunciation, or brand-name delivery.
5. If using local macOS `say`, select and approve a voice before rendering the final video.
6. If using a third-party TTS model or service, verify its current license, commercial-use terms, voice-consent requirements, and required attribution before generating the final narration.

For Fish Audio specifically, read the current upstream model license before use. Do not assume a hackathon or public release is non-commercial. At the time this skill was created, the upstream Fish Audio materials used the Fish Audio Research License and required separate permission for commercial use plus attribution for covered distribution. Confirm the current terms and obtain the user’s explicit classification before use.

## Subtitles and publication text

1. Generate an English SRT file from the final narration timing.
2. Correct automatic-transcription errors manually against the approved narration source. Brand names such as `Siglyph`, `pi-tui`, `OpenAI`, `GPT-5.6`, and `Codex` must be exact.
3. Validate the SRT with:

```bash
ffprobe -v error -show_entries stream=codec_type:format=format_name -of default=nw=1 <subtitles>.srt
```

4. Write a YouTube description with:
   - A direct one-sentence project summary.
   - A concise list of demonstrated features.
   - Repository and package links.
   - Accurate AI-development disclosure when applicable.
   - Required model or asset attribution.
5. Keep description lines intentional. Do not apply artificial line wrapping.

## Validation and handoff

Before reporting completion:

```bash
asciinema play <cast>
ffprobe -v error -show_entries format=duration,size:stream=codec_name,width,height,sample_rate,channels -of default=nw=1 <video>.mp4
ffprobe -v error -show_entries stream=codec_type:format=format_name -of default=nw=1 <subtitles>.srt
```

Also:

- Watch the final video with sound before publishing.
- Confirm the narration contains every required disclosure and attribution.
- Check that each displayed feature exists in the current release target.
- Report generated files, commands run, outcomes, manual checks still required, and any license or publication obligations.

`artifacts/` is not ignored by this repository. Before committing, ask the user whether generated casts, media, subtitle files, and publication copy should be versioned or kept local.
