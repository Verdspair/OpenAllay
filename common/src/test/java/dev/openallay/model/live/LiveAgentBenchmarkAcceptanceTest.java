package dev.openallay.model.live;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.AgentSystemPrompt;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.benchmark.AgentBenchmarkRecorder;
import dev.openallay.benchmark.BenchmarkCase;
import dev.openallay.benchmark.BenchmarkCorpus;
import dev.openallay.benchmark.BenchmarkCorpusCodec;
import dev.openallay.benchmark.BenchmarkOutcome;
import dev.openallay.benchmark.BenchmarkReport;
import dev.openallay.benchmark.BenchmarkRunner;
import dev.openallay.benchmark.BenchmarkVerifier;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.context.game.ObservableGameStateSnapshot;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.anthropic.AnthropicMessagesClient;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.openai.OpenAiChatClient;
import dev.openallay.model.scheduling.ModelRequestScheduler;
import dev.openallay.platform.InstalledModMetadata;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.command.CommandCatalogSnapshot;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.testing.GroundedTestFixtures;
import dev.openallay.testing.JavascriptAgentTestFixtures;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.builtin.RunJavascriptTool;
import dev.openallay.world.BlockObservation;
import dev.openallay.world.EntityObservation;
import dev.openallay.world.WorldBlockSnapshot;
import dev.openallay.world.WorldBounds;
import dev.openallay.world.WorldEntitySnapshot;
import dev.openallay.world.WorldEntitySummary;
import dev.openallay.world.WorldObservationCoordinator;
import dev.openallay.world.WorldObservationCoverage;
import dev.openallay.world.WorldObservationRequest;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.world.WorldPosition;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Explicit, billable corpus benchmark over the production Agent loop and model transports.
 *
 * <p>The fixture contributes data and observed effects only. Expected outcomes stay in the corpus
 * verifier and are never appended to model context.
 */
final class LiveAgentBenchmarkAcceptanceTest {
    private static final String FIXTURE = "javascript-agent-v2";

    @Test
    void realProviderRunsEveryApplicableCorpusCaseAndRetainsRedactedTraces()
            throws Exception {
        Map<String, String> environment = System.getenv();
        Assumptions.assumeTrue(Boolean.parseBoolean(
                environment.get("OPENALLAY_LIVE_AGENT_BENCHMARK")));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        BenchmarkCorpus corpus = corpus();
        int repeats = positive(environment.getOrDefault(
                "OPENALLAY_BENCHMARK_REPEATS", "3"), "OPENALLAY_BENCHMARK_REPEATS");
        boolean includeCommands = Boolean.parseBoolean(
                environment.getOrDefault("OPENALLAY_BENCHMARK_INCLUDE_COMMANDS", "false"));
        Set<String> capabilities = new LinkedHashSet<>(Set.of(
                "game", "player", "registries", "recipes", "extensions",
                "recipe-viewer", "world"));
        if (includeCommands) {
            capabilities.add("commands");
        }
        List<BenchmarkCase> cases =
                select(corpus, environment, capabilities, repeats);
        assertFalse(cases.isEmpty(), "No applicable benchmark cases were selected");

        ModelClient rawModel = model(environment, gson);
        JavascriptDataModuleRegistry extensions = extensions();
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
        commands.replace(new CommandCapabilityConfig(
                CommandCapabilityConfig.SCHEMA_VERSION, includeCommands));
        WorldObservationRuntime world = new WorldObservationRuntime();
        RunJavascriptTool javascript = new RunJavascriptTool(
                new RhinoJavascriptRuntime(),
                context -> new MinecraftAgentHostGraph(
                        context,
                        dev.openallay.knowledge.KnowledgeSnapshot::empty,
                        extensions),
                new AgentResultWorkspaceRegistry(),
                new JavascriptResultPresenter(),
                commands,
                world);
        ToolRegistry registry = new ToolRegistry();
        registry.register("openallay:live-benchmark", List.of(javascript));
        SkillRepository skills = new SkillRepository(
                new SkillParser(),
                registry.descriptors().stream().map(value -> value.id()).toList());
        if (!skills.reload(new BundledSkillLoader().load(), Set.of())) {
            throw new IllegalStateException("Bundled Skills failed validation");
        }
        registry.register("openallay:skills", List.of(new LoadSkillTool(skills)));
        GameGuideAgent agent = new GameGuideAgent(
                new ModelRequestScheduler(rawModel),
                new LocalAgentToolExecutor(registry, gson),
                new AgentSessionStore(),
                gson);
        String prompt = AgentSystemPrompt.compose(
                skills.metadataPrompt(),
                dev.openallay.script.schema.CoreJavascriptContract.render(
                        MinecraftAgentHostGraph.describeRequest(
                                benchmarkContext("descriptor"), extensions)));
        boolean stream = Boolean.parseBoolean(
                environment.getOrDefault("OPENALLAY_LIVE_STREAM", "true"));
        List<AttemptTrace> traces = new CopyOnWriteArrayList<>();

        BenchmarkReport report = new BenchmarkRunner(new BenchmarkVerifier()).run(
                corpus.version(),
                cases,
                (testCase, attempt) -> execute(
                        testCase,
                        attempt,
                        agent,
                        prompt,
                        stream,
                        commands,
                        world,
                        includeCommands,
                        traces));

        Path retained = retain(environment, corpus, report, traces, gson);
        report.cases().forEach(value -> System.out.println(
                "OPENALLAY_BENCHMARK_CASE"
                        + " id=" + value.caseId()
                        + " successes=" + value.successes() + "/" + value.attempts()
                        + " probability=" + value.successProbability()
                        + " median_model_turns=" + value.medianModelTurns()
                        + " median_tool_calls=" + value.medianToolCalls()));
        System.out.println("OPENALLAY_BENCHMARK_REPORT " + retained.toAbsolutePath());
    }

