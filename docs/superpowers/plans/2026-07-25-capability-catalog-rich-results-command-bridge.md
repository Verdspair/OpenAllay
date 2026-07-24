# Capability Catalog, Typed Results, and Experimental Command Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the complete detached Minecraft capability graph to Rhino,
make its schema discoverable, render trusted typed JavaScript results natively,
and add an opt-in complete Minecraft command catalog/execution bridge.

**Architecture:** A shared Java-side descriptor catalog derived from KubeJS
Rhino type information drives host mounting, schema discovery, Extensions
settings, Skills, and result semantic sidecars. Commands are a separately
enabled request capability: discovery uses a detached Brigadier projection and
execution marshals exact player commands to the owning Minecraft thread.

**Tech Stack:** Java 25, Minecraft 26.2, KubeJS Rhino, Brigadier, Gson, JUnit 5,
Fabric, NeoForge.

---

### Task 1: Record the accepted state and architecture

**Files:**
- Create: `docs/isme/decisions/2026-07-25-028-capability-catalog-rich-results-and-experimental-commands.md`
- Create: `docs/superpowers/specs/2026-07-25-capability-catalog-rich-results-command-bridge-design.md`
- Modify: `docs/isme/SKMB.md`
- Modify: `AGENTS.md`

- [ ] Add SKMB-028 to the decision index with status `accepted` and commit
  `pending`.
- [ ] Add transitions for future-request command-setting capture, ordered
  command submission, and terminal submission failure.
- [ ] Add invariants for disabled invisibility, player identity/permission,
  full active command registry, and non-rollback.
- [ ] Replace the repository's blanket command prohibition with the exact
  SKMB-028 experimental exception.
- [ ] Run `rg -n "SKMB-2026-07-25-028|command" docs/isme AGENTS.md` and verify
  the accepted decision, transitions, invariants, and repository contract
  agree.

### Task 2: Build one declared host schema catalog

**Files:**
- Create: `common/src/main/java/dev/openallay/script/schema/HostSchema.java`
- Create: `common/src/main/java/dev/openallay/script/schema/HostSchemaCatalog.java`
- Create: `common/src/main/java/dev/openallay/script/schema/HostRootDescriptor.java`
- Create: `common/src/main/java/dev/openallay/script/schema/RhinoTypeSchema.java`
- Modify: `common/src/main/java/dev/openallay/script/host/HostRecordSchema.java`
- Modify: `common/src/main/java/dev/openallay/script/host/RhinoHostAdapter.java`
- Test: `common/src/test/java/dev/openallay/script/schema/HostSchemaCatalogTest.java`

- [ ] Write tests proving records, parameterized lists/maps, optionals, enums,
  Gson open values, and unsupported Java classes receive the same verdict from
  schema generation and the host adapter.
- [ ] Run
  `./gradlew :common:test --tests 'dev.openallay.script.schema.HostSchemaCatalogTest'`
  and confirm the missing catalog fails.
- [ ] Implement the sealed schema algebra and cache record component generic
  types using the dependency's `TypeInfo.of(Type)`.
- [ ] Add root descriptors whose availability and schema can be read without
  resolving their value suppliers.
- [ ] Re-run the focused test and confirm it passes.

### Task 3: Mount the complete captured graph and progressive schema API

**Files:**
- Modify: `common/src/main/java/dev/openallay/script/data/MinecraftAgentHostGraph.java`
- Modify: `common/src/main/java/dev/openallay/script/RhinoJavascriptRuntime.java`
- Modify: `common/src/main/java/dev/openallay/script/extension/JavascriptDataModule.java`
- Modify: `common/src/main/java/dev/openallay/script/extension/JavascriptDataModuleRegistry.java`
- Test: `common/src/test/java/dev/openallay/script/data/MinecraftAgentHostGraphTest.java`
- Test: `common/src/test/java/dev/openallay/script/extension/JavascriptDataModuleRegistryTest.java`
- Test: `common/src/test/java/dev/openallay/script/RhinoJavascriptRuntimeTest.java`

- [ ] Add failing assertions for full recipe catalog metadata, unified registry
  rows, knowledge metadata, stable evidence, exact game paths, extension
  descriptors, `schema.list()`, and `schema.describe(...)`.
- [ ] Run the three focused test classes and confirm the new assertions fail.
- [ ] Replace count-only root suppliers with descriptor-backed complete
  detached records and add `registryEntries`/`knowledgeCatalog`.
- [ ] Add extension descriptors with provider ID and declared type; validate a
  captured extension value against the closed schema without resolving
  descriptors in settings.
- [ ] Inject the immutable schema global and keep `helpers.schema` for dynamic
  sample values.
- [ ] Re-run the focused tests.

### Task 4: Add typed normalization and native result views

