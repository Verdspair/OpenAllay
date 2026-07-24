package dev.openallay.guide.ui;

import com.google.gson.JsonObject;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolPresentation;
import java.util.List;

/** Concise messages for the JavaScript runtime, Skills, and generic Tool fallback. */
public final class GuideToolPresenter {
    private GuideToolPresenter() {}

    public static List<GuideToolMessage> messages(String toolId, JsonObject normalized) {
        return GuideToolPresentation.messages(toolId, normalized);
    }
}
