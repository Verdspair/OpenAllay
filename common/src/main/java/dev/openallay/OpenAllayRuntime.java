package dev.openallay;

import dev.openallay.capability.CapabilitySettingsCatalog;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.integration.patchouli.PatchouliMultiblockStore;
import dev.openallay.platform.PlatformService;
import dev.openallay.skill.SkillRepository;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.world.WorldObservationRuntime;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.trace.minecraft.TraceReplayService;
import java.util.Objects;

public record OpenAllayRuntime(
        PlatformService platform,
        ToolRegistry tools,
        KnowledgeRegistry knowledge,
        PatchouliMultiblockStore patchouliMultiblocks,
        JavascriptDataModuleRegistry javascriptModules,
        CommandCapabilityRuntime commands,
        WorldObservationRuntime worldObservations,
        SkillRepository skills,
        DevelopmentToolInspector developmentTools,
        TraceReplayService traceReplay,
        CapabilitySettingsCatalog capabilitySettings) {
    public OpenAllayRuntime {
        Objects.requireNonNull(capabilitySettings, "capabilitySettings");
        Objects.requireNonNull(javascriptModules, "javascriptModules");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(worldObservations, "worldObservations");
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            JavascriptDataModuleRegistry javascriptModules,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay,
            CapabilitySettingsCatalog capabilitySettings) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                javascriptModules,
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime(),
                skills,
                developmentTools,
                traceReplay,
                capabilitySettings);
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay,
            CapabilitySettingsCatalog capabilitySettings) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                new JavascriptDataModuleRegistry(),
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime(),
                skills,
                developmentTools,
                traceReplay,
                capabilitySettings);
    }

    public OpenAllayRuntime(
            PlatformService platform,
            ToolRegistry tools,
            KnowledgeRegistry knowledge,
            PatchouliMultiblockStore patchouliMultiblocks,
            SkillRepository skills,
            DevelopmentToolInspector developmentTools,
            TraceReplayService traceReplay) {
        this(
                platform,
                tools,
                knowledge,
                patchouliMultiblocks,
                new JavascriptDataModuleRegistry(),
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime(),
                skills,
                developmentTools,
                traceReplay,
                new CapabilitySettingsCatalog());
    }
}
