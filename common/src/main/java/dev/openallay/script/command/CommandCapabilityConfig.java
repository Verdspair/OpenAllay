package dev.openallay.script.command;

/** Strict local configuration for the default-off experimental command bridge. */
public record CommandCapabilityConfig(int schemaVersion, boolean enabled) {
    public static final int SCHEMA_VERSION = 1;

    public CommandCapabilityConfig {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported command capability schema");
        }
    }

    public static CommandCapabilityConfig defaults() {
        return new CommandCapabilityConfig(SCHEMA_VERSION, false);
    }
}
