package hu.cratekit.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lets a GUI ask the player to type a value in chat (chest GUIs cannot take text input). */
public final class ChatPrompt implements Listener {
	private static final long TIMEOUT_MS = 120_000;

	private static final class Pending {
		final Consumer<String> handler;
		final long createdAt = System.currentTimeMillis();

		Pending(Consumer<String> handler) {
			this.handler = handler;
		}
	}

	private final CrateKitPlugin plugin;
	private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

	public ChatPrompt(CrateKitPlugin plugin) {
		this.plugin = plugin;
	}

	public void ask(Player player, String question, Consumer<String> handler) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			player.closeInventory();
			Msg.send(player, question);
			Msg.send(player, "&8Type &ccancel &8to abort.");
			pending.put(player.getUniqueId(), new Pending(handler));
		});
	}

	public static boolean isCancel(String input) {
		return input.equalsIgnoreCase("cancel");
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onChat(AsyncPlayerChatEvent event) {
		Pending p = pending.remove(event.getPlayer().getUniqueId());
		if (p == null) {
			return;
		}
		if (System.currentTimeMillis() - p.createdAt > TIMEOUT_MS) {
			return; // expired: treat as normal chat
		}
		event.setCancelled(true);
		String text = event.getMessage().trim();
		Bukkit.getScheduler().runTask(plugin, () -> p.handler.accept(text));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		pending.remove(event.getPlayer().getUniqueId());
	}
}
