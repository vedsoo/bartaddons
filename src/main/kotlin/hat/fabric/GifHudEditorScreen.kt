package hat.fabric

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.max

class GifHudEditorScreen(private val parent: Screen) : Screen(Text.literal("GIF HUD Editor")) {
    private var dragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val overlay = 0x66000000
        context.fill(0, 0, width, height, overlay)

        GifHudManager.renderPreview(context)

        val w = GifHudManager.getDisplayWidth()
        val h = GifHudManager.getDisplayHeight()
        val x = GifHudManager.getX().toInt()
        val y = GifHudManager.getY().toInt()

        val border = if (dragging || GifHudManager.isHovered(mouseX, mouseY)) 0xFFFFCC33.toInt() else 0x66FFFFFF
        context.fill(x, y, x + w, y + 1, border)
        context.fill(x, y + h - 1, x + w, y + h, border)
        context.fill(x, y, x + 1, y + h, border)
        context.fill(x + w - 1, y, x + w, y + h, border)

        val label = Text.literal("Drag to move. Scroll to scale. ESC to exit.")
        val textX = max(6, width / 2 - textRenderer.getWidth(label) / 2)
        context.drawText(textRenderer, label, textX, 6, Color.WHITE.rgb, false)

        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(click: Click, doubleClick: Boolean): Boolean {
        if (click.button() == 0 && GifHudManager.isHovered(click.x().toInt(), click.y().toInt())) {
            dragging = true
            dragOffsetX = click.x().toFloat() - GifHudManager.getX()
            dragOffsetY = click.y().toFloat() - GifHudManager.getY()
            return true
        }
        return super.mouseClicked(click, doubleClick)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        if (dragging && click.button() == 0) {
            GifHudManager.setPosition(click.x().toFloat() - dragOffsetX, click.y().toFloat() - dragOffsetY)
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        dragging = false
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!GifHudManager.isEnabled()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        }
        val delta = (verticalAmount * 0.1f).toFloat()
        GifHudManager.nudgeScale(delta)
        return true
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.getKeycode() == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }
        return super.keyPressed(input)
    }

    override fun close() {
        client?.setScreen(parent)
    }

    override fun shouldPause(): Boolean = false
}
