package hu.cratekit.paper;

import hu.cratekit.common.Durations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;

/** Admin editor for a single kit. */
public final class KitEditor extends Gui {
	private final Kit kit;

	public KitEditor(CrateKitPlugin plugin, Player player, Kit kit) {
		super(plugin, player, 5);
		this.kit = kit;
	}

	@Override
	protected String title() {
		return "&6Edit: &r" + kit.displayName;
	}

	private void reopen() {
		new KitEditor(plugin, player, kit).open();
	}

	@Override
	protected void build() {
		String command = kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias;
		set(4, decorate(kit.menuIcon(), "&e&l" + kit.displayName, Arrays.asList(
			"&7Id: &f" + kit.name,
			"&7Stacks: &f" + kit.items.size(),
			"&7Cooldown: &f" + Durations.describe(kit.cooldownSeconds),
			"&7Command: &f" + command)));

		set(10, button(Material.CHEST, "&aEdit contents", "&7Add, remove and resize the", "&7items in this kit."), c -> {
			click(1.2f);
			new KitContentsEditor(plugin, player, kit).open();
		});
		set(12, button(Material.NAME_TAG, "&aRename", "&7Current: &r" + kit.displayName, "&7Supports colour codes like &f&&6gold", "", "&eClick, then type in chat"), c ->
			plugin.prompt().ask(player, "Type the new display name &8(supports &6&&7-colour codes)&7:", input -> {
				if (!ChatPrompt.isCancel(input)) {
					kit.displayName = input;
					plugin.kits().changed();
					Msg.send(player, "Renamed to &r" + input + "&7.");
				}
				reopen();
			}));
		set(14, button(Material.ITEM_FRAME, "&aSet icon", "&7Hold an item in your main hand", "&7and click to use it as the icon.", "&7Shift-click to reset to the default."), c -> {
			if (c.shift) {
				kit.icon = null;
			} else {
				ItemStack held = player.getInventory().getItemInMainHand();
				if (held == null || held.getType() == Material.AIR) {
					Msg.send(player, "&cHold the item you want as the icon in your main hand first.");
					return;
				}
				kit.icon = held.clone();
				kit.icon.setAmount(1);
			}
			plugin.kits().changed();
			click(1.2f);
			refresh();
		});
		set(16, button(Material.COMMAND_BLOCK, "&aClaim command",
			"&7Players type this in chat to claim.", "&7Current: &f" + command,
			"", "&eClick, then type a command name", "&7(e.g. &fstarter &7for &f/starter&7)", "&eShift-click: &7reset to /kit claim"), c -> {
			if (c.shift) {
				kit.alias = null;
				plugin.kits().changed();
				plugin.kits().syncAliases();
				refresh();
				return;
			}
			plugin.prompt().ask(player, "Type the command players should use, without the slash &8(e.g. &fstarter&8)&7:", input -> {
				if (!ChatPrompt.isCancel(input)) {
					String alias = input.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
					switch (plugin.kits().checkAlias(kit, alias)) {
						case INVALID:
							Msg.send(player, "&cCommands may only use letters, numbers, '_' and '-'.");
							break;
						case TAKEN:
							Msg.send(player, "&c/" + alias + " is already used by another kit.");
							break;
						case RESERVED:
							Msg.send(player, "&c/" + alias + " is already an existing command.");
							break;
						default:
							kit.alias = alias;
							plugin.kits().changed();
							plugin.kits().syncAliases();
							Msg.send(player, "Players can now claim this kit with &f/" + alias + "&7.");
					}
				}
				reopen();
			});
		});

		set(28, button(Material.CLOCK, "&aCooldown", "&7Current: &f" + Durations.describe(kit.cooldownSeconds),
			"&7Time a player must wait", "&7before claiming again.", "", "&eClick to change"), c -> {
			click(1.2f);
			new CooldownMenu(plugin, player, kit).open();
		});
		set(30, button(Material.HOPPER, "&aReplace with my inventory", "&7Overwrites the kit contents", "&7with what you are carrying now.", "", "&eClick to confirm"), c -> {
			plugin.kits().updateContents(kit, player);
			Msg.send(player, "Kit contents replaced with your inventory.");
			click(1.2f);
			refresh();
		});
		set(32, button(Material.ENDER_EYE, "&aPreview", "&7See what players will get."), c -> {
			click(1.2f);
			new KitPreview(plugin, player, kit, this::reopen).open();
		});
		set(34, button(Material.DIAMOND, "&aGive to me", "&7Gives you the kit, ignoring", "&7the cooldown."), c -> {
			plugin.kits().give(player, kit);
			Msg.send(player, "Gave you the kit.");
			click(1.5f);
		});

		set(36, button(Material.ARROW, "&eBack to all kits"), c -> {
			click(1f);
			new KitMenu(plugin, player, true).open();
		});
		set(44, button(Material.TNT, "&c&lDelete kit", "&7Permanently removes this kit.", "", "&eClick to continue"), c -> {
			click(0.8f);
			new ConfirmDelete(plugin, player, kit).open();
		});
		fillEmpty();
	}
}
