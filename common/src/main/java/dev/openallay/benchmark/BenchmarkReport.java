package dev.openallay.benchmark;

import java.util.List;

public record BenchmarkReport(
        String corpusVersion,
        List<CaseReport> cases) {
    public BenchmarkReport {
        corpusVersion = require(corpusVersion);
        cases = List.copyOf(cases);
    }

    public record CaseReport(
            String caseId,
            int attempts,
            int successes,
            double successProbability,
            double averageModelTurns,
            double medianModelTurns,
            double averageToolCalls,
            double medianToolCalls,
            List<BenchmarkMetrics> metrics,
            List<String> diagnostics) {
        public CaseReport {
            caseId = require(caseId);
            metrics = List.copyOf(metrics);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value;
    }
}
