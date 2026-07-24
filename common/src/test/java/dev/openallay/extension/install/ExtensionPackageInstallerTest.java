package dev.openallay.extension.install;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.catalog.ExtensionCatalogArtifact;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionPackageInstallerTest {
    @TempDir
    Path temporary;

    @Test
    void validatesAndAtomicallyStagesFabricJarForNextRestart() throws Exception {
        byte[] jar = fabricJar("sample_extension");
        Path source = temporary.resolve("sample.jar");
        Files.write(source, jar);
        Path staging = temporary.resolve("pending");
        ExtensionPackageInstaller installer = new ExtensionPackageInstaller(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                staging);

        ExtensionInstallResult result =
                installer.stageLocal(entry(sha256(jar)), source);

        assertEquals(ExtensionInstallState.RESTART_REQUIRED, result.state());
        assertEquals("sample:extension", result.extensionId());
        assertTrue(result.stagedArtifact().isPresent());
        assertEquals(
                "openallay-extension-sample_extension.jar",
                result.stagedArtifact().orElseThrow().getFileName().toString());
        assertArrayEquals(jar, Files.readAllBytes(result.stagedArtifact().orElseThrow()));
        assertFalse(Files.exists(staging.resolve(".sample_extension-1.0.0.jar.tmp")));
    }

    @Test
    void importsLocalJarWithoutACommunityCatalogEntry() throws Exception {
        byte[] jar = fabricJar("local_extension");
        Path source = temporary.resolve("local.jar");
        Files.write(source, jar);
        ExtensionPackageInstaller installer = new ExtensionPackageInstaller(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                temporary.resolve("mods"));

        ExtensionInstallResult result = installer.stageLocal(source);

        assertEquals(ExtensionInstallState.RESTART_REQUIRED, result.state());
        assertEquals("sample:extension", result.extensionId());
        assertEquals("sample:extension", result.manifest()
                .orElseThrow()
                .descriptor()
                .id());
        assertEquals(sha256(jar), result.sha256());
        assertEquals(
                "openallay-extension-sample_extension.jar",
                result.stagedArtifact().orElseThrow().getFileName().toString());
    }

    @Test
    void selectsTheCurrentLoadersArtifactFromOneCatalogVersion() throws Exception {
        byte[] fabric = fabricJar("sample_extension");
        byte[] neoforge = neoforgeJar("sample_extension");
        Path fabricSource = temporary.resolve("fabric.jar");
        Path neoForgeSource = temporary.resolve("neoforge.jar");
        Files.write(fabricSource, fabric);
        Files.write(neoForgeSource, neoforge);
        ExtensionCatalogEntry catalog = new ExtensionCatalogEntry(
                "sample:extension",
                "Sample",
                "1.0.0",
                "Provider",
                "Sample extension",
                "[26.2,26.3)",
                "[0.2,0.3)",
                java.util.List.of(
                        new ExtensionCatalogArtifact(
                                "fabric",
                                "https://example.invalid/sample-fabric.jar",
                                sha256(fabric),
                                Set.of("sample_extension")),
                        new ExtensionCatalogArtifact(
                                "neoforge",
                                "https://example.invalid/sample-neoforge.jar",
                                sha256(neoforge),
                                Set.of("sample_extension"))),
                "community");

        ExtensionInstallResult fabricResult = new ExtensionPackageInstaller(
                        new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                        temporary.resolve("fabric-mods"))
                .stageLocal(catalog, fabricSource);
        ExtensionInstallResult neoForgeResult = new ExtensionPackageInstaller(
                        new OpenAllayExtensionEnvironment("neoforge", "26.2", "0.2.0"),
                        temporary.resolve("neoforge-mods"))
                .stageLocal(catalog, neoForgeSource);

        assertEquals(ExtensionInstallState.RESTART_REQUIRED, fabricResult.state());
        assertEquals(ExtensionInstallState.RESTART_REQUIRED, neoForgeResult.state());
        assertEquals(
                Set.of("fabric"),
                fabricResult.manifest().orElseThrow().descriptor().loaders());
        assertEquals(
                Set.of("neoforge"),
                neoForgeResult.manifest().orElseThrow().descriptor().loaders());
    }

    @Test
    void rejectsChecksumMetadataAndCompatibilityWithoutPublishingCandidate() throws Exception {
        byte[] wrongMod = fabricJar("other_mod", "sample_extension");
        Path source = temporary.resolve("wrong.jar");
        Files.write(source, wrongMod);
        Path staging = temporary.resolve("pending");
        ExtensionPackageInstaller installer = new ExtensionPackageInstaller(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                staging);

        ExtensionInstallResult checksum =
                installer.stageLocal(entry("0".repeat(64)), source);
        ExtensionInstallResult metadata =
                installer.stageLocal(entry(sha256(wrongMod)), source);
        ExtensionInstallResult loader = installer.stageLocal(
                new ExtensionCatalogEntry(
                        "sample:extension",
                        "Sample",
                        "1.0.0",
                        "Provider",
                        "Sample extension",
                        "[26.2,26.3)",
                        "[0.2,0.3)",
                        java.util.List.of(new ExtensionCatalogArtifact(
                                "neoforge",
                                "https://example.invalid/sample.jar",
                                sha256(wrongMod),
                                Set.of("sample_extension"))),
                        "community"),
                source);

        assertEquals("checksum_mismatch", checksum.diagnostic());
        assertEquals("mod_metadata_mismatch", metadata.diagnostic());
        assertEquals("incompatible_loader", loader.diagnostic());
        assertFalse(Files.exists(staging));
    }

    private static ExtensionCatalogEntry entry(String checksum) {
        return new ExtensionCatalogEntry(
                "sample:extension",
                "Sample",
                "1.0.0",
                "Provider",
                "Sample extension",
                "[26.2,26.3)",
                "[0.2,0.3)",
                java.util.List.of(new ExtensionCatalogArtifact(
                        "fabric",
                        "https://example.invalid/sample.jar",
                        checksum,
                        Set.of("sample_extension"))),
                "community");
    }

    private static byte[] fabricJar(String modId) throws Exception {
        return fabricJar(modId, modId);
    }

    private static byte[] fabricJar(String loaderModId, String manifestModId)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(ExtensionPackageManifest.JAR_PATH));
            jar.write(packageManifest(manifestModId).getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"schemaVersion\":1,\"id\":\"" + loaderModId
                            + "\",\"version\":\"1.0.0\",\"name\":\"Sample\"}")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return output.toByteArray();
    }

    private static String packageManifest(String modId) {
        return packageManifest("fabric", modId);
    }

    private static String packageManifest(String loader, String modId) {
        return """
                {
                  "schemaVersion": 1,
                  "id": "sample:extension",
                  "name": "Sample",
                  "version": "1.0.0",
                  "provider": "Provider",
                  "summary": "Sample extension",
                  "loaders": ["%s"],
                  "minecraftVersionRange": "[26.2,26.3)",
                  "openAllayApiVersionRange": "[0.2,0.3)",
                  "modIds": ["%s"],
                  "source": "community"
                }
                """.formatted(loader, modId);
    }

    private static byte[] neoforgeJar(String modId) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry(ExtensionPackageManifest.JAR_PATH));
            jar.write(packageManifest("neoforge", modId)
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));
            jar.write(("modLoader=\"javafml\"\n"
                            + "loaderVersion=\"[1,)\"\n"
                            + "[[mods]]\n"
                            + "modId=\"" + modId + "\"\n"
                            + "version=\"1.0.0\"\n")
                    .getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
