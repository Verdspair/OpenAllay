# JavaScript data graph

`mc` is a lazy, immutable Java-backed view over detached snapshots captured for
the current request. Reading a root or component does not stringify, serialize,
or copy the complete graph. Declared schema discovery does not load a dataset:

```js
return {
  roots: schema.list(),
  installedMods: schema.describe("game.mods.installed"),
  recipeProviders: schema.describe("recipeCatalog.providers")
};
```

Common stable paths are:

- `mc.capabilities`: available roots with provider, schema kind, and evidence
  ownership.
- `mc.items`, `mc.blocks`, `mc.fluids`, `mc.effects`, `mc.enchantments`,
  `mc.entities`: convenient registry arrays when those kinds exist.
- `mc.registryEntries`: all captured registry rows across every kind.
- `mc.registries`: registry evidence, total entry count, and counts by kind.
- `mc.recipes`: recipe rows; `mc.recipeCatalog` also exposes providers, groups,
  diagnostics, and evidence.
- `mc.player`: the caller's detached inventory and visible player state.
- `mc.game.mods.installed`: exact installed mod metadata.
- `mc.game.options.values`, `mc.game.packs`, `mc.game.shaders`,
  `mc.game.diagnostics.values`, `mc.game.player`, and
  `mc.game.worldQueries.values`: exact settings, packs, F3-style values, player
  UI state, and closed world-query results.
- `mc.knowledge`: currently indexed guide and documentation records.
- `mc.knowledgeCatalog`: document count, per-source counts, capture time, and
  evidence.
- `mc.extensions`: trusted extension-provided detached data modules.
- `mc.extensionCatalog`: extension IDs, providers, declared schemas, and
  availability without capturing extension values.
- `mc.extensionDiagnostics`: isolated declaration/capture failures.

Registry rows always include `id`, `kind`, `displayName`, `namespace`,
`provenance`, `aliases`, `tags`, `components`, and `properties`. The
`properties` object is intentionally open-ended: vanilla, loader, and mod
adapters may add arbitrary nested keys such as attributes, food components,
potion effects, durability, enchantment levels, or extension-specific values.

Recipe rows always include `id`, `type`, `layout`, `workstation`,
`ingredients`, `catalysts`, `fluids`, `outputs`, `byproducts`, `processing`,
`conditions`, `extensions`, `unlockState`, and `evidence`. Ingredient rows
include `count`, `consumed`, and `alternatives`; output item IDs are at
`output.stack.itemId`. Use `recipe.id`, never an invented `recipeId` field.

Use `schema.describe(path)` for stable core paths. Use `helpers.schema` only
for representative samples of genuinely dynamic `properties`, recipe
`extensions`, or extension-provided values:

```js
return {
  declaredItems: schema.describe("items"),
  dynamicProperties: helpers.schema(mc.items?.slice(0, 8) ?? [], 4)
};
```

Host arrays support normal non-mutating transforms. `filter`, `map`, `flatMap`,
and `slice` return ordinary JavaScript arrays, which may then be sorted. Direct
mutation of `mc` records or arrays fails by design.

Filter by a stable discriminator (namespace, kind, tag, component, recipe
type) before examining large nested property trees.
