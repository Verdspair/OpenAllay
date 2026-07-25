package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.schema.CoreJavascriptContract;
import org.junit.jupiter.api.Test;

final class AgentSystemPromptTest {
    @Test
    void keepsCasualConversationToolFreeAndDefinesGroundedRecovery() {
        String prompt = AgentSystemPrompt.compose("""
                  <skill>
                    <name>inspect-game-state</name>
                    <description>Inspect settings</description>
                  </skill>
                """);

        assertTrue(prompt.contains("Greetings and casual conversation"));
        assertTrue(prompt.contains("load a matching vertical Skill once"));
        assertTrue(prompt.contains("single most-specific matching Skill"));
        assertTrue(prompt.contains("Do not reload an unchanged completed Skill"));
        assertTrue(prompt.contains("domain-specific or optional workflow"));
        assertFalse(prompt.contains("ALWAYS matches analyze-game-data"));
        assertTrue(prompt.contains("exact known object or exact ID"));
        assertTrue(prompt.contains("merely because it lists the same Tool"));
        assertTrue(prompt.contains("installed mods, options, packs"));
        assertTrue(prompt.contains("does not require a Skill"));
        assertTrue(prompt.contains("Never repeat a successful call"));
        assertTrue(prompt.contains("Do not spend calls rediscovering documented mc root names"));
        assertTrue(prompt.contains("Make at most one focused discovery call"));
        assertTrue(prompt.contains("scope: complete JavaScript result"));
        assertTrue(prompt.contains("normally sufficient"));
        assertTrue(prompt.contains("materially verifies a candidate universe"));
        assertTrue(prompt.contains("require(\"openallay:crafting\")"));
        assertTrue(prompt.contains("there is no second craftability Tool"));
        assertTrue(prompt.contains("roots [\"world\"]"));
        assertTrue(prompt.contains("there is no mc.world"));
        assertTrue(prompt.contains("no Skill preflight is required"));
        assertTrue(prompt.contains("Retry, continue, do it again"));
        assertTrue(prompt.contains("installed instance represented by its evidence"));
        assertTrue(prompt.contains("Do not relabel that scope as vanilla-only"));
        assertTrue(prompt.contains("submitted without observable confirmation"));
        assertTrue(prompt.contains("independent world.inspect evidence"));
        assertTrue(prompt.contains("current request's Tool definitions"));
        assertTrue(prompt.contains("mc.game.mods.installed"));
        assertTrue(prompt.contains("schema.describe(path)"));
        assertTrue(prompt.contains("workspace.open(handle)"));
        assertFalse(prompt.contains("use inspect_game_state"));
        assertTrue(prompt.contains("<name>inspect-game-state</name>"));
        assertFalse(prompt.contains("server-hosted"));
        assertTrue(prompt.contains("You are OpenAllay,"));
        assertFalse(prompt.contains("OpenAllay (OpenAllay)"));
        assertTrue(prompt.indexOf("## SKILL PREFLIGHT") < prompt.indexOf("## AVAILABLE SKILLS"));
        assertTrue(prompt.indexOf("## AVAILABLE SKILLS") < prompt.indexOf("## EXECUTION"));
    }

    @Test
    void acceptsTheDescriptorDerivedCoreContractExplicitly() {
        String contract = CoreJavascriptContract.render(
                MinecraftAgentHostGraph.declaredOnlyCatalog());

        String prompt = AgentSystemPrompt.compose(" ", contract);

        assertTrue(prompt.contains(contract));
        assertTrue(prompt.indexOf("## CORE JAVASCRIPT") < prompt.indexOf("## AVAILABLE SKILLS"));
    }

    @Test
    void suppliesAnExplicitEmptySkillCatalogWithoutChangingAuthority() {
        String prompt = AgentSystemPrompt.compose("  ");
        assertTrue(prompt.contains("<none/>"));
        assertTrue(prompt.contains("Only registered operations are authorized"));
        assertTrue(prompt.contains("JavaScript is isolated data analysis, not a shell"));
    }
}
