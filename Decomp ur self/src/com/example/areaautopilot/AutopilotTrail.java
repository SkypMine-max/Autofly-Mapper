package com.example.areaautopilot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class AutopilotTrail {
   private static final String AP_NAME = "com.example.areaautopilot.AreaAutopilot";
   private static final String EXT_NAME = "com.example.areaautopilot.AutopilotExt";
   private static final String PLUS_NAME = "com.example.areaautopilot.AutopilotPlus";
   private static final String MC_NAME = "net.minecraft.client.Minecraft";
   private static final Map<String, Integer> NAMED = new LinkedHashMap<>();
   private static boolean trailOn = true;
   private static int color = 65535;
   private static String colorLabel = "cyan";
   private static boolean cfgLoaded;
   private static double[] pts = new double[768];
   private static int count;
   private static double lastX;
   private static double lastY;
   private static double lastZ;
   private static boolean haveLast;
   private static int tickN;
   private static boolean warned;
   private static final Map<String, Field> FIELDS = new HashMap<>();
   private static final Map<String, Method> METHODS = new HashMap<>();
   private static Object dustOpts;
   private static int dustColor = -1;
   private static Method particleM;
   private static Class<?> particleLevelClass;
   private static final double RANGE = 48.0;
   private static final int BUDGET = 600;
   private static final int EMIT_EVERY = 10;

   private AutopilotTrail() {
   }

   public static void register(CommandDispatcher var0) {
      try {
         loadCfg();
         LiteralArgumentBuilder var1 = LiteralArgumentBuilder.literal("autopilot");
         LiteralArgumentBuilder var2 = LiteralArgumentBuilder.literal("cache");
         Command var3 = var0x -> {
            say(var0x, cacheSummary());
            return 1;
         };
         Command var4 = var0x -> {
            say(var0x, clearAll());
            return 1;
         };
         var2.executes(var3);
         LiteralArgumentBuilder var5 = LiteralArgumentBuilder.literal("clear");
         var5.executes(var4);
         var2.then(var5);
         var1.then(var2);
         LiteralArgumentBuilder var6 = LiteralArgumentBuilder.literal("trail");
         Command var7 = var0x -> {
            say(var0x, trailStatus());
            return 1;
         };
         var6.executes(var7);
         LiteralArgumentBuilder var8 = LiteralArgumentBuilder.literal("on");
         Command var9 = var0x -> {
            trailOn = true;
            saveCfg();
            say(var0x, "Flight trail ON. " + trailStatus());
            return 1;
         };
         var8.executes(var9);
         var6.then(var8);
         LiteralArgumentBuilder var10 = LiteralArgumentBuilder.literal("off");
         Command var11 = var0x -> {
            trailOn = false;
            saveCfg();
            say(var0x, "Flight trail OFF (points are still recorded while flying; use /autopilot cache clear to wipe them).");
            return 1;
         };
         var10.executes(var11);
         var6.then(var10);
         LiteralArgumentBuilder var12 = LiteralArgumentBuilder.literal("color");
         Command var13 = var0x -> {
            say(var0x, "Trail color: " + colorLabel + ". Use /autopilot trail color <" + names() + " | #RRGGBB | r g b>");
            return 1;
         };
         var12.executes(var13);
         RequiredArgumentBuilder var14 = RequiredArgumentBuilder.argument("value", StringArgumentType.greedyString());
         Command var15 = var0x -> {
            String var1x = StringArgumentType.getString(var0x, "value");
            int var2x = parseColor(var1x);
            if (var2x < 0) {
               say(var0x, "Unknown color '" + var1x + "'. Try: " + names() + ", #RRGGBB, or r g b (0-255 each).");
               return 0;
            } else {
               color = var2x;
               colorLabel = labelFor(var1x, var2x);
               trailOn = true;
               saveCfg();
               say(var0x, "Trail color set to " + colorLabel + ".");
               return 1;
            }
         };
         var14.executes(var15);
         var12.then(var14);
         var6.then(var12);
         var1.then(var6);
         var0.register(var1);
      } catch (Throwable var16) {
         warn("register", var16);
      }
   }

   private static String names() {
      return String.join(", ", NAMED.keySet());
   }

   private static String trailStatus() {
      return "Trail " + (trailOn ? "ON" : "OFF") + ", color " + colorLabel + ", " + count + " points recorded.";
   }

   private static String cacheSummary() {
      int var0 = -1;
      int var1 = -1;

      try {
         var0 = ((Map)getStatic(Class.forName("com.example.areaautopilot.AutopilotExt"), "COL")).size();
      } catch (Throwable var5) {
      }

      try {
         var1 = ((List)getStatic(Class.forName("com.example.areaautopilot.AreaAutopilot"), "path")).size();
      } catch (Throwable var4) {
      }

      long var2 = pts.length * 8L / 1024L;
      return "Memory: "
         + count
         + " trail points (~"
         + var2
         + " KB), "
         + (var0 < 0 ? "?" : String.valueOf(var0))
         + " cached columns, "
         + (var1 < 0 ? "?" : String.valueOf(var1))
         + " waypoints. /autopilot cache clear resets all of it plus points A/B and progress.";
   }

   private static String clearAll() {
      int var0 = count;
      int var1 = 0;
      int var2 = 0;
      boolean var3 = false;
      Class var4 = null;

      try {
         var4 = Class.forName("com.example.areaautopilot.AreaAutopilot");
      } catch (Throwable var12) {
         warn("clear/class", var12);
      }

      if (var4 != null) {
         try {
            Object var5 = getStatic(var4, "running");
            var3 = var5 instanceof Boolean && (Boolean)var5;
            Object var6 = mcInstance();
            if (var3 && var6 != null) {
               Method var7 = var4.getDeclaredMethod("stop", Class.forName("net.minecraft.client.Minecraft"));
               var7.setAccessible(true);
               var7.invoke(null, var6);
            }
         } catch (Throwable var13) {
            warn("clear/stop", var13);
         }

         try {
            Object var14 = getStatic(var4, "path");
            if (var14 instanceof List) {
               var2 = ((List)var14).size();
            }
         } catch (Throwable var11) {
         }

         trySet(var4, "running", Boolean.FALSE);
         trySet(var4, "path", new ArrayList());
         trySet(var4, "ax", null);
         trySet(var4, "az", null);
         trySet(var4, "bx", null);
         trySet(var4, "bz", null);
         trySet(var4, "idx", 0);
         trySet(var4, "landing", Boolean.FALSE);
         trySet(var4, "landTicks", 0);
         trySet(var4, "stuckTicks", 0);
         trySet(var4, "strafeTicks", 0);
      }

      try {
         Class var15 = Class.forName("com.example.areaautopilot.AutopilotExt");
         Object var17 = getStatic(var15, "COL");
         if (var17 instanceof Map) {
            var1 = ((Map)var17).size();
         }

         Method var19 = var15.getDeclaredMethod("resetSession");
         var19.setAccessible(true);
         var19.invoke(null);
      } catch (Throwable var10) {
         warn("clear/ext", var10);
      }

      clearTrail();

      try {
         Class var16 = Class.forName("com.example.areaautopilot.AutopilotPlus");
         Method var18 = var16.getDeclaredMethod("writeState");
         var18.setAccessible(true);
         var18.invoke(null);
      } catch (Throwable var9) {
         warn("clear/state-file", var9);
      }

      try {
         System.gc();
      } catch (Throwable var8) {
      }

      return "Cache cleared: "
         + var0
         + " trail points, "
         + var1
         + " cached columns, "
         + var2
         + " waypoints; points A/B and progress reset"
         + (var3 ? " (autopilot stopped)" : "")
         + ".";
   }

   private static void trySet(Class<?> var0, String var1, Object var2) {
      try {
         setStatic(var0, var1, var2);
      } catch (Throwable var4) {
         warn("clear/" + var1, var4);
      }
   }

   private static void clearTrail() {
      pts = new double[768];
      count = 0;
      haveLast = false;
   }

   public static void onTick(Object var0) {
      try {
         if (var0 == null) {
            return;
         }

         if (!cfgLoaded) {
            loadCfg();
         }

         Object var1 = fieldOf(var0, "player");
         Object var2 = fieldOf(var0, "level");
         if (var1 == null || var2 == null) {
            return;
         }

         tickN++;
         if (isRunning()) {
            record(var1);
         }

         if (trailOn && tickN % 10 == 0) {
            emit(var2, var1);
         }
      } catch (Throwable var3) {
         warn("tick", var3);
      }
   }

   private static boolean isRunning() throws Exception {
      Object var0 = getStatic(Class.forName("com.example.areaautopilot.AreaAutopilot"), "running");
      return var0 instanceof Boolean && (Boolean)var0;
   }

   private static void record(Object var0) throws Exception {
      double var1 = num(var0, "getX");
      double var3 = num(var0, "getY") + 0.2;
      double var5 = num(var0, "getZ");
      if (haveLast) {
         double var7 = var1 - lastX;
         double var9 = var3 - lastY;
         double var11 = var5 - lastZ;
         if (var7 * var7 + var9 * var9 + var11 * var11 < 0.64) {
            return;
         }
      }

      if (count * 3 + 3 > pts.length) {
         double[] var13 = new double[pts.length + pts.length / 2];
         System.arraycopy(pts, 0, var13, 0, count * 3);
         pts = var13;
      }

      pts[count * 3] = var1;
      pts[count * 3 + 1] = var3;
      pts[count * 3 + 2] = var5;
      count++;
      lastX = var1;
      lastY = var3;
      lastZ = var5;
      haveLast = true;
   }

   private static void emit(Object var0, Object var1) throws Exception {
      Object var2 = dust();
      Method var3 = particleMethod(var0, var2);
      if (var3 != null) {
         int var4 = var3.getParameterCount() - 7;
         double var5 = num(var1, "getX");
         double var7 = num(var1, "getY");
         double var9 = num(var1, "getZ");
         double var11 = 2304.0;
         if (isRunning()) {
            emitAhead(var3, var4, var0, var2, var5, var7 + 0.2, var9);
         }

         int var13 = 600;

         for (int var14 = count - 1; var14 >= 0 && var13 > 0; var14--) {
            double var15 = pts[var14 * 3];
            double var17 = pts[var14 * 3 + 1];
            double var19 = pts[var14 * 3 + 2];
            double var21 = var15 - var5;
            double var23 = var17 - var7;
            double var25 = var19 - var9;
            if (!(var21 * var21 + var23 * var23 + var25 * var25 > var11)) {
               spawn(var3, var4, var0, var2, var15, var17, var19);
               var13--;
               if (var14 + 1 < count) {
                  double var27 = pts[var14 * 3 + 3];
                  double var29 = pts[var14 * 3 + 4];
                  double var31 = pts[var14 * 3 + 5];
                  double var33 = var27 - var15;
                  double var35 = var29 - var17;
                  double var37 = var31 - var19;
                  double var39 = Math.sqrt(var33 * var33 + var35 * var35 + var37 * var37);
                  if (var39 > 1.3 && var39 < 8.0) {
                     int var41 = (int)Math.min(7.0, Math.floor(var39));

                     for (int var42 = 1; var42 < var41 && var13 > 0; var42++) {
                        double var43 = (double)var42 / var41;
                        spawn(var3, var4, var0, var2, var15 + var33 * var43, var17 + var35 * var43, var19 + var37 * var43);
                        var13--;
                     }
                  }
               }
            }
         }
      }
   }

   private static void emitAhead(Method var0, int var1, Object var2, Object var3, double var4, double var6, double var8) throws Exception {
      Class var10 = Class.forName("com.example.areaautopilot.AreaAutopilot");
      Object var11 = getStatic(var10, "path");
      Object var12 = getStatic(var10, "idx");
      if (var11 instanceof List && var12 instanceof Integer) {
         List var13 = (List)var11;
         int var14 = Math.max(0, (Integer)var12);
         double var15 = 2304.0;
         int var17 = 500;
         double var18 = var4;
         double var20 = var8;
         int var22 = Math.min(var13.size(), var14 + 12);

         for (int var23 = var14; var23 < var22 && var17 > 0; var23++) {
            int[] var24 = (int[])var13.get(var23);
            double var25 = var24[0] + 0.5;
            double var27 = var24[1] + 0.5;
            double var29 = var25 - var18;
            double var31 = var27 - var20;
            double var33 = Math.sqrt(var29 * var29 + var31 * var31);
            int var35 = (int)Math.max(1.0, Math.ceil(var33 / 1.5));

            for (int var36 = 0; var36 <= var35 && var17 > 0; var36++) {
               double var37 = (double)var36 / var35;
               double var39 = var18 + var29 * var37;
               double var41 = var20 + var31 * var37;
               double var43 = var39 - var4;
               double var45 = var41 - var8;
               if (!(var43 * var43 + var45 * var45 > var15)) {
                  spawn(var0, var1, var2, var3, var39, var6, var41);
                  var17--;
               }
            }

            var18 = var25;
            var20 = var27;
         }
      }
   }

   private static void spawn(Method var0, int var1, Object var2, Object var3, double var4, double var6, double var8) throws Exception {
      Object[] var10 = new Object[7 + var1];
      var10[0] = var3;

      for (int var11 = 1; var11 <= var1; var11++) {
         var10[var11] = Boolean.TRUE;
      }

      var10[1 + var1] = var4;
      var10[2 + var1] = var6;
      var10[3 + var1] = var8;
      var10[4 + var1] = 0.0;
      var10[5 + var1] = 0.0;
      var10[6 + var1] = 0.0;
      var0.invoke(var2, var10);
   }

   private static Object dust() throws Exception {
      if (dustOpts != null && dustColor == color) {
         return dustOpts;
      }

      Class var0 = Class.forName("net.minecraft.core.particles.DustParticleOptions");
      Object var1 = null;

      for (Constructor var5 : var0.getConstructors()) {
         Class[] var6 = var5.getParameterTypes();
         if (var6.length == 2 && var6[0] == int.class && var6[1] == float.class) {
            var1 = var5.newInstance(0xFF000000 | color, 1.5F);
            break;
         }
      }

      if (var1 == null) {
         Class var9 = Class.forName("org.joml.Vector3f");

         for (Constructor var13 : var0.getConstructors()) {
            Class[] var7 = var13.getParameterTypes();
            if (var7.length == 2 && var7[0] == var9 && var7[1] == float.class) {
               Object var8 = var9.getConstructor(float.class, float.class, float.class)
                  .newInstance((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
               var1 = var13.newInstance(var8, 1.5F);
               break;
            }
         }
      }

      if (var1 == null) {
         throw new IllegalStateException("DustParticleOptions constructor not found");
      }

      dustOpts = var1;
      dustColor = color;
      return var1;
   }

   private static Method particleMethod(Object var0, Object var1) {
      if (particleM != null && particleLevelClass == var0.getClass()) {
         return particleM;
      }

      for (String var5 : new String[]{"addAlwaysVisibleParticle", "addParticle"}) {
         Method var6 = null;
         int var7 = 99;

         for (Method var11 : var0.getClass().getMethods()) {
            if (var11.getName().equals(var5)) {
               Class[] var12 = var11.getParameterTypes();
               if (var12.length >= 7 && var12[0].isInstance(var1)) {
                  int var13 = 0;

                  int var14;
                  for (var14 = 1; var14 < var12.length && var12[var14] == boolean.class; var14++) {
                     var13++;
                  }

                  if (var12.length - var14 == 6) {
                     boolean var15 = true;

                     for (int var16 = var14; var16 < var12.length; var16++) {
                        if (var12[var16] != double.class) {
                           var15 = false;
                        }
                     }

                     if (var15 && var13 < var7) {
                        var6 = var11;
                        var7 = var13;
                     }
                  }
               }
            }
         }

         if (var6 != null) {
            try {
               var6.setAccessible(true);
            } catch (Throwable var17) {
            }

            particleM = var6;
            particleLevelClass = var0.getClass();
            return var6;
         }
      }

      if (!warned) {
         warned = true;
         System.err.println("[areaautopilot] trail: no addParticle method found");
      }

      return null;
   }

   static int parseColor(String var0) {
      if (var0 == null) {
         return -1;
      }

      var0 = var0.trim().toLowerCase();
      if (var0.isEmpty()) {
         return -1;
      }

      Integer var1 = NAMED.get(var0);
      if (var1 != null) {
         return var1;
      }

      try {
         String var2 = var0.startsWith("#") ? var0.substring(1) : (var0.startsWith("0x") ? var0.substring(2) : var0);
         if (var2.matches("[0-9a-f]{6}")) {
            return Integer.parseInt(var2, 16);
         }

         if (var2.matches("[0-9a-f]{3}")) {
            int var9 = Character.digit(var2.charAt(0), 16) * 17;
            int var10 = Character.digit(var2.charAt(1), 16) * 17;
            int var11 = Character.digit(var2.charAt(2), 16) * 17;
            return var9 << 16 | var10 << 8 | var11;
         }

         String[] var3 = var0.split("[\\s,]+");
         if (var3.length == 3) {
            int var4 = Integer.parseInt(var3[0]);
            int var5 = Integer.parseInt(var3[1]);
            int var6 = Integer.parseInt(var3[2]);
            if (var4 >= 0 && var4 <= 255 && var5 >= 0 && var5 <= 255 && var6 >= 0 && var6 <= 255) {
               return var4 << 16 | var5 << 8 | var6;
            }
         }
      } catch (Throwable var7) {
      }

      return -1;
   }

   private static String labelFor(String var0, int var1) {
      String var2 = var0.trim().toLowerCase();
      return NAMED.containsKey(var2) ? var2 : String.format("#%06X", var1);
   }

   private static File cfg() {
      return new File("config", "areaautopilot-trail.properties");
   }

   private static void loadCfg() {
      cfgLoaded = true;

      try {
         File var0 = cfg();
         if (!var0.isFile()) {
            return;
         }

         Properties var1 = new Properties();
         FileInputStream var2 = new FileInputStream(var0);

         try {
            var1.load(var2);
         } catch (Throwable var6) {
            try {
               var2.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }

            throw var6;
         }

         var2.close();
         String var8 = var1.getProperty("on");
         if (var8 != null) {
            trailOn = Boolean.parseBoolean(var8.trim());
         }

         String var3 = var1.getProperty("color");
         if (var3 != null) {
            int var4 = parseColor(var3);
            if (var4 >= 0) {
               color = var4;
               colorLabel = labelFor(var3, var4);
            }
         }
      } catch (Throwable var7) {
         warn("loadCfg", var7);
      }
   }

   private static void saveCfg() {
      try {
         File var0 = cfg();
         File var1 = var0.getParentFile();
         if (var1 != null) {
            var1.mkdirs();
         }

         Properties var2 = new Properties();
         var2.setProperty("on", String.valueOf(trailOn));
         var2.setProperty("color", colorLabel);

         try (FileOutputStream var3 = new FileOutputStream(var0)) {
            var2.store(var3, "Area Autopilot trail settings");
         }
      } catch (Throwable var8) {
         warn("saveCfg", var8);
      }
   }

   private static void say(CommandContext<?> var0, String var1) {
      try {
         Method var2 = Class.forName("com.example.areaautopilot.AreaAutopilot").getDeclaredMethod("say", CommandContext.class, String.class);
         var2.setAccessible(true);
         var2.invoke(null, var0, var1);
      } catch (Throwable var3) {
         warn("say", var3);
      }
   }

   private static Object mcInstance() {
      try {
         return Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null);
      } catch (Throwable var1) {
         return null;
      }
   }

   private static Field findField(Class<?> var0, String var1) throws NoSuchFieldException {
      String var2 = var0.getName() + "#" + var1;
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

   private static Object fieldOf(Object var0, String var1) throws Exception {
      return findField(var0.getClass(), var1).get(var0);
   }

   private static Object getStatic(Class<?> var0, String var1) throws Exception {
      return findField(var0, var1).get(null);
   }

   private static void setStatic(Class<?> var0, String var1, Object var2) throws Exception {
      findField(var0, var1).set(null, var2);
   }

   private static double num(Object var0, String var1) throws Exception {
      String var2 = var0.getClass().getName() + "#" + var1;
      Method var3 = METHODS.get(var2);
      if (var3 == null) {
         var3 = var0.getClass().getMethod(var1);
         var3.setAccessible(true);
         METHODS.put(var2, var3);
      }

      return ((Number)var3.invoke(var0)).doubleValue();
   }

   private static void warn(String var0, Throwable var1) {
      if (!warned) {
         warned = true;
         System.err.println("[areaautopilot] trail/" + var0 + ": " + var1);
      }
   }

   static {
      NAMED.put("red", 16719904);
      NAMED.put("orange", 16744448);
      NAMED.put("yellow", 16776960);
      NAMED.put("lime", 8453888);
      NAMED.put("green", 57344);
      NAMED.put("cyan", 65535);
      NAMED.put("blue", 3166463);
      NAMED.put("purple", 10498288);
      NAMED.put("magenta", 16711935);
      NAMED.put("pink", 16744640);
      NAMED.put("white", 16777215);
      NAMED.put("gray", 9474192);
   }
}
