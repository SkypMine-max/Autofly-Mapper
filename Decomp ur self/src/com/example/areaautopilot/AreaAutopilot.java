package com.example.areaautopilot;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class AreaAutopilot implements ClientModInitializer {
   private static Integer ax;
   private static Integer az;
   private static Integer bx;
   private static Integer bz;
   private static boolean aChunk;
   private static boolean bChunk;
   private static int spacing = 32;
   private static float turnSpeed = 4.0F;
   private static boolean elytra = false;
   private static Integer altitude = null;
   private static boolean running = false;
   private static List<int[]> path = new ArrayList<>();
   private static int idx = 0;
   private static double prevX;
   private static double prevZ;
   private static double lastX;
   private static double lastY;
   private static double lastZ;
   private static int stuckTicks = 0;
   private static int strafeTicks = 0;
   private static int msgTimer = 0;
   private static double targetY = 0.0;
   private static int takeoffTick = 0;
   private static int rocketCooldown = 0;
   private static int boostTicks = 0;
   private static final double BAND = 20.0;
   private static int minDurability = 30;
   private static int minRockets = 0;
   private static boolean landing = false;
   private static int landTicks = 0;
   private static String landReason = "";
   private static int supplyTimer = 0;
   private static int swapCooldown = 0;
   private static boolean swapBroken = false;
   private static boolean swapWarned = false;
   private static int totalRockets = 0;
   private static int handRockets = 0;
   private static boolean rocketsReliable = true;
   private static int elytraLeft = -1;
   private static Class<?> heightmapTypes;
   private static Object motionBlocking;
   private static final Map<String, Method> METHOD_CACHE = new HashMap<>();

   public void onInitializeClient() {
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, buildContext) -> {
               dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(
                                                         "autopilot"
                                                      )
                                                      .then(corner("a", true, false)))
                                                   .then(corner("b", false, false)))
                                                .then(
                                                   ClientCommands.literal("spacing")
                                                      .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 512)).executes(c -> {
                                                         spacing = IntegerArgumentType.getInteger(c, "n");
                                                         say(c, "Lane spacing set to " + spacing + " blocks.");
                                                         return 1;
                                                      }))
                                                ))
                                             .then(
                                                ClientCommands.literal("turnspeed")
                                                   .then(ClientCommands.argument("deg", IntegerArgumentType.integer(1, 45)).executes(c -> {
                                                      turnSpeed = IntegerArgumentType.getInteger(c, "deg");
                                                      say(c, "Turn speed set to " + (int)turnSpeed + " degrees per tick (lower = smoother).");
                                                      return 1;
                                                   }))
                                             ))
                                          .then(
                                             ClientCommands.literal("elytra").then(ClientCommands.argument("enabled", BoolArgumentType.bool()).executes(c -> {
                                                elytra = BoolArgumentType.getBool(c, "enabled");
                                                say(c, "Elytra mode " + (elytra ? "ON (wear an elytra; hold fireworks for boosts)" : "OFF (walking)") + ".");
                                                return 1;
                                             }))
                                          ))
                                       .then(((LiteralArgumentBuilder)ClientCommands.literal("altitude").then(ClientCommands.literal("auto").executes(c -> {
                                          altitude = null;
                                          say(c, "Cruise altitude: auto (your Y at start + 40).");
                                          return 1;
                                       }))).then(ClientCommands.argument("y", IntegerArgumentType.integer()).executes(c -> {
                                          altitude = IntegerArgumentType.getInteger(c, "y");
                                          say(c, "Cruise altitude set to Y=" + altitude + ".");
                                          return 1;
                                       }))))
                                    .then(
                                       ClientCommands.literal("durability")
                                          .then(ClientCommands.argument("n", IntegerArgumentType.integer(0, 431)).executes(c -> {
                                             minDurability = IntegerArgumentType.getInteger(c, "n");
                                             say(c, "Will land when elytra durability <= " + minDurability + ".");
                                             return 1;
                                          }))
                                    ))
                                 .then(
                                    ClientCommands.literal("minrockets")
                                       .then(ClientCommands.argument("n", IntegerArgumentType.integer(0, 4096)).executes(c -> {
                                          minRockets = IntegerArgumentType.getInteger(c, "n");
                                          say(c, "Will land when rockets in inventory <= " + minRockets + ".");
                                          return 1;
                                       }))
                                 ))
                              .then(ClientCommands.literal("start").executes(c -> start(c, false))))
                           .then(ClientCommands.literal("resume").executes(c -> start(c, true))))
                        .then(ClientCommands.literal("stop").executes(c -> {
                           stop(Minecraft.getInstance());
                           say(c, "Autopilot stopped.");
                           return 1;
                        })))
                     .then(
                        ClientCommands.literal("status")
                           .executes(
                              c -> {
                                 say(
                                    c,
                                    "A="
                                       + fmt(ax, az, aChunk)
                                       + "  B="
                                       + fmt(bx, bz, bChunk)
                                       + "  spacing="
                                       + spacing
                                       + "  turnspeed="
                                       + (int)turnSpeed
                                       + "  mode="
                                       + (elytra ? "elytra" : "walk")
                                       + (
                                          elytra
                                             ? "  altitude="
                                                + (altitude == null ? "auto" : String.valueOf(altitude))
                                                + "  land<="
                                                + minDurability
                                                + "dur/"
                                                + minRockets
                                                + "rockets  rockets="
                                                + totalRockets
                                                + "  elytra="
                                                + (elytraLeft < 0 ? "?" : String.valueOf(elytraLeft))
                                             : ""
                                       )
                                       + (running ? "  running " + idx + "/" + path.size() : "  idle")
                                 );
                                 return 1;
                              }
                           )
                     )
               );
               AutopilotPlus.register(dispatcher);
            }
         );
      ClientTickEvents.END_CLIENT_TICK.register(AreaAutopilot::safeTick);
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> corner(String name, boolean isA, boolean chunk) {
      return (LiteralArgumentBuilder<FabricClientCommandSource>)((LiteralArgumentBuilder)ClientCommands.literal(name)
            .executes(c -> setCorner(c, isA, chunk, null, null)))
         .then(
            ClientCommands.argument("x", IntegerArgumentType.integer())
               .then(
                  ClientCommands.argument("z", IntegerArgumentType.integer())
                     .executes(c -> setCorner(c, isA, chunk, IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")))
               )
         );
   }

   private static int setCorner(CommandContext<FabricClientCommandSource> c, boolean isA, boolean chunk, Integer x, Integer z) {
      if (x == null || z == null) {
         LocalPlayer p = Minecraft.getInstance().player;
         if (p == null) {
            return 0;
         }

         Vec3 pos = p.position();
         int bxv = (int)Math.floor(pos.x());
         int bzv = (int)Math.floor(pos.z());
         x = chunk ? Math.floorDiv(bxv, 16) : bxv;
         z = chunk ? Math.floorDiv(bzv, 16) : bzv;
      }

      if (isA) {
         ax = x;
         az = z;
         aChunk = chunk;
      } else {
         bx = x;
         bz = z;
         bChunk = chunk;
      }

      say(c, (isA ? "Point A" : "Point B") + " set to " + (chunk ? "chunk " : "block ") + x + " " + z);
      return 1;
   }

   private static int start(CommandContext<FabricClientCommandSource> c, boolean resume) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (ax != null && bx != null && p != null) {
         if (!resume || !path.isEmpty() && idx < path.size()) {
            if (elytra) {
               if (!hasElytra(p)) {
                  say(c, "Elytra mode is on, but you aren't wearing an elytra. Equip one or run /autopilot elytra false");
                  return 0;
               }

               refreshSupplies(p);
               AutopilotPlus.latch(totalRockets);
               if (elytraLeft >= 0 && elytraLeft <= minDurability) {
                  say(c, "Elytra durability is too low (" + elytraLeft + " left, limit " + minDurability + "). Repair it or lower /autopilot durability.");
                  return 0;
               }

               if (rocketsReliable && AutopilotPlus.fw() && totalRockets <= minRockets) {
                  say(c, "Not enough fireworks (" + totalRockets + " found, need more than " + minRockets + ").");
                  return 0;
               }
            }

            if (!resume) {
               buildPath();
               idx = 0;
            }

            stuckTicks = 0;
            strafeTicks = 0;
            takeoffTick = 0;
            rocketCooldown = 0;
            boostTicks = 0;
            landing = false;
            landTicks = 0;
            supplyTimer = 0;
            swapCooldown = 0;
            swapWarned = false;
            running = true;
            Vec3 pos = p.position();
            prevX = pos.x();
            prevZ = pos.z();
            lastX = pos.x();
            lastY = pos.y();
            lastZ = pos.z();
            targetY = altitude != null ? altitude.intValue() : pos.y() + 40.0;
            say(
               c,
               resume
                  ? "Autopilot resumed at waypoint " + (idx + 1) + "/" + path.size() + "."
                  : "Autopilot started: " + path.size() + " waypoints. /autopilot stop to cancel."
            );
            return 1;
         } else {
            say(c, "Nothing to resume - use /autopilot start.");
            return 0;
         }
      } else {
         say(c, "Set both points first: /autopilot a <x> <z> and /autopilot b <x> <z>");
         return 0;
      }
   }

   private static void buildPath() {
      int aMinX = aChunk ? ax * 16 : ax;
      int aMaxX = aChunk ? ax * 16 + 15 : ax;
      int bMinX = bChunk ? bx * 16 : bx;
      int bMaxX = bChunk ? bx * 16 + 15 : bx;
      int aMinZ = aChunk ? az * 16 : az;
      int aMaxZ = aChunk ? az * 16 + 15 : az;
      int bMinZ = bChunk ? bz * 16 : bz;
      int bMaxZ = bChunk ? bz * 16 + 15 : bz;
      int minX = Math.min(aMinX, bMinX);
      int maxX = Math.max(aMaxX, bMaxX);
      int minZ = Math.min(aMinZ, bMinZ);
      int maxZ = Math.max(aMaxZ, bMaxZ);
      List<Integer> lanes = new ArrayList<>();

      for (int x = minX; x < maxX; x += spacing) {
         lanes.add(x);
      }

      lanes.add(maxX);
      path = new ArrayList<>();
      boolean down = true;

      for (int x : lanes) {
         path.add(new int[]{x, down ? minZ : maxZ});
         path.add(new int[]{x, down ? maxZ : minZ});
         down = !down;
      }
   }

   private static void safeTick(Minecraft mc) {
      AutopilotPlus.onTick(mc);
      if (running) {
         try {
            tick(mc);
         } catch (Throwable t) {
            running = false;

            try {
               releaseKeys(mc);
            } catch (Throwable var3) {
            }

            if (mc != null && mc.player != null) {
               mc.player.sendOverlayMessage(Component.literal("Autopilot error: " + t));
            }
         }
      }
   }

   private static void tick(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p == null) {
         stop(mc);
      } else if (idx >= path.size()) {
         stop(mc);
         p.sendOverlayMessage(Component.literal("Autopilot: area covered!"));
      } else if (elytra && !hasElytra(p)) {
         stop(mc);
         p.sendOverlayMessage(Component.literal("Autopilot: elytra no longer equipped - stopped."));
      } else {
         Vec3 pos = p.position();
         if (landing) {
            double lmy = pos.y() - lastY;
            double lspeed = Math.sqrt(Math.pow(pos.x() - lastX, 2.0) + lmy * lmy + Math.pow(pos.z() - lastZ, 2.0));
            lastX = pos.x();
            lastY = pos.y();
            lastZ = pos.z();
            tickElytra(mc, p, pos, lspeed, lmy);
         } else {
            int[] wp = path.get(idx);
            double wx = wp[0] + 0.5;
            double wz = wp[1] + 0.5;
            double dx = wx - pos.x();
            double dz = wz - pos.z();
            double dist = Math.hypot(dx, dz);
            double pdx = wx - prevX;
            double pdz = wz - prevZ;
            boolean passed = -dx * pdx - dz * pdz >= 0.0;
            double reach = elytra ? 12.0 : 1.5;
            double passReach = elytra ? 30.0 : 4.0;
            if (!(dist < reach) && (!(dist < passReach) || !passed)) {
               float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
               float yaw = p.getYRot();
               float delta = ((targetYaw - yaw + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
               float step = Math.max(-turnSpeed, Math.min(turnSpeed, delta));
               p.setYRot(yaw + step);
               double mx = pos.x() - lastX;
               double my = pos.y() - lastY;
               double mz = pos.z() - lastZ;
               double hMoved = Math.hypot(mx, mz);
               double speed = Math.sqrt(mx * mx + my * my + mz * mz);
               lastX = pos.x();
               lastY = pos.y();
               lastZ = pos.z();
               if (elytra) {
                  tickElytra(mc, p, pos, speed, my);
               } else {
                  AutopilotFly.tickWalk(mc, p, delta, hMoved);
               }

               if (++msgTimer % 100 == 0) {
                  p.sendOverlayMessage(
                     Component.literal(
                        "Autopilot: waypoint "
                           + (idx + 1)
                           + "/"
                           + path.size()
                           + (elytra ? " | rockets " + totalRockets + " | elytra " + (elytraLeft < 0 ? "?" : String.valueOf(elytraLeft)) : "")
                     )
                  );
               }
            } else {
               prevX = wx;
               prevZ = wz;
               idx++;
            }
         }
      }
   }

   private static void tickWalk(Minecraft mc, LocalPlayer p, float delta, double moved) {
      p.setXRot(10.0F);
      boolean forward = Math.abs(delta) < 50.0F;
      if (forward) {
         if (moved < 0.03) {
            stuckTicks++;
         } else {
            stuckTicks = 0;
         }
      } else {
         stuckTicks = 0;
      }

      if (stuckTicks > 60) {
         strafeTicks = 15;
         stuckTicks = 0;
      }

      mc.options.keyUp.setDown(forward);
      mc.options.keySprint.setDown(forward);
      mc.options.keyJump.setDown(stuckTicks > 3 || p.isInWater());
      mc.options.keyLeft.setDown(strafeTicks > 0);
      mc.options.keyUse.setDown(false);
      if (strafeTicks > 0) {
         strafeTicks--;
      }
   }

   private static void tickElytra(Minecraft mc, LocalPlayer p, Vec3 pos, double speed, double vy) {
      AutopilotExt.clampTarget();
      mc.options.keyUp.setDown(false);
      mc.options.keySprint.setDown(false);
      mc.options.keyLeft.setDown(false);
      if (supplyTimer-- <= 0) {
         refreshSupplies(p);
         supplyTimer = 10;
      }

      if (swapCooldown > 0) {
         swapCooldown--;
      }

      if (rocketCooldown > 0) {
         rocketCooldown--;
      }

      if (boostTicks > 0) {
         boostTicks--;
      }

      boolean flying = p.isFallFlying();
      AutopilotExt.tick(mc, p);
      if (!landing) {
         String why = null;
         if (elytraLeft >= 0 && elytraLeft <= minDurability) {
            why = "elytra durability low (" + elytraLeft + " left)";
         } else if (rocketsReliable && AutopilotPlus.fw() && totalRockets <= minRockets) {
            why = "out of fireworks";
         }

         if (why != null) {
            if (!flying) {
               stop(mc);
               p.sendOverlayMessage(Component.literal("Autopilot stopped: " + why + ". /autopilot resume to continue."));
               AutopilotExt.groundStop(mc, p);
               return;
            }

            landing = true;
            landTicks = 0;
            landReason = why;
            mc.options.keyUse.setDown(false);
            p.sendOverlayMessage(Component.literal("Autopilot: " + why + " - landing."));
         }
      }

      if (landing) {
         landStep(mc, p, pos, speed, vy, flying);
      } else if (!flying) {
         mc.options.keyUse.setDown(false);
         if (p.onGround()) {
            mc.options.keyJump.setDown(true);
            takeoffTick = 0;
         } else {
            mc.options.keyJump.setDown((takeoffTick++ & 1) == 1);
         }

         p.setXRot(0.0F);
      } else {
         mc.options.keyJump.setDown(false);
         Double h = groundClearance(mc, pos);
         double floor = targetY - 20.0;
         boolean needRocket = pos.y() < floor || h != null && h < 25.0 && speed < 0.6;
         if (handRockets <= 0 && totalRockets > 0 && !swapBroken && swapCooldown == 0) {
            swapRocketIntoHand(mc, p);
         }

         boolean fire = false;
         if (needRocket && rocketCooldown == 0 && handRockets > 0) {
            fire = true;
            rocketCooldown = 30;
            boostTicks = 25;
            handRockets--;
            supplyTimer = 0;
         }

         AutopilotExt.use(mc, p, fire);
         double err = targetY - pos.y();
         float want;
         if (boostTicks > 0) {
            want = -35.0F;
         } else if (h != null && h < 15.0) {
            want = -30.0F;
         } else if (speed < 0.6) {
            want = 28.0F;
         } else if (speed < 0.9) {
            want = 8.0F;
         } else {
            want = (float)Math.max(-40.0, Math.min(25.0, -err * 1.2));
         }

         float cur = p.getXRot();
         p.setXRot(cur + Math.max(-3.0F, Math.min(3.0F, want - cur)));
      }
   }

   private static void landStep(Minecraft mc, LocalPlayer p, Vec3 pos, double speed, double vy, boolean flying) {
      if (!AutopilotExt.landStep(mc, p, pos.x(), pos.y(), pos.z(), speed, vy, flying)) {
         landTicks++;
         mc.options.keyUse.setDown(false);
         mc.options.keyJump.setDown(false);
         if (flying) {
            if (landTicks > 2400) {
               stop(mc);
               p.sendOverlayMessage(Component.literal("Autopilot: landing timed out - stopped."));
            } else {
               Double h = groundClearance(mc, pos);
               double wantVy = h == null ? -0.3 : -Math.max(0.1, Math.min(0.7, h * 0.05));
               float want = (float)Math.max(-30.0, Math.min(30.0, (vy - wantVy) * 50.0));
               float cur = p.getXRot();
               p.setXRot(cur + Math.max(-3.0F, Math.min(3.0F, want - cur)));
               if (landTicks % 100 == 0) {
                  p.sendOverlayMessage(Component.literal("Autopilot: landing" + (h == null ? "" : " (" + (int)Math.floor(h) + " blocks up)")));
               }
            }
         } else {
            if (p.onGround() || p.isInWater() || landTicks > 2400) {
               stop(mc);
               p.sendOverlayMessage(
                  Component.literal("Autopilot: landed (" + landReason + ") at waypoint " + (idx + 1) + "/" + path.size() + ". /autopilot resume to continue.")
               );
            }
         }
      }
   }

   private static void refreshSupplies(LocalPlayer p) {
      int total = 0;
      int hand = 0;
      boolean reliable = true;

      try {
         Object stack = p.getMainHandItem();
         Boolean r = stackIs(stack, Items.FIREWORK_ROCKET);
         if (r == null) {
            reliable = false;
         } else if (r) {
            hand = countOf(stack);
         }
      } catch (Throwable t) {
         reliable = false;
      }

      try {
         Object inv = call(p, "getInventory");

         for (int i = 0; i < 36; i++) {
            Object stack = call(inv, "getItem", i);
            Boolean r = stackIs(stack, Items.FIREWORK_ROCKET);
            if (r == null) {
               reliable = false;
            } else if (r) {
               total += countOf(stack);
            }
         }
      } catch (Throwable t) {
         total = hand;
         swapBroken = true;
      }

      totalRockets = Math.max(total, hand);
      handRockets = hand;
      rocketsReliable = reliable;

      try {
         Object st = p.getItemBySlot(EquipmentSlot.CHEST);
         int max = ((Number)call(st, "getMaxDamage")).intValue();
         int dmg = ((Number)call(st, "getDamageValue")).intValue();
         elytraLeft = max - dmg;
      } catch (Throwable t) {
         elytraLeft = -1;
      }
   }

   private static int countOf(Object stack) {
      try {
         return ((Number)call(stack, "getCount")).intValue();
      } catch (Throwable t) {
         return 1;
      }
   }

   private static void swapRocketIntoHand(Minecraft mc, LocalPlayer p) {
      if (!AutopilotExt.swapRocket(mc, p)) {
         swapCooldown = 20;

         try {
            Object inv = call(p, "getInventory");

            int sel;
            try {
               sel = ((Number)call(inv, "getSelectedSlot")).intValue();
            } catch (Throwable t) {
               sel = ((Number)fieldGet(inv, "selected")).intValue();
            }

            int src = -1;

            for (int i = 0; i < 36; i++) {
               if (i != sel) {
                  Object stack = call(inv, "getItem", i);
                  if (Boolean.TRUE.equals(stackIs(stack, Items.FIREWORK_ROCKET)) && countOf(stack) > 0) {
                     src = i;
                     break;
                  }
               }
            }

            if (src < 0) {
               return;
            }

            Object menu = fieldGet(p, "inventoryMenu");
            int containerId = ((Number)fieldGet(menu, "containerId")).intValue();
            int slotId = src < 9 ? 36 + src : src;
            Object gameMode = fieldGet(mc, "gameMode");
            Class<?> clickType = Class.forName("net.minecraft.world.inventory.ClickType");
            Object swap = Enum.valueOf(clickType, "SWAP");
            call(gameMode, "handleInventoryMouseClick", containerId, slotId, sel, swap, p);
            supplyTimer = 0;
         } catch (Throwable t) {
            swapBroken = true;
            if (!swapWarned) {
               swapWarned = true;
               p.sendOverlayMessage(Component.literal("Autopilot: can't auto-switch fireworks here - keep them in your main hand."));
            }
         }
      }
   }

   private static Double groundClearance(Minecraft mc, Vec3 pos) {
      try {
         Object level = fieldGet(mc, "level");
         if (level == null) {
            return null;
         }

         if (motionBlocking == null) {
            heightmapTypes = Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types");
            motionBlocking = Enum.valueOf(heightmapTypes, "MOTION_BLOCKING");
         }

         Object top = call(level, "getHeight", motionBlocking, (int)Math.floor(pos.x()), (int)Math.floor(pos.z()));
         return pos.y() - ((Number)top).doubleValue();
      } catch (Throwable t) {
         return null;
      }
   }

   private static Class<?> boxed(Class<?> c) {
      if (!c.isPrimitive()) {
         return c;
      } else if (c == int.class) {
         return Integer.class;
      } else if (c == boolean.class) {
         return Boolean.class;
      } else if (c == double.class) {
         return Double.class;
      } else if (c == float.class) {
         return Float.class;
      } else if (c == long.class) {
         return Long.class;
      } else if (c == short.class) {
         return Short.class;
      } else {
         return c == byte.class ? Byte.class : Character.class;
      }
   }

   private static Object call(Object target, String name, Object... args) throws Exception {
      StringBuilder key = new StringBuilder(target.getClass().getName()).append('#').append(name);

      for (Object a : args) {
         key.append('/').append(a == null ? "null" : a.getClass().getName());
      }

      Method m = METHOD_CACHE.get(key.toString());
      if (m == null) {
         label49:
         for (Method cand : target.getClass().getMethods()) {
            if (cand.getName().equals(name) && cand.getParameterCount() == args.length) {
               Class<?>[] pt = cand.getParameterTypes();

               for (int i = 0; i < pt.length; i++) {
                  if (args[i] == null || !boxed(pt[i]).isInstance(args[i])) {
                     continue label49;
                  }
               }

               m = cand;
               break;
            }
         }

         if (m == null) {
            throw new NoSuchMethodException(name);
         }

         m.setAccessible(true);
         METHOD_CACHE.put(key.toString(), m);
      }

      return m.invoke(target, args);
   }

   private static Object fieldGet(Object target, String name) throws Exception {
      for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
         try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(name);
   }

   private static boolean hasElytra(LocalPlayer p) {
      try {
         Boolean r = stackIs(p.getItemBySlot(EquipmentSlot.CHEST), Items.ELYTRA);
         return r == null || r;
      } catch (Throwable t) {
         return true;
      }
   }

   private static Boolean stackIs(Object stack, Object item) {
      if (stack != null && item != null) {
         try {
            for (Method m : stack.getClass().getMethods()) {
               if (m.getName().equals("is") && m.getParameterCount() == 1 && m.getReturnType() == boolean.class && m.getParameterTypes()[0].isInstance(item)) {
                  return (Boolean)m.invoke(stack, item);
               }
            }

            for (Method m : stack.getClass().getMethods()) {
               if (m.getName().equals("getItem") && m.getParameterCount() == 0) {
                  return m.invoke(stack) == item;
               }
            }
         } catch (Throwable var6) {
         }

         return null;
      } else {
         return null;
      }
   }

   private static void stop(Minecraft mc) {
      running = false;
      releaseKeys(mc);
   }

   private static void releaseKeys(Minecraft mc) {
      if (mc != null && mc.options != null) {
         mc.options.keyUp.setDown(false);
         mc.options.keySprint.setDown(false);
         mc.options.keyJump.setDown(false);
         mc.options.keyLeft.setDown(false);
         mc.options.keyUse.setDown(false);
      }
   }

   private static void say(CommandContext<FabricClientCommandSource> c, String msg) {
      ((FabricClientCommandSource)c.getSource()).sendFeedback(Component.literal(msg));
   }

   private static String fmt(Integer x, Integer z, boolean chunk) {
      return x == null ? "unset" : (chunk ? "chunk (" : "block (") + x + ", " + z + ")";
   }
}
