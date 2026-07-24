package dev.openallay.benchmark;

import com.google.gson.JsonElement;
import java.util.Objects;

public record BenchmarkCase(
        String id,
        String category,
        String prompt,
        int attempts,
        int maxModelTurns,
        Verifier verifier) {
    public BenchmarkCase {
        id = nonBlank(id, "id");
        category = nonBlank(category, "category");
        prompt = nonBlank(prompt, "prompt");
        if (attempts <= 0 || maxModelTurns <= 0) {
            throw new IllegalArgumentException("attempts and maxModelTurns must be positive");
        }
        Objects.requireNonNull(verifier, "verifier");
    }

    public record Verifier(
            Kind kind,
            String path,
            JsonElement expected,
            String contains) {
        public Verifier {
            Objects.requireNonNull(kind, "kind");
            path = path == null ? "" : path.strip();
            expected = expected == null ? null : expected.deepCopy();
            contains = contains == null ? "" : contains;
            if (kind == Kind.JSON_PATH_EQUALS && (path.isBlank() || expected == null)) {
                throw new IllegalArgumentException(
                        "json_path_equals requires path and expected");
            }
            if ((kind == Kind.RESULT_CONTAINS || kind == Kind.EFFECT_CONTAINS)
                    && contains.isBlank()) {
                throw new IllegalArgumentException(kind + " requires contains");
            }
        }

        @Override
        public JsonElement expected() {
            return expected == null ? null : expected.deepCopy();
        }
    }

    public enum Kind {
        JSON_PATH_EQUALS,
        RESULT_CONTAINS,
        EFFECT_CONTAINS,
        NON_EMPTY_RESULT
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
