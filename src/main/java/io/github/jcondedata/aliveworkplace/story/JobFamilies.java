package io.github.jcondedata.aliveworkplace.story;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * The twelve job families (docs/design/M31.md): the jobs that share tastes ({@code villager_tastes/<family>.json}),
 * a 4-heart event ({@code heart_events/work_<family>.json}) and a keepsake ({@link Keepsakes}). Fixed in code, by
 * profession id, so a job whose mod isn't installed is simply never met. A job that isn't listed (a later one of
 * ours, or another mod's) has no family and counts as {@link Family#NONE}.
 */
public final class JobFamilies {
	public enum Family {
		BUILDING("aliveworkplace:builder", "aliveworkplace:carpenter", "minecraft:mason", "aliveworkplace:tinkerer", "minecraft:leatherworker"),
		MINING("aliveworkplace:miner", "minecraft:armorer", "minecraft:toolsmith", "minecraft:weaponsmith", "aliveworkplace:sifter",
			"aliveworkplace:netherworker"),
		LAND("aliveworkplace:lumberjack", "aliveworkplace:orchard_keeper", "minecraft:farmer", "aliveworkplace:florist", "aliveworkplace:beekeeper",
			"aliveworkplace:composter"),
		ANIMALS("minecraft:shepherd", "minecraft:butcher", "aliveworkplace:rancher", "minecraft:fisherman"),
		KITCHEN("aliveworkplace:chef"),
		LEARNING("aliveworkplace:scholar", "aliveworkplace:teacher", "minecraft:librarian", "minecraft:cartographer"),
		HEALING("aliveworkplace:nurse", "minecraft:cleric", "aliveworkplace:undertaker"),
		ARMS("aliveworkplace:guard", "minecraft:fletcher"),
		TRADE("aliveworkplace:shopkeeper", "aliveworkplace:innkeeper", "aliveworkplace:ferryman", "aliveworkplace:postman", "aliveworkplace:porter"),
		MUSIC("aliveworkplace:bard"),
		POKEMON("aliveworkplace:trainer", "aliveworkplace:trainer_leader", "aliveworkplace:tutor", "aliveworkplace:ball_smith",
			"aliveworkplace:pokemon_trader", "aliveworkplace:fossil_scientist"),
		/** The jobless and nitwits, and every job no family lists. */
		NONE("minecraft:none", "minecraft:nitwit");

		/** The family's jobs, by profession id. */
		public final List<ResourceLocation> jobs;

		Family(String... jobs) {
			this.jobs = java.util.Arrays.stream(jobs).map(ResourceLocation::parse).toList();
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final Map<ResourceLocation, Family> BY_JOB = new LinkedHashMap<>();

	static {
		for (Family family : Family.values()) {
			for (ResourceLocation job : family.jobs) {
				BY_JOB.put(job, family);
			}
		}
	}

	/** The family of the job with this profession id; {@link Family#NONE} for one no family lists. */
	public static Family of(ResourceLocation profession) {
		return BY_JOB.getOrDefault(profession, Family.NONE);
	}

	/** {@code villager}'s job family. */
	public static Family of(Villager villager) {
		return of(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
	}

	private JobFamilies() {
	}
}
