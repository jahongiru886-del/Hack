package com.example.cheat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Faqat o'z do'stlaring bilan o'ynaydigan serveringda ishlat.
 * Buyruqlar (chatda): /cheat killaura | aim | chests | range <son>
 */
public class CheatClient implements ClientModInitializer {
    static boolean killAura = false;
    static boolean aim = false;
    static double range = 4.0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("cheat")
                .then(ClientCommands.literal("killaura").executes(c -> {
                    killAura = !killAura;
                    msg(c.getSource(), "KillAura: " + (killAura ? "YOQILDI" : "OCHIRILDI"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("aim").executes(c -> {
                    aim = !aim;
                    msg(c.getSource(), "Aim: " + (aim ? "YOQILDI" : "OCHIRILDI"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("chests").executes(c -> {
                    listChests(c.getSource());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("range")
                    .then(ClientCommands.argument("n", DoubleArgumentType.doubleArg(1, 6))
                        .executes(c -> {
                            range = DoubleArgumentType.getDouble(c, "n");
                            msg(c.getSource(), "Range: " + range);
                            return Command.SINGLE_SUCCESS;
                        })))));
    }

    private void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.gameMode == null) return;
        if (!killAura && !aim) return;

        List<Entity> nearby = level.getEntities(
                (Entity) null, p.getBoundingBox().inflate(range), e -> true);

        Entity best = null;
        double bestDist = range * range;
        for (Entity e : nearby) {
            if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double d = p.distanceToSqr(e);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best == null) return;

        if (aim) {
            Vec3 from = p.getEyePosition();
            Vec3 to = best.getEyePosition();
            double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            p.setYRot(yaw);
            p.setXRot(pitch);
        }
        if (killAura && p.getAttackStrengthScale(0f) >= 1.0f) {
            mc.gameMode.attack(p, best);
            p.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void listChests(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        int rd = mc.options.renderDistance().get();
        ChunkPos cp = p.chunkPosition();
        List<BlockEntity> found = new ArrayList<>();
        for (int x = -rd; x <= rd; x++) {
            for (int z = -rd; z <= rd; z++) {
                LevelChunk ch = level.getChunkSource().getChunkNow(cp.x() + x, cp.z() + z);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (be instanceof ChestBlockEntity) found.add(be);
                }
            }
        }
        found.sort(Comparator.comparingDouble(
                be -> be.getBlockPos().distSqr(p.blockPosition())));
        msg(src, "Sandiqlar: " + found.size());
        for (int i = 0; i < Math.min(8, found.size()); i++) {
            var pos = found.get(i).getBlockPos();
            int dist = (int) Math.sqrt(pos.distSqr(p.blockPosition()));
            msg(src, pos.getX() + " " + pos.getY() + " " + pos.getZ() + "  (" + dist + " blok)");
        }
    }

    private static void msg(FabricClientCommandSource src, String s) {
        src.sendFeedback(Component.literal("[Cheat] " + s));
    }
                                }
