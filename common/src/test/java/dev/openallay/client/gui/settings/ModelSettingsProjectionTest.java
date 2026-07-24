package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.settings.model.ServerModelSettingsView;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelSettingsProjectionTest {
    @Test
    void mixesLocalProfilesWithOneReadOnlyConnectionScopedServerModel() {
        ModelProfileDefinition local = new ModelProfileDefinition(
                "local",
                "Local model",
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                "provider/local",
                "LOCAL_KEY",
                256_000,
                4_096,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                null);
        ModelProfilesConfig config = new ModelProfilesConfig(
                ModelProfilesConfig.SCHEMA_VERSION, "local", List.of(local));
        ModelProfileSettingsView locals = ModelProfileSettingsView.from(
                config,
                List.of(new ModelProfileSettingsView.Resolution(local, true, 256_000, null)),
                java.util.Set.of("LOCAL_KEY"),
                null,
                null);

        ModelSettingsProjection projection = ModelSettingsProjection.from(
                locals,
                new ServerModelSettingsView(
                        true, "server/deepseek", 100_000, 8_192, 6_000));

        assertEquals(2, projection.models().size());
        ModelSettingsProjection.ModelCard server = projection.models().getLast();
        assertEquals(ModelSettingsProjection.Origin.SERVER, server.origin());
        assertEquals("server/deepseek", server.displayName());
        assertTrue(server.available());
        assertFalse(server.editable());
        assertFalse(server.testable());
        assertFalse(server.deletable());
    }
}
