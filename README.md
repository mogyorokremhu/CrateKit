# CrateKit

Create **kits** from your inventory and let players **claim them by typing a command in chat**, with a chest-style GUI for browsing and managing everything.

- **Your own claim commands.** Each kit can have its own command, like `/starter` or `/vip`. If you don't pick one, players use `/kit claim <name>`.
- **Cooldowns.** Choose how long players wait before they can claim a kit again, or make it one-time only.
- **GUI.** Browse, preview, claim, create, edit and delete kits from menus.
- **Everything is editable.** Contents, name, icon, claim command and cooldown can all be changed later.
- **Server-side only.** Players do not need to install anything, vanilla clients can join.

## Which file do I download?

Pick the file that matches **what your server runs**, from the [latest release](../../releases/latest).

| Your server | Download | Minecraft versions | Status |
|---|---|---|---|
| **Paper**, **Spigot**, **Purpur** (plugins) | `cratekit-paper-<version>.jar` | 1.17 and newer, including 26.x | Available |
| **Fabric** (mods) | `cratekit-fabric-mc<minecraft version>-<version>.jar` | 1.21.11 | Available |
| Fabric for 26.x | | 26.x | Planned |
| **NeoForge** | | | Planned |
| **Forge** | | | Planned |

Not sure what you run? Look in your server folder: a `plugins` folder means Paper/Spigot/Purpur, a `mods` folder means Fabric, NeoForge or Forge.

A single Paper file covers Paper, Spigot and Purpur because they share the same plugin system. Mods (Fabric, NeoForge, Forge) are tied to one Minecraft version each, so those come as one file per Minecraft version.

## Installing

**Paper / Spigot / Purpur:** put `cratekit-paper-<version>.jar` in the `plugins` folder and restart the server.

**Fabric:** install [Fabric](https://fabricmc.net/use/server/) and [Fabric API](https://modrinth.com/mod/fabric-api), put the CrateKit file in the `mods` folder and restart.

That is all. The `.jar` file *is* the packaged mod or plugin.

## Quick start

1. Put the items you want in the kit into your inventory (armor, offhand and hotbar all count).
2. Run `/kit create starter`.
3. *(Optional)* Give it its own command: `/kit setcommand starter starter`. Players can now type `/starter`.
4. *(Optional)* Set a cooldown: `/kit setcooldown starter 1d`.

Or do all of it from the GUI: run `/kit`, click **Create kit**, then **Manage mode** and click the kit.

## Commands

### For everyone

| Command | What it does |
|---|---|
| `/kit` | Opens the kit menu. Left-click a kit to claim it, right-click to preview it. |
| `/kit list` | Lists kits and whether you can claim them right now. |
| `/kit claim <kit>` | Claims a kit. |
| `/kit preview <kit>` | Shows what is in a kit. |
| `/<custom command>` | Claims the kit it was assigned to, if you set one. |

### For admins

On Paper/Spigot/Purpur this needs the permission `cratekit.admin` (operators have it by default). On Fabric it needs operator permission level 2.

| Command | What it does |
|---|---|
| `/kit create <name>` | Creates a kit from your current inventory. |
| `/kit update <kit>` | Replaces a kit's contents with your current inventory. |
| `/kit edit <kit>` | Opens the editor GUI. |
| `/kit delete <kit>` | Deletes a kit. |
| `/kit rename <kit> <display name>` | Changes the name shown in menus. Supports `&` colour codes, like `&6Starter`. |
| `/kit setcommand <kit> [command]` | Sets the chat command that claims the kit. Leave the command out to go back to `/kit claim`. |
| `/kit setcooldown <kit> <time>` | Sets the cooldown. Examples: `30m`, `2h 30m`, `1d 12h`, `none`, `once`. |
| `/kit give <player> <kit>` | Gives a kit to a player, ignoring the cooldown. |
| `/kit resetcooldown <kit> [player]` | Resets the cooldown for one player or everyone. |
| `/kit info <kit>` | Shows a kit's settings. |
| `/kit reload` | Reloads the saved kits from disk. |

Kit names can use letters, numbers, `_` and `-` (up to 24 characters). Custom commands follow the same rules and cannot clash with an existing command.

## The GUI

- **Main menu** (`/kit`): every kit with its contents count, cooldown, claim command and whether you can claim it now.
- **Preview**: see all items in a kit before claiming.
- **Editor** (admins): change contents, rename, set an icon from the item in your hand, set the claim command, pick a cooldown, replace the contents from your inventory, or delete the kit (with a confirmation).
- **Contents editor**: click an item in your own inventory to add a copy of it to the kit. Shift-left-click a kit item to remove it, right-click to take one off the stack, shift-right-click to add one. Nothing moves out of your inventory, so you cannot lose items by accident.

Chest menus cannot take typed input, so for names and commands the GUI closes and asks you to type in chat. Type `cancel` to abort.

## How claiming works

- Armor in a kit is equipped automatically if that slot is empty, everything else goes into the inventory. If the inventory is full, the rest drops at the player's feet.
- Cooldowns use real time, so they keep counting while the server is off or the player is offline.
- A cooldown of `once` means each player can claim the kit a single time.

## Data

| Platform | Folder | Files |
|---|---|---|
| Paper / Spigot / Purpur | `plugins/CrateKit/` | `kits.yml`, `claims.yml` |
| Fabric | `config/cratekit/` | `kits.json`, `claims.json` |

`kits` holds your kits (back it up, and edit it only with the server stopped). `claims` records when each player last claimed each kit.

## Building from source

You need Java 21.

```bash
./gradlew build
```

Finished files appear in `paper/build/libs/` and `fabric/build/libs/`. The Paper plugin is tested against a simulated server as part of the build.

Project layout: `common/` (code shared by every platform), `paper/` (Paper/Spigot/Purpur plugin), `fabric/` (Fabric mod), `paper-tests/` (tests for the plugin).

## Releasing a new version

1. Update `mod_version` in `gradle.properties` and add a section to `CHANGELOG.md`.
2. Push to `main`, then on GitHub go to **Actions → Release → Run workflow** and type the version (or push a tag like `v1.2.0`).
3. GitHub Actions builds everything and publishes a release with every `.jar` attached.

## License

[MIT](LICENSE). You can use, change and share this freely, including on commercial servers, as long as the license text stays with it.
