package hu.cratekit.paper;

import hu.cratekit.common.Durations;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Pick a cooldown from presets or type a custom one. */
public final class CooldownMenu extends Gui {
	private static final long[] PRESETS = {0, 5 * 60, 30 * 60, 3600, 6 * 3600, 12 * 3600, 86400, 3 * 86400, 7 * 86400, 30 * 86400};

	private final Kit kit;

	public CooldownMenu(CrateKitPlugin plugin, Player player, Kit kit) {
		super(plugin, player, 3);
		this.kit = kit;
	}

	@Override
	protected String title() {
		return "&6Cooldown: &r" + kit.displayName;
	}

	@Override
	protected void build() {
		for (int i = 0; i < PRESETS.length; i++) {
			final long seconds = PRESETS[i];
			boolean current = kit.cooldownSeconds == seconds;
			set(i < 5 ? 10 + i : 19 + (i - 5), button(current ? Material.LIME_CONCRETE : Material.CLOCK,
				(current ? "&a" : "&e") + Durations.describe(seconds), current ? "&aCurrently selected" : "&7Click to select"), c -> choose(seconds));
		}
		set(15, button(kit.cooldownSeconds == Durations.ONCE ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "&cOne-time only",
			"&7Each player can claim this kit once.", kit.cooldownSeconds == Durations.ONCE ? "&aCurrently selected" : "&7Click to select"),
			c -> choose(Durations.ONCE));
		set(16, button(Material.WRITABLE_BOOK, "&bCustom time", "&7Examples: &f45m&7, &f2h 30m&7, &f1d 12h", "", "&eClick, then type in chat"), c ->
			plugin.prompt().ask(player, "Type the cooldown &8(e.g. &f30m&8, &f2h 30m&8, &f1d&8, &fnone&8, &fonce&8)&7:", input -> {
				if (!ChatPrompt.isCancel(input)) {
					try {
						kit.cooldownSeconds = Durations.parse(input);
						plugin.kits().changed();
						Msg.send(player, "Cooldown set to &f" + Durations.describe(kit.cooldownSeconds) + "&7.");
					} catch (IllegalArgumentException e) {
						Msg.send(player, "&c" + e.getMessage());
					}
				}
				new CooldownMenu(plugin, player, kit).open();
			}));
		set(18, button(Material.ARROW, "&eBack"), c -> {
			click(1f);
			new KitEditor(plugin, player, kit).open();
		});
		fillEmpty();
	}

	private void choose(long seconds) {
		kit.cooldownSeconds = seconds;
		plugin.kits().changed();
		click(1.3f);
		refresh();
	}
}
