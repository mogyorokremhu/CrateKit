package hu.cratekit;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text and duration helpers. */
public final class Fmt {
	public static final String PREFIX = "&8[&6CrateKit&8] &7";
	private static final Pattern PART = Pattern.compile("(\\d+)\\s*([smhdw])");

	private Fmt() {
	}

	/** Converts a string with &-colour codes into styled text (italic is off so it suits item names/lore). */
	public static MutableText parse(String input) {
		MutableText root = Text.empty().setStyle(Style.EMPTY.withItalic(false));
		StringBuilder buf = new StringBuilder();
		Style style = Style.EMPTY;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '&' && i + 1 < input.length()) {
				Formatting f = Formatting.byCode(Character.toLowerCase(input.charAt(i + 1)));
				if (f != null) {
					if (!buf.isEmpty()) {
						root.append(Text.literal(buf.toString()).setStyle(style));
						buf.setLength(0);
					}
					style = f == Formatting.RESET ? Style.EMPTY : style.withFormatting(f);
					i++;
					continue;
				}
			}
			buf.append(c);
		}
		if (!buf.isEmpty()) {
			root.append(Text.literal(buf.toString()).setStyle(style));
		}
		return root;
	}

	/** A chat message with the CrateKit prefix. */
	public static Text msg(String message) {
		return parse(PREFIX + message);
	}

	public static void send(ServerPlayerEntity player, String message) {
		player.sendMessage(msg(message));
	}

	/**
	 * Parses "30m", "1d 12h", "2h30m", "90" (seconds), "none"/"0" (no cooldown) or "once".
	 * Returns seconds, 0 for none, or {@link Kit#ONCE}.
	 */
	public static long parseDuration(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			throw new IllegalArgumentException("Empty duration");
		}
		if (s.equals("none") || s.equals("off") || s.equals("0")) {
			return 0;
		}
		if (s.equals("once") || s.equals("one-time") || s.equals("onetime")) {
			return Kit.ONCE;
		}
		if (s.matches("\\d+")) {
			return Long.parseLong(s);
		}
		Matcher m = PART.matcher(s);
		long total = 0;
		int end = 0;
		String rest = s;
		while (m.find()) {
			if (!s.substring(end, m.start()).isBlank()) {
				throw new IllegalArgumentException("Bad duration: " + raw);
			}
			long n = Long.parseLong(m.group(1));
			total += n * switch (m.group(2)) {
				case "s" -> 1L;
				case "m" -> 60L;
				case "h" -> 3600L;
				case "d" -> 86400L;
				default -> 604800L;
			};
			end = m.end();
		}
		if (end == 0 || !rest.substring(end).isBlank()) {
			throw new IllegalArgumentException("Bad duration: " + raw + " (try 30m, 2h, 1d12h, none or once)");
		}
		return total;
	}

	/** Formats seconds as "1d 2h 3m 4s". */
	public static String duration(long seconds) {
		if (seconds <= 0) {
			return "0s";
		}
		long d = seconds / 86400, h = seconds % 86400 / 3600, m = seconds % 3600 / 60, s = seconds % 60;
		StringBuilder sb = new StringBuilder();
		if (d > 0) sb.append(d).append("d ");
		if (h > 0) sb.append(h).append("h ");
		if (m > 0) sb.append(m).append("m ");
		if (s > 0 && d == 0) sb.append(s).append("s ");
		return sb.toString().trim();
	}

	/** Human readable description of a cooldown setting. */
	public static String cooldown(long cooldownSeconds) {
		if (cooldownSeconds == Kit.ONCE) return "One-time only";
		if (cooldownSeconds <= 0) return "None";
		return duration(cooldownSeconds);
	}
}
