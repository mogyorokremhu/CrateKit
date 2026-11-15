package hu.cratekit.paper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/** A custom claim command such as /starter, created at runtime for one kit. */
final class AliasCommand extends Command {
	private final CrateKitPlugin plugin;

	AliasCommand(CrateKitPlugin plugin, String alias) {
		super(alias);
		this.plugin = plugin;
		setDescription("Claim a kit");
		setPermission("cratekit.use");
	}

	@Override
	public boolean execute(CommandSender sender, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("Only players can claim kits.");
			return true;
		}
		Kit kit = plugin.kits().byAlias(getName());
		if (kit == null) {
			Msg.send(sender, "&cThat kit no longer exists.");
			return true;
		}
		KitCommand.claimAndReport(plugin, (Player) sender, kit);
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
		return Collections.emptyList();
	}
}
