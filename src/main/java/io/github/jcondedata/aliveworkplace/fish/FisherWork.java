package io.github.jcondedata.aliveworkplace.fish;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
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
 * from the vanilla fishing loot table (fish and junk; treasure needs a real bobber in open water, so
 * none). Rods come from the barrel and chests nearby and wear out; the catch goes back there.
 */
public class FisherWork extends Behavior<Villager> {
	/** How far from the barrel the fisherman looks for water. */
	public static int RADIUS = 16;
	static final double REACH = 4.5;
	private static final float SPEED = 0.55f;
	private static final int SEARCH_EVERY = 100;
	private static final int CATCHES_PER_TRIP = 5;
	private static final int CATCHES_PER_XP = 3;

	enum Phase { FISHING, NEEDS_ROD, NO_WATER, DEPOSITING }

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
		return canWork(villager);
	}

	private static boolean canWork(Villager villager) {
		return !villager.isSleeping() && Fishers.isHired(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		waited = 0;
		searchTimer = 0;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		RESTING.remove(villager);
		reelUp();
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
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);

		// 1. Bring the catch in every few fish, or when the bag fills up.
		if (catches >= CATCHES_PER_TRIP || bag.freeSlots() < 3) {
			rest(villager, false);
			status(villager, Phase.DEPOSITING);
			if (walker.walkTo(level, villager, barrel, 3.0)) {
				deposit(level, barrel, bag);
				catches = 0;
			}
			return;
		}

		// 2. A rod in hand.
		ItemStack rod = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!isRod(rod)) {
			waited = 0;
			List<BlockPos> supplies = SupplyContainers.find(level, barrel, null);
			BlockPos chest = SupplyContainers.firstMatching(level, supplies, FisherWork::isRod);
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

		// 3. Water to fish in.
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
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(water));
		Vec3 bobber = Vec3.atCenterOf(water).add(0, 0.45, 0);
		if (waited++ == 0) {
			reelUp();
			this.bobber = FishingBobber.cast(level, villager, bobber);
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
		reelIn(level, villager, bag, rod, bobber);
		waited = 0;
		catches++;
	}

	private static void rest(Villager villager, boolean resting) {
		if (resting) {
			RESTING.add(villager);
		} else {
			RESTING.remove(villager);
		}
	}

	private static void reelIn(ServerLevel level, Villager villager, BuilderBag bag, ItemStack rod, Vec3 bobber) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
		LootParams params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, bobber)
			.withParameter(LootContextParams.TOOL, rod)
			.withParameter(LootContextParams.THIS_ENTITY, villager)
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
		int caught = villager.getAttachedOrElse(ModAttachments.FISH_CAUGHT, 0) + 1;
		villager.setAttached(ModAttachments.FISH_CAUGHT, caught);
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

	private static void deposit(ServerLevel level, BlockPos barrel, BuilderBag bag) {
		List<BlockPos> supplies = SupplyContainers.find(level, barrel, null);
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, barrel.above(), rest);
			}
		}
		io.github.jcondedata.aliveworkplace.work.Furnaces.tend(level, barrel, supplies, io.github.jcondedata.aliveworkplace.work.Furnaces::isFish);
		level.playSound(null, barrel, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	private static void status(Villager villager, Phase phase) {
		int caught = villager.getAttachedOrElse(ModAttachments.FISH_CAUGHT, 0);
		Component title = Component.translatable("message.aliveworkplace.fisher.title", caught);
		Component line = Component.translatable("message.aliveworkplace.fisher.state." + phase.name().toLowerCase())
			.withStyle(phase == Phase.NEEDS_ROD || phase == Phase.NO_WATER ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
