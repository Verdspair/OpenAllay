# JavaScript Modules and Complete Real-Client E2E Implementation Plan

> Execute in this branch, keeping the local dirty `main` worktree untouched.

**Goal:** Make bundled JS modules the reusable domain layer, retain complete
real-client traces, silence successful history saves, validate hard tasks with
the real Fabric client, and publish the tested Rhino line to `main`.

**Decision:** SKMB-2026-07-24-027

## 1. Establish the Git safety boundary

- Push `c8d9213` to `deprecated/resource-vfs-v1`.
- Verify the remote ref.
- Do not merge Resource VFS registration or prompt changes into the Rhino tree.
- Preserve uncommitted files in `/Users/nkanf/projs/TomeWisp`.

## 2. Add the closed JavaScript module catalog

- Add `JavascriptModuleCatalog`.
- Load bundled UTF-8 resources by exact ID.
- Add a per-execution `require` function and module cache to
  `RhinoJavascriptRuntime`.
- Reject unknown IDs, malformed exports, cycles, and constructors.
- Return ordered module IDs in `JavascriptExecution`.
- Add focused runtime tests.

## 3. Move craftability into a bundled module

- Add `openallay:crafting` as a resource module.
- Implement recipe cost and deterministic allocation.
- Add parity tests against `CraftabilityCalculator`.
- Remove `CalculateCraftabilityTool` from bootstrap/advertisement.
- Update system prompt, Skills, Tool families, settings projections, and tests.
- Keep the Java calculator as a test oracle/internal utility.

## 4. Retain complete provider-neutral traces

- Record each normal outbound `ModelRequest`.
- Add exact profile/request trace lookup to `ClientModelRuntimeRegistry`.
- Add a trace path to `GuideClientE2EConfig`.
- Make the controller wait for trace publication after terminal state.
- Persist the redacted trace atomically and fail acceptance if unavailable.
- Cover redaction, completeness, and waiting behavior.

## 5. Silence successful history saves

- Remove the `SAVING` transcript row.
- Keep `LOADING` and `UNAVAILABLE` rows.
- Update `GuideUiViewTest` and localization only if keys become unused.

## 5a. Show exact JavaScript in debug Tool detail

- Carry exact immutable invocation arguments from `ToolStarted` into the live
  `GuideToolActivity`.
- Omit invocation arguments from durable history projection and codecs.
- Render complete JavaScript source, roots, handles, modules, and result
  metadata only when debug mode is enabled.
- Replace the fixed preview-row cap with one conservative 8192-token UTF-8
  budget over the complete model-facing Tool projection.
- Execute the exact bundled highest-damage example in a deterministic test and
  assert that it returns total candidate count plus the requested top five.

## 6. Add opt-in live-profile E2E execution

- Add explicit existing-profile mode to `run-real-client-e2e.sh`.
- Copy/restore ignored profile and credential files without printing contents.
- Skip the deterministic fixture only in this explicit mode.
- Retain one summary and one complete trace per run.

## 7. Verify deterministically

- Run focused module/runtime/trace/UI tests.
- Run the complete common test suite.
- Build Fabric and NeoForge.
- Inspect the diff for credentials, endpoints, and unintended VFS changes.

## 8. Run real Fabric acceptance

- Copy the existing Fabric test world into the ignored feature run directory.
- Use the explicitly supplied existing DeepSeek profile.
- Keep the Mac awake during each graphical run.
- Execute at least the sword, minimum-material container, and
  Farmer's Delight food-analysis tasks under one durable session.
- Retain reports, full traces, and relevant client logs.
- Inspect Tool counts, module IDs, final answers, failures, and redaction.

## 9. Publish to main

- Commit coherent implementation and retained verification evidence.
- Create a clean integration ref that has the archived main history as a
  parent but selects the tested Rhino tree.
- Re-run the smallest merge-sensitive gate.
- Push the integration ref to `main` without force.
- Report the exact commit, CI state, retained trace paths, and any remaining
  risk.
