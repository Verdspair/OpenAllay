package dev.openallay.client.gui;

import dev.openallay.client.gui.settings.DiagnosticsSettingsProjection;
import dev.openallay.client.gui.settings.ExtensionSettingsProjection;
import dev.openallay.client.gui.settings.GeneralSettingsProjection;
import dev.openallay.client.gui.settings.HistorySettingsProjection;
import dev.openallay.client.gui.settings.ModelProfileDraft;
import dev.openallay.client.gui.settings.RecipeSettingsProjection;
import dev.openallay.client.gui.settings.SettingsLayout;
import dev.openallay.client.gui.settings.SettingsSection;
import dev.openallay.client.gui.settings.SkillSettingsProjection;
import dev.openallay.guide.e2e.GuideClientE2EConfig;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.catalog.ModelCatalog;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.ClientSettingsSnapshot;
import dev.openallay.settings.SettingsNotice;
import dev.openallay.settings.SettingsOperation;
import dev.openallay.settings.diagnostics.SettingsDiagnosticCard;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.tool.ToolResult;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Native settings shell and model-profile editor backed only by ClientSettingsService. */
public final class OpenAllaySettingsScreen extends Screen {
    private static final int BACKGROUND = 0xE00B0D12;
    private static final int PANEL = 0xE0181B22;
    private static final int PANEL_ALT = 0xE0242933;
    private static final int ACCENT = 0xFF72D5C4;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ERROR = 0xFFFF7D7D;
    private static final String REPOSITORY_URL = "https://github.com/nkanf-dev/OpenAllay";
    private static final Identifier ABOUT_BANNER = Identifier.fromNamespaceAndPath(
            "openallay", "textures/gui/about_banner.png");

    private final ClientSettingsService service;
    private final Runnable returnToGuide;
    private volatile ClientSettingsSnapshot snapshot;
    private AutoCloseable listener;
    private SettingsLayout layout;
    private SettingsSection section = SettingsSection.GENERAL;
    private String selectedProfileId;
    private ModelProfileDraft draft;
    private Confirmation confirmation = Confirmation.NONE;
    private String localNotice = "";
    private boolean draftEnabled;
    private ModelProtocol draftProtocol;
    private int editorScroll;
    private String selectedSkillName;
    private String selectedCommunitySkillId;
    private SkillTab skillTab = SkillTab.INSTALLED;
    private boolean skillEditing;
    private boolean narrowSkillDetail;
    private String skillDraftMarkdown = "";
    private String skillImportPathDraft = "";
    private MultiLineEditBox skillEditor;
    private EditBox skillImportPath;
    private int pageScroll;
    private int pageContentHeight;
    private ClientSettingsService.HistoryConfirmationToken historyConfirmation;
    private EditBox id;
    private EditBox displayName;
    private EditBox baseUrl;
    private EditBox model;
    private PasswordEditBox apiKey;
    private String pendingApiKey = "";
    private EditBox contextWindow;
    private EditBox maxOutput;
    private EditBox connectTimeout;
    private EditBox requestTimeout;
    private EditBox assistantName;
    private String assistantNameDraft;
    private List<String> catalogModelIds = List.of();
    private boolean modelCatalogOpen;
    private int modelCatalogPage;
    private long modelCatalogGeneration;

    public OpenAllaySettingsScreen(
            ClientSettingsService service,
            Runnable returnToGuide) {
        super(Component.translatable("screen.openallay.settings.title"));
        this.service = Objects.requireNonNull(service, "service");
        this.returnToGuide = Objects.requireNonNull(returnToGuide, "returnToGuide");
        this.snapshot = service.snapshot();
        assistantNameDraft = snapshot.display().assistantName();
        selectedSkillName = snapshot.skills().skills().isEmpty()
                ? null
                : snapshot.skills().skills().getFirst().metadata().name();
        selectedCommunitySkillId = snapshot.skillCommunity().packages().isEmpty()
                ? null
                : snapshot.skillCommunity().packages().getFirst().id();
        select(snapshot.models().config().defaultProfileId());
    }

    @Override
    protected void init() {
        layout = SettingsLayout.calculate(width, height);
        addHeaderActions();
        if (layout.wide()) {
            addSectionNavigation();
        }
        if (section == SettingsSection.MODELS) {
            addModelsPage();
        } else if (section == SettingsSection.EXTENSIONS) {
            addExtensionsPage();
        } else if (section == SettingsSection.SKILLS) {
            addSkillsPage();
        } else if (section == SettingsSection.GENERAL) {
            addGeneralPage();
        } else if (section == SettingsSection.HISTORY) {
            addHistoryPage();
        } else if (section == SettingsSection.ABOUT) {
            addAboutPage();
        }
        addFooterActions();
    }

    @Override
    public void added() {
        listener = service.listen(next -> {
            if (layout != null) {
                captureDraft();
            }
            ClientSettingsSnapshot previous = snapshot;
            snapshot = next;
            if (previous.generation() != next.generation()) {
                historyConfirmation = null;
            }
            if (!previous.models().config().equals(next.models().config())
                    || completedReload(
                            previous, next, SettingsOperation.Kind.RELOADING_MODELS)) {
                String retained = next.models().profiles().stream()
                        .map(profile -> profile.definition().id())
                        .filter(profileId -> profileId.equals(selectedProfileId))
                        .findFirst()
                        .orElse(next.models().config().defaultProfileId());
                select(retained);
                confirmation = Confirmation.NONE;
            }
            if (!previous.display().equals(next.display())
                    || completedReload(
                            previous, next, SettingsOperation.Kind.RELOADING_DISPLAY)) {
                assistantNameDraft = next.display().assistantName();
                confirmation = Confirmation.NONE;
            }
            if (!previous.skills().equals(next.skills())) {
                if (selectedSkillName == null || next.skills().find(selectedSkillName).isEmpty()) {
                    selectedSkillName = next.skills().skills().isEmpty()
                            ? null
                            : next.skills().skills().getFirst().metadata().name();
                }
                skillEditing = false;
                skillDraftMarkdown = "";
            }
            SkillSettingsProjection.Community community = skillProjection().community();
            if (selectedCommunitySkillId == null
                    || community.find(selectedCommunitySkillId).isEmpty()) {
                selectedCommunitySkillId = community.packages().isEmpty()
                        ? null
                        : community.packages().getFirst().id();
            }
            if (layout != null) {
                rebuildWidgets();
            }
        });
    }

    @Override
    public void removed() {
        service.cancelConnectionTest();
        service.cancelModelCatalog();
        historyConfirmation = null;
        narrowSkillDetail = false;
        skillEditing = false;
        if (listener != null) {
            try {
                listener.close();
            } catch (Exception ignored) {
                // Detaching a local listener has no recovery action.
            }
            listener = null;
        }
    }

    @Override
    protected void repositionElements() {
        captureDraft();
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        returnToGuide.run();
    }

