package dev.openallay.client.gui.settings;

import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.schema.HostSchema;
import dev.openallay.settings.extension.ExtensionSettingsView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Player-facing projection of Rhino roots, modules, adapters, and experimental capabilities. */
public record ExtensionSettingsProjection(
        RuntimeCard runtime,
        List<RootCard> roots,
        List<ModuleCard> modules,
        List<AdapterCard> adapters,
        List<ExtensionCard> extensions,
        boolean experimentalCommands,
        boolean debugMode) {
    public ExtensionSettingsProjection {
        Objects.requireNonNull(runtime, "runtime");
        roots = List.copyOf(roots);
        modules = List.copyOf(modules);
        adapters = List.copyOf(adapters);
        extensions = List.copyOf(extensions);
    }

    public static ExtensionSettingsProjection from(
            ExtensionSettingsView view,
            CommandCapabilityConfig commands,
            boolean debugMode) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(commands, "commands");
        return new ExtensionSettingsProjection(
                new RuntimeCard(
                        "openallay:run_javascript",
                        "screen.openallay.settings.extensions.runtime.title",
                        "screen.openallay.settings.extensions.runtime.description",
                        List.of("source", "roots", "handles"),
                        List.of(
                                "typed result",
                                "workspace handle",
                                "preview",
                                "evidence")),
                view.roots().stream()
                        .map(root -> new RootCard(
                                root.name(),
                                root.availability().name(),
                                root.provider(),
                                root.summary(),
                                root.evidenceOwner(),
                                renderSchema(root.schema(), 0)))
                        .toList(),
                view.bundledModules().stream().map(ModuleCard::new).toList(),
                view.adapters().stream()
                        .map(adapter -> new AdapterCard(
                                adapter.id(),
                                adapter.provider(),
                                adapter.summary(),
                                adapter.available(),
                                adapter.schema() == null
                                        ? ""
                                        : renderSchema(adapter.schema(), 0),
                                adapter.diagnostic()))
                        .toList(),
                view.extensions().stream()
                        .map(extension -> new ExtensionCard(
                                extension.id(),
                                extension.name(),
                                extension.version(),
                                extension.state().name(),
                                extension.provider(),
                                extension.summary(),
                                extension.loaders(),
                                extension.minecraftVersionRange(),
                                extension.openAllayApiVersionRange(),
                                extension.source(),
                                extension.contributions(),
                                extension.diagnostic()))
                        .toList(),
                commands.enabled(),
                debugMode);
    }

    public ExtensionSettingsProjection toggleExperimentalCommands() {
        return new ExtensionSettingsProjection(
                runtime,
                roots,
                modules,
                adapters,
                extensions,
                !experimentalCommands,
                debugMode);
    }

    public record RuntimeCard(
            String id,
            String titleKey,
            String descriptionKey,
            List<String> parameters,
            List<String> returns) {
        public RuntimeCard {
            parameters = List.copyOf(parameters);
            returns = List.copyOf(returns);
        }
    }

    public record RootCard(
            String name,
            String availability,
            String provider,
            String summary,
            String evidenceOwner,
            String schema) {}

    public record ModuleCard(String id) {}

    public record AdapterCard(
            String id,
            String provider,
            String summary,
            boolean available,
            String schema,
            String diagnostic) {}

    public record ExtensionCard(
            String id,
            String name,
            String version,
            String state,
            String provider,
            String summary,
            List<String> loaders,
            String minecraftVersionRange,
            String openAllayApiVersionRange,
            String source,
            ExtensionSettingsView.Contributions contributions,
            String diagnostic) {
        public ExtensionCard {
            loaders = List.copyOf(loaders);
        }
    }

    private static String renderSchema(HostSchema schema, int depth) {
        if (depth >= 5) {
            return schema.kind();
        }
        return switch (schema) {
            case HostSchema.Scalar scalar -> scalar.kind();
            case HostSchema.Enumeration enumeration ->
                    enumeration.kind() + "(" + String.join(" | ", enumeration.values()) + ")";
            case HostSchema.Sequence sequence ->
                    "array<" + renderSchema(sequence.elements(), depth + 1) + ">";
            case HostSchema.OptionalValue optional ->
                    renderSchema(optional.value(), depth + 1) + "?";
            case HostSchema.Dictionary dictionary ->
                    "map<string, " + renderSchema(dictionary.values(), depth + 1) + ">";
            case HostSchema.DynamicJson ignored -> "dynamic JSON";
            case HostSchema.DynamicDetached ignored -> "declared extension value";
            case HostSchema.RecordValue record -> renderRecord(record, depth);
        };
    }

    private static String renderRecord(HostSchema.RecordValue record, int depth) {
        List<String> fields = new ArrayList<>();
        for (Map.Entry<String, HostSchema> field : record.fields().entrySet()) {
            fields.add(field.getKey() + ": " + renderSchema(field.getValue(), depth + 1));
        }
        fields.sort(String::compareTo);
        return "{ " + String.join(", ", fields) + " }";
    }
}
