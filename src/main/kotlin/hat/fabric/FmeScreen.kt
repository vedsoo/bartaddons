package hat.fabric

import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundManager
import net.minecraft.client.input.KeyInput
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import org.lwjgl.glfw.GLFW
import java.nio.file.Path
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class FmeScreen(private val openGuiSettings: Boolean = false) : Screen(Text.literal("FME")) {
    companion object {
        private const val BASE_PANEL_WIDTH = 640
        private const val BASE_PANEL_HEIGHT = 360
        private const val BASE_CELL_WIDTH = 46
        private const val BASE_CELL_HEIGHT = 24
        private const val BASE_GAP = 6
        private const val BASE_TAB_HEIGHT = 28
        private const val BASE_FIELD_HEIGHT = 18
        private const val BASE_SIDEBAR_WIDTH = 110
        private const val BASE_BUTTON_HEIGHT = 20
        private const val BASE_PAGER_WIDTH = 70
        private const val BASE_PAGER_HEIGHT = 18
        private const val TAB_ICON_SIZE = 16

        private val TAB_CLICK_SOUND_ID = Identifier.of("hat", "ui.click")
        private val TAB_ICON_ALL = Identifier.of("hat", "textures/gui/tabs/all.png")
        private val TAB_ICON_FAVORITES = Identifier.of("hat", "textures/gui/tabs/favorites.png")
        private val TAB_ICON_CUSTOM = Identifier.of("hat", "textures/gui/tabs/custom.png")
        private val TAB_ICON_SETTINGS = Identifier.of("hat", "textures/gui/tabs/settings.png")
    }

    private enum class Tab {
        ALL,
        FAVORITES,
        CUSTOM,
        SETTINGS
    }

    private val allBlocks = mutableListOf<Block>()
    private val textureFiles = mutableListOf<Path>()
    private val customTextureCache = mutableMapOf<Path, Identifier>()
    private val customTextureFailed = mutableSetOf<Path>()
    private val settingsWidgets = mutableListOf<ClickableWidget>()
    private val tabButtons = mutableListOf<TabButton>()
    private var tab = if (openGuiSettings) Tab.SETTINGS else Tab.ALL
    private var page = 0
    private var textureLoadBudget = 0
    private lateinit var searchField: TextFieldWidget
    private lateinit var fmeToggleButton: MinimalButton
    private lateinit var editToggleButton: MinimalButton

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
    private var contentX = 0
    private var titleY = 0
    private var subtitleY = 0
    private var searchY = 0
    private var tabsY = 0
    private var gridX = 0
    private var gridY = 0
    private var gridColumns = 0
    private var gridRows = 0
    private var entriesPerPage = 0
    private var pagerY = 0
    private var settingsY = 0
    private var prevX = 0
    private var nextX = 0
    private var closeX = 0
    private var closeY = 0
    private var closeSize = 0
    private var uiScale = 1f
    private var openTimeMs = 0L

    override fun init() {
        openTimeMs = System.currentTimeMillis()
        recalcMetrics()
        if (allBlocks.isEmpty()) {
            Registries.BLOCK.stream()
                .filter { !it.defaultState.isAir }
                .sorted(compareBy { Registries.BLOCK.getId(it).toString() })
                .forEach(allBlocks::add)
        }
        refreshTextures()

        tabButtons.clear()
        settingsWidgets.clear()
        val scale = uiScale

        val gap = (BASE_GAP * scale).roundToInt()
        val tabHeight = (BASE_TAB_HEIGHT * scale).roundToInt()
        val tabOffset = max(2, (3 * scale).roundToInt())

        val fieldHeight = max(BASE_FIELD_HEIGHT.toFloat(), (textRenderer.fontHeight + 4) * scale).roundToInt()
        val buttonHeight = (BASE_BUTTON_HEIGHT * scale).roundToInt()
        val pagerHeight = (BASE_PAGER_HEIGHT * scale).roundToInt()
        val pagerWidth = (BASE_PAGER_WIDTH * scale).roundToInt()

        val panelRight = panelX + panelWidth
        val panelBottom = panelY + panelHeight

        titleY = panelY + gap
        subtitleY = titleY + textRenderer.fontHeight + (gap / 2)
        tabsY = subtitleY + textRenderer.fontHeight + max(2, (gap / 2))
        pagerY = panelBottom - gap - pagerHeight

        closeSize = max(10, (10 * scale).roundToInt())
        closeX = panelRight - gap - closeSize
        closeY = titleY

        sidebarX = panelX + gap
        contentX = panelX + gap
        gridX = contentX
        val gridWidth = panelRight - gap - contentX
        gridColumns = max(1, gridWidth / (cellWidth + colGap))

        val minTabWidth = 32
        val minTabGap = max(2, (2 * uiScale).roundToInt())
        val minTabsTotal = (minTabWidth * 4) + (minTabGap * 3)
        val desiredSearchWidth = 140.coerceAtLeast((panelWidth * 0.22f).roundToInt())
        val maxSearchWidth = (panelRight - gap) - contentX - minTabsTotal - gap
        val tabsStartY = tabsY + tabOffset
        val placeSearchInline = maxSearchWidth >= 80
        val searchWidth = if (placeSearchInline) {
            max(40, desiredSearchWidth.coerceAtMost(max(40, maxSearchWidth)))
        } else {
            max(80, desiredSearchWidth.coerceAtMost(gridWidth))
        }
        val searchX = if (placeSearchInline) (panelRight - gap) - searchWidth else contentX
        searchY = if (placeSearchInline) {
            tabsStartY + max(0, (tabHeight - fieldHeight) / 2)
        } else {
            tabsY + tabHeight + gap
        }
        gridY = if (placeSearchInline) {
            tabsY + tabHeight + gap
        } else {
            searchY + fieldHeight + gap
        }
        val settingsOffset = max(6, (6 * scale).roundToInt())
        val settingsHeaderPadding = max(4, (4 * scale).roundToInt())
        settingsY = gridY + settingsOffset
        if (tab == Tab.SETTINGS) {
            val minSettingsTop = tabsY + tabHeight + gap + textRenderer.fontHeight + settingsHeaderPadding
            val settingsExtra = max(10, (18 * scale).roundToInt())
            settingsY = max(settingsY, minSettingsTop) + settingsExtra
        }
        val availableGridHeight = pagerY - gap - gridY
        gridRows = max(1, availableGridHeight / (cellHeight + rowGap))
        entriesPerPage = max(1, gridColumns * gridRows)
        searchField = TextFieldWidget(textRenderer, searchX, searchY, max(40, searchWidth), fieldHeight, Text.literal("Search"))
        searchField.setPlaceholder(Text.literal("Search blocks and textures..."))
        searchField.setChangedListener { page = 0 }
        searchField.setDrawsBackground(false)
        addDrawableChild(searchField)
        setInitialFocus(searchField)

        val tabs = listOf(
            Triple("ALL", TAB_ICON_ALL, Tab.ALL),
            Triple("FAVORITES", TAB_ICON_FAVORITES, Tab.FAVORITES),
            Triple("CUSTOM", TAB_ICON_CUSTOM, Tab.CUSTOM),
            Triple("SETTINGS", TAB_ICON_SETTINGS, Tab.SETTINGS)
        )
        val desiredGap = max(6, (4 * uiScale).roundToInt())
        var gapBetween = desiredGap
        val availableTabWidth = if (placeSearchInline) searchX - contentX - gap else (panelRight - gap) - contentX
        val desiredWidths = tabs.map { (label, _, _) ->
            val textWidth = textRenderer.getWidth(label)
            max(48, textWidth + TAB_ICON_SIZE + 18)
        }
        val desiredTotal = desiredWidths.sum() + desiredGap * (tabs.size - 1)
        val tabWidths = if (desiredTotal <= availableTabWidth) {
            desiredWidths
        } else {
            gapBetween = max(2, (2 * uiScale).roundToInt())
            val remaining = (availableTabWidth - gapBetween * (tabs.size - 1)).coerceAtLeast(1)
            val scaleFactor = remaining.toFloat() / desiredWidths.sum().toFloat()
            val minWidth = 32
            val scaled = desiredWidths.map { max(minWidth, (it * scaleFactor).roundToInt()) }.toMutableList()
            if (scaled.size > 1) {
                val used = scaled.dropLast(1).sum() + gapBetween * (scaled.size - 1)
                scaled[scaled.lastIndex] = max(1, availableTabWidth - used)
            }
            scaled
        }

        var tabX = contentX
        tabs.forEachIndexed { index, (label, icon, target) ->
            tabX = addTabButton(tabX, tabsStartY, tabHeight, tabWidths[index], gapBetween, label, icon, target)
        }

        prevX = panelRight - gap - pagerWidth * 2 - gap
        nextX = panelRight - gap - pagerWidth

        fmeToggleButton = MinimalButton(contentX, settingsY, max(160, sidebarWidth + 30), buttonHeight, toggleText(), ::toggleFme)
        editToggleButton = MinimalButton(contentX, settingsY + buttonHeight + gap, max(160, sidebarWidth + 30), buttonHeight, editText(), ::toggleEdit)

        settingsWidgets.add(fmeToggleButton)
        settingsWidgets.add(editToggleButton)
        settingsWidgets.forEach { addDrawableChild(it) }

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

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelColor)
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, borderColor)

        context.drawText(textRenderer, Text.literal("FME"), contentX, titleY, textColor, false)
        val subtitleMaxWidth = max(0, (panelX + panelWidth) - contentX - (BASE_GAP * uiScale).roundToInt())
        val subtitle = trimToWidth(currentSelectionLabel(), subtitleMaxWidth)
        context.drawText(textRenderer, Text.literal(subtitle), contentX, subtitleY, mutedColor, false)
        val closeColor = if (isOverClose(mouseX.toDouble(), mouseY.toDouble())) accentColor else mutedColor
        context.drawText(textRenderer, Text.literal("x"), closeX, closeY, closeColor, false)

        if (tab != Tab.SETTINGS) {
            drawSearchFieldBackground(context, panelColor, borderColor)
            drawGrid(context, mouseX, mouseY, textColor, mutedColor, accentColor, selectionColor)
            drawPager(context, textColor, mutedColor, accentColor)
        }

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
        if (isOverClose(mouseX, mouseY)) {
            close()
            return true
        }
        for (button in tabButtons) {
            if (button.mouseClicked(click, doubleClick)) {
                return true
            }
        }
        if (tab != Tab.SETTINGS && handleGridClick(mouseX, mouseY, button)) {
            return true
        }
        if (tab != Tab.SETTINGS && handlePagerClick(mouseX, mouseY, button)) {
            return true
        }
        return super.mouseClicked(click, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (tab == Tab.SETTINGS) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val count = entriesCount()
        if (count <= 0 || entriesPerPage <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        val direction = if (verticalAmount < 0) 1 else -1
        page = MathHelper.clamp(page + direction, 0, maxPages - 1)
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }
        return super.keyPressed(input)
    }

    override fun shouldPause(): Boolean {
        return false
    }

    private fun drawSearchFieldBackground(context: DrawContext, panelColor: Int, borderColor: Int) {
        if (!searchField.visible) {
            return
        }
        val x = searchField.x
        val y = searchField.y
        val w = searchField.width
        val h = searchField.height
        val bg = withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.6f), 0xFF)
        context.fill(x, y, x + w, y + h, bg)
        drawBorder(context, x, y, w, h, borderColor)
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
        val count = entriesCount()
        if (entriesPerPage <= 0) {
            return
        }
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        page = MathHelper.clamp(page, 0, maxPages - 1)

        textureLoadBudget = 3
        val isCustom = tab == Tab.CUSTOM
        val entriesBlocks = if (isCustom || tab == Tab.SETTINGS) emptyList() else visibleEntries()
        val entriesTextures = if (isCustom) visibleTextureEntries() else emptyList()
        val cellBg = withAlpha(mixColor(textColor, panelColor(), 0.85f), 0xFF)
        val cellBorder = withAlpha(mixColor(textColor, panelColor(), 0.7f), 0xFF)
        val cellSelected = mixColor(selectionColor, panelColor(), 0.35f)
        val pulse = 0.15f * pulse01(900f)

        if (count == 0) {
            context.drawText(textRenderer, Text.literal(if (isCustom) "No custom textures found" else "No blocks found"),
                gridX, gridY, mutedColor, false)
            if (isCustom) {
                val info = "Drop PNGs into: ${HatTextureManager.getTextureDir()}"
                context.drawText(textRenderer, Text.literal(trimToWidth(info, panelWidth - (gridX - panelX) - 12)),
                    gridX, gridY + textRenderer.fontHeight + 4, mutedColor, false)
            }
            return
        }

        for (i in 0 until entriesPerPage) {
            val idx = page * entriesPerPage + i
            val col = i % gridColumns
            val row = i / gridColumns
            if (row >= gridRows) {
                break
            }
            val x = gridX + col * (cellWidth + colGap)
            val y = gridY + row * (cellHeight + rowGap)
            val hovered = isInRect(mouseX.toDouble(), mouseY.toDouble(), x, y, cellWidth, cellHeight)
            if (idx >= count) {
                drawCell(context, x, y, cellBg, cellBorder)
                continue
            }

            if (isCustom) {
                val path = entriesTextures[idx]
                val name = path.fileName.toString()
                val isSelected = name.equals(FmeManager.getSelectedCustomTextureName(), ignoreCase = true)
                val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
                val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
                val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
                drawCell(context, x, y, bg, border)
                val texId = getCustomTextureId(path)
                if (texId != null) {
                    try {
                        context.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x + 4, y + 4, 0f, 0f, 16, 16, 16, 16)
                    } catch (_: Throwable) {
                        // Ignore texture draw errors.
                    }
                }
                if (cellWidth >= 60) {
                    val label = trimToWidth(name, cellWidth - 26)
                    context.drawText(textRenderer, Text.literal(label), x + 24, y + 6, textColor, false)
                }
                if (FmeManager.isCustomTextureFavorite(name)) {
                    drawFavoriteStar(context, x, y, accentColor)
                }
                continue
            }

            val block = entriesBlocks[idx]
            val isSelected = FmeManager.getSelectedSourceType() == FmeManager.SelectedSourceType.BLOCK
                && block == FmeManager.getSelectedSource()
            val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
            val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
            val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
            drawCell(context, x, y, bg, border)
            val icon = ItemStack(block.asItem())
            if (!icon.isEmpty && icon.item != Items.AIR) {
                try {
                    context.drawItem(icon, x + 4, y + 4)
                } catch (_: Throwable) {
                    // Ignore item render errors.
                }
            }
            if (cellWidth >= 60) {
                val label = trimToWidth(block.name.string, cellWidth - 26)
                context.drawText(textRenderer, Text.literal(label), x + 24, y + 6, textColor, false)
            }
            if (FmeManager.isFavorite(block)) {
                drawFavoriteStar(context, x, y, accentColor)
            }
        }

    }

    private fun drawPager(context: DrawContext, textColor: Int, mutedColor: Int, accentColor: Int) {
        val count = entriesCount()
        if (entriesPerPage <= 0) {
            return
        }
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        val label = "Page ${page + 1} / $maxPages"
        val labelWidth = textRenderer.getWidth(label)
        val labelX = prevX - labelWidth - 10
        context.drawText(textRenderer, Text.literal(label), labelX, pagerY + 2, mutedColor, false)

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
        val bg = if (enabled) panelColor() else withAlpha(panelColor(), 120)
        val border = if (enabled) accentColor else withAlpha(accentColor, 120)
        context.fill(x, y, x + w, y + h, bg)
        drawBorder(context, x, y, w, h, border)
        val tx = x + max(0, (w - textRenderer.getWidth(label)) / 2)
        val ty = y + max(0, (h - textRenderer.fontHeight) / 2)
        context.drawText(textRenderer, Text.literal(label), tx, ty, if (enabled) textColor else withAlpha(textColor, 120), false)
    }

    private fun drawCell(context: DrawContext, x: Int, y: Int, bg: Int, border: Int) {
        context.fill(x, y, x + cellWidth, y + cellHeight, bg)
        drawBorder(context, x, y, cellWidth, cellHeight, border)
    }

    private fun drawFavoriteStar(context: DrawContext, x: Int, y: Int, color: Int) {
        val star = "*"
        val sx = x + cellWidth - textRenderer.getWidth(star) - 4
        val sy = y + 2
        context.drawText(textRenderer, Text.literal(star), sx, sy, color, false)
    }

    private fun handleGridClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (entriesPerPage <= 0) {
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

    private fun handlePagerClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) {
            return false
        }
        val w = (BASE_PAGER_WIDTH * uiScale).roundToInt()
        val h = (BASE_PAGER_HEIGHT * uiScale).roundToInt()
        val count = entriesCount()
        val maxPages = max(1, ceil(count / entriesPerPage.toDouble()).toInt())
        if (isInRect(mouseX, mouseY, prevX, pagerY, w, h) && page > 0) {
            page--
            playTabClickSound()
            return true
        }
        if (isInRect(mouseX, mouseY, nextX, pagerY, w, h) && page < maxPages - 1) {
            page++
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

    private fun selectTab(next: Tab) {
        if (tab == next) {
            return
        }
        tab = next
        page = 0
        if (tab == Tab.CUSTOM) {
            refreshTextures()
        }
        updateTabState()
        playTabClickSound()
    }

    private fun updateTabState() {
        val showSettings = tab == Tab.SETTINGS
        searchField.visible = !showSettings
        searchField.active = !showSettings
        settingsWidgets.forEach {
            it.visible = showSettings
            it.active = showSettings
        }
        tabButtons.forEach { it.updateActive(tab) }
    }

    private fun recalcMetrics() {
        val desiredScale = FmeManager.getGuiScale()
        val fitScaleW = (width - 64f) / BASE_PANEL_WIDTH
        val fitScaleH = (height - 64f) / BASE_PANEL_HEIGHT
        val fitScale = minOf(fitScaleW, fitScaleH)
        uiScale = minOf(desiredScale, fitScale, 1.0f).coerceAtLeast(0.5f)
        val scale = uiScale
        panelWidth = (BASE_PANEL_WIDTH * scale).roundToInt()
        panelHeight = (BASE_PANEL_HEIGHT * scale).roundToInt()
        cellWidth = (BASE_CELL_WIDTH * scale).roundToInt()
        cellHeight = (BASE_CELL_HEIGHT * scale).roundToInt()
        colGap = max(2, (BASE_GAP * scale).roundToInt() / 2)
        rowGap = max(2, (BASE_GAP * scale).roundToInt() / 2)
        sidebarWidth = 0

        panelX = max(8, (width - panelWidth) / 2)
        panelY = max(8, (height - panelHeight) / 2)
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

    private fun entriesCount(): Int {
        return when (tab) {
            Tab.SETTINGS -> 0
            Tab.CUSTOM -> visibleTextureEntries().size
            else -> visibleEntries().size
        }
    }

    private fun refreshTextures() {
        textureFiles.clear()
        customTextureCache.clear()
        customTextureFailed.clear()
        textureFiles.addAll(HatTextureManager.listTextures())
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

    private fun currentSelectionLabel(): String {
        return if (FmeManager.getSelectedSourceType() == FmeManager.SelectedSourceType.CUSTOM_TEXTURE) {
            val name = FmeManager.getSelectedCustomTextureName()
            if (name.isNullOrBlank()) "Selected: custom texture" else "Selected: $name"
        } else {
            "Selected: ${FmeManager.getSelectedSource().name.string}"
        }
    }

    private fun toggleFme() {
        val enabled = FmeManager.toggleEnabled()
        fmeToggleButton.message = Text.literal("FME: " + if (enabled) "ON" else "OFF")
    }

    private fun toggleEdit() {
        val edit = FmeManager.toggleEditMode()
        editToggleButton.message = Text.literal("Edit: " + if (edit) "ON" else "OFF")
    }


    private fun toggleText(): Text {
        return Text.literal("FME: " + if (FmeManager.isEnabled()) "ON" else "OFF")
    }

    private fun editText(): Text {
        return Text.literal("Edit: " + if (FmeManager.isEditMode()) "ON" else "OFF")
    }

    private fun playTabClickSound() {
        val client = MinecraftClient.getInstance()
        client.soundManager.play(PositionedSoundInstance.master(SoundEvent.of(TAB_CLICK_SOUND_ID), 1.0f))
    }

    private fun isOverClose(mouseX: Double, mouseY: Double): Boolean {
        return isInRect(mouseX, mouseY, closeX, closeY, closeSize + 4, closeSize + 4)
    }

    private fun isInRect(mouseX: Double, mouseY: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h
    }

    private fun panelColor(): Int = FmeManager.getGuiPanelColor()

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.fill(x, y, x + w, y + 1, color)
        context.fill(x, y + h - 1, x + w, y + h, color)
        context.fill(x, y, x + 1, y + h, color)
        context.fill(x + w - 1, y, x + w, y + h, color)
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
                else -> current == Tab.SETTINGS
            }
        }

        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            val panelColor = panelColor()
            val textColor = FmeManager.getGuiTextColor()
            val accentColor = FmeManager.getGuiAccentTextColor()
            val pulse = if (activeTab || isHovered) 0.12f * pulse01(900f) else 0f
            val bg = mixColor(panelColor, 0xFF000000.toInt(), 0.35f + pulse)
            val border = if (activeTab) accentColor else mixColor(textColor, panelColor, 0.75f)
            context.fill(x, y, x + width, y + height, bg)
            drawBorder(context, x, y, width, height, border)

            val iconX = x + 6
            val iconY = y + max(0, (height - TAB_ICON_SIZE) / 2)
            context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0f, 0f, TAB_ICON_SIZE, TAB_ICON_SIZE, TAB_ICON_SIZE, TAB_ICON_SIZE)

            val labelX = iconX + TAB_ICON_SIZE + 6
            val labelY = y + max(0, (height - textRenderer.fontHeight) / 2)
            val labelText = trimToWidth(label, width - (labelX - x) - 6)
            context.drawText(textRenderer, Text.literal(labelText), labelX, labelY, textColor, false)
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
    }

    private inner class MinimalButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        text: Text,
        private val onPress: () -> Unit
    ) : ClickableWidget(x, y, width, height, text) {
        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            val panelColor = panelColor()
            val textColor = FmeManager.getGuiTextColor()
            val accentColor = FmeManager.getGuiAccentTextColor()
            val pulse = if (isHovered) 0.12f * pulse01(900f) else 0f
            val bg = if (isHovered) mixColor(panelColor, accentColor, 0.2f + pulse)
            else mixColor(panelColor, 0xFF000000.toInt(), 0.1f)
            val border = mixColor(textColor, panelColor, 0.75f)
            context.fill(x, y, x + width, y + height, bg)
            drawBorder(context, x, y, width, height, border)
            val label = message.string
            val labelX = x + max(6, (width - textRenderer.getWidth(label)) / 2)
            val labelY = y + max(0, (height - textRenderer.fontHeight) / 2)
            context.drawText(textRenderer, Text.literal(label), labelX, labelY, textColor, false)
        }

        override fun onClick(click: Click, doubleClick: Boolean) {
            if (click.button() != 0) {
                return
            }
            onPress.invoke()
            playDownSound(MinecraftClient.getInstance().soundManager)
        }

        override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
            appendDefaultNarrations(builder)
        }

        override fun playDownSound(soundManager: SoundManager) {
            soundManager.play(PositionedSoundInstance.master(SoundEvent.of(TAB_CLICK_SOUND_ID), 1.0f))
        }
    }

}
