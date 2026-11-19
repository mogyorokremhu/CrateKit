package hu.cratekit.gui;

import hu.cratekit.ChatPrompt;
import hu.cratekit.CrateKit;
import hu.cratekit.Fmt;
import hu.cratekit.Kit;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Pick a cooldown from presets or type a custom one. */
public final class CooldownMenu extends Gui {
	private static final long[] PRESETS = {0, 5 * 60, 30 * 60, 3600, 6 * 3600, 12 * 3600, 86400, 3 * 86400, 7 * 86400, 30 * 86400};

	private final Kit kit;

	public CooldownMenu(ServerPlayer player, Kit kit) {
		super(player, 3);
		this.kit = kit;
	}

	@Override
	protected Component title() {
		return Fmt.parse("&6Cooldown: &r" + kit.displayName);
	}

	@Override
	protected void build() {
		for (int i = 0; i < PRESETS.length; i++) {
			long seconds = PRESETS[i];
			boolean current = kit.cooldownSeconds == seconds;
			set(i < 5 ? 10 + i : 19 + (i - 5), button(current ? Items.CONCRETE.lime() : Items.CLOCK,
				(current ? "&a" : "&e") + Fmt.cooldown(seconds), current ? "&aCurrently selected" : "&7Click to select"), c -> choose(seconds));
		}
		set(15, button(kit.cooldownSeconds == Kit.ONCE ? Items.CONCRETE.lime() : Items.CONCRETE.red(), "&cOne-time only",
			"&7Each player can claim this kit once.", kit.cooldownSeconds == Kit.ONCE ? "&aCurrently selected" : "&7Click to select"), c -> choose(Kit.ONCE));
		set(16, button(Items.WRITABLE_BOOK, "&bCustom time", "&7Examples: &f45m&7, &f2h 30m&7, &f1d 12h", "", "&eClick, then type in chat"), c ->
			ChatPrompt.ask(player, "Type the cooldown &8(e.g. &f30m&8, &f2h 30m&8, &f1d&8, &fnone&8, &fonce&8)&7:", input -> {
				if (!ChatPrompt.isCancel(input)) {
					try {
						kit.cooldownSeconds = Fmt.parseDuration(input);
						CrateKit.kits().changed();
						Fmt.send(player, "Cooldown set to &f" + Fmt.cooldown(kit.cooldownSeconds) + "&7.");
					} catch (IllegalArgumentException e) {
						Fmt.send(player, "&c" + e.getMessage());
					}
				}
				new CooldownMenu(player, kit).open();
			}));
		set(18, button(Items.ARROW, "&eBack"), c -> {
			click(1f);
			new KitEditor(player, kit).open();
		});
		fillEmpty();
	}

	private void choose(long seconds) {
		kit.cooldownSeconds = seconds;
		CrateKit.kits().changed();
		click(1.3f);
		refresh();
	}
}
