package dev.openallay.recipe.config;

import dev.openallay.context.RecipeReference;
import dev.openallay.recipe.RecipeVisibilityPolicy;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generic stable-ID recipe settings.
 *
 * @param schemaVersion strict persisted schema version
 * @param visibility recipe visibility policy
 * @param preferredViewer preferred recipe-viewer source or {@value #AUTO}
 * @param disabledSources source IDs excluded from recipe discovery
 */
public record RecipeClientConfig(
        int schemaVersion,
        RecipeVisibilityPolicy visibility,
        String preferredViewer,
        Set<String> disabledSources) {
    public static final int SCHEMA_VERSION = 2;
    public static final String AUTO = "auto";

    public RecipeClientConfig {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported recipe configuration schema");
        }
        Objects.requireNonNull(visibility, "visibility");
        if (preferredViewer == null || preferredViewer.isBlank()) {
            throw new IllegalArgumentException("preferredViewer must not be blank");
        }
        if (!AUTO.equals(preferredViewer)) {
            preferredViewer = RecipeReference.requireSourceId(preferredViewer);
        }
        Objects.requireNonNull(disabledSources, "disabledSources");
        TreeSet<String> canonical = new TreeSet<>();
        for (String sourceId : disabledSources) {
            String validated = RecipeReference.requireSourceId(sourceId);
            if (!canonical.add(validated)) {
                throw new IllegalArgumentException("disabledSources must not contain duplicates");
            }
        }
        disabledSources = Collections.unmodifiableSet(canonical);
    }

    public static RecipeClientConfig defaults() {
        return new RecipeClientConfig(
                SCHEMA_VERSION,
                RecipeVisibilityPolicy.ALL_KNOWN,
                AUTO,
                Set.of());
    }
}
