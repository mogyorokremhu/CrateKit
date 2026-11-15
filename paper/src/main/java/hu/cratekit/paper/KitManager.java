package hu.cratekit.paper;

import hu.cratekit.common.Durations;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/** Owns all kits, claim history, persistence and the dynamic claim commands. */
public final class KitManager {
	public enum Status {SUCCESS, COOLDOWN, ONCE, EMPTY}

	public enum AliasError {OK, INVALID, TAKEN, RESERVED}

	public static final class ClaimResult {
		public final Status status;
		public final long remainingSeconds;

		ClaimResult(Status status, long remainingSeconds) {
			this.status = status;
			this.remainingSeconds = remainingSeconds;
		}
	}

	private final CrateKitPlugin plugin;
	private final File kitsFile;
	private final File claimsFile;
	private final Map<String, Kit> kits = new TreeMap<>();
	private final Map<String, Map<UUID, Long>> claims = new HashMap<>();
	private final Set<String> registeredAliases = new HashSet<>();
	private boolean claimsDirty;

	public KitManager(CrateKitPlugin plugin) {
		this.plugin = plugin;
		this.kitsFile = new File(plugin.getDataFolder(), "kits.yml");
		this.claimsFile = new File(plugin.getDataFolder(), "claims.yml");
	}

	// ------------------------------------------------------------------ access

	public Kit get(String name) {
		return name == null ? null : kits.get(name.toLowerCase(Locale.ROOT));
	}

	public List<Kit> all() {
		return new ArrayList<>(kits.values());
	}

	public Kit byAlias(String alias) {
		for (Kit k : kits.values()) {
			if (alias.equalsIgnoreCase(k.alias)) {
				return k;
			}
		}
		return null;
	}

	public static boolean isAdmin(CommandSender sender) {
		return sender.hasPermission("cratekit.admin");
	}

	// ------------------------------------------------------------------ mutations

	public Kit createFromInventory(Player player, String rawName) {
		String name = rawName.toLowerCase(Locale.ROOT);
		if (!Durations.NAME.matcher(name).matches() || kits.containsKey(name)) {
			return null;
		}
		Kit kit = new Kit(name);
		kit.items = snapshot(player);
		kits.put(name, kit);
		save();
		return kit;
	}

	public void updateContents(Kit kit, Player player) {
		kit.items = snapshot(player);
		save();
	}

	public void delete(Kit kit) {
		kits.remove(kit.name);
		claims.remove(kit.name);
		claimsDirty = true;
		save();
		syncAliases();
	}

	public void changed() {
		save();
	}

	public void resetCooldown(Kit kit, UUID player) {
		Map<UUID, Long> map = claims.get(kit.name);
		if (map != null) {
			if (player == null) {
				map.clear();
			} else {
				map.remove(player);
			}
			claimsDirty = true;
		}
	}

	private static List<ItemStack> snapshot(Player player) {
		List<ItemStack> out = new ArrayList<>();
		PlayerInventory inv = player.getInventory();
		for (ItemStack stack : inv.getArmorContents()) {
			add(out, stack);
		}
		add(out, inv.getItemInOffHand());
		for (ItemStack stack : inv.getStorageContents()) {
			add(out, stack);
		}
		return out;
	}

