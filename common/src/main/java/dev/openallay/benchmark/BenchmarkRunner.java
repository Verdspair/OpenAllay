package dev.openallay.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Executes repeatable cases and scores rounds/calls; wall-clock latency is intentionally absent. */
public final class BenchmarkRunner {
    @FunctionalInterface
    public interface Executor {
        BenchmarkOutcome execute(BenchmarkCase testCase, int attempt);
    }

    private final BenchmarkVerifier verifier;

    public BenchmarkRunner(BenchmarkVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    public BenchmarkReport run(
            String corpusVersion,
            List<BenchmarkCase> cases,
            Executor executor) {
        Objects.requireNonNull(executor, "executor");
        ArrayList<BenchmarkReport.CaseReport> reports = new ArrayList<>();
        for (BenchmarkCase testCase : List.copyOf(cases)) {
            ArrayList<BenchmarkMetrics> metrics = new ArrayList<>();
            ArrayList<String> diagnostics = new ArrayList<>();
            int successes = 0;
            long modelTurns = 0;
            long toolCalls = 0;
            for (int attempt = 1; attempt <= testCase.attempts(); attempt++) {
                BenchmarkOutcome outcome = Objects.requireNonNull(
                        executor.execute(testCase, attempt), "benchmark outcome");
                BenchmarkVerifier.Verification verification =
                        verifier.verify(testCase, outcome);
                boolean success = outcome.metrics().success()
                        && verification.passed()
                        && outcome.metrics().modelTurns() <= testCase.maxModelTurns();
                BenchmarkMetrics measured = withSuccess(outcome.metrics(), success);
                metrics.add(measured);
                modelTurns += measured.modelTurns();
                toolCalls += measured.toolCalls();
                if (success) {
                    successes++;
                    diagnostics.add("");
                } else if (!verification.passed()) {
                    diagnostics.add(verification.diagnostic());
                } else {
                    diagnostics.add("model turn budget exceeded");
                }
            }
            int attempts = testCase.attempts();
            reports.add(new BenchmarkReport.CaseReport(
                    testCase.id(),
                    attempts,
                    successes,
                    successes / (double) attempts,
                    modelTurns / (double) attempts,
                    median(metrics.stream()
                            .map(BenchmarkMetrics::modelTurns)
                            .toList()),
                    toolCalls / (double) attempts,
                    median(metrics.stream()
                            .map(BenchmarkMetrics::toolCalls)
                            .toList()),
                    metrics,
                    diagnostics));
        }
        return new BenchmarkReport(corpusVersion, reports);
    }

    private static double median(List<Integer> values) {
        List<Integer> ordered = values.stream().sorted().toList();
        int middle = ordered.size() / 2;
        return ordered.size() % 2 == 1
                ? ordered.get(middle)
                : (ordered.get(middle - 1) + ordered.get(middle)) / 2.0D;
    }

    private static BenchmarkMetrics withSuccess(BenchmarkMetrics metrics, boolean success) {
        return new BenchmarkMetrics(
                success,
                metrics.modelTurns(),
                metrics.toolCalls(),
                metrics.javascriptCalls(),
                metrics.skillLoads(),
                metrics.skillReloads(),
                metrics.duplicateSkillLoads(),
                metrics.invalidCalls(),
                metrics.correctedCalls(),
                metrics.terminalCode());
    }
}
