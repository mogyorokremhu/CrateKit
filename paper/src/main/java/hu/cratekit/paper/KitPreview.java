package hu.cratekit.paper;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;

/** Read-only view of a kit's contents with a claim button. */
public final class KitPreview extends Gui {
	private final Kit kit;
	private final Runnable back;

	public KitPreview(CrateKitPlugin plugin, Player player, Kit kit, Runnable back) {
		super(plugin, player, 6);
		this.kit = kit;
		this.back = back;
	}

	@Override
	protected String title() {
		return "&6Preview: &r" + kit.displayName;
	}

	@Override
	protected void build() {
		for (int i = 0; i < Math.min(45, kit.items.size()); i++) {
			set(i, kit.items.get(i).clone());
		}
		set(45, button(back == null ? Material.BARRIER : Material.ARROW, back == null ? "&cClose" : "&eBack"), c -> {
			click(1f);
			if (back == null) {
				player.closeInventory();
			} else {
				back.run();
			}
		});
		set(49, decorate(new ItemStack(Material.CHEST), "&a&lClaim kit",
			Arrays.asList(KitMenu.status(plugin, kit, player), "", "&eClick to claim")), c -> {
			KitCommand.claimAndReport(plugin, player, kit);
			refresh();
		});
		fillEmpty();
	}
}
