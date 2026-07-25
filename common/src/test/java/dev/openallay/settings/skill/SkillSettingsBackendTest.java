package dev.openallay.settings.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolResult;
import dev.openallay.community.CommunityCatalogClient;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.skill.FilesystemSkillLoader;
import dev.openallay.skill.install.SkillPackageInstaller;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SkillSettingsBackendTest {
    @TempDir Path temporaryDirectory;

    @Test
    void savingBundledSkillCreatesValidatedLocalOverride() throws IOException {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);

        SkillSettingsView initial = backend.currentView();
        assertEquals(SkillSource.Origin.BUNDLED, initial.find("guide").orElseThrow().origin());
        assertTrue(initial.find("guide").orElseThrow().createsOverrideOnSave());

        SkillSettingsView saved = success(backend.saveOverride("guide", skill("guide", "local body")));

        SkillSettingsView.Skill guide = saved.find("guide").orElseThrow();
        assertEquals(SkillSource.Origin.LOCAL, guide.origin());
        assertEquals("local body", guide.body());
        assertTrue(guide.overridePresent());
        assertEquals("local body", repository.find("guide").orElseThrow().instructions());
        assertEquals(skill("guide", "local body"), Files.readString(root.resolve("guide/SKILL.md")));
    }

    @Test
    void invalidEditLeavesPriorFileAndPublishedDocumentUnchanged() throws IOException {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        success(backend.saveOverride("guide", skill("guide", "valid local")));
        String before = Files.readString(root.resolve("guide/SKILL.md"));

        failure(backend.saveOverride("guide", "not frontmatter"));

        assertEquals(before, Files.readString(root.resolve("guide/SKILL.md")));
        assertEquals("valid local", repository.find("guide").orElseThrow().instructions());
        assertEquals("valid local", backend.currentView().find("guide").orElseThrow().body());
    }

    @Test
    void dependencyRejectedEditRollsBackAndExternalInvalidityRetainsLastValidDiagnostic()
            throws IOException {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        success(backend.saveOverride("guide", skill("guide", "valid local")));
        String before = Files.readString(root.resolve("guide/SKILL.md"));

        String unavailableTool = before.replace(
                "allowed-tools: \"\"", "allowed-tools: \"unknown:tool\"");
        ToolResult.Failure<SkillSettingsView> rejected = failure(
                backend.saveOverride("guide", unavailableTool));
        assertEquals("skill_override_dependency_unavailable", rejected.code());
        assertEquals(before, Files.readString(root.resolve("guide/SKILL.md")));

        Files.writeString(root.resolve("guide/SKILL.md"), "externally broken");
        SkillSettingsView retained = success(backend.reloadSkills());
        assertEquals("valid local", retained.find("guide").orElseThrow().body());
        assertEquals(before, retained.find("guide").orElseThrow().markdown());
        assertEquals("skill_validation_failed", retained.diagnostics().getFirst().code());
    }

    @Test
    void deletingLocalOverrideRestoresReadOnlyBundledDocument() {
        Path root = temporaryDirectory.resolve("skills");
        SkillRepository repository = repository();
        SkillSettingsBackend backend = backend(root, repository);
        success(backend.saveOverride("guide", skill("guide", "local")));

        SkillSettingsView restored = success(backend.deleteOverride("guide"));

        SkillSettingsView.Skill guide = restored.find("guide").orElseThrow();
        assertEquals(SkillSource.Origin.BUNDLED, guide.origin());
        assertEquals("bundled body", guide.body());
        assertFalse(guide.overridePresent());
        assertFalse(Files.exists(root.resolve("guide")));
    }

    @Test
    void restartLoadsPersistedOverrideFromExternalMarkdownPackage() {
        Path root = temporaryDirectory.resolve("skills");
        SkillSettingsBackend first = backend(root, repository());
        success(first.saveOverride("guide", skill("guide", "survives restart")));

        SkillRepository restartedRepository = repository();
        SkillSettingsBackend restarted = backend(root, restartedRepository);

        SkillSettingsView.Skill guide = restarted.currentView().find("guide").orElseThrow();
        assertEquals(SkillSource.Origin.LOCAL, guide.origin());
        assertEquals("survives restart", guide.body());
        assertTrue(guide.overridePresent());
        assertEquals("survives restart", restartedRepository.find("guide").orElseThrow().instructions());
    }

    @Test
    void backendListsCachedCommunityAndImportsThroughManagedInstaller() throws Exception {
        Path root = temporaryDirectory.resolve("skills");
        Path cache = temporaryDirectory.resolve("catalogs/skills.json");
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, """
                {"schemaVersion":2,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[{"id":"demo","displayName":"Demo Skill",
                 "description":"A test community Skill.","publisher":"Test Publisher",
                 "version":"1.0.0",
                 "archive":"https://example.test/demo.zip",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/demo"}]}
                """);
        HttpTransport unused = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                return CompletableFuture.failedFuture(new IOException("offline"));
            }
        };
        CommunityCatalogClient catalog = new CommunityCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                unused,
                Duration.ofSeconds(5));
        SkillSettingsBackend backend = new SkillSettingsBackend(
                root,
                repository(),
                new SkillParser(),
                List.of(bundled()),
                Set.of(),
                new FilesystemSkillLoader(),
                catalog,
                new SkillPackageInstaller(root, new SkillParser()));
        Path imported = temporaryDirectory.resolve("demo");
        Files.createDirectories(imported);
        Files.writeString(imported.resolve("SKILL.md"), skill("demo", "community body"));

        assertTrue(backend.currentCommunityView().available());
        assertFalse(backend.currentCommunityView().packages().getFirst().installed());
        SkillCommunityView after = successCommunity(backend.importLocalPackage(imported));
        assertTrue(after.packages().getFirst().installed());

        ToolResult<SkillCommunityView> failed =
                backend.refreshCommunity(new CancellationSignal()).join();
        assertEquals("catalog_refresh_failed",
                assertInstanceOf(ToolResult.Failure.class, failed).code());
        assertTrue(backend.currentCommunityView().available());
    }

    private SkillSettingsBackend backend(Path root, SkillRepository repository) {
        return new SkillSettingsBackend(
                root,
                repository,
                new SkillParser(),
                List.of(bundled()),
                Set.of());
    }

    private static SkillRepository repository() {
        return new SkillRepository(new SkillParser(), Set.of());
    }

    private static SkillSource bundled() {
        return new SkillSource(
                "openallay:bundled",
                "guide/SKILL.md",
                Map.of(
                        "guide/SKILL.md", skill("guide", "bundled body"),
                        "guide/references/facts.md", "Ground facts."),
                SkillSource.Origin.BUNDLED);
    }

    private static String skill(String name, String body) {
        return """
                ---
                name: %s
                description: Guide answers
                allowed-tools: ""
                ---
                %s
                """.formatted(name, body);
    }

    @SuppressWarnings("unchecked")
    private static SkillSettingsView success(ToolResult<SkillSettingsView> result) {
        return ((ToolResult.Success<SkillSettingsView>)
                        assertInstanceOf(ToolResult.Success.class, result))
                .value();
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<SkillSettingsView> failure(
            ToolResult<SkillSettingsView> result) {
        return (ToolResult.Failure<SkillSettingsView>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }

    @SuppressWarnings("unchecked")
    private static SkillCommunityView successCommunity(ToolResult<SkillCommunityView> result) {
        return ((ToolResult.Success<SkillCommunityView>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }
}
