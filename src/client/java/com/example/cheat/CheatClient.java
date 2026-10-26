package com.example.cheat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Faqat o'z do'stlaring bilan o'ynaydigan serveringda ishlat.
 * /cheat menu | killaura | trigger | aim | arrowaim | automine | esp | fps
 *        | chests | ores | range <son>
 */
public class CheatClient implements ClientModInitializer {
    static boolean killAura = false;
    static boolean trigger = false;
    static boolean aim = false;
    static boolean arrowAim = false;
    static boolean autoMine = false;
    static boolean esp = false;
    static boolean fps = false;
    static double range = 4.0;

    static int oldRd, oldSim;
    static double oldScale;
    static int menuDelay = -1;

    static final Map<Integer, Vec3> lastPos = new HashMap<>();
    static final Map<Integer, Vec3> velocity = new HashMap<>();

    record Hit(BlockPos pos, String name) {}

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommands.literal("cheat")
                .then(ClientCommands.literal("menu").executes(c -> {
                    menuDelay = 2;
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("killaura").executes(c -> {
                    killAura = !killAura;
                    msg(c.getSource(), "KillAura: " + onOff(killAura));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("trigger").executes(c -> {
                    trigger = !trigger;
                    msg(c.getSource(), "Trigger: " + onOff(trigger));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("aim").executes(c -> {
                    aim = !aim;
                    msg(c.getSource(), "Aim: " + onOff(aim));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("arrowaim").executes(c -> {
                    arrowAim = !arrowAim;
                    msg(c.getSource(), "ArrowAim: " + onOff(arrowAim));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("automine").executes(c -> {
                    setAutoMine(!autoMine);
                    msg(c.getSource(), "AutoMine: " + onOff(autoMine));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("esp").executes(c -> {
                    setEsp(!esp);
                    msg(c.getSource(), "ESP: " + onOff(esp));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("fps").executes(c -> {
                    toggleFps();
                    msg(c.getSource(), "FPS boost: " + onOff(fps));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("chests").executes(c -> {
                    listChests(c.getSource());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(ClientCommands.literal("ores").executes(c -> {
                    listOres(c.getSource());
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

    private static String onOff(boolean b) {
        return b ? "YOQILDI" : "OCHIRILDI";
    }

    static void setAutoMine(boolean v) {
        autoMine = v;
        if (!v) Minecraft.getInstance().options.keyAttack.setDown(false);
    }

    static void setEsp(boolean v) {
        esp = v;
        if (!v) setGlow(false);
    }

    private void tick(Minecraft mc) {
        if (menuDelay >= 0) {
            if (menuDelay == 0) mc.setScreen(new Menu());
            menuDelay--;
        }

        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null || mc.gameMode == null) return;

        if (autoMine) mc.options.keyAttack.setDown(mc.screen == null);
        if (esp) setGlow(true);

        if (trigger && mc.screen == null
                && mc.hitResult instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le.isAlive()
                && p.getAttackStrengthScale(0f) >= 1.0f) {
            mc.gameMode.attack(p, ehr.getEntity());
        }

        if (arrowAim) {
            updateVelocities(p, level);
            boolean bow = p.isUsingItem() && p.getMainHandItem().is(Items.BOW);
            boolean xbow = p.getMainHandItem().is(Items.CROSSBOW);
            if (bow || xbow) aimArrow(p, level);
        } else if (!lastPos.isEmpty()) {
            lastPos.clear();
            velocity.clear();
        }

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

        if (aim) aimAt(p, best.getEyePosition());
        if (killAura && p.getAttackStrengthScale(0f) >= 1.0f) {
            mc.gameMode.attack(p, best);
        }
    }

    private static void aimAt(LocalPlayer p, Vec3 to) {
        Vec3 from = p.getEyePosition();
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.setYRot(yaw);
        p.setXRot(pitch);
    }

    private static void updateVelocities(LocalPlayer p, ClientLevel level) {
        List<Entity> list = level.getEntities(
                (Entity) null, p.getBoundingBox().inflate(48), e -> true);
        for (Entity e : list) {
            if (e == p || !(e instanceof LivingEntity)) continue;
            Vec3 now = e.position();
            Vec3 old = lastPos.get(e.getId());
            if (old != null) velocity.put(e.getId(), now.subtract(old));
            lastPos.put(e.getId(), now);
        }
    }

    private static void aimArrow(LocalPlayer p, ClientLevel level) {
        List<Entity> list = level.getEntities(
                (Entity) null, p.getBoundingBox().inflate(40), e -> true);
        Entity best = null;
        double bestD = 40 * 40;
        for (Entity e : list) {
            if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double d = p.distanceToSqr(e);
            if (d < bestD) { bestD = d; best = e; }
        }
        if (best == null) return;

        double speed = 3.0; // to'liq tortilgan kamon o'qi (blok/tik)
        Vec3 center = best.getBoundingBox().getCenter();
        Vec3 v = velocity.getOrDefault(best.getId(), Vec3.ZERO);
        Vec3 eye = p.getEyePosition();

        Vec3 target = center;
        for (int i = 0; i < 3; i++) {
            double t = eye.distanceTo(target) / speed;
            target = center.add(v.scale(t)).add(0, 0.5 * 0.05 * t * t, 0);
        }
        aimAt(p, target);
    }

    private static void setGlow(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;
        List<Entity> list = level.getEntities(
                (Entity) null, p.getBoundingBox().inflate(64), e -> true);
        for (Entity e : list) {
            if (e != p && e instanceof LivingEntity) e.setGlowingTag(on);
        }
    }

    static void toggleFps() {
        Minecraft mc = Minecraft.getInstance();
        if (!fps) {
            oldRd = mc.options.renderDistance().get();
            oldSim = mc.options.simulationDistance().get();
            oldScale = mc.options.entityDistanceScaling().get();
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(5);
            mc.options.entityDistanceScaling().set(0.5);
            fps = true;
        } else {
            mc.options.renderDistance().set(oldRd);
            mc.options.simulationDistance().set(oldSim);
            mc.options.entityDistanceScaling().set(oldScale);
            fps = false;
        }
    }

    private static String oreName(BlockState s) {
        if (s.is(BlockTags.DIAMOND_ORES)) return "Olmos";
        if (s.is(BlockTags.EMERALD_ORES)) return "Zumrad";
        if (s.is(BlockTags.GOLD_ORES)) return "Oltin";
        if (s.is(BlockTags.IRON_ORES)) return "Temir";
        if (s.is(Blocks.ANCIENT_DEBRIS)) return "Ancient debris";
        return null;
    }

    private void listOres(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (p == null || level == null) return;

        int r = 20;
        BlockPos c = p.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        List<Hit> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    String n = oreName(level.getBlockState(m));
                    if (n != null) found.add(new Hit(m.immutable(), n));
                }
            }
        }
        found.sort(Comparator.comparingDouble(h -> h.pos().distSqr(c)));
        msg(src, "Ruda: " + found.size());
        for (int i = 0; i < Math.min(10, found.size()); i++) {
            Hit h = found.get(i);
            int dist = (int) Math.sqrt(h.pos().distSqr(c));
            msg(src, h.name() + "  " + h.pos().getX() + " " + h.pos().getY() + " "
                    + h.pos().getZ() + "  (" + dist + " blok)");
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

    /** Tugmali menyu oynasi. */
    static class Menu extends Screen {
        Menu() {
            super(Component.literal("Cheat menu"));
        }

        private static Component label(String name, boolean on) {
            return Component.literal(name + ": " + (on ? "ON" : "OFF"));
        }

        private void toggle(String name, int x, int y, BooleanSupplier get, Runnable flip) {
            Button b = Button.builder(label(name, get.getAsBoolean()), btn -> {
                flip.run();
                btn.setMessage(label(name, get.getAsBoolean()));
            }).bounds(x, y, 150, 20).build();
            this.addRenderableWidget(b);
        }

        @Override
        protected void init() {
            int left = this.width / 2 - 155;
            int right = this.width / 2 + 5;
            int top = this.height / 2 - 70;

            toggle("KillAura", left, top, () -> killAura, () -> killAura = !killAura);
            toggle("Trigger", right, top, () -> trigger, () -> trigger = !trigger);
            toggle("Aim", left, top + 24, () -> aim, () -> aim = !aim);
            toggle("ArrowAim", right, top + 24, () -> arrowAim, () -> arrowAim = !arrowAim);
            toggle("AutoMine", left, top + 48, () -> autoMine, () -> setAutoMine(!autoMine));
            toggle("ESP", right, top + 48, () -> esp, () -> setEsp(!esp));
            toggle("FPS boost", left, top + 72, () -> fps, CheatClient::toggleFps);

            Button rangeBtn = Button.builder(Component.literal("Range: " + range),
                    btn -> {}).bounds(right + 40, top + 72, 70, 20).build();
            rangeBtn.active = false;
            this.addRenderableWidget(rangeBtn);

            this.addRenderableWidget(Button.builder(Component.literal("-"), btn -> {
                range = Math.max(1.0, range - 0.5);
                rangeBtn.setMessage(Component.literal("Range: " + range));
            }).bounds(right, top + 72, 36, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("+"), btn -> {
                range = Math.min(6.0, range + 0.5);
                rangeBtn.setMessage(Component.literal("Range: " + range));
            }).bounds(right + 114, top + 72, 36, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Yopish"),
                    btn -> this.onClose()).bounds(this.width / 2 - 75, top + 104, 150, 20).build());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
                        }
