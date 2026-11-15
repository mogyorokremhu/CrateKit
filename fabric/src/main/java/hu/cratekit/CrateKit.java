package hu.cratekit;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrateKit implements ModInitializer {
	public static final String MOD_ID = "cratekit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final KitManager KITS = new KitManager();

	public static KitManager kits() {
		return KITS;
	}

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> KitCommands.register(dispatcher));
		ChatPrompt.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			KITS.load(server);
			KITS.onDispatcherRebuilt();
		});
		// /reload rebuilds the command dispatcher, so custom kit commands have to be added again.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> KITS.onDispatcherRebuilt());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			KITS.save();
			KITS.saveClaimsIfDirty();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 600 == 0) {
				KITS.saveClaimsIfDirty();
			}
		});
		LOGGER.info("CrateKit loaded. Use /kit to get started.");
	}
}
