# SKMB-2026-07-25-031: Remove Unused Client Compatibility Surfaces

- status: accepted
- decided_by: designer
- approval_source: >-
    The designer explicitly requested an aggressive source cleanup before new
    work, including removing obsolete compatibility and dead code.
- date: 2026-07-25
- commit: pending
- patterns: B_state_persistence, E_security_boundary, F_fail_semantics
- scope: client model-profile input formats and repository-proven unused APIs
- supersedes: none

## Decision

The client supports only the strict `models.json` schema 2 named-profile format.
The client loader rejects schema 1 and does not import the old single-profile
`model.json` format. Startup and settings reload leave old or invalid files
untouched and report the normal redacted configuration failure; the player may
replace them by explicitly saving valid schema 2 settings. There is no automatic
migration, fallback, or deletion.

The flat `server-model.json` format is a separate server-owned configuration and
continues to use `ModelConfigLoader`. This cleanup does not remove server
configuration environment overrides or its supported credential input.

Remove repository-proven unused client compatibility APIs: the old standalone
single-model `ClientGuideRuntime.create` factories, forwarding aliases in
`ExtensionSettingsBackend`, the unreferenced `SemanticStyle` declaration and
`GuideToolPresenter` adapter/test, and the three-argument
`GuideDisplayConfig` constructor. Current consumers use the named-profile
runtime, `importLocalPackage`/`installCommunity`, `GuideToolPresentation`, and
the full display record constructor respectively.

This decision does not authorize deletion of historical decisions/evidence,
SQLite schema rebuild behavior, loader protocol checks, the server model parser,
or public functionality merely because it is complex or has few in-repository
references.

## States and transitions

- A valid schema 2 `models.json` loads into the client model registry.
- A missing `models.json` plus any `model.json` remains unconfigured; do not read
  or modify the legacy file.
- A schema 1, unknown schema, malformed, or invalid `models.json` fails closed,
  leaves the file untouched, and publishes a redacted settings notice.
- An explicit successful save writes schema 2 atomically and publishes the
  prepared named-profile runtime.

## Invariants

1. Client profile loading accepts only schema 2 and never silently imports or
   falls back to another model file.
2. Invalid and legacy client configuration files are never deleted or rewritten
   by startup, reload, or metadata refresh.
3. The server model keeps its separate `server-model.json` contract.
4. Active requests retain the selected immutable runtime during settings changes.
5. Removal of unused source APIs does not change their replacement runtime
   behavior or remove the underlying Extension package staging operations.

## Failure semantics

- Missing `models.json`: `model_not_configured`; no provider request is sent.
- Schema 1 or malformed `models.json`: `invalid_model_config`; preserve the
  source file and retain the unconfigured/previous runtime.
- Explicit profile save failure: retain the prior file and runtime as defined by
  SKMB-2026-07-18-015 and SKMB-2026-07-19-019.

## Verification

- Strict profile-loader tests cover schema 2 success, schema 1 rejection, and
  preservation of an unimported `model.json` file.
- Both loader startup paths use only `models.json` for client profiles.
- Server configuration and history schema behavior retain their existing tests.
- Full common tests and both loader builds pass.
