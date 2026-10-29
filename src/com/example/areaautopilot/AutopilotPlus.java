package com.example.areaautopilot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class AutopilotPlus {
   private static final String AP_NAME = "com.example.areaautopilot.AreaAutopilot";
   private static final String EXT_NAME = "com.example.areaautopilot.AutopilotExt";
   private static Class<?> AP;
   private static final Map<String, Field> FIELDS = new HashMap<>();
   private static volatile int fwMode = 0;
   private static volatile boolean latched = true;
   private static boolean loaded = false;
   private static int tickN = 0;
   private static String lastSnap = null;

   private AutopilotPlus() {
   }

   public static boolean fw() {
      int var0 = fwMode;
      if (var0 == 1) {
         return true;
      } else {
         return var0 == 2 ? false : latched;
      }
   }

   public static void latch(int var0) {
      latched = var0 > 0;
   }

   public static void register(CommandDispatcher var0) {
      AutopilotTrail.register(var0);

      try {
         Class var1 = Class.forName("com.example.areaautopilot.AutopilotExt");
         Method var2 = var1.getMethod("register", CommandDispatcher.class);
         var2.invoke(null, var0);
      } catch (Throwable var5) {
      }

      try {
         loadStateOnce();
      } catch (Throwable var4) {
      }

      try {
         registerOurs(var0);
      } catch (Throwable var3) {
      }
   }

   public static void onTick(Object var0) {
      AutopilotTrail.onTick(var0);

      try {
         if (++tickN % 20 != 0) {
            return;
         }

         if (!loaded) {
            return;
         }

         String var1 = snapshot();
         if (!var1.equals(lastSnap)) {
            lastSnap = var1;
            writeState();
         }
      } catch (Throwable var2) {
      }
   }

   private static void registerOurs(CommandDispatcher var0) {
      LiteralArgumentBuilder var1 = LiteralArgumentBuilder.literal("autopilot");
      LiteralArgumentBuilder var2 = LiteralArgumentBuilder.literal("startfrom");
      var2.then(LiteralArgumentBuilder.literal("current").executes(var0x -> startFrom(var0x, null, null)));
      var2.then(
         RequiredArgumentBuilder.argument("x", IntegerArgumentType.integer())
            .then(
               RequiredArgumentBuilder.argument("z", IntegerArgumentType.integer())
                  .executes(var0x -> startFrom(var0x, IntegerArgumentType.getInteger(var0x, "x"), IntegerArgumentType.getInteger(var0x, "z")))
            )
      );
      var1.then(var2);
      LiteralArgumentBuilder var3 = LiteralArgumentBuilder.literal("fireworks");
      var3.executes(var0x -> {
         say(var0x, fwStatus());
         return 1;
      });
      var3.then(LiteralArgumentBuilder.literal("auto").executes(var0x -> setFw(var0x, 0)));
      var3.then(LiteralArgumentBuilder.literal("true").executes(var0x -> setFw(var0x, 1)));
      var3.then(LiteralArgumentBuilder.literal("false").executes(var0x -> setFw(var0x, 2)));
      var1.then(var3);
      var0.register(var1);
   }

   private static String fwStatus() {
      String var0 = fwMode == 0 ? "auto" : (fwMode == 1 ? "true" : "false");
      String var1 = fw() ? "fireworks required (out of fireworks = land)" : "fireworks NOT required (elytra-fly friendly)";
      return "Autopilot: fireworks mode = " + var0 + " - " + var1 + ". Use /autopilot fireworks auto|true|false";
   }

   private static int setFw(CommandContext<?> var0, int var1) {
      fwMode = var1;
      if (var1 == 1) {
         latched = true;
      }

      if (var1 == 2) {
         latched = false;
      }

      say(var0, fwStatus());
      return 1;
   }

   private static int startFrom(CommandContext var0, Integer var1, Integer var2) {
      try {
         ap();
         Object var3 = Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null);
         Object var4 = readField(var3, "player");
         Integer var5 = (Integer)sget("ax");
         Integer var6 = (Integer)sget("bx");
         if (var5 != null && var6 != null && var4 != null) {
            double var7;
            double var9;
            if (var1 != null && var2 != null) {
               var7 = var1.intValue() + 0.5;
               var9 = var2.intValue() + 0.5;
            } else {
               Object var11 = callNoArg(var4, "position");
               var7 = ((Number)callNoArg(var11, "x")).doubleValue();
               var9 = ((Number)callNoArg(var11, "z")).doubleValue();
            }

            callStaticNoArg("buildPath");
            List var17 = (List)sget("path");
            if (var17 != null && !var17.isEmpty()) {
               int var12 = nearestIndex(var17, var7, var9);
               sset("idx", var12);
               Method var13 = AP.getDeclaredMethod("start", CommandContext.class, boolean.class);
               var13.setAccessible(true);
               Object var14 = var13.invoke(null, var0, Boolean.TRUE);
               int var15 = var14 instanceof Integer ? (Integer)var14 : 0;
               if (var15 != 0) {
                  say(
                     var0,
                     "Autopilot: joined the sweep at waypoint "
                        + (var12 + 1)
                        + "/"
                        + var17.size()
                        + " (nearest to "
                        + (var1 != null ? "that spot" : "you")
                        + "), following the normal pattern from there."
                  );
               }

               return var15;
            } else {
               say(var0, "Autopilot: could not build the path - check /autopilot status.");
               return 0;
            }
         } else {
            say(var0, "Set both points first: /autopilot a <x> <z> and /autopilot b <x> <z>");
            return 0;
         }
      } catch (Throwable var16) {
         say(var0, "Autopilot: startfrom failed: " + var16);
         return 0;
      }
   }

   static int nearestIndex(List<int[]> var0, double var1, double var3) {
      int var5 = var0.size();
      if (var5 < 2) {
         return 0;
      }

      byte var6 = 0;
      double var7 = Double.MAX_VALUE;
      double var9 = 0.0;

      for (byte var11 = 0; var11 + 1 < var5; var11 += 2) {
         int[] var12 = (int[])var0.get(var11);
         int[] var13 = (int[])var0.get(var11 + 1);
         double var14 = var12[0] + 0.5;
         double var16 = var12[1] + 0.5;
         double var18 = var13[0] + 0.5;
         double var20 = var13[1] + 0.5;
         double var22 = var18 - var14;
         double var24 = var20 - var16;
         double var26 = var22 * var22 + var24 * var24;
         double var28 = var26 < 1.0E-9 ? 0.0 : ((var1 - var14) * var22 + (var3 - var16) * var24) / var26;
         double var30 = Math.max(0.0, Math.min(1.0, var28));
         double var32 = var14 + var22 * var30;
         double var34 = var16 + var24 * var30;
         double var36 = Math.hypot(var1 - var32, var3 - var34);
         if (var36 < var7 - 1.0E-6) {
            var7 = var36;
            var6 = var11;
            var9 = var28;
         }
      }

      if (var9 <= 0.05) {
         return var6;
      } else {
         return var9 >= 0.98 ? Math.min(var6 + 2, var5 - 1) : var6 + 1;
      }
   }

   private static File stateFile() {
      return new File("config", "areaautopilot-state.properties");
   }

   private static String snapshot() throws Exception {
      StringBuilder var0 = new StringBuilder();

      for (String var4 : new String[]{
         "ax", "az", "bx", "bz", "aChunk", "bChunk", "spacing", "elytra", "altitude", "turnSpeed", "minDurability", "minRockets", "idx"
      }) {
         var0.append(var4).append('=').append(sget(var4)).append('\n');
      }

      var0.append("fireworks=").append(fwMode).append('\n');
      return var0.toString();
   }

   private static void writeState() throws Exception {
      Properties var0 = new Properties();

      for (String var4 : snapshot().split("\n")) {
         int var5 = var4.indexOf(61);
         if (var5 > 0) {
            String var6 = var4.substring(var5 + 1);
            if (!"null".equals(var6)) {
               var0.setProperty(var4.substring(0, var5), var6);
            }
         }
      }

      File var9 = stateFile();
      File var10 = var9.getParentFile();
      if (var10 != null) {
         var10.mkdirs();
      }

      try (FileOutputStream var11 = new FileOutputStream(var9)) {
         var0.store(var11, "Area Autopilot saved state (points, progress, mode)");
      }
   }

   private static void loadStateOnce() throws Exception {
      if (!loaded) {
         loaded = true;
         ap();
         File var0 = stateFile();
         if (var0.isFile() && sget("ax") == null && sget("bx") == null) {
            Properties var1 = new Properties();
            FileInputStream var2 = new FileInputStream(var0);

            try {
               var1.load(var2);
            } catch (Throwable var15) {
               try {
                  var2.close();
               } catch (Throwable var13) {
                  var15.addSuppressed(var13);
               }

               throw var15;
            }

            var2.close();
            Integer var16 = pInt(var1, "ax");
            Integer var3 = pInt(var1, "az");
            Integer var4 = pInt(var1, "bx");
            Integer var5 = pInt(var1, "bz");
            if (var16 != null && var3 != null && var4 != null && var5 != null) {
               sset("ax", var16);
               sset("az", var3);
               sset("bx", var4);
               sset("bz", var5);
               sset("aChunk", Boolean.parseBoolean(var1.getProperty("aChunk", "false")));
               sset("bChunk", Boolean.parseBoolean(var1.getProperty("bChunk", "false")));
               Integer var6 = pInt(var1, "spacing");
               if (var6 != null && var6 > 0) {
                  sset("spacing", var6);
               }

               sset("elytra", Boolean.parseBoolean(var1.getProperty("elytra", "false")));
               Integer var7 = pInt(var1, "altitude");
               sset("altitude", var7);

               try {
                  String var8 = var1.getProperty("turnSpeed");
                  if (var8 != null) {
                     sset("turnSpeed", Float.parseFloat(var8));
                  }
               } catch (Exception var14) {
               }

               Integer var17 = pInt(var1, "minDurability");
               if (var17 != null) {
                  sset("minDurability", var17);
               }

               Integer var9 = pInt(var1, "minRockets");
               if (var9 != null) {
                  sset("minRockets", var9);
               }

               Integer var10 = pInt(var1, "fireworks");
               if (var10 != null && var10 >= 0 && var10 <= 2) {
                  fwMode = var10;
               }

               callStaticNoArg("buildPath");
               List var11 = (List)sget("path");
               Integer var12 = pInt(var1, "idx");
               if (var11 != null && var12 != null && var12 >= 0 && var12 <= var11.size()) {
                  sset("idx", var12);
               }
            }
         }

         lastSnap = snapshot();
      }
   }

   private static Integer pInt(Properties var0, String var1) {
      try {
         String var2 = var0.getProperty(var1);
         return var2 == null ? null : Integer.valueOf(var2.trim());
      } catch (Exception var3) {
         return null;
      }
   }

   private static void ap() throws Exception {
      if (AP == null) {
         AP = Class.forName("com.example.areaautopilot.AreaAutopilot");
      }
   }

   private static Field apField(String var0) throws Exception {
      ap();
      Field var1 = FIELDS.get(var0);
      if (var1 == null) {
         var1 = AP.getDeclaredField(var0);
         var1.setAccessible(true);
         FIELDS.put(var0, var1);
      }

      return var1;
   }

   private static Object sget(String var0) throws Exception {
      return apField(var0).get(null);
   }

   private static void sset(String var0, Object var1) throws Exception {
      Field var2 = apField(var0);
      Class var3 = var2.getType();
      if (var3 == int.class) {
         var2.setInt(null, ((Number)var1).intValue());
      } else if (var3 == boolean.class) {
         var2.setBoolean(null, (Boolean)var1);
      } else if (var3 == float.class) {
         var2.setFloat(null, ((Number)var1).floatValue());
      } else if (var3 == double.class) {
         var2.setDouble(null, ((Number)var1).doubleValue());
      } else {
         var2.set(null, var1);
      }
   }

   private static void callStaticNoArg(String var0) throws Exception {
      ap();
      Method var1 = AP.getDeclaredMethod(var0);
      var1.setAccessible(true);
      var1.invoke(null);
   }

   private static Object readField(Object var0, String var1) throws Exception {
      for (Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
         try {
            Field var3 = var2.getDeclaredField(var1);
            var3.setAccessible(true);
            return var3.get(var0);
         } catch (NoSuchFieldException var4) {
         }
      }

      throw new NoSuchFieldException(var1);
   }

   private static Object callNoArg(Object var0, String var1) throws Exception {
      for (Class var2 = var0.getClass(); var2 != null; var2 = var2.getSuperclass()) {
         try {
            Method var3 = var2.getDeclaredMethod(var1);
            var3.setAccessible(true);
            return var3.invoke(var0);
         } catch (NoSuchMethodException var4) {
         }
      }

      throw new NoSuchMethodException(var1);
   }

   private static void say(CommandContext<?> var0, String var1) {
      try {
         ap();
         Method var2 = AP.getDeclaredMethod("say", CommandContext.class, String.class);
         var2.setAccessible(true);
         var2.invoke(null, var0, var1);
      } catch (Throwable var3) {
      }
   }
}
