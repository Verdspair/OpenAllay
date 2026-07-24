package dev.openallay.extension;

import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.script.schema.RhinoTypeSchema;
import dev.openallay.skill.SkillRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Common owner of loader-registered Extension declarations.
 *
 * <p>A candidate is completely validated before any contribution registry changes. Rejected
 * candidates never replace the last published generation.
 */
public final class OpenAllayExtensionRegistry {
    private final OpenAllayExtensionEnvironment environment;
    private final JavascriptDataModuleRegistry dataModules;
    private final JavascriptModuleCatalog javascriptModules;
    private final SkillRepository skills;
    private final Set<String> installedMods;
    private final Map<String, RegisteredExtension> active = new TreeMap<>();
    private final Map<String, String> contributionOwners = new TreeMap<>();
    private long generation;

    public OpenAllayExtensionRegistry(
            OpenAllayExtensionEnvironment environment,
            JavascriptDataModuleRegistry dataModules,
            JavascriptModuleCatalog javascriptModules,
            SkillRepository skills,
            Set<String> installedMods) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.dataModules = Objects.requireNonNull(dataModules, "dataModules");
        this.javascriptModules = Objects.requireNonNull(javascriptModules, "javascriptModules");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.installedMods = Set.copyOf(installedMods);
    }

    public synchronized Registration register(OpenAllayExtension extension) {
        Objects.requireNonNull(extension, "extension");
        OpenAllayExtensionDescriptor descriptor;
        OpenAllayExtensionContribution contribution;
        try {
            descriptor = Objects.requireNonNull(extension.descriptor(), "descriptor");
            contribution = Objects.requireNonNull(extension.contribution(), "contribution");
        } catch (RuntimeException failure) {
            return rejected("", OpenAllayExtensionState.UNAVAILABLE, "extension_registration_failed");
        }
        if (active.containsKey(descriptor.id())) {
            return rejected(
                    descriptor.id(), OpenAllayExtensionState.UNAVAILABLE, "duplicate_extension_id");
        }
        String incompatibility = environment.incompatibility(descriptor);
        if (!incompatibility.isEmpty()) {
            return rejected(descriptor.id(), OpenAllayExtensionState.INCOMPATIBLE, incompatibility);
        }
        try {
            validateContribution(descriptor.id(), contribution);
            publish(descriptor, contribution);
            generation++;
            return new Registration(
                    descriptor.id(), OpenAllayExtensionState.ACTIVE, "", generation);
        } catch (DuplicateContribution failure) {
            return rejected(
                    descriptor.id(),
                    OpenAllayExtensionState.UNAVAILABLE,
                    "duplicate_contribution_id");
        } catch (RuntimeException failure) {
            return rejected(
                    descriptor.id(),
                    OpenAllayExtensionState.UNAVAILABLE,
                    "extension_registration_failed");
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                generation,
                active.values().stream()
                        .map(RegisteredExtension::view)
                        .toList());
    }

    public OpenAllayExtensionEnvironment environment() {
        return environment;
    }

    private void validateContribution(
            String extensionId, OpenAllayExtensionContribution contribution) {
        Set<String> batch = new HashSet<>();
        for (JavascriptDataModule module : contribution.dataModules()) {
            Objects.requireNonNull(module, "dataModule");
            claim(extensionId, module.id(), batch);
            RhinoTypeSchema.require(module.valueType());
        }
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        for (JavascriptModuleSource module : contribution.javascriptModules()) {
            Objects.requireNonNull(module, "javascriptModule");
            claim(extensionId, module.id(), batch);
            moduleSources.put(module.id(), module.source());
        }
        for (JavascriptResultViewProvider view : contribution.resultViews()) {
            Objects.requireNonNull(view, "resultView");
            claim(extensionId, view.id(), batch);
            Objects.requireNonNull(view.kind(), "resultView.kind");
            if (view.summary() == null || view.summary().isBlank()) {
                throw new IllegalArgumentException("Result view summary is required");
            }
        }
        dataModules.validateRegistration(extensionId, contribution.dataModules());
        javascriptModules.validateRegistration(extensionId, moduleSources);
        skills.validateExternal(contribution.skills(), installedMods);
    }

    private void claim(String extensionId, String contributionId, Set<String> batch) {
        if (contributionId == null || contributionId.isBlank()) {
            throw new IllegalArgumentException("Contribution ID is required");
        }
        if (!batch.add(contributionId)) {
            throw new DuplicateContribution();
        }
        String owner = contributionOwners.get(contributionId);
        if (owner != null && !owner.equals(extensionId)) {
            throw new DuplicateContribution();
        }
    }

    private void publish(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionContribution contribution) {
        LinkedHashMap<String, String> moduleSources = new LinkedHashMap<>();
        contribution.javascriptModules().forEach(module ->
                moduleSources.put(module.id(), module.source()));
        skills.registerExternal(contribution.skills(), installedMods);
        dataModules.register(descriptor.id(), contribution.dataModules());
        javascriptModules.register(descriptor.id(), moduleSources);
        contribution.dataModules().forEach(module ->
                contributionOwners.put(module.id(), descriptor.id()));
        contribution.javascriptModules().forEach(module ->
                contributionOwners.put(module.id(), descriptor.id()));
        contribution.resultViews().forEach(view ->
                contributionOwners.put(view.id(), descriptor.id()));
        RegisteredExtension registered = new RegisteredExtension(descriptor, contribution);
        active.put(descriptor.id(), registered);
    }

    private Registration rejected(
            String extensionId, OpenAllayExtensionState state, String diagnostic) {
        return new Registration(extensionId, state, diagnostic, generation);
    }

    public record Registration(
            String extensionId,
            OpenAllayExtensionState state,
            String diagnostic,
            long generation) {}

    public record Snapshot(long generation, List<ExtensionView> extensions) {
        public Snapshot {
            extensions = List.copyOf(extensions);
        }
    }

    public record ExtensionView(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionState state,
            List<String> dataModules,
            List<String> javascriptModules,
            List<String> skills,
            List<String> resultViews,
            String diagnostic) {
        public ExtensionView {
            dataModules = List.copyOf(dataModules);
            javascriptModules = List.copyOf(javascriptModules);
            skills = List.copyOf(skills);
            resultViews = List.copyOf(resultViews);
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    private record RegisteredExtension(
            OpenAllayExtensionDescriptor descriptor,
            OpenAllayExtensionContribution contribution) {
        private ExtensionView view() {
            return new ExtensionView(
                    descriptor,
                    OpenAllayExtensionState.ACTIVE,
                    contribution.dataModules().stream().map(JavascriptDataModule::id).sorted().toList(),
                    contribution.javascriptModules().stream()
                            .map(JavascriptModuleSource::id)
                            .sorted()
                            .toList(),
                    contribution.skills().stream()
                            .map(source -> source.directoryName())
                            .sorted()
                            .toList(),
                    contribution.resultViews().stream()
                            .map(JavascriptResultViewProvider::id)
                            .sorted()
                            .toList(),
                    "");
        }
    }

    private static final class DuplicateContribution extends RuntimeException {}
}
