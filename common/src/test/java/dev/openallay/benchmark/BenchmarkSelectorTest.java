package dev.openallay.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BenchmarkSelectorTest {
    @Test
    void distinguishesFixtureMismatchFromMissingCapabilities() {
        BenchmarkCorpus corpus = new BenchmarkCorpus(1, "fixture-v1", List.of(
                testCase("world", "javascript-agent-v2", List.of("world")),
                testCase("routing", "server-model-routing-v1", List.of("server-model"))));

        BenchmarkSelector.Selection selection = new BenchmarkSelector().select(
                corpus,
                "javascript-agent-v2",
                Set.of(),
                Set.of(),
                3);

        assertEquals(List.of(), selection.selected());
        assertEquals(List.of(
                new BenchmarkSelector.SkippedCase(
                        "world",
                        "javascript-agent-v2",
                        BenchmarkSelector.SkipReason.MISSING_CAPABILITIES,
                        List.of("world")),
                new BenchmarkSelector.SkippedCase(
                        "routing",
                        "server-model-routing-v1",
                        BenchmarkSelector.SkipReason.FIXTURE_MISMATCH,
                        List.of("server-model"))),
                selection.skipped());
    }

    @Test
    void requestedUnavailableCasesFailInsteadOfShrinkingTheRun() {
        BenchmarkCorpus corpus = new BenchmarkCorpus(1, "fixture-v1", List.of(
                testCase("routing", "server-model-routing-v1", List.of("server-model"))));

        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSelector().select(
                corpus,
                "javascript-agent-v2",
                Set.of("server-model"),
                Set.of("routing"),
                1));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkSelector().select(
                corpus,
                "javascript-agent-v2",
                Set.of(),
                Set.of("unknown"),
                1));
    }

    private static BenchmarkCase testCase(
            String id, String fixture, List<String> capabilities) {
        return new BenchmarkCase(
                id,
                "test",
                "test prompt",
                fixture,
                capabilities,
                1,
                2,
                new BenchmarkCase.Verifier(
                        BenchmarkCase.Kind.NON_EMPTY_RESULT, "", null, ""));
    }
}
