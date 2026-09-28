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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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

	/** -Daliveworkplace.debug=true logs what every builder is doing every two seconds. */
	private static final boolean DEBUG = Boolean.getBoolean("aliveworkplace.debug");

	/** Give up walking to one spot after this long and hop instead. */
	private static final int MAX_REACH_TICKS = 300;
	/** Place attempts on a spot someone is standing in before moving on to other blocks. */
	private static final int MAX_BLOCKED_ATTEMPTS = 25;
	private static final int STEP_ASIDE_TICKS = 40;

	private int workTimer;
	private int waitTimer;
	private int blockedAttempts;
	private int stuckTimer;
	private int reachTicks;
	private double bestDistance = Double.MAX_VALUE;
	@Nullable
	private BlockPos trackedTarget;
	/** Where to stand to work on {@link #trackedTarget}: in reach, and not on a spot that still needs a block. */
	@Nullable
	private BlockPos approachSpot;
	/** Set when the builder is standing where it needs to build: walk here (or hop, if that fails). */
	@Nullable
	private BlockPos stepAsideSpot;
	/** The block the builder must get out of while stepping aside. */
	@Nullable
	private BlockPos stepAsideFrom;
	private int stepAsideTicks;
	/** True while helping another builder's site (this tick). */
	private boolean helping;
	/** The step being worked on this tick. */
	@Nullable
	private BlockPos currentStep;
	/** Steps a helper gave up on (left for the lead), for the stage in {@link #avoidStage}. */
	private final Set<BlockPos> avoid = new HashSet<>();
	@Nullable
	private BuildPlan.Stage avoidStage;
	/** How far ahead of the lead helpers look for work. */
	private static final int HELP_WINDOW = 64;

	public BuilderWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.workSite(level, villager) != null;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.workSite(level, villager) != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		workTimer = 0;
		waitTimer = 0;
		stuckTimer = 0;
		reachTicks = 0;
		blockedAttempts = 0;
		trackedTarget = null;
		approachSpot = null;
		stepAsideSpot = null;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		BuildSite site = Builders.activeSite(level, villager);
		if (site != null) {
			site.release(villager.getUUID());
		}
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BuildSite site = Builders.workSite(level, villager);
		if (site == null) {
			return;
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			return;
		}
		helping = Builders.isHelping(villager);
		Optional<BlockPos> benchOpt = helping ? Optional.ofNullable(site.bench()) : Builders.benchPos(villager);
		if (benchOpt.isEmpty()) {
			return;
		}
		BlockPos bench = benchOpt.get();
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);

		// 0. Getting out of its own way.
		if (stepAsideSpot != null) {
			boolean clear = stepAsideFrom == null || !villager.getBoundingBox().intersects(new AABB(stepAsideFrom));
			if (clear) {
				stepAsideSpot = null;
			} else if (--stepAsideTicks <= 0) {
				hop(level, villager, stepAsideSpot);
				stepAsideSpot = null;
			} else {
				if (villager.blockPosition().equals(stepAsideSpot)) {
					// Already on the right block but leaning into the build spot: shuffle to its centre.
					villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					villager.getMoveControl().setWantedPosition(stepAsideSpot.getX() + 0.5, stepAsideSpot.getY(), stepAsideSpot.getZ() + 0.5, SPEED);
				} else {
					walkTo(villager, stepAsideSpot, 0);
				}
				return;
			}
		}

		// 1. Find the next step that actually needs work.
		BuildPlan.Step step = null;
		Action action = Action.NONE;
		if (helping) {
			step = helperStep(level, villager, site, plan, bench);
			if (step == null) {
				if (!bag.isEmpty()) {
					deposit(level, villager, site, plan, bag, bench); // hand back what it was carrying
				} else {
					idleNear(villager, site, plan);
				}
				return;
			}
			action = actionFor(level, site.stage(), step, bench);
		}
		for (int budget = SKIP_BUDGET; !helping && budget > 0 && !site.isDone(); budget--) {
			BuildPlan.Step candidate = site.current(plan);
			if (candidate == null) {
				site.finishList();
				continue;
			}
			action = actionFor(level, site.stage(), candidate, bench);
			if (action == Action.NONE) {
				site.advance();
			} else if (action == Action.SKIP) {
				site.defer(); // (lead only: helpers pick their own steps)
			} else {
				step = candidate;
				break;
			}
		}
		if (!helping && site.isDone()) {
			Builders.finish(level, villager, site);
			return;
		}
		if (step == null) {
			return;
		}
		currentStep = step.pos();

		if (DEBUG && gameTime % 40 == 0) {
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[builder {}] t={} at {} stage={} action={} target={} approach={} aside={} blocked={} stuck={} reach={} detail={}",
				villager.getId(), level.getDayTime() % 24000, villager.blockPosition().toShortString(), site.stage(), action, step.pos().toShortString(),
				approachSpot, stepAsideSpot, blockedAttempts, stuckTimer, reachTicks, site.detail() == null ? "" : site.detail().getString());
		}

		// 2. Materials.
		List<MaterialRules.Requirement> requirements = List.of();
		if (action == Action.PLACE) {
			requirements = step.requirements();
			if (requirements.isEmpty()) {
				defer(site, villager, step.pos());
				return;
			}
			if (!free) {
				for (MaterialRules.Requirement r : requirements) {
					if (!bag.has(r.item(), r.count())) {
						fetch(level, villager, site, plan, bag, bench, r);
						return;
					}
				}
			}
		} else if (bag.freeSlots() == 0) {
			deposit(level, villager, site, plan, bag, bench);
			return;
		}

		// 3. Walk into reach.
		if (!moveInReach(level, villager, site, plan, step.pos(), REACH, true)) {
			setStatus(site, BuildSite.Status.WORKING);
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(step.pos()));
		villager.setItemSlot(EquipmentSlot.MAINHAND, !requirements.isEmpty() ? new ItemStack(requirements.get(0).item()) : ItemStack.EMPTY);
		setStatus(site, BuildSite.Status.WORKING);

		// 4. Do the work, one block per workplaceBuildDelay ticks.
		if (workTimer > 0) {
			workTimer--;
			return;
		}
		workTimer = BuilderLevels.delay(level, villager);
		if (action == Action.BREAK && site.stage() == BuildPlan.Stage.DECONSTRUCT) {
			takeDown(level, villager, site, bag, bench, step);
		} else if (action == Action.BREAK) {
			breakBlock(level, villager, bag, bench, plan, step.pos());
		} else {
			place(level, villager, site, plan, bag, step, requirements, free);
		}
	}

	// --- deciding ----------------------------------------------------------------------------

	/**
	 * A helper's next step: its current claim if that still needs work, else the first step ahead of
	 * the lead that needs work and nobody else is on. The lead's own cursor is never touched.
	 */
	@Nullable
	private BuildPlan.Step helperStep(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BlockPos bench) {
		if (avoidStage != site.stage()) {
			avoid.clear();
			avoidStage = site.stage();
		}
		BuildPlan.Step leads = site.current(plan);
		BlockPos claimed = site.claim(villager.getUUID());
		BuildPlan.Step pick = null;
		for (BuildPlan.Step candidate : site.ahead(plan, HELP_WINDOW)) {
			BlockPos pos = candidate.pos();
			if (avoid.contains(pos) || leads != null && leads.pos().equals(pos) || site.claimedByOther(villager.getUUID(), pos)) {
				continue;
			}
			Action a = actionFor(level, site.stage(), candidate, bench);
			if (a != Action.BREAK && a != Action.PLACE) {
				continue;
			}
			if (pos.equals(claimed)) {
				pick = candidate;
				break;
			}
			if (pick == null) {
				pick = candidate;
				if (claimed == null) {
					break;
				}
			}
		}
		if (pick == null) {
			site.release(villager.getUUID());
			site.seen(villager.getUUID(), level.getGameTime());
		} else {
			site.claim(villager.getUUID(), pick.pos(), level.getGameTime());
		}
		return pick;
	}

	/** Nothing to help with right now (the lead is at the end of a stage): hang around the site. */
	private static void idleNear(Villager villager, BuildSite site, BuildPlan plan) {
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		if (site.bench() != null && !near(villager, site.bench(), 6)) {
			walkTo(villager, site.bench(), 3);
		}
	}

	/** A step can't be done now. The lead retries it at the end of the stage; a helper leaves it to the lead. */
	private void defer(BuildSite site, Villager villager, BlockPos pos) {
		if (helping) {
			avoid.add(pos);
			site.release(villager.getUUID());
		} else {
			site.defer();
		}
	}

	/** Only the lead reports the site's status; helpers would just make it flicker. */
	private void setStatus(BuildSite site, BuildSite.Status status) {
		if (!helping) {
			site.setStatus(status);
		}
	}

	private void setDetail(BuildSite site, @Nullable Component detail) {
		if (!helping) {
			site.setDetail(detail);
		}
	}

	private static Action actionFor(ServerLevel level, BuildPlan.Stage stage, BuildPlan.Step step, BlockPos bench) {
		BlockPos pos = step.pos();
		BlockState world = level.getBlockState(pos);
		BlockState wanted = step.state();
		if (stage == BuildPlan.Stage.DECONSTRUCT) {
			// Take down only what the blueprint put there; leave containers and anything else alone.
			return MaterialRules.matches(world, wanted) && !isProtected(level, pos, world, bench) ? Action.BREAK : Action.NONE;
		}
		if (MaterialRules.matches(world, wanted)) {
			return Action.NONE;
		}
		boolean worldEmpty = world.isAir() || world.getBlock() instanceof LiquidBlock;

		if (stage == BuildPlan.Stage.LANDSCAPE) {
			if (wanted.isAir()) {
				// Dig away natural ground only; whatever else has turned up there since stays.
				return !worldEmpty && BuildPlan.isTerrain(world) && !isProtected(level, pos, world, bench) ? Action.BREAK : Action.NONE;
			}
			// Fill a hole: only into air or grass, never over something else.
			return world.isAir() || world.canBeReplaced() && world.getFluidState().isEmpty() ? Action.PLACE : Action.NONE;
		}

		if (stage == BuildPlan.Stage.CLEAR) {
			if (worldEmpty) {
				return Action.NONE;
			}
			boolean wantedEmpty = wanted.isAir();
			// The top of a door or the head of a bed isn't placed on its own, but it needs the spot free: an upgrade
			// putting a door where a wall was has to take that bit of wall down first.
			boolean skipped = MaterialRules.classify(wanted, step.nbt()) == MaterialRules.Kind.SKIP && !MaterialRules.isSecondaryHalf(wanted);
			if (!wantedEmpty && (world.canBeReplaced() || skipped)) {
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
	 * Walks until {@code target} is within {@code reach} (eye to block centre), heading for a spot that
	 * does not still need a block (so the builder is not in its own way later). If it makes no progress
	 * for a while, or takes too long — walled in, target over a gap — it hops to that spot instead.
	 * Returns true once in reach.
	 */
	private boolean moveInReach(ServerLevel level, Villager villager, BuildSite site, @Nullable BuildPlan plan, BlockPos target,
								double reach, boolean deferIfImpossible) {
		double distance = villager.getEyePosition().distanceTo(Vec3.atCenterOf(target));
		if (distance <= reach) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			trackedTarget = null;
			return true;
		}
		if (!target.equals(trackedTarget)) {
			trackedTarget = target;
			bestDistance = distance;
			stuckTimer = 0;
			reachTicks = 0;
			approachSpot = findStandingSpot(level, plan, target, villager.blockPosition(), null, reach - 0.5);
		}
		BlockPos goal = approachSpot != null ? approachSpot : target;
		walkTo(villager, goal, approachSpot != null ? 0 : 1);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (distance < bestDistance - 0.3) {
			bestDistance = distance;
			stuckTimer = 0;
		}
		if (++stuckTimer > STUCK_TICKS || ++reachTicks > MAX_REACH_TICKS) {
			stuckTimer = 0;
			reachTicks = 0;
			BlockPos spot = approachSpot != null ? approachSpot : findStandingSpot(level, null, target, villager.blockPosition(), null, reach - 0.3);
			if (spot != null) {
				hop(level, villager, spot);
			} else if (deferIfImpossible) {
				defer(site, villager, target);
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

	/**
	 * Closest place to stand (feet position) from which {@code target} is in reach, avoiding {@code avoid}
	 * and, when a plan is given, spots where the build still needs a solid block (feet or head).
	 */
	@Nullable
	static BlockPos findStandingSpot(ServerLevel level, @Nullable BuildPlan plan, BlockPos target, BlockPos from, @Nullable AABB avoid,
									 double maxEyeDistance) {
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
					if (plan != null && (plan.needsSolidAt(level, p) || plan.needsSolidAt(level, p.above()))) {
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
		List<Item> accepted = MaterialFamilies.accepted(requirement.item());
		long available = 0;
		for (Item item : accepted) {
			available += SupplyContainers.count(level, supplies, item);
		}
		if (available < needed) {
			if (site.stage() == BuildPlan.Stage.LANDSCAPE) {
				// Out of dirt for tidying up: leave that hole rather than wait (the building is finished).
				if (currentStep != null) {
					defer(site, villager, currentStep);
				}
				return;
			}
			if (takeFromCrewmate(level, villager, site, plan, bag, requirement.item(), needed)) {
				return;
			}
			waitForMaterials(level, villager, site, plan, bag, bench, supplies);
			return;
		}
		waitTimer = 0;
		setStatus(site, BuildSite.Status.FETCHING);
		BlockPos source = null;
		for (Item item : accepted) {
			source = SupplyContainers.firstWith(level, supplies, item);
			if (source != null) {
				break;
			}
		}
		if (source == null) {
			return;
		}
		if (!moveInReach(level, villager, site, plan, source, CONTAINER_REACH, false)) {
			return;
		}

		// At the chest: take what the next stretch of work needs, current block first. Helpers only
		// take a handful for the blocks they are on, so they never sit on the lead's materials.
		Map<Item, Integer> wanted = new LinkedHashMap<>();
		wanted.put(requirement.item(), requirement.count() + (helping ? 3 : 0));
		for (BuildPlan.Step s : helping ? List.<BuildPlan.Step>of() : site.upcoming(plan, LOOKAHEAD)) {
			if (!MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
				for (MaterialRules.Requirement r : s.requirements()) {
					wanted.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		boolean tookAny = false;
		for (Map.Entry<Item, Integer> e : wanted.entrySet()) {
			int want = e.getValue() - bag.count(e.getKey());
			int take = Math.min(want, bag.spaceFor(e.getKey()));
			// The exact block first; otherwise a free variant of it (Chipped), turned into the one we need.
			for (Item source2 : MaterialFamilies.accepted(e.getKey())) {
				if (take <= 0) {
					break;
				}
				int got = SupplyContainers.extract(level, supplies, source2, take);
				if (got > 0) {
					tookAny = true;
					bag.addAll(e.getKey(), got);
					take -= got;
				}
			}
		}
		if (tookAny) {
			level.playSound(null, source, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	/**
	 * The chests are out of something but another builder on this site is carrying spares: walk over
	 * and get some ("pass me those planks"). Returns false if nobody has any to spare.
	 */
	private boolean takeFromCrewmate(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, Item item, int needed) {
		List<java.util.UUID> crew = new java.util.ArrayList<>(site.helpers(level.getGameTime()));
		if (site.builder() != null) {
			crew.add(0, site.builder());
		}
		for (java.util.UUID id : crew) {
			if (id.equals(villager.getUUID()) || !(level.getEntity(id) instanceof Villager mate) || !mate.isAlive()) {
				continue;
			}
			BuilderBag mateBag = mate.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
			// Whatever the mate needs for the block it is on stays with it.
			BuildPlan.Step mateStep = id.equals(site.builder()) ? site.current(plan) : null;
			BlockPos mateClaim = site.claim(id);
			int keep = 0;
			for (BuildPlan.Step s : mateStep != null ? List.of(mateStep) : List.<BuildPlan.Step>of()) {
				for (MaterialRules.Requirement r : s.requirements()) {
					keep += r.item() == item ? r.count() : 0;
				}
			}
			if (mateClaim != null) {
				keep += 1;
			}
			int spare = mateBag.count(item) - keep;
			if (spare < needed) {
				continue;
			}
			setStatus(site, BuildSite.Status.FETCHING);
			if (!moveInReach(level, villager, site, plan, mate.blockPosition(), CONTAINER_REACH, false)) {
				return true;
			}
			int take = Math.min(Math.min(spare, needed + (helping ? 3 : 32)), bag.spaceFor(item));
			if (take > 0) {
				mateBag.remove(item, take);
				bag.addAll(item, take);
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.EntityTracker(mate, true));
			}
			return true;
		}
		return false;
	}

	private void waitForMaterials(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench,
								  List<BlockPos> supplies) {
		if (helping) {
			// A helper doesn't wait: it leaves this block to the lead and looks for one it has materials for.
			if (currentStep != null) {
				defer(site, villager, currentStep);
			}
			return;
		}
		boolean firstTick = site.status() != BuildSite.Status.WAITING_FOR_MATERIALS;
		setStatus(site, BuildSite.Status.WAITING_FOR_MATERIALS);
		if (!near(villager, bench, 3)) {
			walkTo(villager, bench, 2);
		}
		if (firstTick || --waitTimer <= 0) {
			waitTimer = WAIT_RECHECK;
			site.setMissing(Builders.computeMissing(level, site, plan, bag, supplies));
			if (firstTick && !helping) {
				Builders.notifyWaiting(level, villager, site);
			}
		}
	}

	private void deposit(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, plan.bounds());
		BlockPos target = supplies.isEmpty() ? bench : supplies.get(0);
		if (!moveInReach(level, villager, site, plan, target, CONTAINER_REACH, false)) {
			return;
		}
		Set<Item> keep = new HashSet<>();
		boolean keepNothing = helping || site.stage() == BuildPlan.Stage.DECONSTRUCT;
		for (BuildPlan.Step s : keepNothing ? List.<BuildPlan.Step>of() : site.upcoming(plan, LOOKAHEAD)) {
			for (MaterialRules.Requirement r : s.requirements()) {
				keep.add(r.item());
			}
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

	/**
	 * Deconstruction: removes the block and keeps exactly what it cost to build (glass comes back as
	 * glass, a potted flower as a pot and a flower), like a careful builder rather than a pickaxe.
	 */
	private void takeDown(ServerLevel level, Villager villager, BuildSite site, BuilderBag bag, BlockPos bench, BuildPlan.Step step) {
		BlockPos pos = step.pos();
		BlockState state = level.getBlockState(pos);
		if (step.secondaryPos() != null) {
			level.setBlock(step.secondaryPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		level.removeBlockEntity(pos);
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.levelEvent(2001, pos, Block.getId(state)); // break particles and sound
		level.gameEvent(villager, GameEvent.BLOCK_DESTROY, pos);
		for (MaterialRules.Requirement r : step.requirements()) {
			int rest = bag.addAll(r.item(), r.count());
			if (rest > 0) {
				Builders.dropNear(level, bench, new ItemStack(r.item(), rest));
			}
		}
		if (!helping) {
			site.markPlaced();
		} else {
			site.countPlaced();
			site.release(villager.getUUID());
		}
		BuilderLevels.onPlaced(level, villager, site);
	}

	private void place(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BuildPlan.Step step,
					   List<MaterialRules.Requirement> requirements, boolean free) {
		BlockPos pos = step.pos();
		BlockState state = step.state();

		// Two-block blocks need their second spot free.
		if (step.secondaryPos() != null) {
			BlockState there = level.getBlockState(step.secondaryPos());
			if (!there.isAir() && !there.canBeReplaced() && !MaterialRules.matches(there, step.secondaryState())) {
				defer(site, villager, pos);
				return;
			}
		}
		if (!state.canSurvive(level, pos)) {
			defer(site, villager, pos); // nothing to attach to yet; retried at the end of the stage
			return;
		}
		if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
			handleObstruction(level, villager, site, plan, pos);
			return;
		}
		blockedAttempts = 0;
		setDetail(site, null);

		if (!free) {
			for (MaterialRules.Requirement r : requirements) {
				bag.remove(r.item(), r.count());
				// What's left over goes back in the bag (the empty bucket after pouring water).
				ItemStack leftover = r.item().getCraftingRemainingItem() == null ? ItemStack.EMPTY : new ItemStack(r.item().getCraftingRemainingItem(), r.count());
				if (!leftover.isEmpty()) {
					ItemStack rest = bag.add(leftover);
					if (!rest.isEmpty()) {
						Builders.dropNear(level, pos, rest);
					}
				}
			}
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

		if (state.getBlock() instanceof LiquidBlock) {
			level.playSound(null, pos, state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
				SoundSource.BLOCKS, 1f, 1f);
		} else {
			SoundType sound = state.getSoundType();
			level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
		}
		level.gameEvent(villager, GameEvent.BLOCK_PLACE, pos);
		if (helping) {
			site.countPlaced();
			site.release(villager.getUUID());
		} else {
			site.markPlaced();
		}
		BuilderLevels.onPlaced(level, villager, site);
	}

	/**
	 * Something is standing where the next block goes. The builder steps out of its own way, shoos
	 * animals and Pokémon, asks players to move, and after a while works on other blocks first.
	 */
	private void handleObstruction(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BlockPos pos) {
		AABB box = new AABB(pos);
		List<Entity> blockers = level.getEntities((Entity) null, box, e -> e.isAlive() && e.blocksBuilding && !e.isSpectator());
		if (blockers.contains(villager)) {
			BlockPos spot = findStandingSpot(level, plan, pos, villager.blockPosition(), box, REACH - 0.5);
			if (spot == null) {
				spot = findStandingSpot(level, null, pos, villager.blockPosition(), box, REACH - 0.5);
			}
			if (spot != null && ++blockedAttempts <= MAX_BLOCKED_ATTEMPTS) {
				stepAsideSpot = spot;
				stepAsideFrom = pos;
				stepAsideTicks = STEP_ASIDE_TICKS;
			} else {
				blockedAttempts = 0;
				defer(site, villager, pos);
			}
			return;
		}
		Entity first = blockers.isEmpty() ? null : blockers.get(0);
		for (Entity e : blockers) {
			if (e instanceof Player player) {
				player.displayClientMessage(Component.translatable("message.aliveworkplace.in_the_way", villager.getDisplayName()), true);
			} else {
				shoo(level, plan, e, pos, blockedAttempts);
			}
		}
		if (first != null) {
			setDetail(site, Component.translatable("message.aliveworkplace.status.blocked_by", first.getDisplayName(), pos.getX(), pos.getY(), pos.getZ()));
		}
		if (++blockedAttempts > MAX_BLOCKED_ATTEMPTS) {
			blockedAttempts = 0;
			setDetail(site, null);
			defer(site, villager, pos); // try the rest first; this spot is retried at the end of the stage
		}
	}

	/** Nudges a mob off a build spot; if it keeps standing there, moves it next to the site. */
	private static void shoo(ServerLevel level, BuildPlan plan, Entity entity, BlockPos pos, int attempt) {
		if (attempt >= 4) {
			BlockPos spot = findStandingSpot(level, plan, entity.blockPosition(), entity.blockPosition(), new AABB(pos), 4.0);
			if (spot != null) {
				level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5, entity.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
				entity.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				return;
			}
		}
		Vec3 away = entity.position().subtract(Vec3.atCenterOf(pos)).multiply(1, 0, 1);
		if (away.lengthSqr() < 0.01) {
			away = new Vec3(level.random.nextDouble() - 0.5, 0, level.random.nextDouble() - 0.5);
		}
		away = away.normalize().scale(0.45);
		entity.push(away.x, 0.25, away.z);
		entity.hurtMarked = true;
		if (entity instanceof Mob mob) {
			mob.getNavigation().stop();
		}
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
		CompoundTag tag = BlockEntityData.sanitize(level, pos, state, blockEntity, nbt);
		if (tag == null) {
			return;
		}
		try {
			blockEntity.loadWithComponents(tag, level.registryAccess());
			blockEntity.setChanged();
		} catch (RuntimeException ignored) {
			// Bad data in an imported blueprint should never crash the server; the block itself is placed.
		}
	}
}
