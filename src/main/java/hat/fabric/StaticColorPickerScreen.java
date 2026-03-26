package hat.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class StaticColorPickerScreen extends Screen {
    private final Screen parent;

    public StaticColorPickerScreen(Screen parent) {
        super(Text.literal("Static Color"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int centerX = this.width / 2;
        final int centerY = this.height / 2;

        this.addDrawableChild(new ColorSlider(centerX - 90, centerY - 42, "R", HatConfig.staticRed, value -> HatConfig.staticRed = value));
        this.addDrawableChild(new ColorSlider(centerX - 90, centerY - 18, "G", HatConfig.staticGreen, value -> HatConfig.staticGreen = value));
        this.addDrawableChild(new ColorSlider(centerX - 90, centerY + 6, "B", HatConfig.staticBlue, value -> HatConfig.staticBlue = value));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.close())
            .dimensions(centerX - 90, centerY + 34, 180, 20)
            .build());
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private interface IntSetter {
        void set(int value);
    }

    private static final class ColorSlider extends SliderWidget {
        private final String label;
        private final IntSetter setter;

        private ColorSlider(int x, int y, String label, int initial, IntSetter setter) {
            super(x, y, 180, 20, Text.empty(), initial / 255.0D);
            this.label = label;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int v = (int) Math.round(this.value * 255.0D);
            MutableText msg = Text.literal(label + ": " + v);
            this.setMessage(msg);
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(this.value * 255.0D);
            setter.set(v);
            this.updateMessage();
        }
    }
}
