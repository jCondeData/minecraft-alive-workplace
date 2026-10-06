package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * Higher jobs need higher classes (ROADMAP 34.8). The {@code jobs} list in each class file names the jobs a villager must
 * be that class or higher to take; a job no class names (every vanilla job among them) is open to everyone. The gate
 * applies only when a job is taken (picked with its item, the hall's free-workstation list, a grown child taking up a
 * parent's trade, the Steward's morning jobs): nobody is ever fired, and a worker below their job's class keeps it at the
 * usual pace, marked on the hall's people list. Nothing is gated with {@code villageClasses} off, outside a village with
 * a hall, or for a villager with no class yet (unseeded, until 34.22 seeds them). A Legend counts as the top class.
 */
public final class ClassJobs {

	/** The lowest class whose {@code jobs} name {@code job}, or null when it's open to all (or classes are off). */
	@Nullable
	public static SocialClass needed(ResourceLocation job) {
		if (!SocialClasses.ENABLED) {
			return null;
		}
		for (SocialClass c : SocialClasses.ladder()) { // lowest first
			if (c.jobs().contains(job)) {
				return c;
			}
		}
		return null;
	}

	@Nullable
	public static SocialClass needed(VillagerProfession job) {
		return needed(BuiltInRegistries.VILLAGER_PROFESSION.getKey(job));
	}

	/**
	 * Why {@code villager} may not take {@code job} in the village round {@code hall} ("Dara is a Peasant; a Scholar must
	 * be a Burgher"), or null when they may. {@code hall} null: no village with a hall, so nothing is gated.
	 */
	@Nullable
	public static Component refusal(@Nullable BlockPos hall, Villager villager, VillagerProfession job) {
		if (hall == null) {
			return null;
		}
		SocialClass need = needed(job);
		if (need == null) {
			return null;
		}
		SocialClass have = ClassPerks.classOf(villager);
		if (have == null || have.tier() >= need.tier()) {
			return null;
		}
		return Component.translatable("message.aliveworkplace.job.class_needed", villager.getDisplayName(), have.name(),
			Stations.name(job), need.name());
	}

	/** {@link #refusal} in whatever village {@code villager} stands in (the nearest hall's). */
	@Nullable
	public static Component refusal(ServerLevel level, Villager villager, VillagerProfession job) {
		if (!SocialClasses.ENABLED || needed(job) == null) {
			return null; // no hall lookup for an open job
		}
		return refusal(VillageHalls.nearest(level, villager.blockPosition()).orElse(null), villager, job);
	}

	/** Whether {@code villager} may take {@code job} in the village round {@code hall}. */
	public static boolean may(@Nullable BlockPos hall, Villager villager, VillagerProfession job) {
		return refusal(hall, villager, job) == null;
	}

	/**
	 * The class {@code villager}'s present job needs, when they're below it: a worker who had the job before the rule (or
	 * came down in the world since) and keeps it. Null when they're not below it.
	 */
	@Nullable
	public static SocialClass above(Villager villager) {
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT) {
			return null;
		}
		SocialClass need = needed(job);
		SocialClass have = need == null ? null : ClassPerks.classOf(villager);
		return have != null && have.tier() < need.tier() ? need : null;
	}

	/**
	 * The class a hired traveller arrives with, by their level: an Apprentice (or lower) a Peasant, a Journeyman an
	 * Artisan, an Expert (or higher) a Burgher; never above the third class (Nobles are earned). Null with no classes.
	 */
	@Nullable
	public static SocialClass travellerClass(int travellerLevel) {
		List<SocialClass> ladder = SocialClasses.ladder();
		if (!SocialClasses.ENABLED || ladder.isEmpty()) {
			return null;
		}
		int step = Math.max(0, Math.min(travellerLevel - 2, 2));
		return ladder.get(Math.min(step, ladder.size() - 1));
	}

	/** Gives a just-hired traveller the class of their level, if they're hired in a village with a hall. */
	public static void hired(ServerLevel level, Villager guest, int travellerLevel) {
		SocialClass c = travellerClass(travellerLevel);
		if (c != null && VillageHalls.nearest(level, guest.blockPosition()).isPresent()) {
			SocialClasses.seed(guest, c);
		}
	}

	private ClassJobs() {
	}
}
