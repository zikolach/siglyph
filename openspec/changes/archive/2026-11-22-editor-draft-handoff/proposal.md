# Editor draft handoff

## Why

Applications such as Sigma need to validate logical submission text before mutating an editor and retain queued drafts without losing compact paste payloads or cursor positions. Copying displayed marker text is not a draft checkpoint.

## What Changes

- Add read-only `Editor.submittedText` and opaque reusable `EditorDraftSnapshot` values via `snapshotDraft()` / `restoreDraft(snapshot)`.
- Specify committed-buffer reads during streamed paste and safe restoration invalidation rather than a full editor checkpoint.
- Cover typed paste, validation, queued edits, Unicode, history and restoration on JVM and Native.

## Capabilities

### New Capabilities
- `editor-draft-handoff`: Nonmutating submission projection and detached draft restoration.

### Modified Capabilities

None.

## Impact

Shared core Editor API, tests, API inventory and documentation. Additive unreleased API; no publication or Sigma dependency changes.
