package hu.cratekit.gui;

import hu.cratekit.ChatPrompt;
import hu.cratekit.CrateKit;
import hu.cratekit.Fmt;
import hu.cratekit.Kit;
import hu.cratekit.KitCommands;
import hu.cratekit.KitManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Main menu: every kit as an icon. Admins get a "manage" toggle and a "create kit" button. */
public final class KitMenu extends Gui {
	private static final int PAGE_SIZE = 45;

	private final boolean admin;
	private boolean manage;
	private int page;

	public KitMenu(ServerPlayer player, boolean manage) {
		super(player, 6);
		this.admin = KitManager.isAdmin(player);
		this.manage = manage && admin;
	}

	@Override
	protected Component title() {
		return Fmt.parse(manage ? "&6&lKits &8- &cmanage mode" : "&6&lKits");
	}

	@Override
	protected void build() {
		List<Kit> kits = CrateKit.kits().all();
		int pages = Math.max(1, (kits.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.min(page, pages - 1);

		for (int i = 0; i < PAGE_SIZE; i++) {
			int index = page * PAGE_SIZE + i;
			if (index >= kits.size()) {
				break;
			}
			Kit kit = kits.get(index);
			set(i, icon(kit), click -> {
				if (manage && click.left()) {
					click(1.2f);
					new KitEditor(player, kit).open();
				} else if (click.right()) {
					click(1.2f);
					new KitPreview(player, kit, () -> new KitMenu(player, manage).open()).open();
				} else if (!manage) {
					KitCommands.claimAndReport(player, kit);
					refresh();
				}
			});
		}
		if (kits.isEmpty()) {
			set(22, button(Items.BARRIER, "&cNo kits yet", admin ? "&7Use &fCreate kit &7below or &f/kit create <name>" : "&7Ask an admin to create some."));
		}

		if (page > 0) {
			set(45, button(Items.ARROW, "&ePrevious page", "&7Page " + page + "/" + pages), c -> {
				page--;
				click(1f);
				refresh();
			});
		}
		if (page < pages - 1) {
			set(53, button(Items.ARROW, "&eNext page", "&7Page " + (page + 2) + "/" + pages), c -> {
				page++;
				click(1f);
				refresh();
			});
		}
		set(49, button(Items.BARRIER, "&cClose"), c -> player.closeContainer());

		if (admin) {
			set(47, button(Items.EMERALD, "&aCreate kit", "&7Saves the items in your inventory", "&7as a new kit. You will be asked", "&7to type a name in chat."), c -> {
				ChatPrompt.ask(player, "Type a name for the new kit &8(letters, numbers, _ and -)&7:", input -> {
					if (ChatPrompt.isCancel(input)) {
						new KitMenu(player, true).open();
						return;
					}
					Kit kit = CrateKit.kits().createFromInventory(player, input);
					if (kit == null) {
						Fmt.send(player, "&cThat name is invalid or already taken.");
						new KitMenu(player, true).open();
						return;
					}
					Fmt.send(player, "Created kit &e" + kit.name + "&7. Customise it below.");
					new KitEditor(player, kit).open();
				});
			});
			set(51, button(manage ? Items.LIME_DYE : Items.GRAY_DYE, manage ? "&aManage mode: ON" : "&7Manage mode: OFF",
				"&7When on, left-click a kit to edit it.", "&eClick to toggle"), c -> {
				manage = !manage;
				click(1f);
				// The title changes with the mode, so reopen.
				new KitMenu(player, manage).open();
			});
		}
		fillEmpty();
	}

	private ItemStack icon(Kit kit) {
		List<String> lore = new ArrayList<>();
		lore.add("&7Stacks: &f" + kit.items.size());
		lore.add("&7Cooldown: &f" + Fmt.cooldown(kit.cooldownSeconds));
		lore.add("&7Command: &f" + (kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias));
		lore.add("");
		lore.add(status(kit));
		lore.add("");
		if (manage) {
			lore.add("&6Left-click: &fedit this kit");
			lore.add("&6Right-click: &fpreview");
		} else {
			lore.add("&eLeft-click: &fclaim");
			lore.add("&eRight-click: &fpreview");
		}
		return decorate(kit.menuIcon(), "&e&l" + kit.displayName, lore);
	}

	static String status(Kit kit, ServerPlayer player) {
		long left = CrateKit.kits().remaining(kit, player.getUUID());
		if (kit.items.isEmpty()) return "&cThis kit is empty";
		if (left == Kit.ONCE) return "&cAlready claimed (one-time kit)";
		if (left > 0) return "&cAvailable in &f" + Fmt.duration(left);
		return "&aReady to claim!";
	}

	private String status(Kit kit) {
		return status(kit, player);
	}
}
