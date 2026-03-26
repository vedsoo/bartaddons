package hat.fabric;

public final class HatConfig {
    private HatConfig() {
    }

    public static boolean chinaHatEnabled = true;
    public static ColorMode colorMode = ColorMode.RAINBOW;
    public static int staticRed = 255;
    public static int staticGreen = 255;
    public static int staticBlue = 255;

    public enum ColorMode {
        RAINBOW("Rainbow"),
        MLM("MLM"),
        TRANS("Trans"),
        STATIC("Static"),
        TEXTURE("Texture");

        private final String label;

        ColorMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public ColorMode next() {
            ColorMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
