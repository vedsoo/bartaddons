package hat.fabric;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FmeManager {
    private static final Path SAVE_PATH = FabricLoader.getInstance().getConfigDir().resolve("hat-fme.json");
    private static final Path STATE_PATH = FabricLoader.getInstance().getConfigDir().resolve("hat-fme-state.json");
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir()
        .resolve("fme")
        .resolve("config");
    private static final Path CUSTOM_THEME_DIR = CONFIG_DIR.resolve("custom_themes");
    private static Path currentSavePath = SAVE_PATH;
    private static final Map<Long, Block> POSITION_REPLACEMENTS = new HashMap<>();
    private static final Map<Long, String> POSITION_CUSTOM_TEXTURES = new HashMap<>();
    private static final Map<Long, Block> POSITION_CUSTOM_TEXTURE_SOURCES = new HashMap<>();
    private static final Map<Long, Block> POSITION_CUSTOM_TEXTURE_SOUND_SOURCES = new HashMap<>();
    private static final Map<Long, Integer> POSITION_ROTATIONS = new HashMap<>();
    private static final Map<Long, BlockState> POSITION_STATE_OVERRIDES = new HashMap<>();
    private static final Map<String, ItemAppearanceMapping> ITEM_APPEARANCE_MAPPINGS = new HashMap<>();
    private static final Map<String, ItemNameOverrideMapping> ITEM_NAME_OVERRIDES = new HashMap<>();
    private static final Map<Long, Block> LAST_REPLACE_UNDO = new HashMap<>();
    private static boolean lastReplaceWasCustom = false;
    private static final Map<RemapKey, BlockState> REMAP_STATE_CACHE = new HashMap<>();
    private static final Set<Long> AIR_GHOST_POSITIONS = new HashSet<>();
    private static final Set<Identifier> FAVORITES = new HashSet<>();
    private static final Set<String> CUSTOM_TEXTURE_FAVORITES = new HashSet<>();
    private static boolean batching = false;
    private static boolean batchChanged = false;
    private static boolean pendingSave = false;
    private static int saveInTicks = -1;
    private static boolean enabled = false;
    private static boolean editMode = false;
    private static Block selectedSource = net.minecraft.block.Blocks.STONE;
    private static SelectedSourceType selectedSourceType = SelectedSourceType.BLOCK;
    private static String selectedCustomTexture;
    private static int selectedRotation = 0;
    private static int openScreenInTicks = -1;
    private static ScreenMode openScreenMode = ScreenMode.DEFAULT;
    private static String pendingItemEditKey;
    private static ItemStack pendingItemEditStack = ItemStack.EMPTY;
    private static ItemStack pendingItemNameStack = ItemStack.EMPTY;
    private static int pendingItemNameSlot = -1;
    private static int guiPanelColor = 0xFFF5F5F5;
    private static int guiBorderColor = 0xFF000000;
    private static int guiTextColor = 0xFF000000;
    private static int guiAccentTextColor = 0xFF111111;
    private static int selectionBoxColor = 0x66000000;
    private static ThemeAnimation tabAnimation = ThemeAnimation.PULSE;
    private static ThemeAnimation selectionAnimation = ThemeAnimation.PULSE;
    private static float panelCornerRadius = 10.0f;
    private static float elementCornerRadius = 6.0f;
    private static boolean customThemeActive = false;
    private static String customThemeName;
    private static CustomTheme customTheme;
    private static LayoutPreset layoutPreset = LayoutPreset.BLOOM;
    private static float itemFirstPersonX = 0.0f;
    private static float itemFirstPersonY = 0.0f;
    private static float itemFirstPersonZ = 0.0f;
    private static float itemFirstPersonRotX = 0.0f;
    private static float itemFirstPersonRotY = 0.0f;
    private static float itemFirstPersonRotZ = 0.0f;
    private static float itemFirstPersonScale = 1.0f;
    private static float itemThirdPersonX = 0.0f;
    private static float itemThirdPersonY = 0.0f;
    private static float itemThirdPersonZ = 0.0f;
    private static float itemThirdPersonRotX = 0.0f;
    private static float itemThirdPersonRotY = 0.0f;
    private static float itemThirdPersonRotZ = 0.0f;
    private static float itemThirdPersonScale = 1.0f;
    private static float guiScale = 1.5f;
    private static float guiBlockBrightness = 2.0f;
    private static boolean autoSaveEnabled = true;
    private static boolean skipRemapCacheClear = false;
    private static int offsetX = 0;
    private static int offsetY = 0;
    private static int offsetZ = 0;
    private static long renderDataVersion = 0L;
    private static final int SAVE_DEBOUNCE_TICKS = 4;

    private FmeManager() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isEditMode() {
        return editMode;
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        recordEnabledState(enabled);
        save();
        refreshWorld(null);
        return enabled;
    }

    public static boolean toggleEditMode() {
        editMode = !editMode;
        recordEditModeState(editMode);
        save();
        refreshWorld(null);
        return editMode;
    }

    public static void setOffset(BlockPos pos) {
        if (pos == null) {
            return;
        }
        offsetX = pos.getX();
        offsetY = pos.getY();
        offsetZ = pos.getZ();
        save();
        refreshWorld(null);
    }

    public static void setOffset(int x, int y, int z) {
        offsetX = x;
        offsetY = y;
        offsetZ = z;
        save();
        refreshWorld(null);
    }

    public static void clearOffset() {
        offsetX = 0;
        offsetY = 0;
        offsetZ = 0;
        save();
        refreshWorld(null);
    }

    public static BlockPos getOffset() {
        return new BlockPos(offsetX, offsetY, offsetZ);
    }

    public static void setSelectedSource(Block source) {
        if (source != null) {
            selectedSource = source;
            selectedSourceType = SelectedSourceType.BLOCK;
            selectedRotation = 0;
            save();
        }
    }

    public static Block getSelectedSource() {
        return selectedSource;
    }

    public static void selectCustomTexture(Path texturePath) {
        if (texturePath == null) {
            return;
        }
        selectedCustomTexture = texturePath.getFileName().toString();
        selectedSourceType = SelectedSourceType.CUSTOM_TEXTURE;
        selectedRotation = 0;
        save();
    }

    public static String getSelectedCustomTextureName() {
        return selectedCustomTexture;
    }

    public static SelectedSourceType getSelectedSourceType() {
        return selectedSourceType;
    }

    public static int rotateSelectedSource() {
        selectedRotation = (selectedRotation + 1) % 4;
        save();
        return selectedRotation;
    }

    public static int getSelectedRotation() {
        return selectedRotation;
    }

    public static void setSelectedRotation(int rotation) {
        selectedRotation = Math.floorMod(rotation, 4);
        save();
    }

    public static void selectCustomTextureName(String fileName, int rotation) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        selectedCustomTexture = fileName;
        selectedSourceType = SelectedSourceType.CUSTOM_TEXTURE;
        selectedRotation = Math.floorMod(rotation, 4);
        save();
    }

    public static boolean applyReplacement(BlockPos pos, Block target) {
        if (pos == null || target == null || selectedSource == null) {
            return false;
        }
        long key = toConfigKey(pos);
        if (selectedSourceType == SelectedSourceType.CUSTOM_TEXTURE && selectedCustomTexture != null) {
            POSITION_CUSTOM_TEXTURES.put(key, selectedCustomTexture);
            POSITION_CUSTOM_TEXTURE_SOURCES.put(key, target);
            POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.put(key, selectedSource);
            POSITION_REPLACEMENTS.remove(key);
            POSITION_ROTATIONS.put(key, selectedRotation);
            POSITION_STATE_OVERRIDES.remove(key);
            if (target.getDefaultState().isAir()) {
                AIR_GHOST_POSITIONS.add(key);
            } else {
                AIR_GHOST_POSITIONS.remove(key);
            }
            markChanged(pos);
            return true;
        }
        if (target == selectedSource) {
            POSITION_REPLACEMENTS.remove(key);
            POSITION_ROTATIONS.remove(key);
            AIR_GHOST_POSITIONS.remove(key);
            POSITION_CUSTOM_TEXTURES.remove(key);
            POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
            POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
            POSITION_STATE_OVERRIDES.remove(key);
            markChanged(pos);
            return true;
        }
        POSITION_REPLACEMENTS.put(key, selectedSource);
        POSITION_CUSTOM_TEXTURES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
        POSITION_ROTATIONS.put(key, selectedRotation);
        POSITION_STATE_OVERRIDES.remove(key);
        if (target.getDefaultState().isAir()) {
            AIR_GHOST_POSITIONS.add(key);
        } else {
            AIR_GHOST_POSITIONS.remove(key);
        }
        markChanged(pos);
        return true;
    }

    public static boolean applyReplacementDirect(BlockPos pos, Block target, Block mapped, int rotation) {
        if (pos == null || target == null || mapped == null) {
            return false;
        }
        long key = toConfigKey(pos);
        if (target == mapped) {
            POSITION_REPLACEMENTS.remove(key);
            POSITION_ROTATIONS.remove(key);
            AIR_GHOST_POSITIONS.remove(key);
            POSITION_CUSTOM_TEXTURES.remove(key);
            POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
            POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
            POSITION_STATE_OVERRIDES.remove(key);
            markChanged(pos);
            return true;
        }
        POSITION_REPLACEMENTS.put(key, mapped);
        POSITION_CUSTOM_TEXTURES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
        POSITION_ROTATIONS.put(key, Math.floorMod(rotation, 4));
        POSITION_STATE_OVERRIDES.remove(key);
        if (target.getDefaultState().isAir()) {
            AIR_GHOST_POSITIONS.add(key);
        } else {
            AIR_GHOST_POSITIONS.remove(key);
        }
        markChanged(pos);
        return true;
    }

    public static boolean clearReplacement(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long key = toConfigKey(pos);
        boolean removed = POSITION_REPLACEMENTS.remove(key) != null;
        boolean removedCustom = POSITION_CUSTOM_TEXTURES.remove(key) != null;
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
        AIR_GHOST_POSITIONS.remove(key);
        POSITION_STATE_OVERRIDES.remove(key);
        if (removed || removedCustom) {
            POSITION_ROTATIONS.remove(key);
            markChanged(pos);
            return true;
        }
        return false;
    }

    public static boolean clearReplacementKey(long key) {
        boolean removed = POSITION_REPLACEMENTS.remove(key) != null;
        boolean removedCustom = POSITION_CUSTOM_TEXTURES.remove(key) != null;
        POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
        AIR_GHOST_POSITIONS.remove(key);
        POSITION_STATE_OVERRIDES.remove(key);
        if (removed || removedCustom) {
            POSITION_ROTATIONS.remove(key);
            markChanged(null);
            return true;
        }
        return false;
    }

    public static long configKeyFor(BlockPos pos) {
        return toConfigKey(pos);
    }

    public static boolean rotateReplacement(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long key = toConfigKey(pos);
        if (!POSITION_REPLACEMENTS.containsKey(key) && !POSITION_CUSTOM_TEXTURES.containsKey(key)) {
            return false;
        }
        int next = (POSITION_ROTATIONS.getOrDefault(key, 0) + 1) % 4;
        POSITION_ROTATIONS.put(key, next);
        markChanged(pos);
        return true;
    }

    public static int replaceMappedSource(Block from, Block to) {
        if (from == null || to == null || from == to || POSITION_REPLACEMENTS.isEmpty()) {
            return 0;
        }

        LAST_REPLACE_UNDO.clear();
        lastReplaceWasCustom = false;
        int changed = 0;
        for (Map.Entry<Long, Block> entry : POSITION_REPLACEMENTS.entrySet()) {
            if (entry.getValue() == from) {
                LAST_REPLACE_UNDO.put(entry.getKey(), from);
                entry.setValue(to);
                changed++;
            }
        }

        if (changed > 0) {
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        }
        return changed;
    }

    public static int replaceMappedSourceWithCustomTexture(Block from, String textureFile) {
        if (from == null || textureFile == null || textureFile.isBlank() || POSITION_REPLACEMENTS.isEmpty()) {
            return 0;
        }

        LAST_REPLACE_UNDO.clear();
        lastReplaceWasCustom = true;
        int changed = 0;
        java.util.ArrayList<Long> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<Long, Block> entry : POSITION_REPLACEMENTS.entrySet()) {
            if (entry.getValue() == from) {
                long key = entry.getKey();
                LAST_REPLACE_UNDO.put(key, from);
                POSITION_CUSTOM_TEXTURES.put(key, textureFile);
                POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
                POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.put(key, from);
                toRemove.add(key);
                changed++;
            }
        }

        if (changed > 0) {
            for (Long key : toRemove) {
                POSITION_REPLACEMENTS.remove(key);
            }
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        } else {
            lastReplaceWasCustom = false;
        }
        return changed;
    }

    public static int undoLastReplace() {
        if (LAST_REPLACE_UNDO.isEmpty()) {
            return 0;
        }

        int restored = 0;
        for (Map.Entry<Long, Block> entry : LAST_REPLACE_UNDO.entrySet()) {
            long key = entry.getKey();
            if (POSITION_REPLACEMENTS.containsKey(key)) {
                POSITION_REPLACEMENTS.put(key, entry.getValue());
                restored++;
                continue;
            }
            if (lastReplaceWasCustom && POSITION_CUSTOM_TEXTURES.containsKey(key)) {
                POSITION_CUSTOM_TEXTURES.remove(key);
                POSITION_CUSTOM_TEXTURE_SOURCES.remove(key);
                POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.remove(key);
                POSITION_REPLACEMENTS.put(key, entry.getValue());
                restored++;
            }
        }
        LAST_REPLACE_UNDO.clear();
        lastReplaceWasCustom = false;

        if (restored > 0) {
            maybeClearRemapCache();
            save();
            refreshWorld(null);
        }
        return restored;
    }

    public static BlockState remapStateAt(BlockState original, BlockPos pos) {
        if (!enabled) {
            return original;
        }
        long key = toConfigKey(pos);
        if (POSITION_CUSTOM_TEXTURES.containsKey(key)) {
            Block source = POSITION_CUSTOM_TEXTURE_SOURCES.get(key);
            if (source != null && original.getBlock() != source) {
                return original;
            }
            if (original.isAir() && !AIR_GHOST_POSITIONS.contains(key)) {
                return original;
            }
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        BlockState override = POSITION_STATE_OVERRIDES.get(key);
        if (override != null) {
            if (original.isAir() && !AIR_GHOST_POSITIONS.contains(key)) {
                return original;
            }
            return override;
        }
        Block mapped = POSITION_REPLACEMENTS.get(key);
        if (mapped == null) {
            return original;
        }
        if (original.isAir() && !AIR_GHOST_POSITIONS.contains(key)) {
            return original;
        }
        BlockState out = mapped.getDefaultState();
        if (!original.isAir()) {
            for (var property : original.getProperties()) {
                if (out.contains(property)) {
                    out = copyProperty(out, original, property);
                }
            }
        }

        int turns = POSITION_ROTATIONS.getOrDefault(key, 0);
        RemapKey remapKey = new RemapKey(original, mapped, turns);
        BlockState cached = REMAP_STATE_CACHE.get(remapKey);
        if (cached != null) {
            return cached;
        }

        if (turns != 0 && out.contains(Properties.HORIZONTAL_FACING)) {
            Direction facing = out.get(Properties.HORIZONTAL_FACING);
            for (int i = 0; i < turns; i++) {
                facing = facing.rotateYClockwise();
            }
            out = out.with(Properties.HORIZONTAL_FACING, facing);
        } else if (turns != 0 && out.contains(Properties.FACING)) {
            Direction facing = out.get(Properties.FACING);
            if (facing.getAxis().isHorizontal()) {
                for (int i = 0; i < turns; i++) {
                    facing = facing.rotateYClockwise();
                }
                out = out.with(Properties.FACING, facing);
            }
        } else if (turns != 0 && out.contains(Properties.HORIZONTAL_AXIS)) {
            net.minecraft.util.math.Direction.Axis axis = out.get(Properties.HORIZONTAL_AXIS);
            if (turns % 2 != 0) {
                axis = axis == net.minecraft.util.math.Direction.Axis.X
                    ? net.minecraft.util.math.Direction.Axis.Z
                    : net.minecraft.util.math.Direction.Axis.X;
                out = out.with(Properties.HORIZONTAL_AXIS, axis);
            }
        } else if (turns != 0 && out.contains(Properties.AXIS)) {
            net.minecraft.util.math.Direction.Axis axis = out.get(Properties.AXIS);
            if (turns % 2 != 0 && axis != net.minecraft.util.math.Direction.Axis.Y) {
                axis = axis == net.minecraft.util.math.Direction.Axis.X
                    ? net.minecraft.util.math.Direction.Axis.Z
                    : net.minecraft.util.math.Direction.Axis.X;
                out = out.with(Properties.AXIS, axis);
            }
        }
        REMAP_STATE_CACHE.put(remapKey, out);
        return out;
    }

    public static void toggleFavorite(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        if (!FAVORITES.add(id)) {
            FAVORITES.remove(id);
        }
        save();
    }

    public static boolean isFavorite(Block block) {
        return FAVORITES.contains(Registries.BLOCK.getId(block));
    }

    public static Set<Identifier> favoritesView() {
        return Collections.unmodifiableSet(FAVORITES);
    }

    public static void toggleCustomTextureFavorite(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String key = fileName.trim();
        if (!CUSTOM_TEXTURE_FAVORITES.add(key)) {
            CUSTOM_TEXTURE_FAVORITES.remove(key);
        }
        save();
    }

    public static boolean isCustomTextureFavorite(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        return CUSTOM_TEXTURE_FAVORITES.contains(fileName.trim());
    }

    public static Set<String> customTextureFavoritesView() {
        return Collections.unmodifiableSet(CUSTOM_TEXTURE_FAVORITES);
    }

    public static List<String> listConfigNames() {
        ensureConfigDir();
        if (!Files.exists(CONFIG_DIR)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(CONFIG_DIR)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".json"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    public static List<String> listCustomThemeNames() {
        ensureCustomThemeDir();
        if (!Files.exists(CUSTOM_THEME_DIR)) {
            return Collections.emptyList();
        }
        try (Stream<Path> files = Files.list(CUSTOM_THEME_DIR)) {
            return files
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .map(path -> {
                    String name = path.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    return dot > 0 ? name.substring(0, dot) : name;
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        } catch (IOException ignored) {
            return Collections.emptyList();
        }
    }

    public static CustomTheme loadCustomTheme(String name) {
        Path path = resolveCustomThemePath(name);
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            return customThemeFromJson(root, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean saveCustomTheme(CustomTheme theme) {
        if (theme == null || theme.name == null || theme.name.isBlank()) {
            return false;
        }
        ensureCustomThemeDir();
        Path path = resolveCustomThemePath(theme.name);
        if (path == null) {
            return false;
        }
        try {
            JsonObject root = customThemeToJson(theme);
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean deleteCustomTheme(String name) {
        Path path = resolveCustomThemePath(name);
        if (path == null || !Files.exists(path)) {
            return false;
        }
        try {
            Files.deleteIfExists(path);
            if (name != null && name.equalsIgnoreCase(customThemeName)) {
                customThemeActive = false;
                customThemeName = null;
                customTheme = null;
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean applyCustomTheme(String name) {
        CustomTheme theme = loadCustomTheme(name);
        if (theme == null) {
            return false;
        }
        applyCustomTheme(theme);
        return true;
    }

    public static void applyCustomTheme(CustomTheme theme) {
        if (theme == null) {
            return;
        }
        guiPanelColor = theme.panelColor;
        guiBorderColor = theme.borderColor;
        guiTextColor = theme.textColor;
        guiAccentTextColor = theme.accentTextColor;
        selectionBoxColor = theme.selectionColor;
        tabAnimation = theme.tabAnimation != null ? theme.tabAnimation : ThemeAnimation.PULSE;
        selectionAnimation = theme.selectionAnimation != null ? theme.selectionAnimation : ThemeAnimation.PULSE;
        panelCornerRadius = theme.panelRadius > 0 ? theme.panelRadius : 10.0f;
        elementCornerRadius = theme.elementRadius > 0 ? theme.elementRadius : 6.0f;
        customThemeActive = true;
        customThemeName = theme.name;
        customTheme = theme;
        recordCustomThemeState(theme.name);
        save();
    }

    public static String itemAppearanceKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        String baseName = baseItemDisplayName(stack).getString();
        return itemId + "|" + baseName;
    }

    public static String itemNameOverrideKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        String baseName = baseItemDisplayName(stack).getString();
        ComponentChanges componentChanges = sanitizedNameComponentChanges(stack);
        if (componentChanges.isEmpty()) {
            return itemId + "|" + baseName;
        }
        return itemId + "|" + baseName + "|" + componentChanges;
    }

    public static Text baseItemDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Text.literal("Empty");
        }
        Text customName = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (customName != null) {
            return customName;
        }
        Text itemName = stack.get(DataComponentTypes.ITEM_NAME);
        if (itemName != null) {
            return itemName;
        }
        return stack.getItem().getName(stack);
    }

    public static String itemAppearanceLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "Empty";
        }
        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemDisplayName(stack).getString() + " (" + itemId + ")";
    }

    public static void openItemScreen(ItemStack stack) {
        pendingItemEditStack = stack == null ? ItemStack.EMPTY : stack.copy();
        pendingItemEditKey = itemAppearanceKey(stack);
        openScreenMode = ScreenMode.ITEM;
        openScreenInTicks = 2;
    }

    public static void openItemNameScreen(ItemStack stack, int slot) {
        pendingItemNameStack = stack == null ? ItemStack.EMPTY : stack.copy();
        pendingItemNameSlot = slot;
        openScreenMode = ScreenMode.ITEM_NAME;
        openScreenInTicks = 2;
    }

    public static boolean hasPendingItemEdit() {
        return pendingItemEditKey != null && !pendingItemEditKey.isBlank() && !pendingItemEditStack.isEmpty();
    }

    public static ItemStack getPendingItemEditStack() {
        return pendingItemEditStack.copy();
    }

    public static String getPendingItemEditLabel() {
        if (!hasPendingItemEdit()) {
            return "No held item selected";
        }
        return itemAppearanceLabel(pendingItemEditStack);
    }

    public static Map<String, ItemAppearanceMapping> itemAppearanceMappingsView() {
        return Collections.unmodifiableMap(ITEM_APPEARANCE_MAPPINGS);
    }

    public static ItemNameOverrideMapping itemNameOverrideAt(ItemStack stack) {
        String key = itemNameOverrideKey(stack);
        if (key == null || stack == null || stack.isEmpty()) {
            return null;
        }
        ItemNameOverrideMapping exact = ITEM_NAME_OVERRIDES.get(key);
        if (exact != null) {
            return exact;
        }

        String sourceItemId = Registries.ITEM.getId(stack.getItem()).toString();
        String baseName = baseItemDisplayName(stack).getString();
        for (ItemNameOverrideMapping mapping : ITEM_NAME_OVERRIDES.values()) {
            if (mapping != null && sourceItemId.equals(mapping.sourceItemId) && baseName.equals(mapping.baseName)) {
                return mapping;
            }
        }
        return null;
    }

    public static Text itemDisplayName(ItemStack stack) {
        ItemNameOverrideMapping override = itemNameOverrideAt(stack);
        if (override != null) {
            return override.toText();
        }
        return baseItemDisplayName(stack);
    }

    public static Text itemNameOverrideText(ItemStack stack) {
        ItemNameOverrideMapping override = itemNameOverrideAt(stack);
        return override == null ? null : override.toText();
    }

    public static boolean setItemNameOverride(ItemStack stack, String name, int color) {
        if (stack == null || stack.isEmpty() || name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return clearItemNameOverride(stack);
        }
        String key = itemNameOverrideKey(stack);
        if (key == null) {
            return false;
        }
        String sourceItemId = Registries.ITEM.getId(stack.getItem()).toString();
        String baseName = baseItemDisplayName(stack).getString();
        removeItemNameOverridesFor(sourceItemId, baseName);
        ITEM_NAME_OVERRIDES.put(key, new ItemNameOverrideMapping(sourceItemId, baseName, trimmed, color & 0xFFFFFF));
        markChanged(null);
        return true;
    }

    public static boolean clearItemNameOverride(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String sourceItemId = Registries.ITEM.getId(stack.getItem()).toString();
        String baseName = baseItemDisplayName(stack).getString();
        boolean removed = removeItemNameOverridesFor(sourceItemId, baseName) > 0;
        if (removed) {
            markChanged(null);
        }
        return removed;
    }

    public static ItemAppearanceMapping itemAppearanceAt(ItemStack stack) {
        String key = itemAppearanceKey(stack);
        if (key == null || stack == null || stack.isEmpty()) {
            return null;
        }
        ItemAppearanceMapping exact = ITEM_APPEARANCE_MAPPINGS.get(key);
        if (exact != null) {
            return exact;
        }

        String sourceItemId = Registries.ITEM.getId(stack.getItem()).toString();
        String baseName = baseItemDisplayName(stack).getString();
        for (ItemAppearanceMapping mapping : ITEM_APPEARANCE_MAPPINGS.values()) {
            if (mapping == null || !sourceItemId.equals(mapping.sourceItemId)) {
                continue;
            }
            if (mapping.baseName == null || mapping.baseName.isBlank() || baseName.equals(mapping.baseName)) {
                return mapping;
            }
        }
        return null;
    }

    public static boolean clearPendingItemAppearance() {
        if (!hasPendingItemEdit()) {
            return false;
        }
        boolean removed = removeItemAppearanceMappingsFor(pendingItemSourceItemId()) > 0;
        if (removed) {
            markChanged(null);
        }
        return removed;
    }

    public static boolean applyPendingItemAppearanceBlock(Block block) {
        if (block == null || !hasPendingItemEdit()) {
            return false;
        }
        String pendingKey = pendingItemAppearanceKey();
        if (pendingKey == null) {
            return false;
        }
        removeItemAppearanceMappingsFor(pendingItemSourceItemId());
        ITEM_APPEARANCE_MAPPINGS.put(
            pendingKey,
            new ItemAppearanceMapping(
                Registries.ITEM.getId(pendingItemEditStack.getItem()).toString(),
                baseItemDisplayName(pendingItemEditStack).getString(),
                ItemAppearanceTargetType.BLOCK,
                Registries.BLOCK.getId(block).toString()
            )
        );
        markChanged(null);
        return true;
    }

    public static boolean applyPendingItemAppearanceItem(Item item) {
        if (item == null || !hasPendingItemEdit()) {
            return false;
        }
        String pendingKey = pendingItemAppearanceKey();
        if (pendingKey == null) {
            return false;
        }
        removeItemAppearanceMappingsFor(pendingItemSourceItemId());
        ITEM_APPEARANCE_MAPPINGS.put(
            pendingKey,
            new ItemAppearanceMapping(
                Registries.ITEM.getId(pendingItemEditStack.getItem()).toString(),
                baseItemDisplayName(pendingItemEditStack).getString(),
                ItemAppearanceTargetType.ITEM,
                Registries.ITEM.getId(item).toString()
            )
        );
        markChanged(null);
        return true;
    }

    public static boolean applyPendingItemAppearanceCustomTexture(String fileName) {
        if (fileName == null || fileName.isBlank() || !hasPendingItemEdit()) {
            return false;
        }
        String pendingKey = pendingItemAppearanceKey();
        if (pendingKey == null) {
            return false;
        }
        removeItemAppearanceMappingsFor(pendingItemSourceItemId());
        ITEM_APPEARANCE_MAPPINGS.put(
            pendingKey,
            new ItemAppearanceMapping(
                Registries.ITEM.getId(pendingItemEditStack.getItem()).toString(),
                baseItemDisplayName(pendingItemEditStack).getString(),
                ItemAppearanceTargetType.CUSTOM_TEXTURE,
                fileName.trim()
            )
        );
        markChanged(null);
        return true;
    }

    public static boolean rebindItemAppearance(ItemStack oldStack, ItemStack newStack) {
        if (oldStack == null || oldStack.isEmpty() || newStack == null || newStack.isEmpty()) {
            return false;
        }
        String oldKey = itemAppearanceKey(oldStack);
        String newKey = itemAppearanceKey(newStack);
        if (oldKey == null || newKey == null) {
            return false;
        }
        if (oldKey.equals(newKey)) {
            if (pendingItemEditKey != null && pendingItemEditKey.equals(oldKey)) {
                pendingItemEditKey = newKey;
                pendingItemEditStack = newStack.copy();
            }
            return false;
        }
        ItemAppearanceMapping mapping = ITEM_APPEARANCE_MAPPINGS.remove(oldKey);
        if (mapping == null) {
            if (pendingItemEditKey != null && pendingItemEditKey.equals(oldKey)) {
                pendingItemEditKey = newKey;
                pendingItemEditStack = newStack.copy();
            }
            return false;
        }
        ITEM_APPEARANCE_MAPPINGS.put(
            newKey,
            new ItemAppearanceMapping(
                Registries.ITEM.getId(newStack.getItem()).toString(),
                baseItemDisplayName(newStack).getString(),
                mapping.targetType,
                mapping.targetValue
            )
        );
        if (pendingItemEditKey != null && pendingItemEditKey.equals(oldKey)) {
            pendingItemEditKey = newKey;
            pendingItemEditStack = newStack.copy();
        }
        markChanged(null);
        return true;
    }

    private static String pendingItemSourceItemId() {
        if (!hasPendingItemEdit()) {
            return null;
        }
        return Registries.ITEM.getId(pendingItemEditStack.getItem()).toString();
    }

    private static String pendingItemAppearanceKey() {
        if (!hasPendingItemEdit()) {
            return null;
        }
        String currentKey = itemAppearanceKey(pendingItemEditStack);
        if (currentKey != null && !currentKey.isBlank()) {
            pendingItemEditKey = currentKey;
            return currentKey;
        }
        return pendingItemEditKey;
    }

    private static int removeItemAppearanceMappingsFor(String sourceItemId) {
        if (sourceItemId == null || sourceItemId.isBlank() || ITEM_APPEARANCE_MAPPINGS.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<String, ItemAppearanceMapping>> it = ITEM_APPEARANCE_MAPPINGS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ItemAppearanceMapping> entry = it.next();
            ItemAppearanceMapping mapping = entry.getValue();
            if (mapping != null && sourceItemId.equals(mapping.sourceItemId)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    private static int removeItemNameOverridesFor(String sourceItemId, String baseName) {
        if (sourceItemId == null || sourceItemId.isBlank() || baseName == null || ITEM_NAME_OVERRIDES.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator<Map.Entry<String, ItemNameOverrideMapping>> it = ITEM_NAME_OVERRIDES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ItemNameOverrideMapping> entry = it.next();
            ItemNameOverrideMapping mapping = entry.getValue();
            if (mapping != null && sourceItemId.equals(mapping.sourceItemId) && baseName.equals(mapping.baseName)) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    private static ComponentChanges sanitizedNameComponentChanges(ItemStack stack) {
        return stack.getComponentChanges().withRemovedIf(type ->
            type == DataComponentTypes.CUSTOM_NAME
                || type == DataComponentTypes.ITEM_NAME
                || type == DataComponentTypes.LORE
                || type == DataComponentTypes.TOOLTIP_DISPLAY
        );
    }

    public static ItemStack createItemAppearanceRenderStack(ItemStack original) {
        ItemAppearanceMapping mapping = itemAppearanceAt(original);
        if (mapping == null) {
            return ItemStack.EMPTY;
        }
        if (mapping.targetType == ItemAppearanceTargetType.ITEM) {
            Identifier id = Identifier.tryParse(mapping.targetValue);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            Item item = Registries.ITEM.get(id);
            if (item == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, Math.max(1, original.getCount()));
        }
        if (mapping.targetType == ItemAppearanceTargetType.BLOCK) {
            Identifier id = Identifier.tryParse(mapping.targetValue);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            Block block = Registries.BLOCK.get(id);
            if (block == null) {
                return ItemStack.EMPTY;
            }
            Item item = block.asItem();
            if (item == null || item == net.minecraft.item.Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, Math.max(1, original.getCount()));
        }
        return ItemStack.EMPTY;
    }

    public static CustomTheme snapshotCurrentTheme(String name) {
        CustomTheme theme = new CustomTheme();
        theme.name = name;
        theme.panelColor = guiPanelColor;
        theme.borderColor = guiBorderColor;
        theme.textColor = guiTextColor;
        theme.accentTextColor = guiAccentTextColor;
        theme.selectionColor = selectionBoxColor;
        theme.gradientStart = guiPanelColor;
        theme.gradientEnd = guiPanelColor;
        theme.flatTheme = true;
        theme.panelRadius = panelCornerRadius;
        theme.elementRadius = elementCornerRadius;
        theme.tabAnimation = tabAnimation;
        theme.selectionAnimation = selectionAnimation;
        return theme;
    }

    public static Path getConfigDir() {
        return CONFIG_DIR;
    }

    public static String getCurrentConfigName() {
        Path normalized = currentSavePath == null ? SAVE_PATH : currentSavePath;
        Path fileName = normalized.getFileName();
        return fileName == null ? normalized.toString() : fileName.toString();
    }

    public static Map<Block, Block> replacementsView() {
        return Collections.emptyMap();
    }

    public static Map<Long, Block> positionReplacementsView() {
        return Collections.unmodifiableMap(POSITION_REPLACEMENTS);
    }

    public static Map<Long, String> customTextureReplacementsView() {
        return Collections.unmodifiableMap(POSITION_CUSTOM_TEXTURES);
    }

    public static Map<Long, Block> customTextureSourcesView() {
        return Collections.unmodifiableMap(POSITION_CUSTOM_TEXTURE_SOURCES);
    }

    public static Map<Long, Block> customTextureSoundSourcesView() {
        return Collections.unmodifiableMap(POSITION_CUSTOM_TEXTURE_SOUND_SOURCES);
    }

    public static Set<Long> airGhostPositionsView() {
        return Collections.unmodifiableSet(AIR_GHOST_POSITIONS);
    }

    public static Block customTextureSourceAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return POSITION_CUSTOM_TEXTURE_SOURCES.get(toConfigKey(pos));
    }

    public static Block customTextureSoundSourceAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.get(toConfigKey(pos));
    }

    public static boolean isAirGhostPosition(BlockPos pos) {
        return pos != null && AIR_GHOST_POSITIONS.contains(toConfigKey(pos));
    }

    public static BlockState solidClientStateAt(BlockPos pos) {
        if (pos == null || !enabled) {
            return null;
        }

        long key = toConfigKey(pos);
        if (!AIR_GHOST_POSITIONS.contains(key)) {
            return null;
        }

        BlockState override = POSITION_STATE_OVERRIDES.get(key);
        if (override != null && !override.isAir()) {
            return override;
        }

        Block mapped = POSITION_REPLACEMENTS.get(key);
        if (mapped != null) {
            BlockState mappedState = remapStateAt(net.minecraft.block.Blocks.AIR.getDefaultState(), pos);
            return mappedState.isAir() ? mapped.getDefaultState() : mappedState;
        }

        Block source = POSITION_CUSTOM_TEXTURE_SOURCES.get(key);
        if (source != null && !source.getDefaultState().isAir()) {
            return source.getDefaultState();
        }

        return null;
    }

    public static long renderDataVersion() {
        return renderDataVersion;
    }

    public static int rotationAt(BlockPos pos) {
        if (pos == null) {
            return 0;
        }
        return POSITION_ROTATIONS.getOrDefault(toConfigKey(pos), 0);
    }

    public static Block mappedBlockAt(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        return POSITION_REPLACEMENTS.get(toConfigKey(pos));
    }

    public static void openScreen() {
        openScreenMode = ScreenMode.DEFAULT;
        openScreenInTicks = 2;
    }

    public static void openGuiSettings() {
        openScreenMode = ScreenMode.GUI_SETTINGS;
        openScreenInTicks = 2;
    }

    public static void clientTick(MinecraftClient client) {
        if (saveInTicks >= 0) {
            if (saveInTicks == 0) {
                flushPendingSave();
            } else {
                saveInTicks--;
            }
        }
        if (openScreenInTicks < 0) {
            return;
        }
        if (openScreenInTicks > 0) {
            openScreenInTicks--;
            return;
        }
        openScreenInTicks = -1;
        switch (openScreenMode) {
            case ITEM_NAME -> client.setScreen(new ItemNameEditorScreen(client.currentScreen, pendingItemNameStack.copy(), pendingItemNameSlot));
            default -> client.setScreen(new FmeScreen(openScreenMode == ScreenMode.GUI_SETTINGS, openScreenMode == ScreenMode.ITEM));
        }
    }

    public static void sendClientMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            MutableText prefix = Text.literal("[FME] ").formatted(Formatting.LIGHT_PURPLE);
            MutableText body = Text.literal(message).formatted(Formatting.WHITE);
            client.player.sendMessage(prefix.append(body), false);
        }
    }

    public static void load() {
        ensureConfigDir();
        loadSettingsState();
        String stateCustomTheme = readCustomThemeFromState();
        if (stateCustomTheme != null && applyCustomTheme(stateCustomTheme)) {
            // Custom theme applied from state.
        } else {
            Theme stateTheme = readThemeFromState();
            if (stateTheme != null) {
                applyThemeInternal(stateTheme);
            }
        }
        Path lastConfig = readLastConfigPath();
        if (lastConfig != null && Files.exists(lastConfig)) {
            boolean loaded = loadFromPath(lastConfig, false, false);
            if (loaded) {
                currentSavePath = lastConfig;
                return;
            }
        }
        loadFromPath(SAVE_PATH, false, false);
        currentSavePath = SAVE_PATH;
    }

    public static boolean loadDefaultConfig() {
        ensureConfigDir();
        boolean loaded = loadFromPath(SAVE_PATH, true, true);
        if (loaded) {
            currentSavePath = SAVE_PATH;
            recordLastConfigPath(SAVE_PATH);
        }
        return loaded;
    }

    public static boolean loadConfig(String nameOrPath) {
        ensureConfigDir();
        Path path = resolveConfigPath(nameOrPath, false);
        if (path == null) {
            return false;
        }
        boolean loaded = loadFromPath(path, true, true);
        if (loaded) {
            currentSavePath = path;
            recordLastConfigPath(path);
        }
        return loaded;
    }

    public static boolean saveConfig(String name) {
        ensureConfigDir();
        Path path = resolveConfigPath(name, true);
        if (path == null) {
            return false;
        }
        boolean saved = saveToPath(path);
        if (saved) {
            currentSavePath = path;
            recordLastConfigPath(path);
        }
        return saved;
    }

    public static boolean saveCurrentConfig() {
        ensureConfigDir();
        return saveToPath(currentSavePath);
    }

    public static boolean addConfig(String name) {
        ensureConfigDir();
        Path path = resolveConfigPath(name, true);
        if (path == null) {
            return false;
        }
        boolean saved = saveToPath(path);
        if (saved) {
            currentSavePath = path;
            recordLastConfigPath(path);
        }
        return saved;
    }


    private static boolean loadFromPath(Path path, boolean resetState, boolean refreshWorld) {
        clearState(resetState);
        if (path == null || !Files.exists(path)) {
            return false;
        }

        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            boolean loaded = applyConfig(root);
            if (refreshWorld && loaded) {
                refreshWorld(null);
                reloadChunks();
            }
            return loaded;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean applyConfig(JsonObject root) {
        boolean loadedLegacy = false;
        boolean loadedFloors = false;
        if (root.has("selectedSource")) {
            Identifier id = Identifier.tryParse(root.get("selectedSource").getAsString());
            if (id != null) {
                Block block = Registries.BLOCK.get(id);
                if (block != null) {
                    selectedSource = block;
                }
            }
        }
        if (root.has("selectedSourceType")) {
            String sourceTypeRaw = root.get("selectedSourceType").getAsString();
            if ("CUSTOM_TEXTURE".equalsIgnoreCase(sourceTypeRaw)) {
                selectedSourceType = SelectedSourceType.CUSTOM_TEXTURE;
            } else {
                selectedSourceType = SelectedSourceType.BLOCK;
            }
        }
        if (root.has("selectedCustomTexture")) {
            String customTextureRaw = root.get("selectedCustomTexture").getAsString();
            if (!customTextureRaw.isBlank()) {
                selectedCustomTexture = customTextureRaw;
            }
        }
        if (root.has("selectedRotation")) {
            selectedRotation = Math.floorMod(root.get("selectedRotation").getAsInt(), 4);
        }
        if (root.has("customTheme")) {
            String customName = root.get("customTheme").getAsString();
            if (customName != null && !customName.isBlank()) {
                applyCustomTheme(customName);
            }
        }
        if (!customThemeActive && root.has("theme")) {
            try {
                Theme theme = Theme.valueOf(root.get("theme").getAsString().trim().toUpperCase(Locale.ROOT));
                applyThemeInternal(theme);
            } catch (Exception ignored) {
            }
        }
        guiScale = 1.5f;
        if (root.has("blockBrightness")) {
            try {
                float value = (float) root.get("blockBrightness").getAsDouble();
                guiBlockBrightness = MathHelper.clamp(value, 0.25f, 4.0f);
            } catch (Exception ignored) {
                guiBlockBrightness = 2.0f;
            }
        } else {
            guiBlockBrightness = 2.0f;
        }
        itemFirstPersonX = readFloat(root, "itemFirstPersonX", 0.0f);
        itemFirstPersonY = readFloat(root, "itemFirstPersonY", 0.0f);
        itemFirstPersonZ = readFloat(root, "itemFirstPersonZ", 0.0f);
        itemFirstPersonRotX = readFloat(root, "itemFirstPersonRotX", 0.0f);
        itemFirstPersonRotY = readFloat(root, "itemFirstPersonRotY", 0.0f);
        itemFirstPersonRotZ = readFloat(root, "itemFirstPersonRotZ", 0.0f);
        itemFirstPersonScale = readFloat(root, "itemFirstPersonScale", 1.0f);
        itemThirdPersonX = readFloat(root, "itemThirdPersonX", 0.0f);
        itemThirdPersonY = readFloat(root, "itemThirdPersonY", 0.0f);
        itemThirdPersonZ = readFloat(root, "itemThirdPersonZ", 0.0f);
        itemThirdPersonRotX = readFloat(root, "itemThirdPersonRotX", 0.0f);
        itemThirdPersonRotY = readFloat(root, "itemThirdPersonRotY", 0.0f);
        itemThirdPersonRotZ = readFloat(root, "itemThirdPersonRotZ", 0.0f);
        itemThirdPersonScale = readFloat(root, "itemThirdPersonScale", 1.0f);
        migrateLegacyItemTransformDefaultsIfNeeded();
        if (root.has("offsetX")) {
            offsetX = root.get("offsetX").getAsInt();
        }
        if (root.has("offsetY")) {
            offsetY = root.get("offsetY").getAsInt();
        }
        if (root.has("offsetZ")) {
            offsetZ = root.get("offsetZ").getAsInt();
        }
        if (root.has("favorites") && root.get("favorites").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("favorites");
            for (JsonElement e : arr) {
                Identifier id = Identifier.tryParse(e.getAsString());
                if (id != null) {
                    FAVORITES.add(id);
                }
            }
        }
        if (root.has("favoriteCustomTextures") && root.get("favoriteCustomTextures").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("favoriteCustomTextures");
            for (JsonElement e : arr) {
                String name = e.getAsString();
                if (name != null && !name.isBlank()) {
                    CUSTOM_TEXTURE_FAVORITES.add(name);
                }
            }
        }
        if (root.has("itemAppearances") && root.get("itemAppearances").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("itemAppearances");
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject obj = e.getAsJsonObject();
                String key = readString(obj, "key", null);
                String sourceItemId = readString(obj, "sourceItem", null);
                String baseName = readString(obj, "baseName", null);
                String typeRaw = readString(obj, "targetType", null);
                String targetValue = readString(obj, "target", null);
                if (key == null || typeRaw == null || targetValue == null) {
                    continue;
                }
                try {
                    ItemAppearanceTargetType type = ItemAppearanceTargetType.valueOf(typeRaw.trim().toUpperCase(Locale.ROOT));
                    ITEM_APPEARANCE_MAPPINGS.put(key, new ItemAppearanceMapping(sourceItemId, baseName, type, targetValue));
                } catch (Exception ignored) {
                }
            }
        }
        if (root.has("itemNameOverrides") && root.get("itemNameOverrides").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("itemNameOverrides");
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject obj = e.getAsJsonObject();
                String key = readString(obj, "key", null);
                String sourceItemId = readString(obj, "sourceItem", null);
                String baseName = readString(obj, "baseName", null);
                String customName = readString(obj, "name", null);
                if (key == null || sourceItemId == null || baseName == null || customName == null) {
                    continue;
                }
                int color = 0xFFFFFF;
                if (obj.has("color")) {
                    try {
                        color = obj.get("color").getAsInt() & 0xFFFFFF;
                    } catch (Exception ignored) {
                        color = 0xFFFFFF;
                    }
                }
                ITEM_NAME_OVERRIDES.put(key, new ItemNameOverrideMapping(sourceItemId, baseName, customName, color));
            }
        }
        if (root.has("replacements")) {
            JsonElement replacementsEl = root.get("replacements");
            if (replacementsEl.isJsonArray()) {
                JsonArray arr = replacementsEl.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = e.getAsJsonObject();
                    Long pos = parsePosFromObject(obj);
                    if (pos == null) {
                        continue;
                    }
                    Block source = parseBlockFromObject(obj);
                    if (source == null) {
                        continue;
                    }
                    POSITION_REPLACEMENTS.put(pos, source);
                    POSITION_ROTATIONS.put(pos, parseRotationFromObject(obj));
                }
            } else if (replacementsEl.isJsonObject()) {
                JsonObject obj = replacementsEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    Long pos = parsePosFromString(entry.getKey());
                    if (pos == null) {
                        continue;
                    }
                    JsonElement value = entry.getValue();
                    Block source = null;
                    int rotation = 0;
                    if (value != null && value.isJsonPrimitive()) {
                        String raw = value.getAsString();
                        source = parseBlockFromString(raw);
                    } else if (value != null && value.isJsonObject()) {
                        JsonObject valueObj = value.getAsJsonObject();
                        source = parseBlockFromObject(valueObj);
                        rotation = parseRotationFromObject(valueObj);
                    }
                    if (source == null) {
                        continue;
                    }
                    POSITION_REPLACEMENTS.put(pos, source);
                    POSITION_ROTATIONS.put(pos, rotation);
                }
            }
        }
        if (root.has("customTextures")) {
            JsonElement customEl = root.get("customTextures");
            if (customEl.isJsonArray()) {
                JsonArray arr = customEl.getAsJsonArray();
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = e.getAsJsonObject();
                    Long pos = parsePosFromObject(obj);
                    if (pos == null) {
                        continue;
                    }
                    String file = parseCustomTextureFile(obj);
                    if (file == null) {
                        continue;
                    }
                    POSITION_CUSTOM_TEXTURES.put(pos, file);
                    Block source = parseBlockFromObject(obj);
                    if (source != null) {
                        POSITION_CUSTOM_TEXTURE_SOURCES.put(pos, source);
                    }
                    Block soundSource = parseBlock(obj, "soundSource");
                    if (soundSource != null) {
                        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.put(pos, soundSource);
                    }
                    POSITION_ROTATIONS.put(pos, parseRotationFromObject(obj));
                }
            } else if (customEl.isJsonObject()) {
                JsonObject obj = customEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    Long pos = parsePosFromString(entry.getKey());
                    if (pos == null) {
                        continue;
                    }
                    JsonElement value = entry.getValue();
                    String file = null;
                    Block source = null;
                    Block soundSource = null;
                    int rotation = 0;
                    if (value != null && value.isJsonPrimitive()) {
                        file = value.getAsString();
                    } else if (value != null && value.isJsonObject()) {
                        JsonObject valueObj = value.getAsJsonObject();
                        file = parseCustomTextureFile(valueObj);
                        source = parseBlockFromObject(valueObj);
                        soundSource = parseBlock(valueObj, "soundSource");
                        rotation = parseRotationFromObject(valueObj);
                    }
                    if (file == null || file.isBlank()) {
                        continue;
                    }
                    POSITION_CUSTOM_TEXTURES.put(pos, file);
                    if (source != null) {
                        POSITION_CUSTOM_TEXTURE_SOURCES.put(pos, source);
                    }
                    if (soundSource != null) {
                        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.put(pos, soundSource);
                    }
                    POSITION_ROTATIONS.put(pos, rotation);
                }
            }
        }
        if (root.has("airGhosts")) {
            JsonElement airEl = root.get("airGhosts");
            if (airEl.isJsonArray()) {
                JsonArray arr = airEl.getAsJsonArray();
                for (JsonElement e : arr) {
                    Long pos = parsePosElement(e);
                    if (pos != null) {
                        AIR_GHOST_POSITIONS.add(pos);
                    }
                }
            } else if (airEl.isJsonObject()) {
                JsonObject obj = airEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    Long pos = parsePosFromString(entry.getKey());
                    if (pos != null) {
                        AIR_GHOST_POSITIONS.add(pos);
                    }
                }
            }
        }
        if (root.has("airGhosts") && root.get("airGhosts").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("airGhosts");
            for (JsonElement e : arr) {
                AIR_GHOST_POSITIONS.add(e.getAsLong());
            }
        }

        if (POSITION_REPLACEMENTS.isEmpty()
            && POSITION_CUSTOM_TEXTURES.isEmpty()
            && root.has("minecraft:overworld")
            && root.get("minecraft:overworld").isJsonObject()) {
            JsonObject overworld = root.getAsJsonObject("minecraft:overworld");
            for (Map.Entry<String, JsonElement> entry : overworld.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) {
                    continue;
                }
                String raw = value.getAsString();
                if (raw == null || raw.isBlank() || raw.startsWith("!")) {
                    continue;
                }
                String idRaw = raw.split("\\[", 2)[0];
                Identifier id = Identifier.tryParse(idRaw);
                if (id == null) {
                    continue;
                }
                Block source = Registries.BLOCK.get(id);
                if (source == null) {
                    continue;
                }
                BlockPos pos = parseLegacyPos(key);
                if (pos == null) {
                    continue;
                }
                POSITION_REPLACEMENTS.put(pos.asLong(), source);
                POSITION_ROTATIONS.put(pos.asLong(), 0);
                loadedLegacy = true;
            }
        }
        if (POSITION_REPLACEMENTS.isEmpty() && POSITION_CUSTOM_TEXTURES.isEmpty()) {
            loadedFloors = loadFloorsConfig(root);
        }
        int totalEntries = POSITION_REPLACEMENTS.size() + POSITION_CUSTOM_TEXTURES.size();
        skipRemapCacheClear = loadedLegacy || loadedFloors || totalEntries >= 5000;
        autoSaveEnabled = true;
        if (loadedLegacy) {
            sendClientMessage("Loaded legacy config. Auto-save remains enabled.");
        } else if (loadedFloors) {
            sendClientMessage("Loaded floors config. Auto-save remains enabled.");
        } else if (totalEntries >= 5000) {
            sendClientMessage("Large config loaded (" + totalEntries + " entries). Auto-save remains enabled.");
        }
        return true;
    }

    private static void save() {
        saveToPath(currentSavePath);
    }

    public static void beginBatch() {
        batching = true;
        batchChanged = false;
    }

    public static void endBatch() {
        if (!batching) {
            return;
        }
        batching = false;
        if (batchChanged) {
            maybeClearRemapCache();
            scheduleSave();
            refreshWorld(null);
        }
    }

    private static void markChanged(BlockPos pos) {
        bumpRenderDataVersion();
        if (batching) {
            batchChanged = true;
            return;
        }
        maybeClearRemapCache();
        scheduleSave();
        refreshWorld(pos);
    }

    private static void scheduleSave() {
        pendingSave = true;
        saveInTicks = SAVE_DEBOUNCE_TICKS;
    }

    private static void flushPendingSave() {
        if (!pendingSave) {
            saveInTicks = -1;
            return;
        }
        pendingSave = false;
        saveInTicks = -1;
        save();
    }

    private static boolean saveToPath(Path path) {
        if (path == null) {
            return false;
        }
        try {
            JsonObject root = buildConfigJson();
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static JsonObject buildConfigJson() {
        JsonObject root = new JsonObject();
        root.addProperty("theme", getTheme().name());
        if (customThemeActive && customThemeName != null && !customThemeName.isBlank()) {
            root.addProperty("customTheme", customThemeName);
        }
        root.addProperty("selectedSource", Registries.BLOCK.getId(selectedSource).toString());
        root.addProperty("selectedSourceType", selectedSourceType.name());
        if (selectedCustomTexture != null) {
            root.addProperty("selectedCustomTexture", selectedCustomTexture);
        }
        root.addProperty("selectedRotation", selectedRotation);
        root.addProperty("blockBrightness", guiBlockBrightness);
        root.addProperty("itemFirstPersonX", itemFirstPersonX);
        root.addProperty("itemFirstPersonY", itemFirstPersonY);
        root.addProperty("itemFirstPersonZ", itemFirstPersonZ);
        root.addProperty("itemFirstPersonRotX", itemFirstPersonRotX);
        root.addProperty("itemFirstPersonRotY", itemFirstPersonRotY);
        root.addProperty("itemFirstPersonRotZ", itemFirstPersonRotZ);
        root.addProperty("itemFirstPersonScale", itemFirstPersonScale);
        root.addProperty("itemThirdPersonX", itemThirdPersonX);
        root.addProperty("itemThirdPersonY", itemThirdPersonY);
        root.addProperty("itemThirdPersonZ", itemThirdPersonZ);
        root.addProperty("itemThirdPersonRotX", itemThirdPersonRotX);
        root.addProperty("itemThirdPersonRotY", itemThirdPersonRotY);
        root.addProperty("itemThirdPersonRotZ", itemThirdPersonRotZ);
        root.addProperty("itemThirdPersonScale", itemThirdPersonScale);
        root.addProperty("offsetX", offsetX);
        root.addProperty("offsetY", offsetY);
        root.addProperty("offsetZ", offsetZ);

        JsonArray favorites = new JsonArray();
        for (Identifier id : FAVORITES) {
            favorites.add(id.toString());
        }
        root.add("favorites", favorites);

        JsonArray customFavorites = new JsonArray();
        for (String name : CUSTOM_TEXTURE_FAVORITES) {
            customFavorites.add(name);
        }
        root.add("favoriteCustomTextures", customFavorites);

        JsonArray itemAppearances = new JsonArray();
        for (Map.Entry<String, ItemAppearanceMapping> entry : ITEM_APPEARANCE_MAPPINGS.entrySet()) {
            ItemAppearanceMapping mapping = entry.getValue();
            if (mapping == null || mapping.targetType == null || mapping.targetValue == null || mapping.targetValue.isBlank()) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("key", entry.getKey());
            if (mapping.sourceItemId != null && !mapping.sourceItemId.isBlank()) {
                o.addProperty("sourceItem", mapping.sourceItemId);
            }
            if (mapping.baseName != null && !mapping.baseName.isBlank()) {
                o.addProperty("baseName", mapping.baseName);
            }
            o.addProperty("targetType", mapping.targetType.name());
            o.addProperty("target", mapping.targetValue);
            itemAppearances.add(o);
        }
        root.add("itemAppearances", itemAppearances);

        JsonArray itemNameOverrides = new JsonArray();
        for (Map.Entry<String, ItemNameOverrideMapping> entry : ITEM_NAME_OVERRIDES.entrySet()) {
            ItemNameOverrideMapping mapping = entry.getValue();
            if (mapping == null || mapping.sourceItemId == null || mapping.baseName == null || mapping.customName == null) {
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("key", entry.getKey());
            o.addProperty("sourceItem", mapping.sourceItemId);
            o.addProperty("baseName", mapping.baseName);
            o.addProperty("name", mapping.customName);
            o.addProperty("color", mapping.color & 0xFFFFFF);
            itemNameOverrides.add(o);
        }
        root.add("itemNameOverrides", itemNameOverrides);

        JsonArray replacements = new JsonArray();
        for (Map.Entry<Long, Block> entry : POSITION_REPLACEMENTS.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("pos", entry.getKey());
            o.addProperty("source", Registries.BLOCK.getId(entry.getValue()).toString());
            o.addProperty("rotation", POSITION_ROTATIONS.getOrDefault(entry.getKey(), 0));
            replacements.add(o);
        }
        root.add("replacements", replacements);

        JsonArray customTextures = new JsonArray();
        for (Map.Entry<Long, String> entry : POSITION_CUSTOM_TEXTURES.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("pos", entry.getKey());
            o.addProperty("file", entry.getValue());
            Block source = POSITION_CUSTOM_TEXTURE_SOURCES.get(entry.getKey());
            if (source != null) {
                o.addProperty("source", Registries.BLOCK.getId(source).toString());
            }
            Block soundSource = POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.get(entry.getKey());
            if (soundSource != null) {
                o.addProperty("soundSource", Registries.BLOCK.getId(soundSource).toString());
            }
            o.addProperty("rotation", POSITION_ROTATIONS.getOrDefault(entry.getKey(), 0));
            customTextures.add(o);
        }
        root.add("customTextures", customTextures);

        JsonArray airGhosts = new JsonArray();
        for (Long pos : AIR_GHOST_POSITIONS) {
            airGhosts.add(pos);
        }
        root.add("airGhosts", airGhosts);
        return root;
    }

    private static JsonObject customThemeToJson(CustomTheme theme) {
        JsonObject root = new JsonObject();
        root.addProperty("name", theme.name);
        root.addProperty("panelColor", theme.panelColor);
        root.addProperty("borderColor", theme.borderColor);
        root.addProperty("textColor", theme.textColor);
        root.addProperty("accentTextColor", theme.accentTextColor);
        root.addProperty("selectionColor", theme.selectionColor);
        root.addProperty("gradientStart", theme.gradientStart);
        root.addProperty("gradientEnd", theme.gradientEnd);
        root.addProperty("flatTheme", theme.flatTheme);
        root.addProperty("panelRadius", theme.panelRadius);
        root.addProperty("elementRadius", theme.elementRadius);
        root.addProperty("tabAnimation", (theme.tabAnimation == null ? ThemeAnimation.PULSE : theme.tabAnimation).name());
        root.addProperty("selectionAnimation", (theme.selectionAnimation == null ? ThemeAnimation.PULSE : theme.selectionAnimation).name());
        return root;
    }

    private static CustomTheme customThemeFromJson(JsonObject root, String fallbackName) {
        if (root == null) {
            return null;
        }
        CustomTheme theme = new CustomTheme();
        theme.name = readString(root, "name", fallbackName);
        theme.panelColor = readInt(root, "panelColor", 0xFF0F1B2A);
        theme.borderColor = readInt(root, "borderColor", 0xFF274B7A);
        theme.textColor = readInt(root, "textColor", 0xFFE3EDF7);
        theme.accentTextColor = readInt(root, "accentTextColor", 0xFF8ABEFF);
        theme.selectionColor = readInt(root, "selectionColor", 0x66406BA8);
        theme.gradientStart = readInt(root, "gradientStart", theme.panelColor);
        theme.gradientEnd = readInt(root, "gradientEnd", theme.panelColor);
        theme.flatTheme = readBoolean(root, "flatTheme", true);
        theme.panelRadius = readFloat(root, "panelRadius", 10.0f);
        theme.elementRadius = readFloat(root, "elementRadius", 6.0f);
        theme.tabAnimation = readAnimation(root, "tabAnimation", ThemeAnimation.PULSE);
        theme.selectionAnimation = readAnimation(root, "selectionAnimation", ThemeAnimation.PULSE);
        return theme;
    }

    private static String readString(JsonObject root, String key, String fallback) {
        if (root.has(key)) {
            String value = root.get(key).getAsString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        if (root.has(key)) {
            try {
                return root.get(key).getAsInt();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static float readFloat(JsonObject root, String key, float fallback) {
        if (root.has(key)) {
            try {
                return root.get(key).getAsFloat();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        if (root.has(key)) {
            try {
                return root.get(key).getAsBoolean();
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static ThemeAnimation readAnimation(JsonObject root, String key, ThemeAnimation fallback) {
        if (root.has(key)) {
            try {
                return ThemeAnimation.valueOf(root.get(key).getAsString().trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private static void clearState(boolean resetDefaults) {
        POSITION_REPLACEMENTS.clear();
        POSITION_CUSTOM_TEXTURES.clear();
        POSITION_CUSTOM_TEXTURE_SOURCES.clear();
        POSITION_CUSTOM_TEXTURE_SOUND_SOURCES.clear();
        POSITION_ROTATIONS.clear();
        POSITION_STATE_OVERRIDES.clear();
        ITEM_APPEARANCE_MAPPINGS.clear();
        ITEM_NAME_OVERRIDES.clear();
        AIR_GHOST_POSITIONS.clear();
        FAVORITES.clear();
        CUSTOM_TEXTURE_FAVORITES.clear();
        clearRemapCache();
        bumpRenderDataVersion();
        pendingItemEditKey = null;
        pendingItemEditStack = ItemStack.EMPTY;
        pendingItemNameStack = ItemStack.EMPTY;
        pendingItemNameSlot = -1;
        if (resetDefaults) {
            selectedSource = net.minecraft.block.Blocks.STONE;
            selectedSourceType = SelectedSourceType.BLOCK;
            selectedCustomTexture = null;
            selectedRotation = 0;
            guiPanelColor = 0xFFF5F5F5;
            guiBorderColor = 0xFF000000;
            guiTextColor = 0xFF000000;
            guiAccentTextColor = 0xFF111111;
            selectionBoxColor = 0x66000000;
            tabAnimation = ThemeAnimation.PULSE;
            selectionAnimation = ThemeAnimation.PULSE;
            panelCornerRadius = 10.0f;
            elementCornerRadius = 6.0f;
            customThemeActive = false;
            customThemeName = null;
            customTheme = null;
            itemFirstPersonX = 0.72f;
            itemFirstPersonY = -0.34f;
            itemFirstPersonZ = -0.18f;
            itemFirstPersonRotX = -64.0f;
            itemFirstPersonRotY = 32.0f;
            itemFirstPersonRotZ = 46.0f;
            itemFirstPersonScale = 1.18f;
            itemThirdPersonX = 0.02f;
            itemThirdPersonY = 0.14f;
            itemThirdPersonZ = 0.0f;
            itemThirdPersonRotX = -70.0f;
            itemThirdPersonRotY = 12.0f;
            itemThirdPersonRotZ = 52.0f;
            itemThirdPersonScale = 1.1f;
            guiScale = 1.5f;
            guiBlockBrightness = 2.0f;
            autoSaveEnabled = true;
            skipRemapCacheClear = false;
            currentSavePath = SAVE_PATH;
            offsetX = 0;
            offsetY = 0;
            offsetZ = 0;
            loadSettingsState();
        }
    }

    private static long toConfigKey(BlockPos pos) {
        if (pos == null) {
            return 0L;
        }
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return pos.asLong();
        }
        return BlockPos.asLong(pos.getX() - offsetX, pos.getY() - offsetY, pos.getZ() - offsetZ);
    }

    private static void maybeClearRemapCache() {
        if (skipRemapCacheClear) {
            return;
        }
        clearRemapCache();
    }

    private static Path resolveConfigPath(String nameOrPath, boolean forSave) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            return null;
        }
        String trimmed = nameOrPath.trim();
        Path path;
        boolean hasSeparator = trimmed.contains("/") || trimmed.contains("\\");
        boolean looksAbsolute = trimmed.contains(":") || trimmed.startsWith("/") || trimmed.startsWith("\\");
        if (looksAbsolute || hasSeparator) {
            path = Path.of(trimmed);
            if (!path.isAbsolute()) {
                path = CONFIG_DIR.resolve(path);
            }
        } else {
            String name = sanitizeConfigName(trimmed);
            if (name.isBlank()) {
                return null;
            }
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || dot == name.length() - 1) {
                name = name + ".json";
            }
            path = CONFIG_DIR.resolve(name);
        }
        if (forSave) {
            ensureConfigDir();
        }
        return path.normalize();
    }

    private static Path resolveCustomThemePath(String nameOrPath) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            return null;
        }
        String fileName = nameOrPath.trim();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            fileName = fileName + ".json";
        }
        ensureCustomThemeDir();
        return CUSTOM_THEME_DIR.resolve(fileName).normalize();
    }

    private static void recordLastConfigPath(Path path) {
        if (path == null) {
            return;
        }
        try {
            Path normalized = path.toAbsolutePath().normalize();
            JsonObject root = readStateRoot();
            root.addProperty("lastConfig", normalized.toString());
            Files.createDirectories(STATE_PATH.getParent());
            Files.writeString(STATE_PATH, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path readLastConfigPath() {
        if (!Files.exists(STATE_PATH)) {
            return null;
        }
        try {
            JsonObject root = readStateRoot();
            if (!root.has("lastConfig")) {
                return null;
            }
            String value = root.get("lastConfig").getAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            return Path.of(value).normalize();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void recordThemeState(Theme theme) {
        if (theme == null) {
            return;
        }
        try {
            JsonObject root = readStateRoot();
            root.addProperty("theme", theme.name());
            root.remove("customTheme");
            writeStateRoot(root);
        } catch (IOException ignored) {
        }
    }

    private static void recordCustomThemeState(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            JsonObject root = readStateRoot();
            root.addProperty("customTheme", name);
            writeStateRoot(root);
        } catch (IOException ignored) {
        }
    }

    private static void recordEnabledState(boolean value) {
        try {
            JsonObject root = readStateRoot();
            root.addProperty("enabled", value);
            writeStateRoot(root);
        } catch (IOException ignored) {
        }
    }

    private static void recordEditModeState(boolean value) {
        try {
            JsonObject root = readStateRoot();
            root.addProperty("editMode", value);
            writeStateRoot(root);
        } catch (IOException ignored) {
        }
    }

    private static Theme readThemeFromState() {
        try {
            JsonObject root = readStateRoot();
            if (!root.has("theme")) {
                return null;
            }
            String value = root.get("theme").getAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            return Theme.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readCustomThemeFromState() {
        try {
            JsonObject root = readStateRoot();
            if (!root.has("customTheme")) {
                return null;
            }
            String value = root.get("customTheme").getAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void loadSettingsState() {
        enabled = readBooleanFromState("enabled", false);
        editMode = readBooleanFromState("editMode", false);
        layoutPreset = readLayoutFromState();
    }

    private static boolean readBooleanFromState(String key, boolean fallback) {
        try {
            JsonObject root = readStateRoot();
            if (!root.has(key)) {
                return fallback;
            }
            return root.get(key).getAsBoolean();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static JsonObject readStateRoot() {
        if (!Files.exists(STATE_PATH)) {
            return new JsonObject();
        }
        try {
            String raw = Files.readString(STATE_PATH, StandardCharsets.UTF_8);
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Throwable ignored) {
            return new JsonObject();
        }
    }

    private static void writeStateRoot(JsonObject root) throws IOException {
        Files.createDirectories(STATE_PATH.getParent());
        Files.writeString(STATE_PATH, root.toString(), StandardCharsets.UTF_8);
    }

    private static void recordLayoutState(LayoutPreset preset) {
        if (preset == null) {
            return;
        }
        try {
            JsonObject root = readStateRoot();
            root.addProperty("layoutPreset", preset.name());
            writeStateRoot(root);
        } catch (IOException ignored) {
        }
    }

    private static LayoutPreset readLayoutFromState() {
        try {
            JsonObject root = readStateRoot();
            if (!root.has("layoutPreset")) {
                return LayoutPreset.BLOOM;
            }
            String value = root.get("layoutPreset").getAsString();
            if (value == null || value.isBlank()) {
                return LayoutPreset.BLOOM;
            }
            return LayoutPreset.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return LayoutPreset.BLOOM;
        }
    }

    private static void ensureConfigDir() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException ignored) {
        }
    }

    private static void ensureCustomThemeDir() {
        try {
            Files.createDirectories(CUSTOM_THEME_DIR);
        } catch (IOException ignored) {
        }
    }

    private static String sanitizeConfigName(String raw) {
        return raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private static BlockPos parseLegacyPos(String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int z = Integer.parseInt(parts[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parsePosFromString(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
        }
        BlockPos pos = parseLegacyPos(trimmed);
        return pos != null ? pos.asLong() : null;
    }

    private static Long parsePosElement(JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsLong();
            } catch (Exception ignored) {
            }
            return parsePosFromString(element.getAsString());
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() == 3) {
                Integer x = parseIntElement(arr.get(0));
                Integer y = parseIntElement(arr.get(1));
                Integer z = parseIntElement(arr.get(2));
                if (x != null && y != null && z != null) {
                    return new BlockPos(x, y, z).asLong();
                }
            }
            return null;
        }
        if (element.isJsonObject()) {
            return parsePosFromObject(element.getAsJsonObject());
        }
        return null;
    }

    private static Long parsePosFromObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("pos")) {
            return parsePosElement(obj.get("pos"));
        }
        if (obj.has("x") && obj.has("y") && obj.has("z")) {
            Integer x = parseIntElement(obj.get("x"));
            Integer y = parseIntElement(obj.get("y"));
            Integer z = parseIntElement(obj.get("z"));
            if (x != null && y != null && z != null) {
                return new BlockPos(x, y, z).asLong();
            }
        }
        return null;
    }

    private static Integer parseIntElement(JsonElement element) {
        if (element == null) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int parseRotationFromObject(JsonObject obj) {
        if (obj == null) {
            return 0;
        }
        if (obj.has("rotation")) {
            return parseRotationElement(obj.get("rotation"));
        }
        if (obj.has("rot")) {
            return parseRotationElement(obj.get("rot"));
        }
        if (obj.has("turns")) {
            return parseRotationElement(obj.get("turns"));
        }
        return 0;
    }

    private static int parseRotationElement(JsonElement element) {
        if (element == null) {
            return 0;
        }
        try {
            return Math.floorMod(element.getAsInt(), 4);
        } catch (Exception ignored) {
            try {
                return Math.floorMod(Integer.parseInt(element.getAsString()), 4);
            } catch (Exception ignored2) {
                return 0;
            }
        }
    }

    private static Block parseBlockFromObject(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("source")) {
            return parseBlockFromString(obj.get("source").getAsString());
        }
        if (obj.has("block")) {
            return parseBlockFromString(obj.get("block").getAsString());
        }
        if (obj.has("id")) {
            return parseBlockFromString(obj.get("id").getAsString());
        }
        if (obj.has("name")) {
            return parseBlockFromString(obj.get("name").getAsString());
        }
        return null;
    }

    private static Block parseBlock(JsonObject obj, String key) {
        if (obj == null || key == null || key.isBlank() || !obj.has(key)) {
            return null;
        }
        return parseBlockFromString(obj.get(key).getAsString());
    }

    private static Block parseBlockFromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String idRaw = trimmed.split("\\[", 2)[0];
        idRaw = remapLegacyBlockId(idRaw, trimmed);
        Identifier id = Identifier.tryParse(idRaw);
        if (id == null) {
            return null;
        }
        Block block = Registries.BLOCK.get(id);
        return block == null ? null : block;
    }

    private static String parseCustomTextureFile(JsonObject obj) {
        if (obj == null) {
            return null;
        }
        if (obj.has("file")) {
            return obj.get("file").getAsString();
        }
        if (obj.has("texture")) {
            return obj.get("texture").getAsString();
        }
        if (obj.has("name")) {
            return obj.get("name").getAsString();
        }
        return null;
    }

    private static boolean loadFloorsConfig(JsonObject root) {
        return loadFloorsConfig(root, false);
    }

    private static boolean loadFloorsConfig(JsonObject root, boolean skipAir) {
        boolean loadedAny = false;
        for (Map.Entry<String, JsonElement> floorEntry : root.entrySet()) {
            if (!floorEntry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject floor = floorEntry.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> blockEntry : floor.entrySet()) {
                String stateRaw = blockEntry.getKey();
                if (!blockEntry.getValue().isJsonArray()) {
                    continue;
                }
                String idRaw = stateRaw.split("\\[", 2)[0];
                idRaw = remapLegacyBlockId(idRaw, stateRaw);
                if (skipAir && "minecraft:air".equals(idRaw)) {
                    continue;
                }
                Identifier id = Identifier.tryParse(idRaw);
                if (id == null) {
                    continue;
                }
                Block source = Registries.BLOCK.get(id);
                if (source == null) {
                    continue;
                }
                int rotation = rotationFromState(stateRaw);
                JsonArray positions = blockEntry.getValue().getAsJsonArray();
                for (JsonElement posEl : positions) {
                    if (!posEl.isJsonPrimitive()) {
                        continue;
                    }
                    BlockPos pos = parseLegacyPos(posEl.getAsString());
                    if (pos == null) {
                        continue;
                    }
                    long key = toConfigKey(pos);
                    POSITION_REPLACEMENTS.put(key, source);
                    POSITION_ROTATIONS.put(key, rotation);
                    AIR_GHOST_POSITIONS.add(key);
                    loadedAny = true;
                }
            }
        }
        return loadedAny;
    }

    private static String remapLegacyBlockId(String idRaw, String stateRaw) {
        if (idRaw == null) {
            return null;
        }
        return switch (idRaw) {
            case "minecraft:stone" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "granite" -> "minecraft:granite";
                        case "granite_smooth" -> "minecraft:polished_granite";
                        case "diorite" -> "minecraft:diorite";
                        case "diorite_smooth" -> "minecraft:polished_diorite";
                        case "andesite" -> "minecraft:andesite";
                        case "andesite_smooth" -> "minecraft:polished_andesite";
                        default -> "minecraft:stone";
                    };
                }
                yield "minecraft:stone";
            }
            case "minecraft:grass" -> "minecraft:grass_block";
            case "minecraft:dirt" -> {
                if (stateRaw != null && stateRaw.contains("variant=podzol")) {
                    yield "minecraft:podzol";
                }
                if (stateRaw != null && stateRaw.contains("variant=coarse_dirt")) {
                    yield "minecraft:coarse_dirt";
                }
                yield "minecraft:dirt";
            }
            case "minecraft:log" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield "minecraft:" + variant + "_log";
                }
                yield "minecraft:oak_log";
            }
            case "minecraft:log2" -> "minecraft:dark_oak_log";
            case "minecraft:leaves" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield "minecraft:" + variant + "_leaves";
                }
                yield "minecraft:oak_leaves";
            }
            case "minecraft:leaves2" -> "minecraft:dark_oak_leaves";
            case "minecraft:tallgrass" -> {
                String type = parseStateProperty(stateRaw, "type");
                if ("fern".equals(type)) {
                    yield "minecraft:fern";
                }
                if ("dead_bush".equals(type)) {
                    yield "minecraft:dead_bush";
                }
                yield "minecraft:grass";
            }
            case "minecraft:red_flower" -> {
                String type = parseStateProperty(stateRaw, "type");
                if (type != null) {
                    yield switch (type) {
                        case "poppy", "rose" -> "minecraft:poppy";
                        case "blue_orchid" -> "minecraft:blue_orchid";
                        case "allium" -> "minecraft:allium";
                        case "houstonia", "azure_bluet" -> "minecraft:azure_bluet";
                        case "red_tulip" -> "minecraft:red_tulip";
                        case "orange_tulip" -> "minecraft:orange_tulip";
                        case "white_tulip" -> "minecraft:white_tulip";
                        case "pink_tulip" -> "minecraft:pink_tulip";
                        case "oxeye_daisy" -> "minecraft:oxeye_daisy";
                        default -> "minecraft:poppy";
                    };
                }
                yield "minecraft:poppy";
            }
            case "minecraft:yellow_flower" -> "minecraft:dandelion";
            case "minecraft:stonebrick" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "mossy" -> "minecraft:mossy_stone_bricks";
                        case "cracked" -> "minecraft:cracked_stone_bricks";
                        case "chiseled" -> "minecraft:chiseled_stone_bricks";
                        case "stonebrick" -> "minecraft:stone_bricks";
                        default -> "minecraft:stone_bricks";
                    };
                }
                yield "minecraft:stone_bricks";
            }
            case "minecraft:sandstone" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "chiseled" -> "minecraft:chiseled_sandstone";
                        case "smooth" -> "minecraft:smooth_sandstone";
                        default -> "minecraft:sandstone";
                    };
                }
                yield "minecraft:sandstone";
            }
            case "minecraft:red_sandstone" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "chiseled" -> "minecraft:chiseled_red_sandstone";
                        case "smooth" -> "minecraft:smooth_red_sandstone";
                        default -> "minecraft:red_sandstone";
                    };
                }
                yield "minecraft:red_sandstone";
            }
            case "minecraft:quartz_block" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "chiseled" -> "minecraft:chiseled_quartz_block";
                        case "lines" -> "minecraft:quartz_pillar";
                        default -> "minecraft:quartz_block";
                    };
                }
                yield "minecraft:quartz_block";
            }
            case "minecraft:stone_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "sandstone" -> "minecraft:sandstone_slab";
                        case "wood_old" -> "minecraft:oak_slab";
                        case "cobblestone" -> "minecraft:cobblestone_slab";
                        case "brick" -> "minecraft:brick_slab";
                        case "stone_brick" -> "minecraft:stone_brick_slab";
                        case "nether_brick" -> "minecraft:nether_brick_slab";
                        case "quartz" -> "minecraft:quartz_slab";
                        default -> "minecraft:stone_slab";
                    };
                }
                yield "minecraft:stone_slab";
            }
            case "minecraft:double_stone_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "sandstone" -> "minecraft:sandstone";
                        case "wood_old" -> "minecraft:oak_planks";
                        case "cobblestone" -> "minecraft:cobblestone";
                        case "brick" -> "minecraft:bricks";
                        case "stone_brick" -> "minecraft:stone_bricks";
                        case "nether_brick" -> "minecraft:nether_bricks";
                        case "quartz" -> "minecraft:quartz_block";
                        default -> "minecraft:smooth_stone";
                    };
                }
                yield "minecraft:smooth_stone";
            }
            case "minecraft:double_wooden_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "spruce" -> "minecraft:spruce_planks";
                        case "birch" -> "minecraft:birch_planks";
                        case "jungle" -> "minecraft:jungle_planks";
                        case "acacia" -> "minecraft:acacia_planks";
                        case "dark_oak" -> "minecraft:dark_oak_planks";
                        default -> "minecraft:oak_planks";
                    };
                }
                yield "minecraft:oak_planks";
            }
            case "minecraft:wooden_slab" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "spruce" -> "minecraft:spruce_slab";
                        case "birch" -> "minecraft:birch_slab";
                        case "jungle" -> "minecraft:jungle_slab";
                        case "acacia" -> "minecraft:acacia_slab";
                        case "dark_oak" -> "minecraft:dark_oak_slab";
                        default -> "minecraft:oak_slab";
                    };
                }
                yield "minecraft:oak_slab";
            }
            case "minecraft:planks" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield "minecraft:" + variant + "_planks";
                }
                yield "minecraft:oak_planks";
            }
            case "minecraft:prismarine" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "bricks" -> "minecraft:prismarine_bricks";
                        case "dark" -> "minecraft:dark_prismarine";
                        default -> "minecraft:prismarine";
                    };
                }
                yield "minecraft:prismarine";
            }
            case "minecraft:monster_egg" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if (variant != null) {
                    yield switch (variant) {
                        case "cobblestone" -> "minecraft:infested_cobblestone";
                        case "stonebrick" -> "minecraft:infested_stone_bricks";
                        case "mossy_stonebrick" -> "minecraft:infested_mossy_stone_bricks";
                        case "cracked_stonebrick" -> "minecraft:infested_cracked_stone_bricks";
                        case "chiseled_stonebrick" -> "minecraft:infested_chiseled_stone_bricks";
                        default -> "minecraft:infested_stone";
                    };
                }
                yield "minecraft:infested_stone";
            }
            case "minecraft:cobblestone_wall" -> {
                String variant = parseStateProperty(stateRaw, "variant");
                if ("mossy".equals(variant)) {
                    yield "minecraft:mossy_cobblestone_wall";
                }
                yield "minecraft:cobblestone_wall";
            }
            case "minecraft:stained_glass" -> mapLegacyColoredBlock(stateRaw, "stained_glass", "minecraft:", "_stained_glass");
            case "minecraft:stained_glass_pane" -> mapLegacyColoredBlock(stateRaw, "stained_glass_pane", "minecraft:", "_stained_glass_pane");
            case "minecraft:stained_hardened_clay" -> mapLegacyColoredBlock(stateRaw, "stained_hardened_clay", "minecraft:", "_terracotta");
            case "minecraft:wool" -> mapLegacyColoredBlock(stateRaw, "wool", "minecraft:", "_wool");
            case "minecraft:carpet" -> mapLegacyColoredBlock(stateRaw, "carpet", "minecraft:", "_carpet");
            default -> idRaw;
        };
    }

    private static String mapLegacyColoredBlock(String stateRaw, String fallbackName, String prefix, String suffix) {
        String color = parseStateProperty(stateRaw, "color");
        if (color == null) {
            color = parseStateProperty(stateRaw, "variant");
        }
        String mapped = mapLegacyColor(color);
        if (mapped == null) {
            if ("stained_hardened_clay".equals(fallbackName)) {
                return "minecraft:terracotta";
            }
            return prefix + "white" + suffix;
        }
        return prefix + mapped + suffix;
    }

    private static String mapLegacyColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw) {
            case "silver" -> "light_gray";
            case "light_blue" -> "light_blue";
            case "light_gray" -> "light_gray";
            default -> raw;
        };
    }

    private static String parseStateProperty(String stateRaw, String key) {
        if (stateRaw == null || key == null) {
            return null;
        }
        int bracket = stateRaw.indexOf('[');
        int end = stateRaw.lastIndexOf(']');
        if (bracket == -1 || end <= bracket) {
            return null;
        }
        String props = stateRaw.substring(bracket + 1, end);
        String[] parts = props.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String k = kv[0].trim().toLowerCase(Locale.ROOT);
            if (!k.equals(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String v = kv[1].trim().toLowerCase(Locale.ROOT);
            return v.isBlank() ? null : v;
        }
        return null;
    }

    private static int rotationFromState(String stateRaw) {
        String facing = parseStateProperty(stateRaw, "facing");
        if (facing == null) {
            return 0;
        }
        return switch (facing) {
            case "east" -> 1;
            case "south" -> 2;
            case "west" -> 3;
            default -> 0;
        };
    }

    private static void refreshWorld(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer == null) {
            return;
        }

        if (pos != null && client.world != null) {
            BlockState state = client.world.getBlockState(pos);
            client.worldRenderer.scheduleBlockRerenderIfNeeded(pos, state, state);
            client.worldRenderer.scheduleBlockRenders(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
            );
            return;
        }
        client.worldRenderer.scheduleTerrainUpdate();
    }

    private static void reloadChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer == null) {
            return;
        }
        client.execute(() -> {
            client.worldRenderer.reload();
            client.worldRenderer.scheduleTerrainUpdate();
        });
    }


    private static void clearRemapCache() {
        REMAP_STATE_CACHE.clear();
    }

    private static void bumpRenderDataVersion() {
        renderDataVersion++;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
        BlockState into,
        BlockState from,
        net.minecraft.state.property.Property<T> property
    ) {
        return into.with(property, from.get(property));
    }

    private record RemapKey(BlockState original, Block mapped, int turns) {
    }

    public enum SelectedSourceType {
        BLOCK,
        CUSTOM_TEXTURE
    }

    public enum ScreenMode {
        DEFAULT,
        GUI_SETTINGS,
        ITEM,
        ITEM_NAME
    }

    public enum ItemAppearanceTargetType {
        BLOCK,
        ITEM,
        CUSTOM_TEXTURE
    }

    public static final class ItemAppearanceMapping {
        public final String sourceItemId;
        public final String baseName;
        public final ItemAppearanceTargetType targetType;
        public final String targetValue;

        public ItemAppearanceMapping(String sourceItemId, String baseName, ItemAppearanceTargetType targetType, String targetValue) {
            this.sourceItemId = sourceItemId;
            this.baseName = baseName;
            this.targetType = targetType;
            this.targetValue = targetValue;
        }
    }

    public static final class ItemNameOverrideMapping {
        public final String sourceItemId;
        public final String baseName;
        public final String customName;
        public final int color;

        public ItemNameOverrideMapping(String sourceItemId, String baseName, String customName, int color) {
            this.sourceItemId = sourceItemId;
            this.baseName = baseName;
            this.customName = customName;
            this.color = color & 0xFFFFFF;
        }

        public Text toText() {
            return Text.literal(customName).setStyle(Style.EMPTY.withItalic(false).withColor(color));
        }
    }

    public enum Theme {
        WHITE,
        BLACK_WHITE,
        BLUE,
        PURPLE,
        RED,
        PASTEL_MINT,
        PASTEL_PEACH,
        PASTEL_LAVENDER,
        PASTEL_SKY,
        PASTEL_ROSE,
        PASTEL_BUTTER,
        PASTEL_AQUA,
        MOONWALKER,
        VIOLET,
        FEMBOY,
        ARGON,
        MOSS,
        HAZEL,
        BLOSSOM
    }

    public enum LayoutPreset {
        BLOOM,
        TOPAZ,
        OBSIDIAN,
        SLATE,
        ALPINE
    }

    public enum ThemeAnimation {
        NONE,
        PULSE,
        GLOW,
        SLIDE,
        BOUNCE,
        FADE
    }

    public static final class CustomTheme {
        public String name;
        public int panelColor;
        public int borderColor;
        public int textColor;
        public int accentTextColor;
        public int selectionColor;
        public int gradientStart;
        public int gradientEnd;
        public boolean flatTheme;
        public float panelRadius;
        public float elementRadius;
        public ThemeAnimation tabAnimation;
        public ThemeAnimation selectionAnimation;
    }

    public static int getGuiPanelColor() {
        return guiPanelColor;
    }

    public static int getGuiBorderColor() {
        return guiBorderColor;
    }

    public static int getGuiTextColor() {
        return guiTextColor;
    }

    public static int getGuiAccentTextColor() {
        return guiAccentTextColor;
    }

    public static int getSelectionBoxColor() {
        return selectionBoxColor;
    }

    public static ThemeAnimation getTabAnimation() {
        return tabAnimation;
    }

    public static ThemeAnimation getSelectionAnimation() {
        return selectionAnimation;
    }

    public static float getPanelCornerRadius() {
        return panelCornerRadius;
    }

    public static float getElementCornerRadius() {
        return elementCornerRadius;
    }

    public static boolean isCustomThemeActive() {
        return customThemeActive;
    }

    public static String getCustomThemeName() {
        return customThemeName;
    }

    public static CustomTheme getCustomTheme() {
        return customThemeActive ? customTheme : null;
    }

    public static Theme getTheme() {
        if (guiPanelColor == 0xFFF5F5F5
            && guiBorderColor == 0xFF000000
            && guiTextColor == 0xFF000000
            && guiAccentTextColor == 0xFF111111
            && selectionBoxColor == 0x66000000) {
            return Theme.WHITE;
        }
        if (guiPanelColor == 0xFF0A0A0A
            && guiBorderColor == 0xFFFFFFFF
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFFFFFFFF
            && selectionBoxColor == 0x66FFFFFF) {
            return Theme.BLACK_WHITE;
        }
        if (guiPanelColor == 0xFF2A1238
            && guiBorderColor == 0xFF5E2C83
            && guiTextColor == 0xFFF2E9FF
            && guiAccentTextColor == 0xFFB98CFF
            && selectionBoxColor == 0x664C2A73) {
            return Theme.PURPLE;
        }
        if (guiPanelColor == 0xFF1A0000
            && guiBorderColor == 0xFF000000
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFFFF4D4D
            && selectionBoxColor == 0x66000000) {
            return Theme.RED;
        }
        if (guiPanelColor == 0xFFF3FFFA
            && guiBorderColor == 0xFF7DBEA3
            && guiTextColor == 0xFF1E3A34
            && guiAccentTextColor == 0xFF58A98C
            && selectionBoxColor == 0x6686C7AD) {
            return Theme.PASTEL_MINT;
        }
        if (guiPanelColor == 0xFFFFF6F1
            && guiBorderColor == 0xFFE1A38D
            && guiTextColor == 0xFF4A2E2A
            && guiAccentTextColor == 0xFFE07A5F
            && selectionBoxColor == 0x66E3B3A0) {
            return Theme.PASTEL_PEACH;
        }
        if (guiPanelColor == 0xFFFAF5FF
            && guiBorderColor == 0xFFB79DE5
            && guiTextColor == 0xFF2F2341
            && guiAccentTextColor == 0xFF9B7EDC
            && selectionBoxColor == 0x66C6B0EB) {
            return Theme.PASTEL_LAVENDER;
        }
        if (guiPanelColor == 0xFFF4FAFF
            && guiBorderColor == 0xFF8DB6E1
            && guiTextColor == 0xFF203447
            && guiAccentTextColor == 0xFF6EA7D9
            && selectionBoxColor == 0x669AC3E9) {
            return Theme.PASTEL_SKY;
        }
        if (guiPanelColor == 0xFFFFF7F8
            && guiBorderColor == 0xFFE7A9B8
            && guiTextColor == 0xFF4A2C33
            && guiAccentTextColor == 0xFFD97B8D
            && selectionBoxColor == 0x66E6B7C4) {
            return Theme.PASTEL_ROSE;
        }
        if (guiPanelColor == 0xFFFFFDF4
            && guiBorderColor == 0xFFE8D99A
            && guiTextColor == 0xFF4A3E20
            && guiAccentTextColor == 0xFFD6B95B
            && selectionBoxColor == 0x66E8D8A6) {
            return Theme.PASTEL_BUTTER;
        }
        if (guiPanelColor == 0xFFF1FFFD
            && guiBorderColor == 0xFF86D8C8
            && guiTextColor == 0xFF1E3A35
            && guiAccentTextColor == 0xFF5ABFAE
            && selectionBoxColor == 0x66A8E1D6) {
            return Theme.PASTEL_AQUA;
        }
        if (guiPanelColor == 0xFF152331
            && guiBorderColor == 0xFF000000
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFF9DB3C7
            && selectionBoxColor == 0x66000000) {
            return Theme.MOONWALKER;
        }
        if (guiPanelColor == 0xFF654EA3
            && guiBorderColor == 0xFF3E2C6D
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFFEAAFC8
            && selectionBoxColor == 0x66574591) {
            return Theme.VIOLET;
        }
        if (guiPanelColor == 0xFFCF62A9
            && guiBorderColor == 0xFF000000
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFF58CEF8
            && selectionBoxColor == 0x66000000) {
            return Theme.FEMBOY;
        }
        if (guiPanelColor == 0xFF03001E
            && guiBorderColor == 0xFF000000
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFFEC38BC
            && selectionBoxColor == 0x66000000) {
            return Theme.ARGON;
        }
        if (guiPanelColor == 0xFF134E5E
            && guiBorderColor == 0xFF0B2E37
            && guiTextColor == 0xFFFFFFFF
            && guiAccentTextColor == 0xFF71B280
            && selectionBoxColor == 0x66000000) {
            return Theme.MOSS;
        }
        if (guiPanelColor == 0xFF77A1D3
            && guiBorderColor == 0xFF4B6F9C
            && guiTextColor == 0xFF0D1E2D
            && guiAccentTextColor == 0xFFE684AE
            && selectionBoxColor == 0x6677A1D3) {
            return Theme.HAZEL;
        }
        if (guiPanelColor == 0xFFF2E7FA
            && guiBorderColor == 0xFF8C6FB2
            && guiTextColor == 0xFF452B5F
            && guiAccentTextColor == 0xFF6E9F58
            && selectionBoxColor == 0x66B99AE0) {
            return Theme.BLOSSOM;
        }
        return Theme.BLUE;
    }

    public static void applyTheme(Theme theme) {
        if (theme == null) {
            return;
        }
        customThemeActive = false;
        customThemeName = null;
        customTheme = null;
        applyThemeInternal(theme);
        recordThemeState(theme);
        save();
    }

    private static void applyThemeInternal(Theme theme) {
        if (theme == null) {
            return;
        }
        customThemeActive = false;
        customThemeName = null;
        customTheme = null;
        tabAnimation = ThemeAnimation.PULSE;
        selectionAnimation = ThemeAnimation.PULSE;
        panelCornerRadius = 10.0f;
        elementCornerRadius = 6.0f;
        switch (theme) {
            case WHITE -> {
                guiPanelColor = 0xFFF5F5F5;
                guiBorderColor = 0xFF000000;
                guiTextColor = 0xFF000000;
                guiAccentTextColor = 0xFF111111;
                selectionBoxColor = 0x66000000;
            }
            case BLACK_WHITE -> {
                guiPanelColor = 0xFF0A0A0A;
                guiBorderColor = 0xFFFFFFFF;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFFFFFFFF;
                selectionBoxColor = 0x66FFFFFF;
            }
            case BLUE -> {
                guiPanelColor = 0xFF0F1B2A;
                guiBorderColor = 0xFF274B7A;
                guiTextColor = 0xFFE3EDF7;
                guiAccentTextColor = 0xFF8ABEFF;
                selectionBoxColor = 0x66406BA8;
            }
            case PURPLE -> {
                guiPanelColor = 0xFF2A1238;
                guiBorderColor = 0xFF5E2C83;
                guiTextColor = 0xFFF2E9FF;
                guiAccentTextColor = 0xFFB98CFF;
                selectionBoxColor = 0x664C2A73;
            }
            case RED -> {
                guiPanelColor = 0xFF1A0000;
                guiBorderColor = 0xFF000000;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFFFF4D4D;
                selectionBoxColor = 0x66000000;
            }
            case PASTEL_MINT -> {
                guiPanelColor = 0xFFF3FFFA;
                guiBorderColor = 0xFF7DBEA3;
                guiTextColor = 0xFF1E3A34;
                guiAccentTextColor = 0xFF58A98C;
                selectionBoxColor = 0x6686C7AD;
            }
            case PASTEL_PEACH -> {
                guiPanelColor = 0xFFFFF6F1;
                guiBorderColor = 0xFFE1A38D;
                guiTextColor = 0xFF4A2E2A;
                guiAccentTextColor = 0xFFE07A5F;
                selectionBoxColor = 0x66E3B3A0;
            }
            case PASTEL_LAVENDER -> {
                guiPanelColor = 0xFFFAF5FF;
                guiBorderColor = 0xFFB79DE5;
                guiTextColor = 0xFF2F2341;
                guiAccentTextColor = 0xFF9B7EDC;
                selectionBoxColor = 0x66C6B0EB;
            }
            case PASTEL_SKY -> {
                guiPanelColor = 0xFFF4FAFF;
                guiBorderColor = 0xFF8DB6E1;
                guiTextColor = 0xFF203447;
                guiAccentTextColor = 0xFF6EA7D9;
                selectionBoxColor = 0x669AC3E9;
            }
            case PASTEL_ROSE -> {
                guiPanelColor = 0xFFFFF7F8;
                guiBorderColor = 0xFFE7A9B8;
                guiTextColor = 0xFF4A2C33;
                guiAccentTextColor = 0xFFD97B8D;
                selectionBoxColor = 0x66E6B7C4;
            }
            case PASTEL_BUTTER -> {
                guiPanelColor = 0xFFFFFDF4;
                guiBorderColor = 0xFFE8D99A;
                guiTextColor = 0xFF4A3E20;
                guiAccentTextColor = 0xFFD6B95B;
                selectionBoxColor = 0x66E8D8A6;
            }
            case PASTEL_AQUA -> {
                guiPanelColor = 0xFFF1FFFD;
                guiBorderColor = 0xFF86D8C8;
                guiTextColor = 0xFF1E3A35;
                guiAccentTextColor = 0xFF5ABFAE;
                selectionBoxColor = 0x66A8E1D6;
            }
            case MOONWALKER -> {
                guiPanelColor = 0xFF152331;
                guiBorderColor = 0xFF000000;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFF9DB3C7;
                selectionBoxColor = 0x66000000;
            }
            case VIOLET -> {
                guiPanelColor = 0xFF654EA3;
                guiBorderColor = 0xFF3E2C6D;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFFEAAFC8;
                selectionBoxColor = 0x66574591;
            }
            case FEMBOY -> {
                guiPanelColor = 0xFFCF62A9;
                guiBorderColor = 0xFF000000;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFF58CEF8;
                selectionBoxColor = 0x66000000;
            }
            case ARGON -> {
                guiPanelColor = 0xFF03001E;
                guiBorderColor = 0xFF000000;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFFEC38BC;
                selectionBoxColor = 0x66000000;
            }
            case MOSS -> {
                guiPanelColor = 0xFF134E5E;
                guiBorderColor = 0xFF0B2E37;
                guiTextColor = 0xFFFFFFFF;
                guiAccentTextColor = 0xFF71B280;
                selectionBoxColor = 0x66000000;
            }
            case HAZEL -> {
                guiPanelColor = 0xFF77A1D3;
                guiBorderColor = 0xFF4B6F9C;
                guiTextColor = 0xFF0D1E2D;
                guiAccentTextColor = 0xFFE684AE;
                selectionBoxColor = 0x6677A1D3;
            }
            case BLOSSOM -> {
                guiPanelColor = 0xFFF2E7FA;
                guiBorderColor = 0xFF8C6FB2;
                guiTextColor = 0xFF452B5F;
                guiAccentTextColor = 0xFF6E9F58;
                selectionBoxColor = 0x66B99AE0;
                tabAnimation = ThemeAnimation.GLOW;
                selectionAnimation = ThemeAnimation.FADE;
                panelCornerRadius = 18.0f;
                elementCornerRadius = 12.0f;
            }
        }
    }

    public static LayoutPreset getLayoutPreset() {
        return layoutPreset;
    }

    public static float getItemFirstPersonX() {
        return itemFirstPersonX;
    }

    public static void setItemFirstPersonX(float value) {
        itemFirstPersonX = value;
        save();
    }

    public static float getItemFirstPersonY() {
        return itemFirstPersonY;
    }

    public static void setItemFirstPersonY(float value) {
        itemFirstPersonY = value;
        save();
    }

    public static float getItemFirstPersonZ() {
        return itemFirstPersonZ;
    }

    public static void setItemFirstPersonZ(float value) {
        itemFirstPersonZ = value;
        save();
    }

    public static float getItemFirstPersonRotX() {
        return itemFirstPersonRotX;
    }

    public static void setItemFirstPersonRotX(float value) {
        itemFirstPersonRotX = value;
        save();
    }

    public static float getItemFirstPersonRotY() {
        return itemFirstPersonRotY;
    }

    public static void setItemFirstPersonRotY(float value) {
        itemFirstPersonRotY = value;
        save();
    }

    public static float getItemFirstPersonRotZ() {
        return itemFirstPersonRotZ;
    }

    public static void setItemFirstPersonRotZ(float value) {
        itemFirstPersonRotZ = value;
        save();
    }

    public static float getItemFirstPersonScale() {
        return itemFirstPersonScale;
    }

    public static void setItemFirstPersonScale(float value) {
        itemFirstPersonScale = value;
        save();
    }

    public static float getItemThirdPersonX() {
        return itemThirdPersonX;
    }

    public static void setItemThirdPersonX(float value) {
        itemThirdPersonX = value;
        save();
    }

    public static float getItemThirdPersonY() {
        return itemThirdPersonY;
    }

    public static void setItemThirdPersonY(float value) {
        itemThirdPersonY = value;
        save();
    }

    public static float getItemThirdPersonZ() {
        return itemThirdPersonZ;
    }

    public static void setItemThirdPersonZ(float value) {
        itemThirdPersonZ = value;
        save();
    }

    public static float getItemThirdPersonRotX() {
        return itemThirdPersonRotX;
    }

    public static void setItemThirdPersonRotX(float value) {
        itemThirdPersonRotX = value;
        save();
    }

    public static float getItemThirdPersonRotY() {
        return itemThirdPersonRotY;
    }

    public static void setItemThirdPersonRotY(float value) {
        itemThirdPersonRotY = value;
        save();
    }

    public static float getItemThirdPersonRotZ() {
        return itemThirdPersonRotZ;
    }

    public static void setItemThirdPersonRotZ(float value) {
        itemThirdPersonRotZ = value;
        save();
    }

    public static float getItemThirdPersonScale() {
        return itemThirdPersonScale;
    }

    public static void setItemThirdPersonScale(float value) {
        itemThirdPersonScale = value;
        save();
    }

    public static void resetItemTransformSettings() {
        itemFirstPersonX = 0.0f;
        itemFirstPersonY = 0.0f;
        itemFirstPersonZ = 0.0f;
        itemFirstPersonRotX = 0.0f;
        itemFirstPersonRotY = 0.0f;
        itemFirstPersonRotZ = 0.0f;
        itemFirstPersonScale = 1.0f;
        itemThirdPersonX = 0.0f;
        itemThirdPersonY = 0.0f;
        itemThirdPersonZ = 0.0f;
        itemThirdPersonRotX = 0.0f;
        itemThirdPersonRotY = 0.0f;
        itemThirdPersonRotZ = 0.0f;
        itemThirdPersonScale = 1.0f;
        save();
    }

    private static void migrateLegacyItemTransformDefaultsIfNeeded() {
        if (!isLegacyDefaultItemTransform(itemFirstPersonX, 0.72f)
            || !isLegacyDefaultItemTransform(itemFirstPersonY, -0.34f)
            || !isLegacyDefaultItemTransform(itemFirstPersonZ, -0.18f)
            || !isLegacyDefaultItemTransform(itemFirstPersonRotX, -64.0f)
            || !isLegacyDefaultItemTransform(itemFirstPersonRotY, 32.0f)
            || !isLegacyDefaultItemTransform(itemFirstPersonRotZ, 46.0f)
            || !isLegacyDefaultItemTransform(itemFirstPersonScale, 1.18f)
            || !isLegacyDefaultItemTransform(itemThirdPersonX, 0.02f)
            || !isLegacyDefaultItemTransform(itemThirdPersonY, 0.14f)
            || !isLegacyDefaultItemTransform(itemThirdPersonZ, 0.0f)
            || !isLegacyDefaultItemTransform(itemThirdPersonRotX, -70.0f)
            || !isLegacyDefaultItemTransform(itemThirdPersonRotY, 12.0f)
            || !isLegacyDefaultItemTransform(itemThirdPersonRotZ, 52.0f)
            || !isLegacyDefaultItemTransform(itemThirdPersonScale, 1.1f)) {
            return;
        }
        itemFirstPersonX = 0.0f;
        itemFirstPersonY = 0.0f;
        itemFirstPersonZ = 0.0f;
        itemFirstPersonRotX = 0.0f;
        itemFirstPersonRotY = 0.0f;
        itemFirstPersonRotZ = 0.0f;
        itemFirstPersonScale = 1.0f;
        itemThirdPersonX = 0.0f;
        itemThirdPersonY = 0.0f;
        itemThirdPersonZ = 0.0f;
        itemThirdPersonRotX = 0.0f;
        itemThirdPersonRotY = 0.0f;
        itemThirdPersonRotZ = 0.0f;
        itemThirdPersonScale = 1.0f;
    }

    private static boolean isLegacyDefaultItemTransform(float value, float legacyDefault) {
        return Math.abs(value - legacyDefault) < 0.0001f;
    }

    public static void setLayoutPreset(LayoutPreset preset) {
        if (preset == null || preset == layoutPreset) {
            return;
        }
        layoutPreset = preset;
        recordLayoutState(preset);
    }

    public static float getGuiScale() {
        return 1.5f;
    }

    public static void setGuiScale(float value) {
        guiScale = 1.5f;
    }

    public static float getBlockBrightness() {
        return guiBlockBrightness;
    }

    public static void setBlockBrightness(float value) {
        guiBlockBrightness = MathHelper.clamp(value, 0.25f, 4.0f);
        save();
    }

}
