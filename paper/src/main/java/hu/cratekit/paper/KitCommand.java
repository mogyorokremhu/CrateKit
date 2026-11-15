package hu.cratekit.paper;

import hu.cratekit.common.Durations;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /kit ... */
public final class KitCommand implements CommandExecutor, TabCompleter {
	private static final List<String> PLAYER_SUBS = Arrays.asList("gui", "list", "claim", "preview");
	private static final List<String> ADMIN_SUBS = Arrays.asList("create", "update", "edit", "delete", "rename",
		"setcommand", "setcooldown", "give", "resetcooldown", "info", "reload");
	private static final List<String> KIT_ARG = Arrays.asList("claim", "preview", "update", "edit", "delete", "rename",
		"setcommand", "setcooldown", "resetcooldown", "info");

	private final CrateKitPlugin plugin;

	public KitCommand(CrateKitPlugin plugin) {
		this.plugin = plugin;
	}

	private KitManager kits() {
		return plugin.kits();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
			Player p = playerOnly(sender);
			if (p != null) {
				new KitMenu(plugin, p, false).open();
			}
			return true;
		}
		String sub = args[0].toLowerCase(Locale.ROOT);
		if (ADMIN_SUBS.contains(sub) && !KitManager.isAdmin(sender)) {
			Msg.send(sender, "&cYou do not have permission to do that.");
			return true;
		}
		switch (sub) {
			case "list":
				return list(sender);
			case "claim":
			case "preview":
				return playerKit(sender, args, 1, (p, kit) -> {
					if (sub.equals("claim")) {
						claimAndReport(plugin, p, kit);
					} else {
						new KitPreview(plugin, p, kit, null).open();
					}
				});
			case "create":
				return create(sender, args);
			case "update":
				return playerKit(sender, args, 1, (p, kit) -> {
					kits().updateContents(kit, p);
					Msg.send(p, "Kit &e" + kit.name + " &7now contains your current inventory (&f" + kit.items.size() + " &7stacks).");
				});
			case "edit":
				return playerKit(sender, args, 1, (p, kit) -> new KitEditor(plugin, p, kit).open());
			case "delete":
				return kit(sender, args, 1, kit -> {
					kits().delete(kit);
					Msg.send(sender, "Deleted kit &e" + kit.name + "&7.");
				});
			case "rename":
				if (args.length < 3) {
					return usage(sender, "/kit rename <kit> <display name>");
				}
				return kit(sender, args, 1, kit -> {
					kit.displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
					kits().changed();
					Msg.send(sender, "Kit &e" + kit.name + " &7is now shown as &r" + Msg.c(kit.displayName) + "&7.");
				});
			case "setcommand":
				return kit(sender, args, 1, kit -> setCommand(sender, kit, args.length > 2 ? args[2] : null));
			case "setcooldown":
				if (args.length < 3) {
					return usage(sender, "/kit setcooldown <kit> <time>   (e.g. 30m, 2h 30m, 1d, none, once)");
				}
				return kit(sender, args, 1, kit -> {
					try {
						kit.cooldownSeconds = Durations.parse(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
					} catch (IllegalArgumentException e) {
						Msg.send(sender, "&c" + e.getMessage());
						return;
					}
					kits().changed();
					Msg.send(sender, "Cooldown of &e" + kit.name + " &7is now &f" + Durations.describe(kit.cooldownSeconds) + "&7.");
				});
			case "give":
				return give(sender, args);
			case "resetcooldown":
				return resetCooldown(sender, args);
			case "info":
				return kit(sender, args, 1, kit -> Msg.send(sender, "&e" + kit.name + " &7- name: &r" + Msg.c(kit.displayName)
					+ "&7, stacks: &f" + kit.items.size() + "&7, cooldown: &f" + Durations.describe(kit.cooldownSeconds)
					+ "&7, command: &f" + (kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias)));
			case "reload":
				kits().unregisterAllAliases();
				kits().load();
				kits().syncAliases();
				Msg.send(sender, "Reloaded &f" + kits().all().size() + " &7kit(s) from disk.");
				return true;
			default:
				return usage(sender, "/kit [list|claim <kit>|preview <kit>]" + (KitManager.isAdmin(sender) ? " or an admin command (see the README)" : ""));
		}
	}

