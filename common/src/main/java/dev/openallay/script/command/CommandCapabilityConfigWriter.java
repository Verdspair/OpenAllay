package dev.openallay.script.command;

/** Deterministic writer for the strict command capability document. */
public final class CommandCapabilityConfigWriter {
    public String encode(CommandCapabilityConfig config) {
        return """
                {
                  "schemaVersion": %d,
                  "enabled": %s
                }
                """.formatted(config.schemaVersion(), config.enabled());
    }
}