    private static BenchmarkOutcome execute(
            BenchmarkCase testCase,
            int attempt,
            GameGuideAgent agent,
            String prompt,
            boolean stream,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime world,
            boolean includeCommands,
            List<AttemptTrace> traces) {
        String correlationId = "benchmark-" + testCase.id() + "-" + attempt;
        AgentBenchmarkRecorder recorder = new AgentBenchmarkRecorder();
        world.capture(correlationId, new FixtureWorld());
        if (includeCommands) {
            commands.capture(
                    correlationId,
                    GroundedTestFixtures.PLAYER_ID,
                    commandCatalog(),
                    (actor, command, cancellation) -> {
                        recorder.effect("command-submitted:" + command);
                        if (command.startsWith("give ")) {
                            recorder.effect("enchanted-item-created");
                        }
                        commands.acceptFeedback(actor, "Command completed: " + command);
                        return CompletableFuture.completedFuture(null);
                    });
        }
        AgentResult result;
        try {
            result = agent.ask(
                            new AgentRequest(
                                    UUID.randomUUID(),
                                    GroundedTestFixtures.PLAYER_ID,
                                    testCase.id() + "-" + attempt,
                                    testCase.prompt(),
                                    prompt,
                                    benchmarkContext(correlationId),
                                    stream),
                            recorder)
                    .get(6, TimeUnit.MINUTES);
        } catch (Exception failure) {
            result = new AgentResult(
                    dev.openallay.agent.AgentState.FAILED,
                    "",
                    "benchmark_harness_failure",
                    "Benchmark attempt did not reach an Agent terminal result",
                    null);
        }
        traces.add(new AttemptTrace(testCase.id(), attempt, result.trace()));
        return recorder.outcome(result);
    }

