# Autofly-Mapper
A small client-side Fabric mod for Minecraft 26.1.2(needs fabric api 0.155.3+26.1.2).  Pick two corners and it walks a "lawnmower" sweep over the whole rectangle between them. useful for large map downloads

# Important
Now can use both Elytrafly and Rockets
source code is only available for the version  V1.10.3
made using Claude Sonnet 5.5

# Commands

# Area Autopilot Commands

Choose any Y-level you want. Above build height is recommended.

## 1. Main Setup

```text
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



# updates
Ill update the source code for the new version in the next 48h

