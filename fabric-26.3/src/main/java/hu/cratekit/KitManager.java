package hu.cratekit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import hu.cratekit.common.Durations;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.literal;

/** Owns all kits, the claim history, persistence and the dynamic claim commands. */
public final class KitManager {
	public static final Pattern NAME = Durations.NAME;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve("cratekit");
	private final Path kitsFile = dir.resolve("kits.json");
	private final Path claimsFile = dir.resolve("claims.json");

	private final Map<String, Kit> kits = new TreeMap<>();
	/** kit name -> (player uuid -> epoch millis of last claim) */
	private final Map<String, Map<UUID, Long>> claims = new HashMap<>();
	/** Aliases whose command node we created in the current dispatcher. */
	private final Set<String> ownedAliases = new HashSet<>();
	private boolean claimsDirty;
	private MinecraftServer server;

	public enum Status {SUCCESS, COOLDOWN, ONCE, EMPTY}

	public record ClaimResult(Status status, long remainingSeconds) {
	}

	public enum AliasError {OK, INVALID, TAKEN, RESERVED}

	public MinecraftServer server() {
		return server;
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
			if (alias.equals(k.alias)) {
				return k;
			}
		}
		return null;
	}

	public static boolean isAdmin(CommandSourceStack source) {
		return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
	}

	public static boolean isAdmin(ServerPlayer player) {
		return isAdmin(player.createCommandSourceStack());
	}

	// ------------------------------------------------------------------ mutations

	public Kit createFromInventory(ServerPlayer player, String rawName) {
		String name = rawName.toLowerCase(Locale.ROOT);
		if (!NAME.matcher(name).matches() || kits.containsKey(name)) {
			return null;
		}
		Kit kit = new Kit(name);
		kit.items = snapshot(player);
		kits.put(name, kit);
		save();
		return kit;
	}

	public void updateContents(Kit kit, ServerPlayer player) {
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

	/** Call after changing any kit field so it is written to disk. */
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

	private static List<ItemStack> snapshot(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR) {
			add(out, player.getItemBySlot(slot));
		}
		add(out, player.getItemBySlot(EquipmentSlot.OFFHAND));
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			add(out, stack);
		}
		return out;
	}

	private static void add(List<ItemStack> out, ItemStack stack) {
		if (!stack.isEmpty()) {
			out.add(stack.copy());
		}
	}

	// ------------------------------------------------------------------ claiming

	public long remaining(Kit kit, UUID player) {
		Map<UUID, Long> map = claims.get(kit.name);
		return Durations.remaining(kit.cooldownSeconds, map == null ? null : map.get(player), System.currentTimeMillis());
	}

	public ClaimResult claim(ServerPlayer player, Kit kit, boolean ignoreCooldown) {
		if (kit.items.isEmpty()) {
			return new ClaimResult(Status.EMPTY, 0);
		}
		if (!ignoreCooldown) {
			long left = remaining(kit, player.getUUID());
			if (left == Kit.ONCE) {
				return new ClaimResult(Status.ONCE, 0);
			}
			if (left > 0) {
				return new ClaimResult(Status.COOLDOWN, left);
			}
		}
		give(player, kit);
		if (!ignoreCooldown) {
			claims.computeIfAbsent(kit.name, k -> new HashMap<>()).put(player.getUUID(), System.currentTimeMillis());
			claimsDirty = true;
		}
		return new ClaimResult(Status.SUCCESS, 0);
	}

	/** Gives the kit's items. Armor goes to empty armor slots, everything else to the inventory, overflow is dropped. */
	public void give(ServerPlayer player, Kit kit) {
		for (ItemStack original : kit.items) {
			ItemStack stack = original.copy();
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (equippable != null && stack.getCount() == 1 && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR
				&& player.getItemBySlot(equippable.slot()).isEmpty()) {
				player.setItemSlot(equippable.slot(), stack);
				continue;
			}
			player.getInventory().add(stack);
			if (!stack.isEmpty()) {
				player.drop(stack, false);
			}
		}
	}

	// ------------------------------------------------------------------ aliases

	public AliasError checkAlias(Kit kit, String alias) {
		if (!Pattern.matches("[a-z0-9_-]{1,24}", alias)) {
			return AliasError.INVALID;
		}
		if (alias.equals("kit")) {
			return AliasError.RESERVED;
		}
		Kit other = byAlias(alias);
		if (other != null && other != kit) {
			return AliasError.TAKEN;
		}
		if (server != null && !ownedAliases.contains(alias)
			&& server.getCommands().getDispatcher().getRoot().getChild(alias) != null) {
			return AliasError.RESERVED; // an existing vanilla/mod command
		}
		return AliasError.OK;
	}

	/** Registers a command node for every alias in the live dispatcher and refreshes player command trees. */
	public void syncAliases() {
		if (server == null) {
			return;
		}
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		for (Kit kit : kits.values()) {
			if (kit.alias != null) {
				registerAlias(dispatcher, kit.alias);
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			server.getCommands().sendCommands(p);
		}
	}

	private void registerAlias(CommandDispatcher<CommandSourceStack> dispatcher, String alias) {
		if (dispatcher.getRoot().getChild(alias) != null) {
			return; // already ours (hidden/shown via its requirement) or someone else's
		}
		ownedAliases.add(alias);
		dispatcher.register(literal(alias)
			.requires(src -> src.getPlayer() != null && byAlias(alias) != null)
			.executes(ctx -> {
				Kit kit = byAlias(alias);
				if (kit == null) {
					return 0;
				}
				return KitCommands.claimAndReport(ctx.getSource().getPlayerOrException(), kit);
			}));
	}

	/** Called when the dispatcher was rebuilt (server start or /reload). */
	public void onDispatcherRebuilt() {
		ownedAliases.clear();
		syncAliases();
	}

	// ------------------------------------------------------------------ persistence

	public void load(MinecraftServer server) {
		this.server = server;
		kits.clear();
		claims.clear();
		try {
			Files.createDirectories(dir);
			RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			if (Files.exists(kitsFile)) {
				try (Reader r = Files.newBufferedReader(kitsFile, StandardCharsets.UTF_8)) {
					JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
					for (var entry : root.entrySet()) {
						Kit kit = readKit(entry.getKey(), entry.getValue().getAsJsonObject(), ops);
						kits.put(kit.name, kit);
					}
				}
			}
			if (Files.exists(claimsFile)) {
				try (Reader r = Files.newBufferedReader(claimsFile, StandardCharsets.UTF_8)) {
					JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
					for (var kitEntry : root.entrySet()) {
						Map<UUID, Long> map = new HashMap<>();
						for (var e : kitEntry.getValue().getAsJsonObject().entrySet()) {
							map.put(UUID.fromString(e.getKey()), e.getValue().getAsLong());
						}
						claims.put(kitEntry.getKey(), map);
					}
				}
			}
		} catch (Exception e) {
			CrateKit.LOGGER.error("Failed to load CrateKit data (kits are left untouched on disk)", e);
		}
		CrateKit.LOGGER.info("Loaded {} kit(s)", kits.size());
	}

	private Kit readKit(String name, JsonObject o, RegistryOps<JsonElement> ops) {
		Kit kit = new Kit(name);
		if (o.has("displayName")) kit.displayName = o.get("displayName").getAsString();
		if (o.has("alias") && !o.get("alias").isJsonNull()) kit.alias = o.get("alias").getAsString();
		if (o.has("cooldownSeconds")) kit.cooldownSeconds = o.get("cooldownSeconds").getAsLong();
		if (o.has("icon")) {
			kit.icon = ItemStack.CODEC.parse(ops, o.get("icon")).resultOrPartial(err -> CrateKit.LOGGER.warn("Bad icon in kit {}: {}", name, err)).orElse(ItemStack.EMPTY);
		}
		if (o.has("items")) {
			for (JsonElement el : o.getAsJsonArray("items")) {
				ItemStack.CODEC.parse(ops, el)
					.resultOrPartial(err -> CrateKit.LOGGER.warn("Skipping unreadable item in kit {}: {}", name, err))
					.ifPresent(kit.items::add);
			}
		}
		return kit;
	}

	public void save() {
		if (server == null) {
			return;
		}
		try {
			Files.createDirectories(dir);
			RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			JsonObject root = new JsonObject();
			for (Kit kit : kits.values()) {
				JsonObject o = new JsonObject();
				o.addProperty("displayName", kit.displayName);
				if (kit.alias != null) o.addProperty("alias", kit.alias);
				o.addProperty("cooldownSeconds", kit.cooldownSeconds);
				if (!kit.icon.isEmpty()) {
					o.add("icon", ItemStack.CODEC.encodeStart(ops, kit.icon).getOrThrow());
				}
				JsonArray items = new JsonArray();
				for (ItemStack stack : kit.items) {
					items.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
				}
				o.add("items", items);
				root.add(kit.name, o);
			}
			write(kitsFile, root);
		} catch (Exception e) {
			CrateKit.LOGGER.error("Failed to save kits", e);
		}
	}

	public void saveClaimsIfDirty() {
		if (!claimsDirty) {
			return;
		}
		claimsDirty = false;
		JsonObject root = new JsonObject();
		claims.forEach((kit, map) -> {
			JsonObject o = new JsonObject();
			map.forEach((uuid, time) -> o.addProperty(uuid.toString(), time));
			root.add(kit, o);
		});
		try {
			write(claimsFile, root);
		} catch (IOException e) {
			CrateKit.LOGGER.error("Failed to save claim history", e);
		}
	}

	private void write(Path file, JsonObject root) throws IOException {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(root, w);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
