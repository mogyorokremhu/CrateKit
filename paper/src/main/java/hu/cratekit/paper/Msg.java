package hu.cratekit.paper;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Chat colour helpers. */
public final class Msg {
	public static final String PREFIX = "&8[&6CrateKit&8] &7";

	private Msg() {
	}

	/** Translates &-codes. */
	public static String c(String s) {
		return ChatColor.translateAlternateColorCodes('&', s);
	}

	public static void send(CommandSender to, String message) {
		to.sendMessage(c(PREFIX + message));
	}

	public static void sound(Player player, Sound sound, float volume, float pitch) {
		try {
			player.playSound(player.getLocation(), sound, volume, pitch);
		} catch (Throwable ignored) {
			// Sounds are cosmetic; never let them break a command.
		}
	}
}
