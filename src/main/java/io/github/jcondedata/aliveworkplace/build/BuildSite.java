package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * A building in progress. Owned by the player who handed over the blueprint; worked on by a
 * builder villager. Persisted per dimension in {@link BuildSiteManager}.
 */
public final class BuildSite {
	public enum Status {
		STARTING, WORKING, FETCHING, WAITING_FOR_MATERIALS, NO_BUILDER, BLUEPRINT_MISSING, DONE
	}

	private final UUID id;
	private final UUID owner;
	private final String ownerName;
	private final ResourceLocation structure;
	private final BlueprintData.Placement placement;
	private BuildPlan.Stage stage = BuildPlan.Stage.CLEAR;
	private int cursor;
	private boolean retrying;
	private final List<Integer> deferred = new ArrayList<>();
	private int skipped;
	private int placed;
	/**
	 * The most progress shown so far (B46). The foundation and landscaping lists are worked out again from the terrain
	 * after a restart, and the foundation already filled then counts as ground, so the raw figure would drop.
	 */
	private float shownProgress;
	/**
	 * Loaded from a save written before B46 (no shown_progress): the progress it showed is worked out from the saved
	 * cursor before the first plan redoes the foundation list (B64). Transient.
	 */
	private boolean progressFromCursor;
	@Nullable
	private UUID builder;
	/** Waiting in the builder's queue (the builder is busy with an earlier site). */
	private boolean queued;
	/** Taking the build down instead of putting it up. */
	private boolean deconstruct;
	/** Putting back what's missing from a finished build (see {@link BuildPlan#repair}). */
	private boolean repair;
	/**
	 * 36.21: a repair that brings the build up to its latest look ({@link Refreshes}): the older drawing it was built from
	 * (its key in the shipped history), whose leftovers are cleared first. Null: an ordinary repair. None in older saves.
	 */
	@Nullable
	private String refresh;
	/** 36.7: works only the cellar of a build finished before its blueprint had one ({@link Cellars}). */
	private boolean cellarOnly;
	/** The lead builder's bench: where the supply chests are, for helpers too. Null in pre-0.6 saves. */
	@Nullable
	private BlockPos bench;

	// Transient, recomputed as needed
	@Nullable
	private BuildPlan plan;
	private Status status = Status.STARTING;
	private Map<Item, Integer> missing = Map.of();
	/** Supply runs finished (materials taken, or the bag emptied at the chests): progress for the stall watch (B40). */
	private int trips;
	@Nullable
	private net.minecraft.network.chat.Component detail;
	private Runnable onChange = () -> {
	};
	/** Helpers working ahead of the lead: which step each is on, and when they last worked (game time). */
	private final Map<UUID, BlockPos> claims = new java.util.HashMap<>();
	private final Map<UUID, Long> helpersSeen = new java.util.HashMap<>();
	private long lastNotified = Long.MIN_VALUE / 2;

	/** True (and remembers now) if nobody was told about this site within the last {@code interval} ticks. */
	public boolean shouldNotify(long gameTime, long interval) {
		if (gameTime - lastNotified < interval) {
			return false;
		}
		lastNotified = gameTime;
		return true;
	}

	/** Blocks placed per builder since the site was loaded (for XP and for the crew). */
	private final Map<UUID, Integer> placedBy = new java.util.HashMap<>();

