package dev.openallay.extension.install;

import com.google.gson.JsonParser;
import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarInputStream;

/** Validates an Extension JAR and atomically stages it for the next loader restart. */
public final class ExtensionPackageInstaller {
    private final OpenAllayExtensionEnvironment environment;
    private final Path stagingRoot;
    private final HttpTransport transport;

    public ExtensionPackageInstaller(
            OpenAllayExtensionEnvironment environment, Path stagingRoot) {
        this(
                environment,
                stagingRoot,
                new JdkHttpTransport(new HttpTransportPolicy(
                        java.time.Duration.ofSeconds(15),
                        "openallay-extension-package-http")));
    }

    public ExtensionPackageInstaller(
            OpenAllayExtensionEnvironment environment,
            Path stagingRoot,
            HttpTransport transport) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.stagingRoot = Objects.requireNonNull(stagingRoot, "stagingRoot")
                .toAbsolutePath()
                .normalize();
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public synchronized ExtensionInstallResult stageLocal(
            ExtensionCatalogEntry entry, Path source) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return failed(entry.id(), "extension_install_failed");
            }
            return stage(entry, Files.readAllBytes(normalized));
        } catch (IOException | RuntimeException failure) {
            return failed(entry.id(), "extension_install_failed");
        }
    }

    public CompletableFuture<ExtensionInstallResult> stageDownload(
            ExtensionCatalogEntry entry, CancellationSignal cancellation) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(cancellation, "cancellation");
        String incompatibility = environment.incompatibility(entry.descriptor());
        if (!incompatibility.isEmpty()) {
            return CompletableFuture.completedFuture(failed(entry.id(), incompatibility));
        }
        return transport.execute(
                        HttpExchangeRequest.newBuilder(entry.artifact())
                                .timeout(java.time.Duration.ofSeconds(60))
                                .header("accept", "application/java-archive, application/octet-stream")
                                .get()
                                .build(),
                        cancellation,
                        (status, headers, body) ->
                                new Download(status, body.readAllBytes()))
                .handle((download, failure) -> {
                    if (failure != null || download == null || download.status() != 200) {
                        return failed(entry.id(), "extension_install_failed");
                    }
                    synchronized (this) {
                        return stage(entry, download.bytes());
                    }
                });
    }

    private ExtensionInstallResult stage(ExtensionCatalogEntry entry, byte[] bytes) {
        String incompatibility = environment.incompatibility(entry.descriptor());
        if (!incompatibility.isEmpty()) {
            return failed(entry.id(), incompatibility);
        }
        if (!sha256(bytes).equals(entry.sha256())) {
            return failed(entry.id(), "checksum_mismatch");
        }
        Set<String> declaredModIds;
        try {
            declaredModIds = declaredModIds(bytes);
        } catch (RuntimeException failure) {
            return failed(entry.id(), "mod_metadata_invalid");
        }
        if (!declaredModIds.containsAll(entry.modIds())) {
            return failed(entry.id(), "mod_metadata_mismatch");
        }
        Path temporary = null;
        try {
            Files.createDirectories(stagingRoot);
            temporary = Files.createTempFile(stagingRoot, ".extension-", ".jar.tmp");
            Files.write(temporary, bytes);
            String fileName = entry.id().replace(':', '_').replace('/', '_')
                    + "-" + entry.version() + ".jar";
            Path target = stagingRoot.resolve(fileName).normalize();
            if (!target.getParent().equals(stagingRoot)) {
                throw new IllegalArgumentException("Extension staging path escapes its root");
            }
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            return new ExtensionInstallResult(
                    entry.id(),
                    ExtensionInstallState.RESTART_REQUIRED,
                    "restart_required",
                    Optional.of(target));
        } catch (IOException | RuntimeException failure) {
            return failed(entry.id(), "extension_install_failed");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Hidden staging files are never loader-visible.
                }
            }
        }
    }

    private Set<String> declaredModIds(byte[] bytes) {
        Set<String> modIds = new HashSet<>();
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().equals("fabric.mod.json")) {
                    var root = JsonParser.parseReader(
                                    new java.io.InputStreamReader(
                                            jar, java.nio.charset.StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    if (root.has("id") && root.get("id").isJsonPrimitive()) {
                        modIds.add(root.get("id").getAsString());
                    }
                } else if (entry.getName().equals("META-INF/neoforge.mods.toml")
                        || entry.getName().equals("META-INF/mods.toml")) {
                    String metadata = new String(
                            jar.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("(?m)^\\s*modId\\s*=\\s*[\"']([a-z0-9_.-]+)[\"']")
                            .matcher(metadata);
                    while (matcher.find()) {
                        modIds.add(matcher.group(1));
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Invalid Extension JAR metadata", failure);
        }
        if (modIds.isEmpty()) {
            throw new IllegalArgumentException("Extension JAR has no loader metadata");
        }
        return Set.copyOf(modIds);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ExtensionInstallResult failed(String extensionId, String diagnostic) {
        return new ExtensionInstallResult(
                extensionId, ExtensionInstallState.FAILED, diagnostic, Optional.empty());
    }

    private record Download(int status, byte[] bytes) {
        private Download {
            bytes = bytes.clone();
        }
    }
}
