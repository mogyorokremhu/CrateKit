package hu.cratekit.gui;

import hu.cratekit.Fmt;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Minimal server-side chest GUI. Every click is cancelled and routed to an {@link Action}, so nothing
 * can be taken out of (or put into) the menu and players need no client mod.
 */
public abstract class Gui {
	protected final ServerPlayer player;
	private final int rows;
	private final Container container;
	private final Action[] actions;
	private Handler handler;

	public record Click(int button, boolean shift) {
		public boolean left() {
			return button == 0;
		}

		public boolean right() {
			return button == 1;
		}
	}

	@FunctionalInterface
	public interface Action {
		void click(Click click);
	}

	protected Gui(ServerPlayer player, int rows) {
		this.player = player;
		this.rows = rows;
		this.container = new SimpleContainer(rows * 9);
		this.actions = new Action[rows * 9];
	}

	protected abstract Component title();

	/** Fill the inventory using {@link #set}. Called on open and on every {@link #refresh()}. */
	protected abstract void build();

	/** Called when the player clicks one of their own inventory slots. */
	protected void onInventoryClick(ItemStack clicked, Click click) {
	}

	protected void onClose() {
	}

	public final void open() {
		clear();
		build();
		player.openMenu(new SimpleMenuProvider((syncId, inv, p) -> {
			handler = new Handler(syncId, inv);
			return handler;
		}, title()));
	}

	/** Rebuilds the contents in place without reopening the window. */
	protected final void refresh() {
		clear();
		build();
		if (handler != null) {
			handler.broadcastChanges();
		}
	}

	protected final void set(int slot, ItemStack stack, Action action) {
		container.setItem(slot, stack);
		actions[slot] = action;
	}

	protected final void set(int slot, ItemStack stack) {
		set(slot, stack, null);
	}

	private void clear() {
		for (int i = 0; i < actions.length; i++) {
			container.setItem(i, ItemStack.EMPTY);
			actions[i] = null;
		}
	}

	protected final void fillEmpty() {
		ItemStack filler = button(Items.GRAY_STAINED_GLASS_PANE, "&8 ");
		for (int i = 0; i < actions.length; i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, filler.copy());
			}
		}
	}

	protected final void click(float pitch) {
		player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.6f, pitch);
	}

	// ------------------------------------------------------------------ item builders

	/** An item with a coloured (&-codes) name and lore lines. */
	public static ItemStack button(Item item, String name, String... lore) {
		return decorate(new ItemStack(item), name, List.of(lore));
	}

	public static ItemStack decorate(ItemStack stack, String name, List<String> lore) {
		stack.set(DataComponents.CUSTOM_NAME, Fmt.parse(name));
		List<Component> lines = new ArrayList<>();
		for (String line : lore) {
			lines.add(Fmt.parse(line));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines));
		// Hide the noisy default tooltip lines (attributes, enchantments...) on menu icons.
		LinkedHashSet<DataComponentType<?>> hidden = new LinkedHashSet<>();
		hidden.add(DataComponents.ATTRIBUTE_MODIFIERS);
		hidden.add(DataComponents.ENCHANTMENTS);
		hidden.add(DataComponents.STORED_ENCHANTMENTS);
		hidden.add(DataComponents.UNBREAKABLE);
		hidden.add(DataComponents.POTION_CONTENTS);
		hidden.add(DataComponents.TRIM);
		hidden.add(DataComponents.DYED_COLOR);
		hidden.add(DataComponents.JUKEBOX_PLAYABLE);
		stack.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, hidden));
		return stack;
	}

	// ------------------------------------------------------------------ menu

	private static MenuType<ChestMenu> typeFor(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	private final class Handler extends ChestMenu {
		Handler(int syncId, Inventory playerInventory) {
			super(typeFor(rows), syncId, playerInventory, container, rows);
		}

		@Override
		public void clicked(int slotIndex, int button, ClickType clickType, Player clicker) {
			boolean usable = clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE;
			if (usable) {
				Click click = new Click(button, clickType == ClickType.QUICK_MOVE);
				if (slotIndex >= 0 && slotIndex < actions.length) {
					Action action = actions[slotIndex];
					if (action != null) {
						action.click(click);
					}
				} else if (slotIndex >= actions.length && slotIndex < slots.size()) {
					onInventoryClick(slots.get(slotIndex).getItem(), click);
				}
			}
			// Always cancel: put everything back the way the server sees it.
			if (clicker.containerMenu == this) {
				sendAllDataToRemote();
			}
		}

		@Override
		public ItemStack quickMoveStack(Player player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (handler == this) {
				handler = null;
				onClose();
			}
		}
	}
}
