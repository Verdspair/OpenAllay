package dev.openallay.benchmark;

import java.util.HashSet;
import java.util.List;

public record BenchmarkCorpus(int schemaVersion, String version, List<BenchmarkCase> cases) {
    public BenchmarkCorpus {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "Unsupported benchmark schema version " + schemaVersion);
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        cases = List.copyOf(cases);
        HashSet<String> ids = new HashSet<>();
        for (BenchmarkCase testCase : cases) {
            if (!ids.add(testCase.id())) {
                throw new IllegalArgumentException(
                        "Duplicate benchmark case " + testCase.id());
            }
        }
    }
}
