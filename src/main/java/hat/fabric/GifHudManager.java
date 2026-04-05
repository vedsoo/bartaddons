package hat.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.metadata.IIOMetadata;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class GifHudManager {
    private static final Identifier TEXTURE_ID = Identifier.of("hat", "gif_hud");
    private static final Path GIF_DIR = FabricLoader.getInstance().getConfigDir()
        .resolve("fme")
        .resolve("gif_hud");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
        .resolve("fme")
        .resolve("gif_hud.json");
    private static final float MIN_SCALE = 0.1f;
    private static final float MAX_SCALE = 5.0f;

    private static boolean enabled = false;
    private static float x = 20f;
    private static float y = 20f;
    private static float scale = 1f;
    private static String selectedFileName;

    private static final List<GifFrame> frames = new ArrayList<>();
    private static int frameIndex = 0;
    private static long nextFrameTimeMs = 0L;
    private static int frameWidth = 0;
    private static int frameHeight = 0;
    private static NativeImageBackedTexture texture;

    private GifHudManager() {
    }

    public static void load() {
        ensureDirs();
        readConfig();
        // Defer loading GIF frames until the first render tick to avoid
        // touching GPU resources before RenderSystem is initialized.
    }

    public static void tick(long nowMs) {
        if (frames.isEmpty()) {
            tryLoadSelectedGif();
        }
        if (!enabled || frames.isEmpty()) {
            return;
        }
        if (nextFrameTimeMs == 0L) {
            nextFrameTimeMs = nowMs + frames.get(frameIndex).delayMs();
            return;
        }
        if (nowMs < nextFrameTimeMs) {
            return;
        }
        frameIndex = (frameIndex + 1) % frames.size();
        nextFrameTimeMs = nowMs + frames.get(frameIndex).delayMs();
        updateTextureFromFrame(frames.get(frameIndex).image());
    }

    public static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (frames.isEmpty()) {
            tryLoadSelectedGif();
        }
        renderHudInternal(context);
    }

    private static void renderHudInternal(DrawContext context) {
        if (!enabled || frames.isEmpty()) {
            return;
        }
        ensureTexture();
        if (texture == null) {
            return;
        }
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(drawX, drawY);
        matrices.scale(scale, scale);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, 0, 0, 0f, 0f, frameWidth, frameHeight, frameWidth, frameHeight);
        matrices.popMatrix();
    }

    public static void renderPreview(DrawContext context) {
        renderHudInternal(context);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggleEnabled() {
        enabled = !enabled;
        writeConfig();
    }

    public static float getX() {
        return x;
    }

    public static float getY() {
        return y;
    }

    public static float getScale() {
        return scale;
    }

    public static void setPosition(float newX, float newY) {
        x = newX;
        y = newY;
        writeConfig();
    }

    public static void nudgeX(float delta) {
        x += delta;
        writeConfig();
    }

    public static void nudgeY(float delta) {
        y += delta;
        writeConfig();
    }

    public static void nudgeScale(float delta) {
        scale = clamp(scale + delta, MIN_SCALE, MAX_SCALE);
        writeConfig();
    }

    public static boolean hasGif() {
        return selectedFileName != null && !selectedFileName.isBlank() && !frames.isEmpty();
    }

    public static String getSelectedFileName() {
        return selectedFileName;
    }

    public static Path getGifDir() {
        return GIF_DIR;
    }

    public static boolean selectGif(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        setGif(path);
        return hasGif();
    }

    public static void selectNextGif() {
        List<Path> gifs = listGifFiles();
        if (gifs.isEmpty()) {
            clearGif();
            return;
        }
        int idx = 0;
        if (selectedFileName != null) {
            for (int i = 0; i < gifs.size(); i++) {
                if (gifs.get(i).getFileName().toString().equalsIgnoreCase(selectedFileName)) {
                    idx = (i + 1) % gifs.size();
                    break;
                }
            }
        }
        setGif(gifs.get(idx));
    }

    public static void clearGif() {
        selectedFileName = null;
        clearFrames();
        writeConfig();
    }

    public static boolean isHovered(int mouseX, int mouseY) {
        int w = Math.max(1, Math.round(frameWidth * scale));
        int h = Math.max(1, Math.round(frameHeight * scale));
        int drawX = Math.round(x);
        int drawY = Math.round(y);
        return mouseX >= drawX && mouseX <= drawX + w && mouseY >= drawY && mouseY <= drawY + h;
    }

    public static int getDisplayWidth() {
        return Math.max(1, Math.round(frameWidth * scale));
    }

    public static int getDisplayHeight() {
        return Math.max(1, Math.round(frameHeight * scale));
    }

    private static void setGif(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        String nextName = path.getFileName().toString();
        if (nextName.equalsIgnoreCase(selectedFileName) && !frames.isEmpty()) {
            return;
        }
        clearFrames();
        selectedFileName = nextName;
        writeConfig();
        tryLoadSelectedGif();
    }

    private static void loadGif(Path path) {
        clearFrames();
        try (InputStream in = Files.newInputStream(path);
             ImageInputStream stream = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            reader.setInput(stream, false);
            int count = reader.getNumImages(true);
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                if (frame == null) {
                    continue;
                }
                int delayMs = readGifDelay(reader.getImageMetadata(i));
                NativeImage nativeFrame = toNativeImage(frame);
                if (i == 0) {
                    frameWidth = nativeFrame.getWidth();
                    frameHeight = nativeFrame.getHeight();
                } else if (nativeFrame.getWidth() != frameWidth || nativeFrame.getHeight() != frameHeight) {
                    NativeImage scaled = scaleImage(nativeFrame, frameWidth, frameHeight);
                    nativeFrame.close();
                    nativeFrame = scaled;
                }
                frames.add(new GifFrame(nativeFrame, delayMs));
            }
            reader.dispose();
        } catch (Exception ignored) {
            clearFrames();
        }
        frameIndex = 0;
        nextFrameTimeMs = 0L;
    }

    private static void ensureTexture() {
        if (texture != null) {
            return;
        }
        if (frames.isEmpty()) {
            return;
        }
        NativeImage copy = copyImage(frames.get(0).image());
        texture = new NativeImageBackedTexture(() -> "hat_gif_hud", copy);
        texture.setFilter(true, true);
        MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
    }

    private static void updateTextureFromFrame(NativeImage frame) {
        if (frame == null) {
            return;
        }
        if (texture == null || frame.getWidth() != frameWidth || frame.getHeight() != frameHeight) {
            if (texture != null) {
                texture.close();
                texture = null;
            }
            frameWidth = frame.getWidth();
            frameHeight = frame.getHeight();
            ensureTexture();
        }
        if (texture == null) {
            return;
        }
        NativeImage target = texture.getImage();
        if (target == null) {
            return;
        }
        copyInto(frame, target);
        texture.upload();
    }

    private static void clearFrames() {
        for (GifFrame frame : frames) {
            frame.image().close();
        }
        frames.clear();
        frameIndex = 0;
        nextFrameTimeMs = 0L;
        frameWidth = 0;
        frameHeight = 0;
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private static void tryLoadSelectedGif() {
        if (selectedFileName == null || selectedFileName.isBlank()) {
            return;
        }
        Path path = GIF_DIR.resolve(selectedFileName);
        if (!Files.isRegularFile(path)) {
            return;
        }
        loadGif(path);
    }

    private static List<Path> listGifFiles() {
        if (!ensureDirs()) {
            return List.of();
        }
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(GIF_DIR)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gif"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(entries::add);
        } catch (IOException ignored) {
        }
        return entries;
    }

    private static int readGifDelay(IIOMetadata metadata) {
        if (metadata == null) {
            return 100;
        }
        String format = metadata.getNativeMetadataFormatName();
        if (format == null) {
            return 100;
        }
        Node root = metadata.getAsTree(format);
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if ("GraphicControlExtension".equals(node.getNodeName())) {
                NamedNodeMap attrs = node.getAttributes();
                Node delay = attrs.getNamedItem("delayTime");
                if (delay != null) {
                    try {
                        int centiseconds = Integer.parseInt(delay.getNodeValue());
                        int ms = centiseconds * 10;
                        return ms <= 0 ? 100 : ms;
                    } catch (NumberFormatException ignored) {
                        return 100;
                    }
                }
            }
        }
        return 100;
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage out = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out.setColorArgb(x, y, image.getRGB(x, y));
            }
        }
        return out;
    }

    private static NativeImage scaleImage(NativeImage source, int targetW, int targetH) {
        NativeImage scaled = new NativeImage(targetW, targetH, true);
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        for (int y = 0; y < targetH; y++) {
            int srcY = y * srcH / targetH;
            for (int x = 0; x < targetW; x++) {
                int srcX = x * srcW / targetW;
                scaled.setColorArgb(x, y, source.getColorArgb(srcX, srcY));
            }
        }
        return scaled;
    }

    private static NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), true);
        copyInto(source, copy);
        return copy;
    }

    private static void copyInto(NativeImage src, NativeImage dst) {
        int width = Math.min(src.getWidth(), dst.getWidth());
        int height = Math.min(src.getHeight(), dst.getHeight());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                dst.setColorArgb(x, y, src.getColorArgb(x, y));
            }
        }
    }

    private static boolean ensureDirs() {
        try {
            Files.createDirectories(GIF_DIR);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void readConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }
        try {
            String raw = Files.readString(CONFIG_PATH);
            enabled = readBoolean(raw, "enabled", enabled);
            x = readFloat(raw, "x", x);
            y = readFloat(raw, "y", y);
            scale = clamp(readFloat(raw, "scale", scale), MIN_SCALE, MAX_SCALE);
            String file = readString(raw, "file");
            if (file != null && !file.isBlank()) {
                selectedFileName = file;
            }
        } catch (IOException ignored) {
        }
    }

    private static void writeConfig() {
        String file = selectedFileName == null ? "" : selectedFileName;
        String json = "{\"enabled\":" + enabled
            + ",\"x\":" + x
            + ",\"y\":" + y
            + ",\"scale\":" + scale
            + ",\"file\":\"" + file.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException ignored) {
        }
    }

    private static boolean readBoolean(String raw, String key, boolean fallback) {
        int idx = raw.indexOf("\"" + key + "\"");
        if (idx == -1) {
            return fallback;
        }
        int colon = raw.indexOf(':', idx);
        if (colon == -1) {
            return fallback;
        }
        String tail = raw.substring(colon + 1).trim();
        if (tail.startsWith("true")) {
            return true;
        }
        if (tail.startsWith("false")) {
            return false;
        }
        return fallback;
    }

    private static float readFloat(String raw, String key, float fallback) {
        int idx = raw.indexOf("\"" + key + "\"");
        if (idx == -1) {
            return fallback;
        }
        int colon = raw.indexOf(':', idx);
        if (colon == -1) {
            return fallback;
        }
        int end = raw.indexOf(',', colon);
        if (end == -1) {
            end = raw.indexOf('}', colon);
        }
        if (end == -1) {
            return fallback;
        }
        String value = raw.substring(colon + 1, end).trim();
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String readString(String raw, String key) {
        int idx = raw.indexOf("\"" + key + "\"");
        if (idx == -1) {
            return null;
        }
        int colon = raw.indexOf(':', idx);
        if (colon == -1) {
            return null;
        }
        int quoteStart = raw.indexOf('"', colon + 1);
        if (quoteStart == -1) {
            return null;
        }
        int quoteEnd = raw.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) {
            return null;
        }
        return raw.substring(quoteStart + 1, quoteEnd);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record GifFrame(NativeImage image, int delayMs) {
    }
}
