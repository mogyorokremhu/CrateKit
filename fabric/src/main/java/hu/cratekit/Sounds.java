package hu.cratekit;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/** Plays a sound to one player only (ServerPlayerEntity#playSound would play it to everyone nearby except that player). */
public final class Sounds {
	private Sounds() {
	}

	public static void play(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(sound), SoundCategory.MASTER,
			player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}
}
