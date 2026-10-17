package io.github.jcondedata.aliveworkplace.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 26.13: a cap on fishermen per village. A barrel is the Fisherman's workstation, and builds are full of storage
 * barrels, so without it most of a village's jobless villagers turned fisherman. A village takes {@link #BASE}
 * fishermen, plus one more for every {@link #PER_VILLAGERS} villagers in it; the village is every villager within
 * {@link #RADIUS} blocks of the barrel (the config's {@code villageRadius}, as for the worker cap).
 * <p>
 * Once a village has its fishermen, a jobless villager's job search doesn't see free barrels there, so he takes another
 * free workstation instead ({@code WorkerLimits.hideFull}); a barrel picked anyway is let go on the way
 * ({@code WorkerLimits.holdBack}) and the job is refused at the last moment ({@code WorkerLimits.refuse}). A fisherman
 * the village already has keeps his job when it is over the cap (nothing is saved, nobody is fired), and a job given by
 * order (the hall's job menu, the admin tools) isn't capped.
 */
public final class FishermanCap {
	/** The config's {@code fishermanCap}: off, villages take as many fishermen as they have free barrels (vanilla). */
	public static boolean ENABLED = true;
	/** Fishermen every village may have: the config's {@code fishermenPerVillage}. */
	public static int BASE = 1;
	/** One more fisherman for every this many villagers: the config's {@code villagersPerFisherman}. */
	public static int PER_VILLAGERS = 10;
	/** How far from a barrel its village's villagers are counted: the config's {@code villageRadius}. */
	public static int RADIUS = 48;

	private FishermanCap() {
	}

	/** How many fishermen a village of {@code villagers} villagers takes. */
	public static int cap(int villagers) {
		return Math.max(0, BASE) + Math.max(0, villagers) / Math.max(1, PER_VILLAGERS);
	}

	/** Whether {@code type} is the Fisherman's workstation (a barrel, or a modded block filed as one). */
	public static boolean barrel(Holder<PoiType> type) {
		return type.is(PoiTypes.FISHERMAN);
	}

	/** Whether {@code job} is the job this cap is about. */
	public static boolean capped(VillagerProfession job) {
		return job == VillagerProfession.FISHERMAN;
	}

	/** Whether the village around {@code site} has all the fishermen it takes (false when the cap is off). */
	public static boolean full(ServerLevel level, BlockPos site) {
		if (!ENABLED) {
			return false;
		}
		int r = Math.max(1, RADIUS);
		double reach = (double) r * r;
		int villagers = 0;
		int fishermen = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(site).inflate(r), Villager::isAlive)) {
			if (v.distanceToSqr(site.getX() + 0.5, site.getY() + 0.5, site.getZ() + 0.5) > reach) {
				continue;
			}
			villagers++;
			if (capped(v.getVillagerData().getProfession())) {
				fishermen++;
			}
		}
		return fishermen >= cap(villagers);
	}
}
