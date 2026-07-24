package dev.openallay.bridge.server;

import com.google.gson.Gson;
import dev.openallay.bridge.CorrelationRegistry;
import dev.openallay.bridge.protocol.RemoteCancelPayload;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.bridge.protocol.RemoteToolResultChunkPayload;
import dev.openallay.bridge.protocol.RemoteToolRequestClosePayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.CancellationSignal;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.builtin.RunJavascriptTool;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.trace.replay.ToolResultNormalizer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RemoteToolServer {
    @FunctionalInterface
    public interface ContextProvider {
        CompletableFuture<ToolInvocationContext> capture(
                UUID actorId,
                Set<ContextCapability> capabilities,
                String correlationId,
                CancellationSignal cancellation);
    }

    @FunctionalInterface
    public interface ResponseSink {
        void send(UUID actorId, RemoteToolResultChunkPayload chunk);
    }

    private final ExportedToolPolicy policy;
    private final ContextProvider contexts;
    private final ResponseSink responses;
    private final CorrelationRegistry correlations;
    private final ToolArgumentCodec arguments;
    private final ToolResultNormalizer normalizer;
    private final Gson gson;
    private final int transportChunkBytes;
    private final java.util.concurrent.ConcurrentMap<UUID, java.util.Set<String>> requestScopes =
            new java.util.concurrent.ConcurrentHashMap<>();

    public RemoteToolServer(
            ExportedToolPolicy policy,
            ContextProvider contexts,
            ResponseSink responses,
            CorrelationRegistry correlations,
            Gson gson,
            int transportChunkBytes) {
        if (transportChunkBytes <= 0) {
            throw new IllegalArgumentException("transportChunkBytes must be positive");
        }
        this.policy = policy;
        this.contexts = contexts;
        this.responses = responses;
        this.correlations = correlations;
        this.gson = gson;
        this.transportChunkBytes = transportChunkBytes;
        arguments = new ToolArgumentCodec(gson);
        normalizer = new ToolResultNormalizer(gson);
    }

    public ToolResult<VoidResult> handle(UUID sender, RemoteToolCallPayload payload) {
        Tool<?, ?> tool = policy.find(payload.toolId()).orElse(null);
        if (tool == null) {
            return new ToolResult.Failure<>("remote_tool_denied", "Tool is not exported as read-only");
        }
        CancellationSignal cancellation = new CancellationSignal();
        if (!correlations.register(sender, payload.correlationId(), cancellation)) {
            return new ToolResult.Failure<>("duplicate_correlation", "Correlation ID is already active");
        }
        String requestScope = requestScope(sender, payload.sessionId());
        if (tool instanceof RequestScopeParticipant) {
            requestScopes.computeIfAbsent(sender, ignored ->
                    java.util.concurrent.ConcurrentHashMap.newKeySet()).add(requestScope);
        }
        contexts.capture(
                        sender,
                        tool.descriptor().requiredContext(),
                        requestScope,
                        cancellation)
                .thenCompose(context ->
                        invoke(tool, context, payload.argumentsJson(), cancellation))
                .exceptionally(throwable -> new ToolResult.Failure<>(
                        failureCode(throwable), safeMessage(throwable)))
                .thenAccept(result -> finish(sender, payload.correlationId(), tool, result));
        return new ToolResult.Success<>(new VoidResult());
    }

    public boolean cancel(UUID sender, RemoteCancelPayload payload) {
        return correlations.cancel(sender, payload.correlationId());
    }

    public void closeRequest(UUID sender, RemoteToolRequestClosePayload payload) {
        String scope = requestScope(sender, payload.requestId());
        java.util.Set<String> scopes = requestScopes.get(sender);
        if (scopes == null || !scopes.remove(scope)) {
            return;
        }
        policy.closeRequestScope(scope);
        if (scopes.isEmpty()) {
            requestScopes.remove(sender, scopes);
        }
    }

    public int disconnect(UUID sender) {
        int cancelled = correlations.cancelActor(sender);
        java.util.Set<String> scopes = requestScopes.remove(sender);
        if (scopes != null) {
            scopes.forEach(policy::closeRequestScope);
        }
        return cancelled;
    }

    private void finish(UUID actor, UUID correlation, Tool<?, ?> tool, ToolResult<?> result) {
        if (!correlations.complete(actor, correlation)) {
            return;
        }
        String json = gson.toJson(normalizer.normalize(result, tool.descriptor().outputType()));
        new ResultChunker().split(correlation, json, transportChunkBytes)
                .forEach(chunk -> responses.send(actor, chunk));
    }

    private CompletableFuture<ToolResult<?>> invoke(
            Tool<?, ?> tool,
            ToolInvocationContext context,
            String argumentsJson,
            CancellationSignal cancellation) {
        cancellation.throwIfCancelled();
        com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(argumentsJson);
        if (!parsed.isJsonObject()) {
            return CompletableFuture.completedFuture(
                    new ToolResult.Failure<>(
                            "invalid_arguments", "Remote tool arguments must be an object"));
        }
        com.google.gson.JsonObject object = parsed.getAsJsonObject();
        if (tool.descriptor().id().equals(RunJavascriptTool.ID)
                && requestsCommands(object)) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "remote_tool_denied",
                    "The server JavaScript projection does not expose experimental commands"));
        }
        ToolResult<?> decoded = arguments.decode(object, tool.descriptor().inputType());
        if (decoded instanceof ToolResult.Failure<?> failure) {
            return CompletableFuture.completedFuture(failure);
        }
        cancellation.throwIfCancelled();
        return invokeTypedAsync(
                tool,
                context,
                ((ToolResult.Success<?>) decoded).value(),
                cancellation);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletableFuture<ToolResult<?>> invokeTypedAsync(
            Tool<?, ?> raw,
            ToolInvocationContext context,
            Object input,
            CancellationSignal cancellation) {
        return (CompletableFuture<ToolResult<?>>) (CompletableFuture<?>)
                ((Tool<I, O>) raw).invokeAsync(context, (I) input, cancellation);
    }

    private static String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String failureCode(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                        || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof dev.openallay.script.JavascriptExecutionException failure) {
            return failure.code();
        }
        if (current instanceof dev.openallay.model.ModelClientException failure) {
            return failure.failure().code();
        }
        return "remote_tool_failure";
    }

    private static boolean requestsCommands(com.google.gson.JsonObject arguments) {
        if (!arguments.has("roots") || !arguments.get("roots").isJsonArray()) {
            return false;
        }
        for (com.google.gson.JsonElement root : arguments.getAsJsonArray("roots")) {
            if (root.isJsonPrimitive() && root.getAsJsonPrimitive().isString()
                    && root.getAsString().equals("commands")) {
                return true;
            }
        }
        return false;
    }

    private static String requestScope(UUID actorId, String requestId) {
        return actorId + "/" + requestId;
    }

    public record VoidResult() {}
}