	private static void add(List<ItemStack> out, ItemStack stack) {
		if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
			out.add(stack.clone());
		}
	}

	// ------------------------------------------------------------------ claiming

	public long remaining(Kit kit, UUID player) {
		Map<UUID, Long> map = claims.get(kit.name);
		return Durations.remaining(kit.cooldownSeconds, map == null ? null : map.get(player), System.currentTimeMillis());
	}

	public ClaimResult claim(Player player, Kit kit, boolean ignoreCooldown) {
		if (kit.items.isEmpty()) {
			return new ClaimResult(Status.EMPTY, 0);
		}
		if (!ignoreCooldown) {
			long left = remaining(kit, player.getUniqueId());
			if (left == Durations.ONCE) {
				return new ClaimResult(Status.ONCE, 0);
			}
			if (left > 0) {
				return new ClaimResult(Status.COOLDOWN, left);
			}
		}
		give(player, kit);
		if (!ignoreCooldown) {
			claims.computeIfAbsent(kit.name, k -> new HashMap<>()).put(player.getUniqueId(), System.currentTimeMillis());
			claimsDirty = true;
		}
		return new ClaimResult(Status.SUCCESS, 0);
	}

	/** Armor goes to empty armor slots, everything else to the inventory; leftovers drop at the player's feet. */
	public void give(Player player, Kit kit) {
		PlayerInventory inv = player.getInventory();
		for (ItemStack original : kit.items) {
			ItemStack stack = original.clone();
			if (stack.getAmount() == 1 && equipArmor(inv, stack)) {
				continue;
			}
			Map<Integer, ItemStack> leftover = inv.addItem(stack);
			for (ItemStack rest : leftover.values()) {
				player.getWorld().dropItemNaturally(player.getLocation(), rest);
			}
		}
		player.updateInventory();
	}

	private static boolean equipArmor(PlayerInventory inv, ItemStack stack) {
		String type = stack.getType().name();
		if (type.endsWith("_HELMET") && isEmpty(inv.getHelmet())) {
			inv.setHelmet(stack);
		} else if ((type.endsWith("_CHESTPLATE") || type.equals("ELYTRA")) && isEmpty(inv.getChestplate())) {
			inv.setChestplate(stack);
		} else if (type.endsWith("_LEGGINGS") && isEmpty(inv.getLeggings())) {
			inv.setLeggings(stack);
		} else if (type.endsWith("_BOOTS") && isEmpty(inv.getBoots())) {
			inv.setBoots(stack);
		} else {
			return false;
		}
		return true;
	}

	private static boolean isEmpty(ItemStack stack) {
		return stack == null || stack.getType() == Material.AIR;
	}

	// ------------------------------------------------------------------ aliases (dynamic commands)

	/** Reflection keeps this working on Paper, Spigot and Purpur across versions. */
	private static CommandMap commandMap() {
		try {
			Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
			return (CommandMap) m.invoke(Bukkit.getServer());
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Command> knownCommands(CommandMap map) {
		try {
			return (Map<String, Command>) map.getClass().getMethod("getKnownCommands").invoke(map);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	public AliasError checkAlias(Kit kit, String alias) {
		if (!alias.matches("[a-z0-9_-]{1,24}")) {
			return AliasError.INVALID;
		}
		if (alias.equals("kit")) {
			return AliasError.RESERVED;
		}
		Kit other = byAlias(alias);
		if (other != null && other != kit) {
			return AliasError.TAKEN;
		}
		CommandMap map = commandMap();
		Map<String, Command> known = map == null ? null : knownCommands(map);
		if (known != null && known.containsKey(alias) && !registeredAliases.contains(alias)) {
			return AliasError.RESERVED;
		}
		return AliasError.OK;
	}

	/** Makes the registered command list match the kits' aliases, then refreshes players' tab completion. */
	public void syncAliases() {
		CommandMap map = commandMap();
		if (map == null) {
			plugin.getLogger().warning("Could not access the command map; custom kit commands are unavailable.");
			return;
		}
		Map<String, Command> known = knownCommands(map);
		Set<String> wanted = new HashSet<>();
		for (Kit kit : kits.values()) {
			if (kit.alias != null) {
				wanted.add(kit.alias);
			}
		}
		for (String alias : new ArrayList<>(registeredAliases)) {
			if (!wanted.contains(alias)) {
				removeCommand(map, known, alias);
				registeredAliases.remove(alias);
			}
		}
		for (String alias : wanted) {
			if (!registeredAliases.contains(alias)) {
				if (known != null && known.containsKey(alias)) {
					plugin.getLogger().warning("Cannot register /" + alias + ": another command already uses it.");
					continue;
				}
				map.register("cratekit", new AliasCommand(plugin, alias));
				registeredAliases.add(alias);
			}
		}
		for (Player p : Bukkit.getOnlinePlayers()) {
			try {
				p.updateCommands();
			} catch (Throwable ignored) {
				// Older servers: players simply see the new command after rejoining.
			}
		}
	}

	private static void removeCommand(CommandMap map, Map<String, Command> known, String alias) {
		if (known == null) {
			return;
		}
		Command cmd = known.get(alias);
		if (cmd instanceof AliasCommand) {
			cmd.unregister(map);
		}
		known.remove(alias);
		known.remove("cratekit:" + alias);
	}

	public void unregisterAllAliases() {
		CommandMap map = commandMap();
		if (map == null) {
			return;
		}
		Map<String, Command> known = knownCommands(map);
		for (String alias : new ArrayList<>(registeredAliases)) {
			removeCommand(map, known, alias);
		}
		registeredAliases.clear();
	}

	// ------------------------------------------------------------------ persistence

	public void load() {
		kits.clear();
		claims.clear();
		if (kitsFile.exists()) {
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(kitsFile);
			ConfigurationSection root = yaml.getConfigurationSection("kits");
			if (root != null) {
				for (String name : root.getKeys(false)) {
					ConfigurationSection s = root.getConfigurationSection(name);
					if (s == null) {
						continue;
					}
					Kit kit = new Kit(name);
					kit.displayName = s.getString("displayName", name);
					kit.alias = s.getString("alias");
					kit.cooldownSeconds = s.getLong("cooldownSeconds", 0);
					kit.icon = s.getItemStack("icon");
					List<?> list = s.getList("items");
					if (list != null) {
						for (Object o : list) {
							if (o instanceof ItemStack) {
								kit.items.add((ItemStack) o);
							}
						}
					}
					kits.put(name, kit);
				}
			}
		}
		if (claimsFile.exists()) {
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(claimsFile);
			for (String kitName : yaml.getKeys(false)) {
				ConfigurationSection s = yaml.getConfigurationSection(kitName);
				if (s == null) {
					continue;
				}
				Map<UUID, Long> map = new HashMap<>();
				for (String uuid : s.getKeys(false)) {
					try {
						map.put(UUID.fromString(uuid), s.getLong(uuid));
					} catch (IllegalArgumentException ignored) {
						// skip malformed entry
					}
				}
				claims.put(kitName, map);
			}
		}
		plugin.getLogger().info("Loaded " + kits.size() + " kit(s).");
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Kit kit : kits.values()) {
			String path = "kits." + kit.name;
			yaml.set(path + ".displayName", kit.displayName);
			yaml.set(path + ".alias", kit.alias);
			yaml.set(path + ".cooldownSeconds", kit.cooldownSeconds);
			yaml.set(path + ".icon", kit.icon);
			yaml.set(path + ".items", kit.items);
		}
		write(yaml, kitsFile);
	}

	public void saveClaimsIfDirty() {
		if (!claimsDirty) {
			return;
		}
		claimsDirty = false;
		YamlConfiguration yaml = new YamlConfiguration();
		for (Map.Entry<String, Map<UUID, Long>> kit : claims.entrySet()) {
			for (Map.Entry<UUID, Long> e : kit.getValue().entrySet()) {
				yaml.set(kit.getKey() + "." + e.getKey(), e.getValue());
			}
		}
		write(yaml, claimsFile);
	}

	private void write(YamlConfiguration yaml, File file) {
		try {
			file.getParentFile().mkdirs();
			File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
			yaml.save(tmp);
			java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save " + file.getName(), e);
		}
	}
}
