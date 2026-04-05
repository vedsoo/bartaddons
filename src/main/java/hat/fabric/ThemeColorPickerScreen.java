package hat.fabric;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public final class ThemeColorPickerScreen extends Screen {
    @FunctionalInterface
    public interface ColorSetter {
        void set(int value);
    }

    private final Screen parent;
    private final String label;
    private final ColorSetter setter;
    private int color;

    public ThemeColorPickerScreen(Screen parent, String label, int color, ColorSetter setter) {
        super(Text.literal("Pick Color"));
        this.parent = parent;
        this.label = label;
        this.setter = setter;
        this.color = color;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addDrawableChild(new ColorSlider(centerX - 90, centerY - 54, "A", (color >> 24) & 0xFF, value -> setComponent(24, value)));
        this.addDrawableChild(new ColorSlider(centerX - 90, centerY - 30, "R", (color >> 16) & 0xFF, value -> setComponent(16, value)));
        this.addDrawableChild(new ColorSlider(centerX - 90, centerY - 6, "G", (color >> 8) & 0xFF, value -> setComponent(8, value)));
        this.addDrawableChild(new ColorSlider(centerX - 90, centerY + 18, "B", color & 0xFF, value -> setComponent(0, value)));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
            .dimensions(centerX - 90, centerY + 46, 180, 20)
            .build());
    }

    private void setComponent(int shift, int value) {
        int mask = 0xFF << shift;
        color = (color & ~mask) | ((value & 0xFF) << shift);
        setter.set(color);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class ColorSlider extends SliderWidget {
        private final String label;
        private final ComponentSetter setter;

        private ColorSlider(int x, int y, String label, int initial, ComponentSetter setter) {
            super(x, y, 180, 20, Text.empty(), initial / 255.0D);
            this.label = label;
            this.setter = setter;
            updateMessage();
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
            updateMessage();
        }
    }

    private interface ComponentSetter {
        void set(int value);
    }
}
