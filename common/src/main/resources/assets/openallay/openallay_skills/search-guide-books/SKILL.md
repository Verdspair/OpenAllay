---
name: search-guide-books
description: Use when a player needs entries from Patchouli or another indexed in-game guide book.
allowed-tools: "openallay:run_javascript"
---
Resolve useful exact item/block/effect IDs, then search `mc.knowledge` by those
IDs, localized names, title, body, namespace, and mechanic terms. Rank
candidate records before opening complete bodies; do not treat a search snippet
as the complete guide entry.

Enumerate only matches within the captured evidence scope. Preserve sourceId,
documentId, structureRef, provenance, and evidence. Treat missing, malformed,
config-gated, and unsupported entries as unavailable; never reconstruct their
contents from guesses. Trusted structure data may be present under
`mc.extensions`; use it only when its explicit reference matches the document.
