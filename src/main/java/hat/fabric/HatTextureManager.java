package hat.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class HatTextureManager {
    private static final Identifier TEXTURE_ID = Identifier.of("hat", "dynamic_hat_texture");
    private static final Path TEXTURE_DIR = FabricLoader.getInstance().getConfigDir()
        .resolve("fme")
        .resolve("custom_textures");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("hat-texture.json");
    private static NativeImageBackedTexture texture;
    private static boolean available;
    private static Path loadedPath;
    private static String selectedFileName;
    private static final Map<Path, Identifier> CUSTOM_TEXTURE_IDS = new HashMap<>();
    private static final Map<Path, NativeImageBackedTexture> CUSTOM_TEXTURES = new HashMap<>();
    private static final Map<String, Path> RESOLVED_PATH_CACHE = new HashMap<>();

    private HatTextureManager() {
    }

    public static void reload() {
        available = false;
        loadedPath = null;
        if (texture != null) {
            texture.close();
            texture = null;
        }

        if (!ensureDirs()) {
            return;
        }

        selectedFileName = readSelectedFileName();

        Optional<Path> pick = findPreferredTexture(selectedFileName);
        if (pick.isEmpty()) {
            return;
        }

        Path path = pick.get();
        try {
            String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".gif")) {
                return;
            }
            NativeImage image = readImage(path);
            if (image == null) {
                return;
            }
            NativeImage finalImage = normalizeTexture(image);
            texture = new NativeImageBackedTexture(() -> "hat_custom_texture", finalImage);
            texture.setFilter(true, true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(TEXTURE_ID, texture);
            available = true;
            loadedPath = path;
            selectedFileName = path.getFileName().toString();
            writeSelectedFileName(selectedFileName);
        } catch (IOException ignored) {
            available = false;
        }
    }

    public static boolean hasTexture() {
        return available;
    }

    public static Identifier getTextureId() {
        return TEXTURE_ID;
    }

    public static Path getTexturePath() {
        return loadedPath != null ? loadedPath : TEXTURE_DIR;
    }

    public static Path getTextureDir() {
        return TEXTURE_DIR;
    }

    public static List<Path> listTextures() {
        if (!ensureDirs()) {
            return List.of();
        }
        List<Path> combined = new ArrayList<>();
        combined.addAll(listImageFiles(TEXTURE_DIR));
        return combined;
    }

    public static Path getSelectedTexturePath() {
        return loadedPath;
    }

    public static void selectTexture(Path path) {
        if (path == null) {
            return;
        }
        selectedFileName = path.getFileName().toString();
        writeSelectedFileName(selectedFileName);
        reload();
    }

    public static Identifier getOrLoadTexture(Path path) {
        if (path == null) {
            return null;
        }
        Identifier existing = CUSTOM_TEXTURE_IDS.get(path);
        if (existing != null) {
            return existing;
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
            Identifier id = Identifier.of("hat", "custom/" + sanitizeTexturePath(path.getFileName().toString()));
            if (lower.endsWith(".gif")) {
                return null;
            }
            NativeImage image = readImage(path);
            if (image == null) {
                return null;
            }
            NativeImage finalImage = normalizeTexture(image);
            NativeImageBackedTexture newTexture = new NativeImageBackedTexture(() -> "hat_custom/" + path.getFileName(), finalImage);
            newTexture.setFilter(true, true);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, newTexture);
            CUSTOM_TEXTURE_IDS.put(path, id);
            CUSTOM_TEXTURES.put(path, newTexture);
            return id;
        } catch (IOException ignored) {
            return null;
        }
    }

    public static Path resolveTexturePath(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path cached = RESOLVED_PATH_CACHE.get(fileName);
        if (cached != null) {
            if (Files.isRegularFile(cached)) {
                return cached;
            }
            RESOLVED_PATH_CACHE.remove(fileName);
        }
        Path primary = TEXTURE_DIR.resolve(fileName);
        if (Files.isRegularFile(primary)) {
            RESOLVED_PATH_CACHE.put(fileName, primary);
            return primary;
        }
        return null;
    }

    public static void tickAnimations(long nowMs) {
        // GIF textures are not supported.
    }

    private static Optional<Path> findPreferredTexture(String preferredFileName) {
        if (preferredFileName != null && !preferredFileName.isBlank()) {
            Path primary = TEXTURE_DIR.resolve(preferredFileName);
            if (Files.isRegularFile(primary)) {
                return Optional.of(primary);
            }
        }
        Optional<Path> primaryPick = findFirstImage(TEXTURE_DIR);
        if (primaryPick.isPresent()) {
            return primaryPick;
        }
        return Optional.empty();
    }

    private static List<Path> listImageFiles(Path dir) {
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .forEach(entries::add);
        } catch (IOException ignored) {
        }
        return entries;
    }

    private static String readSelectedFileName() {
        if (!Files.exists(CONFIG_PATH)) {
            return null;
        }
        try {
            String raw = Files.readString(CONFIG_PATH);
            int idx = raw.indexOf("\"selected\"");
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
            String value = raw.substring(quoteStart + 1, quoteEnd);
            return value.isBlank() ? null : value;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void writeSelectedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String json = "{\"selected\":\"" + fileName.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        try {
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException ignored) {
        }
    }

    private static Optional<Path> findFirstImage(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static boolean ensureDirs() {
        try {
            Files.createDirectories(TEXTURE_DIR);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static NativeImage normalizeTexture(NativeImage image) {
        NativeImage cropped = centerCropToSquare(image);
        int size = cropped.getWidth();
        int target = size;
        if (target < 16) {
            target = 16;
        } else if (target > 512) {
            target = 512;
        }
        if (target == size) {
            return cropped;
        }
        NativeImage scaled = new NativeImage(target, target, true);
        for (int y = 0; y < target; y++) {
            int srcY = y * size / target;
            for (int x = 0; x < target; x++) {
                int srcX = x * size / target;
                scaled.setColorArgb(x, y, cropped.getColorArgb(srcX, srcY));
            }
        }
        cropped.close();
        return scaled;
    }

    private static NativeImage normalizeTextureToSize(NativeImage image, int targetSize) {
        NativeImage cropped = centerCropToSquare(image);
        if (cropped.getWidth() == targetSize && cropped.getHeight() == targetSize) {
            return cropped;
        }
        NativeImage scaled = new NativeImage(targetSize, targetSize, true);
        int size = cropped.getWidth();
        for (int y = 0; y < targetSize; y++) {
            int srcY = y * size / targetSize;
            for (int x = 0; x < targetSize; x++) {
                int srcX = x * size / targetSize;
                scaled.setColorArgb(x, y, cropped.getColorArgb(srcX, srcY));
            }
        }
        cropped.close();
        return scaled;
    }

    private static NativeImage centerCropToSquare(NativeImage image) {
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        int size = Math.min(srcW, srcH);
        if (srcW == size && srcH == size) {
            return image;
        }
        int x0 = (srcW - size) / 2;
        int y0 = (srcH - size) / 2;
        NativeImage cropped = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            int srcY = y0 + y;
            for (int x = 0; x < size; x++) {
                int srcX = x0 + x;
                cropped.setColorArgb(x, y, image.getColorArgb(srcX, srcY));
            }
        }
        image.close();
        return cropped;
    }

    private static NativeImage readImage(Path path) throws IOException {
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            try (InputStream in = Files.newInputStream(path)) {
                return NativeImage.read(in);
            }
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            return null;
        }
        NativeImage out = new NativeImage(image.getWidth(), image.getHeight(), true);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setColorArgb(x, y, image.getRGB(x, y));
            }
        }
        return out;
    }

    private static String sanitizeTexturePath(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT).replace('\\', '/');
        cleaned = cleaned.replaceAll("[^a-z0-9/._-]", "_");
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            return "texture";
        }
        return cleaned;
    }
}
