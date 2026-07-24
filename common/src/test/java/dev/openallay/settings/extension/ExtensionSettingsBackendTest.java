package dev.openallay.settings.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.install.ExtensionPackageInstaller;
import dev.openallay.extension.install.ExtensionPackageManifest;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionSettingsBackendTest {
    @TempDir
    Path temporary;

    @Test
    void productionCatalogUsesThePublishedExtensionRepository() {
        assertEquals(
                "https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json",
                ExtensionSettingsBackend.DEFAULT_CATALOG_URI.toString());
    }

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
                  "schemaVersion": 2,
                  "kind": "extension",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "extensions": [
                    {
                      "id": "community:compatible",
                      "name": "Compatible",
                      "version": "1.0.0",
                      "provider": "Community",
                      "summary": "Compatible package",
                      "minecraftVersionRange": "[26.2,26.3)",
                      "openAllayApiVersionRange": "[0.2,0.3)",
                      "artifacts": [{
                        "loader": "fabric",
                        "artifact": "https://example.invalid/compatible.jar",
                        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "modIds": ["compatible_extension"]
                      }],
                      "source": "community"
                    },
                    {
                      "id": "community:neoforge",
                      "name": "NeoForge only",
                      "version": "1.0.0",
                      "provider": "Community",
                      "summary": "Incompatible package",
                      "minecraftVersionRange": "[26.2,26.3)",
                      "openAllayApiVersionRange": "[0.2,0.3)",
                      "artifacts": [{
                        "loader": "neoforge",
                        "artifact": "https://example.invalid/neoforge.jar",
                        "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "modIds": ["neoforge_extension"]
                      }],
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
        ExtensionSettingsView.PackageInfo incompatiblePackage =
                extension(backend.currentView(), "community:neoforge").packageInfo();
        assertTrue(incompatiblePackage.catalogListed());
        assertEquals("1.0.0", incompatiblePackage.availableVersion());
        assertEquals("", incompatiblePackage.artifact());
        assertEquals("", incompatiblePackage.sha256());
        assertTrue(!incompatiblePackage.installable());
        ToolResult.Failure<ExtensionSettingsView> failure =
                assertInstanceOf(ToolResult.Failure.class, rejected);
        assertEquals("catalog_refresh_failed", failure.code());
        assertEquals(3, backend.currentView().extensions().size());
    }

    @Test
    void activeExtensionExposesUpdateAndLocalImportStagesRestartRequired() throws Exception {
        JavascriptDataModuleRegistry dataModules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionEnvironment environment =
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0");
        OpenAllayExtensionRegistry registry = new OpenAllayExtensionRegistry(
                environment,
                dataModules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of()),
                Set.of());
        registry.register(new OpenAllayExtension() {
            @Override
            public OpenAllayExtensionDescriptor descriptor() {
                return new OpenAllayExtensionDescriptor(
                        "sample:extension",
                        "Sample",
                        "1.0.0",
                        "Provider",
                        "Sample Extension",
                        Set.of("fabric"),
                        "[26.2,26.3)",
                        "[0.2,0.3)",
                        "bundled");
            }

            @Override
            public OpenAllayExtensionContribution contribution() {
                return OpenAllayExtensionContribution.empty();
            }
        });
        ExtensionSettingsBackend backend = new ExtensionSettingsBackend(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(environment, temporary.resolve("mods")));
        byte[] jar = fabricJar("sample_extension");
        Path local = temporary.resolve("sample.jar");
        Files.write(local, jar);
        String catalog = catalog("2.0.0", sha256(jar));

        assertInstanceOf(ToolResult.Success.class, backend.replaceCatalog(catalog));
        ExtensionSettingsView.Extension update =
                extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.ACTIVE, update.state());
        assertTrue(update.packageInfo().updateAvailable());
        assertTrue(update.packageInfo().installable());

        assertInstanceOf(
                ToolResult.Success.class,
                backend.importLocalPackage("sample:extension", local));
        ExtensionSettingsView.Extension staged =
                extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED, staged.state());
        assertEquals("1.0.0", staged.version());
        assertEquals("2.0.0", staged.packageInfo().availableVersion());
        assertTrue(Files.isRegularFile(
                temporary.resolve("mods").resolve(
                        "openallay-extension-sample_extension.jar")));
    }

    @Test
    void localImportIsAvailableWhenCommunityCatalogIsEmpty() throws Exception {
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
                new ExtensionPackageInstaller(environment, temporary.resolve("local-mods")));
        Path local = temporary.resolve("local.jar");
        Files.write(local, fabricJar("sample_extension"));

        assertInstanceOf(ToolResult.Success.class, backend.importLocalPackage(local));

        ExtensionSettingsView.Extension staged =
                extension(backend.currentView(), "sample:extension");
        assertEquals(ExtensionSettingsView.State.RESTART_REQUIRED, staged.state());
        assertEquals("2.0.0", staged.version());
        assertEquals("community", staged.source());
        assertTrue(!staged.packageInfo().catalogListed());
        assertEquals(64, staged.packageInfo().sha256().length());
    }

    private static ExtensionSettingsView.Extension extension(
            ExtensionSettingsView view, String id) {
        return view.extensions().stream()
                .filter(extension -> extension.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static String catalog(String version, String checksum) {
        return """
                {
                  "schemaVersion": 2,
                  "kind": "extension",
                  "generatedAt": "2026-07-25T00:00:00Z",
                  "extensions": [{
                    "id": "sample:extension",
                    "name": "Sample",
                    "version": "%s",
                    "provider": "Provider",
                    "summary": "Sample Extension",
                    "minecraftVersionRange": "[26.2,26.3)",
                    "openAllayApiVersionRange": "[0.2,0.3)",
                    "artifacts": [{
                      "loader": "fabric",
                      "artifact": "https://example.invalid/sample.jar",
                      "sha256": "%s",
                      "modIds": ["sample_extension"]
                    }],
                    "source": "community"
                  }]
                }
                """.formatted(version, checksum);
    }

    private static byte[] fabricJar(String modId) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(ExtensionPackageManifest.JAR_PATH));
            jar.write(("""
                            {
                              "schemaVersion": 1,
                              "id": "sample:extension",
                              "name": "Sample",
                              "version": "2.0.0",
                              "provider": "Provider",
                              "summary": "Sample Extension",
                              "loaders": ["fabric"],
                              "minecraftVersionRange": "[26.2,26.3)",
                              "openAllayApiVersionRange": "[0.2,0.3)",
                              "modIds": ["%s"],
                              "source": "community"
                            }
                            """)
                    .formatted(modId)
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"schemaVersion\":1,\"id\":\"" + modId
                            + "\",\"version\":\"2.0.0\",\"name\":\"Sample\"}")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
