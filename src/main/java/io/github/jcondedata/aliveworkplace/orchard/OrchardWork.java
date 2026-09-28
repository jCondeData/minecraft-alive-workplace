package io.github.jcondedata.aliveworkplace.orchard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * The Orchard Keeper's shift: walk round the orchard near the Fruit Basket picking whatever is ripe
 * (see {@link Fruit}), and carry the harvest to the chests by the basket. Plants are picked, never cut, so
 * they grow back. Fruit up in a tree is reached with a picking pole (a longer reach than other jobs).
 */
public class OrchardWork extends Behavior<Villager> {
	/** How far from the Fruit Basket the keeper picks. */
	public static int RADIUS = 16;
	private static final int DOWN = 4;
	private static final int UP = 10;
	/** The picking pole. */
	static final double REACH = 5.5;
	private static final float SPEED = 0.55f;
	private static final int SEARCH_EVERY = 100;
	/** Fruit carried before a trip to the chests. */
	private static final int CARRY = 48;
	/** Base ticks between two picks (faster with levels). */
	private static final int PICK_DELAY = 12;
	/** Give up on fruit that can't be reached in this many ticks, and leave it alone for a while. */
	private static final int GIVE_UP = 300;
	private static final int SKIP_FOR = 2400;

	private enum Phase { LOOKING, PICKING, DEPOSITING, PLANTING }

	/** Seeds of a kind taken from the chests at a time for the orchard. */
	private static final int SEEDS = 16;

	private final Walker walker = new Walker(SPEED).reachingUp(6);
	/** Ripe fruit found by the last look round, picked nearest first. */
	private final List<BlockPos> ripe = new ArrayList<>();
	private final Map<BlockPos, Long> unreachable = new HashMap<>();
	@Nullable
	private BlockPos fruit;
	private int searchTimer;
	private int tryingFor;
	private int pickTimer;
	private boolean depositDue;
	/** Where the next seed goes in the orchard, and which seed. */
	@Nullable
	private BlockPos plantSpot;
	private ItemStack plantSeed = ItemStack.EMPTY;
	@Nullable
	private BlockPos seedChest;

	public OrchardWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		fruit = null;
		ripe.clear();
		searchTimer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos basket = Builders.benchPos(villager).orElse(null);
		if (basket == null) {
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);

		// 1. A full basket (or a finished round) goes to the chests.
		if (depositDue || carried(bag) >= CARRY || bag.freeSlots() < 2) {
			status(villager, Phase.DEPOSITING);
			if (bag.isEmpty() || walker.walkTo(level, villager, containerNear(level, basket), 3.0)) {
				deposit(level, villager, basket, bag);
				depositDue = false;
			}
			return;
		}

		// 1b. Planting the orchard (between rounds of picking).
		net.minecraft.world.level.levelgen.structure.BoundingBox orchard = Orchards.orchard(villager);
		if (orchard != null && (plantSpot != null || seedChest != null)) {
			plant(level, villager, basket, bag);
			return;
		}

		// 2. Next ripe fruit.
		if (fruit == null || !Fruit.isRipe(level.getBlockState(fruit))) {
			fruit = next(level, villager);
			tryingFor = 0;
			if (fruit == null) {
				if (--searchTimer > 0) {
					status(villager, Phase.LOOKING);
					return;
				}
				searchTimer = SEARCH_EVERY;
				ripe.addAll(findFruit(level, basket, orchard, gameTime, unreachable));
				fruit = next(level, villager);
				if (fruit == null && orchard != null && choosePlanting(level, villager, basket, bag, orchard)) {
					return;
				}
				if (fruit == null) {
					// Nothing ripe: take in what was picked, then wait by the basket.
					status(villager, Phase.LOOKING);
					if (!bag.isEmpty()) {
						depositDue = true;
					} else if (!villager.blockPosition().closerThan(basket, 8)) {
						walker.walkTo(level, villager, basket, 3.0);
					}
					return;
				}
			}
		}