**Files:**
- Create: `common/src/main/java/dev/openallay/script/result/JavascriptSemanticKind.java`
- Create: `common/src/main/java/dev/openallay/script/result/JavascriptResultShape.java`
- Create: `common/src/main/java/dev/openallay/script/result/JavascriptResultViewRegistry.java`
- Modify: `common/src/main/java/dev/openallay/script/host/HostObjectView.java`
- Modify: `common/src/main/java/dev/openallay/script/host/HostListView.java`
- Modify: `common/src/main/java/dev/openallay/script/RhinoJsonNormalizer.java`
- Modify: `common/src/main/java/dev/openallay/script/JavascriptExecution.java`
- Modify: `common/src/main/java/dev/openallay/script/workspace/JavascriptResultPresenter.java`
- Modify: `common/src/main/java/dev/openallay/tool/builtin/RunJavascriptTool.java`
- Modify: `common/src/main/java/dev/openallay/guide/ui/GuideDetailCard.java`
- Modify: `common/src/main/java/dev/openallay/guide/ui/GuideToolDetailPresenter.java`
- Test: `common/src/test/java/dev/openallay/script/JavascriptTypedResultTest.java`
- Test: `common/src/test/java/dev/openallay/guide/ui/GuideToolDetailPresenterTest.java`

- [ ] Write failing cases for direct recipes, direct items, homogeneous derived
  rows, scalars, mixed rows, forged recipe-shaped rows, and preview truncation.
- [ ] Run the two focused test classes and verify failure.
- [ ] Carry trusted type metadata on host wrappers and return a sidecar beside
  canonical JSON without adding type markers to script-visible data.
- [ ] Classify canonical values before preview budgeting and add bounded typed
  presentation data to `RunJavascriptTool.Output`.
- [ ] Reuse existing recipe/item bindings, add a structured table card, and
  degrade malformed or mixed values to the generic preview.
- [ ] Re-run the focused tests and assert model text/canonical JSON contain no
  internal sidecar markers.

### Task 5: Replace Tools settings with Extensions

**Files:**
- Modify: `common/src/main/java/dev/openallay/client/gui/settings/SettingsSection.java`
- Modify: `common/src/main/java/dev/openallay/tool/config/ToolFamilyId.java`
- Create: `common/src/main/java/dev/openallay/client/gui/settings/ExtensionSettingsProjection.java`
- Modify: `common/src/main/java/dev/openallay/client/gui/OpenAllaySettingsScreen.java`
- Modify: `common/src/main/java/dev/openallay/settings/ClientSettingsSnapshot.java`
- Modify: `common/src/main/java/dev/openallay/settings/ClientSettingsService.java`
- Modify: `common/src/main/resources/assets/openallay/lang/en_us.json`
- Modify: `common/src/main/resources/assets/openallay/lang/zh_cn.json`
- Test: `common/src/test/java/dev/openallay/client/gui/OpenAllaySettingsScreenProjectionTest.java`
- Test: `common/src/test/java/dev/openallay/client/gui/settings/ExtensionSettingsProjectionTest.java`

- [ ] Write failing tests that require the top-level Extensions label, unique
  ownership of `run_javascript`, bundled module IDs, adapter descriptors, root
  availability/schema, empty states, and debug metadata.
- [ ] Run the focused settings tests and verify failure.
- [ ] Keep existing family/source configuration but present it under
  Extensions; add descriptor-only module/adapter/root sections without
  triggering capture.
- [ ] Add localized normal/debug cards and an experimental subsection.
- [ ] Re-run settings and localization tests.

### Task 6: Make invocation and output detail useful

**Files:**
- Create: `common/src/main/java/dev/openallay/guide/GuideToolInvocationView.java`
- Modify: `common/src/main/java/dev/openallay/guide/GuideToolInvocationPresentation.java`
- Modify: `common/src/main/java/dev/openallay/guide/GuideToolActivity.java`
- Modify: `common/src/main/java/dev/openallay/guide/history/GuideHistoryCodec.java`
- Modify: `common/src/main/java/dev/openallay/guide/ui/GuideToolDetailView.java`
- Modify: `common/src/main/java/dev/openallay/guide/ui/GuideToolDetailPresenter.java`
- Modify: `common/src/main/java/dev/openallay/client/gui/OpenAllayScreen.java`
- Test: `common/src/test/java/dev/openallay/guide/history/GuideHistoryCodecTest.java`
- Test: `common/src/test/java/dev/openallay/guide/ui/GuideToolDetailPresenterTest.java`

- [ ] Add failing tests for normal roots/handles/modules, typed output rows,
  live Debug source/raw envelope, restored Debug unavailability, and raw
  invocation exclusion from durable JSON.
- [ ] Run the focused tests and verify failure.
- [ ] Persist only the closed normal invocation projection; retain exact raw
  arguments in current-request memory.
- [ ] Split detail into input/output/debug sections and cache/virtualize wrapped
  code lines so render cost follows visible lines rather than source length.