	// ------------------------------------------------------------------ helpers

	private interface PlayerKit {
		void run(Player player, Kit kit);
	}

	private interface KitAction {
		void run(Kit kit);
	}

	private static Player playerOnly(CommandSender sender) {
		if (sender instanceof Player) {
			return (Player) sender;
		}
		sender.sendMessage("This command can only be used by a player.");
		return null;
	}

	private static boolean usage(CommandSender sender, String usage) {
		Msg.send(sender, "&cUsage: &f" + usage);
		return true;
	}

	private boolean kit(CommandSender sender, String[] args, int index, KitAction action) {
		if (args.length <= index) {
			return usage(sender, "/kit " + args[0] + " <kit>");
		}
		Kit kit = kits().get(args[index]);
		if (kit == null) {
			Msg.send(sender, "&cThere is no kit called '" + args[index] + "'.");
			return true;
		}
		action.run(kit);
		return true;
	}

	private boolean playerKit(CommandSender sender, String[] args, int index, PlayerKit action) {
		Player player = playerOnly(sender);
		if (player == null) {
			return true;
		}
		return kit(sender, args, index, kit -> action.run(player, kit));
	}

	private boolean list(CommandSender sender) {
		Player player = playerOnly(sender);
		if (player == null) {
			return true;
		}
		List<Kit> all = kits().all();
		if (all.isEmpty()) {
			Msg.send(player, "There are no kits yet.");
			return true;
		}
		Msg.send(player, "Available kits:");
		for (Kit kit : all) {
			long left = kits().remaining(kit, player.getUniqueId());
			String status = left == 0 ? "&aready" : left == Durations.ONCE ? "&cclaimed" : "&c" + Durations.format(left);
			String how = kit.alias == null ? "/kit claim " + kit.name : "/" + kit.alias;
			player.sendMessage(Msg.c(" &8- &e" + kit.name + " &8(" + status + "&8) &7" + how));
		}
		return true;
	}

	private boolean create(CommandSender sender, String[] args) {
		Player player = playerOnly(sender);
		if (player == null) {
			return true;
		}
		if (args.length < 2) {
			return usage(sender, "/kit create <name>");
		}
		String name = args[1].toLowerCase(Locale.ROOT);
		if (!Durations.NAME.matcher(name).matches()) {
			Msg.send(player, "&cKit names may only use letters, numbers, '_' and '-' (max 24 characters).");
			return true;
		}
		if (kits().get(name) != null) {
			Msg.send(player, "&cA kit called '" + name + "' already exists. Use /kit update " + name + " to replace its contents.");
			return true;
		}
		Kit kit = kits().createFromInventory(player, name);
		if (kit == null) {
			Msg.send(player, "&cCould not create the kit.");
			return true;
		}
		if (kit.items.isEmpty()) {
			Msg.send(player, "&eKit created, but your inventory was empty. Open &f/kit edit " + kit.name + " &eto add items.");
		} else {
			Msg.send(player, "Created kit &e" + kit.name + " &7with &f" + kit.items.size() + " &7stacks from your inventory.");
		}
		Msg.send(player, "Players claim it with &f/kit claim " + kit.name + "&7. Customise it with &f/kit edit " + kit.name + "&7.");
		return true;
	}

