# Editor navigation and conversational composers

**Available since 0.13.0; APIs remain experimental.** The editor behavior is included in published artifacts; the demo commands below require a source checkout.

The shared `Editor` uses rendered visual rows for Up/Down, including soft wraps. The first arrow captures a preferred **display-cell** column, not a UTF-16 or grapheme index. Short rows clamp without losing that preference. Wide glyphs floor to their leading boundary; combining clusters remain whole. A full wrapped row's end belongs to the next row, so movement clamps before it. Sanitized control expansions can share a source boundary across rows: rows without an independent logical cursor position are skipped. Source text is never rewritten by navigation.

Render at your component width before sending input. Before the first render the fallback width is one cell. Resizing resets the preference to the newly projected cursor column. Explicit cursor placement, horizontal/word/Home/End commands, successful jumps, page/wheel navigation, edits, committed paste, undo, history transitions, submit and autocomplete completion reset it too. Unchanged-width rendering and vertical boundary no-ops preserve it. Active autocomplete still receives arrows first.

## History and drafts

Applications explicitly call `addToHistory` (usually in `onSubmit`); submission does not automatically add history or clear text. Entries are trimmed, consecutive duplicates and blank entries are ignored without changing browsing or preferred column, and at most 100 are retained. Actual history additions end browsing to avoid shifting an active history index.

- Up enters history from an empty editor, or deliberately from display column zero on the first visual row of a nonempty draft. Up elsewhere in that row does not replace the draft.
- Recalled entries start at logical `(0, 0)`. While browsing, Up at the first visual row recalls older entries; Down at the last visual row recalls newer entries. Other arrows move within the recalled text, including wrapped single-line entries.
- Down past newest restores the original draft's exact text and logical cursor, including private large-paste marker metadata. Capture happens once, not each time an entry changes.
- Entry creates one undo snapshot; browsing and restoration create none. Text changes invoke `onChange`; cursor-only restoration requests rendering without a text callback.
- Edits, applied undo, `setText`, `setCursor`, marker expansion, autocomplete completion, submit and actual history additions end browsing and discard the retained draft. Ordinary cursor movement does not. An edited recalled entry becomes the current draft; it does not alter stored history.
- Empty history and exhausted bounds do not change text, cursor, callbacks or undo. A non-paste input still commits an unfinished paste before handling that input.

## Runnable core-only composer

From a source checkout:

```bash
mill demo.runMain scalatui.demo.ConversationalComposer
```

The deterministic [example](../demo/src/scalatui/demo/ConversationalComposer.scala) submits a local echo, edits a wrapped Unicode draft, recalls history, and asserts exact restoration. It uses the actual shared core `Editor`, typed input and submission callback; no TTY, network, chat service or runtime dependency is required. Its assertions and printed transcript provide a small executable walkthrough, not a real-terminal appearance test. Queueing, busy-state admission, message execution and transcript persistence remain application-owned.

## Real-terminal composer

The same core editor also has a small shared interactive local-echo UI, selected through the existing JVM/Native demo launchers:

```bash
mill interactiveJvmDemo.run --composer
mill interactiveNativeDemo.nativeLink
out/interactiveNativeDemo/nativeLink.dest/out --composer
```

Enter sends and clears the composer; Shift+Enter adds a newline; Escape/Ctrl+C exits. Send one message, type a multiline Unicode draft, resize until it wraps, and navigate with Up/Down. At the draft's first logical line use Home then Up to recall history. Down from the recalled entry's last visual row restores the draft. The UI retains only the latest local exchange; no transport, queue or chat infrastructure is involved. This interactive check is separate from the deterministic walkthrough above; compilation and virtual checks do not establish emulator appearance.

## Deferred snapshots

This milestone adds no public editor snapshot API. Internal history restoration reuses `EditorBuffer.Snapshot`, including paste registry and counter. A future full-editor snapshot contract must define undo-stack and kill-ring ownership, paste registry lifetimes and identifier collision safety, pending paste transactions, history and preferred-column state, autocomplete cancellation, and callback/render ordering. Saving only `text` and `cursor` is not a full undo/paste-registry snapshot.
