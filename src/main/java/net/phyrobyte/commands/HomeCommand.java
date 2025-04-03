package net.phyrobyte.commands;

import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "homeland", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HomeCommand {

    public static class HomeData {
        public final BlockPos pos;
        public final float yaw;
        public final float pitch;

        public HomeData(BlockPos pos, float yaw, float pitch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final Map<UUID, HomeData> homeDataMap = new HashMap<>();
    private static final Map<UUID, BlockPos> deathLocations = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MinecraftServer server;

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("home")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> teleportHome(ctx.getSource()))
                        .then(Commands.literal("set")
                                .executes(ctx -> setHome(ctx.getSource()))
                        )
        );

        event.getDispatcher().register(
                Commands.literal("tpdeath")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .executes(ctx -> tpDeath(ctx.getSource()))
        );
    }

    private static int setHome(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        assert player != null;
        BlockPos pos = player.blockPosition();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        UUID uuid = player.getUUID();

        homeDataMap.put(uuid, new HomeData(pos, yaw, pitch));
        saveHomes();

        source.sendSuccess(() -> Component.literal("Home set at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        return 1;
    }

    private static int teleportHome(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        UUID uuid = player.getUUID();

        if (!homeDataMap.containsKey(uuid)) {
            source.sendFailure(Component.literal("You haven't set a home yet! Use /home set"));
            return 0;
        }

        HomeData home = homeDataMap.get(uuid);
        player.teleportTo(player.serverLevel(), home.pos.getX() + 0.5, home.pos.getY(), home.pos.getZ() + 0.5, home.yaw, home.pitch);
        source.sendSuccess(() -> Component.literal("Teleported to home!"), false);
        return 1;
    }

    private static int tpDeath(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        assert player != null;

        if (!deathLocations.containsKey(player.getUUID())) {
            source.sendFailure(Component.literal("You haven't died yet!"));
            return 0;
        }

        BlockPos deathLocation = deathLocations.get(player.getUUID());
        player.teleportTo(deathLocation.getX() + 0.5, deathLocation.getY(), deathLocation.getZ() + 0.5);
        source.sendSuccess(() -> Component.literal("Teleported to your death location!"), false);
        return 1;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        loadHomes();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        saveHomes();
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            deathLocations.put(player.getUUID(), player.blockPosition());
        }
    }


    private static File getSaveFile() {
        if (server == null) return null;
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return worldDir.resolve("homes/homes.json").toFile();
    }

    private static void saveHomes() {
        File file = getSaveFile();
        if (file == null) return;

        file.getParentFile().mkdirs();

        JsonObject json = new JsonObject();
        for (Map.Entry<UUID, HomeData> entry : homeDataMap.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", entry.getValue().pos.getX());
            obj.addProperty("y", entry.getValue().pos.getY());
            obj.addProperty("z", entry.getValue().pos.getZ());
            obj.addProperty("yaw", entry.getValue().yaw);
            obj.addProperty("pitch", entry.getValue().pitch);
            json.add(entry.getKey().toString(), obj);
        }

        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            System.err.println("Failed to save homes: " + e.getMessage());
        }
    }

    private static void loadHomes() {
        File file = getSaveFile();
        if (file == null || !file.exists()) return;

        try (Reader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject obj = entry.getValue().getAsJsonObject();
                BlockPos pos = new BlockPos(
                        obj.get("x").getAsInt(),
                        obj.get("y").getAsInt(),
                        obj.get("z").getAsInt()
                );
                float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0f;
                float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0f;
                homeDataMap.put(uuid, new HomeData(pos, yaw, pitch));
            }
        } catch (IOException e) {
            System.err.println("Failed to load homes: " + e.getMessage());
        }
    }


    public static Map<UUID, HomeData> getAllHomes() {
        return new HashMap<>(homeDataMap);
    }
}