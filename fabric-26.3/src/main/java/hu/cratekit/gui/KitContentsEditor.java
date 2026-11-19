package hu.cratekit.gui;

import hu.cratekit.CrateKit;
import hu.cratekit.Fmt;
import hu.cratekit.Kit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/**
 * Edits the items of a kit. Nothing is moved between your inventory and the kit: clicking one of your
 * own items adds a <em>copy</em> to the kit, so you can never lose or duplicate real items here.
 * Every change is saved immediately.
 */
public final class KitContentsEditor extends Gui {
	private static final int CAPACITY = 45;

	private final Kit kit;

	public KitContentsEditor(ServerPlayer player, Kit kit) {
		super(player, 6);
		this.kit = kit;
	}

	@Override
	protected Component title() {
		return Fmt.parse("&6Contents: &r" + kit.displayName);
	}

	@Override
	protected void build() {
		for (int i = 0; i < kit.items.size() && i < CAPACITY; i++) {
			int index = i;
			set(i, kit.items.get(i).copy(), c -> {
				ItemStack stack = kit.items.get(index);
				if (c.shift() && c.left()) {
					kit.items.remove(index);
				} else if (c.right() && !c.shift()) {
					stack.shrink(1);
					if (stack.isEmpty()) {
						kit.items.remove(index);
					}
				} else if (c.right() && c.shift()) {
					if (stack.getCount() < stack.getMaxStackSize()) {
						stack.grow(1);
					}
				} else {
					return;
				}
				CrateKit.kits().changed();
				click(1.4f);
				refresh();
			});
		}
		set(45, button(Items.ARROW, "&eBack"), c -> {
			click(1f);
			new KitEditor(player, kit).open();
		});
		set(47, button(Items.LAVA_BUCKET, "&cClear all items", "&7Removes every item from this kit.", "", "&eShift-click to confirm"), c -> {
			if (c.shift()) {
				kit.items.clear();
				CrateKit.kits().changed();
				refresh();
			}
		});
		set(49, button(Items.BOOK, "&6How to edit",
			"&eClick an item in your inventory", "&7adds a copy of it to the kit.", "",
			"&eShift-left-click a kit item", "&7removes it.", "",
			"&eRight-click a kit item", "&7takes 1 away from the stack.", "",
			"&eShift-right-click a kit item", "&7adds 1 to the stack.", "",
			"&7Changes are saved instantly.", "&7Stacks: &f" + kit.items.size() + "&7/" + CAPACITY));
		fillEmptyBottomOnly();
	}

	private void fillEmptyBottomOnly() {
		for (int i = 46; i < 54; i++) {
			if (i != 47 && i != 49) {
				set(i, button(Items.STAINED_GLASS_PANE.gray(), "&8 "));
			}
		}
		set(46, button(Items.STAINED_GLASS_PANE.gray(), "&8 "));
		set(48, button(Items.STAINED_GLASS_PANE.gray(), "&8 "));
	}

	@Override
	protected void onInventoryClick(ItemStack clicked, Click click) {
		if (clicked.isEmpty()) {
			return;
		}
		if (kit.items.size() >= CAPACITY) {
			Fmt.send(player, "&cA kit can hold at most " + CAPACITY + " stacks.");
			return;
		}
		kit.items.add(clicked.copy());
		CrateKit.kits().changed();
		click(1.6f);
		refresh();
	}
}
