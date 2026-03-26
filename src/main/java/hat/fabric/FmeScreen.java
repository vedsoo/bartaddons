package hat.fabric;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class FmeScreen extends Screen {
    private static final int BASE_PANEL_WIDTH = 620;
    private static final int BASE_PANEL_HEIGHT = 320;
    private static final int BASE_CELL_WIDTH = 44;
    private static final int BASE_CELL_HEIGHT = 22;
    private static final int GRID_COLUMNS = 10;
    private static final int GRID_ROWS = 8;
    private static final int ENTRIES_PER_PAGE = GRID_COLUMNS * GRID_ROWS;
    private static final int BASE_TAB_WIDTH = 90;
    private static final int BASE_TAB_HEIGHT = 18;
    private static final int BASE_BUTTON_HEIGHT = 20;
    private static final int BASE_FIELD_HEIGHT = 18;
    private static final int BASE_GAP = 4;
    private static final int BASE_GRID_TOP = 96;
    private static final int UI_PANEL_COLOR = 0xE0161216;
    private static final int UI_PANEL_BORDER = 0x003A2A2F;
    private static final int UI_TEXT = 0xFFF5E9E1;
    private static final int UI_TEXT_MUTED = 0xFFB3A39A;
    private static final int UI_ACCENT = 0xFFF28C3A;
    private static final int UI_TAB_ACTIVE = 0xFF1E151A;
    private static final int UI_TAB_INACTIVE = 0xFF171116;
    private static final int UI_SIDEBAR_BG = 0xFF131015;
    private static final int UI_SIDEBAR_BORDER = 0xFF2C2127;
    private static final int UI_FIELD_BG = 0xFF1A1318;
    private static final int UI_FIELD_BORDER = 0xFF3A2A2F;
    private static final int UI_CELL_BG = 0xFF171116;
    private static final int UI_CELL_BORDER = 0xFF2C2127;
    private static final int UI_CELL_SELECTED = 0xFF22161B;
    private static final int UI_CELL_SELECTED_ACCENT = 0xFF2E1C20;
    private static final int UI_CONTROL_BG = 0xFF1A1318;
    private static final int UI_CONTROL_BORDER = 0xFF2C2127;
    private static final int UI_CONTROL_ACCENT = 0xFFF28C3A;
    private static final int UI_BUTTON_BG = 0xFF1E151A;
    private static final int UI_BUTTON_BORDER = 0xFF2C2127;
    private static final long OPEN_ANIMATION_MS = 300L;
    private static final float OPEN_START_SCALE = 0.1f;
    private static final int UI_BLUR_TINT = 0x66FFFFFF;
    private static final float TAB_ANIM_SPEED = 0.25f;
    private static final Identifier TAB_CLICK_SOUND_ID = Identifier.of("hat", "ui.click");
    private static final long SETTINGS_BUTTON_ANIM_MS = 220L;
    private static final Identifier TAB_ICON_ALL = Identifier.of("hat", "textures/gui/tabs/all.png");
    private static final Identifier TAB_ICON_FAVORITES = Identifier.of("hat", "textures/gui/tabs/favorites.png");
    private static final Identifier TAB_ICON_CUSTOM = Identifier.of("hat", "textures/gui/tabs/custom.png");
    private static final Identifier TAB_ICON_SETTINGS = Identifier.of("hat", "textures/gui/tabs/settings.png");

    private enum Tab {
        ALL,
        FAVORITES,
        CUSTOM_TEXURES,
        SETTINGS
    }

    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Path> textureFiles = new ArrayList<>();
    private final java.util.Map<Path, Identifier> customTextureCache = new java.util.HashMap<>();
    private final java.util.Set<Path> customTextureFailed = new java.util.HashSet<>();
    private final List<ClickableWidget> settingsWidgets = new ArrayList<>();
    private final List<GuiColorSlider> colorSliders = new ArrayList<>();
    private final List<TabButton> tabButtons = new ArrayList<>();
    private final List<ButtonWidget> settingsTabButtons = new ArrayList<>();
    private Tab tab = Tab.ALL;
    private final float[] tabAnim = new float[Tab.values().length];
    private int page = 0;
    private TextFieldWidget searchField;
    private int panelWidth;
    private int panelHeight;
    private int cellWidth;
    private int cellHeight;
    private int gap;
    private int colGap;
    private int rowGap;
    private int tabHeight;
    private int tabWidth;
    private int buttonHeight;
    private int fieldHeight;
    private int sidebarWidth;
    private int sidebarX;
    private int contentX;
    private int titleY;
    private int subtitleY;
    private int tabY;
    private int toggleY;
    private int searchY;
    private int gridY;
    private int closeX;
    private int closeY;
    private int closeSize;
    private long openStartedAtMs = -1L;
    private Boolean blurAvailable;
    private int textureLoadBudget;
    private long fmeButtonAnimUntilMs;
    private long editButtonAnimUntilMs;
    private ButtonWidget fmeToggleButton;
    private ButtonWidget editToggleButton;

    public FmeScreen() {
        this(false);
    }

    public FmeScreen(boolean openGuiSettings) {
        super(Text.literal("FME"));
        this.tab = Tab.ALL;
    }

    @Override
    protected void init() {
        recalcMetrics();
        if (openStartedAtMs < 0L) {
            openStartedAtMs = Util.getMeasuringTimeMs();
        }
        if (blurAvailable == null) {
            blurAvailable = detectBlurSupport();
        }
        float scale = FmeManager.getGuiScale();
        if (allBlocks.isEmpty()) {
            Registries.BLOCK.stream()
                .filter(block -> !block.getDefaultState().isAir())
                .sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString()))
                .forEach(allBlocks::add);
        }
        refreshTextures();

        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        tabButtons.clear();
        settingsTabButtons.clear();

        gap = Math.round(BASE_GAP * scale);
        colGap = Math.round(4 * scale);
        rowGap = Math.round(3 * scale);
        tabHeight = Math.round(BASE_TAB_HEIGHT * scale);
        tabWidth = Math.round(BASE_TAB_WIDTH * scale);
        buttonHeight = Math.round(BASE_BUTTON_HEIGHT * scale);
        fieldHeight = Math.round((this.textRenderer.fontHeight + 3) * scale);
        int lineHeight = this.textRenderer.fontHeight + 2;

        sidebarWidth = Math.round(88 * scale);
        sidebarX = panelX + Math.round(8 * scale);
        contentX = sidebarX + sidebarWidth + Math.round(10 * scale);

        titleY = panelY + Math.round(10 * scale);
        subtitleY = titleY + lineHeight + Math.round(2 * scale);
        tabY = panelY + Math.round(56 * scale);
        toggleY = titleY;
        tabHeight = Math.round(16 * scale);

        int closeWidth = Math.round(14 * scale);
        int rightEdge = panelX + panelWidth - Math.round(12 * scale);
        int closeX = rightEdge - closeWidth;
        this.closeX = closeX;
        this.closeY = titleY - Math.round(1 * scale);
        this.closeSize = closeWidth;

        int tabsStart = sidebarX + Math.round(6 * scale);
        int tabs = 4;

        tabWidth = sidebarWidth - Math.round(12 * scale);
        int tabX = tabsStart;
        int tabYCursor = tabY;
        addTabButton(tabX, tabYCursor, tabWidth, tabHeight, "ALL", Tab.ALL);
        tabYCursor += tabHeight + Math.round(4 * scale);
        addTabButton(tabX, tabYCursor, tabWidth, tabHeight, "FAVORITES", Tab.FAVORITES);
        tabYCursor += tabHeight + Math.round(4 * scale);
        addTabButton(tabX, tabYCursor, tabWidth, tabHeight, "CUSTOM", Tab.CUSTOM_TEXURES);
        tabYCursor += tabHeight + Math.round(4 * scale);
        addTabButton(tabX, tabYCursor, tabWidth, tabHeight, "SETTINGS", Tab.SETTINGS);

        searchY = subtitleY + lineHeight + Math.round(18 * scale);
        gridY = searchY + fieldHeight + Math.round(8 * scale);

        int searchWidth = panelWidth - (contentX - panelX) - Math.round(140 * scale);
        this.searchField = new TextFieldWidget(this.textRenderer, contentX + 10, searchY, searchWidth, fieldHeight, Text.literal("Search"));
        this.searchField.setPlaceholder(Text.literal("Search blocks and textures..."));
        this.searchField.setCentered(false);
        this.searchField.setChangedListener(s -> page = 0);
        this.searchField.setDrawsBackground(false);
        this.addDrawableChild(this.searchField);
        this.setInitialFocus(this.searchField);

        buildSettingsTabWidgets(panelX, panelY);
        initTabAnimation();
        updateTabState();
    }

    @Override
    public void tick() {
        updateTabAnimation();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float openProgress = getOpenProgress();
        float openEase = easeOutQuad(openProgress);
        float openScale = OPEN_START_SCALE + (1f - OPEN_START_SCALE) * openEase;
        int overlayAlpha = MathHelper.clamp(Math.round(0x88 * openEase), 0, 0x88);
        int overlay = overlayAlpha << 24;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        if (Boolean.TRUE.equals(blurAvailable)) {
            try {
                context.applyBlur();
            } catch (RuntimeException ignored) {
                blurAvailable = false;
            }
        }
        int blurTint = withAlpha(UI_BLUR_TINT, MathHelper.clamp(Math.round(((UI_BLUR_TINT >> 24) & 0xFF) * openEase), 0, 255));
        context.fill(0, 0, this.width, this.height, blurTint);
        context.fill(0, 0, this.width, panelY, overlay);
        context.fill(0, panelY + panelHeight, this.width, this.height, overlay);
        context.fill(0, panelY, panelX, panelY + panelHeight, overlay);
        context.fill(panelX + panelWidth, panelY, this.width, panelY + panelHeight, overlay);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(this.width / 2f, this.height / 2f);
        context.getMatrices().scale(openScale, openScale);
        context.getMatrices().translate(-this.width / 2f, -this.height / 2f);
        if (openScale != 1f) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            mouseX = Math.round((mouseX - centerX) / openScale + centerX);
            mouseY = Math.round((mouseY - centerY) / openScale + centerY);
        }
        int panelColor = UI_PANEL_COLOR;
        int borderColor = UI_PANEL_BORDER;
        int textColor = UI_TEXT;
        int accentColor = UI_ACCENT;
        int mutedColor = UI_TEXT_MUTED;
        int cellBase = UI_CELL_BG;
        int cellSelected = UI_CELL_SELECTED;
        int cellSelectedCustom = UI_CELL_SELECTED_ACCENT;
        int cellBorder = UI_CELL_BORDER;
        int cellFavoriteBorder = withAlpha(mixColor(cellBorder, textColor, 0.4f), 0xFF);

        int radius = MathHelper.clamp(Math.round(6 * FmeManager.getGuiScale()), 3, 8);
        drawRoundedPanel(context, panelX, panelY, panelWidth, panelHeight, panelColor, borderColor, radius);
        drawSidebar(context, panelX, panelY, panelHeight, sidebarWidth, radius);
        context.drawText(this.textRenderer, Text.literal("Block Selector"), contentX + 12, titleY, textColor, false);
        context.drawText(this.textRenderer, Text.literal(currentBrushLabel()), contentX + 12, subtitleY, mutedColor, false);
        int closeColor = isOverClose(mouseX, mouseY)
            ? withAlpha(mixColor(textColor, UI_ACCENT, 0.6f), 0xFF)
            : mutedColor;
        context.drawText(this.textRenderer, Text.literal("x"), closeX + 2, closeY, closeColor, false);

        renderTabs(context, textColor, mutedColor, accentColor);
        renderSearchBackground(context, panelX, mutedColor);

        if (tab != Tab.SETTINGS) {
            int entriesCount = getEntriesCount();
            int maxPages = Math.max(1, MathHelper.ceil((double) entriesCount / ENTRIES_PER_PAGE));
            page = MathHelper.clamp(page, 0, maxPages - 1);
            String countLabel = countLabel(entriesCount);
            int countWidth = this.textRenderer.getWidth(countLabel);
            int countX = panelX + panelWidth - Math.round(12 * FmeManager.getGuiScale()) - countWidth;
            int countY = searchY + Math.max(0, (fieldHeight - this.textRenderer.fontHeight) / 2);
            context.drawText(this.textRenderer, Text.literal(countLabel), countX, countY, mutedColor, false);
        }

        float scale = FmeManager.getGuiScale();
        int gridX = contentX + 10;
        boolean showLabels = cellWidth >= Math.round(60 * scale);
        Text hoverText = null;
        if (tab == Tab.SETTINGS) {
            renderGuiSettings(context, panelX, gridX, gridY, textColor, mutedColor, cellBase, cellSelected, cellSelectedCustom, cellBorder, cellFavoriteBorder);
        } else if (tab == Tab.CUSTOM_TEXURES) {
            textureLoadBudget = 3;
            List<Path> entries = visibleTextureEntries();
            if (entries.isEmpty()) {
                context.drawText(this.textRenderer, Text.literal("No custom textures found"), gridX + 6, gridY + 6, mutedColor, false);
                context.drawText(this.textRenderer, Text.literal("Drop PNGs into: " + HatTextureManager.getTextureDir()),
                    gridX + 6, gridY + 18, mutedColor, false);
            }
            String selectedName = FmeManager.getSelectedCustomTextureName();
            for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
                int idx = page * ENTRIES_PER_PAGE + i;
                if (idx >= entries.size()) {
                    break;
                }
                Path texturePath = entries.get(idx);
                int col = i % GRID_COLUMNS;
                int row = i / GRID_COLUMNS;
                int x = gridX + col * (cellWidth + colGap);
                int y = gridY + row * (cellHeight + rowGap);

                boolean isSelected = selectedName != null
                    && texturePath.getFileName().toString().equalsIgnoreCase(selectedName);
                int bg = isSelected ? cellSelectedCustom : cellBase;
                drawCell(context, x, y, cellWidth, cellHeight, bg, cellBorder);

                Identifier texId = getCustomTextureId(texturePath);
                if (texId != null) {
                    try {
                        context.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x + 3, y + 3, 0, 0, 16, 16, 16, 16);
                    } catch (Throwable ignored) {
                        // If texture rendering fails, fall back to text only.
                    }
                }
                if (showLabels) {
                    String label = trimToWidth(texturePath.getFileName().toString(), cellWidth - 26);
                    context.drawText(this.textRenderer, Text.literal(label), x + 23, y + 7, textColor, false);
                }
                String textureName = texturePath.getFileName().toString();
                if (FmeManager.isCustomTextureFavorite(textureName)) {
                    drawFavoriteStar(context, x, y, cellWidth, cellHeight);
                }
                if (mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + cellHeight) {
                    hoverText = Text.literal(textureName + "\nLClick: Select | RClick: Fav");
                }
            }
        } else {
            List<Block> entries = visibleEntries();
            for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
                int idx = page * ENTRIES_PER_PAGE + i;
                if (idx >= entries.size()) {
                    break;
                }
                Block block = entries.get(idx);
                int col = i % GRID_COLUMNS;
                int row = i / GRID_COLUMNS;
                int x = gridX + col * (cellWidth + colGap);
                int y = gridY + row * (cellHeight + rowGap);

                boolean selected = block == FmeManager.getSelectedSource();
                boolean favorite = FmeManager.isFavorite(block);
                int bg = selected ? cellSelected : cellBase;
                drawCell(context, x, y, cellWidth, cellHeight, bg, cellBorder);

                ItemStack icon = new ItemStack(block.asItem());
                if (!icon.isEmpty() && icon.getItem() != Items.AIR) {
                    try {
                        context.drawItem(icon, x + 3, y + 3);
                    } catch (Throwable ignored) {
                        // Some blocks/items can fail icon rendering in screen context; fallback to text only.
                    }
                }
                if (showLabels) {
                    String label = trimToWidth(Registries.BLOCK.getId(block).getPath(), cellWidth - 26);
                    context.drawText(this.textRenderer, Text.literal(label), x + 23, y + 7, textColor, false);
                }
                if (favorite) {
                    drawFavoriteStar(context, x, y, cellWidth, cellHeight);
                }
                if (mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + cellHeight) {
                    String name = new ItemStack(block.asItem()).getName().getString();
                    String hint = "LClick: Select | RClick: Fav";
                    hoverText = Text.literal(name + "\n" + hint);
                }
            }
        }

        super.render(context, mouseX, mouseY, delta);
        if (hoverText != null && tab != Tab.SETTINGS) {
            drawTooltipBox(context, hoverText.getString(), mouseX, mouseY);
        }
        context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (click.button() == 0 && handleTabClick(click.x(), click.y())) {
            return true;
        }
        if (click.button() == 0 && isOverClose(click.x(), click.y())) {
            this.close();
            return true;
        }
        if (tab == Tab.SETTINGS) {
            return super.mouseClicked(click, doubleClick);
        }
        int panelX = (this.width - panelWidth) / 2;
        int gridX = contentX + 10;

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (tab == Tab.CUSTOM_TEXURES) {
            List<Path> entries = visibleTextureEntries();
            for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
                int idx = page * ENTRIES_PER_PAGE + i;
                if (idx >= entries.size()) {
                    break;
                }
                int col = i % GRID_COLUMNS;
                int row = i / GRID_COLUMNS;
                int x = gridX + col * (cellWidth + colGap);
                int y = gridY + row * (cellHeight + rowGap);

                if (mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + cellHeight) {
                    if (button == 0) {
                        FmeManager.selectCustomTexture(entries.get(idx));
                    } else if (button == 1) {
                        FmeManager.toggleCustomTextureFavorite(entries.get(idx).getFileName().toString());
                    }
                    return true;
                }
            }
        } else {
            List<Block> entries = visibleEntries();
            for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
                int idx = page * ENTRIES_PER_PAGE + i;
                if (idx >= entries.size()) {
                    break;
                }
                int col = i % GRID_COLUMNS;
                int row = i / GRID_COLUMNS;
                int x = gridX + col * (cellWidth + colGap);
                int y = gridY + row * (cellHeight + rowGap);

                if (mouseX >= x && mouseX < x + cellWidth && mouseY >= y && mouseY < y + cellHeight) {
                    Block block = entries.get(idx);
                    if (button == 1) {
                        FmeManager.toggleFavorite(block);
                    } else if (button == 0) {
                        FmeManager.setSelectedSource(block);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.SETTINGS) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (tab == Tab.CUSTOM_TEXURES) {
            refreshTextures();
        }
        int maxPages = Math.max(1, MathHelper.ceil((double) getEntriesCount() / ENTRIES_PER_PAGE));
        if (verticalAmount < 0) {
            page = Math.min(page + 1, maxPages - 1);
            return true;
        }
        if (verticalAmount > 0) {
            page = Math.max(page - 1, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Text tabText() {
        return switch (tab) {
            case ALL -> Text.literal("Tab: All");
            case FAVORITES -> Text.literal("Tab: Favorites");
            case CUSTOM_TEXURES -> Text.literal("Tab: Custom Textures");
            case SETTINGS -> Text.literal("Tab: Settings");
        };
    }

    private String tabHintText() {
        return switch (tab) {
            case ALL -> "RMB entry=fav | RMB world=apply (edit on) | LMB world=reset";
            case FAVORITES -> "RMB entry=fav(remove) | RMB world=apply (edit on) | LMB world=reset";
            case CUSTOM_TEXURES -> "LMB entry=select texture";
            case SETTINGS -> "Settings";
        };
    }

    private String screenTitle() {
        return switch (tab) {
            case CUSTOM_TEXURES -> "FME - custom textures";
            case SETTINGS -> "FME - settings";
            default -> "FME - pick source texture block";
        };
    }

    private String currentBrushLabel() {
        if (tab == Tab.CUSTOM_TEXURES) {
            String label = FmeManager.getSelectedCustomTextureName();
            if (label == null || label.isBlank()) {
                label = "none";
            }
            return "Brush: " + label;
        }
        Identifier sourceId = Registries.BLOCK.getId(FmeManager.getSelectedSource());
        return "Brush: " + sourceId.getPath();
    }

    private String countLabel(int count) {
        if (tab == Tab.CUSTOM_TEXURES) {
            return count + (count == 1 ? " texture" : " textures");
        }
        return count + (count == 1 ? " block" : " blocks");
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        for (TabButton tabButton : tabButtons) {
            if (mouseX >= tabButton.x && mouseX < tabButton.x + tabButton.width
                && mouseY >= tabButton.y && mouseY < tabButton.y + tabButton.height) {
                if (tab != tabButton.tab) {
                    tab = tabButton.tab;
                    page = 0;
                    if (tab == Tab.CUSTOM_TEXURES) {
                        refreshTextures();
                    }
                    playTabClickSound();
                    updateTabState();
                }
                return true;
            }
        }
        return false;
    }

    private boolean isOverClose(double mouseX, double mouseY) {
        return mouseX >= closeX && mouseX < closeX + closeSize
            && mouseY >= closeY && mouseY < closeY + closeSize;
    }

    private List<Block> visibleEntries() {
        String query = this.searchField != null ? this.searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (tab == Tab.ALL) {
            if (query.isEmpty()) {
                return allBlocks;
            }
            List<Block> filtered = new ArrayList<>();
            for (Block block : allBlocks) {
                Identifier id = Registries.BLOCK.getId(block);
                String key = id.toString().toLowerCase(Locale.ROOT);
                if (key.contains(query)) {
                    filtered.add(block);
                }
            }
            return filtered;
        }

        List<Block> favorites = new ArrayList<>();
        for (Block block : allBlocks) {
            if (FmeManager.isFavorite(block)) {
                if (!query.isEmpty()) {
                    Identifier id = Registries.BLOCK.getId(block);
                    String key = id.toString().toLowerCase(Locale.ROOT);
                    if (!key.contains(query)) {
                        continue;
                    }
                }
                favorites.add(block);
            }
        }
        return favorites;
    }

    private void refreshTextures() {
        textureFiles.clear();
        customTextureCache.clear();
        customTextureFailed.clear();
        textureFiles.addAll(HatTextureManager.listTextures());
    }

    private Identifier getCustomTextureId(Path path) {
        if (path == null) {
            return null;
        }
        Identifier cached = customTextureCache.get(path);
        if (cached != null) {
            return cached;
        }
        if (customTextureFailed.contains(path)) {
            return null;
        }
        if (textureLoadBudget <= 0) {
            return null;
        }
        textureLoadBudget--;
        Identifier id = HatTextureManager.getOrLoadTexture(path);
        if (id != null) {
            customTextureCache.put(path, id);
            return id;
        }
        customTextureFailed.add(path);
        return null;
    }

    private List<Path> visibleTextureEntries() {
        String query = this.searchField != null ? this.searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (query.isEmpty()) {
            return textureFiles;
        }
        List<Path> filtered = new ArrayList<>();
        for (Path path : textureFiles) {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.contains(query)) {
                filtered.add(path);
            }
        }
        return filtered;
    }

    private int getEntriesCount() {
        if (tab == Tab.SETTINGS) {
            return 0;
        }
        return tab == Tab.CUSTOM_TEXURES ? visibleTextureEntries().size() : visibleEntries().size();
    }

    private String trimToWidth(String input, int maxWidth) {
        if (this.textRenderer == null) {
            return input;
        }
        if (this.textRenderer.getWidth(input) <= maxWidth) {
            return input;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.textRenderer.getWidth(ellipsis);
        String trimmed = this.textRenderer.trimToWidth(input, Math.max(0, maxWidth - ellipsisWidth));
        return trimmed + ellipsis;
    }

    private static void drawCyanBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void drawFavoriteStar(DrawContext context, int x, int y, int width, int height) {
        String star = "*";
        int textWidth = this.textRenderer.getWidth(star);
        int textHeight = this.textRenderer.fontHeight;
        int starX = x + Math.max(0, (width - textWidth) / 2);
        int starY = y + Math.max(0, (height - textHeight) / 2);
        context.drawText(this.textRenderer, Text.literal(star), starX, starY, UI_ACCENT, false);
    }

    private static void drawRoundedPanel(DrawContext context, int x, int y, int width, int height,
                                         int panelColor, int borderColor, int radius) {
        int r = MathHelper.clamp(radius, 0, Math.min(width, height) / 2);
        if (((borderColor >>> 24) & 0xFF) > 0) {
            drawRoundedRect(context, x, y, width, height, r, borderColor);
            if (width > 2 && height > 2) {
                drawRoundedRect(context, x + 1, y + 1, width - 2, height - 2, Math.max(0, r - 1), panelColor);
            }
        } else {
            drawRoundedRect(context, x, y, width, height, r, panelColor);
        }
        if (height > 1) {
            context.fill(x + 1, y + height - 1, x + width - 1, y + height, panelColor);
        }
    }

    private static void drawRoundedRect(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (radius <= 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        double rr = r * r;
        for (int iy = 0; iy < height; iy++) {
            double dy;
            if (iy < r) {
                dy = r - iy - 0.5;
            } else if (iy >= height - r) {
                dy = iy - (height - r) + 0.5;
            } else {
                dy = 0.0;
            }
            int inset = 0;
            if (dy > 0.0) {
                inset = (int) Math.ceil(r - Math.sqrt(rr - dy * dy));
            }
            int x0 = x + inset;
            int x1 = x + width - inset;
            if (x1 > x0) {
                context.fill(x0, y + iy, x1, y + iy + 1, color);
            }
        }
    }

    private void drawTooltipBox(DrawContext context, String text, int mouseX, int mouseY) {
        if (text == null || text.isBlank()) {
            return;
        }
        String[] lines = text.split("\n");
        int paddingX = 6;
        int paddingY = 4;
        int lineHeight = this.textRenderer.fontHeight + 2;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, this.textRenderer.getWidth(line));
        }
        int boxWidth = textWidth + paddingX * 2;
        int boxHeight = lines.length * lineHeight + paddingY * 2 - 2;

        int x = mouseX + 8;
        int y = mouseY + 8;
        if (x + boxWidth > this.width - 4) {
            x = mouseX - boxWidth - 8;
        }
        if (y + boxHeight > this.height - 4) {
            y = mouseY - boxHeight - 8;
        }
        x = MathHelper.clamp(x, 4, this.width - boxWidth - 4);
        y = MathHelper.clamp(y, 4, this.height - boxHeight - 4);

        int bg = 0xF0121316;
        int border = 0xFF2A2E35;
        context.fill(x, y, x + boxWidth, y + boxHeight, bg);
        drawCyanBorder(context, x, y, boxWidth, boxHeight, border);
        for (int i = 0; i < lines.length; i++) {
            int color = i == 0 ? UI_TEXT : UI_TEXT_MUTED;
            context.drawText(this.textRenderer, Text.literal(lines[i]), x + paddingX, y + paddingY + i * lineHeight, color, false);
        }
    }

    private void updateTabState() {
        boolean showSettings = tab == Tab.SETTINGS;
        for (ClickableWidget widget : settingsWidgets) {
            widget.visible = showSettings;
            widget.active = showSettings;
        }
        for (ButtonWidget button : settingsTabButtons) {
            button.visible = showSettings;
            button.active = showSettings;
        }
        if (searchField != null) {
            searchField.setVisible(!showSettings);
            searchField.setEditable(!showSettings);
        }
    }

    private void addTabButton(int x, int y, int width, int height, String label, Tab target) {
        tabButtons.add(new TabButton(x, y, width, height, label, target));
    }

    private void buildSettingsWidgets(int panelX, int panelY) {
        settingsWidgets.clear();
        colorSliders.clear();
        float scale = FmeManager.getGuiScale();
        int baseY = gridY;
        int sliderWidth = Math.round(160 * scale);
        int sliderHeight = Math.round(16 * scale);
        int gap = Math.round(3 * scale);
        int leftX = contentX + 10;
        int rightX = contentX + Math.round(190 * scale);

        int leftRow = 0;
        leftRow += addChannelSliderRow("Panel A", leftX, baseY + leftRow, sliderWidth, sliderHeight, gap,
            FmeManager::getGuiPanelColor, FmeManager::setGuiPanelColor, 24);
        leftRow += addColorGroup("Panel", leftX, baseY + leftRow, sliderWidth, sliderHeight, gap,
            FmeManager::getGuiPanelColor, FmeManager::setGuiPanelColor);
        leftRow += addColorGroup("Border", leftX, baseY + leftRow, sliderWidth, sliderHeight, gap,
            FmeManager::getGuiBorderColor, FmeManager::setGuiBorderColor);

        int rightRow = 0;
        rightRow += addColorGroup("Text", rightX, baseY + rightRow, sliderWidth, sliderHeight, gap,
            FmeManager::getGuiTextColor, FmeManager::setGuiTextColor);
        rightRow += addColorGroup("Accent", rightX, baseY + rightRow, sliderWidth, sliderHeight, gap,
            FmeManager::getGuiAccentTextColor, FmeManager::setGuiAccentTextColor);
        rightRow += addChannelSliderRow("Select A", rightX, baseY + rightRow, sliderWidth, sliderHeight, gap,
            FmeManager::getSelectionBoxColor, FmeManager::setSelectionBoxColor, 24);
        rightRow += addColorGroup("Select", rightX, baseY + rightRow, sliderWidth, sliderHeight, gap,
            FmeManager::getSelectionBoxColor, FmeManager::setSelectionBoxColor);
        GuiScaleSlider scaleSlider = new GuiScaleSlider(contentX + 10, panelY + panelHeight - Math.round(44 * scale),
            Math.round(220 * scale), Math.round(18 * scale));
        settingsWidgets.add(scaleSlider);
        this.addDrawableChild(scaleSlider);

        ButtonWidget reset = ButtonWidget.builder(Text.literal("Reset GUI Colors"), button -> {
            FmeManager.resetGuiColors();
            for (GuiColorSlider slider : colorSliders) {
                slider.sync();
            }
        }).dimensions(panelX + panelWidth - Math.round(170 * scale), panelY + panelHeight - Math.round(44 * scale),
            Math.round(160 * scale), Math.round(20 * scale)).build();
        settingsWidgets.add(reset);
        this.addDrawableChild(reset);
    }

    private void buildSettingsTabWidgets(int panelX, int panelY) {
        settingsTabButtons.clear();
        float scale = FmeManager.getGuiScale();
        int buttonW = Math.round(140 * scale);
        int buttonH = Math.round(22 * scale);
        int startX = contentX + 10;
        int startY = gridY;

        ButtonWidget fmeToggle = new TabStyleButton(startX, startY, buttonW, buttonH,
            Text.literal(FmeManager.isEnabled() ? "FME ON" : "FME OFF"), button -> {
            boolean nowEnabled = FmeManager.toggleEnabled();
            button.setMessage(Text.literal(nowEnabled ? "FME ON" : "FME OFF"));
            fmeButtonAnimUntilMs = Util.getMeasuringTimeMs() + SETTINGS_BUTTON_ANIM_MS;
            playTabClickSound();
        });
        this.addDrawableChild(fmeToggle);
        settingsTabButtons.add(fmeToggle);
        fmeToggleButton = fmeToggle;

        ButtonWidget editToggle = new TabStyleButton(startX, startY + buttonH + Math.round(4 * scale), buttonW, buttonH,
            Text.literal(FmeManager.isEditMode() ? "EDIT ON" : "EDIT OFF"), button -> {
            boolean nowEdit = FmeManager.toggleEditMode();
            button.setMessage(Text.literal(nowEdit ? "EDIT ON" : "EDIT OFF"));
            editButtonAnimUntilMs = Util.getMeasuringTimeMs() + SETTINGS_BUTTON_ANIM_MS;
            playTabClickSound();
        });
        this.addDrawableChild(editToggle);
        settingsTabButtons.add(editToggle);
        editToggleButton = editToggle;
    }

    private int addColorGroup(String label, int x, int y, int width, int height, int gap,
                              IntSupplier colorGetter, IntConsumer colorSetter) {
        int row = 0;
        row += addChannelSliderRow(label + " R", x, y + row, width, height, gap, colorGetter, colorSetter, 16);
        row += addChannelSliderRow(label + " G", x, y + row, width, height, gap, colorGetter, colorSetter, 8);
        row += addChannelSliderRow(label + " B", x, y + row, width, height, gap, colorGetter, colorSetter, 0);
        return row;
    }

    private int addChannelSliderRow(String label, int x, int y, int width, int height, int gap,
                                    IntSupplier colorGetter, IntConsumer colorSetter, int shift) {
        addChannelSlider(label, x, y, width, height, colorGetter, colorSetter, shift);
        return height + gap;
    }

    private void addChannelSlider(String label, int x, int y, int width, int height,
                                  IntSupplier colorGetter, IntConsumer colorSetter, int shift) {
        IntSupplier channelGetter = () -> (colorGetter.getAsInt() >> shift) & 0xFF;
        IntConsumer channelSetter = value -> {
            int color = colorGetter.getAsInt();
            int updated = (color & ~(0xFF << shift)) | ((value & 0xFF) << shift);
            colorSetter.accept(updated);
        };
        GuiColorSlider slider = new GuiColorSlider(x, y, width, height, label, channelGetter, channelSetter);
        settingsWidgets.add(slider);
        colorSliders.add(slider);
        this.addDrawableChild(slider);
    }

    private void renderTabs(DrawContext context, int textColor, int mutedColor, int accentColor) {
        for (TabButton tabButton : tabButtons) {
            float anim = tabAnim[tabButton.tab.ordinal()];
            int bg = mixColor(UI_TAB_INACTIVE, UI_TAB_ACTIVE, anim);
            context.fill(tabButton.x, tabButton.y, tabButton.x + tabButton.width, tabButton.y + tabButton.height, bg);
            context.fill(tabButton.x, tabButton.y, tabButton.x + 1, tabButton.y + tabButton.height, UI_SIDEBAR_BORDER);
            context.fill(tabButton.x + tabButton.width - 1, tabButton.y,
                tabButton.x + tabButton.width, tabButton.y + tabButton.height, UI_SIDEBAR_BORDER);
            int accent = withAlpha(accentColor, MathHelper.clamp(Math.round(255 * anim), 0, 255));
            context.fill(tabButton.x + 2, tabButton.y + 2, tabButton.x + 5, tabButton.y + tabButton.height - 2, accent);
            Identifier iconId = tabIconFor(tabButton.tab);
            if (iconId != null) {
                int iconSize = Math.round(12 * FmeManager.getGuiScale());
                int iconX = tabButton.x + (tabButton.width - iconSize) / 2;
                int iconY = tabButton.y + (tabButton.height - iconSize) / 2;
                int iconBg = mixColor(UI_TAB_ACTIVE, UI_ACCENT, 0.18f);
                context.fill(iconX - 2, iconY - 2, iconX + iconSize + 2, iconY + iconSize + 2, iconBg);
                try {
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, iconId, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
                } catch (Throwable ignored) {
                    // If icon fails, skip drawing.
                }
            }
        }
    }

    private Identifier tabIconFor(Tab tab) {
        return switch (tab) {
            case ALL -> TAB_ICON_ALL;
            case FAVORITES -> TAB_ICON_FAVORITES;
            case CUSTOM_TEXURES -> TAB_ICON_CUSTOM;
            case SETTINGS -> TAB_ICON_SETTINGS;
        };
    }

    private void initTabAnimation() {
        for (Tab tabValue : Tab.values()) {
            tabAnim[tabValue.ordinal()] = tabValue == tab ? 1.0f : 0.0f;
        }
    }

    private void updateTabAnimation() {
        for (Tab tabValue : Tab.values()) {
            int idx = tabValue.ordinal();
            float target = tabValue == tab ? 1.0f : 0.0f;
            float current = tabAnim[idx];
            float next = current + (target - current) * TAB_ANIM_SPEED;
            if (Math.abs(next - target) < 0.01f) {
                next = target;
            }
            tabAnim[idx] = MathHelper.clamp(next, 0.0f, 1.0f);
        }
    }

    private void playTabClickSound() {
        if (client == null) {
            return;
        }
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(TAB_CLICK_SOUND_ID), 1.0F));
    }

    private void renderSearchBackground(DrawContext context, int panelX, int mutedColor) {
        if (searchField == null || tab == Tab.SETTINGS) {
            return;
        }
        int x = searchField.getX() - 1;
        int y = searchField.getY() - 1;
        int w = searchField.getWidth() + 2;
        int h = searchField.getHeight() + 2;
        int r = Math.max(3, Math.round(3 * FmeManager.getGuiScale()));
        drawRoundedRect(context, x, y, w, h, r, UI_FIELD_BORDER);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, UI_FIELD_BG);
        context.fill(x, y + h, x + w, y + h + 1, UI_PANEL_BORDER);
    }

    private void drawSidebar(DrawContext context, int panelX, int panelY, int panelHeight, int sidebarWidth, int radius) {
        int x = sidebarX;
        int y = panelY + Math.round(6 * FmeManager.getGuiScale());
        int h = panelHeight - Math.round(12 * FmeManager.getGuiScale());
        int r = Math.max(3, Math.round(radius * 0.8f));
        drawRoundedRect(context, x, y, sidebarWidth, h, r, UI_SIDEBAR_BG);
        // Square off the right edge so only left corners are rounded.
        context.fill(x + sidebarWidth - r, y, x + sidebarWidth, y + h, UI_SIDEBAR_BG);
        context.fill(x + sidebarWidth - 1, y, x + sidebarWidth, y + h, UI_SIDEBAR_BORDER);
    }

    private void drawCell(DrawContext context, int x, int y, int width, int height, int bg, int border) {
        context.fill(x, y, x + width, y + height, bg);
        drawCyanBorder(context, x, y, width, height, border);
        int highlight = withAlpha(mixColor(UI_TEXT, bg, 0.9f), 0x22);
        context.fill(x + 2, y + 2, x + width - 2, y + 3, highlight);
    }

    private void renderGuiSettings(DrawContext context, int panelX, int gridX, int gridY, int textColor, int mutedColor,
                                   int cellBase, int cellSelected, int cellSelectedCustom,
                                   int cellBorder, int cellFavoriteBorder) {
        context.drawText(this.textRenderer, Text.literal("Settings"), gridX + 4, gridY - 12, textColor, false);
    }

    private static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0xFFFFFF);
    }

    private float getOpenProgress() {
        if (openStartedAtMs < 0L) {
            openStartedAtMs = Util.getMeasuringTimeMs();
        }
        long elapsed = Util.getMeasuringTimeMs() - openStartedAtMs;
        float t = elapsed / (float) OPEN_ANIMATION_MS;
        return MathHelper.clamp(t, 0f, 1f);
    }

    private static float easeOutQuad(float t) {
        float inv = 1f - t;
        return 1f - inv * inv;
    }

    private boolean detectBlurSupport() {
        var manager = MinecraftClient.getInstance().getResourceManager();
        return manager.getResource(Identifier.of("minecraft", "shaders/core/gui.vsh")).isPresent()
            && manager.getResource(Identifier.of("minecraft", "shaders/core/gui.fsh")).isPresent();
    }

    private static int mixColor(int colorA, int colorB, float t) {
        int a = colorA & 0xFFFFFF;
        int b = colorB & 0xFFFFFF;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = MathHelper.clamp(Math.round(ar + (br - ar) * t), 0, 255);
        int g = MathHelper.clamp(Math.round(ag + (bg - ag) * t), 0, 255);
        int bOut = MathHelper.clamp(Math.round(ab + (bb - ab) * t), 0, 255);
        return (r << 16) | (g << 8) | bOut;
    }

    private static int dimColor(int color, float factor) {
        int rgb = color & 0xFFFFFF;
        int r = MathHelper.clamp(Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = MathHelper.clamp(Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = MathHelper.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static final class TabButton {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final String label;
        private final Tab tab;

        private TabButton(int x, int y, int width, int height, String label, Tab tab) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.tab = tab;
        }
    }

    private final class TabStyleButton extends ButtonWidget {
        private TabStyleButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            // Suppress default click; we play a custom sound on press.
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            long now = Util.getMeasuringTimeMs();
            float anim = 0f;
            if (this == fmeToggleButton && now < fmeButtonAnimUntilMs) {
                anim = 1f - (fmeButtonAnimUntilMs - now) / (float) SETTINGS_BUTTON_ANIM_MS;
            } else if (this == editToggleButton && now < editButtonAnimUntilMs) {
                anim = 1f - (editButtonAnimUntilMs - now) / (float) SETTINGS_BUTTON_ANIM_MS;
            }
            anim = MathHelper.clamp(anim, 0f, 1f);
            float eased = 1f - (1f - anim) * (1f - anim);
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            boolean hovered = this.isHovered();
            int bgBase = hovered ? UI_TAB_ACTIVE : UI_TAB_INACTIVE;
            int bg = anim > 0f ? mixColor(bgBase, UI_ACCENT, 0.25f * eased) : bgBase;

            context.fill(x, y, x + w, y + h, bg);
            context.fill(x, y, x + 1, y + h, UI_SIDEBAR_BORDER);
            context.fill(x + w - 1, y, x + w, y + h, UI_SIDEBAR_BORDER);
            int accent = withAlpha(UI_ACCENT, MathHelper.clamp(Math.round(255 * Math.max(anim, hovered ? 0.35f : 0f)), 0, 255));
            context.fill(x + 2, y + 2, x + 5, y + h - 2, accent);

            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int textColor = this.active ? UI_TEXT : UI_TEXT_MUTED;
            int textWidth = textRenderer.getWidth(this.getMessage());
            int textX = x + Math.max(8, (w - textWidth) / 2);
            int textY = y + Math.max(0, (h - textRenderer.fontHeight) / 2);
            context.drawText(textRenderer, this.getMessage(), textX, textY, textColor, false);
        }
    }

    private static final class GuiColorSlider extends SliderWidget {
        private final String label;
        private final IntSupplier getter;
        private final IntConsumer setter;

        private GuiColorSlider(int x, int y, int width, int height, String label,
                               IntSupplier getter, IntConsumer setter) {
            super(x, y, width, height, Text.empty(), getter.getAsInt() / 255.0D);
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            int bg = UI_CONTROL_BG;
            int border = UI_CONTROL_BORDER;
            int accent = UI_CONTROL_ACCENT;

            context.fill(x, y, x + w, y + h, bg);
            context.fill(x, y + h - 1, x + w, y + h, border);
            context.fill(x, y, x + 1, y + h, border);
            context.fill(x + w - 1, y, x + w, y + h, border);

            int fillW = Math.max(2, (int) Math.round((w - 2) * this.value));
            context.fill(x + 1, y + h - 2, x + 1 + fillW, y + h - 1, accent);

            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int textColor = this.active ? UI_TEXT : UI_TEXT_MUTED;
            int labelX = x + 8;
            int labelY = y + Math.max(0, (h - textRenderer.fontHeight) / 2);
            int maxWidth = Math.max(0, w - 16);
            String labelText = textRenderer.trimToWidth(this.getMessage().getString(), maxWidth);
            context.drawText(textRenderer, Text.literal(labelText), labelX, labelY, textColor, false);
        }

        @Override
        protected void updateMessage() {
            int v = (int) Math.round(this.value * 255.0D);
            this.setMessage(Text.literal(label + ": " + v));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(this.value * 255.0D);
            setter.accept(v);
            this.updateMessage();
        }

        private void sync() {
            this.value = getter.getAsInt() / 255.0D;
            this.updateMessage();
        }
    }

    private final class GuiScaleSlider extends SliderWidget {
        private GuiScaleSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.empty(), (FmeManager.getGuiScale() - 0.75f) / 0.75f);
            this.updateMessage();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            int bg = UI_CONTROL_BG;
            int border = UI_CONTROL_BORDER;
            int accent = UI_CONTROL_ACCENT;

            context.fill(x, y, x + w, y + h, bg);
            context.fill(x, y + h - 1, x + w, y + h, border);
            context.fill(x, y, x + 1, y + h, border);
            context.fill(x + w - 1, y, x + w, y + h, border);

            int fillW = Math.max(2, (int) Math.round((w - 2) * this.value));
            context.fill(x + 1, y + h - 2, x + 1 + fillW, y + h - 1, accent);

            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int textColor = this.active ? UI_TEXT : UI_TEXT_MUTED;
            int labelX = x + 8;
            int labelY = y + Math.max(0, (h - textRenderer.fontHeight) / 2);
            int maxWidth = Math.max(0, w - 16);
            String labelText = textRenderer.trimToWidth(this.getMessage().getString(), maxWidth);
            context.drawText(textRenderer, Text.literal(labelText), labelX, labelY, textColor, false);
        }

        @Override
        protected void updateMessage() {
            float scale = 0.75f + (float) this.value * 0.75f;
            this.setMessage(Text.literal(String.format(java.util.Locale.ROOT, "UI Scale: %.2f", scale)));
        }

        @Override
        protected void applyValue() {
            float scale = 0.75f + (float) this.value * 0.75f;
            float current = FmeManager.getGuiScale();
            if (Math.abs(scale - current) < 0.005f) {
                return;
            }
            FmeManager.setGuiScale(scale);
            if (client != null) {
                client.setScreen(new FmeScreen(true));
            }
        }
    }

    private void recalcMetrics() {
        float scale = FmeManager.getGuiScale();
        panelWidth = Math.round(BASE_PANEL_WIDTH * scale);
        panelHeight = Math.round(BASE_PANEL_HEIGHT * scale);
        cellWidth = Math.round(BASE_CELL_WIDTH * scale);
        cellHeight = Math.round(BASE_CELL_HEIGHT * scale);
    }
}
