package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class BenchmarkCorpusTest {
    @Test
    void bundledCorpusIsStrictGeneralizableAndCoversPlatformCapabilities() {
        var input = getClass().getClassLoader()
                .getResourceAsStream("data/openallay/benchmarks/core.json");
        BenchmarkCorpus corpus = new BenchmarkCorpusCodec().decode(
                new InputStreamReader(input, StandardCharsets.UTF_8));

        assertEquals(1, corpus.schemaVersion());
        assertTrue(corpus.cases().size() >= 10);
        Set<String> categories = corpus.cases().stream()
                .map(BenchmarkCase::category)
                .collect(Collectors.toSet());
        assertTrue(categories.containsAll(Set.of(
                "core-context",
                "data-analysis",
                "recipes",
                "schema",
                "extensions",
                "commands",
                "world",
                "model-routing")));
        for (BenchmarkCase testCase : corpus.cases()) {
            assertFalse(testCase.prompt().contains("/give "));
            assertFalse(testCase.prompt().contains("return {"));
        }
    }

    @Test
    void rejectsUnknownFieldsAndSchemaVersions() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkCorpusCodec().decode(
                new java.io.StringReader("""
                        {"schemaVersion":1,"version":"v","cases":[],"answer":"cheat"}
                        """)));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkCorpusCodec().decode(
                new java.io.StringReader("""
                        {"schemaVersion":2,"version":"v","cases":[]}
                        """)));
    }
}
