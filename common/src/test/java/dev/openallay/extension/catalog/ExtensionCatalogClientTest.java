package dev.openallay.extension.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpResponseHeaders;
import dev.openallay.net.HttpTransport;
import dev.openallay.tool.ToolResult;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionCatalogClientTest {
    @TempDir Path temporary;

    @Test
    void invalidRefreshRetainsLastValidatedDiskGeneration() throws Exception {
        Path cache = temporary.resolve("extensions.json");
        ExtensionCatalogClient first = new ExtensionCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, catalog("sample:one", "1.0.0")),
                Duration.ofSeconds(5),
                new ExtensionCatalogCodec());
        assertInstanceOf(
                ToolResult.Success.class, first.refresh(new CancellationSignal()).join());

        ExtensionCatalogClient failing = new ExtensionCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, "{\"schemaVersion\":99}"),
                Duration.ofSeconds(5),
                new ExtensionCatalogCodec());
        ToolResult<ExtensionCatalogManifest> result =
                failing.refresh(new CancellationSignal()).join();

        assertEquals(
                "catalog_refresh_failed",
                assertInstanceOf(ToolResult.Failure.class, result).code());
        assertEquals(
                "sample:one",
                failing.current().orElseThrow().extensions().getFirst().id());
        assertEquals(
                "sample:one",
                new ExtensionCatalogCodec()
                        .decode(Files.readString(cache))
                        .extensions()
                        .getFirst()
                        .id());
    }

    @Test
    void concurrentRefreshesShareOneInFlightRequest() {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        HttpTransport delayed = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                requests.incrementAndGet();
                return gate.thenApply(ignored -> decode(
                        decoder, 200, catalog("sample:one", "1.0.0")));
            }
        };
        ExtensionCatalogClient client = new ExtensionCatalogClient(
                URI.create("https://example.test/catalog.json"),
                temporary.resolve("cache.json"),
                delayed,
                Duration.ofSeconds(5),
                new ExtensionCatalogCodec());

        CompletableFuture<ToolResult<ExtensionCatalogManifest>> first =
                client.refresh(new CancellationSignal());
        CompletableFuture<ToolResult<ExtensionCatalogManifest>> second =
                client.refresh(new CancellationSignal());

        assertSame(first, second);
        assertEquals(1, requests.get());
        gate.complete(null);
        assertInstanceOf(ToolResult.Success.class, first.join());
    }

    private static HttpTransport transport(int status, String body) {
        return new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                return CompletableFuture.completedFuture(decode(decoder, status, body));
            }
        };
    }

    private static <T> T decode(
            HttpTransport.ResponseDecoder<T> decoder, int status, String body) {
        try {
            return decoder.decode(
                    status,
                    new HttpResponseHeaders(Map.of()),
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new java.util.concurrent.CompletionException(failure);
        }
    }

    static String catalog(String id, String version) {
        return """
                {"schemaVersion":1,"kind":"extension","generatedAt":"2026-07-25T00:00:00Z",
                 "extensions":[{
                   "id":"%s","name":"Sample","version":"%s",
                   "provider":"Community","summary":"Sample Extension",
                   "loaders":["fabric"],"minecraftVersionRange":"[26.2,26.3)",
                   "openAllayApiVersionRange":"[0.2,0.3)",
                   "artifact":"https://example.test/sample.jar",
                   "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                   "modIds":["sample_extension"],"source":"https://example.test/sample"
                 }]}
                """.formatted(id, version);
    }
}
