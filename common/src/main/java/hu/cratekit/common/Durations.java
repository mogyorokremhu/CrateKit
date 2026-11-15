package hu.cratekit.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cooldown parsing and formatting shared by every CrateKit platform. Java 8 compatible. */
public final class Durations {
	/** Cooldown value meaning "each player can claim the kit only once". */
	public static final long ONCE = -1;
	public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,24}");

	private static final Pattern PART = Pattern.compile("(\\d+)\\s*([smhdw])");

	private Durations() {
	}

	/**
	 * Parses "30m", "1d 12h", "2h30m", "90" (seconds), "none"/"0" (no cooldown) or "once".
	 *
	 * @return seconds, 0 for none, or {@link #ONCE}
	 * @throws IllegalArgumentException if it cannot be understood
	 */
	public static long parse(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			throw new IllegalArgumentException("Empty duration");
		}
		if (s.equals("none") || s.equals("off") || s.equals("0")) {
			return 0;
		}
		if (s.equals("once") || s.equals("one-time") || s.equals("onetime")) {
			return ONCE;
		}
		if (s.matches("\\d+")) {
			return Long.parseLong(s);
		}
		Matcher m = PART.matcher(s);
		long total = 0;
		int end = 0;
		while (m.find()) {
			if (!s.substring(end, m.start()).trim().isEmpty()) {
				throw bad(raw);
			}
			long n = Long.parseLong(m.group(1));
			char unit = m.group(2).charAt(0);
			long mult = unit == 's' ? 1L : unit == 'm' ? 60L : unit == 'h' ? 3600L : unit == 'd' ? 86400L : 604800L;
			total += n * mult;
			end = m.end();
		}
		if (end == 0 || !s.substring(end).trim().isEmpty()) {
			throw bad(raw);
		}
		return total;
	}

	private static IllegalArgumentException bad(String raw) {
		return new IllegalArgumentException("Bad duration: " + raw + " (try 30m, 2h, 1d12h, none or once)");
	}

	/** Formats seconds as "1d 2h 3m 4s". */
	public static String format(long seconds) {
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
	public static String describe(long cooldownSeconds) {
		if (cooldownSeconds == ONCE) return "One-time only";
		if (cooldownSeconds <= 0) return "None";
		return format(cooldownSeconds);
	}

	/**
	 * Seconds left before the kit can be claimed again.
	 *
	 * @param lastClaimMillis when the player last claimed it, or null if never
	 * @return 0 if ready, {@link #ONCE} if it can never be claimed again, otherwise seconds remaining
	 */
	public static long remaining(long cooldownSeconds, Long lastClaimMillis, long nowMillis) {
		if (lastClaimMillis == null || cooldownSeconds == 0) {
			return 0;
		}
		if (cooldownSeconds == ONCE) {
			return ONCE;
		}
		return Math.max(0, cooldownSeconds - (nowMillis - lastClaimMillis) / 1000);
	}
}
