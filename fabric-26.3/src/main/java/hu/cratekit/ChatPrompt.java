package hu.cratekit;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lets a GUI ask the player to type a value in chat (chest GUIs cannot take text input). */
public final class ChatPrompt {
	private static final long TIMEOUT_MS = 120_000;
	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private record Pending(Consumer<String> handler, long createdAt) {
	}

	private ChatPrompt() {
	}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			Pending pending = PENDING.remove(sender.getUUID());
			if (pending == null) {
				return true;
			}
			if (System.currentTimeMillis() - pending.createdAt > TIMEOUT_MS) {
				return true; // expired, treat as a normal chat message
			}
			String text = message.signedContent().trim();
			CrateKit.kits().server().execute(() -> pending.handler.accept(text));
			return false;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.getPlayer().getUUID()));
	}

	/** Closes the player's screen, tells them what to type, and runs the handler with their next chat message. */
	public static void ask(ServerPlayer player, String question, Consumer<String> handler) {
		player.closeContainer();
		Fmt.send(player, question);
		Fmt.send(player, "&8Type &ccancel &8to abort.");
		PENDING.put(player.getUUID(), new Pending(handler, System.currentTimeMillis()));
	}

	public static boolean isCancel(String input) {
		return input.equalsIgnoreCase("cancel");
	}
}
