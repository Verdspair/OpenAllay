package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.settings.extension.ExtensionSettingsView;
import org.junit.jupiter.api.Test;

final class ExtensionSettingsProjectionTest {
    @Test
    void descriptorOnlyProjectionIncludesRootsModulesAndDefaultOffCommands() {
        ExtensionSettingsProjection projection = ExtensionSettingsProjection.from(
                ExtensionSettingsView.from(new JavascriptDataModuleRegistry()),
                CommandCapabilityConfig.defaults(),
                true);

        assertEquals("openallay:run_javascript", projection.runtime().id());
        assertEquals(
                java.util.List.of("source", "roots", "handles"),
                projection.runtime().parameters());
        assertTrue(projection.roots().stream()
                .anyMatch(root -> root.name().equals("items")
                        && root.availability().equals("REQUEST_SCOPED")
                        && root.schema().startsWith("array<")));
        assertEquals(
                java.util.List.of("openallay:crafting"),
                projection.modules().stream().map(ExtensionSettingsProjection.ModuleCard::id).toList());
        assertTrue(projection.adapters().isEmpty());
        assertFalse(projection.experimentalCommands());
        assertTrue(projection.debugMode());
    }

    @Test
    void toggleOnlyChangesExperimentalCommandChoice() {
        ExtensionSettingsProjection original = ExtensionSettingsProjection.from(
                ExtensionSettingsView.defaults(),
                CommandCapabilityConfig.defaults(),
                false);

        ExtensionSettingsProjection toggled = original.toggleExperimentalCommands();

        assertTrue(toggled.experimentalCommands());
        assertEquals(original.runtime(), toggled.runtime());
        assertEquals(original.roots(), toggled.roots());
        assertEquals(original.modules(), toggled.modules());
        assertEquals(original.adapters(), toggled.adapters());
    }
}
