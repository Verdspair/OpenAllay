# SKMB-2026-07-25-030: Loader-Specific Extension Artifacts

- status: accepted
- decided_by: designer-delegated implementation
- approval_source: >-
    The designer requires Fabric and NeoForge to remain equal first-class
    loaders, approved normal loader-owned Extension packaging, delegated final
    0.2.x implementation decisions, and asked OpenAllay to continue iterating
    rather than preserve a known incompatible catalog shape.
- date: 2026-07-25
- commit: 5a1520f; implemented through 897bced
- patterns:
  - B_state_persistence
  - D_external_dependency
  - F_fail_semantics
- scope: Extension community catalog identity, loader artifact selection,
  package verification, cache compatibility, and settings projection
- supersedes: >-
    The schema-1 Extension catalog shape in SKMB-2026-07-25-029. The embedded
    per-JAR package manifest remains schema 1.

## Decision basis

```yaml
decision_basis:
  decision_id: SKMB-2026-07-25-030
  trigger: >-
    A normal Fabric Extension and a normal NeoForge Extension are different
    loader JARs, but schema 1 exposed only one artifact URL, checksum, and mod
    ID set for a logical Extension version. A client could therefore download
    or validate the wrong loader package.
  authority:
    - Fabric and NeoForge are equal first-class loaders
    - Extensions register through their normal loader lifecycle
    - catalog installation must verify the exact downloaded package
    - the 0.2.x line may make focused compatibility corrections
  selected_behavior:
    catalog_schema: 2
    logical_identity: one entry per Extension ID and version
    artifact_identity: exactly zero or one artifact per loader in that entry
    selection: select the current loader before compatibility, HTTP, or staging
    checksum_authority: selected loader artifact
    mod_id_authority: selected loader artifact and embedded package manifest
    local_import_authority: embedded package manifest; catalog membership optional
    incompatible_loader: no artifact for the current loader
    cache_upgrade: schema-1 cache is rejected; a valid schema-2 refresh replaces it
  retained_boundaries:
    - each downloaded JAR is verified independently
    - no hot loading or loader emulation
    - failed refresh/install retains prior valid installed packages
    - package manifest schema and loader metadata remain strict
```

## States

- `extension_catalog_v2_ready`: one validated schema-2 generation is readable.
- `extension_artifact_selected`: one entry has resolved exactly one artifact
  for the current loader.
- `extension_restart_required`: the selected, verified JAR has been atomically
  staged for the next normal loader restart.

## Transitions

1. `catalog_refreshing -> extension_catalog_v2_ready`: decode the exact
   schema-2 fields, reject duplicate logical identities or duplicate loader
   artifacts, and atomically publish the generation.
2. `extension_catalog_v2_ready -> extension_artifact_selected`: resolve the
   current normalized loader before making an HTTP request or reading catalog
   checksum/mod-ID authority.
3. `extension_artifact_selected -> extension_restart_required`: verify the
   selected artifact URL response, SHA-256, embedded identity, current-loader
   descriptor, package mod IDs, and loader metadata, then atomically stage it.
4. `extension_catalog_v2_ready -> incompatible_loader`: no artifact is
   declared for the current loader; make no HTTP request and stage nothing.

## Invariants

1. One logical Extension ID/version contains at most one artifact per loader.
2. Fabric never downloads, checks, or stages the NeoForge artifact, and
   NeoForge never downloads, checks, or stages the Fabric artifact.
3. The selected artifact checksum and mod IDs are not shared across loaders.
4. Settings may show the union of supported loaders, but install/update
   details and actions use only the current-loader artifact.
5. A local JAR does not require a community catalog entry and remains governed
   by its embedded manifest and actual loader metadata.

## Failure semantics

- Schema 1, unknown fields, duplicate loader artifacts, invalid URLs,
  checksums, or mod IDs reject the candidate catalog as
  `catalog_refresh_failed`; the last valid schema-2 generation remains active.
- A missing current-loader artifact returns `incompatible_loader` before
  transport.
- A mismatch between the selected catalog artifact and embedded JAR returns
  the existing checksum, manifest, or loader-metadata failure and publishes no
  staged replacement.

## Required evidence

1. strict schema-2 encode/decode and schema-1 rejection;
2. duplicate loader artifact rejection;
3. independent Fabric and NeoForge artifact selection from one logical entry;
4. checksum, manifest, mod-ID, compatibility, and loader metadata failures;
5. public catalog validator parity with runtime validation;
6. a real external example that builds separate Fabric and NeoForge JARs
   against a released OpenAllay 0.2.x API.
