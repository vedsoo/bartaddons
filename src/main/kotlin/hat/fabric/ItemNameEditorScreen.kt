package hat.fabric

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import java.awt.Color
import java.util.Locale
import kotlin.math.roundToInt

class ItemNameEditorScreen(
    private val parent: Screen?,
    heldItem: ItemStack,
    private val slotIndex: Int
) : Screen(Text.literal("Item Name")) {
    private val originalStack: ItemStack = heldItem.copy()
    private val initialOverride = FmeManager.itemNameOverrideAt(heldItem)
    private var nameField: TextFieldWidget? = null
    private var hexField: TextFieldWidget? = null

    private var hue = 0f
    private var saturation = 0f
    private var brightness = 1f
    private var draggingSv = false
    private var draggingHue = false

    init {
        val initialRgb = detectRgb(heldItem)
        val hsb = FloatArray(3)
        Color.RGBtoHSB((initialRgb shr 16) and 0xFF, (initialRgb shr 8) and 0xFF, initialRgb and 0xFF, hsb)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
    }

    override fun init() {
        clearChildren()

        val panelX = panelX()
        val panelY = panelY()

        val itemName = TextFieldWidget(textRenderer, panelX + 16, panelY + 36, 228, 20, Text.literal("Name"))
        itemName.setMaxLength(64)
        itemName.setText(initialOverride?.customName ?: FmeManager.itemDisplayName(originalStack).string)
        addDrawableChild(itemName)
        nameField = itemName

        val hex = TextFieldWidget(textRenderer, panelX + 16, panelY + 188, 110, 20, Text.literal("Hex"))
        hex.setMaxLength(6)
        hex.setText(currentHex())
        hex.setChangedListener { text ->
            if (text.length == 6) {
                text.toIntOrNull(16)?.let { rgb ->
                    val hsb = FloatArray(3)
                    Color.RGBtoHSB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, hsb)
                    hue = hsb[0]
                    saturation = hsb[1]
                    brightness = hsb[2]
                }
            }
        }
        addDrawableChild(hex)
        hexField = hex

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Apply")) {
                applyNameChange(clearOnly = false)
            }.dimensions(panelX + 16, panelY + 218, 90, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Reset Name")) {
                applyNameChange(clearOnly = true)
            }.dimensions(panelX + 124, panelY + 218, 100, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) {
                close()
            }.dimensions(panelX + 246, panelY + 218, 90, 20).build()
        )

        setInitialFocus(itemName)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xA0000000.toInt())
        val panelX = panelX()
        val panelY = panelY()
        val panelWidth = panelWidth()
        val panelHeight = panelHeight()
        val border = FmeManager.getGuiBorderColor()
        val textColor = 0xFFFFFFFF.toInt()
        val accentColor = 0xFFFFFFFF.toInt()

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010.toInt())
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, border)
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, border)
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, border)
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, border)

        context.drawText(textRenderer, title, panelX + 16, panelY + 12, textColor, false)
        context.drawText(textRenderer, Text.literal("Held item: ${FmeManager.baseItemDisplayName(originalStack).string}"), panelX + 16, panelY + 24, accentColor, false)
        context.drawText(textRenderer, Text.literal("HSV Color Picker"), panelX + 16, panelY + 66, accentColor, false)
        context.drawText(textRenderer, Text.literal("Hex Color"), panelX + 16, panelY + 170, accentColor, false)
        drawSvBox(context)
        drawHueBar(context)

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (click.button() == 0) {
            if (insideSv(click.x(), click.y())) {
                draggingSv = true
                updateSv(click.x(), click.y())
                return true
            }
            if (insideHue(click.x(), click.y())) {
                draggingHue = true
                updateHue(click.y())
                return true
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        if (click.button() == 0 && draggingSv) {
            updateSv(click.x(), click.y())
            return true
        }
        if (click.button() == 0 && draggingHue) {
            updateHue(click.y())
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        draggingSv = false
        draggingHue = false
        return super.mouseReleased(click)
    }

    override fun resize(client: MinecraftClient, width: Int, height: Int) {
        val currentName = nameField?.text ?: ""
        val currentHex = hexField?.text ?: currentHex()
        super.resize(client, width, height)
        clearAndInit()
        nameField?.setText(currentName)
        hexField?.setText(currentHex)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false

    private fun applyNameChange(clearOnly: Boolean) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val inventory = player.inventory
        val current = inventory.getStack(slotIndex)
        if (current == null || current.isEmpty) {
            close()
            return
        }

        if (clearOnly) {
            FmeManager.clearItemNameOverride(current)
        } else {
            val rawName = nameField?.text?.trim().orEmpty()
            if (rawName.isEmpty()) {
                FmeManager.clearItemNameOverride(current)
            } else {
                FmeManager.setItemNameOverride(current, rawName, currentRgb())
            }
        }
        FmeManager.sendClientMessage(
            if (clearOnly || nameField?.text?.trim().isNullOrEmpty()) {
                "Reset item name for ${FmeManager.baseItemDisplayName(current).string}"
            } else {
                "Updated item name for ${FmeManager.baseItemDisplayName(current).string}"
            }
        )
        close()
    }

    private fun drawSvBox(context: DrawContext) {
        val x = svX()
        val y = svY()
        val size = pickerSize()
        val step = 2
        for (yy in 0 until size step step) {
            for (xx in 0 until size step step) {
                val s = xx.toFloat() / (size - 1).toFloat()
                val v = 1f - yy.toFloat() / (size - 1).toFloat()
                context.fill(x + xx, y + yy, x + xx + step, y + yy + step, hsvToRgbInt(hue, s, v))
            }
        }

        val markerX = x + (saturation * size).roundToInt()
        val markerY = y + ((1f - brightness) * size).roundToInt()
        context.fill(markerX - 2, markerY - 2, markerX + 2, markerY + 2, 0xFFFFFFFF.toInt())
        context.fill(markerX - 1, markerY - 1, markerX + 1, markerY + 1, 0xFF000000.toInt())
    }

    private fun drawHueBar(context: DrawContext) {
        val x = hueX()
        val y = svY()
        val width = hueWidth()
        val height = pickerSize()
        for (yy in 0 until height) {
            val h = yy.toFloat() / (height - 1).toFloat()
            context.fill(x, y + yy, x + width, y + yy + 1, hsvToRgbInt(h, 1f, 1f))
        }

        val markerY = y + (hue * height).roundToInt()
        context.fill(x - 1, markerY - 1, x + width + 1, markerY + 1, 0xFFFFFFFF.toInt())
    }

    private fun updateSv(mouseX: Double, mouseY: Double) {
        val x = ((mouseX - svX()) / pickerSize().toDouble()).coerceIn(0.0, 1.0)
        val y = ((mouseY - svY()) / pickerSize().toDouble()).coerceIn(0.0, 1.0)
        saturation = x.toFloat()
        brightness = (1.0 - y).toFloat()
        syncHexField()
    }

    private fun updateHue(mouseY: Double) {
        hue = ((mouseY - svY()) / pickerSize().toDouble()).coerceIn(0.0, 1.0).toFloat()
        syncHexField()
    }

    private fun syncHexField() {
        val field = hexField ?: return
        val hex = currentHex()
        if (!field.text.equals(hex, ignoreCase = true)) {
            field.setText(hex)
        }
    }

    private fun currentRgb(): Int {
        return Color.HSBtoRGB(hue, saturation, brightness) and 0xFFFFFF
    }

    private fun currentHex(): String {
        return String.format(Locale.US, "%06X", currentRgb())
    }

    private fun detectRgb(stack: ItemStack): Int {
        return initialOverride?.color ?: FmeManager.baseItemDisplayName(stack).style.color?.rgb ?: 0xFFFFFF
    }

    private fun insideSv(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= svX() && mouseX <= svX() + pickerSize()
            && mouseY >= svY() && mouseY <= svY() + pickerSize()
    }

    private fun insideHue(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= hueX() && mouseX <= hueX() + hueWidth()
            && mouseY >= svY() && mouseY <= svY() + pickerSize()
    }

    private fun hsvToRgbInt(h: Float, s: Float, v: Float): Int {
        return 0xFF000000.toInt() or (Color.HSBtoRGB(h, s, v) and 0xFFFFFF)
    }

    private fun panelWidth(): Int = 352

    private fun panelHeight(): Int = 280

    private fun panelX(): Int = (width - panelWidth()) / 2

    private fun panelY(): Int = (height - panelHeight()) / 2

    private fun pickerSize(): Int = 96

    private fun svX(): Int = panelX() + 16

    private fun svY(): Int = panelY() + 80

    private fun hueWidth(): Int = 14

    private fun hueX(): Int = svX() + pickerSize() + 10
}
