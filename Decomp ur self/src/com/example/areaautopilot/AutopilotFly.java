package com.example.areaautopilot;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class AutopilotFly {
   private static final String AP_N = "com.example.areaautopilot.AreaAutopilot";
   private static final String EX_N = "com.example.areaautopilot.AutopilotExt";
   private static final String PL_N = "com.example.areaautopilot.AutopilotPlus";
   private static Class<?> AP;
   private static Class<?> EX;
   private static Class<?> PL;
   private static boolean mendNoted_unused;
   private static int flushCd;

   private AutopilotFly() {
   }

   private static Class<?> ap() throws Exception {
      if (AP == null) {
         AP = Class.forName("com.example.areaautopilot.AreaAutopilot");
      }

      return AP;
   }

   private static Class<?> ex() throws Exception {
      if (EX == null) {
         EX = Class.forName("com.example.areaautopilot.AutopilotExt");
      }

      return EX;
   }

   private static Class<?> pl() throws Exception {
      if (PL == null) {
         PL = Class.forName("com.example.areaautopilot.AutopilotPlus");
      }

      return PL;
   }

   private static Field fld(Class<?> var0, String var1) throws Exception {
      Field var2 = var0.getDeclaredField(var1);
      var2.setAccessible(true);
      return var2;
   }

   private static Object sGet(Class<?> var0, String var1) throws Exception {
      return fld(var0, var1).get(null);
   }

   private static int sInt(Class<?> var0, String var1) throws Exception {
      return ((Number)sGet(var0, var1)).intValue();
   }

   private static boolean sBool(Class<?> var0, String var1) throws Exception {
      return (Boolean)sGet(var0, var1);
   }

   private static void sSet(Class<?> var0, String var1, Object var2) throws Exception {
      Field var3 = fld(var0, var1);
      Class var4 = var3.getType();
      if (var4 == int.class) {
         var3.setInt(null, ((Number)var2).intValue());
      } else if (var4 == boolean.class) {
         var3.setBoolean(null, (Boolean)var2);
      } else {
         var3.set(null, var2);
      }
   }

   private static Object stat(Class<?> var0, String var1, Object... var2) throws Exception {
      for (Method var6 : var0.getDeclaredMethods()) {
         if (var6.getName().equals(var1) && Modifier.isStatic(var6.getModifiers()) && var6.getParameterCount() == var2.length) {
            var6.setAccessible(true);

            try {
               return var6.invoke(null, var2);
            } catch (InvocationTargetException var9) {
               Throwable var8 = var9.getCause();
               if (var8 instanceof Exception) {
                  throw (Exception)var8;
               }

               throw var9;
            } catch (IllegalArgumentException var10) {
            }
         }
      }

      throw new NoSuchMethodException(var0.getName() + "." + var1 + "/" + var2.length);
   }

   private static Object call(Object var0, String var1, Object... var2) throws Exception {
      return stat(ex(), "call", var0, var1, var2);
   }

   private static Object field(Object var0, String var1) throws Exception {
      return stat(ex(), "field", var0, var1);
   }

   private static double num(Object var0) {
      return ((Number)var0).doubleValue();
   }

   private static void tell(Object var0, Object var1, String var2) {
      try {
         stat(ex(), "notify", var0, var1, var2);
      } catch (Throwable var4) {
      }
   }

   public static void tickWalk(Minecraft var0, LocalPlayer var1, float var2, double var3) {
      try {
         if (lowElytraCheck(var0, var1)) {
            return;
         }
      } catch (Throwable var6) {
      }

      origWalk(var0, var1, var2, var3);
   }

   private static void origWalk(Minecraft var0, LocalPlayer var1, float var2, double var3) {
      try {
         Method var5 = ap().getDeclaredMethod("tickWalk", Minecraft.class, LocalPlayer.class, float.class, double.class);
         var5.setAccessible(true);
         var5.invoke(null, var0, var1, var2, var3);
      } catch (InvocationTargetException var7) {
         Throwable var6 = var7.getCause();
         if (var6 instanceof RuntimeException) {
            throw (RuntimeException)var6;
         } else if (var6 instanceof Error) {
            throw (Error)var6;
         } else {
            throw new RuntimeException(var6);
         }
      } catch (Exception var8) {
         throw new RuntimeException(var8);
      }
   }

   private static boolean lowElytraCheck(Object var0, Object var1) throws Exception {
      Class var2 = ap();
      int var3 = sInt(var2, "supplyTimer") - 1;
      if (var3 <= 0) {
         stat(var2, "refreshSupplies", var1);
         var3 = 10;
      }

      sSet(var2, "supplyTimer", var3);
      Class var4 = Class.forName("net.minecraft.world.entity.EquipmentSlot");
      Enum var5 = Enum.valueOf(var4, "CHEST");
      Object var6 = call(var1, "getItemBySlot", var5);
      if (!(Boolean)stat(ex(), "isItem", var6, "elytra")) {
         return false;
      }

      boolean var7 = (Boolean)call(var1, "isFallFlying");
      if (var7) {
         stat(ex(), "tick", var0, var1);
      }

      if (sBool(var2, "landing")) {
         return false;
      }

      int var8 = sInt(var2, "elytraLeft");
      int var9 = sInt(var2, "minDurability");
      if (var8 >= 0 && var8 <= var9) {
         sSet(ex(), "disconnect", Boolean.TRUE);
         flushState();
         if (var7) {
            sSet(var2, "landing", Boolean.TRUE);
            sSet(var2, "landTicks", 0);
            sSet(var2, "landReason", "elytra low (" + var8 + " left)");

            try {
               Object var20 = field(var0, "options");
               Object var21 = field(var20, "keyUse");
               call(var21, "setDown", Boolean.FALSE);
            } catch (Throwable var19) {
            }

            tell(var0, var1, "Autopilot: elytra low (" + var8 + " left) - landing safely, then disconnecting.");
            return true;
         } else {
            Object var10 = call(var1, "position");
            double var11 = num(call(var10, "x"));
            double var13 = num(call(var10, "y"));
            double var15 = num(call(var10, "z"));
            boolean var17 = (Boolean)call(var1, "onGround");
            boolean var18 = (Boolean)call(var1, "isInWater");
            stat(var2, "stop", var0);
            stat(ex(), "afterLanded", var0, var1, var11, var13, var15, var17, var18);
            return true;
         }
      } else {
         return false;
      }
   }

   public static void maybeRepair(Object var0, Object var1) {
   }

   private static void flushState() {
      try {
         stat(pl(), "writeState");
      } catch (Throwable var1) {
      }
   }

   public static void finishDisconnect(Object var0, int var1) {
      flushState();

      try {
         stat(ex(), "finishDisconnect", var0, var1);
      } catch (Throwable var3) {
         throw new RuntimeException(var3);
      }
   }
}
