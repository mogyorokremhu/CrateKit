package hu.cratekit.gui;

import hu.cratekit.ChatPrompt;
import hu.cratekit.CrateKit;
import hu.cratekit.Fmt;
import hu.cratekit.Kit;
import hu.cratekit.KitManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

/** Admin editor for a single kit. */
public final class KitEditor extends Gui {
	private final Kit kit;

	public KitEditor(ServerPlayerEntity player, Kit kit) {
		super(player, 5);
		this.kit = kit;
	}

	@Override
	protected Text title() {
		return Fmt.parse("&6Edit: &r" + kit.displayName);
	}

	private void reopen() {
		new KitEditor(player, kit).open();
	}

	@Override
	protected void build() {
		set(4, decorate(kit.menuIcon(), "&e&l" + kit.displayName, List.of(
			"&7Id: &f" + kit.name,
			"&7Stacks: &f" + kit.items.size(),
			"&7Cooldown: &f" + Fmt.cooldown(kit.cooldownSeconds),
			"&7Command: &f" + (kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias + " &8(and /kit claim " + kit.name + ")"))));

		set(10, button(Items.CHEST, "&aEdit contents", "&7Add, remove and resize the", "&7items in this kit."), c -> {
			click(1.2f);
			new KitContentsEditor(player, kit).open();
		});
		set(12, button(Items.NAME_TAG, "&aRename", "&7Current: &r" + kit.displayName, "&7Supports colour codes like &f&&6gold", "", "&eClick, then type in chat"), c ->
			ChatPrompt.ask(player, "Type the new display name &8(supports &6&&7-colour codes)&7:", input -> {
				if (!ChatPrompt.isCancel(input)) {
					kit.displayName = input;
					CrateKit.kits().changed();
					Fmt.send(player, "Renamed to &r" + input + "&7.");
				}
				reopen();
			}));
		set(14, button(Items.ITEM_FRAME, "&aSet icon", "&7Hold an item in your main hand", "&7and click to use it as the icon.", "&7Shift-click to reset to the default."), c -> {
			if (c.shift()) {
				kit.icon = ItemStack.EMPTY;
			} else {
				ItemStack held = player.getEquippedStack(EquipmentSlot.MAINHAND);
				if (held.isEmpty()) {
					Fmt.send(player, "&cHold the item you want as the icon in your main hand first.");
					return;
				}
				kit.icon = held.copyWithCount(1);
			}
			CrateKit.kits().changed();
			click(1.2f);
			refresh();
		});
		set(16, button(Items.COMMAND_BLOCK, "&aClaim command",
			"&7Players type this in chat to claim.", "&7Current: &f" + (kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias),
			"", "&eClick, then type a command name", "&7(e.g. &fstarter &7for &f/starter&7)", "&eShift-click: &7reset to /kit claim"), c -> {
			if (c.shift()) {
				kit.alias = null;
				CrateKit.kits().changed();
				CrateKit.kits().syncAliases();
				refresh();
				return;
			}
			ChatPrompt.ask(player, "Type the command players should use, without the slash &8(e.g. &fstarter&8)&7:", input -> {
				if (!ChatPrompt.isCancel(input)) {
					String alias = input.toLowerCase().replaceFirst("^/", "");
					KitManager.AliasError err = CrateKit.kits().checkAlias(kit, alias);
					switch (err) {
						case INVALID -> Fmt.send(player, "&cCommands may only use letters, numbers, '_' and '-'.");
						case TAKEN -> Fmt.send(player, "&c/" + alias + " is already used by another kit.");
						case RESERVED -> Fmt.send(player, "&c/" + alias + " is already an existing command.");
						case OK -> {
							kit.alias = alias;
							CrateKit.kits().changed();
							CrateKit.kits().syncAliases();
							Fmt.send(player, "Players can now claim this kit with &f/" + alias + "&7.");
						}
					}
				}
				reopen();
			});
		});

		set(28, button(Items.CLOCK, "&aCooldown", "&7Current: &f" + Fmt.cooldown(kit.cooldownSeconds), "&7Time a player must wait", "&7before claiming again.", "", "&eClick to change"), c -> {
			click(1.2f);
			new CooldownMenu(player, kit).open();
		});
		set(30, button(Items.HOPPER, "&aReplace with my inventory", "&7Overwrites the kit contents", "&7with what you are carrying now.", "", "&eClick to confirm"), c -> {
			CrateKit.kits().updateContents(kit, player);
			Fmt.send(player, "Kit contents replaced with your inventory.");
			click(1.2f);
			refresh();
		});
		set(32, button(Items.ENDER_EYE, "&aPreview", "&7See what players will get."), c -> {
			click(1.2f);
			new KitPreview(player, kit, this::reopen).open();
		});
		set(34, button(Items.DIAMOND, "&aGive to me", "&7Gives you the kit, ignoring", "&7the cooldown."), c -> {
			CrateKit.kits().give(player, kit);
			Fmt.send(player, "Gave you the kit.");
			click(1.5f);
		});

		set(36, button(Items.ARROW, "&eBack to all kits"), c -> {
			click(1f);
			new KitMenu(player, true).open();
		});
		set(44, button(Items.TNT, "&c&lDelete kit", "&7Permanently removes this kit.", "", "&eClick to continue"), c -> {
			click(0.8f);
			new ConfirmDelete(player, kit).open();
		});
		fillEmpty();
	}
}