- [ ] Re-run the focused tests.

### Task 7: Add the experimental complete command catalog and bridge

**Files:**
- Create: `common/src/main/java/dev/openallay/script/command/CommandCapabilityConfig.java`
- Create: `common/src/main/java/dev/openallay/script/command/CommandCapabilityRuntime.java`
- Create: `common/src/main/java/dev/openallay/script/command/CommandCatalogSnapshot.java`
- Create: `common/src/main/java/dev/openallay/script/command/CommandSubmission.java`
- Create: `common/src/main/java/dev/openallay/script/command/JavascriptCommandBridge.java`
- Modify: `common/src/main/java/dev/openallay/tool/ToolAccess.java`
- Modify: `common/src/main/java/dev/openallay/tool/builtin/RunJavascriptTool.java`
- Modify: `common/src/main/java/dev/openallay/bridge/client/ClientToolExecutionEndpoint.java`
- Modify: `common/src/main/java/dev/openallay/bridge/server/ExportedToolPolicy.java`
- Modify: `fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java`
- Modify: `neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java`
- Test: `common/src/test/java/dev/openallay/script/command/JavascriptCommandBridgeTest.java`
- Test: `common/src/test/java/dev/openallay/bridge/PlayerClientCommandBridgeTest.java`

- [ ] Write failing tests for disabled absence, enabled list/describe/run,
  fixture mod literal and argument nodes, exact player identity, ordered
  submission, disconnect, cancellation-before-submit, and no rollback after a
  later JavaScript failure.
- [ ] Run the focused tests and verify failure.
- [ ] Capture a detached Brigadier tree from the current player-visible
  dispatcher on the client thread, preserving literals, argument type labels,
  executable flags, redirects, and usage.
- [ ] Add a client-thread submitter that removes only one leading slash and
  invokes Minecraft's normal player command route without an OpenAllay
  allowlist, argument filter, or call cap.
- [ ] Add the `EXPERIMENTAL_ACTION` maximum access classification and permit
  player-client routing only when the captured local setting enables the
  command bridge.
- [ ] Re-run the focused tests.

### Task 8: Persist the experimental toggle and gate its Skill

**Files:**
- Create: `common/src/main/java/dev/openallay/script/command/CommandCapabilityConfigLoader.java`
- Create: `common/src/main/java/dev/openallay/script/command/CommandCapabilityConfigWriter.java`
- Modify: `common/src/main/java/dev/openallay/settings/ClientSettingsRuntime.java`
- Modify: `common/src/main/java/dev/openallay/settings/ClientSettingsService.java`
- Create: `common/src/main/resources/assets/openallay/openallay_skills/run-game-commands/SKILL.md`
- Create: `common/src/main/resources/assets/openallay/openallay_skills/run-game-commands/references/commands.md`
- Modify: `common/src/main/java/dev/openallay/agent/AgentSystemPrompt.java`
- Modify: `common/src/main/resources/assets/openallay/openallay_skills/analyze-game-data/SKILL.md`
- Modify: `common/src/main/resources/assets/openallay/openallay_skills/analyze-game-data/references/datasets.md`
- Test: `common/src/test/java/dev/openallay/script/command/CommandCapabilityConfigTest.java`
- Test: `common/src/test/java/dev/openallay/skill/BundledSkillsTest.java`
- Test: `common/src/test/java/dev/openallay/agent/AgentSystemPromptTest.java`

- [ ] Write failing tests proving default-off strict persistence, future-request
  capture, disabled Skill omission, enabled Skill metadata, exact stable
  dataset paths, and command Skill examples.
- [ ] Run the focused tests and verify failure.
- [ ] Implement atomic strict config storage and settings mutation; do not
  migrate pre-release files.
- [ ] Capture the command-enabled flag and matching Skill catalog when a future
  request starts.
- [ ] Replace drift-prone handwritten paths with generated catalog excerpts
  where possible and keep direct common paths concise in the system prompt.
- [ ] Re-run the focused tests.

### Task 9: Verify, commit, and push

**Files:**
- Modify: `docs/development.md`
- Modify: `docs/isme/SKMB.md`
- Modify: `docs/isme/decisions/2026-07-25-028-capability-catalog-rich-results-and-experimental-commands.md`

- [ ] Run focused tests for schema, host graph, typed results, settings, detail,
  command bridge, Skills, and prompt.
- [ ] Run `./gradlew :common:test`.
- [ ] Run `./gradlew :fabric:build :neoforge:build`.
- [ ] Inspect `git diff --check`, `git status --short`, and staged filenames for
  credentials, generated run files, or unrelated worktree changes.
- [ ] Update SKMB-028 and the index with the final commit state, stage the
  coherent implementation, and commit with a Conventional Commit message.
- [ ] Push `feat/js-agent-runtime` to `origin` without force and report the
  exact commit and verification truth.
