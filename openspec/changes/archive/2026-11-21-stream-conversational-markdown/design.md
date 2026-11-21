## Design

Use a new opt-in StreamingMarkdown component wrapping the existing Markdown component and BasicMarkdownRenderer. An owned parser adapter retains completed baseline blocks. The public MarkdownParser interface and ordinary Markdown rendering are unchanged. Custom parsers receive the original entire document with no projection or incremental assumptions.

Admission is bounded by a configurable UTF-16 source limit (default 1 Mi units); reject a whole chunk atomically on overflow or after finish. Counters report parser calls, UTF-16 parser input units, reused blocks and fallback parses. They are work counters, not timing or heap measurements.

Only blank-line-terminated baseline prefixes are eligible for commitment. Fenced regions require a complete closing line. Encountering list, quote or indented-code syntax prevents further commitment in that suffix, because those constructs can absorb blank lines and following same-kind blocks. Their suffix is reparsed on every source change. Tables can commit only after a blank line. CRLF is normalized before boundary detection; trailing CR is kept unstable. Reset clears checkpoints; finish parses literal final source. Rendering invalidation preserves source checkpoints.

The live baseline tail projects simple unfinished links to their visible label and closes unmatched emphasis/code-span delimiters provisionally. Fence bodies are never inline-projected. Finalization removes projection. This is a practical baseline grammar, not CommonMark; provisional output may reflow. Custom parsers always use whole-document fallback.

Full source concatenation/normalization, active-tail scanning, block-vector assembly and complete-document rendering still cost work proportional to retained data. No universal O(delta), bounded total stream, or exact heap accounting claim is made. Source admission bounds retained source; existing renderer LRU budgets still apply.

The viewer keeps one ScrollView and child, uses followEnd=true, never jumps on append, and invalidates only selection/search mapping. Selection offsets are stable only when preceding rendered text stays stable; active-tail reflow and width changes are not semantic source anchors. Transport and provider lifecycles remain application-owned.
