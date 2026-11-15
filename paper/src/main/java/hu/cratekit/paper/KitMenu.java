package hu.cratekit.paper;

import hu.cratekit.common.Durations;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Main menu: every kit as an icon. Admins get a "manage" toggle and a "create kit" button. */
public final class KitMenu extends Gui {
	private static final int PAGE_SIZE = 45;

	private final boolean admin;
	private boolean manage;
	private int page;

	public KitMenu(CrateKitPlugin plugin, Player player, boolean manage) {
		super(plugin, player, 6);
		this.admin = KitManager.isAdmin(player);
		this.manage = manage && admin;
	}

	@Override
	protected String title() {
		return manage ? "&6&lKits &8- &cmanage" : "&6&lKits";
	}

	@Override
	protected void build() {
		List<Kit> kits = plugin.kits().all();
		int pages = Math.max(1, (kits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);

		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= kits.size()) {
				break;
			}
			Kit kit = kits.get(index);
			set(i, icon(kit), click -> {
				if (manage && click.left) {
					click(1.2f);
					new KitEditor(plugin, player, kit).open();
				} else if (click.right) {
					click(1.2f);
					new KitPreview(plugin, player, kit, () -> new KitMenu(plugin, player, manage).open()).open();
				} else if (!manage) {
					KitCommand.claimAndReport(plugin, player, kit);
					refresh();
				}
			});
		}
		if (kits.isEmpty()) {
			set(22, button(Material.BARRIER, "&cNo kits yet",
				admin ? "&7Use &fCreate kit &7below or &f/kit create <name>" : "&7Ask an admin to create some."));
		}
		if (page > 0) {
			set(45, button(Material.ARROW, "&ePrevious page", "&7Page " + page + "/" + pages), c -> {
				page--;
				click(1f);
				refresh();
			});
		}
		if (page < pages - 1) {
			set(53, button(Material.ARROW, "&eNext page", "&7Page " + (page + 2) + "/" + pages), c -> {
				page++;
				click(1f);
				refresh();
			});
		}
		set(49, button(Material.BARRIER, "&cClose"), c -> player.closeInventory());

		if (admin) {
			set(47, button(Material.EMERALD, "&aCreate kit", "&7Saves the items in your inventory", "&7as a new kit. You will be asked", "&7to type a name in chat."), c ->
				plugin.prompt().ask(player, "Type a name for the new kit &8(letters, numbers, _ and -)&7:", input -> {
					if (ChatPrompt.isCancel(input)) {
						new KitMenu(plugin, player, true).open();
						return;
					}
					Kit kit = plugin.kits().createFromInventory(player, input);
					if (kit == null) {
						Msg.send(player, "&cThat name is invalid or already taken.");
						new KitMenu(plugin, player, true).open();
						return;
					}
					Msg.send(player, "Created kit &e" + kit.name + "&7. Customise it below.");
					new KitEditor(plugin, player, kit).open();
				}));
			set(51, button(manage ? Material.LIME_DYE : Material.GRAY_DYE, manage ? "&aManage mode: ON" : "&7Manage mode: OFF",
				"&7When on, left-click a kit to edit it.", "&eClick to toggle"), c -> {
				click(1f);
				new KitMenu(plugin, player, !manage).open();
			});
		}
		fillEmpty();
	}

	private org.bukkit.inventory.ItemStack icon(Kit kit) {
		List<String> lore = new ArrayList<>();
		lore.add("&7Stacks: &f" + kit.items.size());
		lore.add("&7Cooldown: &f" + Durations.describe(kit.cooldownSeconds));
		lore.add("&7Command: &f" + (kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias));
		lore.add("");
		lore.add(status(plugin, kit, player));
		lore.add("");
		if (manage) {
			lore.addAll(Arrays.asList("&6Left-click: &fedit this kit", "&6Right-click: &fpreview"));
		} else {
			lore.addAll(Arrays.asList("&eLeft-click: &fclaim", "&eRight-click: &fpreview"));
		}
		return decorate(kit.menuIcon(), "&e&l" + kit.displayName, lore);
	}

	static String status(CrateKitPlugin plugin, Kit kit, Player player) {
		long left = plugin.kits().remaining(kit, player.getUniqueId());
		if (kit.items.isEmpty()) return "&cThis kit is empty";
		if (left == Durations.ONCE) return "&cAlready claimed (one-time kit)";
		if (left > 0) return "&cAvailable in &f" + Durations.format(left);
		return "&aReady to claim!";
	}
}
