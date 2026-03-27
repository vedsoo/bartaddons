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
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundManager
import net.minecraft.client.input.CharInput
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
import java.awt.Color
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class FmeScreen(private val openGuiSettings: Boolean = false) : Screen(Text.literal("FME")) {
    companion object {
        private const val BASE_PANEL_WIDTH = 640
        private const val BASE_PANEL_HEIGHT = 360
        private const val BASE_CELL_WIDTH = 36
        private const val BASE_CELL_HEIGHT = 36
        private const val BASE_GAP = 6
        private const val BASE_TAB_HEIGHT = 24
        private const val BASE_FIELD_HEIGHT = 18
        private const val BASE_SIDEBAR_WIDTH = 120
        private const val BASE_BUTTON_HEIGHT = 20
        private const val BASE_PAGER_WIDTH = 70
        private const val BASE_PAGER_HEIGHT = 18
        private const val TAB_ICON_SIZE = 16

        private val TAB_CLICK_SOUND_ID = Identifier.of("hat", "ui.click")
        private val OPEN_SOUND_ID = Identifier.of("hat", "ui.open")
        private val CLOSE_SOUND_ID = Identifier.of("hat", "ui.close")
        private val TAB_ICON_ALL = Identifier.of("hat", "textures/gui/tabs/all.png")
        private val TAB_ICON_FAVORITES = Identifier.of("hat", "textures/gui/tabs/favorites.png")
        private val TAB_ICON_CUSTOM = Identifier.of("hat", "textures/gui/tabs/custom.png")
    }

    private enum class Tab {
        ALL,
        FAVORITES,
        CUSTOM
    }

    private val allBlocks = mutableListOf<Block>()
    private val textureFiles = mutableListOf<Path>()
    private val customTextureCache = mutableMapOf<Path, Identifier>()
    private val customTextureFailed = mutableSetOf<Path>()
    private val tabButtons = mutableListOf<TabButton>()
    private var tab = Tab.ALL
    private var page = 0
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
    private var contentX = 0
    private var headerY = 0
    private var searchY = 0
    private var searchX = 0
    private var searchWidth = 0
    private var searchHeight = 0
    private var searchTextOffsetX = 0
    private var searchTextOffsetY = 0
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
        playOpenSound()
        recalcMetrics()
        if (allBlocks.isEmpty()) {
            Registries.BLOCK.stream()
                .filter { !it.defaultState.isAir }
                .sorted(compareBy { Registries.BLOCK.getId(it).toString() })
                .forEach(allBlocks::add)
        }
        refreshTextures()

        tabButtons.clear()
        val scale = uiScale

        val gap = (BASE_GAP * scale).roundToInt()
        val tabHeight = (BASE_TAB_HEIGHT * scale).roundToInt()
        val fieldHeight = max(BASE_FIELD_HEIGHT.toFloat(), (textRenderer.fontHeight + 4) * scale).roundToInt()
        searchHeight = fieldHeight + max(2, (2 * scale).roundToInt())
        searchTextOffsetX = max(2, (2 * scale).roundToInt())
        searchTextOffsetY = max(4, (4 * scale).roundToInt())
        val pagerHeight = (BASE_PAGER_HEIGHT * scale).roundToInt()
        val pagerWidth = (BASE_PAGER_WIDTH * scale).roundToInt()

        val panelRight = panelX + panelWidth
        val panelBottom = panelY + panelHeight

        headerY = panelY + gap
        val tabsOffset = max(3, (6 * scale).roundToInt())
        tabsY = headerY + tabsOffset
        pagerY = panelBottom - gap - pagerHeight

        closeSize = max(10, (10 * scale).roundToInt())
        closeX = panelRight - gap - closeSize
        closeY = headerY

        sidebarWidth = (BASE_SIDEBAR_WIDTH * scale).roundToInt()
        sidebarX = panelX + gap
        contentX = sidebarX + sidebarWidth + gap
        val gridWidth = panelRight - gap - contentX
        gridColumns = max(1, gridWidth / (cellWidth + colGap))
        val actualGridWidth = gridColumns * cellWidth + max(0, gridColumns - 1) * colGap
        gridX = contentX + max(0, (gridWidth - actualGridWidth) / 2)

        searchX = contentX
        searchY = headerY + max(0, (tabHeight - searchHeight) / 2)
        gridY = headerY + tabHeight + gap
        val settingsOffset = max(6, (6 * scale).roundToInt())
        settingsY = gridY + settingsOffset
        val availableGridHeight = pagerY - gap - gridY
        gridRows = max(1, availableGridHeight / (cellHeight + rowGap))
        entriesPerPage = max(1, gridColumns * gridRows)
        searchWidth = max(80, panelRight - gap - searchX - closeSize - gap)
        searchField = TextFieldWidget(
            textRenderer,
            searchX + searchTextOffsetX,
            searchY + searchTextOffsetY,
            searchWidth,
            searchHeight,
            Text.literal("Search")
        )
        searchField.setPlaceholder(Text.literal("Search blocks and textures..."))
        searchField.setChangedListener { page = 0 }
        searchField.setDrawsBackground(false)
        addDrawableChild(searchField)
        searchField.visible = true
        searchField.active = true

        val tabs = listOf(
            Triple("ALL", TAB_ICON_ALL, Tab.ALL),
            Triple("FAVORITES", TAB_ICON_FAVORITES, Tab.FAVORITES),
            Triple("CUSTOM", TAB_ICON_CUSTOM, Tab.CUSTOM)
        )
        val tabGap = max(6, (4 * uiScale).roundToInt())
        val tabWidth = sidebarWidth - gap * 2
        var tabY = tabsY
        tabs.forEach { (label, icon, target) ->
            addTabButton(sidebarX + gap, tabY, tabHeight, tabWidth, tabGap, label, icon, target)
            tabY += tabHeight + tabGap
        }

        prevX = panelRight - gap - pagerWidth * 2 - gap
        nextX = panelRight - gap - pagerWidth

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

        drawPanelBackground(context, panelColor, borderColor, innerPanelColor, sidebarColor)
        drawSearchFieldBackground(context, panelColor, borderColor)

        val closeColor = if (isOverClose(mouseX.toDouble(), mouseY.toDouble())) accentColor else mutedColor
        context.drawText(textRenderer, Text.literal("x"), closeX, closeY, closeColor, false)

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
        if (searchField.visible && searchField.isMouseOver(mouseX, mouseY)) {
            searchField.setFocused(true)
            return searchField.mouseClicked(click, doubleClick)
        }
        searchField.setFocused(false)
        if (isOverClose(mouseX, mouseY)) {
            close()
            return true
        }
        for (button in tabButtons) {
            if (button.mouseClicked(click, doubleClick)) {
                return true
            }
        }
        if (handleGridClick(mouseX, mouseY, button)) {
            return true
        }
        if (handlePagerClick(mouseX, mouseY, button)) {
            return true
        }
        return super.mouseClicked(click, doubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
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
        val bg = withAlpha(mixColor(panelColor, 0xFF000000.toInt(), 0.6f), 0xFF)
        drawRoundedRect(context, x, y, w, h, bg, borderColor, max(3, (6 * uiScale).roundToInt()))
    }

    private fun drawPanelBackground(
        context: DrawContext,
        panelColor: Int,
        borderColor: Int,
        innerPanelColor: Int,
        sidebarColor: Int
    ) {
        val radius = max(6, (10 * uiScale).roundToInt())
        val innerRadius = max(2, radius - 3)
        val innerInset = 4
        val innerX = panelX + innerInset
        val innerY = panelY + innerInset
        val innerW = panelWidth - innerInset * 2
        val innerH = panelHeight - innerInset * 2
        val sidebarBottom = panelY + panelHeight - (BASE_GAP * uiScale).roundToInt()
        val sidebarH = max(0, sidebarBottom - headerY)

        val gradientStart = Color(80, 130, 210, 235)
        val gradientEnd = Color(10, 16, 30, 245)
        val border = toAwtColor(borderColor)
        val inner = toAwtColor(innerPanelColor, 170)
        val sidebar = toAwtColor(sidebarColor, 155)

        PIPNVG.drawNVG(context, 0, 0, width, height) {
            NVG.gradientRect(panelX, panelY, panelWidth, panelHeight, gradientStart, gradientEnd, Gradient.TopToBottom, radius.toFloat())
            NVG.hollowRect(panelX, panelY, panelWidth, panelHeight, 1f, border, radius.toFloat())
            NVG.rect(innerX, innerY, innerW, innerH, inner, innerRadius.toFloat())
            if (sidebarH > 0) {
                NVG.rect(sidebarX, headerY, sidebarWidth, sidebarH, sidebar, innerRadius.toFloat())
            }
        }
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
        val entriesBlocks = if (isCustom) emptyList() else visibleEntries()
        val entriesTextures = if (isCustom) visibleTextureEntries() else emptyList()
        val cellBg = withAlpha(mixColor(panelColor(), 0xFF000000.toInt(), 0.18f), 0xFF)
        val cellBorder = withAlpha(mixColor(textColor, panelColor(), 0.75f), 120)
        val cellSelected = mixColor(selectionColor, panelColor(), 0.2f)
        val pulse = 0.15f * pulse01(900f)

        if (count == 0) {
            context.drawText(textRenderer, Text.literal(if (isCustom) "No custom textures found" else "No blocks found"),
                gridX, gridY, mutedColor, false)
            if (isCustom) {
                val info = "Drop PNGs into: config/fme/custom_textures"
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
                val label = trimToWidth(name, cellWidth - 12)
                val labelY = y + max(0, (cellHeight - textRenderer.fontHeight) / 2)
                context.drawText(textRenderer, Text.literal(label), x + 8, labelY, textColor, false)
                continue
            }

            val block = entriesBlocks[idx]
            val isSelected = FmeManager.getSelectedSourceType() == FmeManager.SelectedSourceType.BLOCK
                && block == FmeManager.getSelectedSource()
            val baseBg = if (isSelected) mixColor(cellSelected, accentColor, pulse) else cellBg
            val bg = if (hovered) mixColor(baseBg, accentColor, 0.12f + pulse) else baseBg
            val border = if (hovered) mixColor(cellBorder, accentColor, 0.25f + pulse) else cellBorder
            drawCell(context, x, y, bg, border)
            val item = block.asItem()
            val hasItem = item != Items.AIR
            val iconSize = max(12, minOf(24, cellHeight - 10))
            val iconX = x + max(2, (cellWidth - iconSize) / 2) - 1
            val iconY = y + max(2, (cellHeight - iconSize) / 2) + 1
            if (hasItem) {
                context.drawItem(ItemStack(item), iconX, iconY)
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
        val bg = if (enabled) panelColor() else withAlpha(panelColor(), 120)
        val border = if (enabled) accentColor else withAlpha(accentColor, 120)
        drawRoundedRect(context, x, y, w, h, bg, border, max(3, (6 * uiScale).roundToInt()))
        val tx = x + max(0, (w - textRenderer.getWidth(label)) / 2)
        val ty = y + max(0, (h - textRenderer.fontHeight) / 2)
        context.drawText(textRenderer, Text.literal(label), tx, ty, if (enabled) textColor else withAlpha(textColor, 120), false)
    }

    private fun drawCell(context: DrawContext, x: Int, y: Int, bg: Int, border: Int) {
        drawRoundedRect(context, x, y, cellWidth, cellHeight, bg, border, max(3, (6 * uiScale).roundToInt()))
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
        searchField.visible = true
        searchField.active = true
        searchField.setFocused(true)
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
        sidebarWidth = (BASE_SIDEBAR_WIDTH * scale).roundToInt()

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

    private fun drawRoundedRect(context: DrawContext, x: Int, y: Int, w: Int, h: Int, fill: Int, border: Int, radius: Int) {
        PIPNVG.drawNVG(context, 0, 0, width, height) {
            NVG.rect(x, y, w, h, toAwtColor(fill), radius.toFloat())
            NVG.hollowRect(x, y, w, h, 1f, toAwtColor(border), radius.toFloat())
        }
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
                else -> false
            }
        }

        override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
            val panelColor = panelColor()
            val textColor = FmeManager.getGuiTextColor()
            val accentColor = FmeManager.getGuiAccentTextColor()
            val pulse = if (activeTab || isHovered) 0.1f * pulse01(900f) else 0f
            val base = mixColor(panelColor, 0xFF000000.toInt(), 0.2f)
            val active = mixColor(panelColor, accentColor, 0.2f + pulse)
            val bg = if (activeTab || isHovered) active else base
            val border = mixColor(textColor, panelColor, 0.75f)
            drawRoundedRect(context, x, y, width, height, bg, border, max(3, (6 * uiScale).roundToInt()))

            val labelX = x + 8
            val labelY = y + max(0, (height - textRenderer.fontHeight) / 2)
            val labelText = trimToWidth(label.lowercase(Locale.ROOT), width - 12)
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

}
