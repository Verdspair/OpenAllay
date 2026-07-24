package dev.openallay.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Closed catalog of reviewed JavaScript modules available inside one Rhino scope. */
public final class JavascriptModuleCatalog {
    private static final Map<String, String> BUNDLED = Map.of(
            "openallay:crafting",
            "assets/openallay/openallay_js_modules/crafting.js");

    private final Map<String, String> sources;

    public JavascriptModuleCatalog(Map<String, String> sources) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        Objects.requireNonNull(sources, "sources").forEach((id, source) -> {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid JavaScript module id");
            }
            if (source == null || source.isBlank()) {
                throw new IllegalArgumentException("JavaScript module source is required");
            }
            copy.put(id, source);
        });
        this.sources = Map.copyOf(copy);
    }

    public static JavascriptModuleCatalog bundled() {
        ClassLoader loader = JavascriptModuleCatalog.class.getClassLoader();
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        BUNDLED.forEach((id, path) -> sources.put(id, read(loader, path)));
        return new JavascriptModuleCatalog(sources);
    }

    /** Descriptor-only view used by Settings without loading module source text. */
    public static Set<String> bundledIds() {
        return Set.copyOf(BUNDLED.keySet());
    }

    public String source(String id) {
        String source = sources.get(id);
        if (source == null) {
            throw new JavascriptExecutionException(
                    "javascript_module_unavailable",
                    "JavaScript module is unavailable: " + id);
        }
        return source;
    }

    public Set<String> ids() {
        return sources.keySet();
    }

    private static String read(ClassLoader loader, String path) {
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled JavaScript module " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load bundled JavaScript module " + path, failure);
        }
    }
}
