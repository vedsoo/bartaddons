package hat.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.client.texture.NativeImage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.registry.Registries;
import org.lwjgl.glfw.GLFW;
import com.mojang.brigadier.CommandDispatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class HatClientMod implements ClientModInitializer {
    private static final KeyBinding.Category BART_ADDONS_CATEGORY =
        KeyBinding.Category.create(Identifier.of("hat", "bart_addons"));
    private static KeyBinding openGuiKey;
    private static KeyBinding rotateFmeKey;
    private static KeyBinding deleteFmeKey;
    private static KeyBinding copyFmeKey;
    private static KeyBinding rotateSchematicKey;
    private static boolean middleMouseWasDown;
    private static BlockPos worldEditPos1;
    private static BlockPos worldEditPos2;
    private static BlockPos worldEditPos3;
    private static int schematicRotationTurns = 0;
    private static final long WORLD_EDIT_MAX_BLOCKS = 200_000L;
    private static final int WORLD_EDIT_LIST_LIMIT = 50;
    private static final java.util.concurrent.ExecutorService DOWNLOAD_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "hat-fme-download");
            t.setDaemon(true);
            return t;
        });

    @Override
    public void onInitializeClient() {
        FmeManager.load();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hat.open_gui",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            BART_ADDONS_CATEGORY
        ));
        rotateFmeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hat.fme_rotate",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            BART_ADDONS_CATEGORY
        ));
        deleteFmeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hat.fme_delete",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            BART_ADDONS_CATEGORY
        ));
        copyFmeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hat.fme_copy",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            BART_ADDONS_CATEGORY
        ));
        rotateSchematicKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hat.schematic_rotate",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS,
            BART_ADDONS_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(HatClientMod::handleKeys);
        WorldRenderEvents.AFTER_ENTITIES.register(ChinaHatRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(FmeCustomTextureRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(FmeGhostBlockRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(FmeWorldEditRenderer::render);
        ClientCommandRegistrationCallback.EVENT.register(HatClientMod::registerFmeCommands);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient() || !FmeManager.isEnabled() || !FmeManager.isEditMode()) {
                return ActionResult.PASS;
            }
            if (FmeManager.clearReplacement(pos.toImmutable()) && player != null) {
                FmeManager.sendClientMessage("Reset block to original texture");
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() || hand != Hand.MAIN_HAND || !FmeManager.isEnabled() || !FmeManager.isEditMode()) {
                return ActionResult.PASS;
            }

            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitState = world.getBlockState(hitPos);

            if (player != null && player.isSneaking()) {
                BlockPos placePos = null;
                Direction placeSide = null;
                if (!hitState.isAir() || FmeManager.isAirGhostPosition(hitPos)) {
                    placePos = hitPos.offset(hitResult.getSide());
                    placeSide = hitResult.getSide();
                }
                if (placePos == null) {
                    GhostHit ghostHit = findGhostHit(MinecraftClient.getInstance(), 6.0);
                    if (ghostHit != null) {
                        placePos = ghostHit.pos().offset(ghostHit.side());
                        placeSide = ghostHit.side();
                    }
                }
                if (placePos == null || placeSide == null) {
                    return ActionResult.PASS;
                }
                BlockState placeState = world.getBlockState(placePos);
                if (placeState.isAir()) {
                    if (FmeManager.applyReplacement(placePos.toImmutable(), Blocks.AIR)) {
                        FmeManager.sendClientMessage(
                            "Placed ghost block using " + selectedSourceLabel()
                        );
                    }
                }
                return ActionResult.PASS;
            }

            if (hitState.isAir()) {
                return ActionResult.PASS;
            }
            if (!FmeManager.applyReplacement(hitPos.toImmutable(), hitState.getBlock())) {
                return ActionResult.PASS;
            }

            FmeManager.sendClientMessage(
                hitState.getBlock().getName().getString() + " now uses " +
                    selectedSourceLabel()
            );
            // Keep vanilla interaction/hitbox/gameplay behavior by not consuming the click.
            return ActionResult.PASS;
        });
    }

    private static void handleKeys(MinecraftClient client) {
        FmeManager.clientTick(client);
        HatTextureManager.tickAnimations(System.currentTimeMillis());

        while (rotateFmeKey.wasPressed()) {
            if (!FmeManager.isEnabled() || !FmeManager.isEditMode() || client.player == null) {
                continue;
            }
            if (client.crosshairTarget instanceof BlockHitResult hitResult
                && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                if (FmeManager.rotateReplacement(hitResult.getBlockPos().toImmutable())) {
                    FmeManager.sendClientMessage("Rotated mapped block");
                    continue;
                }
            }
            int rotation = FmeManager.rotateSelectedSource();
            FmeManager.sendClientMessage("Selected source rotation: " + (rotation * 90) + "°");
        }
        while (deleteFmeKey.wasPressed()) {
            if (!FmeManager.isEnabled() || !FmeManager.isEditMode() || client.player == null || client.world == null) {
                continue;
            }
            BlockPos mappedPos = findMappedPosInSight(client);
            if (mappedPos != null && FmeManager.clearReplacement(mappedPos.toImmutable())) {
                FmeManager.sendClientMessage("Deleted mapped ghost block");
            }
        }
        while (copyFmeKey.wasPressed()) {
            if (!FmeManager.isEnabled() || client.player == null || client.world == null) {
                continue;
            }
            BlockPos mappedPos = findMappedOrCustomPosInSight(client);
            if (mappedPos == null) {
                FmeManager.sendClientMessage("No mapped block in sight");
                continue;
            }
            long key = mappedPos.asLong();
            String customTexture = FmeManager.customTextureReplacementsView().get(key);
            if (customTexture != null) {
                int rotation = FmeManager.rotationAt(mappedPos);
                FmeManager.selectCustomTextureName(customTexture, rotation);
                FmeManager.sendClientMessage("Selected custom texture: " + customTexture);
                continue;
            }
            Block mapped = FmeManager.positionReplacementsView().get(key);
            if (mapped != null) {
                int rotation = FmeManager.rotationAt(mappedPos);
                FmeManager.setSelectedSource(mapped);
                FmeManager.setSelectedRotation(rotation);
                FmeManager.sendClientMessage("Selected source block: " + mapped.getName().getString());
                continue;
            }
            FmeManager.sendClientMessage("No mapped block in sight");
        }
        while (rotateSchematicKey.wasPressed()) {
            schematicRotationTurns = (schematicRotationTurns + 1) % 4;
            FmeManager.sendClientMessage(
                "Schematic rotation set to " + (schematicRotationTurns * 90) + "°"
            );
        }
        if (client.getWindow() != null) {
            boolean middleMouseDown =
                GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
            if (middleMouseDown && !middleMouseWasDown
                && FmeManager.isEnabled() && FmeManager.isEditMode()
                && client.player != null && client.world != null
                && client.currentScreen == null) {

                    BlockPos supportPos = findFmeMiddleClickSupport(client);
                    if (supportPos != null) {
                    BlockPos targetPos = supportPos.up();
                    if (client.crosshairTarget instanceof BlockHitResult hitResult
                        && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                        BlockPos hitPos = hitResult.getBlockPos();
                        BlockState hitState = client.world.getBlockState(hitPos);
                        if (!hitState.isAir() || FmeManager.isAirGhostPosition(hitPos)) {
                            targetPos = hitPos.offset(hitResult.getSide());
                        }
                    } else {
                        GhostHit ghostHit = findGhostHit(client, 6.0);
                        if (ghostHit != null) {
                            targetPos = ghostHit.pos().offset(ghostHit.side());
                        }
                    }
                    BlockState targetState = client.world.getBlockState(targetPos);
                    if (FmeManager.applyReplacement(targetPos.toImmutable(), targetState.getBlock())) {
                        if (targetState.isAir()) {
                            FmeManager.sendClientMessage(
                                "Placed ghost block using " + selectedSourceLabel()
                            );
                        } else {
                            FmeManager.sendClientMessage(
                                targetState.getBlock().getName().getString() + " now uses "
                                    + selectedSourceLabel()
                            );
                        }
                    }
                }
            }
            middleMouseWasDown = middleMouseDown;
        }

        while (openGuiKey.wasPressed()) {
            client.setScreen(new HatToggleScreen());
        }
    }

    private static void registerFmeCommands(
        CommandDispatcher<FabricClientCommandSource> dispatcher,
        CommandRegistryAccess registryAccess
    ) {
        dispatcher.register(
            ClientCommandManager.literal("fme")
                .executes(ctx -> {
                    FmeManager.openScreen();
                    return 1;
                })
                .then(ClientCommandManager.literal("toggle").executes(ctx -> {
                    boolean enabled = FmeManager.toggleEnabled();
                    ctx.getSource().sendFeedback(Text.literal("FME is now " + (enabled ? "ON" : "OFF")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("edit").executes(ctx -> {
                    boolean edit = FmeManager.toggleEditMode();
                    ctx.getSource().sendFeedback(Text.literal("FME edit mode is now " + (edit ? "ON" : "OFF")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("undo").executes(ctx -> {
                    int restored = FmeManager.undoLastReplace();
                    if (restored == 0) {
                        ctx.getSource().sendFeedback(Text.literal("Nothing to undo."));
                        return 1;
                    }
                    ctx.getSource().sendFeedback(Text.literal(
                        "Undid last replace for " + restored + " mapped block" + (restored == 1 ? "" : "s")
                    ));
                    return 1;
                }))
                .then(ClientCommandManager.literal("gui")
                    .then(ClientCommandManager.literal("settings").executes(ctx -> {
                        FmeManager.openGuiSettings();
                        return 1;
                    })))
                .then(ClientCommandManager.literal("config")
                    .then(ClientCommandManager.literal("load").executes(ctx -> {
                        boolean loaded = FmeManager.loadDefaultConfig();
                        if (!loaded) {
                            ctx.getSource().sendError(Text.literal("No default FME config found."));
                            return 0;
                        }
                        ctx.getSource().sendFeedback(Text.literal("Loaded default FME config."));
                        return 1;
                    }).then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = ctx.getArgument("name", String.class);
                            boolean loaded = FmeManager.loadConfig(name);
                            if (!loaded) {
                                ctx.getSource().sendError(Text.literal("Config not found: " + name));
                                return 0;
                            }
                            ctx.getSource().sendFeedback(Text.literal("Loaded FME config: " + name));
                            return 1;
                        })))
                    .then(ClientCommandManager.literal("save")
                        .executes(ctx -> {
                            boolean saved = FmeManager.saveCurrentConfig();
                            if (!saved) {
                                ctx.getSource().sendError(Text.literal("No current FME config to save. Use /fme config save <name>."));
                                return 0;
                            }
                            ctx.getSource().sendFeedback(Text.literal("Saved current FME config."));
                            return 1;
                        })
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String name = ctx.getArgument("name", String.class);
                                boolean saved = FmeManager.saveConfig(name);
                                if (!saved) {
                                    ctx.getSource().sendError(Text.literal("Failed to save config: " + name));
                                    return 0;
                                }
                                ctx.getSource().sendFeedback(Text.literal("Saved FME config: " + name));
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String name = ctx.getArgument("name", String.class);
                                boolean saved = FmeManager.addConfig(name);
                                if (!saved) {
                                    ctx.getSource().sendError(Text.literal("Failed to add config: " + name));
                                    return 0;
                                }
                            ctx.getSource().sendFeedback(Text.literal("Added and loaded FME config: " + name));
                            return 1;
                        }))))
                .then(ClientCommandManager.literal("worldedit")
                    .then(ClientCommandManager.literal("pos1")
                        .executes(ctx -> executeWorldEditPosCurrent(ctx.getSource(), 1))
                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        worldEditPos1 = new BlockPos(
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z")
                                        );
                                        ctx.getSource().sendFeedback(Text.literal("WorldEdit pos1 set to " + worldEditPos1));
                                        return 1;
                                    })))))
                    .then(ClientCommandManager.literal("pos2")
                        .executes(ctx -> executeWorldEditPosCurrent(ctx.getSource(), 2))
                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        worldEditPos2 = new BlockPos(
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z")
                                        );
                                        ctx.getSource().sendFeedback(Text.literal("WorldEdit pos2 set to " + worldEditPos2));
                                        return 1;
                                    })))))
                    .then(ClientCommandManager.literal("pos3")
                        .executes(ctx -> executeWorldEditPosCurrent(ctx.getSource(), 3))
                        .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        worldEditPos3 = new BlockPos(
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z")
                                        );
                                        ctx.getSource().sendFeedback(Text.literal("WorldEdit pos3 (top) set to " + worldEditPos3));
                                        return 1;
                                    })))))
                    .then(ClientCommandManager.literal("clear").executes(ctx -> {
                        return executeWorldEditClear(ctx.getSource());
                    }))
                    .then(ClientCommandManager.literal("list").executes(ctx -> executeWorldEditList(ctx.getSource())))
                    .then(ClientCommandManager.literal("delete").executes(ctx -> executeWorldEditClearSelection(ctx.getSource())))
                    .then(ClientCommandManager.literal("replace")
                        .then(ClientCommandManager.argument("from", IdentifierArgumentType.identifier())
                            .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder))
                            .then(ClientCommandManager.argument("to", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder))
                                .executes(ctx -> executeWorldEditReplace(
                                    ctx.getSource(),
                                    ctx.getArgument("from", Identifier.class).toString(),
                                    ctx.getArgument("to", Identifier.class).toString()
                                ))))))
                .then(ClientCommandManager.literal("replace")
                    .then(ClientCommandManager.argument("from", IdentifierArgumentType.identifier())
                        .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder))
                        .then(ClientCommandManager.literal("texture")
                            .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestTextureFiles(builder))
                                .executes(ctx -> executeReplaceWithTexture(
                                    ctx.getSource(),
                                    ctx.getArgument("from", Identifier.class).toString(),
                                    ctx.getArgument("file", String.class)
                                ))))
                        .then(ClientCommandManager.argument("to", IdentifierArgumentType.identifier())
                            .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder))
                            .executes(ctx -> executeReplace(
                                ctx.getSource(),
                                ctx.getArgument("from", Identifier.class).toString(),
                                ctx.getArgument("to", Identifier.class).toString()
                            )))
                        .then(ClientCommandManager.literal("with")
                            .then(ClientCommandManager.argument("to", IdentifierArgumentType.identifier())
                                .suggests((ctx, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder))
                                .executes(ctx -> {
                                    return executeReplace(
                                        ctx.getSource(),
                                        ctx.getArgument("from", Identifier.class).toString(),
                                        ctx.getArgument("to", Identifier.class).toString()
                                    );
                                }))
                            .then(ClientCommandManager.literal("texture")
                                .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> suggestTextureFiles(builder))
                                    .executes(ctx -> executeReplaceWithTexture(
                                        ctx.getSource(),
                                        ctx.getArgument("from", Identifier.class).toString(),
                                        ctx.getArgument("file", String.class)
                                    )))))))
                .then(ClientCommandManager.literal("addurl")
                    .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> executeAddUrl(
                            ctx.getSource(),
                            ctx.getArgument("url", String.class)
                        ))))
                .then(ClientCommandManager.literal("image")
                    .then(ClientCommandManager.literal("import")
                        .then(ClientCommandManager.argument("file", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestImageFiles(builder))
                            .executes(ctx -> executeImageImport(
                                ctx.getSource(),
                                ctx.getArgument("file", String.class),
                                null,
                                null,
                                null
                            ))
                            .then(ClientCommandManager.argument("orientation", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                    List.of("vertical", "horizontal"), builder))
                                .executes(ctx -> executeImageImport(
                                    ctx.getSource(),
                                    ctx.getArgument("file", String.class),
                                    ctx.getArgument("orientation", String.class),
                                    null,
                                    null
                                ))
                                .then(ClientCommandManager.argument("width", IntegerArgumentType.integer(1, 2048))
                                    .then(ClientCommandManager.argument("height", IntegerArgumentType.integer(1, 2048))
                                    .executes(ctx -> executeImageImport(
                                        ctx.getSource(),
                                        ctx.getArgument("file", String.class),
                                        ctx.getArgument("orientation", String.class),
                                        IntegerArgumentType.getInteger(ctx, "width"),
                                        IntegerArgumentType.getInteger(ctx, "height")
                                        ))))))))
                .then(ClientCommandManager.literal("schematic")
                    .then(ClientCommandManager.literal("paste")
                        .then(ClientCommandManager.argument("file", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestSchematicFiles(builder))
                            .executes(ctx -> executeSchematicPaste(
                                ctx.getSource(),
                                ctx.getArgument("file", String.class)
                            ))))
                    .then(ClientCommandManager.literal("undo")
                        .executes(ctx -> executeSchematicUndo(ctx.getSource()))))
        );
    }

    private static int executeReplace(FabricClientCommandSource source, String fromRaw, String toRaw) {
        Block fromBlock = parseBlockId(fromRaw);
        Block toBlock = parseBlockId(toRaw);

        if (fromBlock == null) {
            source.sendError(Text.literal("Unknown block id: " + fromRaw));
            return 0;
        }
        if (toBlock == null) {
            source.sendError(Text.literal("Unknown block id: " + toRaw));
            return 0;
        }

        int changed = FmeManager.replaceMappedSource(fromBlock, toBlock);
        if (changed == 0) {
            source.sendFeedback(Text.literal("No existing FME-mapped blocks used " + fromRaw));
            return 1;
        }

        source.sendFeedback(Text.literal(
            "Updated " + changed + " mapped block" + (changed == 1 ? "" : "s") +
                ": " + fromRaw + " -> " + toRaw
        ));
        return 1;
    }

    private static int executeWorldEditPosCurrent(FabricClientCommandSource source, int posIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            source.sendError(Text.literal("No player found."));
            return 0;
        }
        BlockPos pos = client.player.getBlockPos();
        if (posIndex == 1) {
            worldEditPos1 = pos;
            source.sendFeedback(Text.literal("WorldEdit pos1 set to " + pos));
            return 1;
        }
        if (posIndex == 2) {
            worldEditPos2 = pos;
            source.sendFeedback(Text.literal("WorldEdit pos2 set to " + pos));
            return 1;
        }
        if (posIndex == 3) {
            worldEditPos3 = pos;
            source.sendFeedback(Text.literal("WorldEdit pos3 (top) set to " + pos));
            return 1;
        }
        source.sendError(Text.literal("Invalid position index."));
        return 0;
    }

    private static int executeReplaceWithTexture(FabricClientCommandSource source, String fromRaw, String textureFile) {
        Block fromBlock = parseBlockId(fromRaw);
        if (fromBlock == null) {
            source.sendError(Text.literal("Unknown block id: " + fromRaw));
            return 0;
        }
        if (textureFile == null || textureFile.isBlank()) {
            source.sendError(Text.literal("Missing texture file name."));
            return 0;
        }
        var texturePath = HatTextureManager.resolveTexturePath(textureFile.trim());
        if (texturePath == null) {
            source.sendError(Text.literal("Unknown custom texture: " + textureFile));
            sendTextureList(source);
            return 0;
        }

        String fileName = texturePath.getFileName().toString();
        int changed = FmeManager.replaceMappedSourceWithCustomTexture(fromBlock, fileName);
        if (changed == 0) {
            source.sendFeedback(Text.literal("No existing FME-mapped blocks used " + fromRaw));
            return 1;
        }

        source.sendFeedback(Text.literal(
            "Updated " + changed + " mapped block" + (changed == 1 ? "" : "s") +
                ": " + fromRaw + " -> " + fileName
        ));
        return 1;
    }

    private static int executeWorldEditList(FabricClientCommandSource source) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            source.sendError(Text.literal("No world loaded."));
            return 0;
        }
        Selection selection = getWorldEditSelection(source);
        if (selection == null) {
            return 0;
        }
        if (selection.volume > WORLD_EDIT_MAX_BLOCKS) {
            source.sendError(Text.literal(
                "Selection too large (" + selection.volume + " blocks). Max is " + WORLD_EDIT_MAX_BLOCKS + "."
            ));
            return 0;
        }

        Map<Identifier, Integer> counts = new HashMap<>();
        long nonAir = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = selection.min.getY(); y <= selection.max.getY(); y++) {
            for (int x = selection.min.getX(); x <= selection.max.getX(); x++) {
                for (int z = selection.min.getZ(); z <= selection.max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    Identifier id = Registries.BLOCK.getId(state.getBlock());
                    counts.merge(id, 1, Integer::sum);
                    nonAir++;
                }
            }
        }

        if (nonAir == 0) {
            source.sendFeedback(Text.literal("No non-air blocks in selection."));
            return 1;
        }

        List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Comparator
            .<Map.Entry<Identifier, Integer>>comparingInt(Map.Entry::getValue)
            .reversed()
            .thenComparing(entry -> entry.getKey().toString()));

        source.sendFeedback(Text.literal(
            "Selection " + selection.sizeX + "x" + selection.sizeY + "x" + selection.sizeZ +
                " (" + selection.volume + " blocks), non-air: " + nonAir +
                ", block types: " + counts.size()
        ));
        int shown = Math.min(entries.size(), WORLD_EDIT_LIST_LIMIT);
        for (int i = 0; i < shown; i++) {
            Map.Entry<Identifier, Integer> entry = entries.get(i);
            source.sendFeedback(Text.literal(entry.getKey() + " x" + entry.getValue()));
        }
        if (entries.size() > WORLD_EDIT_LIST_LIMIT) {
            source.sendFeedback(Text.literal(
                "Showing top " + WORLD_EDIT_LIST_LIMIT + " of " + entries.size() + " block types."
            ));
        }
        return 1;
    }

    private static int executeWorldEditReplace(FabricClientCommandSource source, String fromRaw, String toRaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            source.sendError(Text.literal("No world loaded."));
            return 0;
        }
        Selection selection = getWorldEditSelection(source);
        if (selection == null) {
            return 0;
        }
        if (selection.volume > WORLD_EDIT_MAX_BLOCKS) {
            source.sendError(Text.literal(
                "Selection too large (" + selection.volume + " blocks). Max is " + WORLD_EDIT_MAX_BLOCKS + "."
            ));
            return 0;
        }

        Block fromBlock = parseBlockId(fromRaw);
        Block toBlock = parseBlockId(toRaw);
        if (fromBlock == null) {
            source.sendError(Text.literal("Unknown block id: " + fromRaw));
            return 0;
        }
        if (toBlock == null) {
            source.sendError(Text.literal("Unknown block id: " + toRaw));
            return 0;
        }
        if (fromBlock == toBlock) {
            source.sendError(Text.literal("From and to blocks are the same."));
            return 0;
        }

        int changed = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        FmeManager.beginBatch();
        try {
            for (int y = selection.min.getY(); y <= selection.max.getY(); y++) {
                for (int x = selection.min.getX(); x <= selection.max.getX(); x++) {
                    for (int z = selection.min.getZ(); z <= selection.max.getZ(); z++) {
                        pos.set(x, y, z);
                        BlockState state = client.world.getBlockState(pos);
                        if (state.getBlock() != fromBlock) {
                            continue;
                        }
                        if (FmeManager.applyReplacementDirect(pos, fromBlock, toBlock, 0)) {
                            changed++;
                        }
                    }
                }
            }
        } finally {
            FmeManager.endBatch();
        }

        if (changed == 0) {
            source.sendFeedback(Text.literal("No matching blocks found in selection."));
            return 1;
        }
        source.sendFeedback(Text.literal(
            "Replaced " + changed + " block" + (changed == 1 ? "" : "s") +
                " in selection: " + fromRaw + " -> " + toRaw
        ));
        return 1;
    }

    private static int executeWorldEditClear(FabricClientCommandSource source) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            source.sendError(Text.literal("No world loaded."));
            return 0;
        }
        Selection selection = getWorldEditSelection(source);
        if (selection == null) {
            return 0;
        }
        if (selection.volume > WORLD_EDIT_MAX_BLOCKS) {
            source.sendError(Text.literal(
                "Selection too large (" + selection.volume + " blocks). Max is " + WORLD_EDIT_MAX_BLOCKS + "."
            ));
            return 0;
        }

        int changed = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        FmeManager.beginBatch();
        try {
            for (int y = selection.min.getY(); y <= selection.max.getY(); y++) {
                for (int x = selection.min.getX(); x <= selection.max.getX(); x++) {
                    for (int z = selection.min.getZ(); z <= selection.max.getZ(); z++) {
                        pos.set(x, y, z);
                        BlockState state = client.world.getBlockState(pos);
                        if (FmeManager.applyReplacementDirect(pos, state.getBlock(), Blocks.BARRIER, 0)) {
                            changed++;
                        }
                    }
                }
            }
        } finally {
            FmeManager.endBatch();
        }

        if (changed == 0) {
            source.sendFeedback(Text.literal("No blocks changed in selection."));
            return 1;
        }
        source.sendFeedback(Text.literal(
            "Replaced " + changed + " block" + (changed == 1 ? "" : "s") + " in selection with barrier."
        ));
        return 1;
    }

    private static int executeWorldEditClearSelection(FabricClientCommandSource source) {
        worldEditPos1 = null;
        worldEditPos2 = null;
        worldEditPos3 = null;
        source.sendFeedback(Text.literal("WorldEdit selection cleared."));
        return 1;
    }

    private static Selection getWorldEditSelection(FabricClientCommandSource source) {
        Selection selection = getWorldEditSelectionForRender();
        if (selection == null) {
            source.sendError(Text.literal("Set /fme worldedit pos1 and pos2 first."));
        }
        return selection;
    }

    static Selection getWorldEditSelectionForRender() {
        if (worldEditPos1 == null || worldEditPos2 == null) {
            return null;
        }
        int minX = Math.min(worldEditPos1.getX(), worldEditPos2.getX());
        int minY = Math.min(worldEditPos1.getY(), worldEditPos2.getY());
        int minZ = Math.min(worldEditPos1.getZ(), worldEditPos2.getZ());
        int maxX = Math.max(worldEditPos1.getX(), worldEditPos2.getX());
        int maxY = Math.max(worldEditPos1.getY(), worldEditPos2.getY());
        int maxZ = Math.max(worldEditPos1.getZ(), worldEditPos2.getZ());
        if (worldEditPos3 != null) {
            maxY = Math.max(minY, worldEditPos3.getY());
        }
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        return new Selection(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), sizeX, sizeY, sizeZ, volume);
    }

    private static void sendTextureList(FabricClientCommandSource source) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (java.nio.file.Path path : HatTextureManager.listTextures()) {
            names.add(path.getFileName().toString());
        }
        if (names.isEmpty()) {
            source.sendFeedback(Text.literal("No custom textures found."));
            return;
        }
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        source.sendFeedback(Text.literal("Available custom textures (" + names.size() + "):"));
        source.sendFeedback(Text.literal(String.join(", ", names)));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTextureFiles(
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        for (java.nio.file.Path path : HatTextureManager.listTextures()) {
            builder.suggest(path.getFileName().toString());
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestImageFiles(
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        for (java.nio.file.Path path : FmeImageManager.listImages()) {
            builder.suggest(path.getFileName().toString());
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSchematicFiles(
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        for (String name : FmeSchematicImporter.listSchematics()) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static int executeSchematicPaste(FabricClientCommandSource source, String fileRaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            source.sendError(Text.literal("No player found."));
            return 0;
        }
        if (fileRaw == null || fileRaw.isBlank()) {
            source.sendError(Text.literal("Missing schematic file name."));
            return 0;
        }
        java.nio.file.Path path = FmeSchematicImporter.resolveSchematicPath(fileRaw);
        Direction right = client.player.getHorizontalFacing().rotateYClockwise();
        BlockPos origin = client.player.getBlockPos().offset(right);
        FmeSchematicImporter.PasteResult result = FmeSchematicImporter.pasteFromFile(
            path,
            origin,
            schematicRotationTurns
        );
        if (result.failed()) {
            source.sendError(Text.literal(result.error()));
            return 0;
        }
        source.sendFeedback(Text.literal(
            "Pasted schematic: " + result.placed() + " blocks (" + result.skipped() + " skipped)."
        ));
        source.sendFeedback(Text.literal(
            "Origin was one block to your right. Rotation: " + (schematicRotationTurns * 90) + "°."
        ));
        return 1;
    }

    private static int executeSchematicUndo(FabricClientCommandSource source) {
        FmeSchematicImporter.PasteResult result = FmeSchematicImporter.undoLastPaste();
        if (result.failed()) {
            source.sendError(Text.literal(result.error()));
            return 0;
        }
        source.sendFeedback(Text.literal("Undid last schematic paste (" + result.placed() + " blocks removed)."));
        return 1;
    }

    private static int executeAddUrl(FabricClientCommandSource source, String urlRaw) {
        if (urlRaw == null || urlRaw.isBlank()) {
            source.sendError(Text.literal("Missing URL."));
            return 0;
        }
        String resolved = normalizeImageUrl(urlRaw.trim());
        if (resolved == null) {
            source.sendError(Text.literal("Unsupported URL. Use a direct image link or a single Imgur image."));
            return 0;
        }
        source.sendFeedback(Text.literal("Downloading image..."));
        java.util.concurrent.CompletableFuture.runAsync(
            () -> downloadImageToCustomTextures(source, resolved),
            DOWNLOAD_EXECUTOR
        );
        return 1;
    }

    private static int executeImageImport(
        FabricClientCommandSource source,
        String imageFile,
        String orientationRaw,
        Integer width,
        Integer height
    ) {
        if (imageFile == null || imageFile.isBlank()) {
            source.sendError(Text.literal("Missing image file name."));
            sendImageList(source);
            return 0;
        }
        java.nio.file.Path imagePath = FmeImageManager.resolveImagePath(imageFile.trim());
        if (imagePath == null) {
            source.sendError(Text.literal("Unknown image: " + imageFile));
            sendImageList(source);
            return 0;
        }
        ImageOrientation orientation = parseOrientation(orientationRaw);
        if (orientation == null) {
            source.sendError(Text.literal("Unknown orientation: " + orientationRaw + " (use vertical or horizontal)."));
            return 0;
        }
        boolean imported = importImageToFme(source, imagePath, orientation, width, height);
        if (!imported) {
            source.sendError(Text.literal("Failed to import image: " + imagePath.getFileName()));
            return 0;
        }
        source.sendFeedback(Text.literal(
            "Imported image " + imagePath.getFileName() + " (" + orientation.name().toLowerCase(Locale.ROOT) + ")"
        ));
        return 1;
    }

    private static boolean importImageToFme(
        FabricClientCommandSource source,
        java.nio.file.Path imagePath,
        ImageOrientation orientation,
        Integer targetWidth,
        Integer targetHeight
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            source.sendError(Text.literal("No world loaded."));
            return false;
        }
        if (!FmeManager.isEnabled()) {
            FmeManager.toggleEnabled();
        }
        NativeImage image = null;
        try {
            image = FmeImageManager.readImage(imagePath);
            if (image == null) {
                return false;
            }
            int sourceWidth = image.getWidth();
            int sourceHeight = image.getHeight();
            int tw = targetWidth != null ? targetWidth : sourceWidth;
            int th = targetHeight != null ? targetHeight : sourceHeight;
            if (targetWidth == null && targetHeight == null) {
                int maxPixels = 128 * 128;
                long total = (long) sourceWidth * sourceHeight;
                if (total > maxPixels) {
                    double scale = Math.sqrt((double) maxPixels / total);
                    tw = Math.max(1, (int) Math.round(sourceWidth * scale));
                    th = Math.max(1, (int) Math.round(sourceHeight * scale));
                    source.sendFeedback(Text.literal(
                        "Auto-scaled image to " + tw + "x" + th + " for performance."
                    ));
                }
            }
            if (tw <= 0 || th <= 0) {
                return false;
            }
            if (tw != sourceWidth || th != sourceHeight) {
                NativeImage scaled = scaleImage(image, tw, th);
                image.close();
                image = scaled;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            long total = (long) width * height;
            if (total > WORLD_EDIT_MAX_BLOCKS) {
                source.sendError(Text.literal(
                    "Image too large (" + total + " pixels). Max is " + WORLD_EDIT_MAX_BLOCKS + "."
                ));
                return false;
            }

            List<PaletteEntry> palette = buildPalette(client);
            if (palette.isEmpty()) {
                source.sendError(Text.literal("Palette is empty."));
                return false;
            }

            Direction forward = client.player.getHorizontalFacing();
            Direction right = forward.rotateYClockwise();
            BlockPos origin = client.player.getBlockPos().offset(right, 1);
            Direction widthDir = forward;
            Direction heightDir = orientation == ImageOrientation.VERTICAL ? Direction.UP : right;
            int baseY = origin.getY();

            FmeManager.beginBatch();
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = image.getColorArgb(x, y);
                        int alpha = (argb >>> 24) & 0xFF;
                        if (alpha < 10) {
                            continue;
                        }
                        int rgb = argb & 0xFFFFFF;
                        Block mapped = findClosestBlock(palette, rgb);
                        if (mapped == null) {
                            continue;
                        }
                        BlockPos pos;
                        if (orientation == ImageOrientation.VERTICAL) {
                            int up = height - 1 - y;
                            pos = origin.offset(widthDir, x).up(up);
                        } else {
                            pos = new BlockPos(
                                origin.getX() + widthDir.getOffsetX() * x + heightDir.getOffsetX() * y,
                                baseY,
                                origin.getZ() + widthDir.getOffsetZ() * x + heightDir.getOffsetZ() * y
                            );
                        }
                        FmeManager.applyReplacementDirect(pos, Blocks.AIR, mapped, 0);
                    }
                }
            } finally {
                FmeManager.endBatch();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private static List<PaletteEntry> buildPalette(MinecraftClient client) {
        List<PaletteEntry> palette = new ArrayList<>();
        for (DyeColor dye : DyeColor.values()) {
            String name = dye.toString().toLowerCase(Locale.ROOT);
            addPaletteBlock(client, palette, name + "_concrete");
            addPaletteBlock(client, palette, name + "_wool");
            addPaletteBlock(client, palette, name + "_terracotta");
            addPaletteBlock(client, palette, name + "_concrete_powder");
            addPaletteBlock(client, palette, name + "_stained_glass");
        }
        return palette;
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

    private static void addPaletteBlock(MinecraftClient client, List<PaletteEntry> palette, String blockName) {
        Identifier id = Identifier.tryParse("minecraft:" + blockName);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            return;
        }
        Block block = Registries.BLOCK.get(id);
        if (block == null || block == Blocks.AIR) {
            return;
        }
        int color = block.getDefaultState().getMapColor(client.world, BlockPos.ORIGIN).color;
        palette.add(new PaletteEntry(block, color));
    }

    private static Block findClosestBlock(List<PaletteEntry> palette, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        Block best = null;
        int bestDist = Integer.MAX_VALUE;
        for (PaletteEntry entry : palette) {
            int pr = (entry.rgb() >> 16) & 0xFF;
            int pg = (entry.rgb() >> 8) & 0xFF;
            int pb = entry.rgb() & 0xFF;
            int dr = r - pr;
            int dg = g - pg;
            int db = b - pb;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = entry.block();
            }
        }
        return best;
    }

    private static ImageOrientation parseOrientation(String raw) {
        if (raw == null || raw.isBlank()) {
            return ImageOrientation.VERTICAL;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "vertical", "wall" -> ImageOrientation.VERTICAL;
            case "horizontal", "floor" -> ImageOrientation.HORIZONTAL;
            default -> null;
        };
    }

    private static void sendImageList(FabricClientCommandSource source) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (java.nio.file.Path path : FmeImageManager.listImages()) {
            names.add(path.getFileName().toString());
        }
        if (names.isEmpty()) {
            source.sendFeedback(Text.literal("No images found. Drop PNG/JPG into: " + FmeImageManager.getImageDir()));
            return;
        }
        java.util.Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        source.sendFeedback(Text.literal("Available images (" + names.size() + "):"));
        source.sendFeedback(Text.literal(String.join(", ", names)));
    }

    private enum ImageOrientation {
        VERTICAL,
        HORIZONTAL
    }

    private record PaletteEntry(Block block, int rgb) {
    }

    private static void downloadImageToCustomTextures(FabricClientCommandSource source, String url) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build();
        java.net.http.HttpRequest request;
        try {
            request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();
        } catch (IllegalArgumentException ex) {
            runOnClient(() -> source.sendError(Text.literal("Invalid URL: " + url)));
            return;
        }

        java.net.http.HttpResponse<byte[]> response;
        try {
            response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception ex) {
            runOnClient(() -> source.sendError(Text.literal("Failed to download image.")));
            return;
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            runOnClient(() -> source.sendError(Text.literal("Download failed (HTTP " + response.statusCode() + ").")));
            return;
        }

        String contentType = response.headers().firstValue("content-type").orElse("");
        String ext = fileExtensionFromContentType(contentType);
        String nameFromUrl = fileNameFromUrl(url);
        if (ext == null) {
            ext = fileExtensionFromName(nameFromUrl);
        }
        if (ext == null) {
            runOnClient(() -> source.sendError(Text.literal("Unsupported image type. Use PNG or JPG.")));
            return;
        }
        if (!ext.equals(".png") && !ext.equals(".jpg") && !ext.equals(".jpeg")) {
            runOnClient(() -> source.sendError(Text.literal("Unsupported image type. Use PNG or JPG.")));
            return;
        }

        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            runOnClient(() -> source.sendError(Text.literal("Empty image response.")));
            return;
        }
        if (bytes.length > 10 * 1024 * 1024) {
            runOnClient(() -> source.sendError(Text.literal("Image too large (max 10MB).")));
            return;
        }

        String baseName = sanitizeFileName(nameFromUrl);
        if (baseName == null || baseName.isBlank()) {
            baseName = "image";
        }
        if (!baseName.toLowerCase(java.util.Locale.ROOT).endsWith(ext)) {
            baseName = stripExtension(baseName) + ext;
        }

        java.nio.file.Path dir = HatTextureManager.getTextureDir();
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException ex) {
            runOnClient(() -> source.sendError(Text.literal("Could not create texture directory.")));
            return;
        }

        java.nio.file.Path target = uniquePath(dir, baseName);
        try {
            java.nio.file.Files.write(target, bytes);
        } catch (java.io.IOException ex) {
            runOnClient(() -> source.sendError(Text.literal("Failed to save image.")));
            return;
        }

        String fileName = target.getFileName().toString();
        runOnClient(() -> {
            HatTextureManager.selectTexture(target);
            FmeManager.selectCustomTexture(target);
            source.sendFeedback(Text.literal("Added custom texture: " + fileName));
        });
    }

    private static void runOnClient(Runnable task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.execute(task);
        }
    }

    private static String normalizeImageUrl(String raw) {
        try {
            java.net.URI uri = java.net.URI.create(raw);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            String lowerHost = host.toLowerCase(java.util.Locale.ROOT);
            if (lowerHost.endsWith("imgur.com") && !lowerHost.startsWith("i.")) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                String[] parts = path.split("/");
                String id = parts.length > 0 ? parts[parts.length - 1] : "";
                if (id.isBlank() || "gallery".equalsIgnoreCase(id) || "a".equalsIgnoreCase(id)) {
                    return null;
                }
                if (id.contains(".")) {
                    return "https://i.imgur.com/" + id;
                }
                return "https://i.imgur.com/" + id + ".jpg";
            }
            return raw;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String fileNameFromUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return "image";
            }
            int slash = path.lastIndexOf('/');
            if (slash >= 0 && slash + 1 < path.length()) {
                return path.substring(slash + 1);
            }
            return "image";
        } catch (Exception ex) {
            return "image";
        }
    }

    private static String fileExtensionFromContentType(String contentType) {
        String ct = contentType.toLowerCase(java.util.Locale.ROOT);
        if (ct.startsWith("image/png")) {
            return ".png";
        }
        if (ct.startsWith("image/jpeg") || ct.startsWith("image/jpg")) {
            return ".jpg";
        }
        return null;
    }

    private static String fileExtensionFromName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return null;
        }
        String ext = name.substring(dot).toLowerCase(java.util.Locale.ROOT);
        if (ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg")) {
            return ext;
        }
        return null;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private static String sanitizeFileName(String name) {
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < cleaned.length()) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "image" : cleaned;
    }

    private static java.nio.file.Path uniquePath(java.nio.file.Path dir, String fileName) {
        java.nio.file.Path candidate = dir.resolve(fileName);
        if (!java.nio.file.Files.exists(candidate)) {
            return candidate;
        }
        String base = stripExtension(fileName);
        String ext = fileExtensionFromName(fileName);
        if (ext == null) {
            ext = "";
        }
        for (int i = 1; i < 1000; i++) {
            java.nio.file.Path next = dir.resolve(base + "_" + i + ext);
            if (!java.nio.file.Files.exists(next)) {
                return next;
            }
        }
        return candidate;
    }

    private static Block parseBlockId(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            return null;
        }
        return Registries.BLOCK.get(id);
    }

    static record Selection(BlockPos min, BlockPos max, int sizeX, int sizeY, int sizeZ, long volume) {
    }

    private static String selectedSourceLabel() {
        if (FmeManager.getSelectedSourceType() == FmeManager.SelectedSourceType.CUSTOM_TEXTURE) {
            String name = FmeManager.getSelectedCustomTextureName();
            if (name != null && !name.isBlank()) {
                return "custom texture " + name;
            }
            return "custom texture";
        }
        return FmeManager.getSelectedSource().getName().getString() + " texture";
    }

    private static BlockPos findFmeMiddleClickSupport(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        if (client.crosshairTarget instanceof BlockHitResult hitResult
            && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            if (!state.isAir() || FmeManager.positionReplacementsView().containsKey(pos.asLong())) {
                return pos;
            }
        }

        double reach = 6.0;
        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d look = client.player.getRotationVec(1.0f);
        for (double dist = 0.0; dist <= reach; dist += 0.2) {
            BlockPos pos = BlockPos.ofFloored(start.add(look.multiply(dist)));
            if (FmeManager.positionReplacementsView().containsKey(pos.asLong())) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos findMappedPosInSight(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        if (client.crosshairTarget instanceof BlockHitResult hitResult
            && client.crosshairTarget.getType() == HitResult.Type.BLOCK
            && FmeManager.positionReplacementsView().containsKey(hitResult.getBlockPos().asLong())) {
            return hitResult.getBlockPos();
        }

        double reach = 6.0;
        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d look = client.player.getRotationVec(1.0f);
        for (double dist = 0.0; dist <= reach; dist += 0.2) {
            BlockPos pos = BlockPos.ofFloored(start.add(look.multiply(dist)));
            if (FmeManager.positionReplacementsView().containsKey(pos.asLong())) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos findMappedOrCustomPosInSight(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        if (client.crosshairTarget instanceof BlockHitResult hitResult
            && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            long key = hitResult.getBlockPos().asLong();
            if (FmeManager.positionReplacementsView().containsKey(key)
                || FmeManager.customTextureReplacementsView().containsKey(key)) {
                return hitResult.getBlockPos();
            }
        }

        double reach = 6.0;
        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d look = client.player.getRotationVec(1.0f);
        for (double dist = 0.0; dist <= reach; dist += 0.2) {
            BlockPos pos = BlockPos.ofFloored(start.add(look.multiply(dist)));
            long key = pos.asLong();
            if (FmeManager.positionReplacementsView().containsKey(key)
                || FmeManager.customTextureReplacementsView().containsKey(key)) {
                return pos;
            }
        }
        return null;
    }

    private static GhostHit findGhostHit(MinecraftClient client, double reach) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d look = client.player.getRotationVec(1.0f);
        double step = 0.1;
        for (double dist = 0.0; dist <= reach; dist += step) {
            Vec3d posVec = start.add(look.multiply(dist));
            BlockPos pos = BlockPos.ofFloored(posVec);
            if (!FmeManager.isAirGhostPosition(pos)) {
                continue;
            }
            Direction side = faceFromHit(posVec, pos);
            if (side == null) {
                continue;
            }
            return new GhostHit(pos, side);
        }
        return null;
    }

    private static Direction faceFromHit(Vec3d hit, BlockPos blockPos) {
        double cx = blockPos.getX() + 0.5;
        double cy = blockPos.getY() + 0.5;
        double cz = blockPos.getZ() + 0.5;
        double dx = hit.x - cx;
        double dy = hit.y - cy;
        double dz = hit.z - cz;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= az) {
            return dy >= 0 ? Direction.UP : Direction.DOWN;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private record GhostHit(BlockPos pos, Direction side) {
    }
}
