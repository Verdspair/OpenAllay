package dev.openallay.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.tool.ToolResult;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LoadSkillToolTest {
    @Test
    void returnsOnlyAValidatedNamedSkill() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/skill.md",
                Map.of("guide/skill.md", """
                        ---
                        name: guide
                        description: Guide the player
                        required-mods: []
                        allowed-tools: []
                        references: []
                        ---
                        Follow evidence.
                        """))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository);

        ToolResult.Success<LoadSkillTool.Output> success = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
        assertEquals("Follow evidence.", success.value().content());
        assertEquals("SKILL.md", success.value().document());
        assertEquals(true, success.value().complete());
        assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("missing")));
    }

    @Test
    void cannotLoadSkillExcludedFromCapturedCatalog() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/skill.md",
                Map.of("guide/skill.md", """
                        ---
                        name: guide
                        description: Guide the player
                        required-mods: []
                        allowed-tools: []
                        references: []
                        ---
                        Follow evidence.
                        """))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository.snapshot(Set.of("guide")));

        ToolResult.Failure<LoadSkillTool.Output> failure = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));

        assertEquals("skill_not_found", failure.code());
        assertInstanceOf(
                ToolResult.Success.class,
                new LoadSkillTool(repository).invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
    }

    @Test
    void progressivelyLoadsOneDeclaredReferenceInsteadOfInjectingAllReferences() {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/SKILL.md",
                Map.of(
                        "guide/SKILL.md", """
                                ---
                                name: guide
                                description: Guide the player
                                ---
                                Read the matching reference.
                                """,
                        "guide/references/a.md", "A contents",
                        "guide/references/b.md", "B contents"))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository);

        ToolResult.Success<LoadSkillTool.Output> entrySuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output entry = entrySuccess.value();
        assertEquals("Read the matching reference.", entry.content());
        assertEquals(java.util.List.of("references/a.md", "references/b.md"),
                entry.availableReferences());

        ToolResult.Success<LoadSkillTool.Output> referenceSuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", "references/b.md")));
        LoadSkillTool.Output reference = referenceSuccess.value();
        assertEquals("references/b.md", reference.document());
        assertEquals("B contents", reference.content());

        ToolResult.Failure<LoadSkillTool.Output> missing = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", "references/missing.md")));
        assertEquals("skill_reference_not_found", missing.code());
    }

    @Test
    void readsLargeDocumentsWithSnapshotBoundOpaqueCursors() {
        String contents = "paragraph\n\n".repeat(1_500);
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/SKILL.md",
                Map.of("guide/SKILL.md", """
                        ---
                        name: guide
                        description: Guide the player
                        ---
                        %s
                        """.formatted(contents)))), Set.of());
        LoadSkillTool tool = new LoadSkillTool(repository);

        ToolResult.Success<LoadSkillTool.Output> firstSuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output first = firstSuccess.value();
        assertEquals(false, first.complete());

        ToolResult.Success<LoadSkillTool.Output> secondSuccess = assertInstanceOf(
                ToolResult.Success.class,
                tool.invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", null, first.nextCursor())));
        LoadSkillTool.Output second = secondSuccess.value();
        assertEquals(first.nextOffset(), second.offset());
        assertEquals(contents.strip(), (first.content() + second.content()
                + readRemaining(tool, second)).strip());

        ToolResult.Failure<LoadSkillTool.Output> wrongDocument = assertInstanceOf(
                ToolResult.Failure.class,
                tool.invoke(
                        ToolInvocationContext.developmentConsole("test"),
                        new LoadSkillTool.Input("guide", "references/a.md", first.nextCursor())));
        assertEquals("skill_reference_not_found", wrongDocument.code());
    }

    @Test
    void completedDocumentReturnsACompactReceiptInsteadOfRepeatingContent() {
        SkillRepository repository = repository("Follow evidence.");
        LoadSkillTool tool = new LoadSkillTool(repository);
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");

        LoadSkillTool.Output first = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output duplicate = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.COMPLETE, first.state());
        assertEquals("Follow evidence.", first.content());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, duplicate.state());
        assertEquals("", duplicate.content());
        assertEquals(true, duplicate.complete());
    }

    @Test
    void explicitRehydrationAndRequestCloseAllowContentToBeReadAgain() {
        SkillRepository repository = repository("Follow evidence.");
        LoadSkillTool tool = new LoadSkillTool(repository);
        ToolInvocationContext firstRequest =
                ToolInvocationContext.developmentConsole("request-1");

        success(tool.invoke(firstRequest, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output rehydrated = success(tool.invoke(
                firstRequest, new LoadSkillTool.Input("guide", null, null, true)));
        assertEquals(LoadSkillTool.LoadState.REHYDRATED, rehydrated.state());
        assertEquals("Follow evidence.", rehydrated.content());

        tool.closeRequestScope("request-1");
        LoadSkillTool.Output reopened = success(tool.invoke(
                firstRequest, new LoadSkillTool.Input("guide")));
        assertEquals(LoadSkillTool.LoadState.COMPLETE, reopened.state());
        assertEquals("Follow evidence.", reopened.content());
    }

    @Test
    void repeatingTheFirstChunkReturnsTheExpectedContinuationReceipt() {
        SkillRepository repository = repository("paragraph\n\n".repeat(1_500));
        LoadSkillTool tool = new LoadSkillTool(repository);
        ToolInvocationContext request = ToolInvocationContext.developmentConsole("request-1");

        LoadSkillTool.Output first = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));
        LoadSkillTool.Output duplicate = success(tool.invoke(
                request, new LoadSkillTool.Input("guide")));

        assertEquals(LoadSkillTool.LoadState.CONTENT, first.state());
        assertEquals(LoadSkillTool.LoadState.ALREADY_LOADED, duplicate.state());
        assertEquals("", duplicate.content());
        assertEquals(false, duplicate.complete());
        assertEquals(first.nextCursor(), duplicate.nextCursor());
    }

    private static SkillRepository repository(String body) {
        SkillRepository repository = new SkillRepository(new SkillParser(), Set.of());
        repository.reload(java.util.List.of(new SkillSource(
                "pack",
                "guide/SKILL.md",
                Map.of("guide/SKILL.md", """
                        ---
                        name: guide
                        description: Guide the player
                        ---
                        %s
                        """.formatted(body)))), Set.of());
        return repository;
    }

    @SuppressWarnings("unchecked")
    private static LoadSkillTool.Output success(ToolResult<LoadSkillTool.Output> result) {
        return ((ToolResult.Success<LoadSkillTool.Output>) result).value();
    }

    private static String readRemaining(LoadSkillTool tool, LoadSkillTool.Output current) {
        StringBuilder result = new StringBuilder();
        while (!current.complete()) {
            ToolResult.Success<LoadSkillTool.Output> success = assertInstanceOf(
                    ToolResult.Success.class,
                    tool.invoke(
                            ToolInvocationContext.developmentConsole("test"),
                            new LoadSkillTool.Input("guide", null, current.nextCursor())));
            current = success.value();
            result.append(current.content());
        }
        return result.toString();
    }
}
