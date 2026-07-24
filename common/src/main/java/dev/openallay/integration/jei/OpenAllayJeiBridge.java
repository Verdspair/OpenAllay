package dev.openallay.integration.jei;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.recipe.RecipeViewerNavigatorRegistry;
import dev.openallay.recipe.RecipeViewerProviderRegistry;
import dev.openallay.client.gui.nativeview.NativeDomainViewProviderRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import mezz.jei.api.runtime.IJeiRuntime;

/** Loader JEI plugins delegate lifecycle state into this common integration boundary. */
public final class OpenAllayJeiBridge {
    private static volatile IJeiRuntime runtime;
    private static final AtomicBoolean extensionRegistered = new AtomicBoolean();

    static {
        RecipeViewerProviderRegistry.register(
                "viewer:jei",
                (capturedAt, platform) -> new JeiRecipeProvider(runtime, capturedAt, platform));
        RecipeViewerNavigatorRegistry.register(new JeiRecipeNavigator());
        NativeDomainViewProviderRegistry.register(new JeiNativeRecipeViewProvider(() -> runtime));
    }

    private OpenAllayJeiBridge() {}

    /** Registers the first-party Extension as soon as JEI discovers its loader plugin. */
    public static void registerExtension() {
        if (extensionRegistered.compareAndSet(false, true)) {
            OpenAllayBootstrap.registerExtension(JeiOpenAllayExtension.instance());
        }
    }

    public static void runtimeAvailable(IJeiRuntime value) {
        runtime = java.util.Objects.requireNonNull(value, "value");
        registerExtension();
    }

    public static void runtimeUnavailable() {
        runtime = null;
    }

    static IJeiRuntime runtime() {
        return runtime;
    }
}
