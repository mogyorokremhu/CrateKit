# CrateKit

A Minecraft mod for Fabric servers that lets you **create kits from your inventory** and lets players **claim them by typing a command in chat**, with a chest-style GUI for browsing and managing everything.

- **Server-side only.** Players do not need to install anything, vanilla clients can join.
- **Your own claim commands.** Each kit can have its own command, like `/starter` or `/vip`. If you don't pick one, players use `/kit claim <name>`.
- **Cooldowns.** Choose how long players wait before they can claim a kit again, or make it one-time only.
- **GUI.** Browse, preview, claim, create, edit and delete kits from menus.
- **Everything is editable.** Contents, name, icon, claim command and cooldown can all be changed later.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | [Fabric](https://fabricmc.net/) 0.16+ |
| Requires | [Fabric API](https://modrinth.com/mod/fabric-api) |
| Java | 21 |

## Installing

1. Install [Fabric](https://fabricmc.net/use/server/) on your server and add [Fabric API](https://modrinth.com/mod/fabric-api) to the `mods` folder.
2. Download `cratekit-<version>.jar` from the [latest release](../../releases/latest) and put it in the same `mods` folder.
3. Restart the server.

That is all. The `.jar` file *is* the packaged mod.

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

### For admins (operators, permission level 2)

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
| `/kit reload` | Reloads `kits.json` from disk. |

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

Everything lives in `config/cratekit/`:

- `kits.json`: your kits (safe to back up, and edit with the server stopped).
- `claims.json`: when each player last claimed each kit.

## Building from source

You need Java 21.

```bash
./gradlew build
```

The finished mod is `build/libs/cratekit-<version>.jar`.

## Releasing a new version

1. Update `mod_version` in `gradle.properties` and add a section to `CHANGELOG.md`.
2. Commit, then tag and push: `git tag v1.0.1 && git push origin v1.0.1`.
3. GitHub Actions builds the mod and publishes a release with the `.jar` attached.

## License

[MIT](LICENSE). You can use, change and share this mod freely, including on commercial servers, as long as the license text stays with it.
