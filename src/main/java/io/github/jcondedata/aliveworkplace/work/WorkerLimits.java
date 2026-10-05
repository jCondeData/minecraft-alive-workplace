package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import com.mojang.datafixers.util.Pair;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * The server owner's caps on workers (ROADMAP 25.5, {@code config/aliveworkplace.json}):
 * <ul>
 *   <li>{@link #MAX_PER_VILLAGE}: a jobless villager doesn't take a free workstation when that many workstations within
 *   {@link #RADIUS} blocks of it are already taken. Their job search doesn't see such workstations ({@link #hideFull});
 *   one picked anyway is let go ({@link #holdBack}), and the job is refused at the last moment ({@link #refuse}). Jobs
 *   given by order ({@link #order}) aren't capped, and nobody who has a job loses it when the cap is lowered.</li>
 *   <li>{@link #PATH_RANGE}: how far a villager with a job looks for a path in one go (vanilla's 48 blocks). Path searches
 *   are most of what villagers cost; a worker whose target is farther walks there in legs, one search at a time.</li>
 * </ul>
 * Both are checked from the villager's own tick (the range once a second), so nothing touches the world from another
 * thread.
 */
public final class WorkerLimits {
	/** Workers per village (taken workstations within {@link #RADIUS} of each other); 0: no cap. */
	public static int MAX_PER_VILLAGE = 0;
	/** How far apart the workstations of one village may be, for the cap: the config's {@code villageRadius}. */
	public static int RADIUS = 48;
	/** How far a villager with a job paths in one go, in blocks: vanilla's follow range is 48. */
	public static int PATH_RANGE = 48;
	static final ResourceLocation RANGE_ID = AliveWorkplace.id("worker_path_range");
	private static final int CHECK_EVERY = 20;

	private WorkerLimits() {
	}

	/** From the villager's server tick. */
	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		// every tick, before the brain's: a villager next to the workstation it picked takes it on the brain's next tick
		holdBack(level, villager);
		if ((villager.tickCount + villager.getId()) % CHECK_EVERY == 0) {
			pathRange(villager);
		}
	}

	/** Puts {@link #PATH_RANGE} on a villager with a job (a modifier that isn't saved), and takes it off everyone else. */
	static void pathRange(Villager villager) {
		AttributeInstance range = villager.getAttribute(Attributes.FOLLOW_RANGE);
		if (range == null) {
			return;
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		boolean employed = !villager.isBaby() && job != VillagerProfession.NONE && job != VillagerProfession.NITWIT;
		double change = PATH_RANGE - range.getBaseValue();
		if (!employed || change == 0) {
			range.removeModifier(RANGE_ID);
			return;
		}
		AttributeModifier current = range.getModifier(RANGE_ID);
		if (current == null || current.amount() != change) {
			range.addOrUpdateTransientModifier(new AttributeModifier(RANGE_ID, change, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/**
	 * A jobless villager who has picked a free workstation (and is walking to it) lets it go when its village is full.
	 * Vanilla takes a ticket on the workstation when the villager picks it, so the count includes this villager's own
	 * pick: the village is full when more than {@link #MAX_PER_VILLAGE} are taken.
	 */
	static void holdBack(ServerLevel level, Villager villager) {
		if (MAX_PER_VILLAGE <= 0 || villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
			return;
		}
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
		if (site == null || site.dimension() != level.dimension() || !full(level, site, true)) {
			return;
		}
		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	/** The villager whose brain is running right now (on the server thread), or null. */
	@Nullable
	private static Villager thinking;

	/** From the villager's tick: whose brain runs now (null when it's done). */
	public static void thinking(@Nullable Villager villager) {
		thinking = villager;
	}

	/** The villager whose brain runs now, or null (Curfew's schedule check, 30.9). */
	@Nullable
	public static Villager thinker() {
		return thinking;
	}

	/**
	 * A jobless villager's search for a free workstation (vanilla's): the free ones in a full village are left out, so the
	 * villager doesn't walk to one (or even look for a path) only to let it go.
	 */
	public static Stream<Pair<Holder<PoiType>, BlockPos>> hideFull(Stream<Pair<Holder<PoiType>, BlockPos>> found) {
		Villager villager = thinking;
		if (MAX_PER_VILLAGE <= 0 || villager == null || !(villager.level() instanceof ServerLevel level)
			|| villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
			return found;
		}
		return found.filter(p -> !JobSiteTickets.workstation(p.getFirst()) || !full(level, GlobalPos.of(level.dimension(), p.getSecond()), false));
	}

	/** Whether {@code villager} is being given a job by order right now (not taking one by themselves). */
	public static boolean ordering(Villager villager) {
		return villager == employing;
	}

	/** The villager being given a job by order right now (see {@link #order}). */
	@Nullable
	private static Villager employing;

	/**
	 * Gives {@code villager} a job by order (a player at the hall, a hired mercenary, an admin tool): the cap is for
	 * villagers taking free workstations on their own, so it doesn't apply.
	 */
	public static void order(Villager villager, VillagerProfession job) {
		Villager before = employing;
		employing = villager;
		try {
			villager.setVillagerData(villager.getVillagerData().setProfession(job));
		} finally {
			employing = before;
		}
	}

	/**
	 * Whether {@code villager} may not take the job in {@code data}: a jobless villager taking a workstation (vanilla
	 * gives the job when they reach the one they picked) in a full village. They let it go and stay jobless.
	 */
	public static boolean refuse(Villager villager, VillagerData data) {
		if (MAX_PER_VILLAGE <= 0 || villager == employing || !(villager.level() instanceof ServerLevel level)
			|| villager.getVillagerData().getProfession() != VillagerProfession.NONE
			|| data.getProfession() == VillagerProfession.NONE || data.getProfession() == VillagerProfession.NITWIT) {
			return false;
		}
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (site == null || site.dimension() != level.dimension() || !full(level, site, true)) {
			return false;
		}
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		return true;
	}

	/**
	 * Whether the village around {@code site} has its {@link #MAX_PER_VILLAGE} workers; {@code picked}: the site's own
	 * ticket is the asking villager's and doesn't count.
	 */
	public static boolean full(ServerLevel level, GlobalPos site, boolean picked) {
		if (MAX_PER_VILLAGE <= 0) {
			return false;
		}
		long taken = level.getPoiManager().getCountInRange(JobSiteTickets::workstation, site.pos(), Math.max(1, RADIUS),
			PoiManager.Occupancy.IS_OCCUPIED);
		return taken - (picked ? 1 : 0) >= MAX_PER_VILLAGE;
	}
}
