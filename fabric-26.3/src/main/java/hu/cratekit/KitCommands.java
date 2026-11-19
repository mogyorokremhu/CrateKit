package hu.cratekit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import hu.cratekit.gui.KitEditor;
import hu.cratekit.gui.KitMenu;
import hu.cratekit.gui.KitPreview;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /kit ... */
public final class KitCommands {
	private static final SuggestionProvider<CommandSourceStack> KIT_NAMES = (ctx, builder) ->
		SharedSuggestionProvider.suggest(CrateKit.kits().all().stream().map(k -> k.name), builder);

	private KitCommands() {
	}

	private static KitManager kits() {
		return CrateKit.kits();
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		Predicate<CommandSourceStack> admin = KitManager::isAdmin;

		dispatcher.register(literal("kit")
			.executes(ctx -> openMenu(ctx.getSource()))
			.then(literal("gui").executes(ctx -> openMenu(ctx.getSource())))
			.then(literal("list").executes(ctx -> list(ctx.getSource())))
			.then(literal("claim")
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> claimAndReport(ctx.getSource().getPlayerOrException(), kit(ctx)))))
			.then(literal("preview")
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> {
						new KitPreview(ctx.getSource().getPlayerOrException(), kit(ctx), null).open();
						return 1;
					})))

			// ---- admin commands
			.then(literal("create").requires(admin)
				.then(argument("name", StringArgumentType.word())
					.executes(ctx -> create(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")))))
			.then(literal("update").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						Kit kit = kit(ctx);
						kits().updateContents(kit, player);
						Fmt.send(player, "Kit &e" + kit.name + " &7now contains your current inventory (&f" + kit.items.size() + " &7stacks).");
						return 1;
					})))
			.then(literal("edit").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> {
						new KitEditor(ctx.getSource().getPlayerOrException(), kit(ctx)).open();
						return 1;
					})))
			.then(literal("delete").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> {
						Kit kit = kit(ctx);
						kits().delete(kit);
						reply(ctx, "Deleted kit &e" + kit.name + "&7.");
						return 1;
					})))
			.then(literal("rename").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.then(argument("displayName", StringArgumentType.greedyString())
						.executes(ctx -> {
							Kit kit = kit(ctx);
							kit.displayName = StringArgumentType.getString(ctx, "displayName");
							kits().changed();
							reply(ctx, "Kit &e" + kit.name + " &7is now shown as &r" + kit.displayName + "&7.");
							return 1;
						}))))
			.then(literal("setcommand").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> setCommand(ctx, kit(ctx), null))
					.then(argument("command", StringArgumentType.word())
						.executes(ctx -> setCommand(ctx, kit(ctx), StringArgumentType.getString(ctx, "command"))))))
			.then(literal("setcooldown").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.then(argument("time", StringArgumentType.greedyString())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("none", "once", "30m", "1h", "12h", "1d", "7d"), b))
						.executes(ctx -> {
							Kit kit = kit(ctx);
							try {
								kit.cooldownSeconds = Fmt.parseDuration(StringArgumentType.getString(ctx, "time"));
							} catch (IllegalArgumentException e) {
								throw error(e.getMessage());
							}
							kits().changed();
							reply(ctx, "Cooldown of &e" + kit.name + " &7is now &f" + Fmt.cooldown(kit.cooldownSeconds) + "&7.");
							return 1;
						}))))
			.then(literal("give").requires(admin)
				.then(argument("player", EntityArgument.player())
					.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
						.executes(ctx -> {
							ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
							Kit kit = kit(ctx);
							kits().give(target, kit);
							Fmt.send(target, "You received the kit &e" + Fmt.parse(kit.displayName).getString() + "&7.");
							reply(ctx, "Gave kit &e" + kit.name + " &7to &f" + target.getName().getString() + "&7.");
							return 1;
						}))))
			.then(literal("resetcooldown").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> {
						kits().resetCooldown(kit(ctx), null);
						reply(ctx, "Reset cooldown of &e" + kit(ctx).name + " &7for everyone.");
						return 1;
					})
					.then(argument("player", EntityArgument.player())
						.executes(ctx -> {
							ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
							kits().resetCooldown(kit(ctx), target.getUUID());
							reply(ctx, "Reset cooldown of &e" + kit(ctx).name + " &7for &f" + target.getName().getString() + "&7.");
							return 1;
						}))))
			.then(literal("info").requires(admin)
				.then(argument("kit", StringArgumentType.word()).suggests(KIT_NAMES)
					.executes(ctx -> {
						Kit kit = kit(ctx);
						reply(ctx, "&e" + kit.name + " &7- name: &r" + kit.displayName + "&7, stacks: &f" + kit.items.size()
							+ "&7, cooldown: &f" + Fmt.cooldown(kit.cooldownSeconds)
							+ "&7, command: &f" + (kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias));
						return 1;
					})))
			.then(literal("reload").requires(admin)
				.executes(ctx -> {
					kits().load(ctx.getSource().getServer());
					kits().syncAliases();
					reply(ctx, "Reloaded &f" + kits().all().size() + " &7kit(s) from disk.");
					return 1;
				}))
		);
	}

	// ------------------------------------------------------------------ helpers

	private static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(Fmt.msg("&c" + message)).create();
	}

	private static Kit kit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String name = StringArgumentType.getString(ctx, "kit");
		Kit kit = kits().get(name);
		if (kit == null) {
			throw error("There is no kit called '" + name + "'.");
		}
		return kit;
	}

	private static void reply(CommandContext<CommandSourceStack> ctx, String message) {
		ctx.getSource().sendSuccess(() -> Fmt.msg(message), false);
	}

	private static int openMenu(CommandSourceStack source) throws CommandSyntaxException {
		new KitMenu(source.getPlayerOrException(), false).open();
		return 1;
	}

	private static int list(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		List<Kit> all = kits().all();
		if (all.isEmpty()) {
			Fmt.send(player, "There are no kits yet.");
			return 0;
		}
		Fmt.send(player, "Available kits:");
		for (Kit kit : all) {
			long left = kits().remaining(kit, player.getUUID());
			String status = left == 0 ? "&aready" : left == Kit.ONCE ? "&cclaimed" : "&c" + Fmt.duration(left);
			String how = kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias;
			player.sendSystemMessage(Fmt.parse(" &8- &e" + kit.name + " &8(" + status + "&8) &7" + how));
		}
		return all.size();
	}

	private static int create(ServerPlayer player, String name) throws CommandSyntaxException {
		if (!KitManager.NAME.matcher(name.toLowerCase()).matches()) {
			throw error("Kit names may only use letters, numbers, '_' and '-' (max 24 characters).");
		}
		if (kits().get(name) != null) {
			throw error("A kit called '" + name + "' already exists. Use /kit update " + name + " to replace its contents.");
		}
		Kit kit = kits().createFromInventory(player, name);
		if (kit == null) {
			throw error("Could not create the kit.");
		}
		if (kit.items.isEmpty()) {
			Fmt.send(player, "&eKit created, but your inventory was empty. Open &f/kit edit " + kit.name + " &eto add items.");
		} else {
			Fmt.send(player, "Created kit &e" + kit.name + " &7with &f" + kit.items.size() + " &7stacks from your inventory.");
		}
		Fmt.send(player, "Players claim it with &f/kit claim " + kit.name + "&7. Customise it with &f/kit edit " + kit.name + "&7.");
		return 1;
	}

	private static int setCommand(CommandContext<CommandSourceStack> ctx, Kit kit, String alias) throws CommandSyntaxException {
		if (alias == null) {
			kit.alias = null;
			kits().changed();
			kits().syncAliases();
			reply(ctx, "Kit &e" + kit.name + " &7is now claimed with &f/kit claim " + kit.name + "&7.");
			return 1;
		}
		alias = alias.toLowerCase();
		switch (kits().checkAlias(kit, alias)) {
			case INVALID -> throw error("Commands may only use letters, numbers, '_' and '-' (max 24 characters).");
			case TAKEN -> throw error("/" + alias + " is already used by another kit.");
			case RESERVED -> throw error("/" + alias + " is already an existing command.");
			default -> {
			}
		}
		kit.alias = alias;
		kits().changed();
		kits().syncAliases();
		reply(ctx, "Players can now claim &e" + kit.name + " &7with &f/" + alias + " &7(and &f/kit claim " + kit.name + "&7).");
		return 1;
	}

	/** Claims a kit for the player and tells them how it went. */
	public static int claimAndReport(ServerPlayer player, Kit kit) {
		KitManager.ClaimResult result = kits().claim(player, kit, false);
		String name = Fmt.parse(kit.displayName).getString();
		switch (result.status()) {
			case SUCCESS -> {
				Fmt.send(player, "&aYou claimed the kit &e" + name + "&a!");
				player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.2f);
				return 1;
			}
			case COOLDOWN -> Fmt.send(player, "&cYou can claim &e" + name + " &cagain in &f" + Fmt.duration(result.remainingSeconds()) + "&c.");
			case ONCE -> Fmt.send(player, "&cYou have already claimed &e" + name + "&c, it can only be claimed once.");
			case EMPTY -> Fmt.send(player, "&cThe kit &e" + name + " &cis empty.");
		}
		player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7f, 1f);
		return 0;
	}
}