		// 3. Walk over and pick.
		status(villager, Phase.PICKING);
		if (!walker.reach(level, villager, fruit, REACH)) {
			if (walker.noSpot() || ++tryingFor > GIVE_UP) {
				unreachable.put(fruit, gameTime + SKIP_FOR);
				fruit = null;
			}
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(fruit));
		if (++pickTimer < BuilderLevels.delay(PICK_DELAY, villager)) {
			return;
		}
		pickTimer = 0;
		villager.swing(InteractionHand.MAIN_HAND);
		for (ItemStack stack : Fruit.pick(level, fruit, villager)) {
			ItemStack rest = bag.add(stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest);
			}
		}
		int picked = villager.getAttachedOrElse(ModAttachments.FRUIT_PICKED, 0) + 1;
		villager.setAttached(ModAttachments.FRUIT_PICKED, picked);
		if (picked % 4 == 0) {
			BuilderLevels.addXp(level, villager, 1, null);
		}
		fruit = null;
	}

	/** The nearest still-ripe fruit from the last look round (and forgets it), or null. */
	@Nullable
	private BlockPos next(ServerLevel level, Villager villager) {
		ripe.removeIf(p -> !Fruit.isRipe(level.getBlockState(p)));
		BlockPos from = villager.blockPosition();
		BlockPos best = null;
		for (BlockPos p : ripe) {
			if (best == null || p.distSqr(from) < best.distSqr(from)) {
				best = p;
			}
		}
		ripe.remove(best);
		return best;
	}

	/** Every ripe fruit within {@link #RADIUS} of the basket (and in the orchard) that hasn't been given up on lately. */
	static List<BlockPos> findFruit(ServerLevel level, BlockPos basket, @Nullable net.minecraft.world.level.levelgen.structure.BoundingBox orchard,
		long now, Map<BlockPos, Long> unreachable) {
		unreachable.values().removeIf(until -> until < now);
		List<BlockPos> found = new ArrayList<>();
		Iterable<BlockPos> around = BlockPos.betweenClosed(basket.offset(-RADIUS, -DOWN, -RADIUS), basket.offset(RADIUS, UP, RADIUS));
		if (orchard != null) {
			around = com.google.common.collect.Iterables.concat(around,
				BlockPos.betweenClosed(orchard.minX(), orchard.minY() - 2, orchard.minZ(), orchard.maxX(), orchard.maxY() + 6, orchard.maxZ()));
		}
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		for (BlockPos p : around) {
			if (!seen.add(p.immutable())) {
				continue;
			}
			if (Fruit.isRipe(level.getBlockState(p)) && !unreachable.containsKey(p)) {
				found.add(p.immutable());
			}
		}
		return found;
	}

	// --- the orchard -------------------------------------------------------------------------------

	/**
	 * Nothing ripe: find an empty spot in the orchard for a seed from the bag, or go and fetch seeds from the chests if
	 * there's room. Returns false when there's nothing to plant.
	 */
	private boolean choosePlanting(ServerLevel level, Villager villager, BlockPos basket, BuilderBag bag,
		net.minecraft.world.level.levelgen.structure.BoundingBox orchard) {
		// Seeds in the bag first (each kind until one has somewhere to go: berries want farmland — or grass to till, with a
		// hoe in hand or in the chests — bushes and trees dirt).
		List<BlockPos> supplies = SupplyContainers.find(level, basket, null);
		boolean canTill = Orchards.isHoe(villager.getMainHandItem()) || SupplyContainers.firstMatching(level, supplies, Orchards::isHoe) != null;
		for (ItemStack seed : Orchards.seedsCarried(bag)) {
			BlockPos spot = Orchards.nextSpot(level, orchard, seed, villager.blockPosition(), canTill).orElse(null);
			if (spot != null) {
				plantSpot = spot;
				plantSeed = seed;
				walker.reset();
				return true;
			}
		}
		// Then the chests.
		for (BlockPos chest : supplies) {
			for (ItemStack sample : SupplyContainers.peekMatching(level, chest, Orchards::isSeed)) {
				if (!bag.has(sample.getItem(), 1) && Orchards.nextSpot(level, orchard, sample, villager.blockPosition(), canTill).isPresent()) {
					seedChest = chest;
					plantSeed = sample.copyWithCount(1);
					walker.reset();
					return true;
				}
			}
		}
		return false;
	}

	/** Fetches the seeds, or walks to the spot and plants. */
	private void plant(ServerLevel level, Villager villager, BlockPos basket, BuilderBag bag) {
		status(villager, Phase.PLANTING);
		if (seedChest != null) {
			if (walker.walkTo(level, villager, seedChest, 3.0)) {
				List<BlockPos> supplies = SupplyContainers.find(level, basket, null);
				bag.addAll(plantSeed.getItem(), SupplyContainers.extract(level, supplies, plantSeed.getItem(), SEEDS));
				seedChest = null;
				searchTimer = 0; // look for a spot straight away
			}
			return;
		}
		if (plantSpot == null) {
			return;
		}
		if (Orchards.needsTilling(level, plantSpot, plantSeed) && !Orchards.isHoe(villager.getMainHandItem())) {
			// A hoe from the chests to till the ground for the berry.
			List<BlockPos> supplies = SupplyContainers.find(level, basket, null);
			BlockPos chest = SupplyContainers.firstMatching(level, supplies, Orchards::isHoe);
			if (chest == null) {
				plantSpot = null;
				return;
			}
			if (walker.walkTo(level, villager, chest, 3.0)) {
				ItemStack hoe = SupplyContainers.takeOne(level, supplies, Orchards::isHoe);
				if (!hoe.isEmpty()) {
					villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, hoe);
					villager.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 2f); // it's the owner's: dropped, not lost
				}
				walker.reset();
			}
			return;
		}
		if (walker.reach(level, villager, plantSpot, REACH)) {
			if (villager.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(plantSpot))) {
				// Standing on the spot: step off first (a berry bush would prick them).
				for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
					if (Walker.canStand(level, plantSpot.relative(d))) {
						Walker.hop(level, villager, plantSpot.relative(d));
						break;
					}
				}
				return;
			}
			villager.swing(InteractionHand.MAIN_HAND);
			Orchards.plant(level, villager, bag, plantSeed, plantSpot);
			plantSpot = null;
			searchTimer = 0;
		} else if (walker.noSpot()) {
			plantSpot = null;
		}
	}

	private static int carried(BuilderBag bag) {
		int n = 0;
		for (ItemStack s : bag.stacks()) {
			n += s.getCount();
		}
		return n;
	}

	private static void deposit(ServerLevel level, Villager villager, BlockPos basket, BuilderBag bag) {
		if (bag.isEmpty()) {
			return;
		}
		List<BlockPos> supplies = SupplyContainers.find(level, basket, null);
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, basket.above(), rest);
			}
		}
		level.playSound(null, villager.blockPosition(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.1f);
	}

	private static BlockPos containerNear(ServerLevel level, BlockPos basket) {
		List<BlockPos> supplies = SupplyContainers.find(level, basket, null);
		return supplies.isEmpty() ? basket : supplies.get(0);
	}

	private static void status(Villager villager, Phase phase) {
		Component title = Component.translatable("message.aliveworkplace.orchard.title", villager.getAttachedOrElse(ModAttachments.FRUIT_PICKED, 0));
		Component line = Component.translatable("message.aliveworkplace.orchard.state." + phase.name().toLowerCase()).withStyle(ChatFormatting.GRAY);
		WorkerStatus.set(villager, title, -1f, line);
	}
}
