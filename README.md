# Autofly-Mapper
A small client-side Fabric mod for Minecraft 26.1.2(needs fabric api 0.155.3+26.1.2).  Pick two corners and it walks a "lawnmower" sweep over the whole rectangle between them. useful for large map downloads

# Important
Requires Fabric API
Now can use both Elytrafly and Rockets
source code is only available for the version  V1.10.3
made using Claude Sonnet 5.5


# Area Autopilot Commands


## 1. Main Setup

```text

Choose any Y-level you want. Above build height is recommended.

/autopilot a <x> <z>
/autopilot b <x> <z>
/autopilot start
/autopilot startfrom current
/autopilot startfrom <x> <z>
/autopilot resume
/autopilot stop
/autopilot status
```

# 2. Flight Settings

```text
/autopilot altitude auto
/autopilot altitude <y>
/autopilot elytra <true/false>
/autopilot spacing <n>
/autopilot turnspeed <degrees>
/autopilot durability <n>
/autopilot minrockets <n>
```

# 3. Firework Settings

```text
/autopilot fireworks
/autopilot fireworks auto
/autopilot fireworks true
/autopilot fireworks false
```

# 4. Landing & Safety Settings

```text
/autopilot safeland <true/false>
/autopilot landband <true/false>
/autopilot flyband <true/false>
/autopilot disconnect <true/false>
/autopilot elytraswap <true/false>
/autopilot landnow
/autopilot landy <min> <max>
/autopilot flyy <min> <max>
```

# 5. Flight Trail

```text
/autopilot trail
/autopilot trail on
/autopilot trail off
/autopilot trail color <name>
/autopilot trail color <#hex>
/autopilot trail color <r> <g> <b>
```

# 6. Cache Management

```text
/autopilot cache
/autopilot cache clear
```

# 7. Information & Debugging

```text
/autopilot extras
/autopilot debug
```

---

# Notes

- `/autopilot a` and `/autopilot b` set corners using your current position.
- `<x> <z>` allows you to specify coordinates manually.
- `startfrom current` starts from your current location without changing the original corners.
- `cache clear` resets saved corners, route progress, generated paths and caches.
- Turning the flight trail off does not erase recorded trail points.
- `spacing` controls the distance between flight paths.
- `turnspeed` controls how quickly the autopilot turns.


# Decomp ur self

This folder has everything you need to turn the compiled mod **Area Autopilot** into readable Java source code.

## What's in this folder
- `areaautopilot-1_10_3.jar`: the mod (compiled)
- `vineflower-1.12.0-slim.jar`: the decompiler (Vineflower)

## What you need
- **Java 17 or newer** (the mod itself needs Java 21). Check with: `java -version`

## Steps

1. **Open a terminal in this folder**
   - Windows: open the folder, click the address bar, type `cmd`, press Enter
   - Mac: right-click the folder, choose "New Terminal at Folder"
   - Linux: right-click in the folder, choose "Open in Terminal"
   - Or type `cd "Decomp ur self"` (keep the quotes because of the spaces)

2. **Run these two commands**

   ```
   mkdir src
   java -jar vineflower-1.12.0-slim.jar areaautopilot-1_10_3.jar src
   ```

3. **Open the output**
   The source files are in `src/com/example/areaautopilot/`.
   If you only see a `.jar` inside `src`, unzip it to get the `.java` files.

## What you'll find

| File | Purpose |
|---|---|
| `AreaAutopilot.java` | Main mod: commands, sweep path, walking and elytra flight, landing |
| `AutopilotExt.java` | Extra features and helpers |
| `AutopilotPlus.java` | Saved state, `fireworks` and `startfrom` commands |
| `AutopilotFly.java` | Low-elytra-durability check and safe landing |
| `AutopilotTrail.java` | Colored particle flight-path line |

## Good to know
- Decompiled code is **not identical** to the original. Local variable names show as `var0`, `var1`, etc., and comments are gone.
- You may see warnings about missing Minecraft classes. This is normal, because Minecraft's own code is not inside the mod jar.
- Vineflower is a separate project: https://github.com/Vineflower/vineflower
- Other decompilers also work: **CFR** (command line) or **Recaf** (graphical).

## License
The mod's `fabric.mod.json` lists the license as **CC0-1.0** (public domain).

