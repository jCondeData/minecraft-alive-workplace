package io.github.jcondedata.aliveworkplace.build;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * The builder's work shift. Runs in the villager WORK activity while the villager has a build
 * job. Each tick it looks at the next unfinished step of the build and does whatever that needs:
 * fetch materials from the supply chests, walk into reach, break what is in the way, or place.
 *
 * <p>All progress lives in the {@link BuildSite} (saved with the world), so this behaviour can be
 * stopped and restarted at any time — night, panic, chunk unload, server restart — without losing work.
 */
public class BuilderWork extends Behavior<Villager> {
	/** How far (eye to block centre) a builder can place from. Generous so roofs are reachable from the ground. */
	static final double REACH = 5.5;
	/** How close (eye to block centre) a builder must be to use a chest. */
	private static final double CONTAINER_REACH = 3.0;
	private static final float SPEED = 0.6f;
	/** How many upcoming steps to gather materials for in one trip. */
	private static final int LOOKAHEAD = 192;
	/** Already-finished steps skipped per tick (cheap checks). */
	private static final int SKIP_BUDGET = 256;
	private static final int STUCK_TICKS = 100;
	private static final int WAIT_RECHECK = 100;

	private enum Action { NONE, BREAK, PLACE, SKIP }

	private int workTimer;
	private int waitTimer;
	private int blockedTimer;
	private int stuckTimer;
	private double bestDistance = Double.MAX_VALUE;
	@Nullable
	private BlockPos trackedTarget;

