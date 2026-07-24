package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.settings.skill.SkillCommunityView;
import dev.openallay.settings.skill.SkillSettingsView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkillSettingsProjectionTest {
    @Test
    void emptySkillCatalogHasNoSyntheticOptionsOrToggleRows() {
        SkillSettingsProjection projection =
                SkillSettingsProjection.from(SkillSettingsView.empty(), false);

        assertTrue(projection.skills().isEmpty());
        assertEquals(0, projection.diagnosticCount());
        assertFalse(projection.community().available());
    }

    @Test
    void communityPackagesExposeInstallUpdateAndCompatibilityStates() {
        SkillCommunityView community = new SkillCommunityView(
                true,
                Optional.of(Instant.EPOCH),
                List.of(
                        communityPackage("available", false, false, true),
                        communityPackage("installed", true, false, true),
                        communityPackage("update", true, true, true),
                        communityPackage("incompatible", false, false, false)),
                Optional.empty());

        SkillSettingsProjection projection =
                SkillSettingsProjection.from(SkillSettingsView.empty(), community, true);

        assertEquals(
                SkillSettingsProjection.PackageState.AVAILABLE,
                projection.community().find("available").orElseThrow().state());
        assertEquals(
                SkillSettingsProjection.PackageState.INSTALLED,
                projection.community().find("installed").orElseThrow().state());
        assertEquals(
                SkillSettingsProjection.PackageState.UPDATE_AVAILABLE,
                projection.community().find("update").orElseThrow().state());
        assertEquals(
                SkillSettingsProjection.PackageState.INCOMPATIBLE,
                projection.community().find("incompatible").orElseThrow().state());
        assertTrue(projection.community().find("available").orElseThrow().installable());
        assertTrue(projection.community().find("update").orElseThrow().installable());
        assertFalse(projection.community().find("installed").orElseThrow().installable());
        assertFalse(projection.community().find("incompatible").orElseThrow().installable());
    }

    private static SkillCommunityView.Package communityPackage(
            String id,
            boolean installed,
            boolean update,
            boolean compatible) {
        return new SkillCommunityView.Package(
                id,
                "1.0.0",
                installed,
                update,
                compatible,
                "https://example.test/" + id,
                "https://example.test/" + id + ".zip",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }
}
