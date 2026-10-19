package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * The name plate over a working villager the player looks at (B221): "Fisherman, Journeyman", or
 * "Dara — Fisherman, Journeyman" for a villager with a name of its own. Every job shows, ours and vanilla's; a jobless
 * villager or a nitwit shows nothing new. Composed on the client at draw time from the villager data vanilla already
 * syncs, so no name is ever saved or overwritten.
 */
public final class JobPlate {
	/** The job and level ("Fisherman, Journeyman"), or null for a villager with no job (none, nitwit). */
	public static @Nullable Component job(Villager villager) {
		VillagerProfession profession = villager.getVillagerData().getProfession();
		if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
			return null;
		}
		return Component.translatable("message.aliveworkplace.plate.job", BuilderLevels.jobName(villager),
			BuilderLevels.levelName(BuilderLevels.level(villager)));
	}

	/** Whether a villager the player looks at gets a plate it wouldn't get in vanilla (a working one without a name). */
	public static boolean shows(Entity entity) {
		return entity instanceof Villager villager && job(villager) != null;
	}

	/**
	 * What the plate reads, given the name vanilla would draw: the job and level for an unnamed worker (vanilla's name is
	 * only its job), the name followed by them for a named one, and {@code name} unchanged for anyone else.
	 */
	public static Component plate(Entity entity, Component name) {
		if (!(entity instanceof Villager villager)) {
			return name;
		}
		Component job = job(villager);
		if (job == null) {
			return name;
		}
		return villager.hasCustomName() ? Component.translatable("message.aliveworkplace.plate.named", name, job) : job;
	}

	private JobPlate() {
	}
}