	public BuildSite(UUID id, UUID owner, String ownerName, ResourceLocation structure, BlueprintData.Placement placement) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.structure = structure;
		this.placement = placement;
		this.kineticOrder = io.github.jcondedata.aliveworkplace.mechanic.Contraptions.active(); // 39.5: a new site (a load sets it from its save)
	}

	/**
	 * 39.5: the structure list goes in a contraption's order (kinetic parts after what holds them up, by group), chosen
	 * when the site is made, so a switch flipped mid-build never moves the saved cursor onto other steps. A site saved
	 * before 39.5 builds in today's order.
	 */
	private boolean kineticOrder;
	/** 39.5: how often the builder went back over the kinetic parts for one broken mid-build (at most twice). Saved. */
	private int kineticPasses;

	/** Whether this site builds in a contraption's order (39.5). */
	public boolean kineticOrder() {
		return kineticOrder;
	}

	/** How often the builder went back over the kinetic parts (39.5). */
	public int kineticPasses() {
		return kineticPasses;
	}

	/**
	 * 39.5: back to the structure list at {@code index} (the first kinetic part), to put back one broken since it was
	 * placed; what's still in place is passed straight over.
	 */
	public void backToKineticParts(int index) {
		kineticPasses++;
		backTo(BuildPlan.Stage.STRUCTURE, index);
	}

	/**
	 * B508: how often the build went back over its structure and decoration for a block broken since it was laid (at
	 * most {@link BuilderWork#MOST_RECHECKS} times a build). Saved; 0 in older saves.
	 */
	private int rechecks;
	/**
	 * B508: the structure and decoration blocks given up for good this build (deferred, then skipped), so the last look
	 * before DONE doesn't go back for them and count them twice. Saved; empty in older saves.
	 */
	private final java.util.Set<BlockPos> leftOut = new java.util.HashSet<>();

	/** How often the build went back for blocks broken mid-build (B508). */
	public int rechecks() {
		return rechecks;
	}

	/** Whether the block at {@code pos} was given up for good this build (B508). */
	public boolean leftOut(BlockPos pos) {
		return leftOut.contains(pos);
	}

	/**
	 * B508: back to {@code stage}'s list at {@code index} (the first block broken since it was laid), to put back what's
	 * gone before the build is called done; what's still in place is passed straight over.
	 */
	public void backToBroken(BuildPlan.Stage stage, int index) {
		rechecks++;
		backTo(stage, index);
	}

	/** B508: blocks still gone once the build has gone back for them as often as it does: counted as skipped. */
	public void leftBroken(int count) {
		skipped += count;
		onChange.run();
	}

	private void backTo(BuildPlan.Stage stage, int index) {
		this.stage = stage;
		cursor = index;
		retrying = false;
		deferred.clear();
		gaveUp.clear();
		placedThisStage.clear();
		placedStage = null;
		onChange.run();
	}

	/** Whether the ground around the build gets levelled (the blueprint's switch; the gamerule sets how wide). */
	private boolean levelGround = true;
	/** The Village Hall whose Steward started this build (ROADMAP 27.8), or null for a player's own. */
	@Nullable
	private BlockPos stewardHall;

	/** The Wonder this build is a part of (35.4, {@code WonderSite.id}), or null for an ordinary build. */
	@Nullable
	private UUID wonder;
	/** Whether a foundation is filled under it (35.4: a Wonder's later stages stand on the stage below, so only stage I does). */
	private boolean foundation = true;

	/** Whether a foundation is filled under the build. */
	public boolean foundation() {
		return foundation;
	}

	public void setFoundation(boolean foundation) {
		this.foundation = foundation;
		plan = null; // worked out again with (or without) its foundation
		onChange.run();
	}

	/** The Wonder this build is a part of, or null for an ordinary build. */
	@Nullable
	public UUID wonder() {
		return wonder;
	}

	public void setWonder(@Nullable UUID wonder) {
		this.wonder = wonder;
		onChange.run();
	}

	/** The hall whose Steward started this build, or null if a player handed it over. */
	@Nullable
	public BlockPos stewardHall() {
		return stewardHall;
	}

	public boolean isSteward() {
		return stewardHall != null;
	}

	/** {@link #waitingSince} when the build isn't waiting. */
	public static final long NOT_WAITING = Long.MIN_VALUE;
	/** When this Steward's build began waiting for materials (game time; 27.19), or {@link #NOT_WAITING}. */
	private long waitingSince = NOT_WAITING;
	/** Steps skipped because a player's block was in the way of the Steward's build (27.19). */
	private int playerBlocks;

	public long waitingSince() {
		return waitingSince;
	}

	public void setWaitingSince(long time) {
		this.waitingSince = time;
		onChange.run();
	}

	public int playerBlocks() {
		return playerBlocks;
	}

	/** A player's block is in the way: left where it is, its step skipped. */
	public void playerBlockInTheWay() {
		playerBlocks++;
		onChange.run();
	}

	public void setStewardHall(@Nullable BlockPos hall) {
		this.stewardHall = hall == null ? null : hall.immutable();
		onChange.run();
	}

	public boolean levelGround() {
		return levelGround;
	}

	public void setLevelGround(boolean levelGround) {
		this.levelGround = levelGround;
		plan = null; // the landscaping list depends on it
		onChange.run();
	}

	// --- plan & stepping -------------------------------------------------------------------

	@Nullable
	public BuildPlan plan(ServerLevel level) {
		if (plan == null) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level.getServer(), structure);
			if (blueprint.isEmpty()) {
				status = Status.BLUEPRINT_MISSING;
				return null;
			}
			if (furniture == null) {
				// 37.3: chosen once, for a new build; a repair or a taking-down is of what stands, built with what it was
				furniture = deconstruct || repair || cellarOnly ? BuildSiteManager.get(level).furniture(placement)
					: io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.forNewBuild(structure, level, placement.origin()); // 37.4: a partner's Trainer's room
				onChange.run();
			}
			Blueprint drawn = blueprint.get();
			blueprint = Optional.of(furniture.apply(blueprint.get(), structure));
			if (cellarOnly) {
				blueprint = Optional.of(Cellars.cellarOnly(blueprint.get())); // 36.7: the cellar and the way down, nothing above
			}
			int depth = foundation && !cellarOnly ? Rules.number(level, io.github.jcondedata.aliveworkplace.registry.ModGameRules.FOUNDATION_DEPTH) : 0;
			int margin = levelGround && !cellarOnly ? Rules.number(level, io.github.jcondedata.aliveworkplace.registry.ModGameRules.LEVEL_GROUND) : 0;
			plan = deconstruct ? BuildPlan.deconstruct(blueprint.get(), placement)
				: repair && refresh != null ? Refreshes.plan(level, drawn, structure, refresh, placement, furniture) // B642: furnished as built, and anew
				: repair ? BuildPlan.repair(blueprint.get(), placement, level, BuildSiteManager.get(level).gaveUp(placement))
				: BuildPlan.create(blueprint.get(), placement, level, depth, margin);
			if (kineticOrder && !deconstruct && !repair) {
				plan = plan.kineticOrdered(); // 39.5: supports, then shafts and gearboxes, cogwheels, belts
			}
			if (progressFromCursor) {
				progressFromCursor = false;
				shownProgress = Math.max(shownProgress, oldSaveProgress(plan));
			}
			if (stage == BuildPlan.Stage.FOUNDATION || stage == BuildPlan.Stage.LANDSCAPE || refresh != null) {
				// (36.21: a refresh's lists are worked out from what stands too)
				// The foundation and landscaping lists depend on the terrain, which we have been changing:
				// start over (what's already done is skipped straight away).
				cursor = 0;
				retrying = false;
				deferred.clear();
				gaveUp.clear();
			}
		}
		return plan;
	}

	/**
	 * B64: the progress a pre-B46 save showed when it was written, from its saved stage and cursor. Progress counts
	 * the foundation, and its list is worked out again from the terrain on load: the blocks already filled there count
	 * as ground now and drop off it, so a save in the foundation, walls or decoration would show less than it did. The
	 * list it was saved against is about the new one plus the foundation blocks placed so far: {@link #placed} minus
	 * the blocks of later stages the cursor has passed.
	 */
	private float oldSaveProgress(BuildPlan plan) {
		if (deconstruct || repair) {
			return 0f;
		}
		int structure = plan.steps(BuildPlan.Stage.STRUCTURE).size();
		int decoration = plan.steps(BuildPlan.Stage.DECORATION).size();
		int after = switch (stage) { // blocks of the stages after the foundation already passed
			case STRUCTURE -> retrying ? structure : cursor;
			case DECORATION -> structure + (retrying ? decoration : cursor);
			default -> 0;
		};
		if (stage != BuildPlan.Stage.FOUNDATION && stage != BuildPlan.Stage.STRUCTURE && stage != BuildPlan.Stage.DECORATION) {
			return 0f; // nothing placed yet (clearing), or the building is finished (landscaping, done: 100% anyway)
		}
		int foundation = plan.steps(BuildPlan.Stage.FOUNDATION).size() + Math.max(0, placed - after);
		int total = foundation + structure + decoration;
		if (total <= 0) {
			return 0f;
		}
		int done = stage == BuildPlan.Stage.FOUNDATION ? (retrying ? foundation : Math.min(cursor, foundation)) : foundation + after;
		return Math.max(0f, Math.min(1f, done / (float) total));
	}

	/** The step at the cursor for the current stage, or null when the stage's list is exhausted. */
	@Nullable
	public BuildPlan.Step current(BuildPlan plan) {
		List<BuildPlan.Step> list = plan.steps(stage);
		if (!retrying) {
			return cursor < list.size() ? list.get(cursor) : null;
		}
		return cursor < deferred.size() ? list.get(deferred.get(cursor)) : null;
	}

	/** The step is finished (or was already fine): move on. */
	public void advance() {
		cursor++;
		onChange.run();
	}

	public void markPlaced() {
		placed++;
		advance();
	}

	/** Counts a block placed by {@code builder}; returns how many that builder has placed here. */
	public int placedBy(UUID builder, boolean add) {
		return add ? placedBy.merge(builder, 1, Integer::sum) : placedBy.getOrDefault(builder, 0);
	}

	/** A helper placed a block ahead of the lead: count it, but the lead's cursor stays where it is. */
	public void countPlaced() {
		placed++;
		onChange.run();
	}

	/**
	 * Steps after the lead's current one in this stage, for helpers to work on: while the lead retries deferred steps,
	 * the deferred steps after its current one.
	 */
	public List<BuildPlan.Step> ahead(BuildPlan plan, int max) {
		if (stage == BuildPlan.Stage.DONE) {
			return List.of();
		}
		List<BuildPlan.Step> list = plan.steps(stage);
		List<BuildPlan.Step> out = new ArrayList<>();
		if (retrying) {
			// 23.1a: helpers help with the deferred steps too (the lead alone took a crew of four's last fifth).
			for (int i = cursor + 1; i < deferred.size() && out.size() < max; i++) {
				out.add(list.get(deferred.get(i)));
			}
			return out;
		}
		int end = list.size();
		for (int g = 0; stage == BuildPlan.Stage.STRUCTURE && plan.kineticStart(g) >= 0; g++) {
			if (plan.kineticStart(g) > cursor) {
				end = plan.kineticStart(g); // 39.5: helpers stay in the lead's group, so no part goes in before what it sits on
				break;
			}
		}
		for (int i = cursor + 1; i < end && out.size() < max; i++) {
			out.add(list.get(i));
		}
		return out;
	}

	/** While retrying: moves the current deferred step to the back of the list (a helper is on it). */
	public void postpone() {
		if (retrying && cursor < deferred.size()) {
			deferred.add(deferred.remove(cursor));
			onChange.run();
		}
	}

	/** Deferred steps still to retry, from the cursor on (0 when not retrying). */
	public int retryLeft() {
		return retrying ? Math.max(0, deferred.size() - cursor) : 0;
	}

	/** True while the lead goes back over the stage's deferred steps. */
	public boolean isRetrying() {
		return retrying;
	}

	/** A block the lead is on away from its cursor, among the helpers' (23.1a); helpers leave it alone. Not saved. */
	@Nullable
	private BlockPos leadClaim;

	public void setLeadClaim(@Nullable BlockPos pos) {
		leadClaim = pos;
	}

	@Nullable
	public BlockPos leadClaim() {
		return leadClaim;
	}

	public boolean claimedByOther(UUID who, BlockPos pos) {
		if (pos.equals(leadClaim) && !who.equals(builder)) {
			return true;
		}
		for (Map.Entry<UUID, BlockPos> e : claims.entrySet()) {
			if (!e.getKey().equals(who) && e.getValue().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	public BlockPos claim(UUID who) {
		return claims.get(who);
	}

	public void claim(UUID who, BlockPos pos, long gameTime) {
		claims.put(who, pos);
		helpersSeen.put(who, gameTime);
	}

	public void release(UUID who) {
		claims.remove(who);
		crewColumns.remove(who);
	}

	/** Helpers that worked on this site within the last few seconds. */
	public List<UUID> helpers(long gameTime) {
		helpersSeen.values().removeIf(t -> gameTime - t > 200);
		claims.keySet().retainAll(helpersSeen.keySet());
		crewColumns.keySet().removeIf(who -> !who.equals(builder) && !helpersSeen.containsKey(who));
		return List.copyOf(helpersSeen.keySet());
	}

	// --- a Wonder's crew (35.5): each member on a column of its own. Not saved: worked out again after a load. ----------

	/** The crew column (BuildPlan#column) each member of the crew is on. */
	private final Map<UUID, Long> crewColumns = new java.util.HashMap<>();
	/** The game tick {@link #crewLayer} was worked out for, the stage it was for, and the layer. */
	private long crewLayerTick = Long.MIN_VALUE;
	@Nullable
	private BuildPlan.Stage crewLayerStage;
	private int crewLayer;

	/**
	 * Steps the lead gave up on this stage (deferred twice): a crew's lowest layer doesn't wait for them, and the block
	 * over one goes up without it (B139). Saved: after a reload a given-up block would hold its column up for good.
	 */
	private final java.util.Set<BlockPos> gaveUp = new java.util.HashSet<>();

	/**
	 * Steps placed in {@link #placedStage} (B139): a crew doesn't go back to one that changed by itself since (farmland
	 * drying out to dirt before its water comes, grass under a floor), as a lone builder never goes back behind its
	 * cursor. Not saved: after a load such a block is redone once at most.
	 */
	private final java.util.Set<BlockPos> placedThisStage = new java.util.HashSet<>();
	@Nullable
	private BuildPlan.Stage placedStage;

	/** The block at {@code pos} was placed (B139). */
	public void placedNow(BlockPos pos) {
		if (placedStage != stage) {
			placedThisStage.clear();
			placedStage = stage;
		}
		placedThisStage.add(pos.immutable());
	}

	/** Whether the block at {@code pos} was placed in this stage (since the site was loaded). */
	public boolean placedThisStage(BlockPos pos) {
		return placedStage == stage && placedThisStage.contains(pos);
	}

	/** Whether the lead gave up on the step at {@code pos} (35.5). */
	public boolean gaveUp(BlockPos pos) {
		return gaveUp.contains(pos);
	}

	/** The column {@code who} is on, or null. */
	@Nullable
	public Long crewColumn(UUID who) {
		return crewColumns.get(who);
	}

	/** Whether someone other than {@code who} is on column {@code column}. */
	public boolean columnTakenByOther(UUID who, long column) {
		for (Map.Entry<UUID, Long> e : crewColumns.entrySet()) {
			if (e.getValue() == column && !e.getKey().equals(who)) {
				return true;
			}
		}
		return false;
	}

	/** {@code who} works column {@code column} (and no other). */
	public void takeColumn(UUID who, long column) {
		crewColumns.put(who, column);
	}

	/** {@code who} lets its column go. */
	public void dropColumn(UUID who) {
		crewColumns.remove(who);
	}

	/** Every member's column, for the crew tests and the debug log. */
	public Map<UUID, Long> crewColumns() {
		return java.util.Collections.unmodifiableMap(crewColumns);
	}

	/** The part's lowest (while clearing: highest) unfinished layer, cached for the tick {@code gameTime} of {@link #stage}. */
	public int crewLayer(long gameTime, java.util.function.IntSupplier compute) {
		if (crewLayerTick != gameTime || crewLayerStage != stage) {
			crewLayer = compute.getAsInt();
			crewLayerTick = gameTime;
			crewLayerStage = stage;
		}
		return crewLayer;
	}

	public void seen(UUID helper, long gameTime) {
		helpersSeen.put(helper, gameTime);
	}

	/** The step cannot be done yet (nothing to attach to, unreachable): try again at the end of the stage. */
	public void defer() {
		if (retrying) {
			skipped++;
			if (plan != null && cursor < deferred.size() && deferred.get(cursor) < plan.steps(stage).size()) {
				BlockPos pos = plan.steps(stage).get(deferred.get(cursor)).pos();
				gaveUp.add(pos); // 35.5: a crew's layers don't wait for it
				if (stage == BuildPlan.Stage.STRUCTURE || stage == BuildPlan.Stage.DECORATION) {
					leftOut.add(pos.immutable()); // B508: the last look before DONE leaves it be
				}
			}
		} else {
			deferred.add(cursor);
		}
		advance();
	}

	/** Called when {@link #current} returns null: retry deferred steps once, then move to the next stage. */
	public void finishList() {
		if (!retrying && !deferred.isEmpty()) {
			retrying = true;
		} else {
			stage = stage.next();
			retrying = false;
			deferred.clear();
			gaveUp.clear();
		}
		cursor = 0;
		onChange.run();
	}

	/** Upcoming steps in the current stage and the later ones, for material look-ahead. */
	public List<BuildPlan.Step> upcoming(BuildPlan plan, int max) {
		List<BuildPlan.Step> out = new ArrayList<>();
		BuildPlan.Stage s = stage;
		boolean first = true;
		while (s != BuildPlan.Stage.DONE && out.size() < max) {
			// Clearing needs nothing; landscaping uses the dirt it digs up and never holds a build up.
			if (s != BuildPlan.Stage.CLEAR && s != BuildPlan.Stage.LANDSCAPE) {
				List<BuildPlan.Step> list = plan.steps(s);
				if (first && retrying) {
					for (int i = cursor; i < deferred.size() && out.size() < max; i++) {
						out.add(list.get(deferred.get(i)));
					}
				} else {
					int start = first ? cursor : 0;
					for (int i = start; i < list.size() && out.size() < max; i++) {
						out.add(list.get(i));
					}
					if (first) {
						for (int idx : deferred) {
							if (out.size() >= max) {
								break;
							}
							out.add(list.get(idx));
						}
					}
				}
			}
			s = s.next();
			first = false;
		}
		return out;
	}

	/**
	 * While levelling the ground: the landscaping steps still to come (left out of {@link #upcoming}, since they never
	 * hold a build up), so a builder carries dirt for the whole stretch of filling instead of one block per trip.
	 */
	public List<BuildPlan.Step> landscapeLeft(BuildPlan plan, int max) {
		if (stage != BuildPlan.Stage.LANDSCAPE) {
			return List.of();
		}
		List<BuildPlan.Step> list = plan.steps(BuildPlan.Stage.LANDSCAPE);
		List<BuildPlan.Step> out = new ArrayList<>();
		if (retrying) {
			for (int i = cursor; i < deferred.size() && out.size() < max; i++) {
				out.add(list.get(deferred.get(i)));
			}
			return out;
		}
		for (int i = cursor; i < list.size() && out.size() < max; i++) {
			out.add(list.get(i));
		}
		for (int idx : deferred) {
			if (out.size() >= max) {
				break;
			}
			out.add(list.get(idx));
		}
		return out;
	}

	/** 0..1, counting placement work (clearing counts as 0%). */
	public float progress(BuildPlan plan) {
		int total = plan.placeableCount();
		if (total == 0 || stage == BuildPlan.Stage.DONE) {
			return 1f;
		}
		int done = switch (stage) {
			case CLEAR -> 0;
			case FOUNDATION -> retrying ? plan.steps(BuildPlan.Stage.FOUNDATION).size() : cursor;
			case STRUCTURE -> plan.steps(BuildPlan.Stage.FOUNDATION).size() + (retrying ? plan.steps(BuildPlan.Stage.STRUCTURE).size() : cursor);
			case DECORATION -> plan.steps(BuildPlan.Stage.FOUNDATION).size() + plan.steps(BuildPlan.Stage.STRUCTURE).size()
				+ (retrying ? plan.steps(BuildPlan.Stage.DECORATION).size() : cursor);
			case DECONSTRUCT -> retrying ? plan.steps(BuildPlan.Stage.DECONSTRUCT).size() : cursor;
			case LANDSCAPE, DONE -> total; // the building itself is finished
		};
		float now = Math.min(1f, done / (float) total);
		if (now > shownProgress) {
			shownProgress = now;
		}
		return shownProgress;
	}

	// --- accessors -------------------------------------------------------------------------

	public UUID id() {
		return id;
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public ResourceLocation structure() {
		return structure;
	}

	public BlueprintData.Placement placement() {
		return placement;
	}

	public BuildPlan.Stage stage() {
		return stage;
	}

	public boolean isDone() {
		return stage == BuildPlan.Stage.DONE;
	}

	/** Where in the current stage's list the build has got to (tests). */
	public int cursor() {
		return cursor;
	}

	public int skipped() {
		return skipped;
	}

	/**
	 * Changes whenever the site moves on (a step done, deferred or skipped, a new list or stage, a supply run finished);
	 * for stall checks.
	 */
	public long progressMark() {
		return (((((long) stage.ordinal() * 2 + (retrying ? 1 : 0)) * 1_000_003L + cursor) * 1_000_003L + placed) * 1_000_003L + skipped)
			* 1_000_003L + trips;
	}

	/**
	 * A builder of this site finished a supply run: took materials from chests or a crewmate, or emptied its bag at the
	 * chests. A run to chests 60 blocks away takes 30 s and more without a block placed, and is work, not a stall (B40).
	 */
	public void supplied() {
		trips++;
	}

	public int placed() {
		return placed;
	}

	@Nullable
	public UUID builder() {
		return builder;
	}

	public void setBuilder(@Nullable UUID builder) {
		this.builder = builder;
		onChange.run();
	}

	@Nullable
	public BlockPos bench() {
		return bench;
	}

	public void setBench(@Nullable BlockPos bench) {
		this.bench = bench;
		onChange.run();
	}

	public boolean isDeconstruction() {
		return deconstruct;
	}

	public boolean isRepair() {
		return repair;
	}

	/** Turns a new site into a repair (call right after creating it): straight to the structure, nothing cleared. */
	public void setRepair() {
		repair = true;
		stage = BuildPlan.Stage.STRUCTURE;
		cursor = 0;
		plan = null;
		onChange.run();
	}

	/** Whether this repair brings the build up to its latest look (36.21). */
	public boolean isRefresh() {
		return refresh != null;
	}

	/** The key of the older drawing a refresh brings the build on from (36.21), or null. */
	@Nullable
	public String refreshFrom() {
		return refresh;
	}

	/**
	 * Turns a new site into a refresh (36.21, call right after creating it): a repair that first clears what the older
	 * drawing {@code from} put there and the latest one no longer has, then puts in the new pieces.
	 */
	public void setRefresh(String from) {
		repair = true;
		refresh = from;
		stage = BuildPlan.Stage.CLEAR;
		cursor = 0;
		plan = null;
		onChange.run();
	}

	/** Whether this site digs and fits out only the cellar of the finished build on its spot (36.7). */
	public boolean isCellarOnly() {
		return cellarOnly;
	}

	/** Turns a new site into a cellar dig (call right after creating it): only the basement and the way down are worked. */
	public void setCellarOnly() {
		cellarOnly = true;
		plan = null;
		onChange.run();
	}

	/** Turns a new site into a deconstruction job (call right after creating it). */
	public void setDeconstruction() {
		deconstruct = true;
		stage = BuildPlan.Stage.DECONSTRUCT;
		plan = null;
		onChange.run();
	}

	public boolean isQueued() {
		return queued;
	}

	public void setQueued(boolean queued) {
		this.queued = queued;
		onChange.run();
	}

	public Status status() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	/** What the builder is waiting on right now (shown in the status), or null. */
	@Nullable
	public net.minecraft.network.chat.Component detail() {
		return detail;
	}

	public void setDetail(@Nullable net.minecraft.network.chat.Component detail) {
		this.detail = detail;
	}

	public Map<Item, Integer> missing() {
		return missing;
	}

	public void setMissing(Map<Item, Integer> missing) {
		this.missing = missing;
	}

	void setOnChange(Runnable onChange) {
		this.onChange = onChange;
	}

	// --- scaffolding (35.6) ----------------------------------------------------------------

	/** The scaffolding this site's builders have put up and not yet taken down ({@link Scaffolds}). Saved; empty by default. */
	private final List<BlockPos> scaffolds = new ArrayList<>();

	/**
	 * Which modded furniture this build gets (37.3): null until its first plan chooses (a new site), then saved, so a
	 * restart doesn't change a build in progress. A site saved before 37.3 loads with {@link FurnitureChoice#VANILLA}.
	 */
	@Nullable
	private io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture;

	/** The furniture chosen for this build (vanilla until chosen). */
	public io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture() {
		return furniture == null ? io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.VANILLA : furniture;
	}

	/** Sets the furniture (a repair keeps what its building was built with). */
	public void setFurniture(io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice choice) {
		this.furniture = choice;
		plan = null;
		onChange.run();
	}

	public List<BlockPos> scaffolds() {
		return java.util.Collections.unmodifiableList(scaffolds);
	}

	public void addScaffold(BlockPos pos) {
		scaffolds.add(pos.immutable());
		onChange.run();
	}

	public void removeScaffold(BlockPos pos) {
		if (scaffolds.remove(pos)) {
			onChange.run();
		}
	}

	/** True if the list running out now ends the stage (no deferred steps left to retry). */
	public boolean endsStage() {
		return retrying || deferred.isEmpty();
	}

	// --- persistence -----------------------------------------------------------------------

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		Nbt.putUuid(tag, "id", id);
		Nbt.putUuid(tag, "owner", owner);
		tag.putString("owner_name", ownerName);
		tag.putString("structure", structure.toString());
		tag.put("placement", BlueprintData.Placement.CODEC.encodeStart(NbtOps.INSTANCE, placement).getOrThrow());
		tag.putString("stage", stage.name());
		tag.putInt("cursor", cursor);
		tag.putBoolean("retrying", retrying);
		tag.putIntArray("deferred", deferred);
		tag.putInt("skipped", skipped);
		tag.putInt("placed", placed);
		if (shownProgress > 0) {
			tag.putFloat("shown_progress", shownProgress);
		}
		if (builder != null) {
			Nbt.putUuid(tag, "builder", builder);
		}
		if (queued) {
			tag.putBoolean("queued", true);
		}
		if (deconstruct) {
			tag.putBoolean("deconstruct", true);
		}
		if (repair) {
			tag.putBoolean("repair", true);
		}
		if (refresh != null) {
			tag.putString("refresh_from", refresh);
		}
		if (cellarOnly) {
			tag.putBoolean("cellar_only", true);
		}
		if (!levelGround) {
			tag.putBoolean("no_level_ground", true);
		}
		if (bench != null) {
			tag.putLong("bench", bench.asLong());
		}
		if (stewardHall != null) {
			tag.putBoolean("steward", true);
			tag.putLong("hall", stewardHall.asLong());
		}
		if (waitingSince != NOT_WAITING) {
			tag.putLong("waiting_since", waitingSince);
		}
		if (playerBlocks > 0) {
			tag.putInt("player_blocks", playerBlocks);
		}
		if (wonder != null) {
			Nbt.putUuid(tag, "wonder", wonder);
		}
		if (!foundation) {
			tag.putBoolean("no_foundation", true);
		}
		if (!scaffolds.isEmpty()) {
			tag.putLongArray("scaffolds", scaffolds.stream().mapToLong(BlockPos::asLong).toArray());
		}
		if (!gaveUp.isEmpty()) {
			tag.putLongArray("gave_up", gaveUp.stream().mapToLong(BlockPos::asLong).toArray());
		}
		if (kineticOrder) {
			tag.putBoolean("kinetic_order", true); // 39.5
		}
		if (kineticPasses > 0) {
			tag.putInt("kinetic_passes", kineticPasses);
		}
		if (rechecks > 0) {
			tag.putInt("rechecks", rechecks); // B508
		}
		if (!leftOut.isEmpty()) {
			tag.putLongArray("left_out", leftOut.stream().mapToLong(BlockPos::asLong).toArray()); // B508
		}
		if (furniture == null) {
			tag.putBoolean("furniture_unchosen", true); // 37.3: a new site not yet planned chooses when it is
		} else if (!furniture.isVanilla()) {
			tag.put("furniture", furniture.save());
		}
		return tag;
	}

	@Nullable
	public static BuildSite load(CompoundTag tag) {
		ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(tag, "structure"));
		Optional<BlueprintData.Placement> placement = BlueprintData.Placement.CODEC.parse(NbtOps.INSTANCE, tag.get("placement")).result();
		if (structure == null || placement.isEmpty() || !Nbt.hasUuid(tag, "id") || !Nbt.hasUuid(tag, "owner")) {
			return null;
		}
		BuildSite site = new BuildSite(Nbt.getUuid(tag, "id"), Nbt.getUuid(tag, "owner"), Nbt.getString(tag, "owner_name"), structure, placement.get());
		try {
			site.stage = BuildPlan.Stage.valueOf(Nbt.getString(tag, "stage"));
		} catch (IllegalArgumentException e) {
			site.stage = BuildPlan.Stage.CLEAR;
		}
		site.cursor = Nbt.getInt(tag, "cursor");
		site.retrying = Nbt.getBoolean(tag, "retrying");
		if (Nbt.has(tag, "deferred", Tag.TAG_INT_ARRAY)) {
			for (int i : ((IntArrayTag) tag.get("deferred")).getAsIntArray()) {
				site.deferred.add(i);
			}
		}
		site.skipped = Nbt.getInt(tag, "skipped");
		site.placed = Nbt.getInt(tag, "placed");
		site.shownProgress = Nbt.getFloat(tag, "shown_progress");
		// B64: saves from 0.138.0 and earlier have no shown_progress; the first plan works it out from the cursor.
		site.progressFromCursor = !Nbt.has(tag, "shown_progress", Tag.TAG_ANY_NUMERIC);
		site.builder = Nbt.hasUuid(tag, "builder") ? Nbt.getUuid(tag, "builder") : null;
		site.queued = Nbt.getBoolean(tag, "queued");
		site.deconstruct = Nbt.getBoolean(tag, "deconstruct");
		site.repair = Nbt.getBoolean(tag, "repair");
		site.refresh = Nbt.has(tag, "refresh_from", Tag.TAG_STRING) ? Nbt.getString(tag, "refresh_from") : null; // 36.21: none in older saves
		site.cellarOnly = Nbt.getBoolean(tag, "cellar_only"); // 36.7: none in older saves
		site.levelGround = !Nbt.getBoolean(tag, "no_level_ground");
		site.bench = Nbt.has(tag, "bench", Tag.TAG_LONG) ? BlockPos.of(Nbt.getLong(tag, "bench")) : null;
		site.stewardHall = Nbt.getBoolean(tag, "steward") && Nbt.has(tag, "hall", Tag.TAG_LONG) ? BlockPos.of(Nbt.getLong(tag, "hall")) : null;
		site.waitingSince = Nbt.has(tag, "waiting_since", Tag.TAG_LONG) ? Nbt.getLong(tag, "waiting_since") : NOT_WAITING;
		site.playerBlocks = Nbt.getInt(tag, "player_blocks");
		site.wonder = Nbt.hasUuid(tag, "wonder") ? Nbt.getUuid(tag, "wonder") : null;
		site.foundation = !Nbt.getBoolean(tag, "no_foundation");
		for (long p : Nbt.getLongArray(tag, "scaffolds")) { // 35.6: none in older saves
			site.scaffolds.add(BlockPos.of(p));
		}
		for (long p : Nbt.getLongArray(tag, "gave_up")) { // B139: none in older saves
			site.gaveUp.add(BlockPos.of(p));
		}
		site.kineticOrder = Nbt.getBoolean(tag, "kinetic_order"); // 39.5: older saves build in today's order
		site.kineticPasses = Nbt.getInt(tag, "kinetic_passes");
		site.rechecks = Nbt.getInt(tag, "rechecks"); // B508: none in older saves
		for (long p : Nbt.getLongArray(tag, "left_out")) {
			site.leftOut.add(BlockPos.of(p));
		}
		// 37.3: older saves have neither, and build vanilla
		site.furniture = Nbt.getBoolean(tag, "furniture_unchosen") ? null
			: io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.load(Nbt.getList(tag, "furniture", Tag.TAG_COMPOUND));
		return site;
	}

	/** Missing materials as "40 Glass, 12 Oak Planks" style pairs, largest first. */
	public List<Map.Entry<Item, Integer>> missingSorted() {
		List<Map.Entry<Item, Integer>> list = new ArrayList<>(new LinkedHashMap<>(missing).entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list;
	}
}
