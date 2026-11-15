package hu.cratekit.gui;

import hu.cratekit.Fmt;
import hu.cratekit.Kit;
import hu.cratekit.KitCommands;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Read-only view of a kit's contents with a claim button. */
public final class KitPreview extends Gui {
	private final Kit kit;
	private final Runnable back;

	public KitPreview(ServerPlayerEntity player, Kit kit, Runnable back) {
		super(player, 6);
		this.kit = kit;
		this.back = back;
	}

	@Override
	protected Text title() {
		return Fmt.parse("&6Preview: &r" + kit.displayName);
	}

	@Override
	protected void build() {
		for (int i = 0; i < Math.min(45, kit.items.size()); i++) {
			set(i, kit.items.get(i).copy());
		}
		set(45, button(back == null ? Items.BARRIER : Items.ARROW, back == null ? "&cClose" : "&eBack"), c -> {
			click(1f);
			if (back == null) {
				player.closeHandledScreen();
			} else {
				back.run();
			}
		});
		set(49, decorate(new ItemStack(Items.CHEST), "&a&lClaim kit", java.util.List.of(
			KitMenu.status(kit, player), "", "&eClick to claim")), c -> {
			KitCommands.claimAndReport(player, kit);
			refresh();
		});
		fillEmpty();
	}
}
