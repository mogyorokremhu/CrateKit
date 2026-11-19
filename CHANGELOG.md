# Changelog

## [1.2.0]

- **New: Fabric for Minecraft 26.3** (`cratekit-fabric-mc26.3-<version>.jar`). Same commands, custom claim commands, cooldowns and GUI as the other versions. Needs Java 25, Fabric Loader 0.20.1 or newer and Fabric API.
- The Paper, Spigot and Purpur plugin is now tested automatically against simulated Minecraft 1.21 and 26.3 servers.
- Fabric 1.21.11 and the Paper plugin are unchanged.

## [1.1.0]

- **New: Paper, Spigot and Purpur plugin** (`cratekit-paper-<version>.jar`). One file works on Paper, Spigot and Purpur, Minecraft 1.17 and newer, with the same commands and GUI as the Fabric mod.
- The Fabric mod is now named `cratekit-fabric-mc<minecraft version>-<version>.jar` so it is clear which Minecraft version it is for (currently 1.21.11).
- Project restructured into one module per platform so more can be added.
- Automated tests for the Paper plugin (commands, cooldowns, custom commands, GUI clicks, saving).

## [1.0.0]

First release.

- Create kits from your inventory with `/kit create <name>`.
- Players claim kits with `/kit claim <name>`, or with a custom command you choose (for example `/starter`).
- Per-kit cooldowns (`30m`, `2h 30m`, `1d`, `none`) or one-time-only kits.
- Chest-style GUI (`/kit`) for browsing, previewing and claiming kits. Players do not need the mod.
- Admin editor GUI to change contents, name, icon, claim command and cooldown, or delete a kit.
- Kits and claim history are saved in `config/cratekit/`.
- Built for Minecraft 1.21.11 on Fabric.
