package hu.cratekit.gui;

import hu.cratekit.Fmt;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Minimal server-side chest GUI. Every click is cancelled and routed to an {@link Action}, so nothing
 * can be taken out of (or put into) the menu and players need no client mod.
 */
public abstract class Gui {
	protected final ServerPlayerEntity player;
	private final int rows;
	private final SimpleInventory inventory;
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

	protected Gui(ServerPlayerEntity player, int rows) {
		this.player = player;
		this.rows = rows;
		this.inventory = new SimpleInventory(rows * 9);
		this.actions = new Action[rows * 9];
	}

	protected abstract Text title();

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
		player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, p) -> {
			handler = new Handler(syncId, inv);
			return handler;
		}, title()));
	}

	/** Rebuilds the contents in place without reopening the window. */
	protected final void refresh() {
		clear();
		build();
		if (handler != null) {
			handler.sendContentUpdates();
		}
	}

	protected final void set(int slot, ItemStack stack, Action action) {
		inventory.setStack(slot, stack);
		actions[slot] = action;
	}

	protected final void set(int slot, ItemStack stack) {
		set(slot, stack, null);
	}

	private void clear() {
		for (int i = 0; i < actions.length; i++) {
			inventory.setStack(i, ItemStack.EMPTY);
			actions[i] = null;
		}
	}

	protected final void fillEmpty() {
		ItemStack filler = button(Items.GRAY_STAINED_GLASS_PANE, "&8 ");
		for (int i = 0; i < actions.length; i++) {
			if (inventory.getStack(i).isEmpty()) {
				inventory.setStack(i, filler.copy());
			}
		}
	}

	protected final void click(float pitch) {
		player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, pitch);
	}

	// ------------------------------------------------------------------ item builders

	/** An item with a coloured (&-codes) name and lore lines. */
	public static ItemStack button(Item item, String name, String... lore) {
		return decorate(new ItemStack(item), name, List.of(lore));
	}

	public static ItemStack decorate(ItemStack stack, String name, List<String> lore) {
		stack.set(DataComponentTypes.CUSTOM_NAME, Fmt.parse(name));
		List<Text> lines = new ArrayList<>();
		for (String line : lore) {
			lines.add(Fmt.parse(line));
		}
		stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
		// Hide the noisy default tooltip lines (attributes, enchantments...) on menu icons.
		LinkedHashSet<ComponentType<?>> hidden = new LinkedHashSet<>();
		hidden.add(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		hidden.add(DataComponentTypes.ENCHANTMENTS);
		hidden.add(DataComponentTypes.STORED_ENCHANTMENTS);
		hidden.add(DataComponentTypes.UNBREAKABLE);
		hidden.add(DataComponentTypes.POTION_CONTENTS);
		hidden.add(DataComponentTypes.TRIM);
		hidden.add(DataComponentTypes.DYED_COLOR);
		hidden.add(DataComponentTypes.JUKEBOX_PLAYABLE);
		stack.set(DataComponentTypes.TOOLTIP_DISPLAY, new TooltipDisplayComponent(false, hidden));
		return stack;
	}

	// ------------------------------------------------------------------ screen handler

	private static ScreenHandlerType<GenericContainerScreenHandler> typeFor(int rows) {
		return switch (rows) {
			case 1 -> ScreenHandlerType.GENERIC_9X1;
			case 2 -> ScreenHandlerType.GENERIC_9X2;
			case 3 -> ScreenHandlerType.GENERIC_9X3;
			case 4 -> ScreenHandlerType.GENERIC_9X4;
			case 5 -> ScreenHandlerType.GENERIC_9X5;
			default -> ScreenHandlerType.GENERIC_9X6;
		};
	}

	private final class Handler extends GenericContainerScreenHandler {
		Handler(int syncId, PlayerInventory playerInventory) {
			super(typeFor(rows), syncId, playerInventory, inventory, rows);
		}

		@Override
		public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clicker) {
			boolean usable = actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE;
			if (usable) {
				Click click = new Click(button, actionType == SlotActionType.QUICK_MOVE);
				if (slotIndex >= 0 && slotIndex < actions.length) {
					Action action = actions[slotIndex];
					if (action != null) {
						action.click(click);
					}
				} else if (slotIndex >= actions.length && slotIndex < slots.size()) {
					onInventoryClick(slots.get(slotIndex).getStack(), click);
				}
			}
			// Always cancel: put everything back the way the server sees it.
			if (clicker.currentScreenHandler == this) {
				syncState();
			}
		}

		@Override
		public ItemStack quickMove(PlayerEntity player, int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean canUse(PlayerEntity player) {
			return true;
		}

		@Override
		public void onClosed(PlayerEntity player) {
			super.onClosed(player);
			if (handler == this) {
				handler = null;
				onClose();
			}
		}
	}

}
