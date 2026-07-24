# JavaScript Modules and Complete Real-Client E2E Design

**Date:** 2026-07-24
**Status:** Accepted for implementation
**Decision:** SKMB-2026-07-24-027
**Branch:** `feat/js-agent-runtime`

## Objective

Finish the direct-Rhino architecture as one coherent Agent surface:

- data comes from lazy read-only Java host objects;
- reusable domain algorithms come from bundled JavaScript modules taught by
  progressively loaded Skills;
- the model performs a complete batch analysis in one `run_javascript` call;
- real-client acceptance uses the player's configured model and retains the
  complete provider-neutral Agent trace;
- successful history persistence is invisible to the transcript.

## Considered approaches

### Merge the old VFS main line and adapt Rhino around it

This preserves more recent Git ancestry but retains two competing resource
models, two prompt vocabularies, and two retrieval catalogs. The designer
explicitly rejected this implementation. It is archived rather than merged.

### Keep one Tool per domain operation

This makes every reusable operation discoverable as a Tool, but it grows the
schema sent on every model turn and forces repeated model/Tool round trips.
Craftability demonstrates the problem: it is a reusable transformation over
data already visible to Rhino.

### One analytical Tool plus bundled modules and Skills

This is selected. `run_javascript` is the only general analytical Tool.
Versioned bundled modules provide reviewed reusable operations, while Skills
teach when and how to compose them. It matches familiar Agent scripting
patterns without exposing a shell or JVM host access.

## Bundled module runtime

`JavascriptModuleCatalog` loads a closed set of UTF-8 resources at startup:

```text
assets/openallay/openallay_js_modules/crafting.js
```

The runtime exposes:

```javascript
const crafting = require("openallay:crafting");
```

`require` accepts one exact string. It resolves only the catalog, evaluates
inside the current safe Rhino scope, caches exports for that execution, and
returns the same exports identity on repeated calls. Modules may require other
catalog modules, but cycles fail explicitly.

The runtime records the ordered unique module IDs used by the execution.
Modules receive only values explicitly passed by the model program, normally
`mc.recipes` and `mc.player.inventory`.

## Crafting module

`openallay:crafting` exports:

```javascript
recipeCost(recipe)
allocate(recipe, inventory, requestedCrafts = 1)
```

`recipeCost` returns consumed ingredient slots, total consumed count, catalyst
count, and fluid count for ranking and comparison.

`allocate` performs deterministic, non-recursive global allocation across
overlapping ingredient alternatives. Its output mirrors the stable
craftability result fields:

```text
craftable
conclusive
requestedCrafts
maximumCrafts
allocations[]
missing[]
```

Parity tests compare it to `CraftabilityCalculator`; this lets the canonical
Java implementation remain a compact oracle without exposing another Tool.

## Complete trace

`LiveAgentTraceRecorder` adds a `model_request` event immediately before every
normal provider dispatch. The payload is the decoded `ModelRequest`, not an
HTTP body. Existing `model_turn`, `tool_call`, `tool_result`, state, failure,
and terminal fields remain unmodified.

`ClientModelRuntimeRegistry` exposes a read-only encoded-trace lookup for an
exact client profile and request ID. The real-client controller waits one or
more ticks after terminal state until the local runtime has published the
trace, then writes the configured `.trace.json` atomically.

The trace is complete and untruncated. Secret redaction uses the same configured
credential set as the runtime. The E2E summary remains small and can link to
the full artifact.

## Live-profile harness

`run-real-client-e2e.sh` gains an explicit live-profile mode:

```text
OPENALLAY_E2E_USE_EXISTING_PROFILE=true
OPENALLAY_E2E_PROFILE_SOURCE=/path/to/config/openallay
```

The script backs up the target ignored runtime config, copies only the required
model/credential files from the explicit source, skips the fixture server, and
restores the target after shutdown. It never prints configuration contents or
credential rows.

Each run accepts a trace output path. Multiple hard tasks use the same session
ID so durable history and multi-turn context are exercised across launches.

## History UI

The persistence projection becomes:

| persistence state | transcript row |
| --- | --- |
| `LOADING` | visible |
| `SAVING` | none |
| `UNAVAILABLE` | visible failure |
| `DISABLED`, `AVAILABLE` | none |

No persistence-state ownership changes. The screen simply stops allocating a
row for normal background writes.

## Acceptance tasks

The retained Fabric run uses the configured `deepseek-v4-flash` profile and a
recipe-rich world. At minimum it executes:

1. rank all installed swords by effective attack damage and report evidence;
2. find the container recipe with the smallest material cost, explain the
   tie-break, and request a native recipe component;
3. compare Farmer's Delight foods across available nutrition/saturation-like
   fields, tolerate missing mod-added fields, and return the best candidates.

The expected efficient path is one Skill load plus one module-backed
`run_javascript` call for a documented task, with no per-row Tool loop.
This is guidance rather than a hard call cap: the model may make a bounded
follow-up query when it materially verifies the candidate universe, resolves
omitted rows, or answers another requested dimension.

## Debug JavaScript detail

The live Tool timeline retains an immutable copy of the decoded invocation
arguments. This field is deliberately absent from durable history. Debug mode
uses it to render the exact `run_javascript.source` with line breaks and line
numbers, then `roots`, `handles`, module IDs, and compact execution/result
metadata. Normal mode remains the player-friendly result card.

The result preview has no row-count cutoff. It admits complete values until the
whole model-facing projection reaches one conservative 8192-token UTF-8
budget, including metadata, continuation guidance, and evidence. This keeps
small complete candidate sets such as all seven vanilla swords inline while
still externalizing genuinely large results. Workspace storage remains
canonical and unchanged. Worked Skill examples return their complete derived
answer set; the presenter, not the script, owns progressive disclosure.

## Git publication

The existing VFS main tree is first pushed to
`deprecated/resource-vfs-v1`. A clean integration branch then records the old
main parent while selecting the Rhino tree as the product result. The dirty
local main worktree is left untouched. After deterministic gates and live E2E,
the tested integration commit is pushed to `main` without force.