    private static List<BenchmarkCase> select(
            BenchmarkCorpus corpus,
            Map<String, String> environment,
            Set<String> capabilities,
            int repeats) {
        String selection = environment.getOrDefault("OPENALLAY_BENCHMARK_CASES", "").strip();
        Set<String> requested = selection.isEmpty()
                ? Set.of()
                : java.util.Arrays.stream(selection.split(","))
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!requested.isEmpty()) {
            Set<String> known = corpus.cases().stream()
                    .map(BenchmarkCase::id)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> unknown = new java.util.TreeSet<>(requested);
            unknown.removeAll(known);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Unknown benchmark cases: " + unknown);
            }
        }
        List<BenchmarkCase> selected = corpus.cases().stream()
                .filter(value -> requested.isEmpty() || requested.contains(value.id()))
                .filter(value -> value.applicableTo(FIXTURE, capabilities))
                .map(value -> value.withAttempts(repeats))
                .toList();
        if (!requested.isEmpty() && selected.size() != requested.size()) {
            Set<String> unavailable = new java.util.TreeSet<>(requested);
            selected.forEach(value -> unavailable.remove(value.id()));
            throw new IllegalArgumentException(
                    "Selected cases require unavailable fixture capabilities: " + unavailable);
        }
        return selected;
    }

    private static BenchmarkCorpus corpus() {
        var input = LiveAgentBenchmarkAcceptanceTest.class.getClassLoader()
                .getResourceAsStream("data/openallay/benchmarks/core.json");
        if (input == null) {
            throw new IllegalStateException("Bundled benchmark corpus is unavailable");
        }
        return new BenchmarkCorpusCodec().decode(
                new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private static ToolInvocationContext benchmarkContext(String correlationId) {
        ToolInvocationContext base = JavascriptAgentTestFixtures.context(correlationId);
        ObservableGameStateSnapshot state = base.observableGameState().orElseThrow();
        ArrayList<InstalledModMetadata> installed = new ArrayList<>(state.mods().installed());
        installed.add(new InstalledModMetadata(
                "farmersdelight",
                "Farmer's Delight",
                "26.2-fixture",
                "Benchmark content fixture",
                List.of("vectorwing"),
                List.of("MIT"),
                Map.of(),
                "client_and_server",
                List.of()));
        installed.sort(java.util.Comparator.comparing(InstalledModMetadata::id));
        ObservableGameStateSnapshot game = new ObservableGameStateSnapshot(
                state.capturedAt(),
                state.runtime(),
                new ObservableGameStateSnapshot.ModsState(
                        installed, state.mods().evidence(), state.mods().diagnostics()),
                state.options(),
                state.packs(),
                state.shaders(),
                state.diagnostics(),
                state.player(),
                state.worldQueries());
        return new ToolInvocationContext(
                correlationId,
                base.capturedAt(),
                base.caller(),
                base.player(),
                base.registries(),
                base.recipes(),
                java.util.Optional.of(game),
                base.metrics());
    }

    private static JavascriptDataModuleRegistry extensions() {
        JavascriptDataModuleRegistry registry = new JavascriptDataModuleRegistry();
        registry.register("benchmark-fixture", List.of(
                module(
                        "benchmark:machines",
                        "Dynamic machine metadata",
                        """
                        [{"id":"example:crusher","fields":{"energy":"number","speed":"number"}}]
                        """),
                module(
                        "openallay:jei",
                        "Detached recipe-viewer metadata",
                        """
                        {"provider":"jei","categories":["minecraft:crafting"],\
                        "example":{"recipeId":"minecraft:chest"}}
                        """)));
        return registry;
    }

    private static JavascriptDataModule module(String id, String summary, String json) {
        JsonElement value = JsonParser.parseString(json);
        return new JavascriptDataModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public java.lang.reflect.Type valueType() {
                return JsonElement.class;
            }

            @Override
            public String summary() {
                return summary;
            }

            @Override
            public Snapshot capture(ToolInvocationContext context) {
                return new Snapshot(
                        value.deepCopy(), List.of(GroundedTestFixtures.serverEvidence()));
            }
        };
    }

    private static CommandCatalogSnapshot commandCatalog() {
        return new CommandCatalogSnapshot(
                Instant.EPOCH,
                List.of(
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give", "give", "literal", "", false, "",
                                List.of("give <targets> <item> [count]"),
                                List.of("give <targets>")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give <targets>", "targets", "argument",
                                "minecraft:entity", false, "",
                                List.of("give <targets> <item> [count]"),
                                List.of("give <targets> <item>")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give <targets> <item>", "item", "argument",
                                "minecraft:item_stack", true, "",
                                List.of("give <targets> <item> [count]"),
                                List.of("give <targets> <item> <count>")),
                        new CommandCatalogSnapshot.CommandNodeSnapshot(
                                "give <targets> <item> <count>", "count", "argument",
                                "brigadier:integer", true, "",
                                List.of("give <targets> <item> [count]"),
                                List.of())));
    }

    private static Path retain(
            Map<String, String> environment,
            BenchmarkCorpus corpus,
            BenchmarkReport report,
            List<AttemptTrace> traces,
            Gson gson)
            throws Exception {
        URI endpoint = URI.create(required(environment, "OPENALLAY_MODEL_BASE_URL"));
        String provider = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() < 0 ? "" : ":" + endpoint.getPort());
        LiveReport retained = new LiveReport(
                1,
                corpus.version(),
                FIXTURE,
                environment.getOrDefault("OPENALLAY_PRODUCT_COMMIT", "unknown"),
                provider,
                required(environment, "OPENALLAY_MODEL"),
                report,
                List.copyOf(traces));
        Path directory = Path.of(environment.getOrDefault(
                "OPENALLAY_BENCHMARK_OUTPUT",
                "build/reports/openallay/benchmarks"));
        Files.createDirectories(directory);
        Path path = directory.resolve(
                "live-" + Instant.now().toString().replace(':', '-') + ".json");
        Files.writeString(path, gson.toJson(retained), StandardCharsets.UTF_8);
        return path;
    }

    private static ModelClient model(Map<String, String> environment, Gson gson) {
        ModelProtocol protocol = ModelProtocol.valueOf(environment
                .getOrDefault("OPENALLAY_MODEL_PROTOCOL", "OPENAI_CHAT")
                .toUpperCase());
        ModelConfig config = new ModelConfig(
                true,
                protocol,
                URI.create(required(environment, "OPENALLAY_MODEL_BASE_URL")),
                required(environment, "OPENALLAY_MODEL"),
                SecretValue.of(required(environment, "OPENALLAY_API_KEY")),
                positive(environment.getOrDefault(
                        "OPENALLAY_CONTEXT_WINDOW_TOKENS", "100000"),
                        "OPENALLAY_CONTEXT_WINDOW_TOKENS"),
                positive(environment.getOrDefault(
                        "OPENALLAY_MAX_OUTPUT_TOKENS", "8192"),
                        "OPENALLAY_MAX_OUTPUT_TOKENS"),
                Duration.ofSeconds(30),
                Duration.ofMinutes(5));
        return switch (protocol) {
            case ANTHROPIC_MESSAGES -> new AnthropicMessagesClient(config, gson);
            case OPENAI_CHAT -> new OpenAiChatClient(config, gson);
        };
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " is required");
        return value;
    }

    private static int positive(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private record AttemptTrace(
            String caseId, int attempt, dev.openallay.agent.trace.LiveAgentTrace trace) {}

    private record LiveReport(
            int schemaVersion,
            String corpusVersion,
            String fixture,
            String productCommit,
            String provider,
            String model,
            BenchmarkReport benchmark,
            List<AttemptTrace> traces) {}

    private static final class FixtureWorld implements WorldObservationCoordinator {
        private static final EvidenceMetadata EVIDENCE = new EvidenceMetadata(
                DataAuthority.DETERMINISTIC_TEST,
                DataCompleteness.COMPLETE,
                Instant.EPOCH,
                "openallay:benchmark_world",
                "openallay:live_benchmark_fixture",
                "26.2",
                "common-test",
                Map.of());

        @Override
        public CompletionStage<BlockObservation> inspect(
                WorldObservationRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            WorldPosition position = request.bounds().from();
            return CompletableFuture.completedFuture(new BlockObservation(
                    request.bounds(),
                    List.of(new WorldBlockSnapshot(
                            "minecraft:oak_log",
                            position,
                            new WorldPosition(0, 0, 0),
                            Map.of("axis", "y"),
                            "",
                            false)),
                    coverage(request.bounds()),
                    EVIDENCE));
        }

        @Override
        public CompletionStage<EntityObservation> entities(
                WorldObservationRequest request, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new EntityObservation(
                    request.bounds(),
                    List.of(new WorldEntitySummary(
                            "benchmark-cow",
                            "minecraft:cow",
                            "Cow",
                            request.bounds().from(),
                            true)),
                    coverage(request.bounds()),
                    EVIDENCE));
        }

        @Override
        public CompletionStage<WorldEntitySnapshot> entity(
                String observationId, CancellationSignal cancellation) {
            cancellation.throwIfCancelled();
            return CompletableFuture.completedFuture(new WorldEntitySnapshot(
                    observationId,
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    "minecraft:cow",
                    "Cow",
                    new WorldPosition(1, 64, 1),
                    Map.of("health", 10.0D, "age", 0),
                    EVIDENCE));
        }

        private static WorldObservationCoverage coverage(WorldBounds bounds) {
            return new WorldObservationCoverage(
                    bounds.volume(), bounds.volume(), true, List.of());
        }
    }
}
