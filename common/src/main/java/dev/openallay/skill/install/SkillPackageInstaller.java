package dev.openallay.skill.install;

import dev.openallay.OpenAllayConstants;
import dev.openallay.community.CommunityCatalogManifest;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.skill.SkillDocument;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillSource;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** One validation and atomic-publication path for local and downloaded Skill packages. */
public final class SkillPackageInstaller {
    private final Path managedRoot;
    private final SkillParser parser;
    private final HttpTransport transport;
    private final String minecraftVersion;
    private final String openallayApiVersion;
    private final Set<String> availableTools;
    private final Set<String> installedMods;

    public SkillPackageInstaller(Path managedRoot, SkillParser parser) {
        this(managedRoot, parser, Set.of());
    }

    public SkillPackageInstaller(
            Path managedRoot, SkillParser parser, Set<String> installedMods) {
        this(
                managedRoot,
                parser,
                new JdkHttpTransport(new HttpTransportPolicy(
                        java.time.Duration.ofSeconds(15), "openallay-skill-package-http")),
                "26.2",
                OpenAllayConstants.SKILL_API_VERSION,
                Set.of("openallay:run_javascript", "openallay:load_skill"),
                installedMods);
    }

    public SkillPackageInstaller(
            Path managedRoot,
            SkillParser parser,
            HttpTransport transport,
            String minecraftVersion,
            String openallayApiVersion) {
        this(
                managedRoot,
                parser,
                transport,
                minecraftVersion,
                openallayApiVersion,
                Set.of("openallay:run_javascript", "openallay:load_skill"),
                Set.of());
    }

    public SkillPackageInstaller(
            Path managedRoot,
            SkillParser parser,
            HttpTransport transport,
            String minecraftVersion,
            String openallayApiVersion,
            Set<String> availableTools,
            Set<String> installedMods) {
        this.managedRoot = Objects.requireNonNull(managedRoot, "managedRoot")
                .toAbsolutePath().normalize();
        this.parser = Objects.requireNonNull(parser, "parser");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.minecraftVersion = requireText(minecraftVersion, "minecraftVersion");
        this.openallayApiVersion = requireText(openallayApiVersion, "openallayApiVersion");
        this.availableTools = Set.copyOf(availableTools);
        this.installedMods = Set.copyOf(installedMods);
    }

