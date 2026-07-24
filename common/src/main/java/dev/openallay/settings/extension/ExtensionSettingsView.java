package dev.openallay.settings.extension;

import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.schema.HostRootDescriptor;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.script.schema.HostSchemaCatalog;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Descriptor-only extension state for Settings.
 *
 * <p>Constructing this view never captures a game root or an extension value.
 */
public record ExtensionSettingsView(
        List<Root> roots,
        List<String> bundledModules,
        List<Adapter> adapters) {
    public ExtensionSettingsView {
        roots = List.copyOf(roots);
        bundledModules = List.copyOf(bundledModules);
        adapters = List.copyOf(adapters);
    }

    public static ExtensionSettingsView from(JavascriptDataModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        HostSchemaCatalog catalog = MinecraftAgentHostGraph.declaredOnlyCatalog();
        List<Root> roots = catalog.list().stream()
                .map(summary -> Root.from(catalog, summary))
                .sorted(Comparator.comparing(Root::name))
                .toList();
        List<String> modules = JavascriptModuleCatalog.bundledIds().stream().sorted().toList();
        List<Adapter> adapters = registry.descriptors().stream()
                .map(Adapter::from)
                .sorted(Comparator.comparing(Adapter::id))
                .toList();
        return new ExtensionSettingsView(roots, modules, adapters);
    }

    public static ExtensionSettingsView defaults() {
        return from(new JavascriptDataModuleRegistry());
    }

    public record Root(
            String name,
            HostRootDescriptor.Availability availability,
            String provider,
            String summary,
            String evidenceOwner,
            HostSchema schema) {
        public Root {
            name = require(name, "name");
            Objects.requireNonNull(availability, "availability");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            evidenceOwner = require(evidenceOwner, "evidenceOwner");
            Objects.requireNonNull(schema, "schema");
        }

        private static Root from(
                HostSchemaCatalog catalog, HostSchemaCatalog.RootSummary summary) {
            HostSchema schema = catalog.describe(summary.name())
                    .orElseThrow()
                    .schema();
            return new Root(
                    summary.name(),
                    summary.availability(),
                    summary.provider(),
                    summary.summary(),
                    summary.evidenceOwner(),
                    schema);
        }
    }

    public record Adapter(
            String id,
            String provider,
            String summary,
            boolean available,
            HostSchema schema,
            String diagnostic) {
        public Adapter {
            id = require(id, "id");
            provider = require(provider, "provider");
            summary = require(summary, "summary");
            diagnostic = diagnostic == null ? "" : diagnostic;
        }

        private static Adapter from(JavascriptDataModuleRegistry.Descriptor descriptor) {
            return new Adapter(
                    descriptor.module(),
                    descriptor.provider(),
                    descriptor.summary(),
                    descriptor.available(),
                    descriptor.schema(),
                    descriptor.diagnostic());
        }
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
