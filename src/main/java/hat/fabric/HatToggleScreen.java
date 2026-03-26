package hat.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class HatToggleScreen extends Screen {
    public HatToggleScreen() {
        super(Text.literal("Chinese Hat"));
    }

    @Override
    protected void init() {
        final int centerX = this.width / 2;
        final int centerY = this.height / 2;

        this.addDrawableChild(ButtonWidget.builder(toggleText(), button -> {
            HatConfig.chinaHatEnabled = !HatConfig.chinaHatEnabled;
            button.setMessage(toggleText());
        }).dimensions(centerX - 90, centerY - 10, 180, 20).build());

        this.addDrawableChild(ButtonWidget.builder(modeText(), button -> {
            HatConfig.colorMode = HatConfig.colorMode.next();
            button.setMessage(modeText());
            this.clearAndInit();
        }).dimensions(centerX - 90, centerY + 14, 180, 20).build());

        if (HatConfig.colorMode == HatConfig.ColorMode.STATIC) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Pick Static Color"), button ->
                    this.client.setScreen(new StaticColorPickerScreen(this)))
                .dimensions(centerX - 90, centerY + 38, 180, 20)
                .build());
        }

        if (HatConfig.colorMode == HatConfig.ColorMode.TEXTURE) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Reload Texture"), button -> {
                HatTextureManager.reload();
                if (this.client != null && this.client.player != null) {
                    if (HatTextureManager.hasTexture()) {
                        this.client.player.sendMessage(Text.literal(
                            "Hat texture loaded: " + HatTextureManager.getTexturePath()
                        ), false);
                    } else {
                        this.client.player.sendMessage(Text.literal(
                            "No PNG found in: " + HatTextureManager.getTextureDir()
                            + " " + HatTextureManager.getTextureDir()
                        ), false);
                    }
                }
            }).dimensions(centerX - 90, centerY + 38, 180, 20).build());
        }

        int doneOffset = (HatConfig.colorMode == HatConfig.ColorMode.STATIC
            || HatConfig.colorMode == HatConfig.ColorMode.TEXTURE) ? 62 : 38;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> this.close())
            .dimensions(centerX - 90, centerY + doneOffset, 180, 20)
            .build());
    }

    private Text toggleText() {
        return Text.literal("Chinese Hat: " + (HatConfig.chinaHatEnabled ? "ON" : "OFF"));
    }

    private Text modeText() {
        return Text.literal("Color Mode: " + HatConfig.colorMode.label());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
