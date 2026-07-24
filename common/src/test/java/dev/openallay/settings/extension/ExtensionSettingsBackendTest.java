package dev.openallay.settings.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.install.ExtensionPackageInstaller;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionSettingsBackendTest {
    @TempDir
    Path temporary;

    @Test
    void invalidCatalogRetainsPriorCommunityGenerationAndCompatibilityState() {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment,
                dataModules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()),
                Set.of());
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(environment, temporary.resolve("pending")));

        ToolResult<ExtensionSettingsView> accepted = backend.replaceCatalog("""
                {
                  "schemaVersion": 1,
                  "kind": "extension",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "extensions": [
                    {
                      "id": "community:compatible",
                      "name": "Compatible",
                      "version": "1.0.0",
                      "provider": "Community",
                      "summary": "Compatible package",
                      "loaders": ["fabric"],
                      "minecraftVersionRange": "[26.2,26.3)",
                      "openAllayApiVersionRange": "[0.2,0.3)",
                      "artifact": "https://example.invalid/compatible.jar",
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "modIds": ["compatible_extension"],
                      "source": "community"
                    },
                    {
                      "id": "community:neoforge",
                      "name": "NeoForge only",
                      "version": "1.0.0",
                      "provider": "Community",
                      "summary": "Incompatible package",
                      "loaders": ["neoforge"],
                      "minecraftVersionRange": "[26.2,26.3)",
                      "openAllayApiVersionRange": "[0.2,0.3)",
                      "artifact": "https://example.invalid/neoforge.jar",
                      "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "modIds": ["neoforge_extension"],
                      "source": "community"
                    }
                  ]
                }
                """);
        ToolResult<ExtensionSettingsView> rejected = backend.replaceCatalog("{}");

        assertInstanceOf(ToolResult.Success.class, accepted);
        assertEquals(
                List.of("community:compatible", "community:neoforge", "openallay:core"),
                backend.currentView().extensions().stream()
                        .map(ExtensionSettingsView.Extension::id)
                        .toList());
        assertEquals(
                ExtensionSettingsView.State.COMMUNITY,
                extension(backend.currentView(), "community:compatible").state());
        assertEquals(
                ExtensionSettingsView.State.INCOMPATIBLE,
                extension(backend.currentView(), "community:neoforge").state());
        ToolResult.Failure<ExtensionSettingsView> failure =
                assertInstanceOf(ToolResult.Failure.class, rejected);
        assertEquals("catalog_refresh_failed", failure.code());
        assertEquals(3, backend.currentView().extensions().size());
    }

    private static ExtensionSettingsView.Extension extension(
            ExtensionSettingsView view, String id) {
        return view.extensions().stream()
                .filter(extension -> extension.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
