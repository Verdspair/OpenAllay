package dev.openallay.settings.extension;

import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.catalog.ExtensionCatalogCodec;
import dev.openallay.extension.catalog.ExtensionCatalogEntry;
import dev.openallay.extension.catalog.ExtensionCatalogManifest;
import dev.openallay.extension.install.ExtensionInstallResult;
import dev.openallay.extension.install.ExtensionInstallState;
import dev.openallay.extension.install.ExtensionPackageInstaller;
import dev.openallay.model.CancellationSignal;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.tool.ToolResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** Non-UI owner for catalog generations and restart-required Extension package staging. */
public final class ExtensionSettingsBackend {
    private final OpenAllayExtensionRegistry registry;
    private final JavascriptDataModuleRegistry dataModules;
    private final ExtensionCatalogCodec codec;
    private final ExtensionPackageInstaller installer;
    private ExtensionCatalogManifest catalog =
            new ExtensionCatalogManifest(1, "extension", java.time.Instant.EPOCH, List.of());
    private final Map<String, ExtensionCatalogEntry> staged = new TreeMap<>();

    public ExtensionSettingsBackend(
            OpenAllayExtensionRegistry registry,
            JavascriptDataModuleRegistry dataModules,
            ExtensionCatalogCodec codec,
            ExtensionPackageInstaller installer) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.dataModules = Objects.requireNonNull(dataModules, "dataModules");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.installer = Objects.requireNonNull(installer, "installer");
    }

    public synchronized ExtensionSettingsView currentView() {
        ExtensionSettingsView base = ExtensionSettingsView.from(dataModules, registry);
        Map<String, ExtensionSettingsView.Extension> extensions = new TreeMap<>();
        base.extensions().forEach(extension -> extensions.put(extension.id(), extension));
        for (ExtensionCatalogEntry entry : catalog.extensions()) {
            if (extensions.containsKey(entry.id())) {
                continue;
            }
            extensions.put(entry.id(), community(entry));
        }
        staged.forEach((id, entry) -> extensions.put(id, staged(entry)));
        return base.withExtensions(List.copyOf(extensions.values()));
    }

    public synchronized ToolResult<ExtensionSettingsView> replaceCatalog(String json) {
        try {
            ExtensionCatalogManifest candidate = codec.decode(json);
            catalog = candidate;
            return new ToolResult.Success<>(currentView());
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "catalog_refresh_failed",
                    "The Extension community catalog could not be validated");
        }
    }

    public synchronized ToolResult<ExtensionSettingsView> stageLocal(
            String extensionId, Path source) {
        ExtensionCatalogEntry entry = entry(extensionId);
        if (entry == null) {
            return unavailable();
        }
        ExtensionInstallResult result = installer.stageLocal(entry, source);
        return accept(entry, result);
    }

    public CompletableFuture<ToolResult<ExtensionSettingsView>> stageDownload(
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

    private ToolResult<ExtensionSettingsView> accept(
            ExtensionCatalogEntry entry, ExtensionInstallResult result) {
        if (result.state() != ExtensionInstallState.RESTART_REQUIRED) {
            return new ToolResult.Failure<>(
                    "extension_install_failed",
                    "The Extension package could not be validated and staged");
        }
        staged.put(entry.id(), entry);
        return new ToolResult.Success<>(currentView());
    }

    private ExtensionCatalogEntry entry(String id) {
        return catalog.extensions().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private ExtensionSettingsView.Extension community(ExtensionCatalogEntry entry) {
        String incompatibility = registry.environment().incompatibility(entry.descriptor());
        return extension(
                entry,
                incompatibility.isEmpty()
                        ? ExtensionSettingsView.State.COMMUNITY
                        : ExtensionSettingsView.State.INCOMPATIBLE,
                incompatibility);
    }

    private static ExtensionSettingsView.Extension staged(ExtensionCatalogEntry entry) {
        return extension(entry, ExtensionSettingsView.State.RESTART_REQUIRED, "restart_required");
    }

    private static ExtensionSettingsView.Extension extension(
            ExtensionCatalogEntry entry,
            ExtensionSettingsView.State state,
            String diagnostic) {
        return new ExtensionSettingsView.Extension(
                entry.id(),
                entry.name(),
                entry.version(),
                entry.provider(),
                entry.summary(),
                state,
                entry.loaders().stream().toList(),
                entry.minecraftVersionRange(),
                entry.openAllayApiVersionRange(),
                entry.source(),
                new ExtensionSettingsView.Contributions(
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                diagnostic);
    }

    private static ToolResult<ExtensionSettingsView> unavailable() {
        return new ToolResult.Failure<>(
                "extension_install_failed", "The Extension package is unavailable");
    }
}
