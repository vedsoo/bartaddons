package hat.fabric

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
import kotlin.math.roundToInt

class ItemTransformEditorScreen(
    private val parent: Screen
) : Screen(Text.literal("Item Transform")) {
    override fun init() {
        clearChildren()

        val panelWidth = 620
        val panelHeight = 250
        val panelX = (width - panelWidth) / 2
        val panelY = (height - panelHeight) / 2
        val colGap = 14
        val rowGap = 6
        val colWidth = (panelWidth - colGap * 3) / 2
        val leftX = panelX + colGap
        val rightX = leftX + colWidth + colGap
        val sliderWidth = colWidth
        val sliderHeight = 20
        var leftY = panelY + 28
        var rightY = panelY + 28

        fun addSlider(
            x: Int,
            y: Int,
            label: String,
            initial: Float,
            min: Float,
            max: Float,
            getter: () -> Float,
            setter: (Float) -> Unit
        ) {
            addDrawableChild(object : SliderWidget(x, y, sliderWidth, sliderHeight, Text.empty(), normalize(initial, min, max)) {
                init {
                    updateMessage()
                }

                override fun updateMessage() {
                    message = Text.literal("$label: ${formatValue(getter())}")
                }

                override fun applyValue() {
                    setter(denormalize(value, min, max))
                    updateMessage()
                }
            })
        }

        addSlider(leftX, leftY, "FP X", FmeManager.getItemFirstPersonX(), -2.0f, 2.0f, FmeManager::getItemFirstPersonX, FmeManager::setItemFirstPersonX)
        leftY += sliderHeight + rowGap
        addSlider(leftX, leftY, "FP Y", FmeManager.getItemFirstPersonY(), -2.0f, 2.0f, FmeManager::getItemFirstPersonY, FmeManager::setItemFirstPersonY)
        leftY += sliderHeight + rowGap
        addSlider(leftX, leftY, "FP Z", FmeManager.getItemFirstPersonZ(), -2.0f, 2.0f, FmeManager::getItemFirstPersonZ, FmeManager::setItemFirstPersonZ)
        leftY += sliderHeight + rowGap
        addSlider(leftX, leftY, "FP Rot X", FmeManager.getItemFirstPersonRotX(), -180.0f, 180.0f, FmeManager::getItemFirstPersonRotX, FmeManager::setItemFirstPersonRotX)
        leftY += sliderHeight + rowGap
        addSlider(leftX, leftY, "FP Rot Y", FmeManager.getItemFirstPersonRotY(), -180.0f, 180.0f, FmeManager::getItemFirstPersonRotY, FmeManager::setItemFirstPersonRotY)
        leftY += sliderHeight + rowGap
        addSlider(leftX, leftY, "FP Rot Z", FmeManager.getItemFirstPersonRotZ(), -180.0f, 180.0f, FmeManager::getItemFirstPersonRotZ, FmeManager::setItemFirstPersonRotZ)
        leftY += sliderHeight + rowGap
        addSlider(leftX, leftY, "FP Scale", FmeManager.getItemFirstPersonScale(), 0.1f, 3.0f, FmeManager::getItemFirstPersonScale, FmeManager::setItemFirstPersonScale)

        addSlider(rightX, rightY, "TP X", FmeManager.getItemThirdPersonX(), -2.0f, 2.0f, FmeManager::getItemThirdPersonX, FmeManager::setItemThirdPersonX)
        rightY += sliderHeight + rowGap
        addSlider(rightX, rightY, "TP Y", FmeManager.getItemThirdPersonY(), -2.0f, 2.0f, FmeManager::getItemThirdPersonY, FmeManager::setItemThirdPersonY)
        rightY += sliderHeight + rowGap
        addSlider(rightX, rightY, "TP Z", FmeManager.getItemThirdPersonZ(), -2.0f, 2.0f, FmeManager::getItemThirdPersonZ, FmeManager::setItemThirdPersonZ)
        rightY += sliderHeight + rowGap
        addSlider(rightX, rightY, "TP Rot X", FmeManager.getItemThirdPersonRotX(), -180.0f, 180.0f, FmeManager::getItemThirdPersonRotX, FmeManager::setItemThirdPersonRotX)
        rightY += sliderHeight + rowGap
        addSlider(rightX, rightY, "TP Rot Y", FmeManager.getItemThirdPersonRotY(), -180.0f, 180.0f, FmeManager::getItemThirdPersonRotY, FmeManager::setItemThirdPersonRotY)
        rightY += sliderHeight + rowGap
        addSlider(rightX, rightY, "TP Rot Z", FmeManager.getItemThirdPersonRotZ(), -180.0f, 180.0f, FmeManager::getItemThirdPersonRotZ, FmeManager::setItemThirdPersonRotZ)
        rightY += sliderHeight + rowGap
        addSlider(rightX, rightY, "TP Scale", FmeManager.getItemThirdPersonScale(), 0.1f, 3.0f, FmeManager::getItemThirdPersonScale, FmeManager::setItemThirdPersonScale)

        val buttonY = panelY + panelHeight - 28
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Reset")) {
                FmeManager.resetItemTransformSettings()
                clearAndInit()
            }.dimensions(panelX + colGap, buttonY, 90, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) {
                close()
            }.dimensions(panelX + panelWidth - colGap - 90, buttonY, 90, 20).build()
        )
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, 0xA0000000.toInt())
        val panelWidth = 620
        val panelHeight = 250
        val panelX = (width - panelWidth) / 2
        val panelY = (height - panelHeight) / 2
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0101010.toInt())
        val border = FmeManager.getGuiBorderColor()
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 1, border)
        context.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, border)
        context.fill(panelX, panelY, panelX + 1, panelY + panelHeight, border)
        context.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, border)
        context.drawText(textRenderer, title, panelX + 12, panelY + 10, FmeManager.getGuiTextColor(), false)
        context.drawText(textRenderer, Text.literal("First Person"), panelX + 14, panelY + 18, FmeManager.getGuiAccentTextColor(), false)
        context.drawText(textRenderer, Text.literal("Third Person"), panelX + panelWidth / 2 + 6, panelY + 18, FmeManager.getGuiAccentTextColor(), false)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun resize(client: MinecraftClient, width: Int, height: Int) {
        super.resize(client, width, height)
        clearAndInit()
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false

    private fun formatValue(value: Float): String = ((value * 100f).roundToInt() / 100f).toString()

    private fun normalize(value: Float, min: Float, max: Float): Double {
        if (max <= min) {
            return 0.0
        }
        return ((value - min) / (max - min)).coerceIn(0f, 1f).toDouble()
    }

    private fun denormalize(value: Double, min: Float, max: Float): Float {
        return (min + (max - min) * value.toFloat()).coerceIn(min, max)
    }
}
