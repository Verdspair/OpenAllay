package dev.openallay.settings.extension;

import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.catalog.ExtensionCatalogClient;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import dev.openallay.extension.catalog.ExtensionCatalogManifest;
import dev.openallay.extension.install.ExtensionInstallResult;
import dev.openallay.extension.install.ExtensionInstallState;
import dev.openallay.extension.install.ExtensionPackageInstaller;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Non-UI owner for last-valid catalogs and restart-required Extension staging. */
public final class ExtensionSettingsBackend implements ClientSettingsService.ExtensionActions {
    public static final URI DEFAULT_CATALOG_URI = URI.create(
            "https://raw.githubusercontent.com/nkanf-dev/OpenAllay-Extensions/main/catalog.json");

    private final OpenAllayExtensionRegistry registry;
    private final JavascriptDataModuleRegistry dataModules;
    private final ExtensionCatalogCodec codec;
    private final ExtensionPackageInstaller installer;
    private final ExtensionCatalogClient catalogClient;
    private ExtensionCatalogManifest catalog =
            new ExtensionCatalogManifest(1, "extension", java.time.Instant.EPOCH, List.of());
    private final Map<String, ExtensionCatalogEntry> staged = new TreeMap<>();
    private Optional<ExtensionSettingsView.Notice> notice = Optional.empty();

    /**
     * Creates the production backend.
     *
     * @param configDirectory OpenAllay's own configuration directory
     * @param managedModsRoot the loader-visible mods directory; managed package names are stable
     */
    public ExtensionSettingsBackend(
            Path configDirectory,
            Path managedModsRoot,
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules) {
        this(
                registry,
                dataModules,
                new ExtensionCatalogCodec(),
                new ExtensionPackageInstaller(registry.environment(), managedModsRoot),
                defaultCatalog(configDirectory));
    }

    /** Constructor retained for deterministic backend/installer contract tests. */
    public ExtensionSettingsBackend(
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules,
            ExtensionCatalogCodec codec,
            ExtensionPackageInstaller installer) {
        this(registry, dataModules, codec, installer, null);
    }

