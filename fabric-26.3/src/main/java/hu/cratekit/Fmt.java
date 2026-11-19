package hu.cratekit;

import hu.cratekit.common.Durations;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/** Text helpers. */
public final class Fmt {
	public static final String PREFIX = "&8[&6CrateKit&8] &7";

	private Fmt() {
	}

	/** Converts a string with &-colour codes into styled text (italic is off so it suits item names/lore). */
	public static MutableComponent parse(String input) {
		MutableComponent root = Component.empty().setStyle(Style.EMPTY.withItalic(false));
		StringBuilder buf = new StringBuilder();
		Style style = Style.EMPTY;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '&' && i + 1 < input.length()) {
				ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(input.charAt(i + 1)));
				if (f != null) {
					if (!buf.isEmpty()) {
						root.append(Component.literal(buf.toString()).setStyle(style));
						buf.setLength(0);
					}
					style = f == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(f);
					i++;
					continue;
				}
			}
			buf.append(c);
		}
		if (!buf.isEmpty()) {
			root.append(Component.literal(buf.toString()).setStyle(style));
		}
		return root;
	}

	/** A chat message with the CrateKit prefix. */
	public static Component msg(String message) {
		return parse(PREFIX + message);
	}

	public static void send(ServerPlayer player, String message) {
		player.sendSystemMessage(msg(message));
	}

	public static long parseDuration(String raw) {
		return Durations.parse(raw);
	}

	public static String duration(long seconds) {
		return Durations.format(seconds);
	}

	public static String cooldown(long cooldownSeconds) {
		return Durations.describe(cooldownSeconds);
	}
}
