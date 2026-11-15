package hu.cratekit.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Collections;

/** "Are you sure?" screen for deleting a kit. */
public final class ConfirmDelete extends Gui {
	private final Kit kit;

	public ConfirmDelete(CrateKitPlugin plugin, Player player, Kit kit) {
		super(plugin, player, 3);
		this.kit = kit;
	}

	@Override
	protected String title() {
		return "&cDelete " + kit.name + "?";
	}

	@Override
	protected void build() {
		set(11, button(Material.LIME_CONCRETE, "&a&lYes, delete it", "&7This cannot be undone."), c -> {
			plugin.kits().delete(kit);
			Msg.send(player, "Deleted kit &e" + kit.name + "&7.");
			new KitMenu(plugin, player, true).open();
		});
		set(13, decorate(kit.menuIcon(), "&e" + kit.displayName, Collections.singletonList("&7" + kit.items.size() + " stacks")));
		set(15, button(Material.RED_CONCRETE, "&c&lNo, keep it"), c -> new KitEditor(plugin, player, kit).open());
		fillEmpty();
	}
}
