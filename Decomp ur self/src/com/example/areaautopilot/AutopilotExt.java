package com.example.areaautopilot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class AutopilotExt {
   static volatile boolean safeLand = true;
   static volatile boolean landBand = false;
   static volatile int landMinY = 60;
   static volatile int landMaxY = 320;
   static volatile boolean flyBand = false;
   static volatile int flyMinY = 360;
   static volatile int flyMaxY = 500;
   static volatile boolean disconnect = true;
   static volatile boolean elytraSwap = true;
   static volatile boolean repair = false;
   private static int[] target;
   private static int planCooldown;
   private static boolean targetFallback;
   private static int noTargetTicks;
   private static int boostsUsed;
   private static int stageShown;
   private static final HashMap<Long, int[]> COL = new HashMap<>();
   private static volatile int disconnectToken;
   private static final int F_HAZARD = 1;
   private static final int F_WATER = 2;
   private static final int F_VOID = 4;
   private static final Class<?> AP;
   private static final Map<String, Method> METHODS;
   private static final Map<String, Field> FIELDS;
   private static Class<?> heightmapTypes;
   private static Object motionBlocking;
   private static Class<?> blockPosClass;
   private static int spotRad;
   private static String clickApi;
   static volatile String lastClickError;
   private static int swapElytraCd;
   private static int repairCd;
   private static int tickN;
   private static boolean repairWarned;
   private static boolean swapElytraWarned;

   private AutopilotExt() {
   }

   private static File cfgFile() {
      return new File("config", "areaautopilot-extras.properties");
   }

   private static void loadConfig() {
      try {
         File var0 = cfgFile();
         if (!var0.isFile()) {
            return;
         }

         Properties var1 = new Properties();

         try (FileInputStream var2 = new FileInputStream(var0)) {
            var1.load(var2);
         }

         safeLand = Boolean.parseBoolean(var1.getProperty("safeland", String.valueOf(safeLand)));
         landBand = Boolean.parseBoolean(var1.getProperty("landband", String.valueOf(landBand)));
         landMinY = Integer.parseInt(var1.getProperty("landminy", String.valueOf(landMinY)));
         landMaxY = Integer.parseInt(var1.getProperty("landmaxy", String.valueOf(landMaxY)));
         flyBand = Boolean.parseBoolean(var1.getProperty("flyband", String.valueOf(flyBand)));
         flyMinY = Integer.parseInt(var1.getProperty("flyminy", String.valueOf(flyMinY)));
         flyMaxY = Integer.parseInt(var1.getProperty("flymaxy", String.valueOf(flyMaxY)));
         disconnect = Boolean.parseBoolean(var1.getProperty("disconnect", String.valueOf(disconnect)));
         elytraSwap = Boolean.parseBoolean(var1.getProperty("elytraswap", String.valueOf(elytraSwap)));
         repair = Boolean.parseBoolean(var1.getProperty("repair", String.valueOf(repair)));
      } catch (Throwable var7) {
      }
   }

   private static void saveConfig() {
      try {
         File var0 = cfgFile();
         var0.getParentFile().mkdirs();
         Properties var1 = new Properties();
         var1.setProperty("safeland", String.valueOf(safeLand));
         var1.setProperty("landband", String.valueOf(landBand));
         var1.setProperty("landminy", String.valueOf(landMinY));
         var1.setProperty("landmaxy", String.valueOf(landMaxY));
         var1.setProperty("flyband", String.valueOf(flyBand));
         var1.setProperty("flyminy", String.valueOf(flyMinY));
         var1.setProperty("flymaxy", String.valueOf(flyMaxY));
         var1.setProperty("disconnect", String.valueOf(disconnect));
         var1.setProperty("elytraswap", String.valueOf(elytraSwap));
         var1.setProperty("repair", String.valueOf(repair));

         try (FileOutputStream var2 = new FileOutputStream(var0)) {
            var1.store(var2, "Area Autopilot extras");
         }
      } catch (Throwable var7) {
      }
   }

   private static boolean accepts(Class<?> var0, Object var1) {
      if (var1 == null) {
         return !var0.isPrimitive();
      }

      if (var0.isPrimitive()) {
         if (var0 == int.class) {
            return var1 instanceof Integer;
         }

         if (var0 == double.class) {
            return var1 instanceof Double || var1 instanceof Float || var1 instanceof Integer;
         }

         if (var0 == float.class) {
            return var1 instanceof Float;
         }

         if (var0 == boolean.class) {
            return var1 instanceof Boolean;
         }

         if (var0 != long.class) {
            if (var0 == short.class) {
               return var1 instanceof Short;
            } else if (var0 == byte.class) {
               return var1 instanceof Byte;
            } else {
               return var0 == char.class ? var1 instanceof Character : false;
            }
         } else {
            return var1 instanceof Long || var1 instanceof Integer;
         }
      } else {
         return var0.isInstance(var1);
      }
   }

   private static Method find(Class<?> var0, String var1, Object[] var2, boolean var3) throws NoSuchMethodException {
      for (Class var4 = var0; var4 != null; var4 = var4.getSuperclass()) {
         for (Method var8 : var4.getDeclaredMethods()) {
            if (var8.getName().equals(var1)
               && var8.getParameterCount() == var2.length
               && !var8.isBridge()
               && (Modifier.isStatic(var8.getModifiers()) == var3 || !var3)) {
               Class[] var9 = var8.getParameterTypes();
               boolean var10 = true;

               for (int var11 = 0; var11 < var9.length && var10; var11++) {
                  var10 = accepts(var9[var11], var2[var11]);
               }

               if (var10) {
                  try {
                     var8.setAccessible(true);
                  } catch (Throwable var12) {
                  }

                  return var8;
               }
            }
         }
      }

      for (Method var17 : var0.getMethods()) {
         if (var17.getName().equals(var1) && var17.getParameterCount() == var2.length) {
            Class[] var18 = var17.getParameterTypes();
            boolean var19 = true;

            for (int var20 = 0; var20 < var18.length && var19; var20++) {
               var19 = accepts(var18[var20], var2[var20]);
            }

            if (var19) {
               try {
                  var17.setAccessible(true);
               } catch (Throwable var13) {
               }

               return var17;
            }
         }
      }

      throw new NoSuchMethodException(var0.getName() + "#" + var1 + "/" + var2.length);
   }

   private static Object call(Object var0, String var1, Object... var2) throws Exception {
      Class var3 = var0.getClass();
      String var4 = var3.getName() + "#" + var1 + "#" + var2.length;
      Method var5 = METHODS.get(var4);
      if (var5 == null) {
         var5 = find(var3, var1, var2, false);
         METHODS.put(var4, var5);
      }

      try {
         return var5.invoke(var0, var2);
      } catch (InvocationTargetException var8) {
         Throwable var7 = var8.getCause();
         if (var7 instanceof Exception) {
            throw (Exception)var7;
         } else {
            throw var8;
         }
      }
   }

   private static Object callStatic(Class<?> var0, String var1, Object... var2) throws Exception {
      String var3 = "S:" + var0.getName() + "#" + var1 + "#" + var2.length;
      Method var4 = METHODS.get(var3);
      if (var4 == null) {
         var4 = find(var0, var1, var2, true);
         METHODS.put(var3, var4);
      }

      try {
         return var4.invoke(null, var2);
      } catch (InvocationTargetException var7) {
         Throwable var6 = var7.getCause();
         if (var6 instanceof Exception) {
            throw (Exception)var6;
         } else {
            throw var7;
         }
      }
   }

   private static Field fieldOf(Class<?> var0, String var1) throws NoSuchFieldException {
      String var2 = var0.getName() + "." + var1;
      Field var3 = FIELDS.get(var2);
      if (var3 != null) {
         return var3;
      }

      for (Class var4 = var0; var4 != null; var4 = var4.getSuperclass()) {
         try {
            var3 = var4.getDeclaredField(var1);
            var3.setAccessible(true);
            FIELDS.put(var2, var3);
            return var3;
         } catch (NoSuchFieldException var6) {
         }
      }

      throw new NoSuchFieldException(var2);
   }

   private static Object field(Object var0, String var1) throws Exception {
      return fieldOf(var0.getClass(), var1).get(var0);
   }

   private static int apInt(String var0) throws Exception {
      return fieldOf(AP, var0).getInt(null);
   }

   private static void apSetInt(String var0, int var1) throws Exception {
      fieldOf(AP, var0).setInt(null, var1);
   }

   private static double apDouble(String var0) throws Exception {
      return fieldOf(AP, var0).getDouble(null);
   }

   private static void apSetDouble(String var0, double var1) throws Exception {
      fieldOf(AP, var0).setDouble(null, var1);
   }

   private static boolean apBool(String var0) throws Exception {
      return fieldOf(AP, var0).getBoolean(null);
   }

   private static float apFloat(String var0) throws Exception {
      return fieldOf(AP, var0).getFloat(null);
   }

   private static Object apObj(String var0) throws Exception {
      return fieldOf(AP, var0).get(null);
   }

   private static Object component(String var0) throws Exception {
      Class var1 = Class.forName("net.minecraft.network.chat.Component");
      return var1.getMethod("literal", String.class).invoke(null, var0);
   }

   private static void overlay(Object var0, String var1) {
      try {
         call(var0, "sendOverlayMessage", component(var1));
      } catch (Throwable var3) {
      }
   }

   private static void say(CommandContext var0, String var1) {
      try {
         callStatic(AP, "say", var0, var1);
      } catch (Throwable var3) {
      }
   }

   private static int minY(Object var0) {
      try {
         return ((Number)call(var0, "getMinY")).intValue();
      } catch (Throwable var3) {
         try {
            return ((Number)call(var0, "getMinBuildHeight")).intValue();
         } catch (Throwable var2) {
            return -64;
         }
      }
   }

   private static int heightAt(Object var0, int var1, int var2) throws Exception {
      if (motionBlocking == null) {
         heightmapTypes = Class.forName("net.minecraft.world.level.levelgen.Heightmap$Types");
         motionBlocking = Enum.valueOf(heightmapTypes, "MOTION_BLOCKING");
      }

      return ((Number)call(var0, "getHeight", motionBlocking, var1, var2)).intValue();
   }

   private static String idAt(Object var0, int var1, int var2, int var3) throws Exception {
      if (blockPosClass == null) {
         blockPosClass = Class.forName("net.minecraft.core.BlockPos");
      }

      Object var4 = blockPosClass.getConstructor(int.class, int.class, int.class).newInstance(var1, var2, var3);
      Object var5 = call(var0, "getBlockState", var4);
      return idOf(String.valueOf(var5));
   }

   static String idOf(String var0) {
      String var1 = var0.toLowerCase();
      int var2 = var1.indexOf(123);
      int var3 = var1.indexOf(125);
      if (var2 >= 0 && var3 > var2) {
         var1 = var1.substring(var2 + 1, var3);
      }

      int var4 = var1.indexOf(58);
      if (var4 >= 0) {
         var1 = var1.substring(var4 + 1);
      }

      return var1;
   }

   static boolean isAirId(String var0) {
      return var0.equals("air") || var0.equals("cave_air") || var0.equals("void_air");
   }

   static boolean isHazardId(String var0) {
      return !var0.contains("lava")
            && !var0.contains("magma")
            && !var0.contains("cactus")
            && !var0.contains("berry_bush")
            && !var0.contains("wither_rose")
            && !var0.contains("powder_snow")
            && !var0.contains("cobweb")
            && !var0.contains("dripstone")
            && !var0.contains("portal")
            && !var0.contains("campfire")
            && !var0.contains("honey_block")
            && !var0.contains("slime_block")
            && !var0.contains("end_gateway")
         ? var0.equals("fire") || var0.equals("soul_fire")
         : true;
   }

   static boolean isWaterId(String var0) {
      return var0.contains("water")
         || var0.contains("bubble_column")
         || var0.equals("kelp")
         || var0.equals("kelp_plant")
         || var0.equals("seagrass")
         || var0.equals("tall_seagrass");
   }

   private static int[] col(Object var0, int var1, int var2) throws Exception {
      long var3 = (long)var1 << 32 ^ var2 & 4294967295L;
      int[] var5 = COL.get(var3);
      if (var5 != null) {
         return var5;
      }

      int var6 = heightAt(var0, var1, var2);
      if (var6 <= minY(var0) + 1) {
         return new int[]{var6, 4};
      }

      String var7 = idAt(var0, var1, var6 - 1, var2);
      String var8 = idAt(var0, var1, var6, var2);
      byte var9 = 0;
      if (isAirId(var7)) {
         var9 |= 4;
      }

      if (isHazardId(var7) || isHazardId(var8)) {
         var9 |= 1;
      }

      if (isWaterId(var7) || isWaterId(var8)) {
         var9 |= 2;
      }

      var5 = new int[]{var6, var9};
      if (COL.size() > 20000) {
         COL.clear();
      }

      COL.put(var3, var5);
      return var5;
   }

   private static boolean spotOk(Object var0, int var1, int var2, boolean var3, boolean var4, boolean var5, boolean var6) throws Exception {
      return spotOk(var0, var1, var2, var3, var4, var5, var6, 2);
   }

   private static boolean spotOk(Object var0, int var1, int var2, boolean var3, boolean var4, boolean var5, boolean var6, int var7) throws Exception {
      int[] var8 = col(var0, var1, var2);
      if ((var8[1] & 4) != 0) {
         return false;
      }

      if (!var6 || var8[0] >= Math.min(landMinY, landMaxY) && var8[0] <= Math.max(landMinY, landMaxY)) {
         for (int var9 = -var7; var9 <= var7; var9++) {
            for (int var10 = -var7; var10 <= var7; var10++) {
               int[] var11 = var9 == 0 && var10 == 0 ? var8 : col(var0, var1 + var9, var2 + var10);
               boolean var12 = Math.abs(var9) <= 1 && Math.abs(var10) <= 1;
               if (var4) {
                  if ((var11[1] & 1) != 0) {
                     return false;
                  }

                  if (var12 && (var11[1] & 4) != 0) {
                     return false;
                  }

                  if (var12 && !var3 && (var11[1] & 2) != 0) {
                     return false;
                  }
               } else if (var12 && (var11[1] & 4) != 0) {
                  return false;
               }

               if (var5 && var12 && Math.abs(var11[0] - var8[0]) > 2) {
                  return false;
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static boolean standingSafe(Object var0, double var1, double var3, double var5) throws Exception {
      int var7 = (int)Math.floor(var1);
      int var8 = (int)Math.floor(var5);
      int var9 = (int)Math.floor(var3);

      for (int var10 = -1; var10 <= 1; var10++) {
         for (int var11 = -1; var11 <= 1; var11++) {
            for (int var12 = -1; var12 <= 1; var12++) {
               String var13 = idAt(var0, var7 + var10, var9 + var12, var8 + var11);
               if (isHazardId(var13)) {
                  return false;
               }
            }
         }
      }

      String var14 = idAt(var0, var7, var9, var8);
      String var15 = idAt(var0, var7, var9 + 1, var8);
      String var16 = idAt(var0, var7, var9 - 1, var8);
      return !isWaterId(var14) && !isWaterId(var15) && !isWaterId(var16) ? !isAirId(var16) : false;
   }

   public static void register(CommandDispatcher var0) {
      try {
         LiteralArgumentBuilder var1 = LiteralArgumentBuilder.literal("autopilot");
         var1.then(toggleCmd("safeland", "Safe landing (avoid lava/fire/cactus/water/void)", var0x -> safeLand = var0x, () -> safeLand));
         var1.then(toggleCmd("landband", "Landing Y band", var0x -> landBand = var0x, () -> landBand));
         var1.then(toggleCmd("flyband", "Cruise Y band", var0x -> flyBand = var0x, () -> flyBand));
         var1.then(toggleCmd("disconnect", "Disconnect after a safe landing", var0x -> disconnect = var0x, () -> disconnect));
         var1.then(toggleCmd("elytraswap", "Auto-swap to a healthier elytra", var0x -> elytraSwap = var0x, () -> elytraSwap));
         LiteralArgumentBuilder var2 = LiteralArgumentBuilder.literal("debug");
         var2.executes(var0x -> {
            for (String var2x : debugReport()) {
               say(var0x, var2x);
            }

            return 1;
         });
         var1.then(var2);
         LiteralArgumentBuilder var3 = LiteralArgumentBuilder.literal("landnow");
         var3.executes(var0x -> {
            say(var0x, landNow());
            return 1;
         });
         var1.then(var3);
         var1.then(rangeCmd("landy", true));
         var1.then(rangeCmd("flyy", false));
         LiteralArgumentBuilder var4 = LiteralArgumentBuilder.literal("extras");
         var4.executes(var0x -> {
            say(var0x, summary());
            return 1;
         });
         var1.then(var4);
         var0.register(var1);
      } catch (Throwable var5) {
      }
   }

   private static LiteralArgumentBuilder toggleCmd(String var0, String var1, AutopilotExt.BoolSet var2, AutopilotExt.BoolGet var3) {
      LiteralArgumentBuilder var4 = LiteralArgumentBuilder.literal(var0);
      var4.executes(var3x -> {
         say(var3x, var1 + " is " + (var3.get() ? "ON" : "OFF") + ". Use /autopilot " + var0 + " <true|false>");
         return 1;
      });
      RequiredArgumentBuilder var5 = RequiredArgumentBuilder.argument("enabled", BoolArgumentType.bool());
      var5.executes(var3x -> {
         boolean var4x = BoolArgumentType.getBool(var3x, "enabled");
         var2.set(var4x);
         saveConfig();
         say(var3x, var1 + " " + (var4x ? "ON" : "OFF") + "." + extra(var0, var4x));
         return 1;
      });
      var4.then(var5);
      return var4;
   }

   private static LiteralArgumentBuilder rangeCmd(String var0, boolean var1) {
      LiteralArgumentBuilder var2 = LiteralArgumentBuilder.literal(var0);
      RequiredArgumentBuilder var3 = RequiredArgumentBuilder.argument("min", IntegerArgumentType.integer(-2048, 4096));
      RequiredArgumentBuilder var4 = RequiredArgumentBuilder.argument("max", IntegerArgumentType.integer(-2048, 4096));
      var4.executes(var1x -> {
         int var2x = IntegerArgumentType.getInteger(var1x, "min");
         int var3x = IntegerArgumentType.getInteger(var1x, "max");
         int var4x = Math.min(var2x, var3x);
         int var5 = Math.max(var2x, var3x);
         if (var1) {
            landMinY = var4x;
            landMaxY = var5;
            landBand = true;
            saveConfig();
            say(var1x, "Landing band ON: only land where the ground is between Y=" + var4x + " and Y=" + var5 + ".");
         } else {
            flyMinY = var4x;
            flyMaxY = var5;
            flyBand = true;
            saveConfig();
            say(var1x, "Cruise band ON: elytra cruise height kept between Y=" + var4x + " and Y=" + var5 + ".");
         }

         return 1;
      });
      var3.then(var4);
      var2.then(var3);
      return var2;
   }

   private static String extra(String var0, boolean var1) {
      if (!var1) {
         return "";
      }

      switch (var0) {
         case "landband":
            return " (Y " + landMinY + " to " + landMaxY + "; change with /autopilot landy <min> <max>)";
         case "flyband":
            return " (Y " + flyMinY + " to " + flyMaxY + "; change with /autopilot flyy <min> <max>)";
         case "disconnect":
            return " You will leave the game ~2s after landing somewhere safe.";
         case "elytraswap":
            return " (swaps when the worn elytra is at or below /autopilot durability)";
         case "repair":
            return " Keep XP bottles in your inventory and your offhand empty or holding bottles.";
         default:
            return "";
      }
   }

   private static String summary() {
      return "Extras: safeland="
         + (safeLand ? "ON" : "OFF")
         + " | landband="
         + (landBand ? "ON" : "OFF")
         + " (Y "
         + landMinY
         + ".."
         + landMaxY
         + ") | flyband="
         + (flyBand ? "ON" : "OFF")
         + " (Y "
         + flyMinY
         + ".."
         + flyMaxY
         + ") | disconnect="
         + (disconnect ? "ON" : "OFF")
         + " | elytraswap="
         + (elytraSwap ? "ON" : "OFF")
         + " | repair="
         + (repair ? "ON" : "OFF");
   }

   public static void clampTarget() {
      if (flyBand) {
         try {
            double var0 = Math.min(flyMinY, flyMaxY);
            double var2 = Math.max(flyMinY, flyMaxY);
            double var4 = var0 + Math.min(5.0, (var2 - var0) / 4.0);
            double var6 = var2 - Math.min(5.0, (var2 - var0) / 4.0);
            double var8 = apDouble("targetY");
            double var10 = Math.max(var4, Math.min(var6, var8));
            if (var10 != var8) {
               apSetDouble("targetY", var10);
            }
         } catch (Throwable var12) {
         }
      }
   }

   public static void use(Object var0, Object var1, boolean var2) {
      try {
         Object var3 = field(field(var0, "options"), "keyUse");
         boolean var4 = var2 && needsDirectUse(var0);
         call(var3, "setDown", var2 && !var4);
         if (var4) {
            try {
               Class var5 = Class.forName("net.minecraft.world.InteractionHand");
               Enum var6 = Enum.valueOf(var5, "MAIN_HAND");
               call(field(var0, "gameMode"), "useItem", var1, var6);

               try {
                  call(var1, "swing", var6);
               } catch (Throwable var9) {
               }
            } catch (Throwable var10) {
               call(var0, "startUseItem");
            }
         }
      } catch (Throwable var11) {
         try {
            call(field(field(var0, "options"), "keyUse"), "setDown", var2);
         } catch (Throwable var8) {
         }
      }
   }

   private static boolean needsDirectUse(Object var0) {
      try {
         if (field(var0, "screen") != null) {
            return true;
         }
      } catch (Throwable var3) {
      }

      try {
         if (!(Boolean)call(var0, "isWindowActive")) {
            return true;
         }
      } catch (Throwable var2) {
      }

      return false;
   }

   private static void resetSession() {
      target = null;
      targetFallback = false;
      planCooldown = 0;
      noTargetTicks = 0;
      boostsUsed = 0;
      stageShown = 0;
      COL.clear();
   }

   public static boolean landStep(Object var0, Object var1, double var2, double var4, double var6, double var8, double var10, boolean var12) {
      if (!safeLand && !landBand && !disconnect) {
         return false;
      }

      try {
         return doLand(var0, var1, var2, var4, var6, var8, var10, var12);
      } catch (Throwable var14) {
         return false;
      }
   }

   private static double clamp(double var0, double var2, double var4) {
      return Math.max(var2, Math.min(var4, var0));
   }

   private static boolean doLand(Object var0, Object var1, double var2, double var4, double var6, double var8, double var10, boolean var12) throws Exception {
      int var13 = apInt("landTicks") + 1;
      apSetInt("landTicks", var13);
      if (var13 == 1) {
         resetSession();
      }

      Object var14 = field(var0, "options");
      call(field(var14, "keyUse"), "setDown", false);
      call(field(var14, "keyJump"), "setDown", false);
      if (!var12) {
         boolean var45 = (Boolean)call(var1, "onGround");
         boolean var46 = (Boolean)call(var1, "isInWater");
         if (var45 || var46 || var13 > 2400) {
            finishLanding(var0, var1, var2, var4, var6, var45, var46);
         }

         return true;
      } else {
         if (var13 > 2400) {
            callStatic(AP, "stop", var0);
            notify(var0, var1, "Autopilot: landing timed out - stopped.");
            return true;
         }

         Object var15 = field(var0, "level");
         int var16 = apInt("elytraLeft");
         double var17 = var16 >= 0 ? Math.max(40.0, (var16 - 2) * 20.0) : 1.0E9;
         int var19 = noTargetTicks > 400 ? 2 : (noTargetTicks > 200 ? 1 : 0);
         if (var17 < 200.0) {
            var19 = 2;
         } else if (var17 < 400.0) {
            var19 = Math.max(var19, 1);
         }

         if (var19 > stageShown) {
            stageShown = var19;
            overlay(
               var1,
               var19 == 1
                  ? "Autopilot: no spot found yet - relaxing the safety margin."
                  : "Autopilot: still no spot - water landings allowed (lava/fire still avoided)."
            );
         }

         boolean var20 = safeLand || landBand;
         boolean var21 = landBand;
         boolean var22 = var19 >= 2;
         spotRad = var19 >= 1 ? 1 : 2;
         Double var23 = null;

         try {
            int var24 = heightAt(var15, (int)Math.floor(var2), (int)Math.floor(var6));
            var23 = var4 - var24;
         } catch (Throwable var44) {
         }

         double var47 = Math.sqrt(Math.max(0.0, var8 * var8 - var10 * var10));
         float var26 = ((Number)call(var1, "getYRot")).floatValue();
         if (var20 && var23 != null) {
            if (target != null && var13 % 5 == 0 && !spotOk(var15, target[0], target[1], var22, safeLand, safeLand, var21 && !targetFallback, spotRad)) {
               target = null;
               targetFallback = false;
            }

            if (target == null) {
               if (planCooldown > 0) {
                  planCooldown--;
               } else {
                  plan(var15, var2, var6, var23, var47, var26, var22, var21);
                  if (target == null && var21 && noTargetTicks > 60) {
                     plan(var15, var2, var6, var23, var47, var26, var22, false);
                     targetFallback = target != null;
                  }

                  planCooldown = 8;
               }
            } else if (targetFallback && var21 && var13 % 40 == 0) {
               int[] var27 = target;
               target = null;
               plan(var15, var2, var6, var23, var47, var26, var22, true);
               if (target == null) {
                  target = var27;
               } else {
                  targetFallback = false;
               }
            }

            if (target == null) {
               noTargetTicks++;
            } else {
               noTargetTicks = Math.max(0, noTargetTicks - 2);
            }
         }

         double var48 = var23 == null ? 0.3 : Math.max(0.1, Math.min(0.7, var23 * 0.05));
         double var29 = var48;
         if (var20 && target == null && var23 != null && var23 > 20.0 && noTargetTicks < 120) {
            var29 = 0.15;
         }

         double var32 = -1.0;
         if (var20 && target != null && var23 != null) {
            double var34 = target[0] + 0.5 - var2;
            double var36 = target[1] + 0.5 - var6;
            var32 = Math.hypot(var34, var36);
            double var38 = Math.max(apFloat("turnSpeed"), 6.0F);
            double var40 = var23 >= 40.0 ? 0.9 : (var23 >= 14.0 ? 0.7 : var48);
            float var31;
            if (var32 < 5.0 && var23 > 12.0) {
               var31 = var26 + (float)var38;
               var29 = var40;
            } else {
               float var42 = (float)Math.toDegrees(Math.atan2(-var34, var36));
               float var43 = ((var42 - var26 + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;
               var31 = var26 + (float)clamp(var43, -var38, var38);
               var29 = Math.min(var40, clamp(var23 / Math.max(var32 - 3.0, 1.0) * var47, 0.1, 0.9));
            }

            call(var1, "setYRot", var31);
         }

         if (var23 != null && var23 > 20.0) {
            var29 = Math.max(var29, Math.min(0.9, var23 / var17));
         }

         boolean var49 = false;
         if (var20
            && target == null
            && var23 != null
            && var23 < 35.0
            && var19 == 0
            && boostsUsed < 6
            && apInt("handRockets") > 0
            && apInt("rocketCooldown") == 0) {
            apSetInt("rocketCooldown", 30);
            apSetInt("boostTicks", 25);
            apSetInt("handRockets", apInt("handRockets") - 1);
            apSetInt("supplyTimer", 0);
            boostsUsed++;
            var49 = true;
            use(var0, var1, true);
         }

         float var35;
         if (apInt("boostTicks") > 0 && (var49 || boostsUsed > 0) && target == null && var20) {
            var35 = -35.0F;
         } else {
            var35 = (float)clamp((var10 - -var29) * 50.0, -30.0, 30.0);
         }

         float var50 = ((Number)call(var1, "getXRot")).floatValue();
         call(var1, "setXRot", var50 + (float)clamp(var35 - var50, -3.0, 3.0));
         if (var13 % 100 == 0) {
            String var37 = var23 == null ? "" : " (" + (int)Math.floor(var23) + " blocks up)";
            String var51 = !var20
               ? ""
               : (target != null ? " -> safe spot " + target[0] + ", " + target[1] + " (" + (int)var32 + " away)" : " | searching for a safe spot");
            overlay(var1, "Autopilot: landing" + var37 + var51);
         }

         return true;
      }
   }

   private static void plan(Object var0, double var1, double var3, double var5, double var7, float var9, boolean var10, boolean var11) throws Exception {
      double var12 = Math.min(clamp(var7 * 15.0, 6.0, 40.0), Math.max(6.0, var5 * 3.0));
      double var14 = Math.toRadians(var9);
      double var16 = var1 - Math.sin(var14) * var12;
      double var18 = var3 + Math.cos(var14) * var12;
      double var20 = var5 * 6.0 + 12.0;
      int var22 = (int)Math.floor(var16);
      int var23 = (int)Math.floor(var18);
      int[][] var24 = new int[][]{{40, 3}, {72, 5}, {140, 8}, {280, 14}};

      for (int[] var28 : var24) {
         int var29 = var28[0];
         int var30 = var28[1];
         ArrayList var31 = new ArrayList();

         for (int var32 = -var29; var32 <= var29; var32 += var30) {
            for (int var33 = -var29; var33 <= var29; var33 += var30) {
               if (var32 * var32 + var33 * var33 <= var29 * var29) {
                  int var34 = var22 + var32;
                  int var35 = var23 + var33;
                  if (!(Math.hypot(var34 + 0.5 - var1, var35 + 0.5 - var3) > var20)) {
                     var31.add(new int[]{var34, var35, var32 * var32 + var33 * var33});
                  }
               }
            }
         }

         var31.sort(Comparator.comparingInt(var0x -> var0x[2]));
         int var36 = 0;

         for (int[] var38 : var31) {
            int[] var39 = col(var0, var38[0], var38[1]);
            if ((var39[1] & 4) == 0
               && (!safeLand || (var39[1] & 1) == 0 && ((var39[1] & 2) == 0 || var10))
               && (!var11 || var39[0] >= Math.min(landMinY, landMaxY) && var39[0] <= Math.max(landMinY, landMaxY))) {
               if (var36++ > 80) {
                  break;
               }

               if (spotOk(var0, var38[0], var38[1], var10, safeLand, safeLand, var11, spotRad)) {
                  target = new int[]{var38[0], var38[1], var39[0]};
                  return;
               }
            }
         }
      }
   }

   private static void chat(Object var0, String var1) {
      try {
         Object var2 = call(field(var0, "gui"), "getChat");
         call(var2, "addMessage", component(var1));
      } catch (Throwable var3) {
      }
   }

   private static void notify(Object var0, Object var1, String var2) {
      if (var1 != null) {
         overlay(var1, var2);
      }

      chat(var0, var2);
   }

   private static void click(Object var0, Object var1, int var2, int var3, String var4) throws Exception {
      Object var5 = field(var0, "gameMode");
      int var6 = ((Number)field(field(var1, "inventoryMenu"), "containerId")).intValue();
      ClassNotFoundException var7 = null;
      if (!"ClickType".equals(clickApi)) {
         try {
            Class var11 = Class.forName("net.minecraft.world.inventory.ContainerInput");
            call(var5, "handleContainerInput", var6, var2, var3, Enum.valueOf(var11, var4), var1);
            clickApi = "ContainerInput";
            return;
         } catch (ClassNotFoundException | NoSuchMethodException var10) {
            var7 = var10;
         }
      }

      try {
         Class var8 = Class.forName("net.minecraft.world.inventory.ClickType");
         call(var5, "handleInventoryMouseClick", var6, var2, var3, Enum.valueOf(var8, var4), var1);
         clickApi = "ClickType";
      } catch (Exception var9) {
         lastClickError = var9 + (var7 != null ? " (after " + var7 + ")" : "");
         throw var9;
      }
   }

   private static int menuSlot(int var0) {
      return var0 < 9 ? 36 + var0 : var0;
   }

   private static boolean isItem(Object var0, String var1) {
      return var0 != null && String.valueOf(var0).toLowerCase().contains(var1);
   }

   private static int countOfStack(Object var0) {
      try {
         return ((Number)call(var0, "getCount")).intValue();
      } catch (Throwable var2) {
         return 1;
      }
   }

   private static int durabilityLeft(Object var0) {
      try {
         return ((Number)call(var0, "getMaxDamage")).intValue() - ((Number)call(var0, "getDamageValue")).intValue();
      } catch (Throwable var2) {
         return -1;
      }
   }

   private static int selectedSlot(Object var0) throws Exception {
      try {
         return ((Number)call(var0, "getSelectedSlot")).intValue();
      } catch (Throwable var2) {
         return ((Number)field(var0, "selected")).intValue();
      }
   }

   public static boolean swapRocket(Object var0, Object var1) {
      try {
         apSetInt("swapCooldown", 20);
         Object var9 = call(var1, "getInventory");
         int var3 = selectedSlot(var9);
         int var4 = -1;

         for (int var5 = 0; var5 < 36; var5++) {
            if (var5 != var3) {
               Object var6 = call(var9, "getItem", var5);
               if (isItem(var6, "firework_rocket") && countOfStack(var6) > 0) {
                  var4 = var5;
                  break;
               }
            }
         }

         if (var4 < 0) {
            return true;
         }

         click(var0, var1, menuSlot(var4), var3, "SWAP");
         apSetInt("supplyTimer", 0);
         fieldOf(AP, "swapBroken").setBoolean(null, false);
         return true;
      } catch (Throwable var8) {
         Throwable var2 = var8;

         try {
            fieldOf(AP, "swapBroken").setBoolean(null, true);
            if (!apBool("swapWarned")) {
               fieldOf(AP, "swapWarned").setBoolean(null, true);
               notify(var0, var1, "Autopilot: can't auto-switch fireworks (" + var2 + "). Keep them in your main hand. Run /autopilot debug.");
            }
         } catch (Throwable var7) {
         }

         return true;
      }
   }

   public static void tick(Object var0, Object var1) {
      try {
         tickN++;
         if (swapElytraCd > 0) {
            swapElytraCd--;
         }

         if (repairCd > 0) {
            repairCd--;
         }

         if (tickN % 100 == 0 && apBool("swapBroken") && clickApi != null) {
            fieldOf(AP, "swapBroken").setBoolean(null, false);
         }

         if (!apBool("landing")) {
            maybeSwapElytra(var0, var1);
            AutopilotFly.maybeRepair(var0, var1);
         }
      } catch (Throwable var3) {
      }
   }

   private static void maybeSwapElytra(Object var0, Object var1) throws Exception {
      if (elytraSwap && swapElytraCd <= 0) {
         int var2 = apInt("elytraLeft");
         int var3 = apInt("minDurability");
         if (var2 >= 0 && var2 <= var3) {
            Object var4 = call(var1, "getInventory");
            int var5 = -1;
            int var6 = Math.max(var3 + 30, var2 + 60) - 1;

            for (int var7 = 0; var7 < 36; var7++) {
               Object var8 = call(var4, "getItem", var7);
               if (isItem(var8, "elytra")) {
                  int var9 = durabilityLeft(var8);
                  if (var9 > var6) {
                     var5 = var7;
                     var6 = var9;
                  }
               }
            }

            if (var5 < 0) {
               swapElytraCd = 200;
               if (!swapElytraWarned) {
                  swapElytraWarned = true;
                  notify(var0, var1, "Autopilot: elytra is low but no healthier elytra found in your inventory.");
               }
            } else {
               int var11 = menuSlot(var5);
               click(var0, var1, var11, 0, "PICKUP");
               click(var0, var1, 6, 0, "PICKUP");
               click(var0, var1, var11, 0, "PICKUP");
               swapElytraCd = 100;
               swapElytraWarned = false;

               try {
                  callStatic(AP, "refreshSupplies", var1);
               } catch (Throwable var10) {
               }

               notify(var0, var1, "Autopilot: swapped to a healthier elytra (" + var6 + " durability left).");
            }
         }
      }
   }

   private static boolean hasMending(Object var0) {
      try {
         return String.valueOf(call(var0, "getEnchantments")).toLowerCase().contains("mending");
      } catch (Throwable var2) {
         return true;
      }
   }

   private static void maybeRepair(Object var0, Object var1) throws Exception {
      if (repair && repairCd <= 0) {
         Class var2 = Class.forName("net.minecraft.world.entity.EquipmentSlot");
         Object var3 = call(var1, "getItemBySlot", Enum.valueOf(var2, "CHEST"));
         int var4 = ((Number)call(var3, "getDamageValue")).intValue();
         if (var4 >= 40 && hasMending(var3)) {
            Object var5 = call(var1, "getOffhandItem");
            Class var6 = Class.forName("net.minecraft.world.InteractionHand");
            if (isItem(var5, "experience_bottle")) {
               Enum var11 = Enum.valueOf(var6, "OFF_HAND");
               call(field(var0, "gameMode"), "useItem", var1, var11);

               try {
                  call(var1, "swing", var11);
               } catch (Throwable var10) {
               }

               repairCd = 7;
            } else {
               Object var7 = call(var1, "getInventory");
               int var8 = -1;

               for (int var9 = 0; var9 < 36 && var8 < 0; var9++) {
                  if (isItem(call(var7, "getItem", var9), "experience_bottle")) {
                     var8 = var9;
                  }
               }

               if (var8 < 0) {
                  repairCd = 100;
               } else {
                  boolean var12 = (Boolean)call(var5, "isEmpty");
                  if (!var12) {
                     if (!repairWarned) {
                        repairWarned = true;
                        notify(var0, var1, "Autopilot: repair is on, but your offhand is busy. Empty it or hold XP bottles there.");
                     }

                     repairCd = 200;
                  } else {
                     click(var0, var1, menuSlot(var8), 40, "SWAP");
                     repairCd = 5;
                  }
               }
            }
         }
      }
   }

   private static String landNow() {
      try {
         if (!apBool("running")) {
            return "Autopilot isn't running - /autopilot start first (landnow only works mid-flight).";
         }

         apSetInt("landTicks", 0);
         fieldOf(AP, "landing").setBoolean(null, true);
         fieldOf(AP, "landReason").set(null, "manual /autopilot landnow");
         return "Landing started now (manual test). Safe-land=" + (safeLand ? "ON" : "OFF") + ", disconnect=" + (disconnect ? "ON" : "OFF") + ".";
      } catch (Throwable var1) {
         return "landnow failed: " + var1;
      }
   }

   private static List<String> debugReport() {
      ArrayList var0 = new ArrayList();
      var0.add("=== Autopilot debug (1.8.1) ===");

      try {
         var0.add("running=" + apBool("running") + " landing=" + apBool("landing") + " elytraMode=" + apBool("elytra") + " targetY=" + (int)apDouble("targetY"));
         var0.add(
            "elytraLeft="
               + apInt("elytraLeft")
               + " (landing at <= "
               + apInt("minDurability")
               + ")  rockets total="
               + apInt("totalRockets")
               + " hand="
               + apInt("handRockets")
               + " (landing at <= "
               + apInt("minRockets")
               + ", reliable="
               + apBool("rocketsReliable")
               + ")  swapBroken="
               + apBool("swapBroken")
         );
      } catch (Throwable var21) {
         var0.add("state read failed: " + var21);
      }

      try {
         Class var1 = Class.forName("net.minecraft.client.multiplayer.MultiPlayerGameMode");
         boolean var2 = false;
         boolean var3 = false;

         for (Method var7 : var1.getDeclaredMethods()) {
            if (var7.getName().equals("handleContainerInput")) {
               var2 = true;
            }

            if (var7.getName().equals("handleInventoryMouseClick")) {
               var3 = true;
            }
         }

         var0.add(
            "inventory click API: handleContainerInput="
               + var2
               + " handleInventoryMouseClick="
               + var3
               + " | in use: "
               + (clickApi == null ? "not used yet" : clickApi)
               + (lastClickError != null ? " | last click error: " + lastClickError : "")
         );
      } catch (Throwable var23) {
         var0.add("click API probe failed: " + var23);
      }

      try {
         Object var24 = callStatic(Class.forName("net.minecraft.client.Minecraft"), "getInstance");
         Object var25 = field(var24, "player");
         Object var26 = field(var24, "level");
         if (var25 != null) {
            Object var27 = call(var25, "getInventory");
            int var28 = 0;
            int var29 = -1;
            int var30 = 0;
            int var8 = 0;

            for (int var9 = 0; var9 < 36; var9++) {
               Object var10 = call(var27, "getItem", var9);
               if (isItem(var10, "elytra")) {
                  var28++;
                  var29 = Math.max(var29, durabilityLeft(var10));
               }

               if (isItem(var10, "firework_rocket")) {
                  var30 += countOfStack(var10);
               }

               if (isItem(var10, "experience_bottle")) {
                  var8 += countOfStack(var10);
               }
            }

            Class var31 = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            Object var32 = call(var25, "getItemBySlot", Enum.valueOf(var31, "CHEST"));
            var0.add(
               "worn: "
                  + var32
                  + " ("
                  + durabilityLeft(var32)
                  + " left) | offhand: "
                  + call(var25, "getOffhandItem")
                  + " | spare elytras in inventory: "
                  + var28
                  + " (best "
                  + var29
                  + " left) | rockets in inventory: "
                  + var30
                  + " | XP bottles: "
                  + var8
            );
            Object var11 = call(var25, "position");
            double var12 = ((Number)call(var11, "x")).doubleValue();
            double var14 = ((Number)call(var11, "y")).doubleValue();
            double var16 = ((Number)call(var11, "z")).doubleValue();
            int var18 = (int)Math.floor(var12);
            int var19 = (int)Math.floor(var14);
            int var20 = (int)Math.floor(var16);
            var0.add(
               "below you: "
                  + idAt(var26, var18, var19 - 1, var20)
                  + " | at feet: "
                  + idAt(var26, var18, var19, var20)
                  + " | standingSafe="
                  + standingSafe(var26, var12, var14, var16)
                  + " | heightmap top="
                  + heightAt(var26, var18, var20)
            );
         } else {
            var0.add("(not in a world)");
         }
      } catch (Throwable var22) {
         var0.add("world probe failed: " + var22);
      }

      var0.add(summary());
      return var0;
   }

   private static void finishLanding(Object var0, Object var1, double var2, double var4, double var6, boolean var8, boolean var9) throws Exception {
      callStatic(AP, "stop", var0);
      String var10 = String.valueOf(apObj("landReason"));
      int var11 = apInt("idx");
      int var12 = ((List)apObj("path")).size();
      notify(var0, var1, "Autopilot: landed (" + var10 + ") at waypoint " + (var11 + 1) + "/" + var12 + ". /autopilot resume to continue.");
      afterLanded(var0, var1, var2, var4, var6, var8, var9);
   }

   public static void groundStop(Object var0, Object var1) {
      try {
         Object var2 = call(var1, "position");
         double var3 = ((Number)call(var2, "x")).doubleValue();
         double var5 = ((Number)call(var2, "y")).doubleValue();
         double var7 = ((Number)call(var2, "z")).doubleValue();
         afterLanded(var0, var1, var3, var5, var7, (Boolean)call(var1, "onGround"), (Boolean)call(var1, "isInWater"));
      } catch (Throwable var9) {
      }
   }

   private static void afterLanded(Object var0, Object var1, double var2, double var4, double var6, boolean var8, boolean var9) {
      if (!disconnect) {
         chat(var0, "Autopilot: landing finished. (Auto-disconnect is OFF - /autopilot disconnect true to enable.)");
      } else {
         try {
            String var11 = "";

            boolean var10;
            try {
               Object var12 = field(var0, "level");
               var10 = var8 && !var9 && standingSafe(var12, var2, var4, var6);
            } catch (Throwable var14) {
               var10 = var8 && !var9;
               var11 = " (couldn't scan surroundings: " + var14 + ")";
            }

            if (!var10) {
               notify(
                  var0,
                  var1,
                  "Autopilot: NOT disconnecting - landing spot isn't safe (lava/fire/water/void nearby, or not on solid ground). Stay online." + var11
               );
               return;
            }

            notify(var0, var1, "Autopilot: landed safely - disconnecting in 2s (/autopilot disconnect false to cancel)." + var11);
            int var16 = ++disconnectToken;
            Thread var13 = new Thread(() -> {
               try {
                  Thread.sleep(2000L);
               } catch (InterruptedException var4x) {
               }

               try {
                  call(var0, "execute", (Runnable)() -> AutopilotFly.finishDisconnect(var0, var16));
               } catch (Throwable var3) {
                  AutopilotFly.finishDisconnect(var0, var16);
               }
            }, "autopilot-disconnect");
            var13.setDaemon(true);
            var13.start();
         } catch (Throwable var15) {
            notify(var0, var1, "Autopilot: disconnect step failed (" + var15 + "). Leave manually.");
         }
      }
   }

   private static void finishDisconnect(Object var0, int var1) {
      Object var2 = null;

      try {
         if (var1 == disconnectToken && disconnect) {
            if (!apBool("running")) {
               var2 = field(var0, "player");
               if (var2 != null) {
                  if ((Boolean)call(var2, "onGround") && !(Boolean)call(var2, "isInWater")) {
                     Object var3 = component("Autopilot: landed safely - disconnected.");
                     Throwable var4 = null;

                     try {
                        Object var5 = call(call(var0, "getConnection"), "getConnection");
                        call(var5, "disconnect", var3);
                     } catch (Throwable var7) {
                        try {
                           call(field(var0, "level"), "disconnect", var3);
                        } catch (Throwable var6) {
                           var4 = new RuntimeException(var7 + " / " + var6);
                           throw new RuntimeException(var4);
                        }
                     }
                  }
               }
            }
         }
      } catch (Throwable var8) {
         notify(var0, var2, "Autopilot: couldn't disconnect automatically (" + var8 + "). Leave manually.");
      }
   }

   static {
      Class var0 = null;

      try {
         var0 = Class.forName("com.example.areaautopilot.AreaAutopilot");
      } catch (Throwable var2) {
      }

      AP = var0;
      loadConfig();
      METHODS = new HashMap<>();
      FIELDS = new HashMap<>();
      spotRad = 2;
   }

   private interface BoolGet {
      boolean get();
   }

   private interface BoolSet {
      void set(boolean var1);
   }
}
