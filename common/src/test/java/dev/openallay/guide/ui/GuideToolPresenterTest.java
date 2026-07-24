package dev.openallay.guide.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import dev.openallay.guide.GuideToolMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GuideToolPresenterTest {
    @Test
    void delegatesCurrentToolAndGenericPresentations() {
        var javascript = JsonParser.parseString("""
                {"status":"success","value":{"cardinality":2,"complete":true,"preview":[{},{}]}}
                """).getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(
                        GuideToolMessage.Key.ANALYSIS_COMPLETE, "2")),
                GuideToolPresenter.messages("openallay:run_javascript", javascript));

        var generic = JsonParser.parseString(
                "{\"status\":\"success\",\"value\":{\"answer\":42}}")
                .getAsJsonObject();
        assertEquals(
                List.of(GuideToolMessage.of(GuideToolMessage.Key.RESULT_COMPLETED)),
                GuideToolPresenter.messages("openallay:future_tool", generic));
    }
}
