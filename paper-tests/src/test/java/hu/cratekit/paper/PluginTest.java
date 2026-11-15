package hu.cratekit.paper;

import hu.cratekit.common.Durations;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
	private ServerMock server;
	private CrateKitPlugin plugin;
	private PlayerMock admin;
	private PlayerMock steve;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.load(CrateKitPlugin.class);
		admin = server.addPlayer("Admin");
		admin.setOp(true);
		steve = server.addPlayer("Steve");
		server.getScheduler().performOneTick();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	private int count(PlayerMock p, Material m) {
		int n = 0;
		for (ItemStack s : p.getInventory().getContents()) {
			if (s != null && s.getType() == m) n += s.getAmount();
		}
		return n;
	}

	private void makeStarterKit() {
		admin.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.BREAD, 16));
		admin.performCommand("kit create starter");
	}

	@Test
	void durationsParse() {
		assertEquals(1800, Durations.parse("30m"));
		assertEquals(86400 + 12 * 3600, Durations.parse("1d 12h"));
		assertEquals(9000, Durations.parse("2h30m"));
		assertEquals(0, Durations.parse("none"));
		assertEquals(Durations.ONCE, Durations.parse("once"));
		assertThrows(IllegalArgumentException.class, () -> Durations.parse("banana"));
		assertThrows(IllegalArgumentException.class, () -> Durations.parse("5x"));
		assertEquals("1d 2h 3m", Durations.format(86400 + 2 * 3600 + 3 * 60));
	}

	@Test
	void createAndClaimWithDefaultCommand() {
		makeStarterKit();
		Kit kit = plugin.kits().get("starter");
		assertNotNull(kit, "kit should exist after /kit create");
		assertEquals(2, kit.items.size());

		steve.performCommand("kit claim starter");
		assertEquals(1, count(steve, Material.DIAMOND_SWORD));
		assertEquals(16, count(steve, Material.BREAD));
	}

	@Test
	void nonAdminCannotCreate() {
		steve.getInventory().addItem(new ItemStack(Material.STONE));
		steve.performCommand("kit create hack");
		assertNull(plugin.kits().get("hack"));
	}

	@Test
	void cooldownBlocksSecondClaim() {
		makeStarterKit();
		admin.performCommand("kit setcooldown starter 1h");
		assertEquals(3600, plugin.kits().get("starter").cooldownSeconds);

		steve.performCommand("kit claim starter");
		steve.performCommand("kit claim starter");
		assertEquals(16, count(steve, Material.BREAD), "second claim inside the cooldown must give nothing");

		plugin.kits().resetCooldown(plugin.kits().get("starter"), steve.getUniqueId());
		steve.performCommand("kit claim starter");
		assertEquals(32, count(steve, Material.BREAD));
	}

	@Test
	void onceKitOnlyClaimedOnce() {
		makeStarterKit();
		admin.performCommand("kit setcooldown starter once");
		steve.performCommand("kit claim starter");
		steve.performCommand("kit claim starter");
		assertEquals(16, count(steve, Material.BREAD));
	}

	@Test
	void customCommandClaimsKit() {
		makeStarterKit();
		admin.performCommand("kit setcommand starter freestuff");
		assertEquals("freestuff", plugin.kits().get("starter").alias);

		steve.performCommand("freestuff");
		assertEquals(1, count(steve, Material.DIAMOND_SWORD));

		// changing the command removes the old one
		admin.performCommand("kit setcommand starter other");
		assertFalse(steve.performCommand("freestuff"), "old command should be gone");
		steve.performCommand("other");
		assertEquals(2, count(steve, Material.DIAMOND_SWORD) + 0 * 1 - 0, "new command should work (cooldown is none)");
	}

	@Test
	void cannotStealExistingCommand() {
		makeStarterKit();
		server.getCommandMap().register("other", new org.bukkit.command.Command("taken") {
			@Override
			public boolean execute(org.bukkit.command.CommandSender s, String l, String[] a) {
				return true;
			}
		});
		admin.performCommand("kit setcommand starter taken");
		assertNull(plugin.kits().get("starter").alias, "must not override an existing command");
	}

	@Test
	void deleteRemovesKitAndCommand() {
		makeStarterKit();
		admin.performCommand("kit setcommand starter freestuff");
		admin.performCommand("kit delete starter");
		assertNull(plugin.kits().get("starter"));
		assertFalse(steve.performCommand("freestuff"));
	}

	@Test
	void menuClickClaimsAndCannotTakeItems() {
		makeStarterKit();
		steve.performCommand("kit");
		server.getScheduler().performOneTick();
		assertTrue(steve.getOpenInventory().getTopInventory().getHolder() instanceof KitMenu, "menu should be open");

		// slot 0 is the first kit
		steve.simulateInventoryClick(0);
		assertEquals(16, count(steve, Material.BREAD), "left click in the menu claims the kit");
		assertTrue(steve.getItemOnCursor().getType().isAir(), "nothing may be picked up from the menu");
	}

	@Test
	void editorDoesNotConsumeRealItems() {
		makeStarterKit();
		Kit kit = plugin.kits().get("starter");
		admin.getInventory().clear();
		admin.getInventory().addItem(new ItemStack(Material.GOLD_INGOT, 5));

		new KitContentsEditor(plugin, admin, kit).open();
		server.getScheduler().performOneTick();
		int sizeBefore = kit.items.size();
		// click the gold ingots in the player's own inventory (raw slot after the 54 top slots)
		// MockBukkit numbers the player's slots differently from a real server, so look for the slot that reports our item.
		for (int raw = 54; raw < 54 + 36 && kit.items.size() == sizeBefore; raw++) {
			admin.simulateInventoryClick(admin.getOpenInventory(), org.bukkit.event.inventory.ClickType.LEFT, raw);
		}
		assertEquals(sizeBefore + 1, kit.items.size(), "clicking own item adds a copy to the kit");
		assertEquals(5, count(admin, Material.GOLD_INGOT), "the real items must stay in the inventory");
	}

	@Test
	void kitsSurviveRestart() {
		makeStarterKit();
		admin.performCommand("kit setcooldown starter 2h");
		admin.performCommand("kit setcommand starter freestuff");
		plugin.kits().save();
		plugin.kits().load();
		Kit kit = plugin.kits().get("starter");
		assertNotNull(kit);
		assertEquals(2, kit.items.size());
		assertEquals(7200, kit.cooldownSeconds);
		assertEquals("freestuff", kit.alias);
	}

	@Test
	void armorGetsEquipped() {
		admin.getInventory().clear();
		admin.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
		admin.performCommand("kit create gear");
		steve.performCommand("kit claim gear");
		assertNotNull(steve.getInventory().getHelmet());
		assertEquals(Material.IRON_HELMET, steve.getInventory().getHelmet().getType());
	}
}
