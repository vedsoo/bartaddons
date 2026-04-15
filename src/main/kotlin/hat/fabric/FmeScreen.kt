package hat.fabric

import com.github.noamm9.nvgrenderer.nvg.Gradient
import com.github.noamm9.nvgrenderer.nvg.NVG
import com.github.noamm9.nvgrenderer.nvg.PIPNVG
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundManager
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.awt.Color
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class FmeScreen(
    private val openGuiSettings: Boolean = false,
    private val openItemEditor: Boolean = false
) : Screen(Text.literal("FME")) {
    companion object {
        private const val BASE_PANEL_WIDTH = 640
        private const val BASE_PANEL_HEIGHT = 360
        private const val BASE_CELL_WIDTH = 32
        private const val BASE_CELL_HEIGHT = 32
        private const val BASE_GAP = 6
        private const val BASE_TAB_HEIGHT = 24
        private const val BASE_FIELD_HEIGHT = 18
        private const val BASE_SIDEBAR_WIDTH = 120
        private const val BASE_BUTTON_HEIGHT = 20
        private const val BASE_PAGER_WIDTH = 70
        private const val BASE_PAGER_HEIGHT = 18
        private const val BASE_SUB_TAB_HEIGHT = 18
        private const val PAGE_ANIM_MS = 140L
        private const val TAB_ICON_SIZE = 16

        private val TAB_CLICK_SOUND_ID = Identifier.of("hat", "ui.click")
        private val OPEN_SOUND_ID = Identifier.of("hat", "ui.open")
        private val CLOSE_SOUND_ID = Identifier.of("hat", "ui.close")
        private val TAB_ICON_ALL = Identifier.of("hat", "textures/gui/tabs/all.png")
        private val TAB_ICON_FAVORITES = Identifier.of("hat", "textures/gui/tabs/favorites.png")
        private val TAB_ICON_CUSTOM = Identifier.of("hat", "textures/gui/tabs/custom.png")
        private val TAB_ICON_CONFIGS = Identifier.of("hat", "textures/gui/tabs/configs.png")
        private val TAB_ICON_THEMES = Identifier.of("hat", "textures/gui/tabs/themes.png")
        private val TAB_ICON_LAYOUT = Identifier.of("hat", "textures/gui/tabs/settings.png")
        private val TAB_ICON_OTHER = Identifier.of("hat", "textures/gui/tabs/configs.png")
        private val TAB_ICON_ITEM = Identifier.of("hat", "textures/gui/tabs/custom.png")
        private const val NEW_CUSTOM_THEME_LABEL = "+ New Theme"
    }

    private enum class Tab {
        ALL,
        FAVORITES,
        CUSTOM,
        ITEM,
        OTHER,
        CONFIGS,
        THEMES,
        LAYOUT
    }

    private enum class ThemeSubTab {
        THEMES,
        CUSTOM
    }

    private enum class OtherSubTab {
        GIF,
        VISUAL
    }

    private enum class NavPlacement {
        LEFT,
        TOP
    }

    private val allBlocks = mutableListOf<Block>()
    private val allItems = mutableListOf<Item>()
    private val textureFiles = mutableListOf<Path>()
    private val configNames = mutableListOf<String>()
    private val themeNames = mutableListOf<String>()
    private val customThemeNames = mutableListOf<String>()
    private val layoutEntries = mutableListOf<LayoutEntry>()
    private val otherEntries = mutableListOf<OtherEntry>()
    private val customTextureCache = mutableMapOf<Path, Identifier>()
    private val customTextureFailed = mutableSetOf<Path>()
    private val tabButtons = mutableListOf<TabButton>()
    private val themeSubTabButtons = mutableListOf<ThemeSubTabButton>()
    private val otherSubTabButtons = mutableListOf<OtherSubTabButton>()
    private var tabsWidget: TabsWidget? = null
    private var blockGridWidget: BlockGridWidget? = null
    private var tab = Tab.ALL
    private var themeSubTab = ThemeSubTab.THEMES
    private var otherSubTab = OtherSubTab.GIF
    private var customThemeEditing = false
    private var customThemeDraft: FmeManager.CustomTheme? = null
    private var customThemeNameField: TextFieldWidget? = null
    private val customThemeWidgets = mutableListOf<ClickableWidget>()
    private var page = 0
    private var animFromPage = 0f
    private var animToPage = 0
    private var animStartMs = 0L
    private var animating = false
    private var textureLoadBudget = 0
    private lateinit var searchField: TextFieldWidget

    private var panelX = 0
    private var panelY = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var cellWidth = 0
    private var cellHeight = 0
    private var colGap = 0
    private var rowGap = 0
    private var sidebarWidth = 0
    private var sidebarX = 0
    private var sidebarBottom = 0
    private var tabsEndY = 0
    private var tabsGap = 0
    private var contentX = 0
    private var headerY = 0
    private var searchY = 0
    private var searchX = 0
    private var searchWidth = 0
    private var searchHeight = 0
    private var searchTextOffsetX = 0
    private var searchTextOffsetY = 0
    private var tabsX = 0
    private var tabsWidth = 0
    private var tabsY = 0
    private var baseGridY = 0
    private var gridX = 0
    private var gridY = 0
    private var gridColumns = 0
    private var gridRows = 0
    private var entriesPerPage = 0
    private var gridAreaWidth = 0
    private var baseCellWidth = 0
    private var baseGridColumns = 0
    private var pagerY = 0
    private var settingsY = 0
    private var prevX = 0
    private var nextX = 0
    private var uiScale = 1f
    private var openTimeMs = 0L
    private var themeSubTabsY = 0
    private var themeSubTabHeight = 0
    private var themeSubTabGap = 0

    private data class ItemTargetEntry(
        val title: String,
        val kind: FmeManager.ItemAppearanceTargetType,
        val block: Block? = null,
        val item: Item? = null,
        val texture: Path? = null
    )

    private enum class ConfigEntryKind {
        LOAD_DEFAULT,
        SAVE_CURRENT,
        SAVE_AS,
        NEW_CONFIG,
        STORED
    }

    private data class ConfigEntry(
        val title: String,
        val kind: ConfigEntryKind,
        val configName: String? = null,
        val detail: String = ""
    )

    override fun init() {
        openTimeMs = System.currentTimeMillis()
        playOpenSound()
        recalcMetrics()
        if (allBlocks.isEmpty()) {
            Registries.BLOCK.stream()
                .filter { !it.defaultState.isAir }
                .sorted(compareBy { Registries.BLOCK.getId(it).toString() })
                .forEach(allBlocks::add)
        }
        if (allItems.isEmpty()) {
            Registries.ITEM.stream()
                .filter { it != Items.AIR }
                .sorted(compareBy { Registries.ITEM.getId(it).toString() })
                .forEach(allItems::add)
        }
        if (openItemEditor) {
            tab = Tab.ITEM
        }
        refreshTextures()

        tabButtons.clear()
        val scale = uiScale

        val gap = (BASE_GAP * scale).roundToInt()
        val tabHeight = (BASE_TAB_HEIGHT * scale).roundToInt()
        val fieldHeight = max(BASE_FIELD_HEIGHT.toFloat(), (textRenderer.fontHeight + 4) * scale).roundToInt()
        searchHeight = fieldHeight + max(2, (2 * scale).roundToInt())
        searchTextOffsetX = max(2, (2 * scale).roundToInt())
        searchTextOffsetY = max(5, (5 * scale).roundToInt())
        val pagerHeight = (BASE_PAGER_HEIGHT * scale).roundToInt()
        val pagerWidth = (BASE_PAGER_WIDTH * scale).roundToInt()

        val panelRight = panelX + panelWidth
        val panelBottom = panelY + panelHeight

        headerY = panelY + gap
        val tabsOffset = max(3, (6 * scale).roundToInt())
        tabsY = headerY + tabsOffset
        pagerY = panelBottom - gap - pagerHeight

        sidebarBottom = panelBottom - gap
        if (navPlacement() == NavPlacement.LEFT) {
            sidebarWidth = (layoutSidebarWidth() * scale).roundToInt()
            sidebarX = panelX + gap
            tabsX = sidebarX + gap
            tabsWidth = sidebarWidth - gap * 2
            contentX = sidebarX + sidebarWidth + gap
            searchX = contentX
            searchY = headerY + max(0, (tabHeight - searchHeight) / 2)
            baseGridY = headerY + tabHeight + gap
        } else {
            sidebarWidth = 0
            sidebarX = panelX + gap
            val desiredSearchWidth = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> max(118, (148 * scale).roundToInt())
                FmeManager.LayoutPreset.OBSIDIAN -> max(110, (136 * scale).roundToInt())
                FmeManager.LayoutPreset.SLATE -> max(118, (154 * scale).roundToInt())
                else -> max(120, (170 * scale).roundToInt())
            }
            val horizontalInset = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> max(14, (20 * scale).roundToInt())
                FmeManager.LayoutPreset.OBSIDIAN -> max(10, (12 * scale).roundToInt())
                FmeManager.LayoutPreset.SLATE -> max(16, (18 * scale).roundToInt())
                else -> gap
            }
            tabsX = panelX + horizontalInset
            tabsWidth = max(180, panelWidth - horizontalInset * 2 - gap - desiredSearchWidth)
            searchWidth = desiredSearchWidth
            searchX = tabsX + tabsWidth + gap
            searchY = headerY + when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> max(0, (tabHeight - searchHeight) / 2) - max(1, (2 * scale).roundToInt())
                FmeManager.LayoutPreset.OBSIDIAN -> max(0, (tabHeight - searchHeight) / 2)
                FmeManager.LayoutPreset.SLATE -> max(0, (tabHeight - searchHeight) / 2) - 1
                else -> max(0, (tabHeight - searchHeight) / 2)
            }
            contentX = panelX + horizontalInset
            baseGridY = tabsY + tabHeight + max(gap, (10 * scale).roundToInt())
        }
        val gridWidth = panelRight - gap - contentX
        gridAreaWidth = gridWidth
        gridColumns = max(1, gridWidth / (cellWidth + colGap))
        val actualGridWidth = gridColumns * cellWidth + max(0, gridColumns - 1) * colGap
        gridX = contentX + max(0, (gridWidth - actualGridWidth) / 2)
        baseCellWidth = cellWidth
        baseGridColumns = gridColumns

        gridY = baseGridY
        themeSubTabsY = baseGridY
        themeSubTabHeight = max(14, (BASE_SUB_TAB_HEIGHT * scale).roundToInt())
        themeSubTabGap = max(4, (4 * scale).roundToInt())
        val settingsOffset = max(6, (6 * scale).roundToInt())
        settingsY = gridY + settingsOffset
        val availableGridHeight = pagerY - gap - gridY
        gridRows = max(1, availableGridHeight / (cellHeight + rowGap))
        applyGridLayoutForTab()
        entriesPerPage = max(1, gridColumns * gridRows)
        searchWidth = max(searchWidth, max(80, panelRight - gap - searchX))
        searchField = TextFieldWidget(
            textRenderer,
            searchX + searchTextOffsetX,
            searchY + searchTextOffsetY,
            searchWidth,
            searchHeight,
            Text.literal("Search")
        )
        searchField.setPlaceholder(Text.literal("Search blocks and textures..."))
        searchField.setChangedListener { setPage(0, 1, false) }
        searchField.setDrawsBackground(false)
        addDrawableChild(searchField)
        searchField.visible = true
        searchField.active = true

        customThemeWidgets.clear()
        customThemeNameField = null
        if (customThemeEditing) {
            initCustomThemeEditorWidgets()
        }

        themeSubTabButtons.clear()
        val subTabWidth = max(60, (gridAreaWidth - themeSubTabGap) / 2)
        val subTabX = contentX
        addThemeSubTabButton(subTabX, themeSubTabsY, subTabWidth, themeSubTabHeight, "Themes", ThemeSubTab.THEMES)
        addThemeSubTabButton(
            subTabX + subTabWidth + themeSubTabGap,
            themeSubTabsY,
            subTabWidth,
            themeSubTabHeight,
            "Custom",
            ThemeSubTab.CUSTOM
        )

        otherSubTabButtons.clear()
        val otherWidth = max(60, (gridAreaWidth - themeSubTabGap) / 2)
        addOtherSubTabButton(subTabX, themeSubTabsY, otherWidth, themeSubTabHeight, "GIF", OtherSubTab.GIF)
        addOtherSubTabButton(
            subTabX + otherWidth + themeSubTabGap,
            themeSubTabsY,
            otherWidth,
            themeSubTabHeight,
            "Visual",
            OtherSubTab.VISUAL
        )

        val tabs = listOf(
            Triple("ALL", TAB_ICON_ALL, Tab.ALL),
            Triple("FAVORITES", TAB_ICON_FAVORITES, Tab.FAVORITES),
            Triple("CUSTOM", TAB_ICON_CUSTOM, Tab.CUSTOM),
            Triple("ITEM", TAB_ICON_ITEM, Tab.ITEM),
            Triple("OTHER", TAB_ICON_OTHER, Tab.OTHER),
            Triple("CONFIGS", TAB_ICON_CONFIGS, Tab.CONFIGS),
            Triple("THEMES", TAB_ICON_THEMES, Tab.THEMES),
            Triple("LAYOUT", TAB_ICON_LAYOUT, Tab.LAYOUT)
        )
        val tabGap = max(6, (4 * uiScale).roundToInt())
        tabsGap = tabGap
        tabsWidget = TabsWidget(
            x = tabsX,
            y = tabsY,
            width = tabsWidth,
            tabHeight = tabHeight,
            gapBetween = tabGap,
            tabs = tabs,
            placement = navPlacement()
        )
        tabsWidget?.apply {
            initButtons()
            tabsEndY = endY
        }
        blockGridWidget = BlockGridWidget()

        prevX = panelRight - gap - pagerWidth * 2 - gap
        nextX = panelRight - gap - pagerWidth

        applyThemeSubTabLayout()
        updateTabState()
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val anim = easeOutCubic(((System.currentTimeMillis() - openTimeMs) / 220f).coerceIn(0f, 1f))
        val overlayAlpha = (0xAA * anim).roundToInt().coerceIn(0, 255)
        // Avoid default blur pipeline to prevent "Can only blur once per frame" with other mods.
        context.fill(0, 0, width, height, (overlayAlpha shl 24))

        val panelColor = multiplyAlpha(FmeManager.getGuiPanelColor(), anim)
        val borderColor = multiplyAlpha(FmeManager.getGuiBorderColor(), anim)
        val textColor = multiplyAlpha(FmeManager.getGuiTextColor(), anim)
        val accentColor = multiplyAlpha(FmeManager.getGuiAccentTextColor(), anim)
        val selectionColor = multiplyAlpha(FmeManager.getSelectionBoxColor(), anim)
        val mutedColor = withAlpha(mixColor(textColor, panelColor, 0.55f), (textColor ushr 24) and 0xFF)
        val innerPanelColor = mixColor(panelColor, 0xFF000000.toInt(), 0.12f)
        val sidebarColor = mixColor(panelColor, 0xFF000000.toInt(), 0.18f)

        searchField.setEditableColor(textColor)
        searchField.setUneditableColor(mutedColor)

        drawPanelBackground(context, panelColor, borderColor, innerPanelColor, sidebarColor)
        drawSidebarBranding(context)
        drawSearchFieldBackground(context, panelColor, borderColor)

        drawGrid(context, mouseX, mouseY, textColor, mutedColor, accentColor, selectionColor)
        drawPager(context, textColor, mutedColor, accentColor)

        super.render(context, mouseX, mouseY, delta)
    }

    override fun resize(client: MinecraftClient, width: Int, height: Int) {
        super.resize(client, width, height)
        clearAndInit()
    }

    override fun mouseClicked(click: Click, doubleClick: Boolean): Boolean {
        val mouseX = click.x()
        val mouseY = click.y()
        val button = click.button()
        if (!isInRect(mouseX.toDouble(), mouseY.toDouble(), panelX, panelY, panelWidth, panelHeight)) {
            return true
        }
        if (searchField.visible && searchField.isMouseOver(mouseX, mouseY)) {
            searchField.setFocused(true)
            return searchField.mouseClicked(click, doubleClick)
        }
        searchField.setFocused(false)
        for (button in tabButtons) {
            if (button.mouseClicked(click, doubleClick)) {
                return true
            }
        }
        for (button in themeSubTabButtons) {
            if (button.mouseClicked(click, doubleClick)) {
                return true
            }
        }
        for (button in otherSubTabButtons) {
            if (button.mouseClicked(click, doubleClick)) {
                return true
            }
        }
        if (customThemeEditing) {
            return super.mouseClicked(click, doubleClick)
        }
        if (sidebarWidth > 0 && isInRect(mouseX.toDouble(), mouseY.toDouble(), sidebarX, headerY, sidebarWidth, sidebarBottom - headerY)
            && mouseY >= tabsEndY + tabsGap) {
            return true
        }
        if (blockGridWidget?.handleGridClick(mouseX, mouseY, button) == true) {
            return true
        }
        if (handlePagerClick(mouseX, mouseY, button)) {
            return true
        }
        return super.mouseClicked(click, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (customThemeEditing) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val count = entriesCount()
        if (count <= 0 || entriesPerPage <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        val direction = if (verticalAmount < 0) 1 else -1
        setPage(page + direction, maxPages, true)
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (searchField.visible && searchField.isFocused && searchField.keyPressed(input)) {
            return true
        }
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (searchField.visible && searchField.isFocused && searchField.charTyped(input)) {
            return true
        }
        return super.charTyped(input)
    }

    override fun shouldPause(): Boolean {
        return false
    }

    override fun close() {
        playCloseSound()
        super.close()
    }

    private fun drawSearchFieldBackground(context: DrawContext, panelColor: Int, borderColor: Int) {
        if (!searchField.visible) {
            return
        }
        val x = searchX
        val y = searchY
        val w = searchWidth
        val h = searchHeight
        val bg = when (currentLayout()) {
            FmeManager.LayoutPreset.BLOOM -> withAlpha(mixColor(panelColor, 0xFFFFFFFF.toInt(), 0.16f), 235)
            FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.52f), 245)
            FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(0xFF050505.toInt(), 245)
            FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(panelColor, 0xFF0B1020.toInt(), 0.40f), 236)
            FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(panelColor, 0xFF101538.toInt(), 0.34f), 236)
        }
        drawRoundedRect(context, x, y, w, h, bg, styleBorderColor(borderColor, FmeManager.getGuiAccentTextColor()), styledElementRadius(elementRadius()))
    }

    private fun drawPanelBackground(
        context: DrawContext,
        panelColor: Int,
        borderColor: Int,
        innerPanelColor: Int,
        sidebarColor: Int
    ) {
        val radius = styledPanelRadius(panelRadius())
        val innerRadius = max(2, radius - 3)
        val innerInset = 4
        val innerX = panelX + innerInset
        val innerY = panelY + innerInset
        val innerW = panelWidth - innerInset * 2
        val innerH = panelHeight - innerInset * 2
        val sidebarBottom = panelY + panelHeight - (BASE_GAP * uiScale).roundToInt()
        val sidebarH = max(0, sidebarBottom - headerY)

        val customTheme = FmeManager.getCustomTheme()
        val theme = FmeManager.getTheme()
        val isWhiteTheme = theme == FmeManager.Theme.WHITE
        val isBlackWhiteTheme = theme == FmeManager.Theme.BLACK_WHITE
        val isRedTheme = theme == FmeManager.Theme.RED
        val isMoonwalkerTheme = theme == FmeManager.Theme.MOONWALKER
        val isVioletTheme = theme == FmeManager.Theme.VIOLET
        val isFemboyTheme = theme == FmeManager.Theme.FEMBOY
        val isArgonTheme = theme == FmeManager.Theme.ARGON
        val isMossTheme = theme == FmeManager.Theme.MOSS
        val isHazelTheme = theme == FmeManager.Theme.HAZEL
        val isBlossomTheme = theme == FmeManager.Theme.BLOSSOM
        val isPastelTheme = theme == FmeManager.Theme.PASTEL_MINT
            || theme == FmeManager.Theme.PASTEL_PEACH
            || theme == FmeManager.Theme.PASTEL_LAVENDER
            || theme == FmeManager.Theme.PASTEL_SKY
            || theme == FmeManager.Theme.PASTEL_ROSE
            || theme == FmeManager.Theme.PASTEL_BUTTER
            || theme == FmeManager.Theme.PASTEL_AQUA
        val gradientStart: Color
        val gradientEnd: Color
        if (customTheme != null) {
            gradientStart = toAwtColor(customTheme.gradientStart)
            gradientEnd = toAwtColor(customTheme.gradientEnd)
        } else if (isWhiteTheme) {
            gradientStart = Color(255, 255, 255, 235)
            gradientEnd = Color(0, 0, 0, 245)
        } else if (isBlackWhiteTheme) {
            gradientStart = Color(0, 0, 0, 245)
            gradientEnd = Color(0, 0, 0, 245)
        } else if (isRedTheme) {
            gradientStart = Color(255, 0, 0, 235)
            gradientEnd = Color(0, 0, 0, 245)
        } else if (isMoonwalkerTheme) {
            gradientStart = Color(0x15, 0x23, 0x31, 235)
            gradientEnd = Color(0, 0, 0, 245)
        } else if (isVioletTheme) {
            gradientStart = Color(0x65, 0x4E, 0xA3, 235)
            gradientEnd = Color(0xEA, 0xAF, 0xC8, 245)
        } else if (isFemboyTheme) {
            gradientStart = Color(0xCF, 0x62, 0xA9, 235)
            gradientEnd = Color(0xFF, 0xFF, 0xFF, 245)
        } else if (isArgonTheme) {
            gradientStart = Color(0x03, 0x00, 0x1E, 235)
            gradientEnd = Color(0xFD, 0xEF, 0xF9, 245)
        } else if (isMossTheme) {
            gradientStart = Color(0x13, 0x4E, 0x5E, 235)
            gradientEnd = Color(0x71, 0xB2, 0x80, 245)
        } else if (isHazelTheme) {
            gradientStart = Color(0x77, 0xA1, 0xD3, 235)
            gradientEnd = Color(0xE6, 0x84, 0xAE, 245)
        } else if (isBlossomTheme) {
            gradientStart = Color(0xF8, 0xF2, 0xFC, 238)
            gradientEnd = Color(0xE4, 0xD2, 0xF5, 245)
        } else if (isPastelTheme) {
            gradientStart = Color(255, 255, 255, 235)
            gradientEnd = Color(235, 240, 255, 245)
        } else {
            gradientStart = Color(80, 130, 210, 235)
            gradientEnd = Color(10, 16, 30, 245)
        }
        val themedPanel = stylePanelColor(panelColor)
        val themedBorder = styleBorderColor(borderColor, FmeManager.getGuiAccentTextColor())
        val themedInner = styleInnerPanelColor(innerPanelColor, FmeManager.getGuiAccentTextColor())
        val flatTheme = customTheme?.flatTheme ?: (isWhiteTheme || isBlackWhiteTheme || isRedTheme || isMoonwalkerTheme || isVioletTheme
            || isFemboyTheme || isArgonTheme || isMossTheme || isHazelTheme || isPastelTheme || isBlossomTheme)
        val useFlatTheme = when (currentLayout()) {
            FmeManager.LayoutPreset.OBSIDIAN -> true
            FmeManager.LayoutPreset.TOPAZ -> false
            else -> flatTheme
        }
        val border = toAwtColor(themedBorder, if (isWhiteTheme && currentLayout() == FmeManager.LayoutPreset.BLOOM) 120 else null)
        val inner = toAwtColor(themedInner, 170)
        val sidebar = toAwtColor(styleSidebarColor(sidebarColor, FmeManager.getGuiAccentTextColor()))

        PIPNVG.drawNVG(context, 0, 0, width, height) {
            if (customTheme == null && (isFemboyTheme || isArgonTheme)) {
                val colors = if (isFemboyTheme) {
                    arrayOf(
                        Color(0xCF, 0x62, 0xA9, 235),
                        Color(0xE4, 0xAD, 0xCD, 235),
                        Color(0x58, 0xCE, 0xF8, 235),
                        Color(0xFF, 0xFF, 0xFF, 245)
                    )
                } else {
                    arrayOf(
                        Color(0x03, 0x00, 0x1E, 235),
                        Color(0x73, 0x03, 0xC0, 235),
                        Color(0xEC, 0x38, 0xBC, 235),
                        Color(0xFD, 0xEF, 0xF9, 245)
                    )
                }
                val segmentH = panelHeight / 3f
                val r = radius.toFloat()
                NVG.gradientRect(panelX, panelY, panelWidth, segmentH, colors[0], colors[1], Gradient.TopToBottom, r)
                if (segmentH > r) {
                    NVG.rect(panelX, panelY + segmentH - r, panelWidth, r, colors[1])
                }
                NVG.gradientRect(panelX, panelY + segmentH, panelWidth, segmentH, colors[1], colors[2], Gradient.TopToBottom, 0f)
                NVG.gradientRect(panelX, panelY + segmentH * 2f, panelWidth, panelHeight - segmentH * 2f, colors[2], colors[3], Gradient.TopToBottom, r)
                if (segmentH > r) {
                    NVG.rect(panelX, panelY + segmentH * 2f, panelWidth, r, colors[2])
                }
            } else {
                val start = when (currentLayout()) {
                    FmeManager.LayoutPreset.TOPAZ -> toAwtColor(mixColor(themedPanel, 0xFF1B120D.toInt(), 0.12f))
                    FmeManager.LayoutPreset.OBSIDIAN -> toAwtColor(themedPanel)
                    FmeManager.LayoutPreset.SLATE -> toAwtColor(mixColor(themedPanel, 0xFF0C1220.toInt(), 0.18f))
                    FmeManager.LayoutPreset.ALPINE -> toAwtColor(mixColor(themedPanel, 0xFF232A5F.toInt(), 0.12f))
                    else -> gradientStart
                }
                val end = when (currentLayout()) {
                    FmeManager.LayoutPreset.TOPAZ -> toAwtColor(mixColor(themedPanel, 0xFF000000.toInt(), 0.22f))
                    FmeManager.LayoutPreset.OBSIDIAN -> toAwtColor(mixColor(themedPanel, 0xFF000000.toInt(), 0.08f))
                    FmeManager.LayoutPreset.SLATE -> toAwtColor(mixColor(themedPanel, 0xFF0A0E17.toInt(), 0.22f))
                    FmeManager.LayoutPreset.ALPINE -> toAwtColor(mixColor(themedPanel, 0xFF101537.toInt(), 0.22f))
                    else -> gradientEnd
                }
                NVG.gradientRect(panelX, panelY, panelWidth, panelHeight, start, end, Gradient.TopToBottom, radius.toFloat())
            }
            NVG.hollowRect(panelX, panelY, panelWidth, panelHeight, 1f, border, radius.toFloat())
            if (!useFlatTheme) {
                NVG.rect(innerX, innerY, innerW, innerH, inner, innerRadius.toFloat())
            }
            if (sidebarH > 0) {
                NVG.rect(sidebarX, headerY, sidebarWidth, sidebarH, sidebar, innerRadius.toFloat())
            }
        }
        if (customTheme == null && currentLayout() == FmeManager.LayoutPreset.BLOOM && isBlossomTheme) {
            drawBlossomDecorations(context)
        }
    }

    private fun drawSidebarBranding(context: DrawContext) {
        val scale = uiScale
        val rawLabel = "bart addons"
        val fontSize = max(10f, 14f * scale)
        val padding = max(6, (6 * scale).roundToInt())
        val maxWidth = max(40, if (navPlacement() == NavPlacement.LEFT) sidebarWidth - padding * 2 else panelWidth - padding * 2)
        val label = trimToWidth(rawLabel, maxWidth)
        val labelWidth = textRenderer.getWidth(label)
        val x = when (navPlacement()) {
            NavPlacement.LEFT -> sidebarX + padding
            NavPlacement.TOP -> panelX + max(10, (panelWidth - labelWidth) / 2)
        }
        val y = when (navPlacement()) {
            NavPlacement.LEFT -> if (currentLayout() == FmeManager.LayoutPreset.ALPINE) {
                panelY + padding + fontSize.toInt()
            } else {
                sidebarBottom - padding - fontSize.toInt()
            }
            NavPlacement.TOP -> panelY + panelHeight - padding - fontSize.toInt()
        }
        val clampedX = x.coerceIn(panelX + padding, panelX + panelWidth - padding - labelWidth)
        val clampedY = y.coerceIn(panelY + padding + fontSize.toInt(), panelY + panelHeight - padding)
        val palette = listOf(
            0xFF440347.toInt(),
            0xFF9D6DC7.toInt(),
            0xFFDEB6DC.toInt(),
            0xFFD3C3E0.toInt()
        )
        val periodMs = 2400f
        val base = (System.currentTimeMillis() % periodMs.toLong()) / periodMs

        PIPNVG.drawNVG(context, 0, 0, this.width, this.height) {
            val color = toAwtColor(animatedPaletteColor(palette, base, 0))
            NVG.text(label, clampedX.toFloat(), clampedY.toFloat(), fontSize, color, NVG.font)
        }
    }

    private fun drawBlossomDecorations(context: DrawContext) {
        val leaf = 0xFF6E9F58.toInt()
        val stem = 0xFF7C9159.toInt()
        val flower = 0xFFF4F0FB.toInt()
        val flowerCore = 0xFFF1D56C.toInt()
        val petal = 0xFFA889D6.toInt()

        fun bloom(cx: Int, cy: Int, size: Int) {
            val s = max(2, size)
            context.fill(cx - s, cy - 1, cx + s, cy + 1, petal)
            context.fill(cx - 1, cy - s, cx + 1, cy + s, petal)
            context.fill(cx - s + 1, cy - s + 1, cx + s - 1, cy + s - 1, flower)
            context.fill(cx - 1, cy - 1, cx + 1, cy + 1, flowerCore)
        }

        fun vine(startX: Int, startY: Int, endX: Int, endY: Int, steps: Int) {
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val x = MathHelper.lerp(t, startX.toFloat(), endX.toFloat()).roundToInt()
                val y = MathHelper.lerp(t, startY.toFloat(), endY.toFloat()).roundToInt()
                context.fill(x - 1, y - 1, x + 2, y + 2, stem)
                if (i % 2 == 0) {
                    context.fill(x - 4, y - 2, x - 1, y + 1, leaf)
                } else {
                    context.fill(x + 1, y - 1, x + 4, y + 2, leaf)
                }
            }
        }

        val inset = max(6, (8 * uiScale).roundToInt())
        vine(panelX + inset, panelY + inset + 8, panelX + sidebarWidth - inset, panelY + inset, 18)
        vine(panelX + panelWidth - inset - 24, panelY + inset, panelX + panelWidth - inset, panelY + panelHeight / 4, 10)
        vine(panelX + inset / 2, panelY + panelHeight - inset - 24, panelX + panelWidth / 3, panelY + panelHeight - inset, 16)

        bloom(panelX + inset + 10, panelY + inset + 10, 5)
        bloom(panelX + sidebarWidth - inset, panelY + 16, 4)
        bloom(panelX + panelWidth - inset - 16, panelY + inset + 8, 6)
        bloom(panelX + panelWidth - inset - 8, panelY + panelHeight / 4, 4)
        bloom(panelX + 18, panelY + panelHeight - inset - 18, 6)
        bloom(panelX + panelWidth - 18, panelY + panelHeight - inset - 14, 5)
    }

    private fun animatedPaletteColor(palette: List<Int>, base: Float, offset: Int): Int {
        if (palette.isEmpty()) {
            return 0xFFFFFFFF.toInt()
        }
        val n = palette.size
        val t = ((base + offset / n.toFloat()) % 1f) * n
        val idx = t.toInt().coerceIn(0, n - 1)
        val next = (idx + 1) % n
        val frac = t - idx
        return mixColor(palette[idx], palette[next], frac)
    }

    private fun drawGrid(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        textColor: Int,
        mutedColor: Int,
        accentColor: Int,
        selectionColor: Int
    ) {
        if (customThemeEditing) {
            return
        }
        val count = entriesCount()
        if (entriesPerPage <= 0) {
            return
        }
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        if (page >= maxPages) {
            setPage(maxPages - 1, maxPages, false)
        }

        textureLoadBudget = 3
        val isCustom = tab == Tab.CUSTOM
        val isItemTab = tab == Tab.ITEM
        val isOther = tab == Tab.OTHER
        val isConfigs = tab == Tab.CONFIGS
        val isThemes = tab == Tab.THEMES
        val isLayout = tab == Tab.LAYOUT
        val isCustomThemes = isThemes && themeSubTab == ThemeSubTab.CUSTOM
        val entriesBlocks = if (isCustom || isItemTab || isOther || isConfigs || isThemes || isLayout) emptyList() else visibleEntries()
        val entriesItems = if (isItemTab) visibleItemEntries() else emptyList()
        val entriesTextures = if (isCustom) visibleTextureEntries() else emptyList()
        val entriesConfigs = if (isConfigs) visibleConfigEntries() else emptyList()
        val entriesThemes = if (isThemes) visibleThemeEntries() else emptyList()
        val entriesLayouts = if (isLayout) visibleLayoutEntries() else emptyList()
        val entriesOther = if (isOther) visibleOtherEntries() else emptyList()
        val cellBg = styleCellBackground(panelColor())
        val cellBorder = styleCellBorder(textColor, panelColor(), accentColor)
        val cellSelected = styleSelectedCell(selectionColor, panelColor(), accentColor)
        val pulse = animationAmount(FmeManager.getSelectionAnimation(), 900f)

        if (count == 0) {
            setPage(0, 1, false)
            val emptyLabel = when {
                isCustom -> "No custom textures found"
                isItemTab -> "No item targets found"
                isOther -> "Nothing here yet"
                isConfigs -> "No config actions found"
                isThemes && isCustomThemes -> "No custom themes found"
                isThemes -> "No themes found"
                isLayout -> "No layouts found"
                else -> "No blocks found"
            }
            context.drawText(textRenderer, Text.literal(emptyLabel),
                gridX, gridY, mutedColor, false)
            if (isCustom || isItemTab) {
                val info = "Drop PNGs into: config/fme/custom_textures"
                context.drawText(textRenderer, Text.literal(trimToWidth(info, panelWidth - (gridX - panelX) - 12)),
                    gridX, gridY + textRenderer.fontHeight + 4, mutedColor, false)
            }
            if (isConfigs) {
                val info = "Drop JSONs into: " + FmeManager.getConfigDir()
                context.drawText(textRenderer, Text.literal(trimToWidth(info, panelWidth - (gridX - panelX) - 12)),
                    gridX, gridY + textRenderer.fontHeight + 4, mutedColor, false)
            }
            return
        }

        val allowHover = !animating
        val renderItems = !(animating && !isCustom && !isConfigs)
        var hoveredTooltip: Text? = null

        fun drawPage(pageIndex: Int, yOffset: Float) {
            if (pageIndex < 0 || pageIndex >= maxPages) {
                return
            }
            for (i in 0 until entriesPerPage) {
                val idx = pageIndex * entriesPerPage + i
                val col = i % gridColumns
                val row = i / gridColumns
                if (row >= gridRows) {
                    break
                }
                val x = gridX + col * (cellWidth + colGap)
                val y = gridY + row * (cellHeight + rowGap) + yOffset
                val yDraw = y.roundToInt()
                val hovered = allowHover && isInRect(mouseX.toDouble(), mouseY.toDouble(), x, yDraw, cellWidth, cellHeight)
                if (idx >= count) {
                    continue
                }

                if (isCustom) {
                    val path = entriesTextures[idx]
                    val name = path.fileName.toString()
                    val isSelected = name.equals(FmeManager.getSelectedCustomTextureName(), ignoreCase = true)
                    val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                    val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                    val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                    drawCell(context, x, yDraw, bg, border)
                    val textureId = getCustomTextureId(path)
                    if (textureId != null) {
                        val padding = max(2, (4 * uiScale).roundToInt())
                        val size = max(1, minOf(cellWidth, cellHeight) - padding * 2)
                        val drawX = x + (cellWidth - size) / 2
                        val drawY = yDraw + (cellHeight - size) / 2
                        val textureSize = HatTextureManager.getCustomTextureSize(path)
                        val texSize = if (textureSize > 0) textureSize else size
                        context.drawTexture(
                            RenderPipelines.GUI_TEXTURED,
                            textureId,
                            drawX,
                            drawY,
                            0f,
                            0f,
                            size,
                            size,
                            texSize,
                            texSize,
                            texSize,
                            texSize
                        )
                    } else {
                        val label = trimToWidth(name, cellWidth - 12)
                        val labelY = yDraw + max(0, (cellHeight - textRenderer.fontHeight) / 2)
                        context.drawText(textRenderer, Text.literal(label), x + 8, labelY, textColor, false)
                    }
                    if (hovered) {
                        hoveredTooltip = Text.literal(name)
                    }
                    continue
                }
                if (isItemTab) {
                    val entry = entriesItems[idx]
                    val currentMapping = FmeManager.itemAppearanceAt(FmeManager.getPendingItemEditStack())
                    val isSelected = when (entry.kind) {
                        FmeManager.ItemAppearanceTargetType.BLOCK ->
                            currentMapping?.targetType == FmeManager.ItemAppearanceTargetType.BLOCK &&
                                currentMapping.targetValue == Registries.BLOCK.getId(entry.block!!).toString()
                        FmeManager.ItemAppearanceTargetType.ITEM ->
                            currentMapping?.targetType == FmeManager.ItemAppearanceTargetType.ITEM &&
                                currentMapping.targetValue == Registries.ITEM.getId(entry.item!!).toString()
                        FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE ->
                            currentMapping?.targetType == FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE &&
                                currentMapping.targetValue.equals(entry.texture?.fileName?.toString() ?: "", true)
                    }
                    val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                    val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                    val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                    drawCell(context, x, yDraw, bg, border)
                    when (entry.kind) {
                        FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE -> {
                            val path = entry.texture!!
                            val textureId = getCustomTextureId(path)
                            if (textureId != null) {
                                val padding = max(2, (4 * uiScale).roundToInt())
                                val size = max(1, minOf(cellWidth, cellHeight) - padding * 2)
                                val drawX = x + (cellWidth - size) / 2
                                val drawY = yDraw + (cellHeight - size) / 2
                                val textureSize = HatTextureManager.getCustomTextureSize(path)
                                val texSize = if (textureSize > 0) textureSize else size
                                context.drawTexture(
                                    RenderPipelines.GUI_TEXTURED,
                                    textureId,
                                    drawX,
                                    drawY,
                                    0f,
                                    0f,
                                    size,
                                    size,
                                    texSize,
                                    texSize,
                                    texSize,
                                    texSize
                                )
                            }
                        }
                        FmeManager.ItemAppearanceTargetType.ITEM -> {
                            if (renderItems && entry.item != null) {
                                val iconSize = 16
                                context.drawItem(
                                    ItemStack(entry.item),
                                    x + max(0, (cellWidth - iconSize) / 2),
                                    yDraw + max(0, (cellHeight - iconSize) / 2)
                                )
                            }
                        }
                        FmeManager.ItemAppearanceTargetType.BLOCK -> {
                            val item = entry.block?.asItem()
                            if (renderItems && item != null && item != Items.AIR) {
                                val iconSize = 16
                                context.drawItem(
                                    ItemStack(item),
                                    x + max(0, (cellWidth - iconSize) / 2),
                                    yDraw + max(0, (cellHeight - iconSize) / 2)
                                )
                            }
                        }
                    }
                    if (hovered) {
                        hoveredTooltip = Text.literal(itemTargetTooltip(entry))
                    }
                    continue
                }
                if (isConfigs) {
                    val entry = entriesConfigs[idx]
                    val isSelected = entry.kind == ConfigEntryKind.STORED &&
                        entry.configName.equals(FmeManager.getCurrentConfigName(), ignoreCase = true)
                    val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                    val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                    val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                    drawCell(context, x, yDraw, bg, border)
                    val title = trimToWidth(entry.title, cellWidth - 12)
                    val titleY = yDraw + max(4, (6 * uiScale).roundToInt())
                    context.drawText(textRenderer, Text.literal(title), x + 8, titleY, textColor, false)
                    if (entry.detail.isNotBlank()) {
                        val detail = trimToWidth(entry.detail, cellWidth - 16)
                        val detailColor = if (isSelected) accentColor else mutedColor
                        context.drawText(textRenderer, Text.literal(detail), x + 8, titleY + textRenderer.fontHeight + 3, detailColor, false)
                    }
                    if (hovered) {
                        hoveredTooltip = Text.literal(
                            if (entry.detail.isBlank()) entry.title else "${entry.title} - ${entry.detail}"
                        )
                    }
                    continue
                }
                if (isThemes) {
                    val name = entriesThemes[idx]
                    val isSelected = if (isCustomThemes) {
                        name.equals(FmeManager.getCustomThemeName() ?: "", ignoreCase = true)
                    } else {
                        name.equals(currentThemeLabel(), ignoreCase = true)
                    }
                    val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                    val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                    val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                    drawCell(context, x, yDraw, bg, border)
                    val label = trimToWidth(name, cellWidth - 12)
                    val labelY = yDraw + max(0, (cellHeight - textRenderer.fontHeight) / 2)
                    context.drawText(textRenderer, Text.literal(label), x + 8, labelY, textColor, false)
                    if (hovered) {
                        hoveredTooltip = Text.literal(name)
                    }
                    continue
                }
                if (isLayout) {
                    val entry = entriesLayouts[idx]
                    val isSelected = entry.preset == FmeManager.getLayoutPreset()
                    val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                    val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                    val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                    drawCell(context, x, yDraw, bg, border)
                    val titleY = yDraw + max(4, (6 * uiScale).roundToInt())
                    context.drawText(textRenderer, Text.literal(entry.title), x + 8, titleY, textColor, false)
                    val descColor = if (isSelected) accentColor else mutedColor
                    val detail = trimToWidth(entry.description, cellWidth - 16)
                    context.drawText(
                        textRenderer,
                        Text.literal(detail),
                        x + 8,
                        titleY + textRenderer.fontHeight + 4,
                        descColor,
                        false
                    )
                    if (hovered) {
                        hoveredTooltip = Text.literal("${entry.title}: ${entry.description}")
                    }
                    continue
                }
                if (isOther) {
                    val entry = entriesOther[idx]
                    val baseBg = cellBg
                    val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                    val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                    drawCell(context, x, yDraw, bg, border)
                    val labelY = yDraw + max(0, (cellHeight - textRenderer.fontHeight) / 2)
                    val value = entry.value
                    val valueWidth = if (value == null) 0 else textRenderer.getWidth(value)
                    val colorPreview = entry.colorPreview
                    val previewSize = if (colorPreview == null) 0 else 8
                    val previewGap = if (colorPreview == null) 0 else 4
                    val rightReserve = valueWidth + previewSize + previewGap
                    val title = trimToWidth(entry.title, cellWidth - 12 - rightReserve)
                    context.drawText(textRenderer, Text.literal(title), x + 8, labelY, textColor, false)
                    if (value != null) {
                        val valueX = x + cellWidth - 8 - valueWidth
                        val valueColor = entry.valueColor ?: textColor
                        context.drawText(textRenderer, Text.literal(value), valueX, labelY, valueColor, false)
                    }
                    if (colorPreview != null) {
                        val previewX = x + cellWidth - 8 - valueWidth - previewGap - previewSize
                        val previewY = labelY + (textRenderer.fontHeight - previewSize) / 2
                        context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, colorPreview)
                        context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY, cellBorder)
                        context.fill(previewX - 1, previewY + previewSize, previewX + previewSize + 1, previewY + previewSize + 1, cellBorder)
                        context.fill(previewX - 1, previewY, previewX, previewY + previewSize, cellBorder)
                        context.fill(previewX + previewSize, previewY, previewX + previewSize + 1, previewY + previewSize, cellBorder)
                    }
                    if (hovered) {
                        hoveredTooltip = Text.literal(entry.tooltip ?: entry.title)
                    }
                    continue
                }

                val block = entriesBlocks[idx]
                val isSelected = FmeManager.getSelectedSourceType() == FmeManager.SelectedSourceType.BLOCK
                    && block == FmeManager.getSelectedSource()
                val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                drawCell(context, x, yDraw, bg, border)
                val item = block.asItem()
                val hasItem = item != Items.AIR
                val iconSize = 16
                val iconX = x + max(0, (cellWidth - iconSize) / 2)
                val iconY = yDraw + max(0, (cellHeight - iconSize) / 2)
                if (renderItems && hasItem) {
                    context.drawItem(ItemStack(item), iconX, iconY)
                }
                if (renderItems && FmeManager.isFavorite(block)) {
                    drawFavoriteStar(context, x, yDraw, accentColor)
                }
                if (hovered) {
                    hoveredTooltip = Text.literal(block.name.string)
                }
            }
        }

        val gridW = gridColumns * cellWidth + max(0, gridColumns - 1) * colGap
        val gridH = gridRows * cellHeight + max(0, gridRows - 1) * rowGap
        context.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH)
        val now = System.currentTimeMillis()
        val anim = computePageAnim(now, maxPages)
        if (anim == null) {
            drawPage(page, 0f)
        } else {
            val pageHeight = gridH.toFloat()
            val baseOffset = if (anim.direction < 0) {
                -pageHeight + anim.progress * pageHeight
            } else {
                -anim.progress * pageHeight
            }
            val nextOffset = if (anim.direction < 0) {
                anim.progress * pageHeight
            } else {
                (1f - anim.progress) * pageHeight
            }
            if (anim.direction > 0) {
                drawPage(anim.basePage, baseOffset)
                drawPage(anim.nextPage, nextOffset)
            } else {
                drawPage(anim.nextPage, nextOffset)
                drawPage(anim.basePage, baseOffset)
            }
        }
        context.disableScissor()
        if (hoveredTooltip != null) {
            drawThemedTooltip(context, hoveredTooltip, mouseX, mouseY, textColor, panelColor(), accentColor)
        }

    }

    private fun drawPager(context: DrawContext, textColor: Int, mutedColor: Int, accentColor: Int) {
        if (customThemeEditing) {
            return
        }
        val count = entriesCount()
        if (entriesPerPage <= 0) {
            return
        }
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        val label = "Page ${page + 1} / $maxPages"
        val labelWidth = textRenderer.getWidth(label)
        val pagerHeight = (BASE_PAGER_HEIGHT * uiScale).roundToInt()
        val labelX = prevX - labelWidth - 10
        val labelY = pagerY + max(0, (pagerHeight - textRenderer.fontHeight) / 2)
        context.drawText(textRenderer, Text.literal(label), labelX, labelY, mutedColor, false)

        drawPagerButton(context, prevX, pagerY, textColor, accentColor, "<", page > 0)
        drawPagerButton(context, nextX, pagerY, textColor, accentColor, ">", page < maxPages - 1)
    }

    private fun drawPagerButton(
        context: DrawContext,
        x: Int,
        y: Int,
        textColor: Int,
        accentColor: Int,
        label: String,
        enabled: Boolean
    ) {
        val w = (BASE_PAGER_WIDTH * uiScale).roundToInt()
        val h = (BASE_PAGER_HEIGHT * uiScale).roundToInt()
        val baseBg = if (enabled) styleCellBackground(panelColor()) else withAlpha(styleCellBackground(panelColor()), 120)
        val bg = when (currentLayout()) {
            FmeManager.LayoutPreset.TOPAZ -> if (enabled) mixColor(baseBg, 0xFF120D09.toInt(), 0.25f) else withAlpha(baseBg, 110)
            FmeManager.LayoutPreset.OBSIDIAN -> if (enabled) withAlpha(0xFF050505.toInt(), 240) else withAlpha(0xFF080808.toInt(), 130)
            else -> baseBg
        }
        val border = if (enabled) styleBorderColor(FmeManager.getGuiTextColor(), accentColor) else withAlpha(styleBorderColor(FmeManager.getGuiTextColor(), accentColor), 120)
        drawRoundedRect(context, x, y, w, h, bg, border, styledElementRadius(elementRadius()))
        val tx = x + max(0, (w - textRenderer.getWidth(label)) / 2)
        val ty = y + max(0, (h - textRenderer.fontHeight) / 2)
        context.drawText(textRenderer, Text.literal(label), tx, ty, if (enabled) textColor else withAlpha(textColor, 120), false)
    }

    private fun drawCell(context: DrawContext, x: Int, y: Int, bg: Int, border: Int) {
        val radius = styledElementRadius(elementRadius())
        when (currentLayout()) {
            FmeManager.LayoutPreset.TOPAZ -> {
                drawRoundedRect(context, x, y, cellWidth, cellHeight, bg, border, radius)
                drawBorder(context, x + 4, y + 4, max(0, cellWidth - 8), 1, withAlpha(border, 90))
            }
            FmeManager.LayoutPreset.OBSIDIAN -> {
                context.fill(x, y, x + cellWidth, y + cellHeight, bg)
                drawBorder(context, x, y, cellWidth, cellHeight, border)
            }
            else -> drawRoundedRect(context, x, y, cellWidth, cellHeight, bg, border, radius)
        }
    }

    private fun drawFavoriteStar(context: DrawContext, x: Int, y: Int, color: Int) {
        val star = "*"
        val sx = x + cellWidth - textRenderer.getWidth(star) - 4
        val sy = y + 2
        context.drawText(textRenderer, Text.literal(star), sx, sy, color, false)
    }

    private fun drawThemedTooltip(
        context: DrawContext,
        text: Text,
        mouseX: Int,
        mouseY: Int,
        textColor: Int,
        panelColor: Int,
        accentColor: Int
    ) {
        val paddingX = max(4, (4 * uiScale).roundToInt())
        val paddingY = max(3, (3 * uiScale).roundToInt())
        val maxWidth = (width - 20).coerceAtLeast(40)
        val textStr = text.string
        val tooltipText = Text.literal(trimToWidth(textStr, maxWidth - paddingX * 2))
        val textWidth = textRenderer.getWidth(tooltipText)
        val textHeight = textRenderer.fontHeight
        val boxW = textWidth + paddingX * 2
        val boxH = textHeight + paddingY * 2
        val radius = styledElementRadius(elementRadius())
        val bg = when (currentLayout()) {
            FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.58f), 240)
            FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(0xFF040404.toInt(), 245)
            FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(panelColor, 0xFF0A0E18.toInt(), 0.44f), 235)
            FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(panelColor, 0xFF10153B.toInt(), 0.38f), 238)
            else -> withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.45f), 235)
        }
        val border = styleBorderColor(mixColor(accentColor, panelColor, 0.55f), accentColor)

        var x = mouseX + 10
        var y = mouseY + 10
        if (x + boxW > width - 6) {
            x = width - boxW - 6
        }
        if (y + boxH > height - 6) {
            y = height - boxH - 6
        }
        drawRoundedRect(context, x, y, boxW, boxH, bg, border, radius)
        context.drawText(textRenderer, tooltipText, x + paddingX, y + paddingY, textColor, false)
    }

    private fun handlePagerClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) {
            return false
        }
        val w = (BASE_PAGER_WIDTH * uiScale).roundToInt()
        val h = (BASE_PAGER_HEIGHT * uiScale).roundToInt()
        val count = entriesCount()
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        if (isInRect(mouseX, mouseY, prevX, pagerY, w, h) && page > 0) {
            setPage(page - 1, maxPages, true)
            playTabClickSound()
            return true
        }
        if (isInRect(mouseX, mouseY, nextX, pagerY, w, h) && page < maxPages - 1) {
            setPage(page + 1, maxPages, true)
            playTabClickSound()
            return true
        }
        return false
    }

    private fun addTabButton(
        x: Int,
        y: Int,
        height: Int,
        width: Int,
        gapBetween: Int,
        label: String,
        icon: Identifier,
        tab: Tab
    ): Int {
        val button = TabButton(x, y, width, height, label, icon) { selectTab(tab) }
        tabButtons.add(button)
        addDrawableChild(button)
        return x + width + gapBetween
    }

    private fun addThemeSubTabButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        tab: ThemeSubTab
    ) {
        val button = ThemeSubTabButton(x, y, width, height, label) { selectThemeSubTab(tab) }
        themeSubTabButtons.add(button)
        addDrawableChild(button)
    }

    private fun addOtherSubTabButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: String,
        tab: OtherSubTab
    ) {
        val button = OtherSubTabButton(x, y, width, height, label) { selectOtherSubTab(tab) }
        otherSubTabButtons.add(button)
        addDrawableChild(button)
    }

    private fun selectTab(next: Tab) {
        if (tab == next) {
            return
        }
        tab = next
        if (tab != Tab.THEMES || themeSubTab != ThemeSubTab.CUSTOM) {
            customThemeEditing = false
            customThemeDraft = null
        }
        setPage(0, 1, false)
        if (tab == Tab.CUSTOM || tab == Tab.ITEM) {
            refreshTextures()
        } else if (tab == Tab.OTHER) {
            refreshOther()
        } else if (tab == Tab.CONFIGS) {
            refreshConfigs()
        } else if (tab == Tab.LAYOUT) {
            refreshLayouts()
        } else if (tab == Tab.THEMES) {
            if (themeSubTab == ThemeSubTab.THEMES) {
                refreshThemes()
            } else {
                refreshCustomThemes()
            }
        }
        applyGridLayoutForTab()
        applyThemeSubTabLayout()
        updateTabState()
        playTabClickSound()
    }

    private fun selectThemeSubTab(next: ThemeSubTab) {
        if (themeSubTab == next) {
            return
        }
        themeSubTab = next
        if (themeSubTab != ThemeSubTab.CUSTOM) {
            customThemeEditing = false
            customThemeDraft = null
        }
        setPage(0, 1, false)
        if (tab == Tab.THEMES) {
            if (themeSubTab == ThemeSubTab.THEMES) {
                refreshThemes()
            } else {
                refreshCustomThemes()
            }
        }
        applyThemeSubTabLayout()
        updateTabState()
        playTabClickSound()
    }

    private fun selectOtherSubTab(next: OtherSubTab) {
        if (otherSubTab == next) {
            return
        }
        otherSubTab = next
        setPage(0, 1, false)
        if (tab == Tab.OTHER) {
            refreshOther()
        }
        applyThemeSubTabLayout()
        updateTabState()
        playTabClickSound()
    }

    private fun updateTabState() {
        searchField.visible = !customThemeEditing
        searchField.active = !customThemeEditing
        if (!customThemeEditing) {
            searchField.setFocused(true)
        }
        tabButtons.forEach { it.updateActive(tab) }
        themeSubTabButtons.forEach { it.updateActive(themeSubTab) }
        otherSubTabButtons.forEach { it.updateActive(otherSubTab) }
        customThemeWidgets.forEach {
            it.visible = customThemeEditing
            it.active = customThemeEditing
        }
        val placeholder = when (tab) {
            Tab.ITEM -> "Search items, blocks and textures..."
            Tab.CONFIGS -> "Config name or search..."
            Tab.THEMES -> if (themeSubTab == ThemeSubTab.CUSTOM) "Search custom themes..." else "Search themes..."
            Tab.LAYOUT -> "Search layouts..."
            Tab.OTHER -> "Search..."
            else -> "Search blocks and textures..."
        }
        searchField.setPlaceholder(Text.literal(placeholder))
        val showThemeTabs = tab == Tab.THEMES
        themeSubTabButtons.forEach {
            it.visible = showThemeTabs
            it.active = showThemeTabs
        }
        val showOtherTabs = tab == Tab.OTHER
        otherSubTabButtons.forEach {
            it.visible = showOtherTabs
            it.active = showOtherTabs
        }
    }

    private fun recalcMetrics() {
        val desiredScale = FmeManager.getGuiScale()
        val panelBaseWidth = layoutPanelWidth()
        val panelBaseHeight = layoutPanelHeight()
        val fitScaleW = (width - 64f) / panelBaseWidth
        val fitScaleH = (height - 64f) / panelBaseHeight
        val fitScale = minOf(fitScaleW, fitScaleH)
        uiScale = minOf(desiredScale, fitScale, 1.0f).coerceAtLeast(0.5f)
        val scale = uiScale
        panelWidth = (panelBaseWidth * scale).roundToInt()
        panelHeight = (panelBaseHeight * scale).roundToInt()
        cellWidth = (layoutCellWidth() * scale).roundToInt()
        cellHeight = (layoutCellHeight() * scale).roundToInt()
        colGap = max(2, (BASE_GAP * scale).roundToInt() / 2)
        rowGap = max(2, (BASE_GAP * scale).roundToInt() / 2)
        sidebarWidth = (layoutSidebarWidth() * scale).roundToInt()

        panelX = max(8, (width - panelWidth) / 2)
        panelY = max(8, (height - panelHeight) / 2)
    }

    private fun layoutPanelWidth(): Int = when (FmeManager.getLayoutPreset()) {
        FmeManager.LayoutPreset.BLOOM -> 684
        FmeManager.LayoutPreset.TOPAZ -> 704
        FmeManager.LayoutPreset.OBSIDIAN -> 620
        FmeManager.LayoutPreset.SLATE -> 648
        FmeManager.LayoutPreset.ALPINE -> 748
    }

    private fun layoutPanelHeight(): Int = when (FmeManager.getLayoutPreset()) {
        FmeManager.LayoutPreset.BLOOM -> 392
        FmeManager.LayoutPreset.TOPAZ -> 376
        FmeManager.LayoutPreset.OBSIDIAN -> 348
        FmeManager.LayoutPreset.SLATE -> 332
        FmeManager.LayoutPreset.ALPINE -> 430
    }

    private fun layoutCellWidth(): Int = when (FmeManager.getLayoutPreset()) {
        FmeManager.LayoutPreset.BLOOM -> 30
        FmeManager.LayoutPreset.TOPAZ -> 32
        FmeManager.LayoutPreset.OBSIDIAN -> 28
        FmeManager.LayoutPreset.SLATE -> 34
        FmeManager.LayoutPreset.ALPINE -> 42
    }

    private fun layoutCellHeight(): Int = layoutCellWidth()

    private fun layoutSidebarWidth(): Int = when (FmeManager.getLayoutPreset()) {
        FmeManager.LayoutPreset.BLOOM -> 126
        FmeManager.LayoutPreset.TOPAZ -> 132
        FmeManager.LayoutPreset.OBSIDIAN -> 118
        FmeManager.LayoutPreset.SLATE -> 120
        FmeManager.LayoutPreset.ALPINE -> 162
    }

    private fun navPlacement(): NavPlacement = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM, FmeManager.LayoutPreset.ALPINE -> NavPlacement.LEFT
        FmeManager.LayoutPreset.TOPAZ, FmeManager.LayoutPreset.OBSIDIAN, FmeManager.LayoutPreset.SLATE -> NavPlacement.TOP
    }

    private fun visibleEntries(): List<Block> {
        val query = searchField.text.trim().lowercase(Locale.ROOT)
        if (tab == Tab.ALL) {
            if (query.isEmpty()) {
                return allBlocks
            }
            return allBlocks.filter { Registries.BLOCK.getId(it).toString().lowercase(Locale.ROOT).contains(query) }
        }
        return allBlocks.filter {
            FmeManager.isFavorite(it) && (query.isEmpty()
                || Registries.BLOCK.getId(it).toString().lowercase(Locale.ROOT).contains(query))
        }
    }

    private fun visibleTextureEntries(): List<Path> {
        val query = searchField.text.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return textureFiles
        }
        return textureFiles.filter { it.fileName.toString().lowercase(Locale.ROOT).contains(query) }
    }

    private fun visibleItemEntries(): List<ItemTargetEntry> {
        val query = searchField.text.trim().lowercase(Locale.ROOT)
        val entries = mutableListOf<ItemTargetEntry>()
        allItems.forEach { item ->
            val id = Registries.ITEM.getId(item).toString()
            val title = item.name.string
            if (query.isEmpty() || id.lowercase(Locale.ROOT).contains(query) || title.lowercase(Locale.ROOT).contains(query)) {
                entries += ItemTargetEntry(title, FmeManager.ItemAppearanceTargetType.ITEM, item = item)
            }
        }
        allBlocks.forEach { block ->
            val item = block.asItem()
            if (item == Items.AIR) {
                return@forEach
            }
            val id = Registries.BLOCK.getId(block).toString()
            val title = block.name.string
            if (query.isEmpty() || id.lowercase(Locale.ROOT).contains(query) || title.lowercase(Locale.ROOT).contains(query)) {
                entries += ItemTargetEntry(title, FmeManager.ItemAppearanceTargetType.BLOCK, block = block)
            }
        }
        textureFiles.forEach { path ->
            val name = path.fileName.toString()
            if (query.isEmpty() || name.lowercase(Locale.ROOT).contains(query)) {
                entries += ItemTargetEntry(name, FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE, texture = path)
            }
        }
        return entries
    }

    private fun visibleConfigEntries(): List<ConfigEntry> {
        val raw = searchField.text.trim()
        val query = raw.lowercase(Locale.ROOT)
        val entries = mutableListOf<ConfigEntry>()
        entries += ConfigEntry(
            "Load default",
            ConfigEntryKind.LOAD_DEFAULT,
            detail = "Load the base hat-fme.json"
        )
        entries += ConfigEntry(
            "Save current",
            ConfigEntryKind.SAVE_CURRENT,
            detail = "Overwrite ${FmeManager.getCurrentConfigName()}"
        )
        if (raw.isNotBlank()) {
            entries += ConfigEntry(
                "Save as \"$raw\"",
                ConfigEntryKind.SAVE_AS,
                configName = raw,
                detail = "Save to a named config"
            )
            entries += ConfigEntry(
                "New config \"$raw\"",
                ConfigEntryKind.NEW_CONFIG,
                configName = raw,
                detail = "Create and switch to a new config"
            )
        }
        val storedConfigs = if (query.isEmpty()) {
            configNames
        } else {
            configNames.filter { it.lowercase(Locale.ROOT).contains(query) }
        }
        storedConfigs.forEach { name ->
            entries += ConfigEntry(
                name,
                ConfigEntryKind.STORED,
                configName = name,
                detail = if (name.equals(FmeManager.getCurrentConfigName(), ignoreCase = true)) "Current config" else "Left click to load, right click to save"
            )
        }
        return entries
    }

    private fun visibleThemeEntries(): List<String> {
        val query = searchField.text.trim().lowercase(Locale.ROOT)
        val source = if (themeSubTab == ThemeSubTab.CUSTOM) customThemeNames else themeNames
        if (query.isEmpty()) {
            return source
        }
        return source.filter { it.lowercase(Locale.ROOT).contains(query) }
    }

    private fun visibleLayoutEntries(): List<LayoutEntry> {
        if (layoutEntries.isEmpty()) {
            refreshLayouts()
        }
        val query = searchField.text.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return layoutEntries
        }
        return layoutEntries.filter {
            it.title.lowercase(Locale.ROOT).contains(query) || it.description.lowercase(Locale.ROOT).contains(query)
        }
    }

    private fun visibleOtherEntries(): List<OtherEntry> {
        if (otherEntries.isEmpty()) {
            refreshOther()
        }
        val query = searchField.text.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return otherEntries
        }
        return otherEntries.filter { it.searchLabel.lowercase(Locale.ROOT).contains(query) }
    }

    private fun itemTargetTooltip(entry: ItemTargetEntry): String {
        return when (entry.kind) {
            FmeManager.ItemAppearanceTargetType.BLOCK ->
                "${entry.title} (${Registries.BLOCK.getId(entry.block!!)})"
            FmeManager.ItemAppearanceTargetType.ITEM ->
                "${entry.title} (${Registries.ITEM.getId(entry.item!!)})"
            FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE ->
                "Custom texture ${entry.texture?.fileName}"
        }
    }

    private fun entriesCount(): Int {
        if (customThemeEditing) {
            return 0
        }
        return when (tab) {
            Tab.CUSTOM -> visibleTextureEntries().size
            Tab.ITEM -> visibleItemEntries().size
            Tab.OTHER -> visibleOtherEntries().size
            Tab.CONFIGS -> visibleConfigEntries().size
            Tab.THEMES -> visibleThemeEntries().size
            Tab.LAYOUT -> visibleLayoutEntries().size
            else -> visibleEntries().size
        }
    }

    private fun refreshTextures() {
        textureFiles.clear()
        customTextureCache.clear()
        customTextureFailed.clear()
        textureFiles.addAll(HatTextureManager.listTextures())
    }

    private fun refreshConfigs() {
        configNames.clear()
        configNames.addAll(FmeManager.listConfigNames())
    }

    private fun refreshThemes() {
        themeNames.clear()
        themeNames.addAll(listOf("White", "Black & White", "Blue", "Purple", "Red", "Moonwalker", "Violet", "Femboy", "Argon", "Moss",
            "Hazel", "Pastel Mint", "Pastel Peach", "Pastel Lavender", "Pastel Sky", "Pastel Rose",
            "Pastel Butter", "Pastel Aqua", "Blossom"))
    }

    private fun refreshLayouts() {
        layoutEntries.clear()
        layoutEntries.add(LayoutEntry("Default", "Original FME layout and styling.", FmeManager.LayoutPreset.BLOOM))
        layoutEntries.add(LayoutEntry("Topaz", "Luxury dashboard cards with warm gold accents.", FmeManager.LayoutPreset.TOPAZ))
        layoutEntries.add(LayoutEntry("Obsidian", "Sharp monochrome segmented client-style interface.", FmeManager.LayoutPreset.OBSIDIAN))
        layoutEntries.add(LayoutEntry("Slate", "Soft dark settings board with muted glass cards.", FmeManager.LayoutPreset.SLATE))
        layoutEntries.add(LayoutEntry("Alpine", "Rounded app layout with a larger navigation rail.", FmeManager.LayoutPreset.ALPINE))
    }

    private fun refreshCustomThemes() {
        customThemeNames.clear()
        customThemeNames.add(NEW_CUSTOM_THEME_LABEL)
        customThemeNames.addAll(FmeManager.listCustomThemeNames())
    }

    fun refreshCustomThemesView() {
        if (tab == Tab.THEMES && themeSubTab == ThemeSubTab.CUSTOM) {
            refreshCustomThemes()
            setPage(0, 1, false)
            applyGridLayoutForTab()
            applyThemeSubTabLayout()
            updateTabState()
        }
    }

    private fun enterCustomThemeEditor(name: String?) {
        customThemeEditing = true
        customThemeDraft = if (name == null) {
            FmeManager.snapshotCurrentTheme("New Theme")
        } else {
            FmeManager.loadCustomTheme(name) ?: FmeManager.snapshotCurrentTheme(name)
        }
        clearAndInit()
    }

    private fun exitCustomThemeEditor() {
        customThemeEditing = false
        customThemeDraft = null
        clearAndInit()
    }

    private fun initCustomThemeEditorWidgets() {
        val theme = customThemeDraft ?: return
        val rowGap = max(4, (4 * uiScale).roundToInt())
        val colGap = max(6, (6 * uiScale).roundToInt())
        val fieldHeight = max(BASE_FIELD_HEIGHT.toFloat(), (textRenderer.fontHeight + 4) * uiScale).roundToInt()
        val buttonHeight = (BASE_BUTTON_HEIGHT * uiScale).roundToInt()
        val editorX = contentX
        val editorWidth = gridAreaWidth
        val halfWidth = max(90, (editorWidth - colGap) / 2)
        var y = gridY

        val nameField = TextFieldWidget(
            textRenderer,
            editorX + searchTextOffsetX,
            y + max(1, (fieldHeight - textRenderer.fontHeight) / 2),
            editorWidth,
            fieldHeight,
            Text.literal("Theme Name")
        )
        nameField.setText(theme.name ?: "New Theme")
        nameField.setChangedListener { theme.name = it.trim() }
        nameField.setDrawsBackground(true)
        addDrawableChild(nameField)
        customThemeNameField = nameField
        customThemeWidgets.add(nameField)
        y += fieldHeight + rowGap

        fun addColorButton(
            label: String,
            x: Int,
            y: Int,
            getter: () -> Int,
            setter: (Int) -> Unit
        ) {
            val button = ButtonWidget.builder(Text.literal("$label: ${formatColor(getter())}")) { btn ->
                client?.setScreen(ThemeColorPickerScreen(this, label, getter()) { value ->
                    setter(value)
                    btn.message = Text.literal("$label: ${formatColor(value)}")
                })
            }.dimensions(x, y, halfWidth, buttonHeight).build()
            addDrawableChild(button)
            customThemeWidgets.add(button)
        }

        fun addToggleButton(
            label: String,
            x: Int,
            y: Int,
            getter: () -> Boolean,
            setter: (Boolean) -> Unit
        ) {
            val button = ButtonWidget.builder(Text.literal("$label: ${if (getter()) "On" else "Off"}")) { btn ->
                val next = !getter()
                setter(next)
                btn.message = Text.literal("$label: ${if (next) "On" else "Off"}")
            }.dimensions(x, y, halfWidth, buttonHeight).build()
            addDrawableChild(button)
            customThemeWidgets.add(button)
        }

        fun addAnimButton(
            label: String,
            x: Int,
            y: Int,
            getter: () -> FmeManager.ThemeAnimation?,
            setter: (FmeManager.ThemeAnimation) -> Unit
        ) {
            val current = getter() ?: FmeManager.ThemeAnimation.PULSE
            val button = ButtonWidget.builder(Text.literal("$label: ${current.name}")) { btn ->
                val next = nextAnimation(getter() ?: FmeManager.ThemeAnimation.PULSE)
                setter(next)
                btn.message = Text.literal("$label: ${next.name}")
            }.dimensions(x, y, halfWidth, buttonHeight).build()
            addDrawableChild(button)
            customThemeWidgets.add(button)
        }

        addColorButton("Panel", editorX, y, { theme.panelColor }) { theme.panelColor = it }
        addColorButton("Border", editorX + halfWidth + colGap, y, { theme.borderColor }) { theme.borderColor = it }
        y += buttonHeight + rowGap
        addColorButton("Text", editorX, y, { theme.textColor }) { theme.textColor = it }
        addColorButton("Accent", editorX + halfWidth + colGap, y, { theme.accentTextColor }) { theme.accentTextColor = it }
        y += buttonHeight + rowGap
        addColorButton("Selection", editorX, y, { theme.selectionColor }) { theme.selectionColor = it }
        addColorButton("Gradient A", editorX + halfWidth + colGap, y, { theme.gradientStart }) { theme.gradientStart = it }
        y += buttonHeight + rowGap
        addColorButton("Gradient B", editorX, y, { theme.gradientEnd }) { theme.gradientEnd = it }
        addToggleButton("Flat Theme", editorX + halfWidth + colGap, y, { theme.flatTheme }) { theme.flatTheme = it }
        y += buttonHeight + rowGap

        val panelSlider = ThemeValueSlider(
            editorX,
            y,
            halfWidth,
            buttonHeight,
            "Panel Radius",
            theme.panelRadius,
            0f,
            24f
        ) { value -> theme.panelRadius = value }
        addDrawableChild(panelSlider)
        customThemeWidgets.add(panelSlider)
        val elementSlider = ThemeValueSlider(
            editorX + halfWidth + colGap,
            y,
            halfWidth,
            buttonHeight,
            "Element Radius",
            theme.elementRadius,
            0f,
            18f
        ) { value -> theme.elementRadius = value }
        addDrawableChild(elementSlider)
        customThemeWidgets.add(elementSlider)
        y += buttonHeight + rowGap

        addAnimButton("Tab Anim", editorX, y, { theme.tabAnimation }) { theme.tabAnimation = it }
        addAnimButton("Select Anim", editorX + halfWidth + colGap, y, { theme.selectionAnimation }) { theme.selectionAnimation = it }
        y += buttonHeight + rowGap + max(2, rowGap / 2)

        val saveButton = ButtonWidget.builder(Text.literal("Save & Apply")) {
            if (!theme.name.isNullOrBlank()) {
                FmeManager.saveCustomTheme(theme)
                FmeManager.applyCustomTheme(theme)
                refreshCustomThemes()
                exitCustomThemeEditor()
            }
        }.dimensions(editorX, y, editorWidth, buttonHeight).build()
        addDrawableChild(saveButton)
        customThemeWidgets.add(saveButton)
        y += buttonHeight + rowGap

        val backButton = ButtonWidget.builder(Text.literal("Back")) {
            exitCustomThemeEditor()
        }.dimensions(editorX, y, editorWidth, buttonHeight).build()
        addDrawableChild(backButton)
        customThemeWidgets.add(backButton)
    }

    private fun formatColor(color: Int): String {
        return String.format("#%08X", color)
    }

    private fun nextAnimation(current: FmeManager.ThemeAnimation): FmeManager.ThemeAnimation {
        val values = FmeManager.ThemeAnimation.values()
        val idx = values.indexOf(current).coerceAtLeast(0)
        return values[(idx + 1) % values.size]
    }

    private fun refreshOther() {
        otherEntries.clear()
        if (otherSubTab == OtherSubTab.VISUAL) {
            otherEntries.add(
                OtherEntry(
                    title = "Custom Item Pose",
                    value = "Open",
                    tooltip = "Open sliders for first-person and third-person custom item transform settings.",
                    onLeftClick = {
                        client?.setScreen(ItemTransformEditorScreen(this))
                    },
                    onRightClick = {
                        FmeManager.resetItemTransformSettings()
                        refreshOther()
                    }
                )
            )
            otherEntries.add(
                OtherEntry(
                    title = "Pose Reset",
                    value = "Default",
                    tooltip = "Reset all custom item transform values back to the default pose.",
                    onLeftClick = {
                        FmeManager.resetItemTransformSettings()
                        refreshOther()
                    }
                )
            )
            return
        }
        val hudEnabled = GifHudManager.isEnabled()
        val gifName = GifHudManager.getSelectedFileName()?.takeIf { it.isNotBlank() } ?: "none"

        otherEntries.add(
            OtherEntry(
                title = "GIF HUD",
                value = if (hudEnabled) "ON" else "OFF",
                tooltip = "Toggle GIF HUD overlay. Right click to open editor.",
                onLeftClick = {
                    GifHudManager.toggleEnabled()
                    refreshOther()
                },
                onRightClick = {
                    client?.setScreen(GifHudEditorScreen(this))
                }
            )
        )
        otherEntries.add(
            OtherEntry(
                title = "GIF File",
                value = gifName,
                tooltip = "Left click to cycle GIFs. Right click to clear. Folder: ${GifHudManager.getGifDir()}",
                onLeftClick = {
                    GifHudManager.selectNextGif()
                    refreshOther()
                },
                onRightClick = {
                    GifHudManager.clearGif()
                    refreshOther()
                }
            )
        )
        otherEntries.add(
            OtherEntry(
                title = "Edit",
                value = "Open",
                tooltip = "Open the GIF HUD editor.",
                onLeftClick = {
                    client?.setScreen(GifHudEditorScreen(this))
                }
            )
        )
    }



    private fun themeFromLabel(label: String): FmeManager.Theme? {
        val normalized = label.trim().lowercase(Locale.ROOT).replace("&", "").replace("  ", " ")
        return when (normalized) {
            "white" -> FmeManager.Theme.WHITE
            "black white" -> FmeManager.Theme.BLACK_WHITE
            "blue" -> FmeManager.Theme.BLUE
            "purple" -> FmeManager.Theme.PURPLE
            "red" -> FmeManager.Theme.RED
            "moonwalker" -> FmeManager.Theme.MOONWALKER
            "violet" -> FmeManager.Theme.VIOLET
            "femboy" -> FmeManager.Theme.FEMBOY
            "argon" -> FmeManager.Theme.ARGON
            "moss" -> FmeManager.Theme.MOSS
            "hazel" -> FmeManager.Theme.HAZEL
            "pastel mint" -> FmeManager.Theme.PASTEL_MINT
            "pastel peach" -> FmeManager.Theme.PASTEL_PEACH
            "pastel lavender" -> FmeManager.Theme.PASTEL_LAVENDER
            "pastel sky" -> FmeManager.Theme.PASTEL_SKY
            "pastel rose" -> FmeManager.Theme.PASTEL_ROSE
            "pastel butter" -> FmeManager.Theme.PASTEL_BUTTER
            "pastel aqua" -> FmeManager.Theme.PASTEL_AQUA
            "blossom" -> FmeManager.Theme.BLOSSOM
            else -> null
        }
    }

    private fun currentThemeLabel(): String {
        if (FmeManager.isCustomThemeActive()) {
            return ""
        }
        return when (FmeManager.getTheme()) {
            FmeManager.Theme.WHITE -> "White"
            FmeManager.Theme.BLACK_WHITE -> "Black & White"
            FmeManager.Theme.BLUE -> "Blue"
            FmeManager.Theme.PURPLE -> "Purple"
            FmeManager.Theme.RED -> "Red"
            FmeManager.Theme.MOONWALKER -> "Moonwalker"
            FmeManager.Theme.VIOLET -> "Violet"
            FmeManager.Theme.FEMBOY -> "Femboy"
            FmeManager.Theme.ARGON -> "Argon"
            FmeManager.Theme.MOSS -> "Moss"
            FmeManager.Theme.HAZEL -> "Hazel"
            FmeManager.Theme.PASTEL_MINT -> "Pastel Mint"
            FmeManager.Theme.PASTEL_PEACH -> "Pastel Peach"
            FmeManager.Theme.PASTEL_LAVENDER -> "Pastel Lavender"
            FmeManager.Theme.PASTEL_SKY -> "Pastel Sky"
            FmeManager.Theme.PASTEL_ROSE -> "Pastel Rose"
            FmeManager.Theme.PASTEL_BUTTER -> "Pastel Butter"
            FmeManager.Theme.PASTEL_AQUA -> "Pastel Aqua"
            FmeManager.Theme.BLOSSOM -> "Blossom"
        }
    }

    private fun setPage(target: Int, maxPages: Int, animate: Boolean) {
        val shouldAnimate = animate && tab == Tab.CONFIGS
        val clamped = MathHelper.clamp(target, 0, maxPages - 1)
        if (!shouldAnimate || entriesPerPage <= 0 || kotlin.math.abs(clamped - page) > 1) {
            page = clamped
            animFromPage = clamped.toFloat()
            animToPage = clamped
            animating = false
            animStartMs = 0L
            return
        }
        if (clamped == page && !animating) {
            return
        }
        val now = System.currentTimeMillis()
        if (animating) {
            animFromPage = currentAnimatedPage(now, maxPages)
        } else {
            animFromPage = page.toFloat()
        }
        animToPage = clamped
        animStartMs = now
        animating = true
        page = clamped
    }

    private fun currentAnimatedPage(now: Long, maxPages: Int): Float {
        val anim = computePageAnim(now, maxPages) ?: return page.toFloat()
        return if (anim.direction > 0) {
            anim.basePage + anim.progress
        } else {
            (anim.basePage + 1) - anim.progress
        }
    }

    private fun computePageAnim(now: Long, maxPages: Int): PageAnim? {
        if (!animating) {
            return null
        }
        val delta = animToPage.toFloat() - animFromPage
        if (kotlin.math.abs(delta) <= 0.001f) {
            animating = false
            animFromPage = animToPage.toFloat()
            return null
        }
        val direction = if (delta > 0f) 1 else -1
        val t = ((now - animStartMs).toFloat() / PAGE_ANIM_MS).coerceIn(0f, 1f)
        val eased = easeOutCubic(t)
        val basePage = MathHelper.clamp(kotlin.math.min(animFromPage, animToPage.toFloat()).toInt(), 0, maxPages - 1)
        val nextPage = MathHelper.clamp(basePage + 1, 0, maxPages - 1)
        if (basePage == nextPage) {
            animating = false
            animFromPage = animToPage.toFloat()
            return null
        }
        val startOffset = (animFromPage - basePage).coerceIn(0f, 1f)
        val startProgress = if (direction > 0) startOffset else 1f - startOffset
        val progress = startProgress + (1f - startProgress) * eased
        if (t >= 1f) {
            animating = false
            animFromPage = animToPage.toFloat()
        }
        return PageAnim(basePage, nextPage, progress.coerceIn(0f, 1f), direction)
    }

    private data class PageAnim(
        val basePage: Int,
        val nextPage: Int,
        val progress: Float,
        val direction: Int
    )

    private fun applyGridLayoutForTab() {
        if (tab == Tab.CONFIGS || tab == Tab.THEMES) {
            cellHeight = (BASE_CELL_HEIGHT * uiScale).roundToInt()
            rowGap = max(2, (BASE_GAP * uiScale).roundToInt() / 2)
            gridColumns = 1
            cellWidth = max(60, gridAreaWidth)
            gridX = contentX
            return
        }
        if (tab == Tab.OTHER || tab == Tab.LAYOUT) {
            gridColumns = 1
            cellWidth = max(60, gridAreaWidth)
            val multiplier = if (tab == Tab.LAYOUT) 1.65f else 1.4f
            cellHeight = max(cellHeight, (BASE_CELL_HEIGHT * uiScale * multiplier).roundToInt())
            rowGap = max(rowGap, (BASE_GAP * uiScale).roundToInt())
            gridX = contentX
            return
        }
        cellHeight = (BASE_CELL_HEIGHT * uiScale).roundToInt()
        rowGap = max(2, (BASE_GAP * uiScale).roundToInt() / 2)
        cellWidth = baseCellWidth
        gridColumns = baseGridColumns
        val actualGridWidth = gridColumns * cellWidth + max(0, gridColumns - 1) * colGap
        gridX = contentX + max(0, (gridAreaWidth - actualGridWidth) / 2)
    }

    private fun applyThemeSubTabLayout() {
        gridY = if (tab == Tab.THEMES || tab == Tab.OTHER) {
            baseGridY + themeSubTabHeight + themeSubTabGap
        } else {
            baseGridY
        }
        val layoutGap = (BASE_GAP * uiScale).roundToInt()
        val availableGridHeight = pagerY - layoutGap - gridY
        gridRows = max(1, availableGridHeight / (cellHeight + rowGap))
        entriesPerPage = max(1, gridColumns * gridRows)
    }

    private fun getCustomTextureId(path: Path): Identifier? {
        customTextureCache[path]?.let { return it }
        if (customTextureFailed.contains(path)) {
            return null
        }
        if (textureLoadBudget <= 0) {
            return null
        }
        textureLoadBudget--
        val id = HatTextureManager.getOrLoadTexture(path)
        if (id != null) {
            customTextureCache[path] = id
            return id
        }
        customTextureFailed.add(path)
        return null
    }

    private fun trimToWidth(input: String, maxWidth: Int): String {
        if (textRenderer.getWidth(input) <= maxWidth) {
            return input
        }
        val ellipsis = "..."
        val ellipsisWidth = textRenderer.getWidth(ellipsis)
        val trimmed = textRenderer.trimToWidth(input, max(0, maxWidth - ellipsisWidth))
        return trimmed + ellipsis
    }

    private fun playTabClickSound() {
        val client = MinecraftClient.getInstance()
        client.soundManager.play(PositionedSoundInstance.master(SoundEvent.of(TAB_CLICK_SOUND_ID), 1.0f))
    }

    private fun playOpenSound() {
        val client = MinecraftClient.getInstance()
        client.soundManager.play(PositionedSoundInstance.master(SoundEvent.of(OPEN_SOUND_ID), 1.0f))
    }

    private fun playCloseSound() {
        val client = MinecraftClient.getInstance()
        client.soundManager.play(PositionedSoundInstance.master(SoundEvent.of(CLOSE_SOUND_ID), 1.0f))
    }

    private fun isInRect(mouseX: Double, mouseY: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }

    private fun panelColor(): Int = FmeManager.getGuiPanelColor()

    private fun currentLayout(): FmeManager.LayoutPreset = FmeManager.getLayoutPreset()

    private fun panelRadius(): Int {
        return max(2, (FmeManager.getPanelCornerRadius() * uiScale).roundToInt())
    }

    private fun elementRadius(): Int {
        return max(2, (FmeManager.getElementCornerRadius() * uiScale).roundToInt())
    }

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.fill(x, y, x + w, y + 1, color)
        context.fill(x, y + h - 1, x + w, y + h, color)
        context.fill(x, y, x + 1, y + h, color)
        context.fill(x + w - 1, y, x + w, y + h, color)
    }

    private fun drawRoundedRect(context: DrawContext, x: Int, y: Int, w: Int, h: Int, fill: Int, border: Int, radius: Int) {
        PIPNVG.drawNVG(context, 0, 0, width, height) {
            NVG.rect(x, y, w, h, toAwtColor(fill), radius.toFloat())
            NVG.hollowRect(x, y, w, h, 1f, toAwtColor(border), radius.toFloat())
        }
    }

    private fun styledElementRadius(base: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> max(6, base)
        FmeManager.LayoutPreset.TOPAZ -> max(5, base - 2)
        FmeManager.LayoutPreset.OBSIDIAN -> 1
        FmeManager.LayoutPreset.SLATE -> max(8, base + 2)
        FmeManager.LayoutPreset.ALPINE -> max(12, base + 6)
    }

    private fun styledPanelRadius(base: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> max(12, base + 2)
        FmeManager.LayoutPreset.TOPAZ -> max(10, base)
        FmeManager.LayoutPreset.OBSIDIAN -> 1
        FmeManager.LayoutPreset.SLATE -> max(14, base + 4)
        FmeManager.LayoutPreset.ALPINE -> max(20, base + 8)
    }

    private fun stylePanelColor(color: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> color
        FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(color, 0xFF090909.toInt(), 0.62f), 235)
        FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(mixColor(color, 0xFF000000.toInt(), 0.92f), 245)
        FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(color, 0xFF111827.toInt(), 0.74f), 232)
        FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(color, 0xFF141738.toInt(), 0.58f), 236)
    }

    private fun styleBorderColor(color: Int, accent: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> mixColor(color, accent, 0.18f)
        FmeManager.LayoutPreset.TOPAZ -> mixColor(accent, 0xFFE8C38B.toInt(), 0.45f)
        FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(mixColor(color, 0xFFFFFFFF.toInt(), 0.15f), 135)
        FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(color, accent, 0.22f), 110)
        FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(color, accent, 0.28f), 120)
    }

    private fun styleSidebarColor(color: Int, accent: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> withAlpha(mixColor(color, 0xFFFFFFFF.toInt(), 0.08f), 110)
        FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(color, 0xFF000000.toInt(), 0.22f), 180)
        FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(0xFF000000.toInt(), 180)
        FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(color, 0xFF0B1020.toInt(), 0.25f), 170)
        FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(color, accent, 0.10f), 165)
    }

    private fun styleInnerPanelColor(color: Int, accent: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> withAlpha(mixColor(color, 0xFFFFFFFF.toInt(), 0.06f), 120)
        FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(color, accent, 0.08f), 70)
        FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(0x00000000, 0)
        FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(color, 0xFFFFFFFF.toInt(), 0.03f), 90)
        FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(color, accent, 0.06f), 88)
    }

    private fun styleCellBackground(panelColor: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> withAlpha(mixColor(panelColor, 0xFFFFFFFF.toInt(), 0.14f), 245)
        FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.16f), 232)
        FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.28f), 245)
        FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(panelColor, 0xFFFFFFFF.toInt(), 0.04f), 225)
        FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(panelColor, 0xFFFFFFFF.toInt(), 0.07f), 228)
    }

    private fun styleCellBorder(textColor: Int, panelColor: Int, accent: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> withAlpha(mixColor(textColor, accent, 0.25f), 138)
        FmeManager.LayoutPreset.TOPAZ -> withAlpha(mixColor(accent, 0xFFE0B57A.toInt(), 0.40f), 145)
        FmeManager.LayoutPreset.OBSIDIAN -> withAlpha(mixColor(textColor, 0xFFFFFFFF.toInt(), 0.10f), 92)
        FmeManager.LayoutPreset.SLATE -> withAlpha(mixColor(textColor, panelColor, 0.55f), 95)
        FmeManager.LayoutPreset.ALPINE -> withAlpha(mixColor(accent, textColor, 0.18f), 110)
    }

    private fun styleSelectedCell(selectionColor: Int, panelColor: Int, accent: Int): Int = when (currentLayout()) {
        FmeManager.LayoutPreset.BLOOM -> mixColor(selectionColor, 0xFFFFFFFF.toInt(), 0.22f)
        FmeManager.LayoutPreset.TOPAZ -> mixColor(accent, panelColor, 0.24f)
        FmeManager.LayoutPreset.OBSIDIAN -> mixColor(selectionColor, 0xFFFFFFFF.toInt(), 0.05f)
        FmeManager.LayoutPreset.SLATE -> mixColor(selectionColor, panelColor, 0.14f)
        FmeManager.LayoutPreset.ALPINE -> mixColor(selectionColor, accent, 0.20f)
    }

    private fun toAwtColor(color: Int, alphaOverride: Int? = null): Color {
        val a = alphaOverride ?: ((color ushr 24) and 0xFF)
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return Color(r, g, b, a.coerceIn(0, 255))
    }


    private fun mixColor(a: Int, b: Int, t: Float): Int {
        val aA = (a ushr 24) and 0xFF
        val aR = (a ushr 16) and 0xFF
        val aG = (a ushr 8) and 0xFF
        val aB = a and 0xFF
        val bA = (b ushr 24) and 0xFF
        val bR = (b ushr 16) and 0xFF
        val bG = (b ushr 8) and 0xFF
        val bB = b and 0xFF
        val oA = MathHelper.clamp((aA + (bA - aA) * t).roundToInt(), 0, 255)
        val oR = MathHelper.clamp((aR + (bR - aR) * t).roundToInt(), 0, 255)
        val oG = MathHelper.clamp((aG + (bG - aG) * t).roundToInt(), 0, 255)
        val oB = MathHelper.clamp((aB + (bB - aB) * t).roundToInt(), 0, 255)
        return (oA shl 24) or (oR shl 16) or (oG shl 8) or oB
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
    }

    private fun multiplyAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24) and 0xFF) * factor
        return withAlpha(color, alpha.roundToInt())
    }

    private fun easeOutCubic(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val inv = 1f - clamped
        return 1f - inv * inv * inv
    }

    private fun pulse01(periodMs: Float): Float {
        val now = System.currentTimeMillis()
        val t = (now % periodMs.toLong()).toFloat() / periodMs
        return (0.5f + 0.5f * sin((t * (Math.PI * 2)).toFloat())).coerceIn(0f, 1f)
    }

    private fun animationAmount(animation: FmeManager.ThemeAnimation, periodMs: Float): Float {
        val t = (System.currentTimeMillis() % periodMs.toLong()).toFloat() / periodMs
        val pulse = pulse01(periodMs)
        val triangle = if (t < 0.5f) t * 2f else (1f - t) * 2f
        return when (animation) {
            FmeManager.ThemeAnimation.NONE -> 0f
            FmeManager.ThemeAnimation.PULSE -> 0.15f * pulse
            FmeManager.ThemeAnimation.GLOW -> 0.25f * pulse
            FmeManager.ThemeAnimation.SLIDE -> 0.2f * triangle
            FmeManager.ThemeAnimation.BOUNCE -> 0.2f * (1f - (1f - pulse) * (1f - pulse))
            FmeManager.ThemeAnimation.FADE -> 0.12f * (0.5f + 0.5f * sin((t * (Math.PI * 2)).toFloat()))
        }
    }

    private inner class TabsWidget(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val tabHeight: Int,
        private val gapBetween: Int,
        private val tabs: List<Triple<String, Identifier, Tab>>,
        private val placement: NavPlacement
    ) {
        var endY: Int = y
            private set

        fun initButtons() {
            tabButtons.clear()
            if (placement == NavPlacement.LEFT) {
                var tabY = y
                tabs.forEach { (label, icon, target) ->
                    addTabButton(x, tabY, tabHeight, width, gapBetween, label, icon, target)
                    tabY += tabHeight + gapBetween
                }
                endY = tabY - gapBetween
            } else {
                val count = max(1, tabs.size)
                val tabWidth = max(48, (width - gapBetween * (count - 1)) / count)
                var tabX = x
                tabs.forEach { (label, icon, target) ->
                    addTabButton(tabX, y, tabHeight, tabWidth, gapBetween, label, icon, target)
                    tabX += tabWidth + gapBetween
                }
                endY = y + tabHeight
            }
        }
    }

    private inner class BlockGridWidget {
        fun handleGridClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (entriesPerPage <= 0) {
                return false
            }
            if (animating) {
                return false
            }
            val col = ((mouseX - gridX) / (cellWidth + colGap)).toInt()
            val row = ((mouseY - gridY) / (cellHeight + rowGap)).toInt()
            if (col < 0 || row < 0 || col >= gridColumns || row >= gridRows) {
                return false
            }
            val idx = page * entriesPerPage + row * gridColumns + col
            if (tab == Tab.CUSTOM) {
                val entries = visibleTextureEntries()
                if (idx >= entries.size) {
                    return false
                }
                val file = entries[idx]
                if (button == 0) {
                    FmeManager.selectCustomTexture(file)
                    HatTextureManager.selectTexture(file)
                    playTabClickSound()
                } else if (button == 1) {
                    FmeManager.toggleCustomTextureFavorite(file.fileName.toString())
                    playTabClickSound()
                }
                return true
            }
            if (tab == Tab.ITEM) {
                val entries = visibleItemEntries()
                if (idx >= entries.size) {
                    return false
                }
                val entry = entries[idx]
                if (button == 0) {
                    val applied = when (entry.kind) {
                        FmeManager.ItemAppearanceTargetType.BLOCK -> entry.block?.let { FmeManager.applyPendingItemAppearanceBlock(it) } == true
                        FmeManager.ItemAppearanceTargetType.ITEM -> entry.item?.let { FmeManager.applyPendingItemAppearanceItem(it) } == true
                        FmeManager.ItemAppearanceTargetType.CUSTOM_TEXTURE ->
                            entry.texture?.fileName?.toString()?.let { FmeManager.applyPendingItemAppearanceCustomTexture(it) } == true
                    }
                    if (applied) {
                        FmeManager.sendClientMessage("Saved item appearance for " + FmeManager.getPendingItemEditLabel())
                        close()
                    } else {
                        FmeManager.sendClientMessage("Run /fme custom item while holding the item you want to edit")
                    }
                    playTabClickSound()
                } else if (button == 1) {
                    if (FmeManager.clearPendingItemAppearance()) {
                        FmeManager.sendClientMessage("Cleared item appearance for " + FmeManager.getPendingItemEditLabel())
                        playTabClickSound()
                    }
                }
                return true
            }
            if (tab == Tab.CONFIGS) {
                val entries = visibleConfigEntries()
                if (idx >= entries.size) {
                    return false
                }
                val entry = entries[idx]
                val handled = when (entry.kind) {
                    ConfigEntryKind.LOAD_DEFAULT -> {
                        val loaded = FmeManager.loadDefaultConfig()
                        if (loaded) {
                            FmeManager.sendClientMessage("Loaded default FME config")
                            refreshConfigs()
                            true
                        } else {
                            FmeManager.sendClientMessage("No default FME config found")
                            false
                        }
                    }
                    ConfigEntryKind.SAVE_CURRENT -> {
                        val saved = FmeManager.saveCurrentConfig()
                        if (saved) {
                            FmeManager.sendClientMessage("Saved current FME config")
                            refreshConfigs()
                            true
                        } else {
                            FmeManager.sendClientMessage("No current FME config to save")
                            false
                        }
                    }
                    ConfigEntryKind.SAVE_AS -> {
                        val name = entry.configName
                        if (name.isNullOrBlank()) {
                            false
                        } else {
                            val saved = FmeManager.saveConfig(name)
                            if (saved) {
                                FmeManager.sendClientMessage("Saved FME config: $name")
                                refreshConfigs()
                                true
                            } else {
                                FmeManager.sendClientMessage("Failed to save config: $name")
                                false
                            }
                        }
                    }
                    ConfigEntryKind.NEW_CONFIG -> {
                        val name = entry.configName
                        if (name.isNullOrBlank()) {
                            false
                        } else {
                            val saved = FmeManager.addConfig(name)
                            if (saved) {
                                FmeManager.sendClientMessage("Added and loaded FME config: $name")
                                refreshConfigs()
                                true
                            } else {
                                FmeManager.sendClientMessage("Failed to add config: $name")
                                false
                            }
                        }
                    }
                    ConfigEntryKind.STORED -> {
                        val name = entry.configName
                        if (name.isNullOrBlank()) {
                            false
                        } else if (button == 1) {
                            val saved = FmeManager.saveConfig(name)
                            if (saved) {
                                FmeManager.sendClientMessage("Saved FME config: $name")
                                refreshConfigs()
                                true
                            } else {
                                FmeManager.sendClientMessage("Failed to save config: $name")
                                false
                            }
                        } else {
                            val loaded = FmeManager.loadConfig(name)
                            if (loaded) {
                                FmeManager.sendClientMessage("Loaded FME config: $name")
                                refreshConfigs()
                                true
                            } else {
                                FmeManager.sendClientMessage("Config not found: $name")
                                false
                            }
                        }
                    }
                }
                if (handled) {
                    playTabClickSound()
                }
                return true
            }
            if (tab == Tab.THEMES) {
                val entries = visibleThemeEntries()
                if (idx >= entries.size) {
                    return false
                }
                val entry = entries[idx]
                if (themeSubTab == ThemeSubTab.CUSTOM) {
                    if (button == 0) {
                        if (entry == NEW_CUSTOM_THEME_LABEL) {
                            enterCustomThemeEditor(null)
                        } else if (FmeManager.applyCustomTheme(entry)) {
                            playTabClickSound()
                        }
                    } else if (button == 1 && entry != NEW_CUSTOM_THEME_LABEL) {
                        enterCustomThemeEditor(entry)
                    }
                } else if (button == 0) {
                    val theme = themeFromLabel(entry)
                    if (theme != null) {
                        FmeManager.applyTheme(theme)
                        playTabClickSound()
                    }
                }
                return true
            }
            if (tab == Tab.OTHER) {
                val entries = visibleOtherEntries()
                if (idx >= entries.size) {
                    return false
                }
                val entry = entries[idx]
                if (button == 0) {
                    entry.onLeftClick.invoke()
                    playTabClickSound()
                } else if (button == 1) {
                    entry.onRightClick?.invoke()
                    playTabClickSound()
                }
                return true
            }
            if (tab == Tab.LAYOUT) {
                val entries = visibleLayoutEntries()
                if (idx >= entries.size) {
                    return false
                }
                val entry = entries[idx]
                if (button == 0) {
                    FmeManager.setLayoutPreset(entry.preset)
                    clearAndInit()
                    playTabClickSound()
                }
                return true
            }
            if (tab == Tab.ALL || tab == Tab.FAVORITES) {
                val entries = visibleEntries()
                if (idx >= entries.size) {
                    return false
                }
                val block = entries[idx]
                if (button == 0) {
                    FmeManager.setSelectedSource(block)
                    playTabClickSound()
                } else if (button == 1) {
                    FmeManager.toggleFavorite(block)
                    playTabClickSound()
                }
                return true
            }
            return false
        }
    }

    private inner class TabButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val label: String,
        private val icon: Identifier,
        private val onPress: () -> Unit
    ) : ClickableWidget(x, y, width, height, Text.literal(label)) {
        private var activeTab = false

        fun updateActive(current: Tab) {
            activeTab = when (label) {
                "ALL" -> current == Tab.ALL
                "FAVORITES" -> current == Tab.FAVORITES
                "CUSTOM" -> current == Tab.CUSTOM
                "ITEM" -> current == Tab.ITEM
                "OTHER" -> current == Tab.OTHER
                "CONFIGS" -> current == Tab.CONFIGS
                "THEMES" -> current == Tab.THEMES
                "LAYOUT" -> current == Tab.LAYOUT
                else -> false
            }
        }

        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            val panelColor = panelColor()
            val textColor = FmeManager.getGuiTextColor()
            val accentColor = FmeManager.getGuiAccentTextColor()
            val isBlackWhiteTheme = FmeManager.getTheme() == FmeManager.Theme.BLACK_WHITE
            val pulse = if (activeTab || isHovered) animationAmount(FmeManager.getTabAnimation(), 900f) else 0f
            val base = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> mixColor(stylePanelColor(panelColor), 0xFF000000.toInt(), 0.18f)
                FmeManager.LayoutPreset.OBSIDIAN -> 0xFF050505.toInt()
                FmeManager.LayoutPreset.SLATE -> mixColor(stylePanelColor(panelColor), 0xFF0D1020.toInt(), 0.22f)
                FmeManager.LayoutPreset.ALPINE -> mixColor(stylePanelColor(panelColor), 0xFF232A5E.toInt(), 0.18f)
                else -> if (isBlackWhiteTheme) 0xFFE6E6E6.toInt() else mixColor(panelColor, 0xFF000000.toInt(), 0.2f)
            }
            val active = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> mixColor(base, 0xFFE0B57A.toInt(), 0.18f + pulse)
                FmeManager.LayoutPreset.OBSIDIAN -> mixColor(0xFF050505.toInt(), 0xFFFFFFFF.toInt(), 0.08f + pulse * 0.2f)
                FmeManager.LayoutPreset.SLATE -> mixColor(base, accentColor, 0.14f + pulse)
                FmeManager.LayoutPreset.ALPINE -> mixColor(base, accentColor, 0.22f + pulse)
                else -> if (isBlackWhiteTheme) 0xFFFFFFFF.toInt() else mixColor(panelColor, accentColor, 0.2f + pulse)
            }
            val bg = if (activeTab || isHovered) active else base
            val border = if (isBlackWhiteTheme && currentLayout() != FmeManager.LayoutPreset.OBSIDIAN) 0xFF111111.toInt() else styleBorderColor(textColor, accentColor)
            val radius = styledElementRadius(elementRadius())
            if (currentLayout() == FmeManager.LayoutPreset.OBSIDIAN) {
                context.fill(x, y, x + width, y + height, bg)
                drawBorder(context, x, y, width, height, border)
            } else {
                drawRoundedRect(context, x, y, width, height, bg, border, radius)
            }

            val rawLabel = label.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            val labelText = trimToWidth(rawLabel, width - 12)
            val labelX = x + max(0, (width - textRenderer.getWidth(labelText)) / 2)
            val labelY = y + max(0, (height - textRenderer.fontHeight) / 2)
            val labelColor = if (isBlackWhiteTheme) 0xFF000000.toInt()
                else if (activeTab) textColor
                else mixColor(textColor, panelColor, 0.55f)
            context.drawText(textRenderer, Text.literal(labelText), labelX, labelY, labelColor, false)
        }

        override fun onClick(click: Click, doubleClick: Boolean) {
            if (click.button() != 0) {
                return
            }
            onPress.invoke()
        }

        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            appendDefaultNarrations(builder)
        }

        override fun playDownSound(soundManager: SoundManager) {
            // Use custom tab sound only.
        }
    }

    private inner class ThemeSubTabButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val label: String,
        private val onPress: () -> Unit
    ) : ClickableWidget(x, y, width, height, Text.literal(label)) {
        private var activeTab = false

        fun updateActive(current: ThemeSubTab) {
            activeTab = when (label) {
                "Themes" -> current == ThemeSubTab.THEMES
                "Custom" -> current == ThemeSubTab.CUSTOM
                else -> false
            }
        }

        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            if (!visible) {
                return
            }
            val panelColor = panelColor()
            val textColor = FmeManager.getGuiTextColor()
            val accentColor = FmeManager.getGuiAccentTextColor()
            val isBlackWhiteTheme = FmeManager.getTheme() == FmeManager.Theme.BLACK_WHITE
            val pulse = if (activeTab || isHovered) animationAmount(FmeManager.getTabAnimation(), 900f) else 0f
            val base = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> mixColor(stylePanelColor(panelColor), 0xFF000000.toInt(), 0.18f)
                FmeManager.LayoutPreset.OBSIDIAN -> 0xFF050505.toInt()
                FmeManager.LayoutPreset.SLATE -> mixColor(stylePanelColor(panelColor), 0xFF0D1020.toInt(), 0.22f)
                FmeManager.LayoutPreset.ALPINE -> mixColor(stylePanelColor(panelColor), 0xFF232A5E.toInt(), 0.18f)
                else -> if (isBlackWhiteTheme) 0xFFE6E6E6.toInt() else mixColor(panelColor, 0xFF000000.toInt(), 0.2f)
            }
            val active = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> mixColor(base, 0xFFE0B57A.toInt(), 0.18f + pulse)
                FmeManager.LayoutPreset.OBSIDIAN -> mixColor(0xFF050505.toInt(), 0xFFFFFFFF.toInt(), 0.08f + pulse * 0.2f)
                FmeManager.LayoutPreset.SLATE -> mixColor(base, accentColor, 0.14f + pulse)
                FmeManager.LayoutPreset.ALPINE -> mixColor(base, accentColor, 0.22f + pulse)
                else -> if (isBlackWhiteTheme) 0xFFFFFFFF.toInt() else mixColor(panelColor, accentColor, 0.2f + pulse)
            }
            val bg = if (activeTab || isHovered) active else base
            val border = if (isBlackWhiteTheme && currentLayout() != FmeManager.LayoutPreset.OBSIDIAN) 0xFF111111.toInt() else styleBorderColor(textColor, accentColor)
            val radius = styledElementRadius(elementRadius())
            if (currentLayout() == FmeManager.LayoutPreset.OBSIDIAN) {
                context.fill(x, y, x + width, y + height, bg)
                drawBorder(context, x, y, width, height, border)
            } else {
                drawRoundedRect(context, x, y, width, height, bg, border, radius)
            }

            val labelText = trimToWidth(label, width - 12)
            val labelX = x + max(0, (width - textRenderer.getWidth(labelText)) / 2)
            val labelY = y + max(0, (height - textRenderer.fontHeight) / 2)
            val labelColor = if (isBlackWhiteTheme) 0xFF000000.toInt()
                else if (activeTab) textColor
                else mixColor(textColor, panelColor, 0.55f)
            context.drawText(textRenderer, Text.literal(labelText), labelX, labelY, labelColor, false)
        }

        override fun onClick(click: Click, doubleClick: Boolean) {
            if (click.button() != 0) {
                return
            }
            onPress.invoke()
        }

        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            appendDefaultNarrations(builder)
        }

        override fun playDownSound(soundManager: SoundManager) {
            // Use custom tab sound only.
        }
    }

    private inner class OtherSubTabButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val label: String,
        private val onPress: () -> Unit
    ) : ClickableWidget(x, y, width, height, Text.literal(label)) {
        private var activeTab = false

        fun updateActive(current: OtherSubTab) {
            activeTab = when (label) {
                "GIF" -> current == OtherSubTab.GIF
                "Visual" -> current == OtherSubTab.VISUAL
                else -> false
            }
        }

        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            if (!visible) {
                return
            }
            val panelColor = panelColor()
            val textColor = FmeManager.getGuiTextColor()
            val accentColor = FmeManager.getGuiAccentTextColor()
            val pulse = if (activeTab || isHovered) animationAmount(FmeManager.getTabAnimation(), 900f) else 0f
            val base = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> mixColor(stylePanelColor(panelColor), 0xFF000000.toInt(), 0.18f)
                FmeManager.LayoutPreset.OBSIDIAN -> 0xFF050505.toInt()
                FmeManager.LayoutPreset.SLATE -> mixColor(stylePanelColor(panelColor), 0xFF0D1020.toInt(), 0.22f)
                FmeManager.LayoutPreset.ALPINE -> mixColor(stylePanelColor(panelColor), 0xFF232A5E.toInt(), 0.18f)
                FmeManager.LayoutPreset.BLOOM -> mixColor(panelColor, 0xFF000000.toInt(), 0.2f)
            }
            val active = when (currentLayout()) {
                FmeManager.LayoutPreset.TOPAZ -> mixColor(base, 0xFFE0B57A.toInt(), 0.18f + pulse)
                FmeManager.LayoutPreset.OBSIDIAN -> mixColor(0xFF050505.toInt(), 0xFFFFFFFF.toInt(), 0.08f + pulse * 0.2f)
                FmeManager.LayoutPreset.SLATE -> mixColor(base, accentColor, 0.14f + pulse)
                FmeManager.LayoutPreset.ALPINE -> mixColor(base, accentColor, 0.22f + pulse)
                FmeManager.LayoutPreset.BLOOM -> mixColor(panelColor, accentColor, 0.2f + pulse)
            }
            val bg = if (activeTab || isHovered) active else base
            val border = styleBorderColor(textColor, accentColor)
            val radius = styledElementRadius(elementRadius())
            if (currentLayout() == FmeManager.LayoutPreset.OBSIDIAN) {
                context.fill(x, y, x + width, y + height, bg)
                drawBorder(context, x, y, width, height, border)
            } else {
                drawRoundedRect(context, x, y, width, height, bg, border, radius)
            }
            val labelText = trimToWidth(label, width - 12)
            val labelX = x + max(0, (width - textRenderer.getWidth(labelText)) / 2)
            val labelY = y + max(0, (height - textRenderer.fontHeight) / 2)
            val labelColor = if (activeTab) textColor else mixColor(textColor, panelColor, 0.55f)
            context.drawText(textRenderer, Text.literal(labelText), labelX, labelY, labelColor, false)
        }

        override fun onClick(click: Click, doubleClick: Boolean) {
            if (click.button() != 0) {
                return
            }
            onPress.invoke()
        }

        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            appendDefaultNarrations(builder)
        }

        override fun playDownSound(soundManager: SoundManager) {
            // Use custom tab sound only.
        }
    }

    private inner class ThemeValueSlider(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        private val label: String,
        initial: Float,
        private val min: Float,
        private val max: Float,
        private val setter: (Float) -> Unit
    ) : SliderWidget(x, y, width, height, Text.empty(), normalizeSliderValue(initial, min, max)) {

        override fun updateMessage() {
            val value = (min + (max - min) * this.value.toFloat()).roundToInt()
            message = Text.literal("$label: $value")
        }

        override fun applyValue() {
            val value = (min + (max - min) * this.value.toFloat()).coerceIn(min, max)
            setter(value)
            updateMessage()
        }
    }

    private fun normalizeSliderValue(value: Float, min: Float, max: Float): Double {
        if (max <= min) {
            return 0.0
        }
        return ((value - min) / (max - min)).coerceIn(0f, 1f).toDouble()
    }

    private data class OtherEntry(
        val title: String,
        val value: String? = null,
        val valueColor: Int? = null,
        val colorPreview: Int? = null,
        val tooltip: String? = null,
        val onLeftClick: () -> Unit,
        val onRightClick: (() -> Unit)? = null
    ) {
        val searchLabel: String = buildString {
            append(title)
            if (!value.isNullOrBlank()) {
                append(' ')
                append(value)
            }
        }
    }

    private data class LayoutEntry(
        val title: String,
        val description: String,
        val preset: FmeManager.LayoutPreset
    )

}
