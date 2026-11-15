package hu.cratekit.paper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A saved kit. */
public final class Kit {
	public final String name;
	public String displayName;
	public List<ItemStack> items = new ArrayList<>();
	/** Extra chat command (no slash) that claims this kit, or null. */
	public String alias;
	/** 0 = no cooldown, {@link hu.cratekit.common.Durations#ONCE} = once per player, otherwise seconds. */
	public long cooldownSeconds;
	public ItemStack icon;

	public Kit(String name) {
		this.name = name;
		this.displayName = name;
	}

	public ItemStack menuIcon() {
		if (icon != null) {
			ItemStack copy = icon.clone();
			copy.setAmount(1);
			return copy;
		}
		for (ItemStack stack : items) {
			if (stack != null && stack.getType() != Material.AIR) {
				ItemStack copy = stack.clone();
				copy.setAmount(1);
				return copy;
			}
		}
		return new ItemStack(Material.CHEST);
	}
}
