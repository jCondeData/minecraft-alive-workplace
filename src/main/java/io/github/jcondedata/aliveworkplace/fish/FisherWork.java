package io.github.jcondedata.aliveworkplace.fish;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Boats;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A hired fisherman's shift: walk to water near the barrel, cast, wait for a bite, and reel in a catch
 * from the vanilla fishing loot table (fish and junk; treasure needs open water, so none from the shore).
 * Rods come from the barrel and chests nearby and wear out; the catch goes back there.
 *
 * <p>With a boat in those chests (any plain boat or raft), a fisher whose water is big enough rows out: from the
 * shore to a spot in open water (water all round, as a player's bobber needs for treasure) within {@link #BOAT_RADIUS}
 * of the barrel, straight across the water, and fishes from the boat — where one catch in twenty is treasure, as for
 * a player. They row back to where they set out to bring the catch in, and the boat goes back in the chest. A shift
 * that ends out on the water (nightfall, a cancelled job) brings them ashore at once.
 */
public class FisherWork extends Behavior<Villager> {
	/** How far from the barrel the fisherman looks for water. */
	public static int RADIUS = 16;
	static final double REACH = 4.5;
	private static final float SPEED = 0.55f;
	private static final int SEARCH_EVERY = 100;
	private static final int CATCHES_PER_TRIP = 5;
	private static final int CATCHES_PER_XP = 3;
	/** How far from the barrel a fisher rows out. */
	public static int BOAT_RADIUS = 24;
	/** How far out, at the least, open water must be from where the boat sets out. */
	static final int MIN_ROW = 6;
	/** Marks the boats fishers launch (so one they're found in after a restart is known to be theirs). */
	static final String BOAT_TAG = "aliveworkplace_fisher_boat";
	/** Blocks a tick while rowing. */
	static final double ROW_SPEED = 0.15;
	/** Ticks of rowing that get no nearer before a fisher gives up and comes ashore. */
	static final int ROW_STUCK = 200;
	/** One catch in this many is treasure when fishing open water (5%, a player's chance without Luck of the Sea). */
	static final int TREASURE_ONE_IN = 20;

	enum Phase { FISHING, NEEDS_ROD, NO_WATER, DEPOSITING, ROWING }

	/** Hired fishermen with nothing to do right now (no rod, no water): vanilla's routine may run. */
	private static final Set<Villager> RESTING = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private BlockPos water;
	private final Set<BlockPos> unreachable = new HashSet<>();
	private int searchTimer;
	private int waited;
	private int biteAt;
	private int catches;
	/** The bobber on the water while casting (only for show). */
	@Nullable
	private FishingBobber bobber;

	/** A boat trip: the water the boat sets out from, the open water to fish, where the boat stops (short of it). */
	@Nullable
	private BlockPos dock;
	@Nullable
	private BlockPos openWater;
	@Nullable
	private Vec3 mooring;
	/** Where the fisher stood to board, and comes ashore again. */
	@Nullable
	private BlockPos landing;
	/** The boat they're in, and the item it came from. */
	@Nullable
	private Entity boat;
	private ItemStack boatItem = ItemStack.EMPTY;
	private int boatSearchTimer;
	private double bestLeft;
	private int stuck;

	public FisherWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	static boolean isResting(Villager villager) {
		return RESTING.contains(villager);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return canWork(villager);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		// The game doesn't stop a running behaviour when the activity changes: without this a fisher stayed out on the
		// water past the end of the shift until the run timed out (stop() brings them ashore).
		return canWork(villager) && villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK);
	}

	private static boolean canWork(Villager villager) {
		return !villager.isSleeping() && Fishers.isHired(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		waited = 0;
		searchTimer = 0;
		boatSearchTimer = 0;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
		if (villager.getVehicle() != null && villager.getVehicle().getTags().contains(BOAT_TAG) && boat == null) {
			// Back to work in a boat nobody remembers rowing out (the world was saved mid-trip): ashore first.
			boat = villager.getVehicle();
			comeAshore(level, villager);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		RESTING.remove(villager);
		reelUp();
		if (boat != null) {
			comeAshore(level, villager);
		}
	}

	/** Takes the bobber out of the water. */
	private void reelUp() {
		if (bobber != null) {
			bobber.discard();
			bobber = null;
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos barrel = Builders.benchPos(villager).orElse(null);
		if (barrel == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);

		if (boat != null && boat.isRemoved()) {
			boat = null; // the boat was broken (and dropped as an item)
			clearTrip();
		} else if (boat != null && villager.getVehicle() != boat) {
			comeAshore(level, villager); // knocked out of it: they swim ashore with it
		}

		// 1. Bring the catch in every few fish, or when the bag fills up (rowing back first).
		if (catches >= CATCHES_PER_TRIP || bag.freeSlots() < 3) {
			rest(villager, false);
			if (boat != null) {
				reelUp();
				status(villager, Phase.ROWING);
				rowHome(level, villager);
				return;
			}
			status(villager, Phase.DEPOSITING);
			if (walker.walkTo(level, villager, barrel, 3.0)) {
				deposit(level, villager, barrel, bag);
				catches = 0;
			}
			return;
		}

		// 2. A rod in hand.
		ItemStack rod = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!isRod(rod) && boat != null) {
			reelUp();
			status(villager, Phase.ROWING);
			rowHome(level, villager); // the rod broke out on the water: a new one is ashore
			return;
		}
		if (!isRod(rod)) {
			waited = 0;
			List<BlockPos> supplies = SupplyContainers.find(level, barrel, null);
			BlockPos chest = SupplyContainers.firstMatching(level, supplies, FisherWork::isRod);
			if (chest == null) {
				// Another worker in the village may have a spare rod.
				io.github.jcondedata.aliveworkplace.work.Village.Find elsewhere = io.github.jcondedata.aliveworkplace.work.Village.find(level, villager, barrel, null, FisherWork::isRod);
				if (elsewhere != null) {
					supplies = elsewhere.stash().chests();
					chest = elsewhere.chest();
				}
			}
			if (chest == null) {
				if (!bag.isEmpty()) {
					catches = CATCHES_PER_TRIP; // take what we have home first
					return;
				}
				rest(villager, true);
				status(villager, Phase.NEEDS_ROD);
				return;
			}
			rest(villager, false);
			status(villager, Phase.FISHING);
			if (walker.walkTo(level, villager, chest, 3.0)) {
				ItemStack got = SupplyContainers.takeOne(level, supplies, FisherWork::isRod);
				if (!got.isEmpty()) {
					villager.setItemSlot(EquipmentSlot.MAINHAND, got);
				}
			}
			return;
		}

		// 3a. Out in the boat: row to the mooring, then fish the open water.
		if (boat != null) {
			rest(villager, false);
			if (openWater == null || mooring == null || !isOpenWater(level, openWater)) {
				status(villager, Phase.ROWING);
				rowHome(level, villager);
				return;
			}
			if (!row(level, villager, mooring)) {
				status(villager, Phase.ROWING);
				return;
			}
			Boats.rest(boat);
			status(villager, Phase.FISHING);
			fishAt(level, villager, bag, rod, openWater, true);
			return;
		}

		// 3b. A boat trip, when there's a boat in the chests (or the bag) and open water to row to.
		if (dock != null || planTrip(level, villager, barrel, bag)) {
			rest(villager, false);
			reelUp();
			status(villager, Phase.ROWING);
			if (bag.stacks().stream().noneMatch(Boats::isRowingBoat)) {
				List<BlockPos> supplies = SupplyContainers.find(level, barrel, null);
				BlockPos chest = SupplyContainers.firstMatching(level, supplies, Boats::isRowingBoat);
				if (chest == null) {
					clearTrip();
					return;
				}
				if (walker.walkTo(level, villager, chest, 3.0)) {
					ItemStack got = SupplyContainers.takeOne(level, supplies, Boats::isRowingBoat);
					if (!got.isEmpty()) {
						ItemStack rest = bag.add(got);
						if (!rest.isEmpty()) {
							SupplyContainers.insert(level, supplies, rest);
							clearTrip();
						}
					}
				}
				return;
			}
			if (!walker.reach(level, villager, dock, REACH)) {
				if (walker.noSpot()) {
					unreachable.add(dock);
					clearTrip();
				}
				return;
			}
			launch(level, villager, bag);
			return;
		}

		// 3c. Water to fish in from the shore.
		if (water == null || !isFishable(level, water)) {
			water = null;
			waited = 0;
			if (--searchTimer > 0) {
				rest(villager, true);
				status(villager, Phase.NO_WATER);
				return;
			}
			searchTimer = SEARCH_EVERY;
			water = findWater(level, barrel, villager.blockPosition(), unreachable);
			if (water == null) {
				unreachable.clear();
				rest(villager, true);
				status(villager, Phase.NO_WATER);
				return;
			}
		}

		// 4. Stand at the edge, cast, wait for a bite.
		rest(villager, false);
		status(villager, Phase.FISHING);
		if (!walker.reach(level, villager, water, REACH)) {
			waited = 0;
			reelUp();
			if (walker.noSpot()) {
				unreachable.add(water);
				water = null;
			}
			return;
		}
		fishAt(level, villager, bag, rod, water, false);
	}

	/** Casts at {@code spot}, waits for a bite and reels in; {@code open}: open water, where treasure bites too. */
	private void fishAt(ServerLevel level, Villager villager, BuilderBag bag, ItemStack rod, BlockPos spot, boolean open) {
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(spot));
		Vec3 bobber = Vec3.atCenterOf(spot).add(0, 0.45, 0);
		if (waited++ == 0) {
			reelUp();
			this.bobber = FishingBobber.cast(level, villager, bobber);
			// A Water or Ice partner swims out round the bobber (ROADMAP 28.6).
			io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "cast", spot);
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, villager.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5f, 0.4f + level.random.nextFloat() * 0.4f);
			biteAt = BuilderLevels.delay(level, villager) * (15 + level.random.nextInt(46));
			level.sendParticles(ParticleTypes.SPLASH, bobber.x, bobber.y, bobber.z, 6, 0.1, 0, 0.1, 0);
			return;
		}
		if (waited % 25 == 0) {
			level.sendParticles(ParticleTypes.FISHING, bobber.x, bobber.y, bobber.z, 2, 0.05, 0, 0.05, 0);
		}
		if (waited < Math.max(20, biteAt)) {
			if (this.bobber != null && waited >= Math.max(20, biteAt) - 12 && !this.bobber.biting()) {
				this.bobber.setBiting(true); // a bite: the bobber goes under
				level.sendParticles(ParticleTypes.BUBBLE, bobber.x, bobber.y, bobber.z, 6, 0.1, 0.05, 0.1, 0.05);
			}
			return;
		}
		reelUp();
		reelIn(level, villager, bag, rod, bobber, open);
		waited = 0;
		catches++;
	}

	/**
	 * Looks for a boat trip (every so often): a boat in the bag or the chests by the barrel, shore water to set out
	 * from, and open water at least {@link #MIN_ROW} blocks out from it, straight across the water.
	 */
	private boolean planTrip(ServerLevel level, Villager villager, BlockPos barrel, BuilderBag bag) {
		if (--boatSearchTimer > 0) {
			return false;
		}
		boatSearchTimer = SEARCH_EVERY;
		boolean haveBoat = bag.stacks().stream().anyMatch(Boats::isRowingBoat)
			|| SupplyContainers.firstMatching(level, SupplyContainers.find(level, barrel, null), Boats::isRowingBoat) != null;
		if (!haveBoat) {
			return false;
		}
		BlockPos from = findWater(level, barrel, villager.blockPosition(), unreachable);
		if (from == null) {
			return false;
		}
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(barrel.offset(-BOAT_RADIUS, 0, -BOAT_RADIUS), barrel.offset(BOAT_RADIUS, 0, BOAT_RADIUS))) {
			BlockPos at = new BlockPos(p.getX(), from.getY(), p.getZ());
			double dist = at.distSqr(from);
			if (dist < MIN_ROW * MIN_ROW || dist >= bestDist || !isOpenWater(level, at) || !waterWay(level, from, at)) {
				continue;
			}
			bestDist = dist;
			best = at.immutable();
		}
		if (best == null) {
			return false;
		}
		dock = from;
		openWater = best;
		// The boat stops three blocks short of where the bobber goes
		Vec3 start = surface(dock);
		Vec3 end = surface(openWater);
		Vec3 back = start.subtract(end).normalize().scale(3);
		mooring = end.add(back.x, 0, back.z);
		return true;
	}

	/** Puts the boat on the water at the dock and gets in. */
	private void launch(ServerLevel level, Villager villager, BuilderBag bag) {
		ItemStack item = bag.takeFirst(Boats::isRowingBoat);
		if (item.isEmpty() || dock == null || openWater == null) {
			clearTrip();
			return;
		}
		landing = villager.blockPosition();
		boatItem = item.copyWithCount(1);
		Vec3 at = surface(dock);
		boat = Boats.launch(level, at, Boats.yaw(at, surface(openWater)), boatItem);
		boat.addTag(BOAT_TAG);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		waited = 0;
		villager.startRiding(boat, true);
		level.playSound(null, dock, SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 0.3f, 1.2f);
		bestLeft = Double.MAX_VALUE;
		stuck = 0;
	}

	/** Rows towards {@code to}; true once there (or given up on as out of reach, which brings the fisher home). */
	private boolean row(ServerLevel level, Villager villager, Vec3 to) {
		if (boat == null) {
			return false;
		}
		double left = Boats.row(boat, to, ROW_SPEED);
		if (left < 1.0) {
			bestLeft = Double.MAX_VALUE;
			stuck = 0;
			return true;
		}
		if (left < bestLeft - 0.05) {
			bestLeft = left;
			stuck = 0;
		} else if (++stuck > ROW_STUCK) {
			comeAshore(level, villager); // aground or blocked: ashore where they set out
		}
		return false;
	}

	/** Rows back to the dock and comes ashore there. */
	private void rowHome(ServerLevel level, Villager villager) {
		if (dock == null) {
			comeAshore(level, villager);
			return;
		}
		if (row(level, villager, surface(dock))) {
			comeAshore(level, villager);
		}
	}

	/** Out of the boat onto the shore where they set out (straight away), the boat back in the bag. */
	private void comeAshore(ServerLevel level, Villager villager) {
		Entity b = boat;
		boat = null;
		if (b != null) {
			if (villager.getVehicle() == b) {
				villager.stopRiding();
			}
			BlockPos to = landing;
			if (to == null) {
				to = Walker.standingSpot(level, b.blockPosition(), villager.blockPosition(), 6.0);
			}
			if (to == null) {
				to = Builders.benchPos(villager).map(BlockPos::above).orElse(null);
			}
			if (to != null) {
				Walker.hop(level, villager, to);
			}
			ItemStack item = boatItem.isEmpty() ? Boats.item(b) : boatItem;
			ItemStack rest = ModAttachments.BUILDER_BAG.getOrCreate(villager).add(item);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest);
			}
			b.discard();
		}
		clearTrip();
	}

	private void clearTrip() {
		boatSearchTimer = SEARCH_EVERY; // (not straight back out: the catch goes home first)
		dock = null;
		openWater = null;
		mooring = null;
		landing = null;
		boatItem = ItemStack.EMPTY;
		waited = 0;
	}

	/** Where a boat floats on the water at {@code water}. */
	private static Vec3 surface(BlockPos water) {
		return new Vec3(water.getX() + 0.5, water.getY() + 0.9, water.getZ() + 0.5);
	}

	/** Open water: still water with air above all round (5 x 5), as a player's bobber needs for treasure. */
	public static boolean isOpenWater(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 0, 2))) {
			if (!isFishable(level, p)) {
				return false;
			}
		}
		return true;
	}

	/** Whether a boat can go straight from {@code from} to {@code to}: water with air over it all the way. */
	static boolean waterWay(ServerLevel level, BlockPos from, BlockPos to) {
		Vec3 a = Vec3.atCenterOf(from);
		Vec3 b = Vec3.atCenterOf(to);
		int steps = (int) Math.ceil(a.distanceTo(b) * 2);
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, (double) i / Math.max(1, steps));
			at.set(p.x, from.getY(), p.z);
			if (!level.getFluidState(at).is(FluidTags.WATER) || !level.getBlockState(at.above()).isAir()) {
				return false;
			}
		}
		return true;
	}

	private static void rest(Villager villager, boolean resting) {
		if (resting) {
			RESTING.add(villager);
		} else {
			RESTING.remove(villager);
		}
	}

	private static void reelIn(ServerLevel level, Villager villager, BuilderBag bag, ItemStack rod, Vec3 bobber, boolean open) {
		// (the fishing table's treasure needs a player's bobber in open water, so it's rolled here for open water)
		boolean treasure = open && level.random.nextInt(TREASURE_ONE_IN) == 0;
		LootTable table = level.getServer().reloadableRegistries().getLootTable(treasure ? BuiltInLootTables.FISHING_TREASURE : BuiltInLootTables.FISHING);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, bobber)
			.withParameter(LootContextParams.TOOL, rod)
			.withParameter(LootContextParams.THIS_ENTITY, villager)
			.withLuck(io.github.jcondedata.aliveworkplace.legend.Gifted.lootLuck(villager) // Lucky (29.7)
				+ io.github.jcondedata.aliveworkplace.research.TreeEffects.lootLuck(villager)) // loot_luck research (29.11)
			.create(LootContextParamSets.FISHING);
		for (ItemStack stack : table.getRandomItems(params)) {
			ItemStack rest = bag.add(stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.SPLASH, bobber.x, bobber.y, bobber.z, 12, 0.2, 0, 0.2, 0);
		level.playSound(null, villager.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 0.8f, 1f);
		rod.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
		int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0) + 1;
		ModAttachments.FISH_CAUGHT.set(villager, caught);
		if (caught % CATCHES_PER_XP == 0) {
			BuilderLevels.addXp(level, villager, 1, null);
		}
	}

	static boolean isRod(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.FISHING_ROD);
	}

	/** Still water with air above it: somewhere to cast. */
	static boolean isFishable(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir();
	}

	/**
	 * Water near the barrel with dry ground next to it to stand on, nearest to the fisherman first;
	 * water with more water around it (a pond, not a puddle) wins ties.
	 */
	@Nullable
	static BlockPos findWater(ServerLevel level, BlockPos barrel, BlockPos from, Set<BlockPos> unreachable) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(barrel.offset(-RADIUS, -6, -RADIUS), barrel.offset(RADIUS, 3, RADIUS))) {
			if (!isFishable(level, p) || unreachable.contains(p)) {
				continue;
			}
			if (!hasShore(level, p)) {
				continue;
			}
			int around = 0;
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-2, 0, -2), p.offset(2, 0, 2))) {
				if (level.getFluidState(n).is(FluidTags.WATER)) {
					around++;
				}
			}
			double score = p.distSqr(from) - around * 2.0;
			if (score < bestScore) {
				bestScore = score;
				best = p.immutable();
			}
		}
		return best;
	}

	/** A block of dry ground within two blocks of the water, level with it or one up. */
	private static boolean hasShore(ServerLevel level, BlockPos water) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int step = 1; step <= 2; step++) {
				BlockPos side = water.relative(d, step);
				if (Walker.canStand(level, side.above()) || Walker.canStand(level, side.above(2))) {
					return true;
				}
			}
		}
		return false;
	}

	private static void deposit(ServerLevel level, Villager villager, BlockPos barrel, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, barrel, null);
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, barrel.above(), rest);
			}
		}
		io.github.jcondedata.aliveworkplace.work.Furnaces.tend(level, barrel, supplies, io.github.jcondedata.aliveworkplace.work.Furnaces::isFish, villager);
		level.playSound(null, barrel, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	private static void status(Villager villager, Phase phase) {
		if (phase == Phase.NEEDS_ROD) {
			io.github.jcondedata.aliveworkplace.work.Requests.postTool(villager, Items.FISHING_ROD, "fishing_rod", FisherWork::isRod);
		} else {
			io.github.jcondedata.aliveworkplace.work.Requests.clear(villager);
		}
		int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
		Component title = Component.translatable("message.aliveworkplace.fisher.title", caught);
		Component line = Component.translatable("message.aliveworkplace.fisher.state." + phase.name().toLowerCase())
			.withStyle(phase == Phase.NEEDS_ROD || phase == Phase.NO_WATER ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