    @Override
    public void tick() {
        super.tick();
        service.refreshRuntimeState();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if (section == SettingsSection.MODELS && layout.editor().contains(mouseX, mouseY)) {
            if (modelCatalogOpen) {
                int pageSize = modelCatalogPageSize();
                int pages = Math.max(1, (catalogModelIds.size() + pageSize - 1) / pageSize);
                modelCatalogPage = net.minecraft.util.Mth.clamp(
                        modelCatalogPage - (int) Math.signum(scrollY), 0, pages - 1);
                rebuildWidgets();
                return true;
            }
            captureDraft();
            int viewport = Math.max(1, layout.editor().height() - 38);
            int maximum = Math.max(0, 206 - viewport);
            editorScroll = net.minecraft.util.Mth.clamp(
                    editorScroll - (int) Math.round(scrollY * 22), 0, maximum);
            rebuildWidgets();
            return true;
        }
        boolean skillList = section == SettingsSection.SKILLS
                && (layout.wide() ? layout.list() : layout.content()).contains(mouseX, mouseY)
                && (layout.wide() || !narrowSkillDetail);
        boolean scrollablePage = ((section == SettingsSection.DIAGNOSTICS
                        || section == SettingsSection.HISTORY
                        || section == SettingsSection.EXTENSIONS)
                && layout.content().contains(mouseX, mouseY)) || skillList;
        if (scrollablePage) {
            int maximum = Math.max(0, pageContentHeight - layout.content().height() + 18);
            int replacement = net.minecraft.util.Mth.clamp(
                    pageScroll - (int) Math.round(scrollY * 24), 0, maximum);
            if (replacement != pageScroll) {
                pageScroll = replacement;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        panel(graphics, layout.header(), PANEL);
        panel(graphics, layout.content(), PANEL);
        panel(graphics, layout.footer(), PANEL_ALT);
        graphics.text(font, title, layout.header().x() + 8, layout.header().y() + 9, TEXT, false);
        if (layout.wide()) {
            panel(graphics, layout.navigation(), PANEL_ALT);
        }
        if (section == SettingsSection.MODELS) {
            renderModels(graphics);
        } else if (section == SettingsSection.EXTENSIONS) {
            renderExtensions(graphics);
        } else if (section == SettingsSection.SKILLS) {
            renderSkills(graphics);
        } else if (section == SettingsSection.GENERAL) {
            renderGeneral(graphics);
        } else if (section == SettingsSection.HISTORY) {
            renderHistory(graphics);
        } else if (section == SettingsSection.DIAGNOSTICS) {
            renderDiagnostics(graphics);
        } else if (section == SettingsSection.ABOUT) {
            renderAbout(graphics);
        } else {
            renderPlaceholder(graphics);
        }
        renderNotice(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void addHeaderActions() {
        int y = layout.header().y() + 4;
        if (layout.showBack()) {
            int backX = layout.header().right() - 62;
            addRenderableWidget(OpenAllayButton.create(
                            Component.translatable("screen.openallay.settings.back"),
                            ignored -> backOrClose())
                    .bounds(backX, y, 56, 20)
                    .build());
            int sectionX = layout.header().x() + 90;
            addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(section.translationKey()),
                            ignored -> cycleSection())
                    .bounds(sectionX, y, Math.max(50, backX - sectionX - 4), 20)
                    .build());
        }
    }

    private void addSectionNavigation() {
        int x = layout.navigation().x() + 6;
        int y = layout.navigation().y() + 8;
        int buttonWidth = layout.navigation().width() - 12;
        for (SettingsSection candidate : SettingsSection.topLevel()) {
            Button button = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(candidate.translationKey()),
                            ignored -> switchSection(candidate))
                    .selected(candidate == section)
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            button.active = candidate != section;
            y += 24;
        }
    }

    private void addModelsPage() {
        if (layout.wide()) {
            addProfileList();
        }
        if (modelCatalogOpen) {
            addModelCatalogPicker();
        } else {
            addEditor();
        }
    }

    private void addGeneralPage() {
        GeneralSettingsProjection general = project(snapshot).general();
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = area.y() + 44;
        int width = Math.min(280, Math.max(120, area.width() - 20));
        int saveWidth = Math.min(72, Math.max(50, width / 4));
        assistantName = new EditBox(
                font,
                x,
                y,
                Math.max(60, width - saveWidth - 4),
                20,
                Component.translatable(general.assistantNameLabelKey()));
        assistantName.setValue(assistantNameDraft);
        assistantName.setMaxLength(Integer.MAX_VALUE);
        assistantName.setResponder(value -> assistantNameDraft = value);
        addRenderableWidget(assistantName);
        Button saveName = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                "screen.openallay.settings.general.assistant_name.save"),
                        ignored -> saveAssistantName(general))
                .bounds(x + width - saveWidth, y, saveWidth, 20)
                .build());
        saveName.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        Button debug = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                general.debugLabelKey()).copy().append(" · ")
                                .append(Component.translatable(general.debugStatusKey())),
                        ignored -> accept(service.saveDisplay(general.toggleDebug())))
                .bounds(x, y + 34, width, 22)
                .build());
        debug.setTooltip(Tooltip.create(
                Component.translatable(general.debugDescriptionKey())));
        debug.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        Button animations = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                general.animationsLabelKey()).copy().append(" · ")
                                .append(Component.translatable(general.animationsStatusKey())),
                        ignored -> accept(service.saveDisplay(general.toggleAnimations())))
                .bounds(x, y + 64, width, 22)
                .build());
        animations.setTooltip(Tooltip.create(
                Component.translatable(general.animationsDescriptionKey())));
        animations.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
    }

    private void addAboutPage() {
        SettingsLayout.Rect area = layout.editor();
        int width = Math.min(240, Math.max(120, area.width() - 20));
        addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.about.copy_repository"),
                        ignored -> copyRepositoryUrl())
                .bounds(area.x() + 10, area.bottom() - 30, width, 20)
                .build());
    }

    private void saveAssistantName(GeneralSettingsProjection general) {
        try {
            accept(service.saveDisplay(general.renameAssistant(assistantNameDraft)));
        } catch (IllegalArgumentException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.general.assistant_name.invalid").getString();
        }
    }

    private void copyRepositoryUrl() {
        try {
            minecraft.keyboardHandler.setClipboard(REPOSITORY_URL);
            localNotice = Component.translatable(
                    "screen.openallay.settings.about.copy_success").getString();
        } catch (RuntimeException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.about.copy_failed").getString();
        }
    }

    private void addHistoryPage() {
        HistorySettingsProjection history = project(snapshot).history();
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = area.y() + 64 - pageScroll;
        int width = Math.min(360, Math.max(140, area.width() - 20));
        for (HistorySettingsProjection.ActionRow row : history.actions()) {
            Button button = OpenAllayButton.create(
                            historyActionLabel(row),
                            ignored -> activateHistory(row.action()))
                    .bounds(x, y, width, 22)
                    .build();
            button.setTooltip(Tooltip.create(Component.translatable(row.descriptionKey())));
            button.active = row.enabled();
            if (y >= area.y() + 48 && y + 22 <= area.bottom() - 4) {
                addRenderableWidget(button);
            }
            y += 30;
        }
        pageContentHeight = Math.max(0, y + pageScroll - area.y());
    }

    private void addProfileList() {
        int x = layout.list().x() + 6;
        int y = layout.list().y() + 26;
        int buttonWidth = layout.list().width() - 12;
        for (ModelCard card : project(snapshot).models()) {
            Component label = Component.literal(
                    (card.defaultProfile() ? "★ " : "") + card.displayName());
            Button button = addRenderableWidget(OpenAllayButton.create(
                            label,
                            ignored -> selectAndRebuild(card.id()))
                    .selected(card.id().equals(selectedProfileId))
                    .bounds(x, y, buttonWidth, 22)
                    .build());
            button.active = !card.id().equals(selectedProfileId);
            y += 26;
            if (y > layout.list().bottom() - 50) {
                break;
            }
        }
        addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.add"),
                        ignored -> createProfile())
                .bounds(x, layout.list().bottom() - 28, buttonWidth, 20)
                .build());
    }

    private void addExtensionsPage() {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 9;
        int y = area.y() + 48;
        int width = area.width() - 18;
        ExtensionSettingsProjection extensions = extensionProjection();
        Button commands = OpenAllayButton.create(
                        Component.translatable(
                                extensions.experimentalCommands()
                                        ? "screen.openallay.settings.extensions.commands.disable"
                                        : "screen.openallay.settings.extensions.commands.enable"),
                        ignored -> accept(service.saveExperimentalCommands(
                                !extensions.experimentalCommands())))
                .bounds(x, y, width, 20)
                .build();
        commands.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        commands.setTooltip(Tooltip.create(Component.translatable(
                "screen.openallay.settings.extensions.commands.description")));
        addRenderableWidget(commands);
        pageContentHeight = Math.max(
                0,
                82
                        + extensionRuntimeHeight(extensions.runtime(), width)
                        + extensionCatalogHeight(extensions, width));
    }

    private void addSkillsPage() {
        SkillSettingsProjection projection = skillProjection();
        SettingsLayout.Rect listArea = layout.wide() ? layout.list() : layout.content();
        int x = listArea.x() + 7;
        int y = listArea.y() + 7;
        int width = listArea.width() - 14;
        boolean showList = layout.wide() || !narrowSkillDetail;
        if (showList) {
            int tabWidth = Math.max(40, (width - 4) / 2);
            Button installedTab = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
                                    "screen.openallay.settings.skills.tab.installed"),
                            ignored -> selectSkillTab(SkillTab.INSTALLED))
                    .selected(skillTab == SkillTab.INSTALLED)
                    .bounds(x, y, tabWidth, 20)
                    .build());
            installedTab.active = skillTab != SkillTab.INSTALLED;
            Button communityTab = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
                                    "screen.openallay.settings.skills.tab.community"),
                            ignored -> selectSkillTab(SkillTab.COMMUNITY))
                    .selected(skillTab == SkillTab.COMMUNITY)
                    .bounds(x + tabWidth + 4, y, Math.max(40, width - tabWidth - 4), 20)
                    .build());
            communityTab.active = skillTab != SkillTab.COMMUNITY;
            y += 28 - pageScroll;
        }

        if (showList && skillTab == SkillTab.INSTALLED) {
            for (SkillSettingsProjection.Skill skill : projection.skills()) {
                Component label = Component.literal(skill.name()).copy().append(" · ")
                        .append(Component.translatable(skill.localOverride()
                                ? "screen.openallay.settings.skills.local"
                                : "screen.openallay.settings.skills.bundled"));
                Button button = addRenderableWidget(OpenAllayButton.create(label, ignored -> {
                            selectedSkillName = skill.name();
                            narrowSkillDetail = true;
                            skillEditing = false;
                            skillDraftMarkdown = "";
                            rebuildWidgets();
                        })
                        .selected(skill.name().equals(selectedSkillName))
                        .bounds(x, y, width, 22)
                        .build());
                button.active = !skill.name().equals(selectedSkillName);
                button.visible = y >= listArea.y() + 31 && y + 22 <= listArea.bottom() - 4;
                y += 26;
            }
            pageContentHeight = 28 + projection.skills().size() * 26;
        }
        if (showList && skillTab == SkillTab.COMMUNITY) {
            for (SkillSettingsProjection.Package skill : projection.community().packages()) {
                Component label = Component.literal(skill.id()).copy().append(" · ")
                        .append(Component.translatable(skillStateKey(skill.state())));
                Button button = addRenderableWidget(OpenAllayButton.create(label, ignored -> {
                            selectedCommunitySkillId = skill.id();
                            narrowSkillDetail = true;
                            rebuildWidgets();
                        })
                        .selected(skill.id().equals(selectedCommunitySkillId))
                        .bounds(x, y, width, 22)
                        .build());
                button.active = !skill.id().equals(selectedCommunitySkillId);
                int listBottomInset = layout.wide() ? 4 : 60;
                button.visible = y >= listArea.y() + 31
                        && y + 22 <= listArea.bottom() - listBottomInset;
                y += 26;
            }
            pageContentHeight = 28
                    + projection.community().packages().size() * 26
                    + (layout.wide() ? 0 : 56);
        }

        if ((layout.wide() || narrowSkillDetail) && skillTab == SkillTab.INSTALLED) {
            selectedSkill().ifPresent(skill -> {
            SettingsLayout.Rect area = layout.editor();
            int editorX = area.x() + 9;
            int editorY = area.y() + 58;
            int editorWidth = area.width() - 18;
            if (skillEditing) {
                skillEditor = MultiLineEditBox.builder()
                        .setX(editorX)
                        .setY(editorY)
                        .setPlaceholder(Component.translatable(
                                "screen.openallay.settings.skills.editor_placeholder"))
                        .build(
                                font,
                                editorWidth,
                                Math.max(70, area.bottom() - editorY - 34),
                                Component.translatable("screen.openallay.settings.skills.editor"));
                skillEditor.setValue(skillDraftMarkdown, true);
                skillEditor.setValueListener(value -> skillDraftMarkdown = value);
                addRenderableWidget(skillEditor);
                addRenderableWidget(OpenAllayButton.create(
                                Component.translatable("screen.openallay.settings.save"),
                                ignored -> saveSkillOverride())
                        .bounds(editorX, area.bottom() - 26, Math.min(120, editorWidth), 20)
                        .build());
                addRenderableWidget(OpenAllayButton.create(
                                Component.translatable("screen.openallay.settings.cancel"),
                                ignored -> {
                                    skillEditing = false;
                                    skillDraftMarkdown = "";
                                    rebuildWidgets();
                                })
                        .bounds(
                                editorX + Math.min(120, editorWidth) + 4,
                                area.bottom() - 26,
                                Math.min(100, Math.max(50, editorWidth - 124)),
                                20)
                        .build());
            } else {
                addRenderableWidget(OpenAllayButton.create(
                                Component.translatable(skill.createsOverrideOnSave()
                                        ? "screen.openallay.settings.skills.create_override"
                                        : "screen.openallay.settings.skills.edit_override"),
                                ignored -> {
                                    skillEditing = true;
                                    skillDraftMarkdown = skill.markdown();
                                    rebuildWidgets();
                                })
                        .bounds(editorX, area.bottom() - 26, Math.min(160, editorWidth), 20)
                        .build());
                if (skill.canDeleteOverride()) {
                    addRenderableWidget(OpenAllayButton.create(
                                    Component.translatable(
                                            "screen.openallay.settings.skills.delete_override"),
                                    ignored -> accept(service.deleteSkillOverride(skill.name())))
                            .bounds(
                                    editorX + Math.min(160, editorWidth) + 4,
                                    area.bottom() - 26,
                                    Math.min(140, Math.max(60, editorWidth - 164)),
                                    20)
                            .build());
                }
            }
            });
        }
        if ((layout.wide() || narrowSkillDetail) && skillTab == SkillTab.COMMUNITY) {
            addCommunitySkillActions(true);
        } else if (!layout.wide() && skillTab == SkillTab.COMMUNITY) {
            addCommunitySkillActions(false);
        }
    }

    private void addCommunitySkillActions(boolean includeInstall) {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 9;
        int width = area.width() - 18;
        int actionY = area.bottom() - 54;
        SkillSettingsProjection.Package selected = selectedCommunitySkill().orElse(null);
        int refreshX = x;
        int refreshWidth = width;
        if (includeInstall && selected != null && selected.installable()) {
            int installWidth = Math.max(60, (width - 4) / 2);
            Button install = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
                                    selected.state()
                                                    == SkillSettingsProjection.PackageState.UPDATE_AVAILABLE
                                            ? "screen.openallay.settings.skills.community.update"
                                            : "screen.openallay.settings.skills.community.install"),
                            ignored -> accept(service.installCommunitySkill(selected.id())))
                    .bounds(x, actionY, installWidth, 20)
                    .build());
            install.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
            refreshX = x + installWidth + 4;
            refreshWidth = Math.max(60, width - installWidth - 4);
        }
        Button refresh = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                "screen.openallay.settings.skills.community.refresh"),
                        ignored -> accept(service.refreshSkillCommunity()))
                .bounds(refreshX, actionY, refreshWidth, 20)
                .build());
        refresh.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;

        int importWidth = Math.min(84, Math.max(56, width / 4));
        skillImportPath = new EditBox(
                font,
                x,
                area.bottom() - 27,
                Math.max(50, width - importWidth - 4),
                20,
                Component.translatable(
                        "screen.openallay.settings.skills.community.import_path"));
        skillImportPath.setValue(skillImportPathDraft);
        skillImportPath.setMaxLength(Integer.MAX_VALUE);
        skillImportPath.setResponder(value -> skillImportPathDraft = value);
        skillImportPath.setHint(Component.translatable(
                "screen.openallay.settings.skills.community.import_hint"));
        addRenderableWidget(skillImportPath);
        Button importButton = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                "screen.openallay.settings.skills.community.import"),
                        ignored -> importLocalSkill())
                .bounds(x + width - importWidth, area.bottom() - 27, importWidth, 20)
                .build());
        importButton.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE
                && !skillImportPathDraft.isBlank();
    }

    private void addEditor() {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 8;
        int y = area.y() + 8;
        int editorWidth = Math.max(100, area.width() - 16);
        int toggleWidth = Math.min(118, Math.max(70, (editorWidth - 8) / 2));
        addRenderableWidget(OpenAllayButton.create(protocolLabel(), ignored -> cycleProtocol())
                .bounds(x, y, toggleWidth, 20).build());
        addRenderableWidget(OpenAllayButton.create(enabledLabel(), ignored -> toggleEnabled())
                .bounds(x + toggleWidth + 6, y, toggleWidth, 20).build());
        y += 32 - editorScroll;
        int labelWidth = Math.min(104, Math.max(72, editorWidth / 3));
        int inputX = x + labelWidth;
        int inputWidth = Math.max(70, editorWidth - labelWidth);
        id = field(inputX, y, inputWidth, "screen.openallay.settings.models.id", draft.id());
        y += 22;
        displayName = field(
                inputX, y, inputWidth, "screen.openallay.settings.models.name", draft.displayName());
        y += 22;
        baseUrl = field(
                inputX, y, inputWidth, "screen.openallay.settings.models.base_url", draft.baseUrl());
        baseUrl.setResponder(value -> {
            confirmation = Confirmation.NONE;
            invalidateModelCatalog();
        });
        y += 22;
        int fetchWidth = inputWidth >= 130 ? 46 : 30;
        int chooseWidth = inputWidth >= 130 ? 32 : 20;
        int modelWidth = Math.max(20, inputWidth - fetchWidth - chooseWidth - 6);
        model = field(
                inputX, y, modelWidth, "screen.openallay.settings.models.model_id", draft.model());
        Button fetch = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.fetch"),
                        ignored -> fetchModelCatalog())
                .bounds(inputX + modelWidth + 3, y, fetchWidth, 18)
                .build());
        fetch.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        fetch.visible = model.visible;
        Button choose = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.choose"),
                        ignored -> {
                            captureDraft();
                            modelCatalogOpen = true;
                            modelCatalogPage = 0;
                            rebuildWidgets();
                        })
                .bounds(inputX + modelWidth + fetchWidth + 6, y, chooseWidth, 18)
                .build());
        choose.active = !catalogModelIds.isEmpty();
        choose.visible = model.visible;
        y += 22;
        apiKey = passwordField(inputX, y, inputWidth);
        y += 22;
        contextWindow = field(
                inputX,
                y,
                inputWidth,
                "screen.openallay.settings.models.context_window",
                draft.contextWindowTokens());
        y += 22;
        maxOutput = field(
                inputX, y, inputWidth, "screen.openallay.settings.models.max_output", draft.maxOutputTokens());
        y += 22;
        connectTimeout = field(
                inputX,
                y,
                inputWidth,
                "screen.openallay.settings.models.connect_timeout",
                draft.connectTimeoutSeconds());
        y += 22;
        requestTimeout = field(
                inputX,
                y,
                inputWidth,
                "screen.openallay.settings.models.request_timeout",
                draft.requestTimeoutSeconds());
    }

    private EditBox field(int x, int y, int width, String narrationKey, String value) {
        EditBox field = new EditBox(
                font, x, y, width, 18, Component.translatable(narrationKey));
        field.setValue(value == null ? "" : value);
        field.setMaxLength(2048);
        field.setResponder(ignored -> confirmation = Confirmation.NONE);
        field.setVisible(y >= layout.editor().y() + 30
                && y + 18 <= layout.editor().bottom());
        return addRenderableWidget(field);
    }

    private PasswordEditBox passwordField(int x, int y, int width) {
        PasswordEditBox field = new PasswordEditBox(
                font,
                x,
                y,
                width,
                18,
                Component.translatable("screen.openallay.settings.models.api_key"));
        field.setValue(pendingApiKey);
        boolean saved = selectedView().map(
                        ModelProfileSettingsView.Profile::credentialStoredLocally)
                .orElse(false);
        boolean environment = selectedView().map(
                        ModelProfileSettingsView.Profile::credentialFromEnvironment)
                .orElse(false);
        field.setHint(Component.translatable(saved
                ? "screen.openallay.settings.models.api_key_saved_hint"
                : environment
                        ? "screen.openallay.settings.models.api_key_environment_hint"
                        : "screen.openallay.settings.models.api_key_enter_hint"));
        field.setMaxLength(4096);
        field.setResponder(value -> {
            pendingApiKey = value;
            confirmation = Confirmation.NONE;
            invalidateModelCatalog();
        });
        field.setVisible(y >= layout.editor().y() + 30
                && y + 18 <= layout.editor().bottom());
        return addRenderableWidget(field);
    }

    private void addFooterActions() {
        List<Action> actions = footerActions();
        int gap = 4;
        int columns = Math.min(4, actions.size());
        int available = layout.footer().width() - 12;
        int buttonWidth = Math.max(34, (available - gap * (columns - 1)) / columns);
        for (int index = 0; index < actions.size(); index++) {
            Action action = actions.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = layout.footer().x() + 6 + column * (buttonWidth + gap);
            int y = layout.footer().y() + 4 + row * 23;
            Button button = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(action.translationKey()),
                            ignored -> action.action().run())
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            button.active = actionEnabled(action.translationKey());
        }
    }

    private List<Action> footerActions() {
        return switch (section) {
            case MODELS -> modelCatalogOpen ? List.of(
                    new Action("screen.openallay.settings.models.catalog_close", () -> {
                        modelCatalogOpen = false;
                        rebuildWidgets();
                    }),
                    new Action("screen.openallay.settings.done", this::onClose)) : List.of(
                    new Action("screen.openallay.settings.save", this::saveCurrent),
                    new Action(reloadKey(), this::reloadCurrent),
                    new Action(deleteKey(), this::delete),
                    new Action("screen.openallay.settings.models.default", this::makeDefault),
                    new Action(testKey(), this::testConnection),
                    new Action("screen.openallay.settings.cancel", this::cancel),
                    new Action("screen.openallay.settings.models.refresh", this::refreshMetadata),
                    new Action("screen.openallay.settings.done", this::onClose));
            case EXTENSIONS -> List.of(
                    new Action("screen.openallay.settings.done", this::onClose));
            case SKILLS -> List.of(
                    new Action(
                            "screen.openallay.settings.reload",
                            () -> accept(service.reloadSkills(true))),
                    new Action("screen.openallay.settings.done", this::onClose));
            case GENERAL -> List.of(
                    new Action(
                            "screen.openallay.settings.reload",
                            () -> accept(service.reloadDisplay())),
                    new Action("screen.openallay.settings.done", this::onClose));
            case HISTORY, DIAGNOSTICS, ABOUT ->
                    List.of(new Action("screen.openallay.settings.done", this::onClose));
        };
    }

    private boolean actionEnabled(String key) {
        boolean busy = snapshot.operation().kind() != SettingsOperation.Kind.IDLE;
        if (key.equals("screen.openallay.settings.cancel")) {
            return snapshot.operation().kind() == SettingsOperation.Kind.TESTING_CONNECTION
                    || snapshot.operation().kind()
                            == SettingsOperation.Kind.FETCHING_MODEL_CATALOG;
        }
        if (key.equals("screen.openallay.settings.done")) {
            return true;
        }
        return !busy;
    }

    private void renderModels(GuiGraphicsExtractor graphics) {
        if (layout.wide()) {
            graphics.text(
                    font,
                    Component.translatable("screen.openallay.settings.models.profiles"),
                    layout.list().x() + 8,
                    layout.list().y() + 9,
                    ACCENT,
                    false);
        }
        SettingsLayout.Rect area = layout.editor();
        if (modelCatalogOpen) {
            graphics.text(
                    font,
                    Component.translatable(
                            "screen.openallay.settings.models.catalog_title",
                            catalogModelIds.size()),
                    area.x() + 8,
                    area.y() + 10,
                    ACCENT,
                    false);
            if (catalogModelIds.isEmpty()) {
                graphics.text(font,
                        Component.translatable("screen.openallay.settings.models.catalog_empty"),
                        area.x() + 8, area.y() + 34, MUTED, false);
            }
            return;
        }
        int x = area.x() + 8;
        int y = area.y() + 43 - editorScroll;
        String[] labels = {
            "screen.openallay.settings.models.id",
            "screen.openallay.settings.models.name",
            "screen.openallay.settings.models.base_url",
            "screen.openallay.settings.models.model_id",
            "screen.openallay.settings.models.api_key",
            "screen.openallay.settings.models.context_window",
            "screen.openallay.settings.models.max_output",
            "screen.openallay.settings.models.connect_timeout",
            "screen.openallay.settings.models.request_timeout"
        };
        for (String label : labels) {
            if (y >= area.y() + 30 && y + 18 <= area.bottom()) {
                graphics.text(font, Component.translatable(label), x, y + 5, MUTED, false);
            }
            y += 22;
        }
        int statusY = Math.min(area.bottom() - 13, y + 3);
        selectedView().ifPresent(profile -> {
            int color = profile.available() ? 0xFF7FC8A9 : 0xFFFFD479;
            Component status = Component.translatable(
                            profile.available()
                                    ? "screen.openallay.settings.models.available"
                                    : "screen.openallay.settings.models.unavailable")
                    .copy().append(" · ")
                    .append(Component.translatable(pendingApiKey.isBlank()
                            ? (profile.credentialStoredLocally()
                                    ? "screen.openallay.settings.models.api_key_saved"
                                    : profile.credentialFromEnvironment()
                                            ? "screen.openallay.settings.models.api_key_environment"
                                            : "screen.openallay.settings.models.api_key_not_set")
                            : "screen.openallay.settings.models.api_key_replace"));
            graphics.text(font, status, x, statusY, color, false);
        });
    }

    private void renderGeneral(GuiGraphicsExtractor graphics) {
        GeneralSettingsProjection general = project(snapshot).general();
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                Component.translatable(general.titleKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        graphics.text(
                font,
                Component.translatable(general.assistantNameLabelKey()),
                area.x() + 10,
                area.y() + 31,
                MUTED,
                false);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                Component.translatable(general.assistantNameDescriptionKey()),
                Math.max(80, area.width() - 20));
        int y = area.y() + 136;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.text(font, line, area.x() + 10, y, MUTED, false);
            y += 10;
        }
        y += 5;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.translatable(general.debugDescriptionKey()),
                Math.max(80, area.width() - 20))) {
            graphics.text(font, line, area.x() + 10, y, MUTED, false);
            y += 10;
        }
        y += 5;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.translatable(general.animationsDescriptionKey()),
                Math.max(80, area.width() - 20))) {
            graphics.text(font, line, area.x() + 10, y, MUTED, false);
            y += 10;
        }
        pageContentHeight = y - area.y();
    }

    private void renderAbout(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int contentWidth = Math.max(100, area.width() - 20);
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.about.title"),
                x,
                area.y() + 12,
                ACCENT,
                false);
        int bannerWidth = Math.min(contentWidth, 512);
        int bannerHeight = Math.max(54, bannerWidth * 9 / 16);
        int bannerX = x + Math.max(0, (contentWidth - bannerWidth) / 2);
        int bannerY = area.y() + 31;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ABOUT_BANNER,
                bannerX,
                bannerY,
                0.0F,
                0.0F,
                bannerWidth,
                bannerHeight,
                1024,
                576,
                1024,
                576);
        int y = bannerY + bannerHeight + 12;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.translatable("screen.openallay.settings.about.description"),
                contentWidth)) {
            graphics.text(font, line, x, y, TEXT, false);
            y += 10;
        }
        y += 8;
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.about.repository"),
                x,
                y,
                MUTED,
                false);
        graphics.text(font, REPOSITORY_URL, x, y + 12, ACCENT, false);
        pageContentHeight = y + 24 - area.y();
    }

    private void renderHistory(GuiGraphicsExtractor graphics) {
        HistorySettingsProjection history = project(snapshot).history();
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                Component.translatable(history.titleKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        Component status = Component.translatable(history.scopeLabelKey())
                .copy().append(" · ")
                .append(Component.translatable(history.statusKey()));
        graphics.text(font, status, area.x() + 10, area.y() + 31, MUTED, false);
        int actionsBottom = area.y() + 64 - pageScroll + history.actions().size() * 30;
        pageContentHeight = Math.max(0, actionsBottom + pageScroll - area.y());
    }

    private void renderDiagnostics(GuiGraphicsExtractor graphics) {
        DiagnosticsSettingsProjection diagnostics = project(snapshot).diagnostics();
        SettingsLayout.Rect area = layout.editor();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int x = area.x() + 8;
        int width = Math.max(80, area.width() - 16);
        int y = area.y() + 10 - pageScroll;
        y = settingsHeading(
                graphics,
                Component.translatable(diagnostics.titleKey()),
                x,
                y,
                width,
                ACCENT);
        for (DiagnosticsSettingsProjection.CardRow card : diagnostics.cards()) {
            int cardHeight = 34 + (card.noteKeys().size() + card.metrics().size()) * 11;
            graphics.fill(x, y, x + width, y + cardHeight, PANEL_ALT);
            graphics.text(
                    font,
                    Component.literal(card.statusIcon() + " ")
                            .append(Component.translatable(card.titleKey())),
                    x + 7,
                    y + 6,
                    TEXT,
                    false);
            graphics.text(
                    font,
                    Component.translatable(card.statusTextKey()),
                    x + 7,
                    y + 18,
                    MUTED,
                    false);
            int metricY = y + 30;
            for (String noteKey : card.noteKeys()) {
                graphics.text(font, Component.translatable(noteKey), x + 12, metricY, MUTED, false);
                metricY += 11;
            }
            for (SettingsDiagnosticCard.Metric metric : card.metrics()) {
                graphics.text(
                        font,
                        Component.translatable(metric.labelKey(), metric.value()),
                        x + 12,
                        metricY,
                        MUTED,
                        false);
                metricY += 11;
            }
            y += cardHeight + 6;
        }
        if (diagnostics.debug().isPresent()) {
            y = renderDebugDiagnostics(
                    graphics, diagnostics.debug().orElseThrow(), x, y + 4, width);
        }
        pageContentHeight = Math.max(0, y + pageScroll - area.y() + 8);
        graphics.disableScissor();
    }

    private int renderDebugDiagnostics(
            GuiGraphicsExtractor graphics,
            DiagnosticsSettingsProjection.DebugSection section,
            int x,
            int y,
            int width) {
        SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics debug = section.diagnostics();
        y = settingsHeading(
                graphics,
                Component.translatable(section.titleKey()),
                x,
                y,
                width,
                0xFFFFD479);
        y = debugLine(graphics, x, y, width,
                "screen.openallay.settings.diagnostics.debug.settings_generation",
                Long.toString(debug.settingsGeneration()));
        y = debugLine(graphics, x, y, width,
                "screen.openallay.settings.diagnostics.debug.database_schema",
                Integer.toString(debug.databaseSchema()));
        for (SettingsDiagnosticsSnapshot.DebugModelProfile model : debug.models()) {
            String value = model.profileId() + " · " + model.protocol()
                    + " · " + model.endpointAuthority() + " · " + model.modelId()
                    + " · context=" + model.effectiveContextWindowTokens()
                    + " · credential=" + model.credentialPresent();
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.model", value);
        }
        SettingsDiagnosticsSnapshot.DebugCapabilities capabilities = debug.capabilities();
        y = debugLine(graphics, x, y, width,
                "screen.openallay.settings.diagnostics.debug.capabilities",
                capabilities.catalogEntries() + "/" + capabilities.enabledEntries()
                        + " · sources=" + capabilities.knowledgeSources()
                        + " · tools=" + capabilities.tools()
                        + " · skills=" + capabilities.skills());
        if (debug.guide().isPresent()) {
            SettingsDiagnosticsSnapshot.DebugGuide guide = debug.guide().orElseThrow();
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.guide",
                    guide.scopeKind() + " · session=" + guide.selectedSessionId()
                            + " · " + guide.modelMode()
                            + " · persistence=" + guide.persistenceState()
                            + " · generations=" + guide.committedGeneration()
                            + "/" + guide.submittedGeneration()
                            + " · pending=" + guide.pendingWrites()
                            + " · active=" + guide.activeRequestCount());
            if (guide.request().isPresent()) {
                SettingsDiagnosticsSnapshot.DebugRequest request =
                        guide.request().orElseThrow();
                y = debugLine(graphics, x, y, width,
                        "screen.openallay.settings.diagnostics.debug.request",
                        request.requestId() + " · " + request.topology()
                                + " · " + request.status()
                                + " · retryMs=" + request.retryAfterMillis()
                                + " · tools=" + request.toolCount()
                                + " · sources=" + request.sourceCount());
            }
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.context",
                    "checkpoints=" + guide.context().checkpointCount()
                            + " · failed=" + guide.context().failedCheckpoints()
                            + " · estimatedTokens="
                            + guide.context().estimatedProjectionTokens());
            SettingsDiagnosticsSnapshot.DebugHistory history = guide.history();
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.history_window",
                    "loaded=" + history.loadedRequests() + "/" + history.totalRequests()
                            + " · cursorCounts=" + history.firstLoadedCount()
                            + ".." + history.lastLoadedCount()
                            + " · page=" + history.pageState());
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.presentation",
                    "cache=" + history.cacheHits() + "/" + history.cacheMisses()
                            + " · fallbacks=" + history.semanticFallbackCount());
        }
        for (SettingsDiagnosticsSnapshot.DebugSource source : debug.sources()) {
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.source",
                    source.sourceId() + " · " + source.state()
                            + " · generation=" + source.generation()
                            + " · count=" + source.itemCount()
                            + (source.failureCode() == null
                                    ? ""
                                    : " · failure=" + source.failureCode()));
        }
        for (String code : debug.failureCodes()) {
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.failure", code);
        }
        return y;
    }

    private int settingsHeading(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, width)) {
            graphics.text(font, line, x, y, color, false);
            y += 11;
        }
        return y + 4;
    }

    private int debugLine(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            String labelKey,
            String value) {
        Component line = Component.translatable(labelKey).copy().append(": ").append(value);
        for (net.minecraft.util.FormattedCharSequence wrapped : font.split(line, width - 8)) {
            graphics.text(font, wrapped, x + 4, y, MUTED, false);
            y += 10;
        }
        return y + 2;
    }

    private void renderPlaceholder(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                Component.translatable(section.translationKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.section_pending"),
                area.x() + 10,
                area.y() + 31,
                MUTED,
                false);
    }

    private void renderExtensions(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.extensions"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.extensions.description"),
                area.x() + 10,
                area.y() + 29,
                MUTED,
                false);
        graphics.enableScissor(area.x(), area.y() + 42, area.right(), area.bottom());
        int cardsY = area.y() + 80 - pageScroll;
        int cardsX = area.x() + 9;
        int cardsWidth = Math.max(80, area.width() - 18);
        ExtensionSettingsProjection projection = extensionProjection();
        cardsY = renderExtensionRuntime(
                graphics, projection.runtime(), cardsX, cardsY, cardsWidth);
        renderExtensionCatalog(graphics, projection, cardsX, cardsY + 7, cardsWidth);
        graphics.disableScissor();
    }

    private int renderExtensionRuntime(
            GuiGraphicsExtractor graphics,
            ExtensionSettingsProjection.RuntimeCard runtime,
            int x,
            int y,
            int width) {
        int height = extensionRuntimeHeight(runtime, width);
        graphics.fill(x, y, x + width, y + height, PANEL_ALT);
        graphics.outline(x, y, width, height, ACCENT);
        int cursor = y + 7;
        cursor = renderWrapped(
                graphics,
                Component.translatable(runtime.titleKey()),
                x + 7,
                cursor,
                width - 14,
                ACCENT,
                10);
        cursor = renderWrapped(
                graphics,
                Component.translatable(runtime.descriptionKey()),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        cursor = renderWrapped(
                graphics,
                Component.translatable(
                        "screen.openallay.settings.extensions.runtime.inputs",
                        String.join(", ", runtime.parameters())),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        renderWrapped(
                graphics,
                Component.translatable(
                        "screen.openallay.settings.extensions.runtime.outputs",
                        String.join(", ", runtime.returns())),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        return y + height;
    }

    private int renderExtensionCatalog(
            GuiGraphicsExtractor graphics,
            ExtensionSettingsProjection projection,
            int x,
            int y,
            int width) {
        y = renderExtensionHeading(
                graphics,
                "screen.openallay.settings.extensions.modules",
                projection.modules().size(),
                x,
                y);
        if (projection.modules().isEmpty()) {
            y = renderExtensionEmpty(graphics, x, y);
        } else {
            for (ExtensionSettingsProjection.ModuleCard module : projection.modules()) {
                y = renderExtensionCard(
                        graphics,
                        Component.literal(module.id()),
                        Component.translatable(
                                "screen.openallay.settings.extensions.module.bundled"),
                        null,
                        x,
                        y,
                        width);
            }
        }

        y = renderExtensionHeading(
                graphics,
                "screen.openallay.settings.extensions.adapters",
                projection.adapters().size(),
                x,
                y + 4);
        if (projection.adapters().isEmpty()) {
            y = renderExtensionEmpty(graphics, x, y);
        } else {
            for (ExtensionSettingsProjection.AdapterCard adapter : projection.adapters()) {
                Component detail = Component.literal(adapter.summary()).copy()
                        .append("\n")
                        .append(Component.translatable(
                                "screen.openallay.settings.extensions.provider",
                                adapter.provider()));
                String schema = schemaPreview(adapter.schema(), projection.debugMode());
                y = renderExtensionCard(
                        graphics,
                        Component.literal(adapter.id()),
                        detail,
                        schema.isBlank() ? null : Component.literal(schema),
                        x,
                        y,
                        width);
            }
        }

        y = renderExtensionHeading(
                graphics,
                "screen.openallay.settings.extensions.roots",
                projection.roots().size(),
                x,
                y + 4);
        for (ExtensionSettingsProjection.RootCard root : projection.roots()) {
            Component detail = Component.literal(root.summary()).copy()
                    .append("\n")
                    .append(Component.translatable(
                            root.availability().equals("REQUEST_SCOPED")
                                    ? "screen.openallay.settings.extensions.request_scoped"
                                    : "screen.openallay.settings.extensions.provider",
                            root.provider()));
            if (projection.debugMode()) {
                detail = detail.copy().append("\nprovider: ")
                        .append(root.provider())
                        .append(" · evidence: ")
                        .append(root.evidenceOwner());
            }
            y = renderExtensionCard(
                    graphics,
                    Component.literal("mc." + root.name()),
                    detail,
                    Component.literal(schemaPreview(root.schema(), projection.debugMode())),
                    x,
                    y,
                    width);
        }
        return y;
    }

    private int renderExtensionHeading(
            GuiGraphicsExtractor graphics,
            String key,
            int count,
            int x,
            int y) {
        graphics.text(
                font,
                Component.translatable(key, count),
                x + 2,
                y,
                ACCENT,
                false);
        return y + 14;
    }

    private int renderExtensionEmpty(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.extensions.none"),
                x + 7,
                y,
                MUTED,
                false);
        return y + 16;
    }

    private int renderExtensionCard(
            GuiGraphicsExtractor graphics,
            Component title,
            Component detail,
            Component schema,
            int x,
            int y,
            int width) {
        int height = extensionCardHeight(title, detail, schema, width);
        graphics.fill(x, y, x + width, y + height, PANEL_ALT);
        graphics.outline(x, y, width, height, 0xFF46515F);
        int cursor = y + 6;
        cursor = renderWrapped(graphics, title, x + 7, cursor, width - 14, TEXT, 10);
        cursor = renderWrapped(graphics, detail, x + 7, cursor + 2, width - 14, MUTED, 10);
        if (schema != null) {
            cursor = renderWrapped(
                    graphics,
                    Component.translatable("screen.openallay.settings.extensions.schema")
                            .copy()
                            .append(": ")
                            .append(schema),
                    x + 7,
                    cursor + 2,
                    width - 14,
                    0xFFFFD479,
                    10);
        }
        return y + height + 5;
    }

    private int extensionCatalogHeight(
            ExtensionSettingsProjection projection, int width) {
        int height = 14;
        if (projection.modules().isEmpty()) {
            height += 16;
        } else {
            for (ExtensionSettingsProjection.ModuleCard module : projection.modules()) {
                height += extensionCardHeight(
                                Component.literal(module.id()),
                                Component.translatable(
                                        "screen.openallay.settings.extensions.module.bundled"),
                                null,
                                width)
                        + 5;
            }
        }
        height += 18;
        if (projection.adapters().isEmpty()) {
            height += 16;
        } else {
            for (ExtensionSettingsProjection.AdapterCard adapter : projection.adapters()) {
                Component detail = Component.literal(adapter.summary()).copy()
                        .append("\n")
                        .append(Component.translatable(
                                "screen.openallay.settings.extensions.provider",
                                adapter.provider()));
                String schema = schemaPreview(adapter.schema(), projection.debugMode());
                height += extensionCardHeight(
                                Component.literal(adapter.id()),
                                detail,
                                schema.isBlank() ? null : Component.literal(schema),
                                width)
                        + 5;
            }
        }
        height += 18;
        for (ExtensionSettingsProjection.RootCard root : projection.roots()) {
            Component detail = Component.literal(root.summary()).copy()
                    .append("\n")
                    .append(Component.translatable(
                            root.availability().equals("REQUEST_SCOPED")
                                    ? "screen.openallay.settings.extensions.request_scoped"
                                    : "screen.openallay.settings.extensions.provider",
                            root.provider()));
            if (projection.debugMode()) {
                detail = detail.copy().append("\nprovider: ")
                        .append(root.provider())
                        .append(" · evidence: ")
                        .append(root.evidenceOwner());
            }
            height += extensionCardHeight(
                            Component.literal("mc." + root.name()),
                            detail,
                            Component.literal(schemaPreview(
                                    root.schema(), projection.debugMode())),
                            width)
                    + 5;
        }
        return height;
    }

    private int extensionCardHeight(
            Component title, Component detail, Component schema, int width) {
        int inner = Math.max(20, width - 14);
        int height = 12
                + wrappedHeight(title, inner, 10)
                + wrappedHeight(detail, inner, 10);
        if (schema != null) {
            height += 2 + wrappedHeight(
                    Component.translatable("screen.openallay.settings.extensions.schema")
                            .copy()
                            .append(": ")
                            .append(schema),
                    inner,
                    10);
        }
        return height;
    }

    private int extensionRuntimeHeight(
            ExtensionSettingsProjection.RuntimeCard runtime, int width) {
        int inner = Math.max(20, width - 14);
        return 18
                + wrappedHeight(Component.translatable(runtime.titleKey()), inner, 10)
                + wrappedHeight(Component.translatable(runtime.descriptionKey()), inner, 10)
                + wrappedHeight(Component.translatable(
                                "screen.openallay.settings.extensions.runtime.inputs",
                                String.join(", ", runtime.parameters())),
                        inner,
                        10)
                + wrappedHeight(Component.translatable(
                                "screen.openallay.settings.extensions.runtime.outputs",
                                String.join(", ", runtime.returns())),
                        inner,
                        10);
    }

    private static String schemaPreview(String schema, boolean debugMode) {
        if (schema == null || schema.isBlank()) {
            return "";
        }
        int maximum = debugMode ? 2_000 : 260;
        return schema.length() <= maximum
                ? schema
                : schema.substring(0, maximum - 1) + "…";
    }

    private int renderWrapped(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int lineHeight) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            graphics.text(font, line, x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    private int wrappedHeight(Component text, int width, int lineHeight) {
        return Math.max(1, font.split(text, Math.max(20, width)).size()) * lineHeight;
    }

    private void renderSkills(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        if (!layout.wide() && !narrowSkillDetail) {
            return;
        }
        if (skillTab == SkillTab.COMMUNITY) {
            renderCommunitySkills(graphics, area);
            return;
        }
        Optional<SkillSettingsProjection.Skill> selected = selectedSkill();
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.skills"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        if (selected.isEmpty()) {
            graphics.text(
                    font,
                    Component.translatable("screen.openallay.settings.skills.empty"),
                    area.x() + 10,
                    area.y() + 31,
                    MUTED,
                    false);
            return;
        }
        SkillSettingsProjection.Skill skill = selected.orElseThrow();
        graphics.text(font, skill.name(), area.x() + 10, area.y() + 30, TEXT, false);
        graphics.text(
                font,
                Component.translatable(skill.localOverride()
                        ? "screen.openallay.settings.skills.local"
                        : "screen.openallay.settings.skills.bundled"),
                area.x() + 10,
                area.y() + 42,
                MUTED,
                false);
        if (skillEditing) {
            return;
        }
        int y = area.y() + 60;
        int width = Math.max(80, area.width() - 20);
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.literal(skill.description()), width)) {
            graphics.text(font, line, area.x() + 10, y, MUTED, false);
            y += 10;
        }
        y += 7;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.literal(skill.body()), width)) {
            if (y > area.bottom() - 36) {
                break;
            }
            graphics.text(font, line, area.x() + 10, y, TEXT, false);
            y += 10;
        }
        if (snapshot.display().debugMode()) {
            graphics.text(
                    font,
                    Component.literal(skill.provenance()),
                    area.x() + 10,
                    Math.min(y + 6, area.bottom() - 38),
                    MUTED,
                    false);
        }
    }

    private void renderCommunitySkills(
            GuiGraphicsExtractor graphics,
            SettingsLayout.Rect area) {
        SkillSettingsProjection.Community community = skillProjection().community();
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.skills.community.title"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        if (!community.available()) {
            renderWrapped(
                    graphics,
                    Component.translatable(
                            "screen.openallay.settings.skills.community.unavailable"),
                    area.x() + 10,
                    area.y() + 31,
                    Math.max(80, area.width() - 20),
                    MUTED,
                    10);
            return;
        }
        SkillSettingsProjection.Package skill = selectedCommunitySkill().orElse(null);
        if (skill == null) {
            graphics.text(
                    font,
                    Component.translatable("screen.openallay.settings.skills.community.empty"),
                    area.x() + 10,
                    area.y() + 31,
                    MUTED,
                    false);
            return;
        }
        int x = area.x() + 10;
        int width = Math.max(80, area.width() - 20);
        int y = area.y() + 31;
        graphics.text(font, skill.id(), x, y, TEXT, false);
        y += 14;
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.skills.community.version",
                        skill.version()),
                x,
                y,
                MUTED,
                false);
        y += 13;
        graphics.text(
                font,
                Component.translatable(skillStateKey(skill.state())),
                x,
                y,
                skill.installable() ? ACCENT : MUTED,
                false);
        y += 18;
        y = renderWrapped(
                graphics,
                Component.translatable(
                        "screen.openallay.settings.skills.community.source", skill.source()),
                x,
                y,
                width,
                TEXT,
                10);
        if (skillProjection().debugMode()) {
            y += 7;
            y = renderWrapped(
                    graphics,
                    Component.translatable(
                            "screen.openallay.settings.skills.community.archive",
                            skill.archive()),
                    x,
                    y,
                    width,
                    MUTED,
                    10);
            renderWrapped(
                    graphics,
                    Component.translatable(
                            "screen.openallay.settings.skills.community.sha256",
                            skill.sha256()),
                    x,
                    y + 3,
                    width,
                    MUTED,
                    10);
        }
        int noticeY = Math.min(y + 18, area.bottom() - 72);
        community.notice().ifPresent(value -> renderWrapped(
                graphics,
                Component.literal(value.message()),
                x,
                noticeY,
                width,
                ERROR,
                10));
    }

    private void renderNotice(GuiGraphicsExtractor graphics) {
        String message = localNotice;
        int color = ERROR;
        SettingsNotice serviceNotice = snapshot.notice();
        if (message.isBlank() && serviceNotice != null) {
            message = serviceNotice.message();
            color = serviceNotice.level() == SettingsNotice.Level.SUCCESS ? 0xFF7FC8A9 : ERROR;
        }
        if (message.isBlank()) {
            return;
        }
        graphics.text(
                font,
                message,
                layout.header().x() + 150,
                layout.header().y() + 10,
                color,
                false);
    }

    private static boolean completedReload(
            ClientSettingsSnapshot previous,
            ClientSettingsSnapshot next,
            SettingsOperation.Kind reloadKind) {
        return previous.operation().kind() == reloadKind
                && next.operation().kind() == SettingsOperation.Kind.IDLE
                && next.notice() != null
                && next.notice().level() == SettingsNotice.Level.SUCCESS;
    }

    private void saveCurrent() {
        confirmation = Confirmation.NONE;
        if (section == SettingsSection.MODELS) {
            save();
        }
    }

    private void reloadCurrent() {
        if (confirmation != Confirmation.RELOAD) {
            confirmation = Confirmation.RELOAD;
            localNotice = Component.translatable(
                    "screen.openallay.settings.confirm_reload").getString();
            rebuildWidgets();
            return;
        }
        confirmation = Confirmation.NONE;
        if (section == SettingsSection.MODELS) {
            accept(service.reloadModels(true));
        }
    }

    private void backOrClose() {
        if (!layout.wide() && section == SettingsSection.SKILLS && narrowSkillDetail) {
            narrowSkillDetail = false;
            skillEditing = false;
            skillDraftMarkdown = "";
            rebuildWidgets();
            return;
        }
        onClose();
    }

    private Component historyActionLabel(HistorySettingsProjection.ActionRow row) {
        if (historyConfirmation == null
                || historyConfirmation.action() != serviceHistoryAction(row.action())) {
            return Component.translatable(row.labelKey());
        }
        if (row.action() == HistorySettingsProjection.Action.RESET_DATABASE
                && historyConfirmation.stage()
                        == ClientSettingsService.ConfirmationStage.FIRST) {
            return Component.translatable(
                    "screen.openallay.settings.history.confirm_reset_again");
        }
        return Component.translatable("screen.openallay.settings.confirm");
    }

    private void activateHistory(HistorySettingsProjection.Action action) {
        ClientSettingsService.HistoryAction serviceAction = serviceHistoryAction(action);
        if (historyConfirmation == null
                || historyConfirmation.action() != serviceAction
                || historyConfirmation.generation() != snapshot.generation()) {
            ToolResult<ClientSettingsService.HistoryConfirmationToken> requested =
                    service.requestHistoryConfirmation(serviceAction);
            if (requested instanceof ToolResult.Success<
                    ClientSettingsService.HistoryConfirmationToken> success) {
                historyConfirmation = success.value();
                localNotice = Component.translatable(
                        action == HistorySettingsProjection.Action.RESET_DATABASE
                                ? "screen.openallay.settings.history.confirm_reset"
                                : "screen.openallay.settings.history.confirm_delete")
                        .getString();
                rebuildWidgets();
            } else {
                ToolResult.Failure<ClientSettingsService.HistoryConfirmationToken> failure =
                        (ToolResult.Failure<ClientSettingsService.HistoryConfirmationToken>) requested;
                localNotice = failure.message();
            }
            return;
        }
        if (action == HistorySettingsProjection.Action.RESET_DATABASE
                && historyConfirmation.stage()
                        == ClientSettingsService.ConfirmationStage.FIRST) {
            ToolResult<ClientSettingsService.HistoryConfirmationToken> second =
                    service.confirmHistoryReset(historyConfirmation);
            if (second instanceof ToolResult.Success<
                    ClientSettingsService.HistoryConfirmationToken> success) {
                historyConfirmation = success.value();
                localNotice = Component.translatable(
                        "screen.openallay.settings.history.confirm_reset_again_notice")
                        .getString();
                rebuildWidgets();
            } else {
                historyConfirmation = null;
                localNotice = ((ToolResult.Failure<
                        ClientSettingsService.HistoryConfirmationToken>) second).message();
            }
            return;
        }

        ClientSettingsService.HistoryConfirmationToken confirmed = historyConfirmation;
        historyConfirmation = null;
        confirmation = Confirmation.NONE;
        accept(switch (action) {
            case DELETE_CURRENT -> service.deleteCurrentHistory(confirmed);
            case DELETE_ACTOR -> service.deleteActorHistory(confirmed);
            case RESET_DATABASE -> service.resetHistoryDatabase(confirmed);
        });
    }

    private static ClientSettingsService.HistoryAction serviceHistoryAction(
            HistorySettingsProjection.Action action) {
        return switch (action) {
            case DELETE_CURRENT -> ClientSettingsService.HistoryAction.DELETE_CURRENT;
            case DELETE_ACTOR -> ClientSettingsService.HistoryAction.DELETE_ACTOR;
            case RESET_DATABASE -> ClientSettingsService.HistoryAction.RESET_DATABASE;
        };
    }

    private ExtensionSettingsProjection extensionProjection() {
        return ExtensionSettingsProjection.from(
                snapshot.extensions(),
                snapshot.experimentalCommands(),
                snapshot.display().debugMode());
    }

    private SkillSettingsProjection skillProjection() {
        return SkillSettingsProjection.from(
                snapshot.skills(),
                snapshot.skillCommunity(),
                snapshot.display().debugMode());
    }

    private Optional<SkillSettingsProjection.Skill> selectedSkill() {
        return selectedSkillName == null
                ? Optional.empty()
                : skillProjection().find(selectedSkillName);
    }

    private Optional<SkillSettingsProjection.Package> selectedCommunitySkill() {
        return selectedCommunitySkillId == null
                ? Optional.empty()
                : skillProjection().community().find(selectedCommunitySkillId);
    }

    private static String skillStateKey(SkillSettingsProjection.PackageState state) {
        return switch (state) {
            case AVAILABLE -> "screen.openallay.settings.skills.community.available";
            case INSTALLED -> "screen.openallay.settings.skills.community.installed";
            case UPDATE_AVAILABLE ->
                    "screen.openallay.settings.skills.community.update_available";
            case INCOMPATIBLE -> "screen.openallay.settings.skills.community.incompatible";
        };
    }

    private void selectSkillTab(SkillTab replacement) {
        if (skillTab == replacement) {
            return;
        }
        captureDraft();
        skillTab = replacement;
        pageScroll = 0;
        narrowSkillDetail = false;
        skillEditing = false;
        skillDraftMarkdown = "";
        localNotice = "";
        rebuildWidgets();
    }

    private void importLocalSkill() {
        captureDraft();
        if (skillImportPathDraft.isBlank()) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.skills.community.import_required").getString();
            return;
        }
        try {
            accept(service.importLocalSkillPackage(Path.of(skillImportPathDraft)));
        } catch (InvalidPathException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.skills.community.import_invalid").getString();
        }
    }

    private void saveSkillOverride() {
        SkillSettingsProjection.Skill selected = selectedSkill().orElse(null);
        if (selected == null || skillDraftMarkdown.isBlank()) {
            return;
        }
        skillEditing = false;
        accept(service.saveSkillOverride(selected.name(), skillDraftMarkdown));
        skillDraftMarkdown = "";
    }

    private void save() {
        captureDraft();
        ToolResult<ModelProfileDefinition> validated = draft.validate();
        if (validated instanceof ToolResult.Failure<ModelProfileDefinition> failure) {
            localNotice = failure.message();
            return;
        }
        ModelProfileDefinition definition =
                ((ToolResult.Success<ModelProfileDefinition>) validated).value();
        ModelProfilesConfig candidate;
        try {
            candidate = candidateWith(definition);
        } catch (RuntimeException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.invalid").getString();
            return;
        }
        selectedProfileId = definition.id();
        SecretValue replacement = pendingApiKey.isBlank()
                ? null
                : SecretValue.of(pendingApiKey);
        pendingApiKey = "";
        if (apiKey != null) {
            apiKey.setValue("");
        }
        accept(service.saveModels(candidate, definition.id(), replacement));
    }

    private void delete() {
        if (selectedProfileId == null) {
            select(snapshot.models().config().defaultProfileId());
            rebuildWidgets();
            return;
        }
        if (snapshot.models().config().profiles().size() <= 1) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.cannot_delete_last").getString();
            return;
        }
        if (confirmation != Confirmation.DELETE) {
            confirmation = Confirmation.DELETE;
            localNotice = Component.translatable(
                    "screen.openallay.settings.confirm_delete").getString();
            rebuildWidgets();
            return;
        }
        List<ModelProfileDefinition> retained = snapshot.models().config().profiles().stream()
                .filter(profile -> !profile.id().equals(selectedProfileId))
                .toList();
        String defaultId = snapshot.models().config().defaultProfileId().equals(selectedProfileId)
                ? retained.getFirst().id()
                : snapshot.models().config().defaultProfileId();
        select(retained.getFirst().id());
        confirmation = Confirmation.NONE;
        accept(service.saveModels(new ModelProfilesConfig(
                ModelProfilesConfig.SCHEMA_VERSION, defaultId, retained)));
    }

    private void makeDefault() {
        if (selectedProfileId == null) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.save_first").getString();
            return;
        }
        ModelProfilesConfig current = snapshot.models().config();
        accept(service.saveModels(new ModelProfilesConfig(
                current.schemaVersion(), selectedProfileId, current.profiles())));
    }

    private void testConnection() {
        captureDraft();
        ToolResult<ModelProfileDefinition> validated = draft.validate();
        if (validated instanceof ToolResult.Failure<ModelProfileDefinition> failure) {
            localNotice = failure.message();
            return;
        }
        if (confirmation != Confirmation.TEST_CONNECTION) {
            confirmation = Confirmation.TEST_CONNECTION;
            localNotice = Component.translatable(
                    "screen.openallay.settings.confirm_billable_test").getString();
            rebuildWidgets();
            return;
        }
        confirmation = Confirmation.NONE;
        ModelProfileDefinition definition =
                ((ToolResult.Success<ModelProfileDefinition>) validated).value();
        SecretValue replacement = pendingApiKey.isBlank()
                ? null
                : SecretValue.of(pendingApiKey);
        service.testConnection(definition, replacement).thenAccept(result -> {
            if (result instanceof ModelConnectionResult.Failure failure) {
                localNotice = failure.message();
            } else {
                localNotice = "";
            }
        });
    }

    private void cancel() {
        if (snapshot.operation().kind() == SettingsOperation.Kind.FETCHING_MODEL_CATALOG) {
            service.cancelModelCatalog();
        } else {
            service.cancelConnectionTest();
        }
    }

    private void refreshMetadata() {
        accept(service.refreshMetadata());
    }

    private void fetchModelCatalog() {
        captureDraft();
        ToolResult<ModelCatalogRequest> validated = draft.catalogRequest();
        if (validated instanceof ToolResult.Failure<ModelCatalogRequest> failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.catalog_invalid").getString();
            return;
        }
        ModelCatalogRequest request =
                ((ToolResult.Success<ModelCatalogRequest>) validated).value();
        SecretValue replacement = pendingApiKey.isBlank()
                ? null
                : SecretValue.of(pendingApiKey);
        long generation = ++modelCatalogGeneration;
        service.fetchModelCatalog(request, replacement).thenAccept(result -> {
            if (generation != modelCatalogGeneration) {
                return;
            }
            if (result instanceof ToolResult.Success<ModelCatalog> success) {
                catalogModelIds = success.value().modelIds();
                modelCatalogPage = 0;
                modelCatalogOpen = true;
                localNotice = catalogModelIds.isEmpty()
                        ? Component.translatable(
                                "screen.openallay.settings.models.catalog_empty").getString()
                        : "";
                rebuildWidgets();
            } else {
                ToolResult.Failure<ModelCatalog> failure =
                        (ToolResult.Failure<ModelCatalog>) result;
                localNotice = Component.translatable(
                        "screen.openallay.settings.models.catalog_failed",
                        failure.message()).getString();
            }
        });
    }

    private void addModelCatalogPicker() {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 8;
        int y = area.y() + 30;
        int width = Math.max(80, area.width() - 16);
        int pageSize = modelCatalogPageSize();
        int start = Math.min(catalogModelIds.size(), modelCatalogPage * pageSize);
        int end = Math.min(catalogModelIds.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            String modelId = catalogModelIds.get(index);
            addRenderableWidget(OpenAllayButton.create(Component.literal(modelId), ignored -> {
                        draft = draft.withModel(modelId);
                        modelCatalogOpen = false;
                        localNotice = "";
                        rebuildWidgets();
                    })
                    .bounds(x, y, width, 20)
                    .build());
            y += 23;
        }
        int pages = Math.max(1, (catalogModelIds.size() + pageSize - 1) / pageSize);
        int navY = area.bottom() - 24;
        int navWidth = Math.max(30, (width - 8) / 3);
        Button previous = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.catalog_previous"),
                        ignored -> {
                            modelCatalogPage--;
                            rebuildWidgets();
                        })
                .bounds(x, navY, navWidth, 20)
                .build());
        previous.active = modelCatalogPage > 0;
        Button next = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.catalog_next"),
                        ignored -> {
                            modelCatalogPage++;
                            rebuildWidgets();
                        })
                .bounds(x + navWidth + 4, navY, navWidth, 20)
                .build());
        next.active = modelCatalogPage + 1 < pages;
        addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.catalog_close"),
                        ignored -> {
                            modelCatalogOpen = false;
                            rebuildWidgets();
                        })
                .bounds(x + (navWidth + 4) * 2, navY,
                        Math.max(30, width - (navWidth + 4) * 2), 20)
                .build());
    }

    private int modelCatalogPageSize() {
        return Math.max(1, (layout.editor().height() - 64) / 23);
    }

    private void invalidateModelCatalog() {
        modelCatalogGeneration++;
        catalogModelIds = List.of();
        modelCatalogOpen = false;
        modelCatalogPage = 0;
    }

    private void accept(java.util.concurrent.CompletableFuture<ToolResult<Boolean>> future) {
        future.thenAccept(result -> {
            if (result instanceof ToolResult.Failure<Boolean> failure) {
                localNotice = failure.code().equals("capability_dependency_conflict")
                        ? Component.translatable(
                                "screen.openallay.settings.capability.dependency_conflict")
                                .getString()
                        : failure.message();
            } else {
                localNotice = "";
            }
        });
    }

    private ModelProfilesConfig candidateWith(ModelProfileDefinition replacement) {
        ModelProfilesConfig current = snapshot.models().config();
        List<ModelProfileDefinition> profiles = new ArrayList<>();
        boolean replaced = false;
        for (ModelProfileDefinition profile : current.profiles()) {
            if (selectedProfileId != null && profile.id().equals(selectedProfileId)) {
                profiles.add(replacement);
                replaced = true;
            } else {
                profiles.add(profile);
            }
        }
        if (!replaced) {
            profiles.add(replacement);
        }
        String defaultId = current.defaultProfileId();
        if (selectedProfileId != null
                && defaultId.equals(selectedProfileId)
                && !selectedProfileId.equals(replacement.id())) {
            defaultId = replacement.id();
        }
        return new ModelProfilesConfig(current.schemaVersion(), defaultId, profiles);
    }

    private void switchSection(SettingsSection replacement) {
        captureDraft();
        section = replacement;
        editorScroll = 0;
        pageScroll = 0;
        pageContentHeight = 0;
        historyConfirmation = null;
        narrowSkillDetail = false;
        skillEditing = false;
        skillDraftMarkdown = "";
        confirmation = Confirmation.NONE;
        localNotice = "";
        if (replacement != SettingsSection.MODELS) {
            modelCatalogOpen = false;
        }
        rebuildWidgets();
    }

    private void selectAndRebuild(String profileId) {
        select(profileId);
        editorScroll = 0;
        confirmation = Confirmation.NONE;
        localNotice = "";
        editorScroll = 0;
        rebuildWidgets();
    }

    private void select(String profileId) {
        ModelProfileDefinition definition = snapshot.models().config().profiles().stream()
                .filter(profile -> profile.id().equals(profileId))
                .findFirst()
                .orElseThrow();
        selectedProfileId = definition.id();
        draft = ModelProfileDraft.from(definition);
        draftEnabled = definition.enabled();
        draftProtocol = definition.protocol();
        pendingApiKey = "";
        invalidateModelCatalog();
    }

    private void createProfile() {
        int suffix = 1;
        String candidate = "profile-" + suffix;
        while (containsProfile(candidate)) {
            candidate = "profile-" + ++suffix;
        }
        selectedProfileId = null;
        draft = ModelProfileDraft.create(candidate);
        draftEnabled = true;
        draftProtocol = ModelProtocol.OPENAI_CHAT;
        pendingApiKey = "";
        invalidateModelCatalog();
        confirmation = Confirmation.NONE;
        localNotice = "";
        rebuildWidgets();
    }

    private boolean containsProfile(String profileId) {
        return snapshot.models().config().profiles().stream()
                .anyMatch(profile -> profile.id().equals(profileId));
    }

    private void captureDraft() {
        if (section == SettingsSection.MODELS && id != null) {
            draft = new ModelProfileDraft(
                    id.getValue(),
                    displayName.getValue(),
                    draftEnabled,
                    draftProtocol,
                    baseUrl.getValue(),
                    model.getValue(),
                    draft.credentialRef(),
                    contextWindow.getValue(),
                    maxOutput.getValue(),
                    connectTimeout.getValue(),
                    requestTimeout.getValue(),
                    draft.metadata());
        }
        if (section == SettingsSection.GENERAL && assistantName != null) {
            assistantNameDraft = assistantName.getValue();
        }
        if (section == SettingsSection.SKILLS && skillImportPath != null) {
            skillImportPathDraft = skillImportPath.getValue();
        }
    }

    private void cycleProtocol() {
        captureDraft();
        draftProtocol = draftProtocol == ModelProtocol.OPENAI_CHAT
                ? ModelProtocol.ANTHROPIC_MESSAGES
                : ModelProtocol.OPENAI_CHAT;
        invalidateModelCatalog();
        confirmation = Confirmation.NONE;
        rebuildWidgets();
    }

    private void toggleEnabled() {
        captureDraft();
        draftEnabled = !draftEnabled;
        confirmation = Confirmation.NONE;
        rebuildWidgets();
    }

    private void cycleSection() {
        List<SettingsSection> sections = SettingsSection.topLevel();
        switchSection(sections.get((sections.indexOf(section) + 1) % sections.size()));
    }

    private Component protocolLabel() {
        return Component.translatable(
                draftProtocol == ModelProtocol.OPENAI_CHAT
                        ? "screen.openallay.settings.models.protocol_openai"
                        : "screen.openallay.settings.models.protocol_anthropic");
    }

    private Component enabledLabel() {
        return Component.translatable(
                draftEnabled
                        ? "screen.openallay.settings.models.enabled"
                        : "screen.openallay.settings.models.disabled");
    }

    private String reloadKey() {
        return confirmation == Confirmation.RELOAD
                ? "screen.openallay.settings.confirm"
                : "screen.openallay.settings.reload";
    }

    private String deleteKey() {
        return confirmation == Confirmation.DELETE
                ? "screen.openallay.settings.confirm"
                : "screen.openallay.settings.delete";
    }

    private String testKey() {
        return confirmation == Confirmation.TEST_CONNECTION
                ? "screen.openallay.settings.confirm"
                : "screen.openallay.settings.models.test";
    }

    private java.util.Optional<ModelProfileSettingsView.Profile> selectedView() {
        return snapshot.models().profiles().stream()
                .filter(profile -> profile.definition().id().equals(selectedProfileId))
                .findFirst();
    }

    private static void panel(
            GuiGraphicsExtractor graphics, SettingsLayout.Rect rect, int color) {
        if (rect.width() > 0 && rect.height() > 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        }
    }

    /** Screenshot-harness navigation only; inert in every normal client launch. */
    public void e2eOpenExtensions() {
        requireE2eControls();
        captureDraft();
        section = SettingsSection.EXTENSIONS;
        pageScroll = 0;
        pageContentHeight = 0;
        rebuildWidgets();
    }

    /** Screenshot-harness navigation that exercises the real display-settings save path. */
    public void e2eOpenGeneral(String assistantDisplayName) {
        requireE2eControls();
        Objects.requireNonNull(assistantDisplayName, "assistantDisplayName");
        captureDraft();
        section = SettingsSection.GENERAL;
        assistantNameDraft = assistantDisplayName;
        pageScroll = 0;
        pageContentHeight = 0;
        accept(service.saveDisplay(snapshot.display().withAssistantName(assistantDisplayName)));
        rebuildWidgets();
    }

    /** Screenshot-harness navigation for the player-facing About page. */
    public void e2eOpenAbout() {
        requireE2eControls();
        captureDraft();
        section = SettingsSection.ABOUT;
        pageScroll = 0;
        pageContentHeight = 0;
        rebuildWidgets();
    }

    /** Positive pixels move the Tool detail down; intended for retained screenshot coverage. */
    public void e2eScrollExtensionDetails(int pixels) {
        requireE2eControls();
        if (section != SettingsSection.EXTENSIONS || layout == null) {
            throw new IllegalStateException("E2E Extension details are not open");
        }
        int maximum = Math.max(0, pageContentHeight - layout.content().height() + 18);
        pageScroll = net.minecraft.util.Mth.clamp(pageScroll + pixels, 0, maximum);
        rebuildWidgets();
    }

    static boolean e2eControlsEnabled() {
        return Boolean.getBoolean(GuideClientE2EConfig.ENABLED);
    }

    private static void requireE2eControls() {
        if (!e2eControlsEnabled()) {
            throw new IllegalStateException("OpenAllay E2E controls are disabled");
        }
    }

    static Projection project(ClientSettingsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ModelCard> cards = snapshot.models().profiles().stream()
                .map(profile -> new ModelCard(
                        profile.definition().id(),
                        profile.definition().displayName(),
                        profile.definition().model(),
                        profile.credentialPresent(),
                        profile.available(),
                        profile.definition().id().equals(
                                snapshot.models().config().defaultProfileId()),
                        profile.failure() == null ? null : profile.failure().code()))
                .toList();
        return new Projection(
                SettingsSection.topLevel(),
                cards,
                GeneralSettingsProjection.from(snapshot.display()),
                RecipeSettingsProjection.from(
                        snapshot.recipes(),
                        snapshot.recipes().config(),
                        snapshot.display().debugMode()),
                SkillSettingsProjection.from(
                        snapshot.skills(),
                        snapshot.skillCommunity(),
                        snapshot.display().debugMode()),
                ExtensionSettingsProjection.from(
                        snapshot.extensions(),
                        snapshot.experimentalCommands(),
                        snapshot.display().debugMode()),
                HistorySettingsProjection.from(
                        snapshot.history(),
                        snapshot.display().debugMode(),
                        snapshot.operation()),
                DiagnosticsSettingsProjection.from(snapshot.diagnostics()),
                snapshot.operation(),
                snapshot.notice());
    }

    record Projection(
            List<SettingsSection> sections,
            List<ModelCard> models,
            GeneralSettingsProjection general,
            RecipeSettingsProjection recipes,
            SkillSettingsProjection skills,
            ExtensionSettingsProjection extensions,
            HistorySettingsProjection history,
            DiagnosticsSettingsProjection diagnostics,
            SettingsOperation operation,
            SettingsNotice notice) {
        Projection {
            sections = List.copyOf(sections);
            models = List.copyOf(models);
            Objects.requireNonNull(general, "general");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(skills, "skills");
            Objects.requireNonNull(extensions, "extensions");
            Objects.requireNonNull(history, "history");
            Objects.requireNonNull(diagnostics, "diagnostics");
        }
    }

    record ModelCard(
            String id,
            String displayName,
            String model,
            boolean credentialPresent,
            boolean available,
            boolean defaultProfile,
            String failureCode) {}

    private record Action(String translationKey, Runnable action) {}

    private enum SkillTab {
        INSTALLED,
        COMMUNITY
    }

    private enum Confirmation {
        NONE,
        RELOAD,
        DELETE,
        TEST_CONNECTION
    }

    private static final class PasswordEditBox extends EditBox {
        private PasswordEditBox(
                net.minecraft.client.gui.Font font,
                int x,
                int y,
                int width,
                int height,
                Component narration) {
            super(font, x, y, width, height, narration);
            addFormatter((text, offset) -> net.minecraft.util.FormattedCharSequence.forward(
                    "•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            if (event.isCopy() || event.isCut()) {
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return Component.translatable("screen.openallay.settings.models.api_key");
        }
    }
}