    public CompletableFuture<ToolResult<InstallResult>> install(
            CommunityCatalogManifest.PackageEntry entry,
            CancellationSignal cancellation) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(cancellation, "cancellation");
        if (!entry.compatibility().minecraft().equals(minecraftVersion)
                || !entry.compatibility().openallayApi().equals(openallayApiVersion)) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "skill_install_incompatible",
                    "The Skill package is not compatible with this OpenAllay game runtime"));
        }
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "skill_install_cancelled", "Skill installation was cancelled"));
        }
        CompletableFuture<ArchiveResponse> response;
        try {
            response = transport.execute(
                    HttpExchangeRequest.newBuilder(entry.archive())
                            .timeout(java.time.Duration.ofSeconds(60))
                            .header("accept", "application/zip, application/octet-stream")
                            .get()
                            .build(),
                    cancellation,
                    (status, headers, body) -> new ArchiveResponse(status, body.readAllBytes()));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(installFailure());
        }
        return response.handle((archive, failure) -> {
            if (cancellation.isCancelled()) {
                return new ToolResult.Failure<InstallResult>(
                        "skill_install_cancelled", "Skill installation was cancelled");
            }
            if (failure != null || archive == null || archive.status() != 200
                    || !sha256(archive.bytes()).equals(entry.sha256())) {
                return installFailure();
            }
            Path temporary = null;
            try {
                Files.createDirectories(managedRoot);
                temporary = Files.createTempFile(managedRoot, ".download-", ".zip");
                Files.write(temporary, archive.bytes());
                ToolResult<InstallResult> imported = importLocal(temporary);
                if (imported instanceof ToolResult.Success<InstallResult> success) {
                    return new ToolResult.Success<>(
                            new InstallResult(success.value().skillName(), entry.source().toString()));
                }
                return installFailure();
            } catch (IOException | RuntimeException invalid) {
                return installFailure();
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignored) {
                        // A hidden, non-discoverable download is harmless if cleanup fails.
                    }
                }
            }
        });
    }

    public synchronized ToolResult<InstallResult> importLocal(Path source) {
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        Path operation = managedRoot.resolve(".install-" + UUID.randomUUID()).normalize();
        try {
            if (managedRoot.startsWith(normalized)) {
                throw new IllegalArgumentException(
                        "Skill import source cannot contain the managed Skill root");
            }
            Files.createDirectories(managedRoot);
            if (!operation.getParent().equals(managedRoot)) {
                throw new IllegalArgumentException("Invalid Skill staging path");
            }
            Files.createDirectory(operation);
            Path extracted = operation.resolve("package");
            Files.createDirectory(extracted);
            if (Files.isSymbolicLink(normalized)) {
                throw new IllegalArgumentException("Skill import cannot be a symbolic link");
            }
            if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                Path packageTarget = extracted.resolve(normalized.getFileName().toString()).normalize();
                Files.createDirectory(packageTarget);
                copyDirectory(normalized, packageTarget);
            } else if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    && normalized.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                            .endsWith(".zip")) {
                extractZip(normalized, extracted);
            } else {
                throw new IllegalArgumentException("Skill import must be a directory or ZIP archive");
            }

            Candidate candidate = candidate(extracted);
            publish(candidate.packageRoot(), candidate.document().metadata().name(), operation);
            return new ToolResult.Success<>(new InstallResult(
                    candidate.document().metadata().name(),
                    candidate.document().metadata().provenance()));
        } catch (RuntimeException | IOException failure) {
            return new ToolResult.Failure<>(
                    "skill_install_failed",
                    "The Skill package could not be validated and installed");
        } finally {
            deleteTree(operation);
        }
    }

    private Candidate candidate(Path extracted) throws IOException {
        java.util.List<Path> entries;
        try (var stream = Files.walk(extracted)) {
            entries = stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && path.getFileName().toString().equals("SKILL.md"))
                    .toList();
        }
        if (entries.size() != 1) {
            throw new IllegalArgumentException("Skill package must contain exactly one SKILL.md");
        }
        Path entry = entries.getFirst();
        Path packageRoot = entry.getParent();
        Path relativeRoot = extracted.relativize(packageRoot);
        if (relativeRoot.getNameCount() > 1) {
            throw new IllegalArgumentException("Skill package has unsupported wrapper directories");
        }
        Map<String, String> encoded = new LinkedHashMap<>();
        try (var stream = Files.walk(packageRoot)) {
            for (Path path : stream.sorted().toList()) {
                if (path.equals(packageRoot)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalArgumentException("Skill packages cannot contain symbolic links");
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String relative = packageRoot.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '/');
                encoded.put(relative, Files.readString(path));
            }
        }
        SkillDocument document = parser.parsePackage(
                "local-import:" + packageRoot.getFileName(),
                encoded,
                SkillSource.Origin.LOCAL);
        if (!availableTools.containsAll(document.metadata().allowedTools())) {
            throw new IllegalArgumentException("Skill declares unavailable Tools");
        }
        if (!installedMods.containsAll(document.metadata().requiredMods())) {
            throw new IllegalArgumentException("Skill requires unavailable mods");
        }
        return new Candidate(packageRoot, document);
    }

    private void publish(Path source, String name, Path operation) throws IOException {
        Path target = managedRoot.resolve(name).normalize();
        if (!target.getParent().equals(managedRoot)) {
            throw new IllegalArgumentException("Skill name escapes managed root");
        }
        Path published = operation.resolve("published");
        Files.move(source, published);
        Path backup = operation.resolve("prior");
        boolean hadPrior = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (hadPrior) {
            Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE);
        }
        try {
            Files.move(published, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failure) {
            if (hadPrior && Files.exists(backup) && !Files.exists(target)) {
                Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE);
            }
            throw failure;
        }
        deleteTree(backup);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Path realSource = source.toRealPath();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(directory)
                        || !directory.toRealPath().startsWith(realSource)) {
                    throw new IOException("Skill directory contains an unsafe path");
                }
                Files.createDirectories(target.resolve(source.relativize(directory)).normalize());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(realSource)) {
                    throw new IOException("Skill directory contains an unsafe file");
                }
                Files.copy(file, target.resolve(source.relativize(file)).normalize());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void extractZip(Path archive, Path target) throws IOException {
        try (InputStream input = Files.newInputStream(archive);
                ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String raw = entry.getName();
                if (raw.isBlank() || raw.startsWith("/") || raw.contains("\\")) {
                    throw new IOException("Unsafe ZIP entry");
                }
                Path destination = target.resolve(raw).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("ZIP entry escapes staging root");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination);
                }
                zip.closeEntry();
            }
        }
    }

    private static void deleteTree(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Staging cleanup is best-effort; no unpublished path is discoverable as a Skill.
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static ToolResult.Failure<InstallResult> installFailure() {
        return new ToolResult.Failure<>(
                "skill_install_failed",
                "The Skill package could not be validated and installed");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    public record InstallResult(String skillName, String provenance) {
        public InstallResult {
            if (skillName == null || skillName.isBlank()
                    || provenance == null || provenance.isBlank()) {
                throw new IllegalArgumentException("Installed Skill identity is required");
            }
        }
    }

    private record Candidate(Path packageRoot, SkillDocument document) {}

    private record ArchiveResponse(int status, byte[] bytes) {
        private ArchiveResponse {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