	private void setCommand(CommandSender sender, Kit kit, String alias) {
		if (alias == null) {
			kit.alias = null;
			kits().changed();
			kits().syncAliases();
			Msg.send(sender, "Kit &e" + kit.name + " &7is now claimed with &f/kit claim " + kit.name + "&7.");
			return;
		}
		alias = alias.toLowerCase(Locale.ROOT).replaceFirst("^/", "");
		switch (kits().checkAlias(kit, alias)) {
			case INVALID:
				Msg.send(sender, "&cCommands may only use letters, numbers, '_' and '-' (max 24 characters).");
				return;
			case TAKEN:
				Msg.send(sender, "&c/" + alias + " is already used by another kit.");
				return;
			case RESERVED:
				Msg.send(sender, "&c/" + alias + " is already an existing command.");
				return;
			default:
				break;
		}
		kit.alias = alias;
		kits().changed();
		kits().syncAliases();
		Msg.send(sender, "Players can now claim &e" + kit.name + " &7with &f/" + alias + " &7(and &f/kit claim " + kit.name + "&7).");
	}

	private boolean give(CommandSender sender, String[] args) {
		if (args.length < 3) {
			return usage(sender, "/kit give <player> <kit>");
		}
		Player target = Bukkit.getPlayerExact(args[1]);
		if (target == null) {
			Msg.send(sender, "&cThat player is not online.");
			return true;
		}
		return kit(sender, args, 2, kit -> {
			kits().give(target, kit);
			Msg.send(target, "You received the kit &e" + Msg.c(kit.displayName) + "&7.");
			Msg.send(sender, "Gave kit &e" + kit.name + " &7to &f" + target.getName() + "&7.");
		});
	}

	private boolean resetCooldown(CommandSender sender, String[] args) {
		return kit(sender, args, 1, kit -> {
			if (args.length > 2) {
				Player target = Bukkit.getPlayerExact(args[2]);
				if (target == null) {
					Msg.send(sender, "&cThat player is not online.");
					return;
				}
				kits().resetCooldown(kit, target.getUniqueId());
				Msg.send(sender, "Reset cooldown of &e" + kit.name + " &7for &f" + target.getName() + "&7.");
			} else {
				kits().resetCooldown(kit, (UUID) null);
				Msg.send(sender, "Reset cooldown of &e" + kit.name + " &7for everyone.");
			}
		});
	}

	/** Claims a kit for the player and tells them how it went. */
	public static void claimAndReport(CrateKitPlugin plugin, Player player, Kit kit) {
		KitManager.ClaimResult result = plugin.kits().claim(player, kit, false);
		String name = Msg.c(kit.displayName);
		switch (result.status) {
			case SUCCESS:
				Msg.send(player, "&aYou claimed the kit &e" + name + "&a!");
				Msg.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
				return;
			case COOLDOWN:
				Msg.send(player, "&cYou can claim &e" + name + " &cagain in &f" + Durations.format(result.remainingSeconds) + "&c.");
				break;
			case ONCE:
				Msg.send(player, "&cYou have already claimed &e" + name + "&c, it can only be claimed once.");
				break;
			default:
				Msg.send(player, "&cThe kit &e" + name + " &cis empty.");
		}
		Msg.sound(player, Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
	}

	// ------------------------------------------------------------------ tab completion

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> out = new ArrayList<>();
		if (args.length == 1) {
			out.addAll(PLAYER_SUBS);
			if (KitManager.isAdmin(sender)) {
				out.addAll(ADMIN_SUBS);
			}
		} else if (args.length == 2) {
			String sub = args[0].toLowerCase(Locale.ROOT);
			if (sub.equals("give")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					out.add(p.getName());
				}
			} else if (KIT_ARG.contains(sub) && (PLAYER_SUBS.contains(sub) || KitManager.isAdmin(sender))) {
				for (Kit k : kits().all()) {
					out.add(k.name);
				}
			}
		} else if (args.length == 3 && KitManager.isAdmin(sender)) {
			String sub = args[0].toLowerCase(Locale.ROOT);
			if (sub.equals("give")) {
				for (Kit k : kits().all()) {
					out.add(k.name);
				}
			} else if (sub.equals("setcooldown")) {
				out.addAll(Arrays.asList("none", "once", "30m", "1h", "12h", "1d", "7d"));
			} else if (sub.equals("resetcooldown")) {
				for (Player p : Bukkit.getOnlinePlayers()) {
					out.add(p.getName());
				}
			}
		}
		String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
		out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(typed));
		return out;
	}
}
