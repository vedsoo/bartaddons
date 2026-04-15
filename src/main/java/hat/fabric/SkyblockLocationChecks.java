package hat.fabric;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

public final class SkyblockLocationChecks {
    private static final BlockPos FLOOR7_BOSS_MIN = new BlockPos(-8, 0, -8);
    private static final BlockPos FLOOR7_BOSS_MAX = new BlockPos(134, 254, 147);
    private static final Box[] P3_SECTIONS = {
        new Box(new BlockPos(90, 158, 123), new BlockPos(111, 105, 32)),
        new Box(new BlockPos(16, 158, 122), new BlockPos(111, 105, 143)),
        new Box(new BlockPos(19, 158, 48), new BlockPos(-3, 106, 142)),
        new Box(new BlockPos(91, 158, 50), new BlockPos(-3, 106, 30))
    };

    private SkyblockLocationChecks() {
    }

    public static boolean isOnHypixel() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            return false;
        }

        String brand = networkHandler.getBrand();
        if (brand != null && brand.toLowerCase(Locale.ROOT).contains("hypixel")) {
            return true;
        }

        ServerInfo serverInfo = networkHandler.getServerInfo();
        return serverInfo != null
            && serverInfo.address != null
            && serverInfo.address.toLowerCase(Locale.ROOT).contains("hypixel");
    }

    public static boolean isInDungeonM7OrF7Boss() {
        if (!isOnHypixel()) {
            return false;
        }

        String floor = getDungeonFloor();
        if (!"F7".equals(floor) && !"M7".equals(floor)) {
            return false;
        }

        Vec3d pos = playerPos();
        return pos != null && isInside(pos, FLOOR7_BOSS_MIN, FLOOR7_BOSS_MAX);
    }

    public static boolean isInP3Sim() {
        Vec3d pos = playerPos();
        if (pos == null) {
            return false;
        }

        for (Box section : P3_SECTIONS) {
            if (isInside(pos, section.a, section.b)) {
                return true;
            }
        }
        return false;
    }

    public static Integer getP3Section() {
        Vec3d pos = playerPos();
        if (pos == null) {
            return null;
        }

        for (int i = 0; i < P3_SECTIONS.length; i++) {
            Box section = P3_SECTIONS[i];
            if (isInside(pos, section.a, section.b)) {
                return i + 1;
            }
        }
        return null;
    }

    public static String getDungeonFloor() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) {
            return null;
        }

        for (PlayerListEntry entry : networkHandler.getPlayerList()) {
            String line = clean(playerListText(entry));
            if (!line.contains("The Catacombs (") || line.contains("Queue")) {
                continue;
            }

            int start = line.indexOf('(');
            int end = line.indexOf(')', start + 1);
            if (start < 0 || end < 0 || end <= start + 1) {
                continue;
            }

            return line.substring(start + 1, end).trim();
        }

        return null;
    }

    private static Vec3d playerPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null
            ? new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ())
            : null;
    }

    private static String playerListText(PlayerListEntry entry) {
        Text displayName = entry.getDisplayName();
        if (displayName != null) {
            return displayName.getString();
        }

        GameProfile profile = entry.getProfile();
        return profile != null ? profile.name() : "";
    }

    private static boolean isInside(Vec3d pos, BlockPos a, BlockPos b) {
        double minX = Math.min(a.getX(), b.getX());
        double minY = Math.min(a.getY(), b.getY());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX());
        double maxY = Math.max(a.getY(), b.getY());
        double maxZ = Math.max(a.getZ(), b.getZ());
        return pos.x >= minX && pos.x <= maxX
            && pos.y >= minY && pos.y <= maxY
            && pos.z >= minZ && pos.z <= maxZ;
    }

    private static String clean(String text) {
        return text.replaceAll("(?i)§.", "");
    }

    private record Box(BlockPos a, BlockPos b) {
    }
}
