# Capability Catalog, Typed Results, and Experimental Command Bridge Design

**Date:** 2026-07-25
**Status:** Accepted for implementation
**Decision:** SKMB-2026-07-25-028
**Branch:** `feat/js-agent-runtime`

## Objective

Make OpenAllay's Rhino surface self-describing, connect every already captured
data category, render JavaScript results according to their trusted types, and
add an opt-in complete Minecraft command catalog/execution bridge.

## Selected architecture

One Java-owned capability catalog sits between captured snapshots and all
consumers:

```text
detached Minecraft snapshots
  -> HostRootDescriptor + HostSchema catalog
     -> Rhino root graph and schema API
     -> Extensions settings projection
     -> Skill/schema documentation
     -> normalization semantic sidecar
        -> compact model text
        -> typed player UI
```

The catalog derives record and generic collection structure from the KubeJS
Rhino `TypeInfo` facilities and validates it against OpenAllay's closed
`RhinoHostAdapter` algebra. It does not expose KubeJS's general Java bridge.

## Complete root graph

The root graph keeps the existing convenient kind arrays and adds the metadata
that current snapshots already contain:

- `registryEntries` for cross-kind analysis;
- `registries` with count and evidence;
- `recipes` plus full `recipeCatalog`;
- `knowledge` plus `knowledgeCatalog`;
- `game` with its exact nested record schema;
- `extensions` and `extensionDiagnostics`;
- stable root-scoped evidence descriptors;
- `capabilities` derived from actually available descriptors.

`recipeCatalog` exposes providers, semantic groups, catalog diagnostics, count,
and evidence. JEI/REI recipe-provider presence therefore becomes inspectable
without inventing a separate Tool.

## Schema surface

Rhino receives a separate immutable global named `schema`:

```javascript
schema.list()
schema.describe("game.mods")
schema.describe("recipeCatalog.providers")
```

`list` is compact and does not resolve root values. `describe` walks the
declared type graph to a bounded requested path. `helpers.schema(value)` remains
available for one focused sample of genuinely dynamic mod-added JSON.

Stable core paths are summarized in the Tool description and Skill reference,
so common questions such as installed mods use `mc.game.mods.installed`
directly. Full declared schemas are progressive rather than injected into every
model turn.

## Extensions settings

The top-level player label changes from Tools to Extensions. Existing recipe
and knowledge-source configuration remains available, while the selected
extension detail adds:

- the Rhino analytical surface and exact parameters/returns;
- bundled helper module IDs;
- registered detached data adapter IDs and provider ownership;
- exposed root names, schemas, and availability;
- an experimental command toggle.

Normal mode is player-readable. Debug adds exact IDs, provider, execution
placement, declared schema, and diagnostics. Listing descriptors never captures
live data.

## Typed result pipeline

`RhinoHostAdapter` tags trusted wrappers with a closed semantic kind and declared
type. `RhinoJsonNormalizer` returns canonical JSON together with a sidecar
shape. Host-backed recipes and items retain their kind through non-mutating
array operations. A model-created object is an ordinary derived row.

`JavascriptResultPresenter` classifies complete canonical data before preview
budgeting and emits a bounded typed presentation:

- recipes -> recipe cards/native provider view;
- items -> item grid;
- homogeneous derived rows -> structured table;
- scalar/object -> key/value;
- mixed/unknown -> generic data preview.

The model receives compact factual text. The screen receives the closed typed
presentation. The model can still emit semantic components, but ordinary
recipe/item display no longer depends on it doing so.

## Tool detail

The normal projection stores exact safe invocation facts: roots, opaque
handles, and modules/actions actually used. It shows the typed returned rows and
explicit omission/continuation state.

Live Debug shows the full JavaScript source and bounded normalized result
envelope. Source and raw tree rendering are line-virtualized; wrapping is
cached, and only visible lines are drawn. Durable history does not retain raw
source or canonical workspace values.

## Experimental commands

The independent settings value defaults to false. When false, `commands` and
the matching Skill are absent. When true, the request captures:

- a detached projection of the player's active Brigadier command tree,
  including mod registrations;
- a player-scoped command submitter that marshals strings back to the owning
  Minecraft thread.

The JavaScript contract is:

```javascript
const catalog = commands.list();
const node = commands.describe("give");
const executed = commands.run("say hello from OpenAllay");
return {
  catalog,
  node,
  state: executed.state,
  messages: executed.messages
};
```

No OpenAllay command allowlist, argument restriction, or call-count limit is
added. Minecraft owns parsing and permissions. Calls are serialized per player
and return the observed non-overlay game-message feedback after a quiet window;
`no_feedback` is explicit when Minecraft emits no observable response.
Submission remains explicitly non-transactional.

## Skills and prompting

`analyze-game-data` is rewritten around the generated stable schema index and
verified examples. Simple exact-path questions do not require it. A new
experimental command Skill is advertised only when the request captured the
enabled command capability; it documents discovery, exact command submission,
ordering, and non-rollback semantics.

The system prompt stops carrying the full Rich UI JSON catalog. It explains
that trusted Tool results are rendered automatically and gives only the small
syntax needed for derived UI that cannot be inferred.

## Verification

Focused contracts cover catalog generation, missing/available roots, extension
descriptors, recipe providers, typed normalization, UI projection, history
round-trip, command toggle capture, complete command-tree projection,
mod-command fixtures, command ordering, disconnect/cancellation semantics, and
both loader builds.
