package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
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
 * thread. The same three steps keep a village to its fishermen ({@link FishermanCap}, ROADMAP 26.13).
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
	/** How often a jobless villager walking to a barrel checks the village still has room for a fisherman, in ticks. */
	private static final int FISHERMEN_EVERY = 10;

	private WorkerLimits() {
	}

	/** From the villager's server tick. */
	public static void tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (villager.tickCount == 1) {
			firstLook(level, villager);
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
		boolean fishermen = FishermanCap.ENABLED && (villager.tickCount + villager.getId()) % FISHERMEN_EVERY == 0;
		if ((MAX_PER_VILLAGE <= 0 && !fishermen) || villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
			return;
		}
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
		if (site == null || site.dimension() != level.dimension()) {
			return;
		}
		boolean letGo = MAX_PER_VILLAGE > 0 && full(level, site, true);
		if (!letGo && fishermen) {
			// a barrel picked while the village had room for a fisherman, and someone took the last place on the way
			letGo = level.getPoiManager().getType(site.pos()).map(FishermanCap::barrel).orElse(false) && FishermanCap.full(level, site.pos());
		}
		if (!letGo) {
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
		if (villager != null && io.github.jcondedata.aliveworkplace.people.Retirement.isRetired(villager)) {
			return found.filter(p -> !JobSiteTickets.workstation(p.getFirst())); // a retired elder looks for no workstation (34.20)
		}
		if (!io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.ENABLED && villager != null
			&& villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
			// config mechanics off (39.2): a jobless villager doesn't see Create's toolboxes as workstations
			found = found.filter(p -> !p.getFirst().is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TOOLBOX_POI));
		}
		if (!io.github.jcondedata.aliveworkplace.city.CaravanTrains.ENABLED && villager != null
			&& villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
			// config villageTrains off (39.7d): a jobless villager doesn't see Create's stations as workstations
			found = found.filter(p -> !p.getFirst().is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRACK_STATION_POI));
		}
		if (!io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork.ENABLED && villager != null
			&& villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
			// config furnicrafters off (38.12): a jobless villager doesn't see CobbleFurnies' Furnicrafter as a workstation
			found = found.filter(p -> !p.getFirst().is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.FURNICRAFTER_POI));
		}
		if (!io.github.jcondedata.aliveworkplace.move.MoverWork.ENABLED && villager != null
			&& villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
			// config movers off (45.3): a jobless villager doesn't see the Mover's Crate as a workstation
			found = found.filter(p -> !p.getFirst().is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.MOVERS_CRATE_POI));
		}
		boolean workers = MAX_PER_VILLAGE > 0;
		boolean fishermen = FishermanCap.ENABLED;
		if ((!workers && !fishermen) || villager == null || !(villager.level() instanceof ServerLevel level)
			|| villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
			return found;
		}
		// Barrels are many (storage in every build): the fishermen are counted once per chunk section, not per barrel
		java.util.Map<Long, Boolean> fishermenFull = new java.util.HashMap<>();
		// One walk worked out per block and search: whether this villager can get to it (canTake)
		java.util.Map<BlockPos, Boolean> walks = new java.util.HashMap<>();
		return found.filter(p -> {
			if (!JobSiteTickets.workstation(p.getFirst())) {
				return true;
			}
			if (workers && full(level, GlobalPos.of(level.dimension(), p.getSecond()), false)) {
				return false;
			}
			if (fishermen && fishermansOven(level, p.getSecond())
				&& (keepsToHisBarrel(level, villager, p.getSecond(), walks) || nearerAFreeBarrel(level, villager, p.getSecond(), walks))) {
				return false; // B1496: the hut's smoker is its Fisherman's, for a villager by the hut's free barrel
			}
			return !fishermen || !FishermanCap.barrel(p.getFirst())
				|| (!fishermenFull.computeIfAbsent(net.minecraft.core.SectionPos.asLong(p.getSecond()), k -> FishermanCap.full(level, p.getSecond()))
					&& !storeOfAFreeWorkstation(level, villager, p.getSecond(), walks));
		});
	}

	/**
	 * B1463: whether a free workstation of another job stands within supply reach of the barrel at {@code barrel} (8
	 * across, 4 up or down: {@code SupplyContainers.inReach}, the reach in which that job block reads it as its store).
	 * Workshops keep their stock in barrels (the Supply Shop's six beside its Shop Counter), and a barrel is the vanilla
	 * Fisherman's workstation: while the village had room for a fisherman, a jobless villager walking into the shop took a
	 * stock barrel and turned fisherman instead of minding the counter. While the workshop's own job block is free its
	 * barrels are its stores, not a workstation; once it is taken they count again (for the cap as before).
	 * <p>Only a workstation a jobless villager could take instead counts (B1476, B1477): not one taken only by an item
	 * (a crafting table, a jukebox, a beehive or bee nest, a mailbox: {@link Stations.Station#byItself}) or whose job this
	 * game doesn't have ({@link Stations#takenByItself}), nor one the searching villager can't take ({@link #canTake}:
	 * nowhere beside or on top to stand, a counter walled in; or no way to walk there, a lectern in a sealed room, B1491),
	 * since those stay free forever and would hide the barrel forever; and
	 * not a smoker or furnace, which the Fisherman tends himself in his barrel's reach (fishers_hut.md, Level II), so the
	 * barrel there is the hut's job site, not another workshop's stock.
	 */
	static boolean storeOfAFreeWorkstation(ServerLevel level, Villager villager, BlockPos barrel, java.util.Map<BlockPos, Boolean> walks) {
		int reach = io.github.jcondedata.aliveworkplace.build.SupplyContainers.RADIUS;
		return level.getPoiManager().getInSquare(t -> JobSiteTickets.workstation(t) && !FishermanCap.barrel(t) && !offByConfig(t)
					&& takenByAJoblessVillager(t), barrel, reach, PoiManager.Occupancy.HAS_SPACE)
			.anyMatch(r -> io.github.jcondedata.aliveworkplace.build.SupplyContainers.inReach(r.getPos(), barrel)
				&& !fishermansOven(level, r.getPos()) && canTake(level, villager, r.getPos(), walks));
	}

	/** Whether a jobless villager takes a workstation of kind {@code type} by himself, with no item (see {@link Stations}). */
	private static boolean takenByAJoblessVillager(Holder<PoiType> type) {
		return Stations.takenByItself(type)
			&& Stations.ALL.stream().filter(s -> s.poi().test(type)).findFirst().map(Stations.Station::byItself).orElse(true);
	}

	/** A smoker or furnace: the Fisherman smokes his catch in those within his barrel's reach (fishers_hut.md). */
	private static boolean fishermansOven(ServerLevel level, BlockPos pos) {
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
		return state.is(net.minecraft.world.level.block.Blocks.SMOKER) || state.is(net.minecraft.world.level.block.Blocks.FURNACE);
	}

	/**
	 * B1496: whether {@code villager} stands nearer than the smoker or furnace at {@code oven} to a free barrel in that
	 * oven's reach which his search sees (the village has room for a fisherman, and the barrel isn't another workshop's
	 * stock) and which he can take ({@link #canTake}; B1523, B1525: a barrel boxed in, or one he can't walk to, hid the
	 * oven from him for good and left him jobless). Then the oven is the Fisherman's, the hut's (fishers_hut.md), and is left out of his search: vanilla's
	 * search gives up on every block it tried when one walk fails and retries each after its own random 40 to 80 ticks,
	 * so a villager beside the Fisher's Hut II barrel sometimes saw the smoker again before the barrel and turned Butcher.
	 */
	private static boolean nearerAFreeBarrel(ServerLevel level, Villager villager, BlockPos oven, java.util.Map<BlockPos, Boolean> walks) {
		BlockPos at = villager.blockPosition();
		double toOven = at.distSqr(oven);
		BlockPos barrel = level.getPoiManager().getInSquare(FishermanCap::barrel, oven, io.github.jcondedata.aliveworkplace.build.SupplyContainers.RADIUS, PoiManager.Occupancy.HAS_SPACE)
			.map(r -> r.getPos())
			.filter(b -> io.github.jcondedata.aliveworkplace.build.SupplyContainers.inReach(oven, b) && b.distSqr(at) < toOven)
			.filter(b -> !FishermanCap.full(level, b) && !storeOfAFreeWorkstation(level, villager, b, walks)
				&& canTake(level, villager, b, walks))
			.findFirst().orElse(null);
		if (barrel == null) {
			return false;
		}
		HIS_BARREL.put(villager, new HisBarrel(barrel.immutable(), level.getGameTime() + KEEPS_TO_IT));
		return true;
	}

	/** The free barrel a jobless villager was beside at a job search ({@link #nearerAFreeBarrel}), and until when he keeps to it. */
	private record HisBarrel(BlockPos barrel, long until) {
	}

	/** Not saved: a villager loaded again decides from where he stands. Only touched from the server thread. */
	private static final java.util.Map<Villager, HisBarrel> HIS_BARREL = new java.util.WeakHashMap<>();
	/** How long a villager keeps to the free barrel he was beside, wherever he strolls meanwhile: a minute. */
	private static final int KEEPS_TO_IT = 1200;

	/**
	 * B1556: whether the smoker or furnace at {@code oven} is in reach of the free barrel {@code villager} was beside at an
	 * earlier job search (or on his first tick, {@link #firstLook}). B1496's rule went by where he stands at each search,
	 * and he doesn't stand still: a first search that comes before he has landed (or mid-hop) finds no path, vanilla puts
	 * the barrel on a 40 to 80 tick retry, and meanwhile he strolls off. Traced on three failing runs: 2 to 7 blocks away
	 * at the next search (as far from the barrel as from the smoker, or the rule no longer holding from where he stood),
	 * he saw the smoker again and turned Butcher. So the barrel he was beside stays his for {@link #KEEPS_TO_IT} ticks, while it is still
	 * free, the village still has room for a fisherman and it hasn't become another workshop's stock; after that (a barrel
	 * walled in meanwhile) the rule goes by where he stands again, so the oven is never hidden for good.
	 */
	private static boolean keepsToHisBarrel(ServerLevel level, Villager villager, BlockPos oven, java.util.Map<BlockPos, Boolean> walks) {
		HisBarrel his = HIS_BARREL.get(villager);
		if (his == null) {
			return false;
		}
		BlockPos barrel = his.barrel();
		PoiManager pois = level.getPoiManager();
		if (level.getGameTime() > his.until() || !pois.getType(barrel).map(FishermanCap::barrel).orElse(false) || pois.getFreeTickets(barrel) <= 0
			|| FishermanCap.full(level, barrel) || storeOfAFreeWorkstation(level, villager, barrel, walks)) {
			HIS_BARREL.remove(villager);
			return false;
		}
		return io.github.jcondedata.aliveworkplace.build.SupplyContainers.inReach(oven, barrel);
	}

	/**
	 * B1556: a jobless villager's look round on his first tick (spawned, loaded, cured), as his first job search would see
	 * it from there, so a free barrel he starts beside is his ({@link #keepsToHisBarrel}) even when vanilla's first search
	 * comes up to a second later, a block or two into a stroll.
	 */
	private static void firstLook(ServerLevel level, Villager villager) {
		if (!FishermanCap.ENABLED || villager.isBaby() || villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
			return;
		}
		Villager before = thinking;
		thinking = villager;
		try {
			level.getPoiManager().findAllClosestFirstWithType(t -> t.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE), p -> true,
				villager.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE).limit(5L).forEach(p -> { });
		} finally {
			thinking = before;
		}
	}

	/**
	 * Whether {@code villager} can take the free workstation at {@code pos}: a spot to stand in to take it ({@link
	 * #standingSpots}) which he can walk to ({@link #walkTo}). One he can never take is no reason to hide anything from
	 * him: it stays free for good (B1477, B1491, B1523, B1525). {@code walks} keeps the answers of one search.
	 */
	private static boolean canTake(ServerLevel level, Villager villager, BlockPos pos, java.util.Map<BlockPos, Boolean> walks) {
		Boolean known = walks.get(pos);
		if (known == null) {
			java.util.List<BlockPos> spots = standingSpots(level, pos);
			known = !spots.isEmpty() && walkTo(level, villager, spots);
			walks.put(pos.immutable(), known);
		}
		return known;
	}

	/**
	 * The spots a villager stands in to take the workstation at {@code pos}: on top of it or against one of its four sides
	 * (vanilla takes a workstation from a block at most 1 away, counted along the axes). A workstation with none never gets
	 * taken.
	 */
	private static java.util.List<BlockPos> standingSpots(ServerLevel level, BlockPos pos) {
		java.util.List<BlockPos> spots = new java.util.ArrayList<>(5);
		if (Walker.canStand(level, pos.above())) {
			spots.add(pos.above());
		}
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (Walker.canStand(level, pos.relative(side))) {
				spots.add(pos.relative(side));
			}
		}
		return spots;
	}

	/** How many standing spots the walk check looks at before it gives up and answers yes ({@link #walkTo}). */
	private static final int WALK_SPOTS = 1024;
	/** How far off the straight line from the villager to the workstation the walk check follows a detour. */
	private static final int WALK_PAD = 5;

	/**
	 * Whether {@code villager} can walk from where he stands to one of {@code spots}: a flood fill over the blocks he can
	 * stand on, a step up of one and a drop of up to three at a time, through doors and gates (he opens those), inside a box
	 * round him and the spots with {@link #WALK_PAD} blocks of room for a detour. It answers yes when it can't tell (he is
	 * in the air mid-hop, or the way round is wider than the box or {@link #WALK_SPOTS} spots), so a village is never read
	 * as more shut-in than it is. Vanilla's own search asks its pathfinder the same question before it lets him claim a
	 * workstation; asking the navigation here instead gave the villager's own half-walked path back and flaked.
	 */
	private static boolean walkTo(ServerLevel level, Villager villager, java.util.List<BlockPos> spots) {
		BlockPos start = standingAt(level, villager);
		if (start == null) {
			return true;
		}
		java.util.Set<BlockPos> goals = new java.util.HashSet<>(spots);
		if (goals.contains(start)) {
			return true;
		}
		int minX = start.getX();
		int maxX = start.getX();
		int minY = start.getY();
		int maxY = start.getY();
		int minZ = start.getZ();
		int maxZ = start.getZ();
		for (BlockPos goal : goals) {
			minX = Math.min(minX, goal.getX());
			maxX = Math.max(maxX, goal.getX());
			minY = Math.min(minY, goal.getY());
			maxY = Math.max(maxY, goal.getY());
			minZ = Math.min(minZ, goal.getZ());
			maxZ = Math.max(maxZ, goal.getZ());
		}
		net.minecraft.world.level.levelgen.structure.BoundingBox box = new net.minecraft.world.level.levelgen.structure.BoundingBox(
			minX - WALK_PAD, minY - WALK_PAD, minZ - WALK_PAD, maxX + WALK_PAD, maxY + WALK_PAD, maxZ + WALK_PAD);
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty()) {
			if (seen.size() > WALK_SPOTS) {
				return true; // too wide a way round to work out here: as before the check
			}
			BlockPos at = queue.poll();
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				BlockPos step = stepTo(level, at.relative(side));
				if (step == null || !box.isInside(step) || !seen.add(step)) {
					continue;
				}
				if (goals.contains(step)) {
					return true;
				}
				queue.add(step);
			}
		}
		return false;
	}

	/** Where {@code villager} stands for {@link #walkTo}, or null when he is in no spot of its own (in the air mid-hop). */
	@Nullable
	private static BlockPos standingAt(ServerLevel level, Villager villager) {
		BlockPos at = villager.blockPosition();
		for (BlockPos p : new BlockPos[] {at, at.below(), at.above()}) {
			if (walkable(level, p)) {
				return p;
			}
		}
		return null;
	}

	/** The spot a step to {@code side} lands in: the same height, one up, or up to three down, or null for a wall. */
	@Nullable
	private static BlockPos stepTo(ServerLevel level, BlockPos side) {
		for (int dy = 1; dy >= -3; dy--) {
			BlockPos p = side.above(dy);
			if (walkable(level, p)) {
				return p;
			}
		}
		return null;
	}

	/** A spot a villager walks through: one he can stand in, or a closed door or gate on solid ground (he opens those). */
	private static boolean walkable(ServerLevel level, BlockPos feet) {
		if (Walker.canStand(level, feet)) {
			return true;
		}
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(feet);
		return (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
				|| state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock)
			&& Walker.isGround(level, feet.below(), level.getBlockState(feet.below()));
	}

	/** Workstations a jobless villager's search leaves out while their job's config switch is off (see {@link #hideFull}). */
	private static boolean offByConfig(Holder<PoiType> type) {
		return (!io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.ENABLED && type.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TOOLBOX_POI))
			|| (!io.github.jcondedata.aliveworkplace.city.CaravanTrains.ENABLED && type.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.TRACK_STATION_POI))
			|| (!io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork.ENABLED && type.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.FURNICRAFTER_POI))
			|| (!io.github.jcondedata.aliveworkplace.move.MoverWork.ENABLED && type.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.MOVERS_CRATE_POI));
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
		if (io.github.jcondedata.aliveworkplace.people.Retirement.refuses(villager, data)) { // a retired elder never works again (34.20)
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
			villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			return true;
		}
		boolean fishermen = FishermanCap.ENABLED && FishermanCap.capped(data.getProfession());
		if ((MAX_PER_VILLAGE <= 0 && !fishermen) || villager == employing || !(villager.level() instanceof ServerLevel level)
			|| villager.getVillagerData().getProfession() != VillagerProfession.NONE
			|| data.getProfession() == VillagerProfession.NONE || data.getProfession() == VillagerProfession.NITWIT) {
			return false;
		}
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (site == null || site.dimension() != level.dimension()
			|| !((MAX_PER_VILLAGE > 0 && full(level, site, true)) || (fishermen && FishermanCap.full(level, site.pos())))) {
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
