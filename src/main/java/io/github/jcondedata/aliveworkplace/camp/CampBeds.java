package io.github.jcondedata.aliveworkplace.camp;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The bedrolls by a settlers' wagon (B206, B207): the Settler's Wagon's camp and the Colony Camp each bring a few beds,
 * which are for the road, not homes. Once a village is founded (a Village Hall within reach), its round takes them back
 * ({@link #settle}): a villager who kept one as their home lets it go and finds a bed in the village's houses, and nobody
 * can claim one again (the bed is held, the way {@code LegendGuests} holds a guest's bed). The hall doesn't count them
 * among its beds, and nobody moves in on one ({@link #freeHome}, {@link #claim}).
 *
 * <p>The beds of every camp placed since are on a list ({@code aliveworkplace_camp_beds}, per dimension); a camp placed
 * before the list (an older save) is known by its look ({@link #looksLikeCamp}): a brown or green bedroll with the camp's
 * campfire and hay bale close by and the camp's own Blueprint Table (or an old camp's Builder's Bench) beside its wagon.
 * Such a bed goes on the list once seen, so it stays a wagon bed when the campfire goes. Both camps' bedrolls are brown or
 * green, and only such a bed is ever a wagon bed.
 *
 * <p>B715: the look once asked only for the campfire and the hay bale, so a player's brown bed in a rustic cabin or by a
 * farmhouse's hay went on the list for good. Beds a list saved before then ({@code version} absent) were all listed that
 * way, placed camps' and mistaken ones alike, so each is looked at once more ({@link Data#unverified}): kept if it has the
 * camp's look, a home again if not.
 */
public final class CampBeds {
	/** How close the camp's campfire and hay bale are to its bedrolls in an older camp (both camps: within 10 blocks). */
	static final int LOOK = 10;
	/** How close the camp's Blueprint Table (or Builder's Bench) is to its bedrolls, across (the Colony Camp's: 11 blocks). */
	static final int TABLE = 12;

	private CampBeds() {
	}

	/** The wagon beds in one dimension. */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_camp_beds";
		/** The list's save format: 2 since B715 (before it, {@code beds} held every bed the old look ever took). */
		private static final int VERSION = 2;
		final LongOpenHashSet beds = new LongOpenHashSet();
		/** Beds an older list holds, not yet looked at again (B715): each is kept or let go the first time it is asked about. */
		final LongOpenHashSet unverified = new LongOpenHashSet();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		void add(BlockPos pos) {
			boolean changed = unverified.remove(pos.asLong());
			if (beds.add(pos.asLong()) || changed) {
				setDirty();
			}
		}

		void remove(BlockPos pos) {
			boolean listed = beds.remove(pos.asLong());
			if (unverified.remove(pos.asLong()) || listed) {
				setDirty();
			}
		}

		/** Takes every listed bed in {@code box} off the list (tests clean up after themselves). */
		public void forget(BoundingBox box) {
			boolean listed = beds.removeIf(l -> box.isInside(BlockPos.of(l)));
			if (unverified.removeIf(l -> box.isInside(BlockPos.of(l))) || listed) {
				setDirty();
			}
		}

		/** Whether an older list holds {@code pos} and it hasn't been looked at again yet (B715). */
		public boolean unverified(BlockPos pos) {
			return unverified.contains(pos.asLong());
		}

		public boolean has(BlockPos pos) {
			return beds.contains(pos.asLong());
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("version", VERSION);
			tag.putLongArray("beds", beds.toLongArray());
			if (!unverified.isEmpty()) {
				tag.putLongArray("unverified", unverified.toLongArray());
			}
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			boolean old = Nbt.getInt(tag, "version") < VERSION; // a list from before B715: its beds are looked at again
			for (long pos : Nbt.getLongArray(tag, "beds")) {
				(old ? data.unverified : data.beds).add(pos);
			}
			for (long pos : Nbt.getLongArray(tag, "unverified")) {
				if (!data.beds.contains(pos)) {
					data.unverified.add(pos);
				}
			}
			return data;
		}
	}

	/** Puts the beds (their heads, where a bed's place in the village is) of the camp just placed in {@code box} on the list. */
	public static int record(ServerLevel level, BoundingBox box) {
		Data data = Data.get(level);
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (isBedHead(level.getBlockState(p)) && isBedroll(level.getBlockState(p))) {
				data.add(p.immutable());
				n++;
			}
		}
		return n;
	}

	private static boolean isBedHead(BlockState state) {
		return state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	/** A camp's bedroll: both camps' beds are brown or green. */
	private static boolean isBedroll(BlockState state) {
		return state.is(Blocks.BROWN_BED) || state.is(Blocks.GREEN_BED);
	}

	/** Answers already worked out this tick (an older camp's look is a scan; the census asks for each bed twice). */
	private static final Long2BooleanOpenHashMap SEEN = new Long2BooleanOpenHashMap();
	@Nullable
	private static ServerLevel seenLevel;
	private static long seenTick = -1;

	/**
	 * Whether the bed at {@code pos} is a settlers' wagon bed: on the list, or one of an older camp's bedrolls. Either way
	 * it is a camp's brown or green bedroll (another bed put where one stood is a bed like any other).
	 */
	public static boolean isWagonBed(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !isBedroll(level.getBlockState(pos))) {
			return false;
		}
		Data data = Data.get(level);
		if (data.has(pos)) {
			return true;
		}
		if (!level.getServer().isSameThread()) {
			return looksLikeCamp(level, pos);
		}
		if (seenLevel != level || seenTick != level.getGameTime()) {
			SEEN.clear();
			seenLevel = level;
			seenTick = level.getGameTime();
		}
		long key = pos.asLong();
		if (SEEN.containsKey(key)) {
			return SEEN.get(key);
		}
		boolean camp = looksLikeCamp(level, pos);
		SEEN.put(key, camp);
		if (camp) {
			data.add(pos.immutable()); // known from now on, whatever becomes of the campfire
		} else if (data.unverified(pos)) {
			data.remove(pos); // an older list's mistake (B715): a bed no camp placed is a home again
		}
		return camp;
	}

	/**
	 * Whether the bed at {@code pos} looks like a camp's bedroll (a camp placed before the list): a brown or green bed with
	 * a campfire and a hay bale within {@link #LOOK} blocks and the camp's Blueprint Table (or an old camp's Builder's
	 * Bench) within {@link #TABLE}, as every version of both camps has them. A campfire and a hay bale alone are any rustic
	 * house's (B715).
	 */
	static boolean looksLikeCamp(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		if (!isBedroll(level.getBlockState(pos))) {
			return false;
		}
		boolean campfire = false;
		boolean hay = false;
		boolean table = false;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-TABLE, -2, -TABLE), pos.offset(TABLE, 3, TABLE))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			BlockState state = level.getBlockState(p);
			table |= state.is(ModBlocks.BLUEPRINT_TABLE) || state.is(ModBlocks.BUILDERS_BENCH);
			if (Math.abs(p.getX() - pos.getX()) <= LOOK && Math.abs(p.getZ() - pos.getZ()) <= LOOK) {
				campfire |= state.is(BlockTags.CAMPFIRES);
				hay |= state.is(Blocks.HAY_BLOCK);
			}
			if (campfire && hay && table) {
				return true;
			}
		}
		return false;
	}

	/** The wagon beds round the hall at {@code hall} (taken or not); the list forgets one that is no longer a bed. */
	public static List<BlockPos> inVillage(ServerLevel level, BlockPos hall) {
		List<BlockPos> out = new ArrayList<>();
		VillageHalls.beds(level, hall).filter(p -> isWagonBed(level, p)).forEach(p -> out.add(p.immutable()));
		return out;
	}

	/** The beds round {@code centre} a newcomer can have: free, and not a wagon's. Nearest first. */
	public static List<BlockPos> freeHomes(ServerLevel level, BlockPos centre, int radius) {
		return level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), p -> !isWagonBed(level, p), centre, radius, PoiManager.Occupancy.HAS_SPACE)
			.map(BlockPos::immutable).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(centre))).toList();
	}

	/** The free bed nearest {@code centre} that isn't a wagon's, if any. */
	public static Optional<BlockPos> freeHome(ServerLevel level, BlockPos centre, int radius) {
		return level.getPoiManager().findClosest(h -> h.is(PoiTypes.HOME), p -> !isWagonBed(level, p), centre, radius, PoiManager.Occupancy.HAS_SPACE);
	}

	/**
	 * {@code villager} moves in at the free bed {@code bed}: the bed is theirs at once (its place taken, their home
	 * remembered, as vanilla gives a newborn its parents' free bed), so the next newcomer can't count it free too.
	 * False if somebody had it already.
	 */
	public static boolean claim(ServerLevel level, Villager villager, BlockPos bed) {
		Optional<BlockPos> took = level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
		if (took.isEmpty()) {
			return false;
		}
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		return true;
	}

	/**
	 * The village round {@code hall} is founded: its wagon beds are no homes. Each villager who kept one as theirs gets
	 * up and forgets it (they find a bed in the village's houses themselves), and every wagon bed is held for nobody, so
	 * none is claimed again. Returns how many villagers were moved out.
	 */
	public static int settle(ServerLevel level, BlockPos hall) {
		List<BlockPos> beds = inVillage(level, hall);
		forgetGone(level, hall);
		if (beds.isEmpty()) {
			return 0;
		}
		int moved = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			GlobalPos home = v.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			if (home == null || !home.dimension().equals(level.dimension()) || !beds.contains(home.pos())) {
				continue;
			}
			if (v.isSleeping() && v.getSleepingPos().filter(home.pos()::equals).isPresent()) {
				v.stopSleeping();
			}
			// (its place stays taken, by nobody now: the bed is held below either way)
			v.getBrain().eraseMemory(MemoryModuleType.HOME);
			moved++;
		}
		PoiManager poi = level.getPoiManager();
		for (BlockPos bed : beds) {
			if (poi.getFreeTickets(bed) > 0) {
				poi.take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
			}
		}
		return moved;
	}

	/** Takes listed beds round the hall that are no longer beds (broken, or never placed) off the list. */
	private static void forgetGone(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		if (data.beds.isEmpty() && data.unverified.isEmpty()) {
			return;
		}
		List<BlockPos> gone = new ArrayList<>();
		long r = VillageHalls.RADIUS;
		LongOpenHashSet listed = new LongOpenHashSet(data.beds);
		listed.addAll(data.unverified);
		for (long l : listed) {
			BlockPos p = BlockPos.of(l);
			if (p.distSqr(hall) <= r * r && level.isLoaded(p) && !(isBedHead(level.getBlockState(p)) && isBedroll(level.getBlockState(p)))) {
				gone.add(p);
			}
		}
		gone.forEach(data::remove);
	}
}
