# Follow a log file

[Documentation index](README.md) · [Generated live mode](live-log-explorer.md)

Unreleased; run from a checkout in a real macOS/Linux terminal:

```bash
mill fullscreenJvmDemo.run --follow-file /path/to/application.log
mill fullscreenNativeDemo.nativeLink
./out/fullscreenNativeDemo/nativeLink.dest/out --follow-file /path/to/application.log
```

File mode begins with no synthetic records and reads from byte zero. Ctrl+P pauses reading/delivery; Ctrl+X permanently cancels the source while retaining inspectable rows. Enter applies a filter, arrows browse, Ctrl+G follows the tail, and Esc/Ctrl+C exits. Ctrl+N does not generate records in file mode.

## Bounded policy

- Poll every 250 ms on an explicitly owned worker, never inside a UI callback.
- Read at most 64 KiB of data plus up to 64 bytes of fingerprint per poll; emit at most 256 lines.
- Retain at most 4096 raw bytes before LF per line (including an optional CR). Discard excess until LF, emit the prefix and visibly mark it truncated. Malformed/incomplete UTF-8 in a completed prefix uses replacement characters.
- Hold an incomplete EOF line until LF arrives. Strip a CR immediately before LF. No unterminated line is emitted merely because a poll reaches EOF.
- Keep one immutable batch under backpressure; retry it without reading further. Pausing preserves that batch. The model retains 200 rows by default, with stable IDs and visible eviction counts.
- Render file content through the existing typed output/text boundary; arbitrary file control sequences gain no terminal-control authority.

## File changes and errors

Only local regular files are accepted. Symlinks and special files are rejected at validation. A missing/inaccessible file produces a visible error and is retried; retained rows are preserved. Recovery clears the current error.

Observed identity change, size below the committed offset, or changed initial fingerprint resets reading to zero, discards the partial line and emits a notice. Rotation switches immediately to the current path rather than draining an old renamed descriptor. A post-read identity/size check rejects raced batches before committing their cursor.

This is a polling viewer, not audit-grade exactly-once ingestion. Same-inode truncate/regrow with an identical prefix between polls, adversarial replacement races and arbitrary blocking filesystem behavior cannot be fully detected/preempted. Descriptors close after every poll. Shutdown cancels delivery and joins the daemon producer with a bounded guard; inability to terminate is reported rather than called success.

## Verification

Shared JVM/Native tests cover fragmented UTF-8/CRLF, partial EOF, byte/line limits, oversized lines, malformed bytes, observed truncation/replacement, missing-file recovery, regular-file validation and bounded paused/backpressured delivery. Launcher PTY smoke verifies actual append, rewrite, error/recovery and terminal restoration separately from pixel appearance.
