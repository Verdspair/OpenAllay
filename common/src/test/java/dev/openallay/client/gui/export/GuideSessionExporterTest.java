package dev.openallay.client.gui.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.export.GuideSessionExportSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GuideSessionExporterTest {
    private static final Instant NOW = Instant.parse("2026-07-19T12:34:56.789Z");

    @Test
    void writesChronologicalCredentialRedactedTextUnderTheFixedManagedRoot(@TempDir Path game)
            throws Exception {
        GuideSessionExporter exporter = new GuideSessionExporter(game);
        GuideSessionExportSnapshot snapshot = snapshot(
                "API key: sk-" + "abcdefghijklmnopqrstuvwxyz\n\"apiKey\":\"opaque-provider-value\"",
                "authorization: Bearer " + "highly-sensitive-token");

        GuideSessionExporter.ExportedFile exported = exporter.export(snapshot);
        Path file = game.resolve("openallay/exports").resolve(exported.filename());
        String text = Files.readString(file);

        assertTrue(exported.filename().matches(
                "main-20260719-123456-789-[a-f0-9]{12}\\.txt"));
        assertEquals(1, exported.requestCount());
        assertTrue(text.indexOf("User") < text.indexOf("Assistant"));
        assertTrue(text.indexOf("Assistant") < text.indexOf("Tool · get_recipe"));
        assertFalse(text.contains("abcdefghijklmnopqrstuvwxyz"));
        assertFalse(text.contains("opaque-provider-value"));
        assertFalse(text.contains("highly-sensitive-token"));
        assertFalse(text.contains("normalizedSecret"));
        assertEquals(1, Files.list(game.resolve("openallay/exports")).count());
    }

    @Test
    void rejectsAServiceDirectorySymlinkWithoutWritingOutside(@TempDir Path game)
            throws Exception {
        Path outside = Files.createDirectory(game.resolve("outside"));
        try {
            Files.createSymbolicLink(game.resolve("openallay"), outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot("hello", "answer")));
        assertEquals(0, Files.list(outside).count());
    }

    @Test
    void failedManagedRootCreationPublishesNoFile(@TempDir Path game) throws Exception {
        Files.writeString(game.resolve("openallay"), "not a directory");

        assertThrows(GuideSessionExportException.class,
                () -> new GuideSessionExporter(game).export(snapshot("hello", "answer")));
        assertFalse(Files.exists(game.resolve("openallay/exports")));
    }

    @Test
    void marksCancelledPartialResponsesWithoutChangingCompletedExports() {
        GuideSessionExportSnapshot.Request completed = new GuideSessionExportSnapshot.Request(
                NOW,
                GuideRequestStatus.COMPLETED,
                "complete",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant("finished", false)));
        GuideSessionExportSnapshot.Request cancelled = new GuideSessionExportSnapshot.Request(
                NOW.plusSeconds(1),
                GuideRequestStatus.CANCELLED,
                "cancelled",
                List.of(new GuideSessionExportSnapshot.Entry.Assistant("partial ans", false)));

        String text = GuideSessionExporter.format(new GuideSessionExportSnapshot(
                "main", List.of(completed, cancelled), NOW.plusSeconds(2)));

        assertEquals(1, text.split(
                "\\[This request ended before the response completed\\.]", -1).length - 1);
        assertTrue(text.indexOf("finished") < text.indexOf("=== Request 2"));
        assertTrue(text.indexOf("partial ans")
                < text.indexOf("[This request ended before the response completed.]"));
    }

    private static GuideSessionExportSnapshot snapshot(String user, String assistant) {
        GuideSessionExportSnapshot.Request request = new GuideSessionExportSnapshot.Request(
                NOW,
                GuideRequestStatus.COMPLETED,
                user,
                List.of(
                        new GuideSessionExportSnapshot.Entry.Assistant(assistant, false),
                        new GuideSessionExportSnapshot.Entry.Tool(
                                "openallay:get_recipe", GuideToolStatus.SUCCEEDED)));
        return new GuideSessionExportSnapshot("main", List.of(request), NOW);
    }
}
