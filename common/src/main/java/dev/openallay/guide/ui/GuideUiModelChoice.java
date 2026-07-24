package dev.openallay.guide.ui;

import dev.openallay.guide.GuideModelSelection;

/** Friendly, credential-free model choice for one guide session. */
public record GuideUiModelChoice(
        GuideModelSelection selection,
        String displayName,
        ModelOrigin origin,
        boolean editable,
        boolean available,
        boolean selected,
        boolean running) {
    public GuideUiModelChoice {
        java.util.Objects.requireNonNull(selection, "selection");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("model choice display name must not be blank");
        }
        java.util.Objects.requireNonNull(origin, "origin");
        if ((selection.kind() == GuideModelSelection.Kind.SERVER)
                != (origin == ModelOrigin.SERVER)) {
            throw new IllegalArgumentException("model origin must match selection authority");
        }
        if (origin == ModelOrigin.SERVER && editable) {
            throw new IllegalArgumentException("server model choices are read-only");
        }
    }

    public GuideUiModelChoice(
            GuideModelSelection selection,
            String displayName,
            boolean available,
            boolean selected,
            boolean running) {
        this(
                selection,
                displayName,
                selection.kind() == GuideModelSelection.Kind.SERVER
                        ? ModelOrigin.SERVER
                        : ModelOrigin.CLIENT,
                selection.kind() == GuideModelSelection.Kind.CLIENT,
                available,
                selected,
                running);
    }
}
