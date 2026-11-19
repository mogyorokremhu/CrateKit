package hu.cratekit;

import hu.cratekit.common.Durations;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** A saved kit: a list of item stacks plus claim settings. */
public final class Kit {
	/** Cooldown value meaning "each player can claim this kit only once". */
	public static final long ONCE = Durations.ONCE;

	public final String name;
	public String displayName;
	public List<ItemStack> items = new ArrayList<>();
	/** Extra chat command (without the slash) that claims this kit, or null. */
	public String alias;
	/** Seconds between claims. 0 = no cooldown, {@link #ONCE} = one claim per player. */
	public long cooldownSeconds;
	/** Optional custom menu icon. */
	public ItemStack icon = ItemStack.EMPTY;

	public Kit(String name) {
		this.name = name;
		this.displayName = name;
	}

	public ItemStack menuIcon() {
		if (!icon.isEmpty()) {
			return icon.copyWithCount(1);
		}
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return stack.copyWithCount(1);
			}
		}
		return new ItemStack(Items.CHEST);
	}
}
