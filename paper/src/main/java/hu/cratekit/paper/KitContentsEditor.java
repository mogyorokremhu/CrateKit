package hu.cratekit.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Edits a kit's items. Nothing moves between your inventory and the kit: clicking one of your items adds
 * a copy to the kit, so you cannot lose or duplicate real items here. Every change saves immediately.
 */
public final class KitContentsEditor extends Gui {
	private static final int CAPACITY = 45;

	private final Kit kit;

	public KitContentsEditor(CrateKitPlugin plugin, Player player, Kit kit) {
		super(plugin, player, 6);
		this.kit = kit;
	}

	@Override
	protected String title() {
		return "&6Contents: &r" + kit.displayName;
	}

	@Override
	protected void build() {
		for (int i = 0; i < kit.items.size() && i < CAPACITY; i++) {
			final int index = i;
			set(i, kit.items.get(i).clone(), c -> {
				ItemStack stack = kit.items.get(index);
				if (c.shift && c.left) {
					kit.items.remove(index);
				} else if (c.right && !c.shift) {
					stack.setAmount(stack.getAmount() - 1);
					if (stack.getAmount() <= 0) {
						kit.items.remove(index);
					}
				} else if (c.right) {
					if (stack.getAmount() < stack.getMaxStackSize()) {
						stack.setAmount(stack.getAmount() + 1);
					}
				} else {
					return;
				}
				plugin.kits().changed();
				click(1.4f);
				refresh();
			});
		}
		set(45, button(Material.ARROW, "&eBack"), c -> {
			click(1f);
			new KitEditor(plugin, player, kit).open();
		});
		set(47, button(Material.LAVA_BUCKET, "&cClear all items", "&7Removes every item from this kit.", "", "&eShift-click to confirm"), c -> {
			if (c.shift) {
				kit.items.clear();
				plugin.kits().changed();
				refresh();
			}
		});
		set(49, button(Material.BOOK, "&6How to edit",
			"&eClick an item in your inventory", "&7adds a copy of it to the kit.", "",
			"&eShift-left-click a kit item", "&7removes it.", "",
			"&eRight-click a kit item", "&7takes 1 away from the stack.", "",
			"&eShift-right-click a kit item", "&7adds 1 to the stack.", "",
			"&7Changes are saved instantly.", "&7Stacks: &f" + kit.items.size() + "&7/" + CAPACITY));
		for (int i = 45; i < 54; i++) {
			if (i != 45 && i != 47 && i != 49) {
				set(i, button(Material.GRAY_STAINED_GLASS_PANE, "&8 "));
			}
		}
	}

	@Override
	protected void onInventoryClick(ItemStack clicked, Click click) {
		if (kit.items.size() >= CAPACITY) {
			Msg.send(player, "&cA kit can hold at most " + CAPACITY + " stacks.");
			return;
		}
		kit.items.add(clicked);
		plugin.kits().changed();
		click(1.6f);
		refresh();
	}
}