    ExtensionSettingsBackend(
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules,
            ExtensionCatalogCodec codec,
            ExtensionPackageInstaller installer,
            ExtensionCatalogClient catalogClient) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dataModules = Objects.requireNonNull(dataModules, "dataModules");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.installer = Objects.requireNonNull(installer, "installer");
        this.catalogClient = catalogClient;
        if (catalogClient != null && catalogClient.current().isPresent()) {
            catalog = catalogClient.current().orElseThrow();
        }
    }

    @Override
    public synchronized ExtensionSettingsView currentView() {
        ExtensionSettingsView base = ExtensionSettingsView.from(dataModules, registry);
        Map<String, ExtensionSettingsView.Extension> extensions = new TreeMap<>();
        base.extensions().forEach(extension -> extensions.put(extension.id(), extension));

        Map<String, ExtensionCatalogEntry> latest = latestEntries();
        latest.forEach((id, entry) -> {
            ExtensionSettingsView.Extension installed = extensions.get(id);
            ExtensionCatalogEntry pending = staged.get(id);
            if (pending != null) {
                extensions.put(id, staged(installed, pending));
            } else if (installed != null) {
                extensions.put(
                        id,
                        installed(
                                installed,
                                entry,
                                registry.environment()
                                        .incompatibility(entry.descriptor())
                                        .isEmpty()));
            } else {
                extensions.put(id, community(entry));
            }
        });
        staged.forEach((id, entry) -> extensions.computeIfAbsent(
                id, ignored -> staged(null, entry)));

        ExtensionSettingsView.Catalog catalogView = new ExtensionSettingsView.Catalog(
                catalogClient != null || hasCatalog(),
                hasCatalog(),
                hasCatalog() ? Optional.of(catalog.generatedAt()) : Optional.empty(),
                notice);
        return base.withCommunity(List.copyOf(extensions.values()), catalogView);
    }

    /**
     * Test/development injection. Production refreshes use the HTTPS catalog client.
     *
     * <p>A decoding failure retains the prior generation.
     */
    public synchronized ToolResult<ExtensionSettingsView> replaceCatalog(String json) {
        try {
            catalog = codec.decode(json);
            notice = Optional.empty();
            return new ToolResult.Success<>(currentView());
        } catch (RuntimeException failure) {
            notice = Optional.of(new ExtensionSettingsView.Notice(
                    "catalog_refresh_failed",
                    "The Extension community catalog could not be validated"));
            return new ToolResult.Failure<>(
                    "catalog_refresh_failed",
                    "The Extension community catalog could not be validated");
        }
    }

    @Override
    public CompletableFuture<ToolResult<ExtensionSettingsView>> refreshCommunity(
            CancellationSignal cancellation) {
        if (catalogClient == null) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_unavailable",
                    "The Extension community catalog is not configured"));
        }
        return catalogClient.refresh(cancellation).thenApply(result -> {
            synchronized (this) {
                if (result instanceof ToolResult.Success<ExtensionCatalogManifest> success) {
                    catalog = success.value();
                    notice = Optional.empty();
                    return new ToolResult.Success<>(currentView());
                }
                ToolResult.Failure<ExtensionCatalogManifest> failure =
                        (ToolResult.Failure<ExtensionCatalogManifest>) result;
                notice = Optional.of(
                        new ExtensionSettingsView.Notice(failure.code(), failure.message()));
                return new ToolResult.Failure<ExtensionSettingsView>(
                        failure.code(), failure.message());
            }
        });
    }

    @Override
    public synchronized ToolResult<ExtensionSettingsView> importLocalPackage(
            String extensionId, Path source) {
        ExtensionCatalogEntry entry = entry(extensionId);
        if (entry == null) {
            return unavailable();
        }
        return accept(entry, installer.stageLocal(entry, source));
    }

    @Override
    public CompletableFuture<ToolResult<ExtensionSettingsView>> installCommunity(
            String extensionId, CancellationSignal cancellation) {
        ExtensionCatalogEntry entry;
        synchronized (this) {
            entry = entry(extensionId);
        }
        if (entry == null) {
            return CompletableFuture.completedFuture(unavailable());
        }
        return installer.stageDownload(entry, cancellation).thenApply(result -> {
            synchronized (this) {
                return accept(entry, result);
            }
        });
    }

    /** Source-compatible aliases used by the focused backend contracts. */
    public synchronized ToolResult<ExtensionSettingsView> stageLocal(
            String extensionId, Path source) {
        return importLocalPackage(extensionId, source);
    }

    public CompletableFuture<ToolResult<ExtensionSettingsView>> stageDownload(
            String extensionId, CancellationSignal cancellation) {
        return installCommunity(extensionId, cancellation);
    }

    private ToolResult<ExtensionSettingsView> accept(
            ExtensionCatalogEntry entry, ExtensionInstallResult result) {
        if (result.state() != ExtensionInstallState.RESTART_REQUIRED) {
            notice = Optional.of(new ExtensionSettingsView.Notice(
                    result.diagnostic().isBlank()
                            ? "extension_install_failed"
                            : result.diagnostic(),
                    "The Extension package could not be validated and staged"));
            return new ToolResult.Failure<>(
                    "extension_install_failed",
                    "The Extension package could not be validated and staged");
        }
        staged.put(entry.id(), entry);
        notice = Optional.empty();
        return new ToolResult.Success<>(currentView());
    }

    private Map<String, ExtensionCatalogEntry> latestEntries() {
        Map<String, ExtensionCatalogEntry> latest = new TreeMap<>();
        for (ExtensionCatalogEntry entry : catalog.extensions()) {
            ExtensionCatalogEntry prior = latest.get(entry.id());
            if (prior == null || compareVersions(entry.version(), prior.version()) > 0) {
                latest.put(entry.id(), entry);
            }
        }
        return latest;
    }

    private ExtensionCatalogEntry entry(String id) {
        return latestEntries().get(id);
    }

    private ExtensionSettingsView.Extension community(ExtensionCatalogEntry entry) {
        String incompatibility = registry.environment().incompatibility(entry.descriptor());
        return extension(
                entry,
                incompatibility.isEmpty()
                        ? ExtensionSettingsView.State.COMMUNITY
                        : ExtensionSettingsView.State.INCOMPATIBLE,
                incompatibility,
                "",
                incompatibility.isEmpty(),
                false,
                emptyContributions());
    }

    private static ExtensionSettingsView.Extension installed(
            ExtensionSettingsView.Extension installed,
            ExtensionCatalogEntry available,
            boolean compatible) {
        boolean update = compatible
                && compareVersions(available.version(), installed.version()) > 0;
        return new ExtensionSettingsView.Extension(
                installed.id(),
                installed.name(),
                installed.version(),
                installed.provider(),
                installed.summary(),
                installed.state(),
                installed.loaders(),
                installed.minecraftVersionRange(),
                installed.openAllayApiVersionRange(),
                installed.source(),
                installed.contributions(),
                installed.diagnostic(),
                packageInfo(available, update, update));
    }

    private static ExtensionSettingsView.Extension staged(
            ExtensionSettingsView.Extension installed,
            ExtensionCatalogEntry entry) {
        ExtensionSettingsView.Contributions contributions = installed == null
                ? emptyContributions()
                : installed.contributions();
        return extension(
                entry,
                ExtensionSettingsView.State.RESTART_REQUIRED,
                "restart_required",
                installed == null ? "" : installed.version(),
                false,
                false,
                contributions);
    }

    private static ExtensionSettingsView.Extension extension(
            ExtensionCatalogEntry entry,
            ExtensionSettingsView.State state,
            String diagnostic,
            String installedVersion,
            boolean installable,
            boolean updateAvailable,
            ExtensionSettingsView.Contributions contributions) {
        String displayVersion =
                installedVersion.isBlank() ? entry.version() : installedVersion;
        return new ExtensionSettingsView.Extension(
                entry.id(),
                entry.name(),
                displayVersion,
                entry.provider(),
                entry.summary(),
                state,
                entry.loaders().stream().toList(),
                entry.minecraftVersionRange(),
                entry.openAllayApiVersionRange(),
                entry.source(),
                contributions,
                diagnostic,
                packageInfo(entry, updateAvailable, installable));
    }

    private static ExtensionSettingsView.PackageInfo packageInfo(
            ExtensionCatalogEntry entry, boolean updateAvailable, boolean installable) {
        return new ExtensionSettingsView.PackageInfo(
                true,
                entry.version(),
                entry.artifact().toString(),
                entry.sha256(),
                updateAvailable,
                installable);
    }

    private static ExtensionSettingsView.Contributions emptyContributions() {
        return new ExtensionSettingsView.Contributions(
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private boolean hasCatalog() {
        return !catalog.generatedAt().equals(java.time.Instant.EPOCH);
    }

    private static ExtensionCatalogClient defaultCatalog(Path configDirectory) {
        Path config = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath()
                .normalize();
        return new ExtensionCatalogClient(
                DEFAULT_CATALOG_URI,
                config.resolve("catalogs").resolve("extensions.json"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30));
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            String a = index < leftParts.length ? leftParts[index] : "0";
            String b = index < rightParts.length ? rightParts[index] : "0";
            int comparison;
            if (a.chars().allMatch(Character::isDigit)
                    && b.chars().allMatch(Character::isDigit)) {
                comparison =
                        new java.math.BigInteger(a).compareTo(new java.math.BigInteger(b));
            } else {
                comparison = a.compareTo(b);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static ToolResult<ExtensionSettingsView> unavailable() {
        return new ToolResult.Failure<>(
                "extension_install_failed", "The Extension package is unavailable");
    }
}
