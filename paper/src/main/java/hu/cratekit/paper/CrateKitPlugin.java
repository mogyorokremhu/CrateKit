package hu.cratekit.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** CrateKit for Paper, Spigot and Purpur. */
public class CrateKitPlugin extends JavaPlugin {
	private KitManager kits;
	private ChatPrompt prompt;

	public KitManager kits() {
		return kits;
	}

	public ChatPrompt prompt() {
		return prompt;
	}

	@Override
	public void onEnable() {
		kits = new KitManager(this);
		prompt = new ChatPrompt(this);
		kits.load();

		KitCommand executor = new KitCommand(this);
		PluginCommand command = getCommand("kit");
		if (command != null) {
			command.setExecutor(executor);
			command.setTabCompleter(executor);
		}
		Bukkit.getPluginManager().registerEvents(new Gui.Listener_(), this);
		Bukkit.getPluginManager().registerEvents(prompt, this);

		// Custom claim commands: register once the server has finished starting so we see every other plugin's commands.
		Bukkit.getScheduler().runTask(this, kits::syncAliases);
		// Persist claim history regularly (every 30 seconds).
		Bukkit.getScheduler().runTaskTimer(this, kits::saveClaimsIfDirty, 600L, 600L);
		getLogger().info("CrateKit enabled. Use /kit to get started.");
	}

	@Override
	public void onDisable() {
		if (kits != null) {
			kits.save();
			kits.saveClaimsIfDirty();
			kits.unregisterAllAliases();
		}
	}
}
