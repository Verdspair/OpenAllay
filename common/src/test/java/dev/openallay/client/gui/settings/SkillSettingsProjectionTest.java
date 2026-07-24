package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.settings.skill.SkillSettingsView;
import org.junit.jupiter.api.Test;

final class SkillSettingsProjectionTest {
    @Test
    void emptySkillCatalogHasNoSyntheticOptionsOrToggleRows() {
        SkillSettingsProjection projection =
                SkillSettingsProjection.from(SkillSettingsView.empty(), false);

        assertTrue(projection.skills().isEmpty());
        assertEquals(0, projection.diagnosticCount());
    }
}
