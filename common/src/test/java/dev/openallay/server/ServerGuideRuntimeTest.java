package dev.openallay.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.skill.BundledSkillLoader;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ServerGuideRuntimeTest {
    @Test
    void commandSkillIsAdvertisedOnlyForRequestsWithTheCapturedCapabilityMarker() {
        SkillRepository skills = new SkillRepository(
                new SkillParser(), Set.of("openallay:run_javascript"));
        assertTrue(skills.reload(new BundledSkillLoader().load(), Set.of("ftbquests")));
        skills.setRuntimeDisabledSkills(Set.of("run-game-commands"));

        String ordinaryPrompt = ServerGuideRuntime.systemPrompt(skills, false);
        String commandPrompt = ServerGuideRuntime.systemPrompt(skills, true);

        assertFalse(ordinaryPrompt.contains("<name>run-game-commands</name>"));
        assertTrue(commandPrompt.contains("<name>run-game-commands</name>"));
    }
}
