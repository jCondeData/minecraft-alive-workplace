package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.HashSet;
import java.util.HashMap;
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
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
	/** How far ahead a builder takes for, at chests far from its site (23.5): fewer long walks. */
	private static final int FAR_LOOKAHEAD = 4 * LOOKAHEAD;
	/** Already-finished steps skipped per tick (cheap checks). */
	private static final int SKIP_BUDGET = 256;
	/** B508: how many times a build goes back over its structure and decoration for blocks broken since they were laid. */
	static final int MOST_RECHECKS = 2;
	private static final int STUCK_TICKS = 100;
	/** How long a builder with no path to where it's going waits before hopping there. */
	private static final int NO_PATH_TICKS = 30;
	/**
	 * How long a builder that is walking somewhere may stand on the spot before it hops there. Its path said it could get
	 * through, but it can't (under a trapdoor flower box, into a loft): with the {@link #STUCK_TICKS} wait it stood
	 * still five seconds a block, and a crew's last blocks took a third longer when one of them hit it.
	 */
	private static final int STILL_TICKS = 40;
	/** Moving less than this far (blocks) in a tick counts as standing still. */
	private static final double STILL_DISTANCE = 0.02;
	private static final int WAIT_RECHECK = 100;

	/** PLAYER_BLOCK: a player's block in the way of the Steward's build, left there (27.19); skipped like SKIP. */
	private enum Action { NONE, BREAK, PLACE, SKIP, PLAYER_BLOCK }

	/** -Daliveworkplace.debug=true logs what every builder is doing every two seconds. */
	private static final boolean DEBUG = Boolean.getBoolean("aliveworkplace.debug");

	/** Give up walking to one spot after this long and hop instead. */
	private static final int MAX_REACH_TICKS = 300;
	/** Place attempts on a spot someone is standing in before moving on to other blocks. */
	private static final int MAX_BLOCKED_ATTEMPTS = 25;
	private static final int STEP_ASIDE_TICKS = 40;
	/** B92: a builder standing inside a block walks out for this long, then hops (shorter than stepping aside: it is clipping). */
	private static final int STEP_OUT_TICKS = 20;
	/** B92: how far inside a block the builder's body must be to count as standing in it (leaning on a wall isn't). */
	private static final double INSIDE_MARGIN = 0.05;

	private int workTimer;
	/** This builder's bag, this tick. */
	@Nullable
	private BuilderBag bagNow;
	private int waitTimer;
	private int blockedAttempts;
	private int stuckTimer;
	private int reachTicks;
	/** Ticks in a row the builder has stood still while walking to {@link #trackedTarget}. */
	private int stillTicks;
	@Nullable
	private Vec3 lastWalkPos;
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
	/** B92: ticks until the builder looks again for a way out of the block it stands in, after finding none. */
	private int stepOutWait;
	/** True while helping another builder's site (this tick). */
	private boolean helping;
	/** Ticks the lead has waited this stage for helpers to finish the last blocks they claimed (23.1a). */
	private int helperWait;
	@Nullable
	private BuildPlan.Stage helperWaitStage;
	/** After this long the lead does the blocks helpers claimed itself (a helper that claims and never places). */
	private static final int HELPER_WAIT = 200;
	/** B254: the lead's site's {@link BuildSite#progressMark} as last seen, and the game tick it last changed (not saved). */
	private long lastMark = Long.MIN_VALUE;
	private long markSince;
	/**
	 * B254: how long a crew's site may stand still before the lead gives up a block that waits on a given-up one under
	 * it (nobody could lay that one): without it the column above waited for good (B139).
	 */
	private static final int GIVEN_UP_WAIT = 200;
	/** True while this builder works on a Wonder with a crew, in columns (35.5). */
	private boolean crew;
	/** True while the lead works on a block away from its cursor, this tick (23.1a). */
	private boolean offCursor;
	/** The villager this behaviour belongs to (each villager's brain has its own). */
	@Nullable
	private java.util.UUID self;
	/** The step being worked on this tick. */
	@Nullable
	private BlockPos currentStep;
	/** Steps a helper gave up on (left for the lead), for the stage in {@link #avoidStage}. */
	private final Set<BlockPos> avoid = new HashSet<>();
	/** B254: blocks the lead gave up on that this builder then failed to lay as well, for the stage in {@link #avoidStage}. */
	private final Set<BlockPos> retakeFailed = new HashSet<>();
	@Nullable
	private BuildPlan.Stage avoidStage;
	/** How close a crewmate must be to pass materials over without walking to them (23.1a). */
	private static final double PASS_DISTANCE = 8;
	/** How far ahead of the lead helpers look for work, for each of the crew; also what a helper fetches for at once. */
	private static final int HELP_WINDOW = 32;

	/** How far ahead of the lead helpers look for work: a stretch for each of the crew (23.1a). */
	private static int helpWindow(ServerLevel level, BuildSite site) {
		return HELP_WINDOW * (1 + site.helpers(level.getGameTime()).size());
	}
	/** A site this far (bench to the site's middle) is far from its chests: stock up before clearing it (B39). */
	static final int FAR_FROM_CHESTS = 16;
	/** The site this builder has stocked up for (or found nothing to stock up with) before clearing. */
	@Nullable
	private java.util.UUID stockedFor;
	/** The scaffolding this builder is putting up or taking down (35.6). */
	private final Scaffolds scaffolds = new Scaffolds();

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
		Scaffolds.toFoot(villager); // 35.6: off to bed or away: down the column first, never off its top
		BuildSite site = Builders.activeSite(level, villager);
		if (site != null) {
			site.release(villager.getUUID());
			if (!Builders.isHelping(villager)) {
				site.setLeadClaim(null);
			}
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
		self = villager.getUUID();
		Optional<BlockPos> benchOpt = helping ? Optional.ofNullable(site.bench()) : Builders.siteBench(level, villager, site);
		if (benchOpt.isEmpty()) {
			return;
		}
		BlockPos bench = benchOpt.get();
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		bag.setSlots(engineeringSlots(villager)); // 9 more stacks a level of Engineering while the Great Library stands (35.14)
		bagNow = bag;
		boolean free = Rules.on(level, ModGameRules.FREE_MATERIALS);

		// 0. Getting out of its own way.
		if (stepAsideSpot != null) {
			boolean clear = stepAsideFrom == null || !villager.getBoundingBox().intersects(new AABB(stepAsideFrom));
			if (clear) {
				stepAsideSpot = null;
			} else if (--stepAsideTicks <= 0) {
				// B60: the spot was picked 40 ticks ago; a crewmate may have built on it since. Never hop into a block.
				BlockPos spot = canStandAt(level, stepAsideSpot) ? stepAsideSpot : freshSpot(level, plan, stepAsideFrom, villager, new AABB(stepAsideFrom));
				if (spot != null) {
					hop(level, villager, spot);
				}
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

		// 0b. B92: standing still inside a block (a door a crewmate shut on it in a doorway, a block that changed shape
		// around it): walk out of it like out of a build spot, rather than stand there clipping until the next block is out
		// of reach. Nothing here is saved: after a reload the builder is found inside the block again and steps out again.
		if (stepOutWait > 0) {
			stepOutWait--;
		} else if (villager.getNavigation().isDone() && !villager.isPassenger()) {
			BlockPos inside = insideBlock(level, villager);
			if (inside != null) {
				BlockPos spot = freshSpot(level, plan, inside, villager, new AABB(inside));
				if (spot == null) {
					stepOutWait = STEP_ASIDE_TICKS;
				} else {
					stepAsideSpot = spot;
					stepAsideFrom = inside;
					stepAsideTicks = STEP_OUT_TICKS;
					walkTo(villager, spot, 0);
					return;
				}
			}
		}

		// 1. Find the next step that actually needs work.
		BuildPlan.Step step = null;
		Action action = Action.NONE;
		// 35.5: a Wonder's crew works in columns, each member on one of its own (a lead alone works as ever).
		crew = site.wonder() != null && (helping || !site.helpers(gameTime).isEmpty());
		if (helping) {
			step = crew ? crewStep(level, villager, site, plan, bench) : helperStep(level, villager, site, plan, bench);
			if (step == null) {
				if (!bag.isEmpty() && handToLead(level, villager, site, plan, bag)) {
					return; // B66: on its way to the lead with what the rest of the build needs
				}
				if (crew && site.stage() != BuildPlan.Stage.DONE) {
					// 35.5: a crew member waiting for a layer to fill keeps what it carries for its next column and
					// stays at the Wonder rather than walking to and fro
					villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				} else if (!bag.isEmpty()) {
					deposit(level, villager, site, plan, bag, bench); // hand back what it was carrying
				} else {
					idleNear(villager, site, plan);
				}
				return;
			}
			action = actionFor(level, site, step, bench);
		}
		int postponed = 0;
		if (site.stage() != helperWaitStage) {
			helperWait = 0;
			helperWaitStage = site.stage();
		}
		if (!helping) {
			site.helpers(gameTime); // lets go of the claims of helpers that have left, so the lead never waits on them
			long mark = site.progressMark();
			if (mark != lastMark) {
				lastMark = mark;
				markSince = gameTime;
			}
		}
		boolean standingStill = !helping && gameTime - markSince >= GIVEN_UP_WAIT;
		boolean gaveUpOver = false;
		BuildPlan.Step crewPick = null;
		if (!helping && crew && !site.isDone()) {
			crewPick = crewStep(level, villager, site, plan, bench); // the lead of a crew takes a column too
			if (crewPick != null) {
				step = crewPick;
				action = actionFor(level, site, crewPick, bench);
			}
		}
		for (int budget = SKIP_BUDGET; !helping && step == null && budget > 0 && !site.isDone(); budget--) {
			BuildPlan.Step candidate = site.current(plan);
			if (candidate == null) {
				// 35.6: the stage is done: its scaffolding comes down first (into the bag, so back to the chests)
				if (site.endsStage() && !site.scaffolds().isEmpty()) {
					if (bag.spaceFor(net.minecraft.world.item.Items.SCAFFOLDING) <= 0) {
						deposit(level, villager, site, plan, bag, bench);
					} else if (scaffolds.takeDown(level, villager, site, bag, bench, free)) {
						setStatus(site, BuildSite.Status.WORKING);
					}
					return;
				}
				if (site.endsStage() && io.github.jcondedata.aliveworkplace.mechanic.Contraptions.goBack(level, site, plan)) {
					continue; // 39.5: a kinetic part broken since it went in: back over the parts, before the build is called done
				}
				if (crew && site.endsStage() && !standingStill && crewOnGivenUp(site, gameTime)) {
					setStatus(site, BuildSite.Status.WORKING);
					return; // B254: a member is laying a block the lead gave up on: the stage waits for it, not for long
				}
				if (site.endsStage() && site.stage() == BuildPlan.Stage.LANDSCAPE && backToBroken(level, site, plan)) {
					continue; // B508: a block laid earlier is gone (broken by a player, a mob, an explosion): put back first
				}
				if (site.endsStage() && site.stage() == BuildPlan.Stage.LANDSCAPE) {
					// B300: nothing left floating. Once the ground is levelled, not at the end of DECORATION: a pot on a
					// grave over a hollow on a hillside has its ground filled in by LANDSCAPE
					io.github.jcondedata.aliveworkplace.furnish.Resting.settle(level, plan);
				}
				site.finishList();
				continue;
			}
			action = actionFor(level, site, candidate, bench);
			if (action == Action.NONE || crew && site.placedThisStage(candidate.pos()) || site.rechecks() > 0 && site.leftOut(candidate.pos())) {
				// (B508: going back for broken blocks, the ones given up earlier stay given up)
				site.advance(); // (B139: a crew member placed it, and it has changed by itself since: it stays)
			} else if (action == Action.PLACE && outForLandscape(level, site, candidate, bag)) {
				site.defer(); // nothing to fill it with: comes round once more at the end, then is left
			} else if (crew && action == Action.SKIP && site.isRetrying() && !standingStill && crewHasWork(level, site, plan, bench)) {
				// 35.5: the lead of a crew reaches its retries while the crew is still building below: a block with nothing
				// to hang on yet may get it from them. Not given up while they have work; tried again after it. B326: not
				// once the crew has stood still a while (the work it "has" may be the very blocks waiting on this one).
				if (postponed++ < site.retryLeft()) {
					site.postpone();
				} else {
					action = Action.NONE;
					break;
				}
			} else if (action == Action.SKIP || action == Action.PLAYER_BLOCK) {
				if (action == Action.PLAYER_BLOCK && site.isRetrying()) {
					site.playerBlockInTheWay(); // 27.19: counted once, as it is skipped for good
				}
				site.defer(); // (lead only: helpers pick their own steps)
			} else if (crew && site.isRetrying() && standingStill && action == Action.PLACE && overGivenUp(level, site, plan, candidate)) {
				// B254: it waits on a block under it the lead gave up and nobody has laid since, and the crew has done
				// nothing for a while: given up too (a hole, never a block in the air), rather than wait for good (B139).
				// B270: every one waiting so in this stand-still, not one each 200 ticks: the Hanging Gardens' crew stood
				// still for minutes giving up the blocks over a terrace's holes one by one. A crewmate who can lay a hole
				// later in the stage still does, and the block over it after (given-up blocks are taken again once clear).
				site.defer();
				gaveUpOver = true;
			} else if (crew && crewBlocked(level, villager, site, plan, candidate, bench)
				|| helperWait < HELPER_WAIT && site.claimedByOther(villager.getUUID(), candidate.pos())) {
				// A helper is on this block (23.1a): leave it to them rather than both walking over to it. If they don't
				// get to it, it comes round again at the end of the stage (or, while retrying, at the end of the list;
				// if every step left is a helper's, the lead waits for them, but not for long).
				if (!site.isRetrying()) {
					site.defer();
				} else if (crew && standingStill && takeOver(level, villager, site, plan, candidate, bench)) {
					// B326: the crew has stood still while retrying, the lead waiting on a member who holds this block's
					// column and never lays it (the folly's dome stood idle 30 s and more near its top): the lead takes the
					// column over and lays the block itself (it can put up scaffolding; a member can't). Only a block with
					// what goes under it there: one waiting on a block under it waits for that one, which the lead takes so.
					step = candidate;
					break;
				} else if (postponed++ < site.retryLeft()) {
					site.postpone();
				} else {
					helperWait++;
					action = Action.NONE;
					break;
				}
			} else {
				step = candidate;
				if (crew) {
					site.takeColumn(villager.getUUID(), plan.column(candidate.pos()));
				}
				break;
			}
		}
		if (gaveUpOver) {
			lastMark = site.progressMark(); // B270: giving those up isn't the crew moving on: the stand-still goes on
		}
		if (!helping && site.isDone()) {
			Builders.finish(level, villager, site);
			return;
		}
		if (step == null) {
			return;
		}
		// 23.1a: with a crew, the lead doesn't walk off to its next block while there's work for the crew within reach
		// (the helpers had taken everything near it, so it walked as much as a builder on its own).
		offCursor = crewPick != null;
		if (!helping && !crew && !site.helpers(gameTime).isEmpty() && villager.getEyePosition().distanceTo(Vec3.atCenterOf(step.pos())) > REACH) {
			BuildPlan.Step near = workInReach(level, villager, site, plan, bench);
			if (near != null) {
				step = near;
				action = actionFor(level, site, near, bench);
				offCursor = true;
			}
		}
		if (!helping) {
			site.setLeadClaim(offCursor ? step.pos() : null);
		}
		currentStep = step.pos();

		if (DEBUG && gameTime % 40 == 0) {
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[builder {}] t={} at {} stage={} action={} target={} approach={} aside={} blocked={} stuck={} reach={} detail={}",
				villager.getId(), level.getDayTime() % 24000, villager.blockPosition().toShortString(), site.stage(), action, step.pos().toShortString(),
				approachSpot, stepAsideSpot, blockedAttempts, stuckTimer, reachTicks, site.detail() == null ? "" : site.detail().getString());
		}

		// 1b. A new site far from the chests (B39): take what the build starts with before clearing it, so the builder
		// doesn't walk all the way back to its chests the moment the site is clear. Only from stock: never waits here.
		if (!helping && !free && site.stage() == BuildPlan.Stage.CLEAR && action == Action.BREAK && !site.id().equals(stockedFor)) {
			MaterialRules.Requirement first = firstNeed(level, site, plan, bag);
			if (first == null || !farFromChests(plan, bench) || !inStock(level, villager, site, bench, plan, first)) {
				stockedFor = site.id();
			} else {
				fetch(level, villager, site, plan, bag, bench, first);
				return;
			}
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

		// 2b. 35.6: nowhere to stand within reach of it: put up scaffolding first (fetched like any material).
		if (!helping && scaffolds.needed(level, site, step.pos())) {
			int toPlace = scaffolds.toPlace(level);
			if (!free && bag.count(net.minecraft.world.item.Items.SCAFFOLDING) < toPlace) {
				MaterialRules.Requirement need = new MaterialRules.Requirement(net.minecraft.world.item.Items.SCAFFOLDING, toPlace);
				if (inStock(level, villager, site, bench, plan, need)) {
					if (site.missing().containsKey(net.minecraft.world.item.Items.SCAFFOLDING)) {
						// B499: there's enough now, so the board stops asking for it
						Map<net.minecraft.world.item.Item, Integer> missing = new LinkedHashMap<>(site.missing());
						missing.remove(net.minecraft.world.item.Items.SCAFFOLDING);
						site.setMissing(missing);
					}
					// 35.6a: takes enough for the column to grow on as the part rises, not one or two a trip
					int carry = (int) Math.max(toPlace, Math.min(Scaffolds.CARRY, scaffoldingOnHand(level, site, plan)));
					fetch(level, villager, site, plan, bag, bench, new MaterialRules.Requirement(net.minecraft.world.item.Items.SCAFFOLDING, carry));
				} else {
					// None to be had: asked for on the requests board, and the step is left as it always was (put off,
					// then skipped and counted). A build never waits on scaffolding (coordinator, 2026-10-12).
					site.setMissing(scaffolds.addMissing(level, site.missing(), bag, SupplyContainers.find(level, bench, plan.supplyExclude())));
					scaffolds.giveUp();
					defer(site, villager, step.pos());
				}
				return;
			}
			trackedTarget = null;
			setStatus(site, BuildSite.Status.WORKING);
			scaffolds.erect(level, villager, site, bag, free);
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
		if (!BuildBudget.take(level)) {
			return; // 25.3: the level's block changes for this tick are spent; try again next tick
		}
		// B515: this tick is the first of the delay, so the next block comes exactly delay ticks later (not delay + 1)
		workTimer = Math.max(0, BuilderLevels.delay(level, villager) - 1);
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
			retakeFailed.clear();
			avoidStage = site.stage();
		}
		BuildPlan.Step leads = site.current(plan);
		BlockPos claimed = site.claim(villager.getUUID());
		BuildPlan.Step pick = null;
		double pickDistance = Double.MAX_VALUE;
		for (BuildPlan.Step candidate : site.ahead(plan, helpWindow(level, site))) {
			BlockPos pos = candidate.pos();
			if (avoid.contains(pos) || leads != null && leads.pos().equals(pos) || site.claimedByOther(villager.getUUID(), pos)) {
				continue;
			}
			Action a = actionFor(level, site, candidate, bench);
			if (a != Action.BREAK && a != Action.PLACE) {
				continue;
			}
			if (pos.equals(claimed)) {
				pick = candidate;
				break;
			}
			// 23.1a: the free block nearest the helper, not the first one: helpers that all went for the block right
			// after the lead's walked about the site more than they built.
			double d = villager.distanceToSqr(Vec3.atCenterOf(pos));
			if (pick == null || d < pickDistance) {
				pick = candidate;
				pickDistance = d;
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

	// --- a Wonder's crew (35.5) -----------------------------------------------------------------

	/** Most steps a crew member takes for in one trip: the rest of its column and its share of the stage ({@link #crewShare}, 35.5). */
	static final int CREW_SHARE = 128;
	/** How many layers above the part's lowest unfinished layer (clearing: below its highest) a crew may work. */
	static final int CREW_LAYERS = 2;

	/**
	 * A crew member's next step (35.5): the first step with work, in the stage's order, of the column it is on, else of
	 * the free column (nobody else on it) whose next step is nearest to it. Never more than {@link #CREW_LAYERS} layers
	 * above the part's lowest unfinished layer (clearing: below its highest), and never a foundation or structure block
	 * with the one the drawing puts under it not there yet. Null when no free column has work it may do now.
	 */
	@Nullable
	private BuildPlan.Step crewStep(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BlockPos bench) {
		if (avoidStage != site.stage()) {
			avoid.clear();
			retakeFailed.clear();
			avoidStage = site.stage();
		}
		java.util.UUID me = villager.getUUID();
		if (site.stage() == BuildPlan.Stage.DONE) {
			site.dropColumn(me);
			return null;
		}
		Map<Long, List<BuildPlan.Step>> columns = plan.columns(site.stage());
		int layer = crewLayer(level, site, plan, bench);
		Long mine = site.crewColumn(me);
		BuildPlan.Step pick = null;
		if (mine != null && !site.columnTakenByOther(me, mine)) {
			pick = columnWork(level, site, plan, columns.getOrDefault(mine, List.of()), layer, bench);
		}
		if (pick == null) {
			double best = Double.MAX_VALUE;
			for (Map.Entry<Long, List<BuildPlan.Step>> e : columns.entrySet()) {
				if (e.getKey().equals(mine) || site.columnTakenByOther(me, e.getKey())) {
					continue;
				}
				BuildPlan.Step s = columnWork(level, site, plan, e.getValue(), layer, bench);
				if (s != null) {
					double d = villager.distanceToSqr(Vec3.atCenterOf(s.pos()));
					if (d < best) {
						pick = s;
						best = d;
					}
				}
			}
		}
		if (pick == null) {
			site.dropColumn(me);
			if (helping) {
				site.release(me);
				site.seen(me, level.getGameTime());
			}
			return null;
		}
		site.takeColumn(me, plan.column(pick.pos()));
		if (helping) {
			site.claim(me, pick.pos(), level.getGameTime());
		}
		return pick;
	}

	/** The first step of {@code column} with work, in the stage's order, if the crew may do it now; else null. */
	@Nullable
	private BuildPlan.Step columnWork(ServerLevel level, BuildSite site, BuildPlan plan, List<BuildPlan.Step> column, int layer, BlockPos bench) {
		boolean topDown = site.stage() == BuildPlan.Stage.CLEAR;
		for (BuildPlan.Step s : column) {
			if (site.placedThisStage(s.pos())) {
				continue; // (B139: a block placed this stage that changed by itself since, as farmland drying out, stays)
			}
			if (site.gaveUp(s.pos()) ? retakeFailed.contains(s.pos()) || !retakeGivenUp(level, site, s.pos()) : avoid.contains(s.pos())) {
				continue; // (B254: a given-up block is tried again, even by one who failed at it before it was given up)
			}
			if (io.github.jcondedata.aliveworkplace.mechanic.Contraptions.notYet(site, plan, s)) {
				continue; // 39.5: a kinetic part waits for the lead to reach its group
			}
			Action a = actionFor(level, site, s, bench);
			if (a != Action.BREAK && a != Action.PLACE) {
				continue;
			}
			int y = s.pos().getY();
			if (layered(site) && (topDown ? y < layer - CREW_LAYERS : y > layer + CREW_LAYERS)) {
				return null; // the rest of the column is further up (clearing: down) still
			}
			if (a == Action.PLACE && (!supported(level, site, plan, s) || bagNow != null && outForLandscape(level, site, s, bagNow))) {
				continue;
			}
			return s;
		}
		return null;
	}

	/**
	 * Whether the lead may not take {@code step} at its cursor while it has a crew (35.5): another member is on its
	 * column, it's too far above the lowest unfinished layer, or what it stands on isn't there yet.
	 */
	private boolean crewBlocked(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuildPlan.Step step, BlockPos bench) {
		if (site.columnTakenByOther(villager.getUUID(), plan.column(step.pos()))) {
			return true;
		}
		int layer = crewLayer(level, site, plan, bench);
		int y = step.pos().getY();
		if (layered(site) && (site.stage() == BuildPlan.Stage.CLEAR ? y < layer - CREW_LAYERS : y > layer + CREW_LAYERS)) {
			return true;
		}
		return actionFor(level, site, step, bench) == Action.PLACE && !supported(level, site, plan, step);
	}

	/**
	 * B326: the lead of a crew that has stood still while retrying takes {@code step} at its cursor itself: the member on
	 * its column (or its block) lets it go, as a member does any column another has taken. Never a foundation or
	 * structure block with the block the drawing puts under it not there yet (B254: never one in the air).
	 */
	private boolean takeOver(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuildPlan.Step step, BlockPos bench) {
		if (actionFor(level, site, step, bench) == Action.PLACE && !supported(level, site, plan, step)) {
			return false;
		}
		java.util.UUID me = villager.getUUID();
		long column = plan.column(step.pos());
		List<java.util.UUID> holders = new java.util.ArrayList<>();
		site.crewColumns().forEach((who, c) -> {
			if (c == column && !who.equals(me)) {
				holders.add(who);
			}
		});
		for (java.util.UUID m : site.helpers(level.getGameTime())) {
			BlockPos claim = site.claim(m);
			if (!m.equals(me) && claim != null && (claim.equals(step.pos()) || plan.column(claim) == column)) {
				holders.add(m);
			}
		}
		holders.forEach(site::release);
		site.takeColumn(me, column);
		return true;
	}

	/** Whether the crew keeps to layers in this stage: not while levelling the ground round the finished building. */
	private static boolean layered(BuildSite site) {
		return site.stage() != BuildPlan.Stage.LANDSCAPE;
	}

	/**
	 * A foundation or structure block goes up only once the block the drawing puts under it is there. B254: not over one
	 * the lead gave up either (B139 let it, and the crew laid the folly's dome over a hole, a block in the air): the crew
	 * lays a given-up block once nothing stands in it ({@link #retakeGivenUp}), and if nobody can, the lead gives up the
	 * one over it in time (B139: the Colossus's robe waited for good on the block under a crewmate's feet).
	 */
	private static boolean supported(ServerLevel level, BuildSite site, BuildPlan plan, BuildPlan.Step step) {
		if (site.stage() != BuildPlan.Stage.STRUCTURE && site.stage() != BuildPlan.Stage.FOUNDATION) {
			return true;
		}
		BlockPos below = step.pos().below();
		return !plan.solidPlannedAt(below) || !level.getBlockState(below).canBeReplaced();
	}

	/** B254: whether {@code step} waits on a block under it that the lead gave up and nobody has laid since. */
	private static boolean overGivenUp(ServerLevel level, BuildSite site, BuildPlan plan, BuildPlan.Step step) {
		return !supported(level, site, plan, step) && site.gaveUp(step.pos().below());
	}

	/**
	 * B254: a crew member takes a foundation or structure block the lead gave up on again once nothing stands in it (what
	 * stood there has gone, or the lead had no spot to reach it from): the blocks over it wait for it.
	 */
	private static boolean retakeGivenUp(ServerLevel level, BuildSite site, BlockPos pos) {
		return (site.stage() == BuildPlan.Stage.STRUCTURE || site.stage() == BuildPlan.Stage.FOUNDATION)
			&& level.getEntities((Entity) null, new AABB(pos), e -> e.isAlive() && e.blocksBuilding && !e.isSpectator()).isEmpty();
	}

	/** B254: whether a member of the crew is on a block the lead gave up on. */
	private static boolean crewOnGivenUp(BuildSite site, long gameTime) {
		for (java.util.UUID m : site.helpers(gameTime)) {
			BlockPos claim = site.claim(m);
			if (claim != null && site.gaveUp(claim)) {
				return true;
			}
		}
		return false;
	}

	/** Whether any step of the stage still has work a builder can do (one the lead gave up on doesn't count). */
	private static boolean crewHasWork(ServerLevel level, BuildSite site, BuildPlan plan, BlockPos bench) {
		int layer = crewLayer(level, site, plan, bench);
		return layer != Integer.MIN_VALUE + CREW_LAYERS && layer != Integer.MAX_VALUE - CREW_LAYERS;
	}

	/** The part's lowest layer with work left in this stage (clearing: the highest), once a tick. */
	private static int crewLayer(ServerLevel level, BuildSite site, BuildPlan plan, BlockPos bench) {
		return site.crewLayer(level.getGameTime(), () -> {
			// The stage's steps go bottom-up (clearing: top-down), so the first with work is on the layer.
			for (BuildPlan.Step s : plan.steps(site.stage())) {
				if (site.gaveUp(s.pos()) || site.placedThisStage(s.pos())) {
					continue; // left for good (out of reach, say), or placed already (B139): nothing waits for it
				}
				Action a = actionFor(level, site, s, bench);
				if (a == Action.BREAK || a == Action.PLACE) {
					return s.pos().getY();
				}
			}
			return site.stage() == BuildPlan.Stage.CLEAR ? Integer.MIN_VALUE + CREW_LAYERS : Integer.MAX_VALUE - CREW_LAYERS;
		});
	}

	/** For the lead: the nearest block within reach that a helper could take (none claimed, not given up on). */
	@Nullable
	private BuildPlan.Step workInReach(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BlockPos bench) {
		if (avoidStage != site.stage()) {
			avoid.clear();
			retakeFailed.clear();
			avoidStage = site.stage();
		}
		BuildPlan.Step pick = null;
		double best = REACH;
		for (BuildPlan.Step candidate : site.ahead(plan, helpWindow(level, site))) {
			double d = villager.getEyePosition().distanceTo(Vec3.atCenterOf(candidate.pos()));
			if (d > best || avoid.contains(candidate.pos()) || site.claimedByOther(villager.getUUID(), candidate.pos())) {
				continue;
			}
			Action a = actionFor(level, site, candidate, bench);
			if (a == Action.BREAK || a == Action.PLACE) {
				pick = candidate;
				best = d;
			}
		}
		return pick;
	}

	/** Nothing to help with right now (the lead is at the end of a stage): hang around the site. */
	private void idleNear(Villager villager, BuildSite site, BuildPlan plan) {
		villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		if (site.bench() != null && !near(villager, site.bench(), 6)) {
			walkTo(villager, site.bench(), 3);
		}
	}

	/**
	 * B508: the build's last look before it's called done. One pass over the structure and decoration: if a block laid
	 * earlier is gone and its spot is open ({@link BuildPlan#missing}, as the repair round sees it; crops and blocks
	 * given up for good aside), the lead goes back to the first such block and over the lists again, what still stands
	 * passed straight over (at most {@link #MOST_RECHECKS} times a build). After that, what's still gone is counted as
	 * skipped. True if it went back.
	 */
	static boolean backToBroken(ServerLevel level, BuildSite site, BuildPlan plan) {
		if (site.isDeconstruction() || site.isRepair()) {
			return false; // a repair only fills the holes it was made for; upkeep looks again later
		}
		int gone = 0;
		BuildPlan.Stage first = null;
		int at = -1;
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			List<BuildPlan.Step> steps = plan.steps(stage);
			for (int i = 0; i < steps.size(); i++) {
				BuildPlan.Step s = steps.get(i);
				if (!site.leftOut(s.pos()) && !BuildPlan.isCrop(s.state()) && BuildPlan.missing(level, s)) {
					if (first == null) {
						first = stage;
						at = i;
						if (site.rechecks() < MOST_RECHECKS) {
							site.backToBroken(first, at);
							return true;
						}
					}
					gone++;
				}
			}
		}
		if (gone > 0) {
			site.leftBroken(gone); // broken again and again: left for the repair round, not claimed done
		}
		return false;
	}

	/** A step can't be done now. The lead retries it at the end of the stage; a helper leaves it to the lead. */
	private void defer(BuildSite site, Villager villager, BlockPos pos) {
		Set<BlockPos> leave = site.gaveUp(pos) ? retakeFailed : avoid; // B254: a given-up block is tried once more each
		if (helping) {
			leave.add(pos);
			site.release(villager.getUUID());
		} else if (offCursor) {
			leave.add(pos); // a block off the lead's cursor: it comes round in its turn
			site.setLeadClaim(null);
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

	private static Action actionFor(ServerLevel level, BuildSite site, BuildPlan.Step step, BlockPos bench) {
		BuildPlan.Stage stage = site.stage();
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
			if (isProtected(level, pos, world, bench)) {
				return Action.NONE;
			}
			if (wantedEmpty && someonesBed(level, pos, world)) {
				return Action.NONE; // 36.14: a bed a villager sleeps in stays through an upgrade over the room it stands in
			}
			// 27.19: the Steward's sites clear only natural blocks; a player's block in the way stays
			return site.isSteward() && StewardSafety.playersBlock(level, pos, world) ? Action.PLAYER_BLOCK : Action.BREAK;
		}

		if (!worldEmpty && !world.canBeReplaced()) {
			if (site.isRepair()) {
				return Action.NONE; // something's been put there since: a repair only fills holes
			}
			if (isProtected(level, pos, world, bench)) {
				return Action.SKIP;
			}
			return site.isSteward() && StewardSafety.playersBlock(level, pos, world) ? Action.PLAYER_BLOCK : Action.BREAK;
		}
		return Action.PLACE;
	}

	/** Whether {@code state} at {@code pos} is (half of) a bed a villager has claimed as their home. */
	static boolean someonesBed(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
			return false;
		}
		BlockPos head = state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
			? pos : pos.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
		net.minecraft.world.entity.ai.village.poi.PoiManager pois = level.getPoiManager();
		return pois.existsAtPosition(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME, head) && pois.getFreeTickets(head) == 0;
	}

	/**
	 * Blocks a build leaves standing: its own bench, containers and the like ({@link MaterialRules#isProtected}), and any
	 * other Builder's Bench (B132: in the soak a build reaching a neighbour's bench pad cleared his bench, he lost his job
	 * site, took another builder's and his build's chests dropped out of reach for good). A Blueprint Table, the other
	 * builders' workstation, has a block entity and is kept as a container is.
	 */
	private static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state, BlockPos bench) {
		return pos.equals(bench) || state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH)
			|| MaterialRules.isProtected(state, state.getDestroySpeed(level, pos));
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
			stillTicks = 0;
			lastWalkPos = null;
			approachSpot = findStandingSpot(level, plan, target, villager.blockPosition(), null, reach - 0.5);
		}
		if (approachSpot != null && scaffolds.climbTo(level, villager, site, approachSpot)) {
			stuckTimer = 0; // 35.6: climbing one of the site's columns to stand on its top
			reachTicks = 0;
			stillTicks = 0;
			return false;
		}
		// 35.6a: up a column with the next block out of reach and nowhere to stand for it: the column grows from where the
		// builder is, rather than it climbing all the way down to find out (once per block)
		if (approachSpot == null && !target.equals(scaffoldTriedFor) && deferIfImpossible && !helping && plan != null && Scaffolds.onAColumn(villager)) {
			scaffoldTriedFor = target;
			if (scaffolds.planInsteadOfHop(level, villager, site, plan, target, reach, scaffoldingOnHand(level, site, plan))) {
				return false;
			}
		}
		Vec3 at = villager.position();
		stillTicks = lastWalkPos != null && at.distanceToSqr(lastWalkPos) < STILL_DISTANCE * STILL_DISTANCE ? stillTicks + 1 : 0;
		lastWalkPos = at;
		BlockPos goal = approachSpot != null ? approachSpot : target;
		walkTo(villager, goal, approachSpot != null ? 0 : 1);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (distance < bestDistance - 0.3) {
			bestDistance = distance;
			stuckTimer = 0;
		}
		// No path there at all (walled in upstairs, the other side of a party wall): hop after a short beat, not the full wait.
		boolean noPath = villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		if (++stuckTimer > (noPath ? NO_PATH_TICKS : STUCK_TICKS) || ++reachTicks > MAX_REACH_TICKS || stillTicks > STILL_TICKS) {
			stuckTimer = 0;
			reachTicks = 0;
			stillTicks = 0;
			// B60: the approach spot was picked when this target was first chosen, up to 300 ticks ago. A crewmate (or a
			// neighbouring site, or a player) may have filled it since: a landscaping hole filled with dirt put the builder
			// inside the ground. Look again rather than hop into a block.
			if (approachSpot != null && !canStandAt(level, approachSpot)) {
				approachSpot = findStandingSpot(level, plan, target, villager.blockPosition(), null, reach - 0.5);
			}
			// 35.6a: rather than hop onto the pillar it's building (into its gaps, onto its top) or up onto the cap over it,
			// scaffolding goes up beside the work and the builder climbs it. Only with scaffolding on hand; else as before.
			boolean hopUp = approachSpot == null
				|| approachSpot.getY() > villager.getY() + 1.5 && !level.getBlockState(approachSpot.below()).is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
			if (hopUp && deferIfImpossible && !helping && plan != null
				&& scaffolds.planInsteadOfHop(level, villager, site, plan, target, reach, scaffoldingOnHand(level, site, plan))) {
				return false;
			}
			BlockPos spot = approachSpot != null ? approachSpot : findStandingSpot(level, null, target, villager.blockPosition(), null, reach - 0.3);
			if (spot == null) {
				// rc.11 playtest: nowhere else to stand, as for the campfire on a chimney's top, well above any floor: the roof beside
				// it will do (a builder on the slope of its stairs). Without it, the campfire was skipped every time, and
				// every repair came for it again: out of the chest, over to the chimney, back in the chest, for good.
				spot = findStandingSpot(level, null, target, villager.blockPosition(), null, reach - 0.3, true);
			}
			if (spot != null) {
				hop(level, villager, spot);
			} else if (deferIfImpossible && !helping && plan != null && scaffolds.plan(level, villager, site, plan, target, reach)) {
				return false; // 35.6: it would be skipped: scaffolding goes up to work from instead
			} else if (deferIfImpossible) {
				defer(site, villager, target);
			} else {
				return true; // nowhere to stand next to it (odd chest placement): let them reach it anyway
			}
		}
		return false;
	}

	/** 35.6a: the last block the builder tried to grow the column it stands on for (once per block). */
	@Nullable
	private BlockPos scaffoldTriedFor;

	/** 35.6a: the scaffolding this builder could put up now: in its bag and the site's supply chests (all it wants, with free materials). */
	private long scaffoldingOnHand(ServerLevel level, BuildSite site, BuildPlan plan) {
		if (Rules.on(level, ModGameRules.FREE_MATERIALS)) {
			return Long.MAX_VALUE;
		}
		long n = bagNow == null ? 0 : bagNow.count(net.minecraft.world.item.Items.SCAFFOLDING);
		return site.bench() == null ? n : n + SupplyContainers.count(level, SupplyContainers.find(level, site.bench(), plan.supplyExclude()), net.minecraft.world.item.Items.SCAFFOLDING);
	}

	/** Ticks since a path to where we're going last failed (see {@link io.github.jcondedata.aliveworkplace.work.Walker#requestWalk}). */
	private int retryWait;

	private void walkTo(Villager villager, BlockPos pos, int closeEnough) {
		if (Scaffolds.climbDown(villager, pos)) {
			return; // 35.6: down the column first
		}
		retryWait = io.github.jcondedata.aliveworkplace.work.Walker.requestWalk(villager, pos, SPEED, closeEnough, retryWait);
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
		return findStandingSpot(level, plan, target, from, avoid, maxEyeDistance, false);
	}

	/** ... and, with {@code roofs}, on top of a roof's stairs too (the last resort, rather than skip the block: a chimney's campfire). */
	@Nullable
	static BlockPos findStandingSpot(ServerLevel level, @Nullable BuildPlan plan, BlockPos target, BlockPos from, @Nullable AABB avoid,
									 double maxEyeDistance, boolean roofs) {
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -5; dy <= 1; dy++) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					p.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (!canStandAt(level, p, roofs)) {
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
		return canStandAt(level, feet, false);
	}

	/**
	 * With {@code roofs}, the top of a stair counts too (a chimney's campfire, rc.11 playtest): its upper step holds a villager stood in the middle of it
	 * at the block's top, as on a roof's slope. (A stair upside down has a sturdy top already.)
	 */
	private static boolean canStandAt(ServerLevel level, BlockPos feet, boolean roofs) {
		BlockPos below = feet.below();
		BlockState under = level.getBlockState(below);
		return (under.isFaceSturdy(level, below, Direction.UP) || under.is(net.minecraft.world.level.block.Blocks.SCAFFOLDING) // 35.6: a column's top
			|| roofs && under.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& level.getFluidState(feet).isEmpty();
	}

	/** A place to step aside to from {@code from} that's free right now (with the plan's rules first, then without). */
	@Nullable
	private static BlockPos freshSpot(ServerLevel level, @Nullable BuildPlan plan, BlockPos from, Villager villager, AABB avoid) {
		BlockPos spot = findStandingSpot(level, plan, from, villager.blockPosition(), avoid, REACH - 0.5);
		return spot != null ? spot : findStandingSpot(level, null, from, villager.blockPosition(), avoid, REACH - 0.5);
	}

	/**
	 * The block the villager's body is inside, or null (B92). Its box is taken a little smaller, so a wall it leans on or
	 * the floor it stands on doesn't count: only a block whose shape changed around it does, as a door shut on it.
	 * The margin is half the showcase check's (a tenth of a block), so whatever that check sees, this does too. Shapes
	 * are asked for as this villager meets them (scaffolding and powder snow it stands in have none), and the row of
	 * blocks under its feet is looked at too: a fence or a wall there is a block and a half tall.
	 */
	@Nullable
	public static BlockPos insideBlock(ServerLevel level, net.minecraft.world.entity.Entity villager) {
		AABB body = villager.getBoundingBox().deflate(INSIDE_MARGIN);
		CollisionContext context = CollisionContext.of(villager);
		for (BlockPos p : BlockPos.betweenClosed(Mth.floor(body.minX), Mth.floor(body.minY) - 1, Mth.floor(body.minZ),
				Mth.floor(body.maxX), Mth.floor(body.maxY), Mth.floor(body.maxZ))) {
			VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p, context);
			if (!shape.isEmpty() && Shapes.joinIsNotEmpty(shape.move(p.getX(), p.getY(), p.getZ()), Shapes.create(body), BooleanOp.AND)) {
				return p.immutable();
			}
		}
		return null;
	}

	/**
	 * B132: a block put down changes its neighbours' shapes (a stair beside a stair turns into a corner, which fills a
	 * quarter more), and a villager standing there (a porter passing through) was left inside it, stuck in the wall. Any
	 * other mob now inside a block around {@code pos} is moved to the nearest place with room to stand; the builder
	 * itself walks out on its own (B92).
	 */
	public static void freeBystanders(ServerLevel level, BlockPos pos, @Nullable Villager builder) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(1.5), m -> m.isAlive() && m != builder && !m.isPassenger()
				&& !m.isSleeping() && m.getBbWidth() <= 1.0f && m.getBbHeight() <= 2.0f)) {
			if (insideBlock(level, mob) == null) {
				continue;
			}
			BlockPos from = mob.blockPosition();
			BlockPos best = null;
			double bestDistance = Double.MAX_VALUE;
			for (BlockPos p : BlockPos.betweenClosed(from.offset(-2, -1, -2), from.offset(2, 2, 2))) {
				// (canStand wants solid ground and no lava or deep water; a spot in an unloaded chunk is never taken)
				if (!level.isLoaded(p) || !level.isLoaded(p.above()) || !io.github.jcondedata.aliveworkplace.work.Walker.canStand(level, p)) {
					continue;
				}
				double d = Vec3.atBottomCenterOf(p).distanceToSqr(mob.position());
				if (d < bestDistance) {
					bestDistance = d;
					best = p.immutable();
				}
			}
			if (best == null) {
				continue;
			}
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 4, 0.2, 0.3, 0.2, 0.01);
			mob.getNavigation().stop();
			mob.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
			mob.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** Teleports the builder onto {@code spot}, only if there's still room to stand there (B60). */
	private static void hop(ServerLevel level, Villager villager, BlockPos spot) {
		if (!canStandAt(level, spot, true)) { // (a roof's stair too, where the search found only that)
			return;
		}
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, villager.getX(), villager.getY() + 0.5, villager.getZ(), 6, 0.2, 0.3, 0.2, 0.01);
	}

	// --- materials ---------------------------------------------------------------------------

	/** What the first step after clearing needs that the bag doesn't hold yet, or null. */
	@Nullable
	private static MaterialRules.Requirement firstNeed(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag) {
		for (BuildPlan.Step s : site.upcoming(plan, LOOKAHEAD)) {
			if (MaterialRules.matches(level.getBlockState(s.pos()), s.state()) || s.requirements().isEmpty()) {
				continue;
			}
			for (MaterialRules.Requirement r : s.requirements()) {
				if (!bag.has(r.item(), r.count())) {
					return r;
				}
			}
			return null;
		}
		return null;
	}

	static boolean farFromChests(BuildPlan plan, BlockPos bench) {
		return bench.distSqr(plan.bounds().getCenter()) > (double) FAR_FROM_CHESTS * FAR_FROM_CHESTS;
	}

	/** True if our chests, or the village's (23.5: a storehouse, another worker's), hold what {@code r} asks for. */
	private static boolean inStock(ServerLevel level, Villager villager, BuildSite site, BlockPos bench, BuildPlan plan,
								   MaterialRules.Requirement r) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, plan.supplyExclude());
		long have = 0;
		for (Item item : MaterialFamilies.accepted(r.item())) {
			have += SupplyContainers.count(level, supplies, item);
		}
		if (have >= r.count()) {
			return true;
		}
		return have + spareElsewhere(level, villager, site, plan, bench).getOrDefault(MaterialFamilies.key(r.item()), 0L) >= r.count();
	}

	private void fetch(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench,
					   MaterialRules.Requirement requirement) {
		// B38: the bag may already hold a free variant of what this block needs (taken in place of something else
		// in its family): turn it into this one on the spot, as when taking from a chest. Counting the bag's whole
		// family while fetching only the exact item left the builder waiting with nothing missing.
		if (convertInBag(bag, requirement)) {
			return;
		}
		if (bag.spaceFor(requirement.item()) < requirement.count()) {
			deposit(level, villager, site, plan, bag, bench);
			return;
		}
		List<BlockPos> supplies = SupplyContainers.find(level, bench, plan.supplyExclude());
		int needed = requirement.count() - bag.count(requirement.item());
		List<Item> accepted = MaterialFamilies.accepted(requirement.item());
		long available = 0;
		for (Item item : accepted) {
			available += SupplyContainers.count(level, supplies, item);
		}
		if (available < needed) {
			// 37.13: any mod's spruce chair will do: the furniture not yet started becomes the pieces the chests hold
			if (FurnitureStock.restock(level, site, bag, supplies, requirement.item())) {
				return;
			}
			if (site.stage() == BuildPlan.Stage.LANDSCAPE) {
				// Out of dirt for tidying up: leave that hole rather than wait (the building is finished), and the
				// other holes that want it too, all at once (35.5: one a tick, levelling round the test folly with no dirt
				// in the chests took 360 ticks, a builder or a whole crew standing about).
				if (landscapeOutSite == null || !landscapeOutSite.equals(site.id()) || level.getGameTime() >= landscapeOutUntil) {
					landscapeOut.clear();
				}
				landscapeOut.add(requirement.item());
				landscapeOutSite = site.id();
				landscapeOutUntil = level.getGameTime() + LANDSCAPE_OUT_TICKS;
				if (currentStep != null) {
					defer(site, villager, currentStep);
				}
				return;
			}
			if (!helping && !crew && stockUpFirst(level, villager, site, plan, bag, supplies, requirement)) {
				return;
			}
			if (takeFromCrewmate(level, villager, site, plan, bag, requirement.item(), needed)) {
				return;
			}
			// Nothing in our chests: another worker in the village may have it (the miner's stone, the lumberjack's logs).
			// Another builder's chests hold what their own builds need: only what they can spare (B31). From a storehouse,
			// first only what the other builders near it can spare (23.5: one that could also reach another storehouse
			// took what builders who can reach only this one were waiting for); if nothing anywhere is spare that way,
			// first come first served, so builders short of the same thing never wait on each other.
			List<Village.Stash> stashes = Village.stashes(level, villager, bench, plan.supplyExclude());
			for (int pass = 0; pass < 2; pass++) {
				for (Village.Stash stash : stashes) {
					if (pass == 1 && stash.job() != ModVillagers.PORTER) {
						continue;
					}
					Map<Item, Integer> reserved = reservedIn(level, stash, site, pass == 0);
					if (Builders.spare(level, stash.chests(), requirement.item(), reserved) <= 0) {
						continue;
					}
					BlockPos chest = SupplyContainers.firstMatching(level, stash.chests(), stack -> accepted.contains(stack.getItem()));
					if (chest == null) {
						continue;
					}
					waitTimer = 0;
					setStatus(site, BuildSite.Status.FETCHING);
					if (moveInReach(level, villager, site, plan, chest, CONTAINER_REACH, false)) {
						takeWanted(level, site, plan, bag, requirement, stash.chests(), chest, reserved, supplies);
					}
					return;
				}
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
		takeWanted(level, site, plan, bag, requirement, supplies, source);
		// A Fighting partner shoulders what was fetched over to the work (28.3).
		PartnerShows.cue(villager, "fetch", currentStep != null ? currentStep : source, new ItemStack(requirement.item()));
	}

	/**
	 * At the chest: take what the next stretch of work needs from {@code supplies} (the chests there), current block
	 * first. Helpers only take a handful for the blocks they are on, so they never sit on the lead's materials.
	 */
	/** The next stretch of work, for what to take and what to keep: later stages, or the rest of the levelling. */
	private static List<BuildPlan.Step> ahead(BuildSite site, BuildPlan plan) {
		return ahead(site, plan, LOOKAHEAD);
	}

	private static List<BuildPlan.Step> ahead(BuildSite site, BuildPlan plan, int steps) {
		return site.stage() == BuildPlan.Stage.LANDSCAPE ? site.landscapeLeft(plan, steps) : site.upcoming(plan, steps);
	}

	/**
	 * Turns free variants (MaterialFamilies) the bag holds into {@code requirement}'s item, as many as it is short of.
	 * True if the bag now has enough.
	 */
	static boolean convertInBag(BuilderBag bag, MaterialRules.Requirement requirement) {
		Item item = requirement.item();
		int shortBy = requirement.count() - bag.count(item);
		if (shortBy <= 0) {
			return true;
		}
		for (Item member : MaterialFamilies.accepted(item)) {
			if (member == item || shortBy <= 0) {
				continue;
			}
			int got = bag.remove(member, shortBy);
			if (got > 0) {
				bag.addAll(item, got);
				MaterialLedger.converted(member, item, got);
				shortBy -= got;
			}
		}
		return shortBy <= 0;
	}

	private void takeWanted(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag, MaterialRules.Requirement requirement,
							List<BlockPos> supplies, BlockPos source) {
		takeWanted(level, site, plan, bag, requirement, supplies, source, Map.of(), List.of());
	}

	/**
	 * The same at a village-mate's chests, leaving {@code reserved} (by family key) in them: another builder's builds
	 * need it. Of the rest of the work's needs it takes only what {@code mine} (our own chests) can't cover (23.5: a
	 * builder at the storehouse took everything its next stretch needed, though its own chests held half of it, and the
	 * builder whose share that was waited for it).
	 */
	private void takeWanted(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag, MaterialRules.Requirement requirement,
							List<BlockPos> supplies, BlockPos source, Map<Item, Integer> reserved, List<BlockPos> mine) {
		Map<Item, Integer> wanted = new LinkedHashMap<>();
		wanted.put(requirement.item(), 0); // first in line when the bag is short of room
		// 23.5: chests far from the site (a village storehouse, a bench far away): take for a longer stretch, so the
		// builder walks there less often (each trip was 30 s and more without placing a block).
		boolean far = farFromChests(plan, source);
		for (BuildPlan.Step s : helping || crew ? helperShare(level, site, plan) : far ? ahead(site, plan, FAR_LOOKAHEAD) : ahead(site, plan)) {
			if (!MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
				for (MaterialRules.Requirement r : s.requirements()) {
					wanted.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		// B132: the stretch ahead starts with the block this is for, so it counts once, as at least what that block
		// asks. Counted on top of the stretch, every trip took one block's worth too many: in the city time-lapse two
		// builders sharing the storehouse each carried the one log or slab the other waited for, and neither finished.
		wanted.merge(requirement.item(), requirement.count(), Math::max);
		boolean tookAny = false;
		if (helping) {
			// B519: a helper at the chests first puts back what its stretch no longer needs (the walls' last planks once
			// the build is on its lanterns), so its bag never holds more than the work ahead
			tookAny = putBackUnwanted(level, bag, wanted, supplies);
		}
		for (Map.Entry<Item, Integer> e : wanted.entrySet()) {
			int want = e.getValue() - bag.count(e.getKey());
			if (!mine.isEmpty()) {
				want -= (int) Math.min(want, Builders.spare(level, mine, e.getKey(), Map.of()));
			}
			int take = Math.min(want, bag.spaceFor(e.getKey()));
			if (!reserved.isEmpty()) {
				take = (int) Math.min(take, Builders.spare(level, supplies, e.getKey(), reserved));
			}
			// The exact block first; otherwise a free variant of it (Chipped), turned into the one we need.
			for (Item source2 : MaterialFamilies.accepted(e.getKey())) {
				if (take <= 0) {
					break;
				}
				int got = SupplyContainers.extract(level, supplies, source2, take);
				if (got > 0) {
					tookAny = true;
					bag.addAll(e.getKey(), got);
					if (source2 != e.getKey()) {
						MaterialLedger.converted(source2, e.getKey(), got);
					}
					take -= got;
				}
			}
		}
		if (tookAny) {
			site.supplied();
			level.playSound(null, source, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		}
	}

	/**
	 * Puts back into {@code supplies} what the bag holds beyond {@code wanted} (by item). Stacks with data of their own
	 * (a filled container) stay in the bag; what the chests have no room for stays too. True if anything went back.
	 */
	private static boolean putBackUnwanted(ServerLevel level, BuilderBag bag, Map<Item, Integer> wanted, List<BlockPos> supplies) {
		Map<Item, Integer> held = new LinkedHashMap<>();
		Set<Item> special = new HashSet<>();
		for (ItemStack stack : bag.stacks()) {
			held.merge(stack.getItem(), stack.getCount(), Integer::sum);
			if (!stack.getComponentsPatch().isEmpty()) {
				special.add(stack.getItem());
			}
		}
		boolean any = false;
		for (Map.Entry<Item, Integer> e : held.entrySet()) {
			int extra = e.getValue() - wanted.getOrDefault(e.getKey(), 0);
			if (extra <= 0 || special.contains(e.getKey())) {
				continue;
			}
			int removed = bag.remove(e.getKey(), extra);
			ItemStack rest = SupplyContainers.insert(level, supplies, new ItemStack(e.getKey(), removed));
			if (!rest.isEmpty()) {
				bag.add(rest);
			}
			any |= rest.getCount() < removed;
		}
		return any;
	}

	/**
	 * What a helper takes for in one trip (23.1a): a stretch of the steps helpers pick from, those nobody else is on.
	 * It used to take a handful for the block it was on and walk back every few blocks.
	 */
	private List<BuildPlan.Step> helperShare(ServerLevel level, BuildSite site, BuildPlan plan) {
		BuildPlan.Step leads = site.current(plan);
		List<BuildPlan.Step> out = new java.util.ArrayList<>();
		if (crew && self != null) {
			return crewShare(level, site, plan);
		}
		for (BuildPlan.Step s : site.ahead(plan, helpWindow(level, site))) {
			if (out.size() >= HELP_WINDOW) {
				break;
			}
			if ((leads == null || !leads.pos().equals(s.pos())) && !site.claimedByOther(self, s.pos()) && !avoid.contains(s.pos())) {
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * What a member of a Wonder's crew (the lead too) takes for in one trip (35.5): the rest of its own column, then a
	 * share of the rest of the stage dealt round the crew like cards, each step to half of them (every step with work, in
	 * turn, to the member at the next seat and the half of the crew after it), so each carries about twice what an equal
	 * n-th of the stage takes, in the stage's mix of blocks; then, dealt the same way, the few blocks of the stages after
	 * this one (the decoration's lanterns and doors). A member doesn't put up exactly its n-th, so taking only that sent it
	 * back for most of what it was short of. With the 1-block columns of a small part, the rest of its own column is a
	 * block or two: taking only for that, a member walked to the chests for nearly every block it put up, and the lead,
	 * taking for the 192 steps a builder on its own takes for, emptied the chests for the rest.
	 */
	private List<BuildPlan.Step> crewShare(ServerLevel level, BuildSite site, BuildPlan plan) {
		List<BuildPlan.Step> out = new java.util.ArrayList<>();
		Set<BlockPos> mineSteps = new HashSet<>();
		Long column = site.crewColumn(self);
		if (column != null) {
			for (BuildPlan.Step s : plan.columns(site.stage()).getOrDefault(column, List.of())) {
				if (out.size() >= CREW_SHARE) {
					return out;
				}
				if (!avoid.contains(s.pos()) && !MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
					out.add(s);
					mineSteps.add(s.pos());
				}
			}
		}
		List<java.util.UUID> members = new java.util.ArrayList<>(site.helpers(level.getGameTime()));
		if (site.builder() != null && !members.contains(site.builder())) {
			members.add(site.builder());
		}
		if (!members.contains(self)) {
			members.add(self);
		}
		members.sort(null);
		int n = members.size();
		int seat = members.indexOf(self);
		int hands = Math.max(1, (n + 1) / 2);
		int dealt = 0;
		int max = CREW_SHARE;
		for (BuildPlan.Stage stage = site.stage(); stage != BuildPlan.Stage.DONE; stage = stage.next()) {
			if (stage != site.stage() && stage == BuildPlan.Stage.LANDSCAPE) {
				continue; // levelling the ground uses the earth it digs up, and never waits for more
			}
			List<BuildPlan.Step> steps = stage == BuildPlan.Stage.LANDSCAPE ? site.landscapeLeft(plan, LOOKAHEAD) : plan.steps(stage);
			for (BuildPlan.Step s : steps) {
				if (out.size() >= max) {
					break;
				}
				if (mineSteps.contains(s.pos()) || site.gaveUp(s.pos()) || s.requirements().isEmpty()
					|| MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
					continue;
				}
				if (Math.floorMod(dealt++ - seat, n) < hands) {
					out.add(s);
				}
			}
			max = out.size() + CREW_SHARE / 4; // the later stages: only a little more
		}
		return out;
	}

	/** The step a helper has claimed, among those helpers pick from; null if none (or it has moved on). */
	@Nullable
	private static BuildPlan.Step claimedStep(BuildSite site, BuildPlan plan, @Nullable BlockPos claim) {
		if (claim == null) {
			return null;
		}
		for (BuildPlan.Step s : site.ahead(plan, HELP_WINDOW * (1 + io.github.jcondedata.aliveworkplace.hall.Guilds.MOST_HELPERS))) {
			if (s.pos().equals(claim)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * The chests are out of something but another builder on this site is carrying spares: walk over
	 * and get some ("pass me those planks"). Returns false if nobody has any to spare.
	 */
	/** Materials the chests were found out of while levelling (for {@link #landscapeOutSite}, until {@link #landscapeOutUntil}). */
	private final Set<Item> landscapeOut = new HashSet<>();
	@Nullable
	private java.util.UUID landscapeOutSite;
	private long landscapeOutUntil;
	/** How long a builder levelling the ground goes on taking the chests to be out of what they were out of. */
	private static final int LANDSCAPE_OUT_TICKS = 200;

	/** A hole to fill round the finished building with what the chests were just found out of, and the bag hasn't either. */
	private boolean outForLandscape(ServerLevel level, BuildSite site, BuildPlan.Step step, BuilderBag bag) {
		if (site.stage() != BuildPlan.Stage.LANDSCAPE || landscapeOut.isEmpty() || !site.id().equals(landscapeOutSite)
			|| level.getGameTime() >= landscapeOutUntil || step.requirements().isEmpty()) {
			return false;
		}
		for (MaterialRules.Requirement r : step.requirements()) {
			if (!landscapeOut.contains(r.item()) || bag.has(r.item(), r.count())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * B155: the lead of a crew, short of something its helpers took, in the build's last stretch (all that is left within
	 * its look ahead): if the chests still hold something else that stretch needs and nobody of the crew carries, it goes for that
	 * first (taking all it can for the stretch), then takes the short item from a helper. Taking only the short item from
	 * a helper close by left the rest (a lantern put off for its support) in the chests, and the lead walked there for it
	 * at the very end while the helpers stood about: a crew of two took 60% of one builder's time, not half. True while
	 * it walks to the chests or has just taken from them.
	 */
	private boolean stockUpFirst(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, List<BlockPos> supplies,
								 MaterialRules.Requirement requirement) {
		if (site.helpers(level.getGameTime()).isEmpty()) {
			return false;
		}
		List<BuildPlan.Step> rest = ahead(site, plan);
		if (rest.size() >= LOOKAHEAD) {
			return false; // not the last stretch yet: the lead comes back to the chests for the next one anyway
		}
		Map<Item, Integer> short_ = new LinkedHashMap<>();
		for (BuildPlan.Step s : rest) {
			if (MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
				continue;
			}
			for (MaterialRules.Requirement r : s.requirements()) {
				if (r.item() != requirement.item()) {
					short_.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		List<BuilderBag> mates = new java.util.ArrayList<>();
		for (java.util.UUID id : site.helpers(level.getGameTime())) {
			if (level.getEntity(id) instanceof Villager mate && mate.isAlive()) {
				mates.add(ModAttachments.BUILDER_BAG.getOrCreate(mate));
			}
		}
		BlockPos chest = null;
		for (Map.Entry<Item, Integer> e : short_.entrySet()) {
			Item item = e.getKey();
			int carried = bag.count(item);
			for (BuilderBag mate : mates) {
				carried += mate.count(item);
			}
			// only what nobody of the crew carries enough of: the helpers take for their own share themselves
			if (carried < e.getValue() && bag.spaceFor(item) > 0) {
				chest = SupplyContainers.firstMatching(level, supplies, st -> st.is(item));
				if (chest != null) {
					break;
				}
			}
		}
		if (chest == null) {
			return false;
		}
		setStatus(site, BuildSite.Status.FETCHING);
		if (moveInReach(level, villager, site, plan, chest, CONTAINER_REACH, false)) {
			takeWanted(level, site, plan, bag, requirement, supplies, chest);
		}
		return true;
	}

	private boolean takeFromCrewmate(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, Item item, int needed) {
		List<java.util.UUID> crew = new java.util.ArrayList<>(site.helpers(level.getGameTime()));
		if (site.builder() != null) {
			crew.add(0, site.builder());
		}
		for (java.util.UUID id : crew) {
			if (id.equals(villager.getUUID()) || !(level.getEntity(id) instanceof Villager mate) || !mate.isAlive()) {
				continue;
			}
			BuilderBag mateBag = ModAttachments.BUILDER_BAG.getOrCreate(mate);
			// Whatever the mate needs for the block it is on stays with it: the lead's current block, or the block a
			// helper has claimed. Only what that block needs of this item: keeping one of anything left the lead
			// waiting, with nothing on its missing list, for a block a helper on other work would never use (B21).
			BuildPlan.Step mateStep = id.equals(site.builder()) ? site.current(plan) : claimedStep(site, plan, site.claim(id));
			if (this.crew) {
				// 35.5: a crew member's block is in its column, anywhere in the stage
				BlockPos on = id.equals(site.builder()) ? site.leadClaim() : site.claim(id);
				if (on != null) {
					for (BuildPlan.Step s : plan.columns(site.stage()).getOrDefault(plan.column(on), List.of())) {
						if (s.pos().equals(on)) {
							mateStep = s;
							break;
						}
					}
				}
			}
			int keep = 0;
			for (BuildPlan.Step s : mateStep != null ? List.of(mateStep) : List.<BuildPlan.Step>of()) {
				for (MaterialRules.Requirement r : s.requirements()) {
					keep += r.item() == item ? r.count() : 0;
				}
			}
			int spare = mateBag.count(item) - keep;
			if (spare < needed) {
				continue;
			}
			setStatus(site, BuildSite.Status.FETCHING);
			// 23.1a: a crewmate working close by passes it over; only one further away is walked to.
			boolean close = villager.distanceToSqr(mate) <= PASS_DISTANCE * PASS_DISTANCE;
			if (!close && !moveInReach(level, villager, site, plan, mate.blockPosition(), CONTAINER_REACH, false)) {
				return true;
			}
			int take = Math.min(Math.min(spare, needed + (helping ? 3 : 32)), bag.spaceFor(item));
			if (take > 0) {
				mateBag.remove(item, take);
				bag.addAll(item, take);
				site.supplied();
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.EntityTracker(mate, true));
				if (close) {
					// 23.1b: only a sound, capped server-wide (TossSounds).
					TossSounds.play(level, villager.blockPosition());
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * A helper with nothing left to help with, in the build's last blocks, brings the lead what it is short of (B66):
	 * passed over when close, else it walks over first. It used to carry it all back to the chests, and the lead walked over there after
	 * it for the build's last blocks (with exactly the build's materials, a lantern a helper had fetched and then left to
	 * the lead): 200 ticks and more at the end of a crew's build. True while it is walking over.
	 */
	private boolean handToLead(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag) {
		if (site.builder() == null || !(level.getEntity(site.builder()) instanceof Villager lead) || !lead.isAlive()) {
			return false;
		}
		BuilderBag leadBag = ModAttachments.BUILDER_BAG.getOrCreate(lead);
		// Only what the lead is short of for its next stretch, so it never fills up on a helper's spares.
		Map<Item, Integer> needed = new LinkedHashMap<>();
		int left = 0;
		for (BuildPlan.Step s : ahead(site, plan)) {
			if (!MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
				left++;
				for (MaterialRules.Requirement r : s.requirements()) {
					needed.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		needed.replaceAll((item, n) -> Math.min(Math.min(n - leadBag.count(item), bag.count(item)), leadBag.spaceFor(item)));
		needed.values().removeIf(n -> n <= 0);
		// Only for the build's last blocks: earlier, the lead's next chest trip takes it anyway, and handing it over
		// then only shuffled the crew's loads about (a crew of two took 6% longer).
		if (needed.isEmpty() || left > HELP_WINDOW) {
			return false;
		}
		if (villager.distanceToSqr(lead) > PASS_DISTANCE * PASS_DISTANCE) {
			walkTo(villager, lead.blockPosition(), 2);
			return true;
		}
		needed.forEach((item, n) -> leadBag.addAll(item, bag.remove(item, n)));
		site.supplied();
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.EntityTracker(lead, true));
		TossSounds.play(level, villager.blockPosition()); // 23.1b: only a sound, capped server-wide
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
			// What the whole village has counts: it isn't missing if another worker's chests hold it.
			site.setMissing(scaffolds.addMissing(level, Builders.computeMissing(level, site, plan, bag, supplies, spareElsewhere(level, villager, site, plan, bench)),
				bag, supplies)); // 35.6: and the scaffolding it's short of, for the requests board
			if (firstTick && !helping) {
				Builders.notifyWaiting(level, villager, site);
			}
		}
	}

	/**
	 * What village-mates' chests hold that this build may take, by family key: everything in theirs, but in another
	 * builder's only what their own builds don't need (B31: counting it all left a builder waiting with nothing missing).
	 */
	static Map<Item, Long> spareElsewhere(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BlockPos bench) {
		Map<Item, Long> out = new HashMap<>();
		for (Village.Stash stash : Village.stashes(level, villager, bench, plan.supplyExclude())) {
			Map<Item, Long> held = new HashMap<>();
			SupplyContainers.contents(level, stash.chests()).forEach((item, n) -> held.merge(MaterialFamilies.key(item), n, Long::sum));
			Map<Item, Integer> reserved = reservedIn(level, stash, site, false);
			held.forEach((key, n) -> {
				long spare = n - reserved.getOrDefault(key, 0);
				if (spare > 0) {
					out.merge(key, spare, Long::sum);
				}
			});
		}
		return out;
	}

	/**
	 * Empties the bag of what the next stretch of work doesn't need: into our chests, or (23.5) when they are full or
	 * there are none, into the village storehouse's, so a full bag never ends on the ground or stops the work.
	 */
	/** What a village-mate's chests keep back from this build: a builder's for its own builds, a storehouse's for the other builders near it. */
	private static Map<Item, Integer> reservedIn(ServerLevel level, Village.Stash stash, BuildSite site, boolean fair) {
		if (stash.job() == ModVillagers.BUILDER) {
			return Builders.reservedAt(level, stash.station(), site);
		}
		if (fair && stash.job() == ModVillagers.PORTER) {
			return Builders.reservedForOthers(level, stash.station(), site);
		}
		return Map.of();
	}

	private void deposit(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuilderBag bag, BlockPos bench) {
		List<BlockPos> supplies = SupplyContainers.find(level, bench, plan.supplyExclude());
		if (supplies.isEmpty() || SupplyContainers.freeSlots(level, supplies) == 0) {
			List<BlockPos> store = Builders.storehouseChests(level, villager, bench, plan.supplyExclude());
			if (!store.isEmpty() && SupplyContainers.freeSlots(level, store) > 0) {
				supplies = store;
			}
		}
		BlockPos target = supplies.isEmpty() ? bench : supplies.get(0);
		if (!moveInReach(level, villager, site, plan, target, CONTAINER_REACH, false)) {
			return;
		}
		Set<Item> keep = new HashSet<>();
		boolean keepNothing = helping || site.stage() == BuildPlan.Stage.DECONSTRUCT;
		for (BuildPlan.Step s : keepNothing ? List.<BuildPlan.Step>of() : ahead(site, plan)) {
			for (MaterialRules.Requirement r : s.requirements()) {
				keep.add(r.item());
			}
		}
		List<ItemStack> junk = bag.takeAllExcept(keep);
		if (junk.isEmpty()) {
			junk = bag.takeAll(); // bag is full of materials for later: drop them back off
		}
		if (!junk.isEmpty()) {
			site.supplied();
		}
		List<BlockPos> store = null;
		for (ItemStack stack : junk) {
			ItemStack rest = SupplyContainers.insert(level, supplies, stack);
			if (!rest.isEmpty()) {
				if (store == null) {
					store = Builders.storehouseChests(level, villager, bench, plan.supplyExclude());
				}
				if (!store.equals(supplies)) {
					rest = SupplyContainers.insert(level, store, rest);
				}
			}
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
		// What hung on it or stood on it just fell off (a lantern under a porch roof being rebuilt, 23.10a): the builder
		// picks that up too, or he'd wait for a lantern lying at his feet.
		List<ItemStack> fell = new java.util.ArrayList<>(drops);
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(pos).inflate(1.5), e -> e.isAlive() && e.getAge() == 0)) {
			fell.add(item.getItem().copy());
			item.discard();
		}
		drops = fell;
		for (ItemStack drop : drops) {
			MaterialLedger.gained(drop);
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
			MaterialLedger.gained(r.item(), r.count());
			int rest = bag.addAll(r.item(), r.count());
			if (rest > 0) {
				Builders.dropNear(level, bench, new ItemStack(r.item(), rest));
			}
		}
		if (offCursor) {
			site.countPlaced();
			site.setLeadClaim(null);
		} else if (!helping) {
			site.markPlaced();
		} else {
			site.countPlaced();
			site.release(villager.getUUID());
		}
		BuilderLevels.onPlaced(level, villager, site);
	}

	/**
	 * A block laid as one piece (Create's belt, ROADMAP 39.4): a segment alone breaks itself on its next tick, so the
	 * rest of it (the plan's steps of the same block in a line with it, along its facing, on its level) goes down now
	 * too, each segment's connector taken from the bag when it's there.
	 */
	private void layTheRest(ServerLevel level, BuildSite site, BuildPlan plan, BuilderBag bag, BuildPlan.Step first, boolean free) {
		BlockState state = first.state();
		if (!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
			return;
		}
		net.minecraft.core.Direction along = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
		java.util.Map<BlockPos, BuildPlan.Step> line = new java.util.HashMap<>();
		for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.STRUCTURE)) {
			if (s.state().getBlock() == state.getBlock() && s.pos().getY() == first.pos().getY()) {
				line.put(s.pos(), s);
			}
		}
		for (net.minecraft.core.Direction way : new net.minecraft.core.Direction[] {along, along.getOpposite()}) {
			for (BlockPos at = first.pos().relative(way); line.containsKey(at); at = at.relative(way)) {
				BuildPlan.Step next = line.get(at);
				if (MaterialRules.matches(level.getBlockState(at), next.state()) || !level.getBlockState(at).canBeReplaced()) {
					continue;
				}
				if (!free) {
					MaterialRules.requirement(next.state()).ifPresent(r -> MaterialLedger.used(r.item(), bag.remove(r.item(), r.count())));
				}
				level.setBlock(at, next.state(), Block.UPDATE_ALL);
				applyBlockEntityData(level, at, next.state(), next.nbt()); // 39.5: its place on the belt
				site.placedNow(at);
			}
		}
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
		BlockPos under = pos.below();
		if ((state.getBlock() instanceof net.minecraft.world.level.block.CropBlock || state.getBlock() instanceof net.minecraft.world.level.block.StemBlock)
			&& level.getBlockState(under).is(net.minecraft.world.level.block.Blocks.DIRT) && !state.canSurvive(level, pos)) {
			// B139: a crop goes only on farmland, which dries out to dirt while the walls go up and its water hasn't come
			// yet: the builder tills it again, as a farmer would, rather than leave the field bare
			level.setBlock(under, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
		}
		if (!state.canSurvive(level, pos)) {
			defer(site, villager, pos); // nothing to attach to yet; retried at the end of the stage
			return;
		}
		// B91: an anvil, sand or gravel with nothing under it yet (a helper works out of order) would fall, land in the
		// floor's place and, broken by hand, drop nothing: the build then waits for an anvil that's gone. It waits too.
		if (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock
			&& net.minecraft.world.level.block.FallingBlock.isFree(level.getBlockState(pos.below())) && pos.getY() > level.getMinBuildHeight()) {
			defer(site, villager, pos);
			return;
		}
		// B132: what goes down is the block shaped by its neighbours (an outer-corner stair with its neighbour not built yet
		// goes down straight, a bigger block), so the room is checked for that one too, or it went down onto a villager.
		BlockState shaped = step.secondaryPos() != null ? state : Block.updateFromNeighbourShapes(state, level, pos);
		if (shaped.isAir()) {
			shaped = state;
		}
		if (!level.isUnobstructed(state, pos, CollisionContext.empty())
			|| shaped != state && !level.isUnobstructed(shaped, pos, CollisionContext.empty())) {
			handleObstruction(level, villager, site, plan, step, pos);
			return;
		}
		// B92: the other half too (a door's top, a bed's head). It was put down unchecked, onto whoever stood there.
		if (step.secondaryPos() != null && step.secondaryState() != null
			&& !level.isUnobstructed(step.secondaryState(), step.secondaryPos(), CollisionContext.empty())) {
			handleObstruction(level, villager, site, plan, step, step.secondaryPos());
			return;
		}
		blockedAttempts = 0;
		setDetail(site, null);

		if (!free) {
			for (MaterialRules.Requirement r : requirements) {
				MaterialLedger.used(r.item(), bag.remove(r.item(), r.count()));
				// What's left over goes back in the bag (the empty bucket after pouring water).
				ItemStack leftover = r.item().getCraftingRemainingItem() == null ? ItemStack.EMPTY : new ItemStack(r.item().getCraftingRemainingItem(), r.count());
				if (!leftover.isEmpty()) {
					MaterialLedger.gained(leftover);
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
			level.setBlock(pos, shaped, Block.UPDATE_ALL);
		}
		freeBystanders(level, pos, villager);
		applyBlockEntityData(level, pos, state, step.nbt());
		site.placedNow(pos);
		MaterialRules.afterPlaced(level, pos, state);
		if (MaterialRules.laidWhole(state)) {
			layTheRest(level, site, plan, bag, step, free);
		}
		if (state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE)) {
			io.github.jcondedata.aliveworkplace.store.Porters.onBuilt(level, pos, site);
		}
		if (state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL)) { // a Village Hall build's heart
			boolean players = site.stewardHall() == null;
			io.github.jcondedata.aliveworkplace.hall.VillageHallBlock.built(level, pos, players ? site.owner() : null, players ? site.ownerName() : "");
		}

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
		} else if (offCursor) {
			site.countPlaced(); // the lead's cursor stays where it is
			site.setLeadClaim(null);
		} else {
			site.markPlaced();
		}
		BuilderLevels.onPlaced(level, villager, site);
		// A Fighting partner punches the block home (28.4).
		PartnerShows.cue(villager, "place", pos, new ItemStack(state.getBlock().asItem()));
	}

	/**
	 * Something is standing where the next block goes. The builder steps out of its own way, shoos
	 * animals and Pokémon, asks players to move, and after a while works on other blocks first.
	 * {@code at} is the block of the step they stand in: the step's own, or its other half (B92); whoever moves is
	 * sent clear of both halves, so stepping off a bed's foot never lands on its head.
	 */
	private void handleObstruction(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, BuildPlan.Step step, BlockPos at) {
		BlockPos pos = step.pos();
		AABB box = new AABB(at);
		AABB keepOff = step.secondaryPos() == null ? new AABB(pos) : new AABB(pos).minmax(new AABB(step.secondaryPos()));
		List<Entity> blockers = level.getEntities((Entity) null, box, e -> e.isAlive() && e.blocksBuilding && !e.isSpectator());
		if (blockers.contains(villager)) {
			BlockPos spot = findStandingSpot(level, plan, pos, villager.blockPosition(), keepOff, REACH - 0.5);
			if (spot == null) {
				spot = findStandingSpot(level, null, pos, villager.blockPosition(), keepOff, REACH - 0.5);
			}
			if (spot != null && ++blockedAttempts <= MAX_BLOCKED_ATTEMPTS) {
				stepAsideSpot = spot;
				stepAsideFrom = at;
				stepAsideTicks = STEP_ASIDE_TICKS;
			} else if (spot == null && blockedAttempts++ == 0 && (spot = farSpot(level, plan, villager, keepOff)) != null) {
				// B308: boxed in where its block goes (the Colossus's lantern: the cell inside the ring of panes, high on the
				// hand) with nowhere in reach to step to. It hops out to the nearest free spot further off instead of giving
				// the block up (and, since B254, everything over it); from there it comes back by its usual way (scaffolding).
				hop(level, villager, spot);
			} else {
				blockedAttempts = 0;
				defer(site, villager, pos);
			}
			return;
		}
		Entity first = blockers.isEmpty() ? null : blockers.get(0);
		for (Entity e : blockers) {
			if (e instanceof Player player) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.in_the_way", villager.getDisplayName()));
			} else {
				shoo(level, plan, e, at, keepOff, blockedAttempts);
			}
		}
		if (first != null) {
			setDetail(site, Component.translatable("message.aliveworkplace.status.blocked_by", first.getDisplayName(), at.getX(), at.getY(), at.getZ()));
		}
		if (++blockedAttempts > MAX_BLOCKED_ATTEMPTS) {
			blockedAttempts = 0;
			setDetail(site, null);
			defer(site, villager, pos); // try the rest first; this spot is retried at the end of the stage
		}
	}

	/** How far round (blocks, across) {@link #farSpot} looks for somewhere to put a villager boxed in on the build. */
	static final int FAR_SPOT_RADIUS = 16;

	/**
	 * B308: the nearest place {@code entity} can stand that the build leaves free, up to {@link #FAR_SPOT_RADIUS} across
	 * and from 2 over its feet down to 32 under them (the ground under a statue's raised hand), outside {@code keepOff};
	 * columns are taken nearest first, each from the top down. Null when there's none.
	 */
	@Nullable
	static BlockPos farSpot(ServerLevel level, @Nullable BuildPlan plan, Entity entity, @Nullable AABB keepOff) {
		BlockPos from = entity.blockPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -FAR_SPOT_RADIUS; dx <= FAR_SPOT_RADIUS; dx++) {
			for (int dz = -FAR_SPOT_RADIUS; dz <= FAR_SPOT_RADIUS; dz++) {
				int across = dx * dx + dz * dz;
				if (across > FAR_SPOT_RADIUS * FAR_SPOT_RADIUS || across >= bestScore) {
					continue;
				}
				for (int dy = 2; dy >= -32; dy--) {
					p.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
					double score = across + dy * dy;
					if (score >= bestScore || !level.isLoaded(p) || !canStandAt(level, p)) {
						continue;
					}
					AABB body = new AABB(p.getX() + 0.2, p.getY(), p.getZ() + 0.2, p.getX() + 0.8, p.getY() + 1.9, p.getZ() + 0.8);
					if (keepOff != null && body.intersects(keepOff) || plan != null && (plan.needsSolidAt(level, p) || plan.needsSolidAt(level, p.above()))) {
						continue;
					}
					bestScore = score;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** Nudges a mob off a build spot; if it keeps standing there, moves it next to the site (further off if it's boxed in, B308). */
	private static void shoo(ServerLevel level, BuildPlan plan, Entity entity, BlockPos pos, AABB keepOff, int attempt) {
		if (attempt >= 4) {
			BlockPos spot = findStandingSpot(level, plan, entity.blockPosition(), entity.blockPosition(), keepOff, 4.0);
			if (spot == null && attempt == 8) {
				spot = farSpot(level, plan, entity, keepOff); // (once: the search is wide)
			}
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

	/** How many stacks {@code builder}'s bag takes: {@link BuilderBag#SLOTS}, 9 more a level of Engineering (35.14). */
	public static int engineeringSlots(Villager builder) {
		return BuilderBag.SLOTS + 9 * io.github.jcondedata.aliveworkplace.research.Research.bonus(builder,
			io.github.jcondedata.aliveworkplace.research.Research.Topic.ENGINEERING);
	}
}
