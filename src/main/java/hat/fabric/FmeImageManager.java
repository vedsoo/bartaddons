package hat.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.texture.NativeImage;

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
import java.util.stream.Stream;

public final class FmeImageManager {
    private static final Path IMAGE_DIR = FabricLoader.getInstance().getConfigDir()
        .resolve("fme")
        .resolve("images");
    private static final Map<String, Path> RESOLVED_PATH_CACHE = new HashMap<>();

    private FmeImageManager() {
    }

    public static Path getImageDir() {
        return IMAGE_DIR;
    }

    public static List<Path> listImages() {
        if (!ensureDirs()) {
            return List.of();
        }
        return listImageFiles(IMAGE_DIR);
    }

    public static Path resolveImagePath(String fileName) {
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
        Path primary = IMAGE_DIR.resolve(fileName);
        if (Files.isRegularFile(primary)) {
            RESOLVED_PATH_CACHE.put(fileName, primary);
            return primary;
        }
        return null;
    }

    public static NativeImage readImage(Path path) throws IOException {
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

    private static boolean ensureDirs() {
        try {
            Files.createDirectories(IMAGE_DIR);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