	public BuilderWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.activeSite(level, villager) != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.activeSite(level, villager) != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		workTimer = 0;
		waitTimer = 0;
		stuckTimer = 0;
		trackedTarget = null;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BuildSite site = Builders.activeSite(level, villager);
		if (site == null) {
			return;
		}
		BuildPlan plan = site.plan(level.getServer());
		if (plan == null) {
			return;
		}
		Optional<BlockPos> benchOpt = Builders.benchPos(villager);
		if (benchOpt.isEmpty()) {
			return;
		}
		BlockPos bench = benchOpt.get();
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);

		// 1. Find the next step that actually needs work.
		BuildPlan.Step step = null;
		Action action = Action.NONE;
		for (int budget = SKIP_BUDGET; budget > 0 && !site.isDone(); budget--) {
			BuildPlan.Step candidate = site.current(plan);
			if (candidate == null) {
				site.finishList();
				continue;
			}
			action = actionFor(level, site.stage(), candidate, bench);
			if (action == Action.NONE) {
				site.advance();
			} else if (action == Action.SKIP) {
				site.defer();
			} else {
				step = candidate;
				break;
			}
		}
		if (site.isDone()) {
			Builders.finish(level, villager, site);
			return;
		}
		if (step == null) {
			return;
		}

		// 2. Materials.
		MaterialRules.Requirement requirement = null;
		if (action == Action.PLACE) {
			requirement = MaterialRules.requirement(step.state()).orElse(null);
			if (requirement == null) {
				site.defer();
				return;
			}
			if (!free && !bag.has(requirement.item(), requirement.count())) {
				fetch(level, villager, site, plan, bag, bench, requirement);
				return;
			}
		} else if (bag.freeSlots() == 0) {
			deposit(level, villager, site, plan, bag, bench);
			return;
		}

		// 3. Walk into reach.
		if (!moveInReach(level, villager, site, step.pos(), REACH, true)) {
			site.setStatus(BuildSite.Status.WORKING);
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(step.pos()));
		villager.setItemSlot(EquipmentSlot.MAINHAND, requirement != null ? new ItemStack(requirement.item()) : ItemStack.EMPTY);
		site.setStatus(BuildSite.Status.WORKING);

		// 4. Do the work, one block per workplaceBuildDelay ticks.
		if (workTimer > 0) {
			workTimer--;
			return;
		}
		workTimer = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		if (action == Action.BREAK) {
			breakBlock(level, villager, bag, bench, plan, step.pos());
		} else {
			place(level, villager, site, bag, step, requirement, free);
		}
	}

	// --- deciding ----------------------------------------------------------------------------

	private static Action actionFor(ServerLevel level, BuildPlan.Stage stage, BuildPlan.Step step, BlockPos bench) {
		BlockPos pos = step.pos();
		BlockState world = level.getBlockState(pos);
		BlockState wanted = step.state();
		if (MaterialRules.matches(world, wanted)) {
			return Action.NONE;
		}
		boolean worldEmpty = world.isAir() || world.getBlock() instanceof LiquidBlock;

		if (stage == BuildPlan.Stage.CLEAR) {
			if (worldEmpty) {
				return Action.NONE;
			}
			boolean wantedEmpty = wanted.isAir();
			if (!wantedEmpty && (world.canBeReplaced() || MaterialRules.classify(wanted) == MaterialRules.Kind.SKIP)) {
				return Action.NONE; // placing will overwrite it, or we leave this spot alone
			}
			return isProtected(level, pos, world, bench) ? Action.NONE : Action.BREAK;
		}

		if (!worldEmpty && !world.canBeReplaced()) {
			return isProtected(level, pos, world, bench) ? Action.SKIP : Action.BREAK;
		}
		return Action.PLACE;
	}

	private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state, BlockPos bench) {
		return pos.equals(bench) || MaterialRules.isProtected(state, state.getDestroySpeed(level, pos));
	}

	// --- moving ------------------------------------------------------------------------------

	/**
	 * Walks until {@code target} is within {@code reach} (eye to block centre). If the builder makes no
	 * progress for a while — walled in, target over a gap — it hops to the nearest spot it can stand on
	 * within reach. Returns true once in reach.
	 */
	private boolean moveInReach(ServerLevel level, Villager villager, BuildSite site, BlockPos target, double reach, boolean deferIfImpossible) {
		double distance = villager.getEyePosition().distanceTo(Vec3.atCenterOf(target));
		if (distance <= reach) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			trackedTarget = null;
			return true;
		}
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, SPEED, 1));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (!target.equals(trackedTarget)) {
			trackedTarget = target;
			bestDistance = distance;
			stuckTimer = 0;
		} else if (distance < bestDistance - 0.3) {
			bestDistance = distance;
			stuckTimer = 0;
		} else if (++stuckTimer > STUCK_TICKS) {
			stuckTimer = 0;
			BlockPos spot = findStandingSpot(level, target, villager.blockPosition(), null, reach - 0.3);
			if (spot != null) {
				hop(level, villager, spot);
			} else if (deferIfImpossible) {
				site.defer();
			} else {
				return true; // nowhere to stand next to it (odd chest placement): let them reach it anyway
			}
		}
		return false;
	}

	private static void walkTo(Villager villager, BlockPos pos, int closeEnough) {
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED, closeEnough));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
	}

	private static boolean near(Villager villager, BlockPos pos, double distance) {
		return villager.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) <= distance * distance;
	}

	/** Closest place to stand (feet position) from which {@code target} is in reach, avoiding {@code avoid}. */
	@Nullable
	static BlockPos findStandingSpot(ServerLevel level, BlockPos target, BlockPos from, @Nullable AABB avoid, double maxEyeDistance) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -5; dy <= 1; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					p.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (!canStandAt(level, p)) {
						continue;
					}
					Vec3 eye = new Vec3(p.getX() + 0.5, p.getY() + 1.62, p.getZ() + 0.5);
					if (eye.distanceToSqr(Vec3.atCenterOf(target)) > maxEyeDistance * maxEyeDistance) {
						continue;
					}
					AABB body = new AABB(p.getX() + 0.2, p.getY(), p.getZ() + 0.2, p.getX() + 0.8, p.getY() + 1.9, p.getZ() + 0.8);
					if (body.intersects(new AABB(target)) || avoid != null && body.intersects(avoid)) {
						continue;
					}
					double score = p.distSqr(from);
					if (score < bestScore) {
						bestScore = score;
						best = p.immutable();
					}
				}
			}
		}
		return best;
	}

	private static boolean canStandAt(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& level.getFluidState(feet).isEmpty();
	}

	private static void hop(ServerLevel level, Villager villager, BlockPos spot) {
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
	}

	// --- materials ---------------------------------------------------------------------------

	private void fetch(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench,
					   MaterialRules.Requirement requirement) {
		if (bag.spaceFor(requirement.item()) < requirement.count()) {
			deposit(level, villager, site, plan, bag, bench);
			return;
		}
		List<BlockPos> supplies = SupplyContainers.find(level, bench, plan.bounds());
		int needed = requirement.count() - bag.count(requirement.item());
		if (SupplyContainers.count(level, supplies, requirement.item()) < needed) {
			waitForMaterials(level, villager, site, plan, bag, bench, supplies);
			return;
		}
		waitTimer = 0;
		site.setStatus(BuildSite.Status.FETCHING);
		BlockPos source = SupplyContainers.firstWith(level, supplies, requirement.item());
		if (source == null) {
			return;
		}
		if (!moveInReach(level, villager, site, source, CONTAINER_REACH, false)) {
			return;
		}

		// At the chest: take what the next stretch of work needs, current block first.
		Map<Item, Integer> wanted = new LinkedHashMap<>();
		wanted.put(requirement.item(), requirement.count());
		for (BuildPlan.Step s : site.upcoming(plan, LOOKAHEAD)) {
			if (!MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
				MaterialRules.requirement(s.state()).ifPresent(r -> wanted.merge(r.item(), r.count(), Integer::sum));
			}
		}
		boolean tookAny = false;
		for (Map.Entry<Item, Integer> e : wanted.entrySet()) {
			int want = e.getValue() - bag.count(e.getKey());
			int take = Math.min(want, bag.spaceFor(e.getKey()));
			if (take <= 0) {
				continue;
			}
			int got = SupplyContainers.extract(level, supplies, e.getKey(), take);
			if (got > 0) {
				tookAny = true;
				bag.addAll(e.getKey(), got);
			}
		}
		if (tookAny) {
			level.playSound(null, source, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	private void waitForMaterials(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench,
								  List<BlockPos> supplies) {
		boolean firstTick = site.status() != BuildSite.Status.WAITING_FOR_MATERIALS;
		site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
		if (!near(villager, bench, 3)) {
			walkTo(villager, bench, 2);
		}
		if (firstTick || --waitTimer <= 0) {
			waitTimer = WAIT_RECHECK;
			site.setMissing(Builders.computeMissing(level, site, plan, bag, supplies));
			if (firstTick) {
				Builders.notifyWaiting(level, villager, site);
			}
		}
	}

	private void deposit(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, plan.bounds());
		BlockPos target = supplies.isEmpty() ? bench : supplies.get(0);
		if (!moveInReach(level, villager, site, target, CONTAINER_REACH, false)) {
			return;
		}
		Set<Item> keep = new HashSet<>();
		for (BuildPlan.Step s : site.upcoming(plan, LOOKAHEAD)) {
			MaterialRules.requirement(s.state()).ifPresent(r -> keep.add(r.item()));
		}
		List<ItemStack> junk = bag.takeAllExcept(keep);
		if (junk.isEmpty()) {
			junk = bag.takeAll(); // bag is full of materials for later: drop them back off
		}
		for (ItemStack stack : junk) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				Builders.dropNear(level, bench, rest);
			}
		}
	}

	// --- doing -------------------------------------------------------------------------------

	private void breakBlock(ServerLevel level, Villager villager, BuilderBag bag, BlockPos bench, BuildPlan plan, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockEntity blockEntity = level.getBlockEntity(pos);
		List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, villager, ItemStack.EMPTY);
		level.destroyBlock(pos, false, villager);
		for (ItemStack drop : drops) {
			ItemStack rest = bag.add(drop);
			if (!rest.isEmpty()) {
				Builders.dropNear(level, bench, rest);
			}
		}
	}

	private void place(ServerLevel level, Villager villager, BuildSite site, BuilderBag bag, BuildPlan.Step step,
					   MaterialRules.Requirement requirement, boolean free) {
		BlockPos pos = step.pos();
		BlockState state = step.state();

		// Two-block blocks need their second spot free.
		if (step.secondaryPos() != null) {
			BlockState there = level.getBlockState(step.secondaryPos());
			if (!there.isAir() && !there.canBeReplaced() && !MaterialRules.matches(there, step.secondaryState())) {
				site.defer();
				return;
			}
		}
		if (!state.canSurvive(level, pos)) {
			site.defer(); // nothing to attach to yet; retried at the end of the stage
			return;
		}
		if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
			AABB blockBox = new AABB(pos);
			if (villager.getBoundingBox().intersects(blockBox)) {
				BlockPos spot = findStandingSpot(level, pos, villager.blockPosition(), blockBox, REACH - 0.5);
				if (spot != null) {
					walkTo(villager, spot, 0);
				}
			}
			if (++blockedTimer > 200) {
				blockedTimer = 0;
				site.defer();
			}
			return;
		}
		blockedTimer = 0;

		if (!free) {
			bag.remove(requirement.item(), requirement.count());
		}
		if (step.secondaryPos() != null && step.secondaryState() != null) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlock(step.secondaryPos(), step.secondaryState(), Block.UPDATE_ALL);
			level.blockUpdated(pos, state.getBlock());
		} else {
			BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
			level.setBlock(pos, shaped.isAir() ? state : shaped, Block.UPDATE_ALL);
		}
		applyBlockEntityData(level, pos, state, step.nbt());

		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
		level.gameEvent(villager, GameEvent.BLOCK_PLACE, pos);
		site.markPlaced();
	}

	/** Copies sign text, banner patterns etc. from the blueprint — but never container contents or loot. */
	private static void applyBlockEntityData(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
		if (nbt == null || !state.hasBlockEntity()) {
			return;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity == null) {
			return;
		}
		CompoundTag tag = nbt.copy();
		for (String key : new String[]{"Items", "LootTable", "LootTableSeed", "RecordItem", "Book", "item", "Bees", "SpawnData", "SpawnPotentials"}) {
			tag.remove(key);
		}
		try {
			blockEntity.loadWithComponents(tag, level.registryAccess());
			blockEntity.setChanged();
		} catch (RuntimeException ignored) {
			// Bad data in an imported blueprint should never crash the server; the block itself is placed.
		}
	}
}
