package dev.openallay.extension;

import dev.openallay.script.extension.JavascriptDataModule;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.skill.SkillSource;
import java.util.List;

/** Immutable declarations owned by one Extension. */
public record OpenAllayExtensionContribution(
        List<JavascriptDataModule> dataModules,
        List<JavascriptModuleSource> javascriptModules,
        List<SkillSource> skills,
        List<JavascriptResultViewProvider> resultViews) {
    public OpenAllayExtensionContribution {
        dataModules = List.copyOf(dataModules);
        javascriptModules = List.copyOf(javascriptModules);
        skills = List.copyOf(skills);
        resultViews = List.copyOf(resultViews);
    }

    public static OpenAllayExtensionContribution empty() {
        return new OpenAllayExtensionContribution(List.of(), List.of(), List.of(), List.of());
    }
}
