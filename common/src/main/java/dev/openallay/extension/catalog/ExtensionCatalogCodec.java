package dev.openallay.extension.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Strict codec: unknown or missing fields reject the candidate catalog generation. */
public final class ExtensionCatalogCodec {
    private static final Set<String> ROOT_FIELDS =
            Set.of("schemaVersion", "kind", "generatedAt", "extensions");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "id",
            "name",
            "version",
            "provider",
            "summary",
            "loaders",
            "minecraftVersionRange",
            "openAllayApiVersionRange",
            "artifact",
            "sha256",
            "modIds",
            "source");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ExtensionCatalogManifest decode(String json) {
        try {
            JsonObject root = object(JsonParser.parseString(json), "catalog");
            exactFields(root, ROOT_FIELDS, "catalog");
            List<ExtensionCatalogEntry> entries = new ArrayList<>();
            JsonElement encoded = root.get("extensions");
            if (encoded == null || !encoded.isJsonArray()) {
                throw new IllegalArgumentException("extensions must be an array");
            }
            for (JsonElement value : encoded.getAsJsonArray()) {
                JsonObject entry = object(value, "extension");
                exactFields(entry, ENTRY_FIELDS, "extension");
                entries.add(new ExtensionCatalogEntry(
                        string(entry, "id"),
                        string(entry, "name"),
                        string(entry, "version"),
                        string(entry, "provider"),
                        string(entry, "summary"),
                        strings(entry, "loaders"),
                        string(entry, "minecraftVersionRange"),
                        string(entry, "openAllayApiVersionRange"),
                        URI.create(string(entry, "artifact")),
                        string(entry, "sha256"),
                        strings(entry, "modIds"),
                        string(entry, "source")));
            }
            return new ExtensionCatalogManifest(
                    integer(root, "schemaVersion"),
                    string(root, "kind"),
                    Instant.parse(string(root, "generatedAt")),
                    entries);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new IllegalArgumentException("Invalid Extension catalog", failure);
        }
    }

    public String encode(ExtensionCatalogManifest manifest) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", manifest.schemaVersion());
        root.addProperty("kind", manifest.kind());
        root.addProperty("generatedAt", manifest.generatedAt().toString());
        var entries = new com.google.gson.JsonArray();
        for (ExtensionCatalogEntry entry : manifest.extensions()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("id", entry.id());
            encoded.addProperty("name", entry.name());
            encoded.addProperty("version", entry.version());
            encoded.addProperty("provider", entry.provider());
            encoded.addProperty("summary", entry.summary());
            encoded.add("loaders", strings(entry.loaders()));
            encoded.addProperty("minecraftVersionRange", entry.minecraftVersionRange());
            encoded.addProperty("openAllayApiVersionRange", entry.openAllayApiVersionRange());
            encoded.addProperty("artifact", entry.artifact().toString());
            encoded.addProperty("sha256", entry.sha256());
            encoded.add("modIds", strings(entry.modIds()));
            encoded.addProperty("source", entry.source());
            entries.add(encoded);
        }
        root.add("extensions", entries);
        return gson.toJson(root) + "\n";
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.getAsString();
    }

    private static Set<String> strings(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        TreeSet<String> values = new TreeSet<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonPrimitive()
                    || !item.getAsJsonPrimitive().isString()
                    || item.getAsString().isBlank()
                    || !values.add(item.getAsString())) {
                throw new IllegalArgumentException(field + " must contain unique strings");
            }
        }
        return Set.copyOf(values);
    }

    private static com.google.gson.JsonArray strings(Set<String> values) {
        var encoded = new com.google.gson.JsonArray();
        values.stream().sorted().forEach(encoded::add);
        return encoded;
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        int parsed = value.getAsInt();
        if (value.getAsDouble() != parsed) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return parsed;
    }

    private static void exactFields(JsonObject object, Set<String> expected, String label) {
        if (!new java.util.HashSet<>(object.keySet()).equals(expected)) {
            throw new IllegalArgumentException(label + " fields do not match schema");
        }
    }
}
