package hu.cratekit.gui;

import hu.cratekit.CrateKit;
import hu.cratekit.Fmt;
import hu.cratekit.Kit;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** "Are you sure?" screen for deleting a kit. */
public final class ConfirmDelete extends Gui {
	private final Kit kit;

	public ConfirmDelete(ServerPlayer player, Kit kit) {
		super(player, 3);
		this.kit = kit;
	}

	@Override
	protected Component title() {
		return Fmt.parse("&cDelete " + kit.displayName + "&c?");
	}

	@Override
	protected void build() {
		set(11, button(Items.LIME_CONCRETE, "&a&lYes, delete it", "&7This cannot be undone."), c -> {
			CrateKit.kits().delete(kit);
			Fmt.send(player, "Deleted kit &e" + kit.name + "&7.");
			new KitMenu(player, true).open();
		});
		set(13, decorate(kit.menuIcon(), "&e" + kit.displayName, java.util.List.of("&7" + kit.items.size() + " stacks")));
		set(15, button(Items.RED_CONCRETE, "&c&lNo, keep it"), c -> new KitEditor(player, kit).open());
		fillEmpty();
	}
}
