package dev.openallay.skill.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.skill.SkillParser;
import dev.openallay.community.CommunityCatalogManifest;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpResponseHeaders;
import dev.openallay.net.HttpTransport;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SkillPackageInstallerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void importsDirectoryAndZipThroughSameValidatedAtomicPublication() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser());
        Path source = temporaryDirectory.resolve("downloaded-skill");
        Files.createDirectories(source.resolve("references"));
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "first"));
        Files.writeString(source.resolve("references/facts.md"), "facts");

        assertEquals("demo", success(installer.importLocal(source)).skillName());
        assertEquals("first", Files.readString(root.resolve("demo/SKILL.md"))
                .lines().reduce((left, right) -> right).orElseThrow());

        Path archive = temporaryDirectory.resolve("demo.zip");
        zip(archive, "SKILL.md", skill("demo", "second"));
        assertEquals("demo", success(installer.importLocal(archive)).skillName());
        assertTrue(Files.readString(root.resolve("demo/SKILL.md")).endsWith("second\n"));
    }

    @Test
    void invalidReplacementAndZipSlipRetainPriorPackage() throws Exception {
        Path root = temporaryDirectory.resolve("managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(root, new SkillParser());
        Path source = temporaryDirectory.resolve("demo");
        Files.createDirectories(source);
        Files.writeString(source.resolve("SKILL.md"), skill("demo", "valid"));
        success(installer.importLocal(source));

        Path broken = temporaryDirectory.resolve("broken.zip");
        zip(broken, "demo/SKILL.md", "not a skill");
        assertEquals("skill_install_failed",
                assertInstanceOf(ToolResult.Failure.class, installer.importLocal(broken)).code());
        assertTrue(Files.readString(root.resolve("demo/SKILL.md")).endsWith("valid\n"));

        Path slip = temporaryDirectory.resolve("slip.zip");
        zip(slip, "../escape", "bad");
        assertInstanceOf(ToolResult.Failure.class, installer.importLocal(slip));
        assertFalse(Files.exists(temporaryDirectory.resolve("escape")));
    }

    @Test
    void remoteInstallVerifiesChecksumAndCompatibilityBeforePublishing() throws Exception {
        byte[] archive = zipBytes("demo/SKILL.md", skill("demo", "remote"));
        HttpTransport transport = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                try {
                    return CompletableFuture.completedFuture(decoder.decode(
                            200,
                            new HttpResponseHeaders(Map.of()),
                            new java.io.ByteArrayInputStream(archive)));
                } catch (IOException failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        };
        Path root = temporaryDirectory.resolve("remote-managed");
        SkillPackageInstaller installer = new SkillPackageInstaller(
                root, new SkillParser(), transport, "26.2", "0.2");

        assertEquals("demo", success(installer.install(
                entry(sha256(archive), "26.2"), new CancellationSignal()).join()).skillName());
        assertEquals("skill_install_incompatible", assertInstanceOf(
                ToolResult.Failure.class,
                installer.install(entry(sha256(archive), "1.21.1"), new CancellationSignal()).join())
                .code());
        assertEquals("skill_install_failed", assertInstanceOf(
                ToolResult.Failure.class,
                installer.install(entry("0".repeat(64), "26.2"), new CancellationSignal()).join())
                .code());
        assertTrue(Files.readString(root.resolve("demo/SKILL.md")).endsWith("remote\n"));
    }

    private static SkillPackageInstaller.InstallResult success(
            ToolResult<SkillPackageInstaller.InstallResult> result) {
        return ((ToolResult.Success<SkillPackageInstaller.InstallResult>)
                assertInstanceOf(ToolResult.Success.class, result)).value();
    }

    private static void zip(Path archive, String name, String contents) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(name));
            output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    private static byte[] zipBytes(String name, String contents) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(name));
            output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static CommunityCatalogManifest.PackageEntry entry(
            String checksum, String minecraft) {
        return new CommunityCatalogManifest.PackageEntry(
                "demo",
                "1.0.0",
                URI.create("https://example.test/demo.zip"),
                checksum,
                new CommunityCatalogManifest.Compatibility(minecraft, "0.2"),
                URI.create("https://example.test/demo"));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String skill(String name, String body) {
        return """
                ---
                name: %s
                description: Demo
                metadata:
                  openallay/version: "1.0.0"
                allowed-tools: ""
                ---
                %s
                """.formatted(name, body);
    }
}
