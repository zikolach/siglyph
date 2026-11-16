## Why
Finish the file-source part of #93 without conflating generated streaming with actual I/O. Keep disk work off the serialized UI path and bound both reading and delivery.

## What Changes
- Add --follow-file PATH with bounded regular-file polling and UTF-8 line decoding.
- Define partial EOF, long-line truncation, observed rotation/copy-truncate and error/recovery policy.
- Keep one pending batch under UI backpressure; stop reading while paused or awaiting delivery.
- Add shared tests and document polling limitations. Semantic styling integration follows #97.

## Capabilities
### New Capabilities
- `file-log-following`: bounded application-owned file ingestion.
### Modified Capabilities

## Impact
Demo source/model/launchers and docs only. No new dependency or core I/O API. No audit-grade exactly-once guarantee or network filesystem timeout is claimed.
