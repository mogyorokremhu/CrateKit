package hu.cratekit.paper;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal chest GUI. Every click is cancelled and routed to an {@link Action}, so nothing can be taken
 * out of (or put into) the menu.
 *
 * <p>Compatibility note: this deliberately never touches {@code InventoryView}, which changed from a
 * class to an interface in 1.21 and would crash on one side of that line.
 */
public abstract class Gui implements InventoryHolder {
	public static final class Click {
		public final boolean left, right, shift;

		Click(ClickType type) {
			this.left = type == ClickType.LEFT || type == ClickType.SHIFT_LEFT;
			this.right = type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;
			this.shift = type == ClickType.SHIFT_LEFT || type == ClickType.SHIFT_RIGHT;
		}
	}

	public interface Action {
		void click(Click click);
	}

	protected final CrateKitPlugin plugin;
	protected final Player player;
	private final int rows;
	private final ItemStack[] contents;
	private final Action[] actions;
	private Inventory inventory;

	protected Gui(CrateKitPlugin plugin, Player player, int rows) {
		this.plugin = plugin;
		this.player = player;
		this.rows = rows;
		this.contents = new ItemStack[rows * 9];
		this.actions = new Action[rows * 9];
	}

	protected abstract String title();

	/** Fill the menu with {@link #set}. Called on open and on every {@link #refresh()}. */
	protected abstract void build();

	protected void onInventoryClick(ItemStack clicked, Click click) {
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	/** Opens the menu (one tick later, so it is safe to call from inside another menu's click handler). */
	public final void open() {
		Bukkit.getScheduler().runTask(plugin, () -> {
			clear();
			build();
			String title = Msg.c(title());
			if (title.length() > 32) {
				title = title.substring(0, 32); // old servers reject longer titles
			}
			inventory = Bukkit.createInventory(this, rows * 9, title);
			inventory.setContents(contents);
			player.openInventory(inventory);
		});
	}

	protected final void refresh() {
		clear();
		build();
		if (inventory != null) {
			inventory.setContents(contents);
		}
	}

	protected final void set(int slot, ItemStack stack, Action action) {
		contents[slot] = stack;
		actions[slot] = action;
	}

	protected final void set(int slot, ItemStack stack) {
		set(slot, stack, null);
	}

	private void clear() {
		Arrays.fill(contents, null);
		Arrays.fill(actions, null);
	}

	protected final void fillEmpty() {
		for (int i = 0; i < contents.length; i++) {
			if (contents[i] == null) {
				contents[i] = button(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
			}
		}
	}

	protected final void click(float pitch) {
		Msg.sound(player, Sound.UI_BUTTON_CLICK, 0.6f, pitch);
	}

	// ------------------------------------------------------------------ item builders

	public static ItemStack button(Material material, String name, String... lore) {
		return decorate(new ItemStack(material), name, Arrays.asList(lore));
	}

	public static ItemStack decorate(ItemStack stack, String name, List<String> lore) {
		ItemMeta meta = stack.getItemMeta();
		if (meta == null) {
			return stack;
		}
		meta.setDisplayName(Msg.c(name));
		List<String> lines = new ArrayList<>();
		for (String line : lore) {
			lines.add(Msg.c(line));
		}
		meta.setLore(lines);
		meta.addItemFlags(ItemFlag.values()); // hide attribute/enchant noise on menu icons
		stack.setItemMeta(meta);
		return stack;
	}

	// ------------------------------------------------------------------ events

	public static final class Listener_ implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onClick(InventoryClickEvent event) {
			Inventory top = event.getInventory();
			if (!(top.getHolder() instanceof Gui)) {
				return;
			}
			Gui gui = (Gui) top.getHolder();
			event.setCancelled(true);
			Inventory clicked = event.getClickedInventory();
			if (clicked == null) {
				return;
			}
			ClickType type = event.getClick();
			if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
				return;
			}
			Click click = new Click(type);
			if (clicked.equals(top)) {
				int slot = event.getRawSlot();
				if (slot >= 0 && slot < gui.actions.length && gui.actions[slot] != null) {
					gui.actions[slot].click(click);
				}
			} else {
				ItemStack item = event.getCurrentItem();
				if (item != null && item.getType() != Material.AIR) {
					gui.onInventoryClick(item.clone(), click);
				}
			}
		}

		@EventHandler(priority = EventPriority.HIGHEST)
		public void onDrag(InventoryDragEvent event) {
			if (event.getInventory().getHolder() instanceof Gui) {
				event.setCancelled(true);
			}
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			// Nothing to clean up: menus hold no real items.
		}
	}
}
