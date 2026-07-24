package dev.openallay.settings.model;

import dev.openallay.bridge.protocol.CapabilityPayload;
import java.util.Objects;

/** Credential-free, connection-scoped projection of the model hosted by the current server. */
public record ServerModelSettingsView(
        boolean available,
        String canonicalModelId,
        int contextWindowTokens,
        int maxOutputTokens,
        int promptAndToolTokens) {
    public ServerModelSettingsView {
        canonicalModelId = Objects.requireNonNull(canonicalModelId, "canonicalModelId");
        if (available) {
            if (canonicalModelId.isBlank()) {
                throw new IllegalArgumentException("available server model requires an identity");
            }
            if (contextWindowTokens <= 0
                    || maxOutputTokens <= 0
                    || promptAndToolTokens < 0) {
                throw new IllegalArgumentException("available server model requires a valid budget");
            }
        } else if (!canonicalModelId.isEmpty()
                || contextWindowTokens != 0
                || maxOutputTokens != 0
                || promptAndToolTokens != 0) {
            throw new IllegalArgumentException(
                    "unavailable server model cannot retain connection state");
        }
    }

    public static ServerModelSettingsView unavailable() {
        return new ServerModelSettingsView(false, "", 0, 0, 0);
    }

    public static ServerModelSettingsView from(CapabilityPayload capability) {
        Objects.requireNonNull(capability, "capability");
        return capability.serverModel()
                ? new ServerModelSettingsView(
                        true,
                        capability.serverCanonicalModelId(),
                        capability.serverContextWindowTokens(),
                        capability.serverMaxOutputTokens(),
                        capability.serverPromptAndToolTokens())
                : unavailable();
    }
}
