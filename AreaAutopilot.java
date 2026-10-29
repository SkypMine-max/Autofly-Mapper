package com.example.areaautopilot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AreaAutopilot implements ClientModInitializer {

    // corners: raw values + whether they are chunk coords (true) or block coords (false)
    private static Integer ax, az, bx, bz;
    private static boolean aChunk, bChunk;

    private static int spacing = 32;       // blocks between sweep lanes
    private static float turnSpeed = 4.0f; // max degrees turned per tick

    private static boolean running = false;
    private static List<int[]> path = new ArrayList<>();
    private static int idx = 0;
    private static double prevX, prevZ;    // where the current leg started
    private static double lastX, lastZ;
    private static int stuckTicks = 0, strafeTicks = 0, msgTimer = 0;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(ClientCommands.literal("autopilot")
                .then(corner("a", true, false))
                .then(corner("b", false, false))
                .then(ClientCommands.literal("spacing")
                    .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 512))
                        .executes(ctx -> {
                            spacing = IntegerArgumentType.getInteger(ctx, "n");
                            say(ctx, "Lane spacing set to " + spacing + " blocks.");
                            return 1;
                        })))
                .then(ClientCommands.literal("turnspeed")
                    .then(ClientCommands.argument("deg", IntegerArgumentType.integer(1, 45))
                        .executes(ctx -> {
                            turnSpeed = IntegerArgumentType.getInteger(ctx, "deg");
                            say(ctx, "Turn speed set to " + (int) turnSpeed + " degrees per tick (lower = smoother).");
                            return 1;
                        })))
                .then(ClientCommands.literal("start").executes(ctx -> start(ctx)))
                .then(ClientCommands.literal("stop").executes(ctx -> {
                    stop(Minecraft.getInstance());
                    say(ctx, "Autopilot stopped.");
                    return 1;
                }))
                .then(ClientCommands.literal("status").executes(ctx -> {
                    say(ctx, "A=" + fmt(ax, az, aChunk) + "  B=" + fmt(bx, bz, bChunk)
                        + "  spacing=" + spacing + "  turnspeed=" + (int) turnSpeed
                        + (running ? "  running " + idx + "/" + path.size() : "  idle"));
                    return 1;
                })));
        });
        ClientTickEvents.END_CLIENT_TICK.register(AreaAutopilot::safeTick);
    }

    // "a"/"b" = block coords; no args = where you stand
    private static LiteralArgumentBuilder<FabricClientCommandSource> corner(String name, boolean isA, boolean chunk) {
        return ClientCommands.literal(name)
            .executes(ctx -> setCorner(ctx, isA, chunk, null, null))
            .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                    .executes(ctx -> setCorner(ctx, isA, chunk,
                        IntegerArgumentType.getInteger(ctx, "x"),
                        IntegerArgumentType.getInteger(ctx, "z")))));
    }

    private static int setCorner(CommandContext<FabricClientCommandSource> ctx, boolean isA, boolean chunk,
                                 Integer x, Integer z) {
        if (x == null || z == null) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) return 0;
            Vec3 pos = p.position();
            int bxNow = (int) Math.floor(pos.x());
            int bzNow = (int) Math.floor(pos.z());
            x = chunk ? Math.floorDiv(bxNow, 16) : bxNow;
            z = chunk ? Math.floorDiv(bzNow, 16) : bzNow;
        }
        if (isA) { ax = x; az = z; aChunk = chunk; } else { bx = x; bz = z; bChunk = chunk; }
        say(ctx, (isA ? "Point A" : "Point B") + " set to " + (chunk ? "chunk " : "block ")
            + "X=" + x + " Z=" + z);
        return 1;
    }

    private static int start(CommandContext<FabricClientCommandSource> ctx) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (ax == null || bx == null || p == null) {
            say(ctx, "Set both points first: /autopilot a <x> <z> and /autopilot b <x> <z>");
            return 0;
        }
        buildPath();
        idx = 0; stuckTicks = 0; strafeTicks = 0; running = true;
        Vec3 pos = p.position();
        prevX = pos.x(); prevZ = pos.z();
        say(ctx, "Autopilot started: " + path.size() + " waypoints. /autopilot stop to cancel.");
        return 1;
    }

    // lawnmower sweep; chunk corners expand to cover the full chunk (16x16 blocks)
    private static void buildPath() {
        int loA_x = aChunk ? ax * 16 : ax, hiA_x = aChunk ? ax * 16 + 15 : ax;
        int loB_x = bChunk ? bx * 16 : bx, hiB_x = bChunk ? bx * 16 + 15 : bx;
        int loA_z = aChunk ? az * 16 : az, hiA_z = aChunk ? az * 16 + 15 : az;
        int loB_z = bChunk ? bz * 16 : bz, hiB_z = bChunk ? bz * 16 + 15 : bz;
        int minX = Math.min(loA_x, loB_x), maxX = Math.max(hiA_x, hiB_x);
        int minZ = Math.min(loA_z, loB_z), maxZ = Math.max(hiA_z, hiB_z);

        List<Integer> lanes = new ArrayList<>();
        for (int x = minX; x < maxX; x += spacing) lanes.add(x);
        lanes.add(maxX);

        path = new ArrayList<>();
        boolean forward = true;
        for (int x : lanes) {
            path.add(new int[]{x, forward ? minZ : maxZ});
            path.add(new int[]{x, forward ? maxZ : minZ});
            forward = !forward;
        }
    }

    private static void safeTick(Minecraft mc) {
        if (!running) return;
        try {
            tick(mc);
        } catch (Throwable t) {
            running = false;
            try { releaseKeys(mc); } catch (Throwable ignored) { }
            if (mc != null && mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal("Autopilot error: " + t));
            }
        }
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) { stop(mc); return; }

        if (idx >= path.size()) {
            stop(mc);
            p.sendOverlayMessage(Component.literal("Autopilot: area covered!"));
            return;
        }

        Vec3 pos = p.position();
        int[] target = path.get(idx);
        double tx = target[0] + 0.5, tz = target[1] + 0.5;
        double dx = tx - pos.x();
        double dz = tz - pos.z();
        double dist = Math.hypot(dx, dz);

        // waypoint reached: close enough, or already passed it along this leg (prevents circling)
        double legX = tx - prevX, legZ = tz - prevZ;
        boolean passed = dist < 4.0 && (-dx * legX - dz * legZ) >= 0;
        if (dist < 1.5 || passed) {
            prevX = tx; prevZ = tz;
            idx++;
            return;
        }

        // smooth turning: rotate toward the target at most turnSpeed degrees per tick
        float wanted = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float current = p.getYRot();
        float diff = ((wanted - current + 180f) % 360f + 360f) % 360f - 180f;
        float step = Math.max(-turnSpeed, Math.min(turnSpeed, diff));
        p.setYRot(current + step);
        p.setXRot(10f);

        // while still facing well away from the target, stop and turn first
        boolean facingTarget = Math.abs(diff) < 50f;

        double moved = Math.hypot(pos.x() - lastX, pos.z() - lastZ);
        lastX = pos.x(); lastZ = pos.z();
        if (facingTarget) {
            if (moved < 0.03) stuckTicks++; else stuckTicks = 0;
        } else {
            stuckTicks = 0;
        }
        if (stuckTicks > 60) { strafeTicks = 15; stuckTicks = 0; }

        mc.options.keyUp.setDown(facingTarget);
        mc.options.keySprint.setDown(facingTarget);
        mc.options.keyJump.setDown(stuckTicks > 3 || p.isInWater());
        mc.options.keyLeft.setDown(strafeTicks > 0);
        if (strafeTicks > 0) strafeTicks--;

        if (++msgTimer % 100 == 0) {
            p.sendOverlayMessage(Component.literal("Autopilot: waypoint " + (idx + 1) + "/" + path.size()));
        }
    }

    private static void stop(Minecraft mc) { running = false; releaseKeys(mc); }

    private static void releaseKeys(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyLeft.setDown(false);
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Component.literal(msg));
    }

    private static String fmt(Integer x, Integer z, boolean chunk) {
        return x == null ? "unset" : (chunk ? "chunk (" : "block (") + x + ", " + z + ")";
    }
}
