# Remove Unused Client Compatibility Surfaces Implementation Plan

> **For agentic workers:** This cleanup is intentionally limited to repository-proven dead client compatibility paths. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove unused client single-model factories and aliases, obsolete thin wrappers, and client model configuration imports for schema 1 and `model.json`, while keeping the independent server model parser and accepted history behavior unchanged.

**Architecture:** The client uses only schema-2 named profiles through `ClientSettingsRuntime` and `ClientModelRuntimeRegistry`. Remove the no-caller factory and alias surfaces. Profile loading becomes strict schema 2 from `models.json`; older client configuration files remain untouched and produce a clear configuration notice. `ModelConfigLoader` stays because server-model configuration still uses it.

**Tech Stack:** Java 25, Gradle, JUnit 5, Fabric, NeoForge.

---

### Task 1: Record the configuration break

**Files:**
- Create: `docs/isme/decisions/2026-07-25-031-remove-unused-client-compatibility.md`
- Modify: `docs/isme/SKMB.md`
- Modify: `docs/development.md`

- [x] Record that client models.json schema 2 is the only supported client model profile format; schema 1 and model.json are not imported; rejected files remain untouched; server-model.json remains on its separate loader.
- [x] Add the decision index row and a failure code/behavior note.
- [x] Update developer configuration docs to require schema 2 and say old model.json/schema-1 files are ignored with an actionable notice.

### Task 2: Remove client legacy model imports

**Files:**
- Modify: `common/src/main/java/dev/openallay/model/config/ModelProfilesConfigLoader.java`
- Modify: `common/src/main/java/dev/openallay/settings/ClientSettingsRuntime.java`
- Modify: `common/src/main/java/dev/openallay/settings/model/ModelSettingsBackend.java`
- Modify: `common/src/main/java/dev/openallay/model/metadata/ModelMetadataBootstrap.java`
- Modify: `common/src/main/java/dev/openallay/client/ClientModelRuntimeRegistry.java`
- Modify: `fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java`
- Modify: `neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java`
- Test: `common/src/test/java/dev/openallay/model/config/ModelProfilesConfigLoaderTest.java`
- Test: `common/src/test/java/dev/openallay/model/metadata/ModelMetadataBootstrapTest.java`
- Test: `common/src/test/java/dev/openallay/settings/ClientSettingsRuntimeTest.java`

- [x] Convert profile fixtures to schema 2 `credentialRef` values.
- [x] Reject schema 1 with `invalid_model_config`; change file loader to load only `models.json` and never inspect/delete `model.json`.
- [x] Remove `Load.legacy`, old model-path parameters, environment-to-inline legacy conversion and `ModelConfigLoader` reference from the client profiles loader. Retain `ModelConfigLoader` for `ServerGuideRuntime`.
- [x] Remove `legacyPath` through settings, registry, and metadata bootstrap constructors/calls on both loaders.
- [x] Test schema-1 rejection, ignored-but-preserved model.json, current schema-2 loading, and normal unconfigured behavior.
- [x] Run focused profile/settings/metadata tests.

### Task 3: Remove proven unused compatibility APIs and wrappers

**Files:**
- Delete: `common/src/main/java/dev/openallay/guide/semantic/SemanticStyle.java`
- Delete: `common/src/main/java/dev/openallay/guide/ui/GuideToolPresenter.java`
- Delete: `common/src/test/java/dev/openallay/guide/ui/GuideToolPresenterTest.java`
- Modify: `common/src/main/java/dev/openallay/settings/extension/ExtensionSettingsBackend.java`
- Modify: `common/src/main/java/dev/openallay/client/ClientGuideRuntime.java`
- Modify: `common/src/main/java/dev/openallay/guide/ui/GuideDisplayConfig.java`
- Modify: `common/src/test/java/dev/openallay/client/gui/OpenAllayScreenProjectionTest.java`
- Modify: `common/src/test/java/dev/openallay/settings/ClientSettingsServiceTest.java`

- [x] Remove the zero-caller backend stageLocal/stageDownload forwarding aliases; preserve calls to `ExtensionPackageInstaller.stageLocal/stageDownload`.
- [x] Remove the unreferenced old single-model `ClientGuideRuntime.create(...)` factories and now-unused imports; preserve runtime constructors used by registry and architecture test.
- [x] Remove the zero-reference `SemanticStyle` enum and presenter-only adapter/test; production callers already use `GuideToolPresentation`.
- [x] Remove the three-argument `GuideDisplayConfig` source-compatibility constructor and update the two remaining tests to pass `DEFAULT_ASSISTANT_NAME` explicitly.
- [x] Confirm no remaining references to removed APIs/types with repository-wide search.

`GuideRequestSnapshot.legacyProgress` was intentionally retained. Durable history loading, history row projection, and recovery currently still construct snapshots without explicit progress, so that helper is not repository-proven dead code.

### Task 4: Validate and commit

**Files:** Verify only.

- [x] Run focused model config/settings/metadata/presenter tests.
- [x] Run `./gradlew clean :common:test :fabric:build :neoforge:build`.
- [x] Run `./scripts/verify-phase4-package.sh`, `./scripts/verify-sqlite-packaging.sh`, `./scripts/verify-distribution.sh`, and `git diff --check`.
- [x] Review final diff and stage only the planned code/tests/docs; commit as `refactor: remove unused client compatibility paths`.
