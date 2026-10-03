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
	@Nullable
	private UUID builder;
	/** Waiting in the builder's queue (the builder is busy with an earlier site). */
	private boolean queued;
	/** Taking the build down instead of putting it up. */
	private boolean deconstruct;
	/** Putting back what's missing from a finished build (see {@link BuildPlan#repair}). */
	private boolean repair;
	/** The lead builder's bench: where the supply chests are, for helpers too. Null in pre-0.6 saves. */
	@Nullable
	private BlockPos bench;

	// Transient, recomputed as needed
	@Nullable
	private BuildPlan plan;
	private Status status = Status.STARTING;
	private Map<Item, Integer> missing = Map.of();
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
	}

	/** Whether the ground around the build gets levelled (the blueprint's switch; the gamerule sets how wide). */
	private boolean levelGround = true;

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
			int depth = Rules.number(level, io.github.jcondedata.aliveworkplace.registry.ModGameRules.FOUNDATION_DEPTH);
			int margin = levelGround ? Rules.number(level, io.github.jcondedata.aliveworkplace.registry.ModGameRules.LEVEL_GROUND) : 0;
			plan = deconstruct ? BuildPlan.deconstruct(blueprint.get(), placement)
				: repair ? BuildPlan.repair(blueprint.get(), placement, level)
				: BuildPlan.create(blueprint.get(), placement, level, depth, margin);
			if (stage == BuildPlan.Stage.FOUNDATION || stage == BuildPlan.Stage.LANDSCAPE) {
				// The foundation and landscaping lists depend on the terrain, which we have been changing:
				// start over (what's already done is skipped straight away).
				cursor = 0;
				retrying = false;
				deferred.clear();
			}
		}
		return plan;
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
	 * Steps after the lead's current one in this stage, for helpers to work on. Empty while the lead
	 * retries deferred steps (the end of a stage is left to the lead).
	 */
	public List<BuildPlan.Step> ahead(BuildPlan plan, int max) {
		if (retrying || stage == BuildPlan.Stage.DONE) {
			return List.of();
		}
		List<BuildPlan.Step> list = plan.steps(stage);
		List<BuildPlan.Step> out = new ArrayList<>();
		for (int i = cursor + 1; i < list.size() && out.size() < max; i++) {
			out.add(list.get(i));
		}
		return out;
	}

	public boolean claimedByOther(UUID who, BlockPos pos) {
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
	}

	/** Helpers that worked on this site within the last few seconds. */
	public List<UUID> helpers(long gameTime) {
		helpersSeen.values().removeIf(t -> gameTime - t > 200);
		claims.keySet().retainAll(helpersSeen.keySet());
		return List.copyOf(helpersSeen.keySet());
	}

	public void seen(UUID helper, long gameTime) {
		helpersSeen.put(helper, gameTime);
	}

	/** The step cannot be done yet (nothing to attach to, unreachable): try again at the end of the stage. */
	public void defer() {
		if (retrying) {
			skipped++;
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
		return Math.min(1f, done / (float) total);
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

	public int skipped() {
		return skipped;
	}

	/** Changes whenever the site moves on (a step done, deferred or skipped, a new list or stage); for stall checks. */
	public long progressMark() {
		return ((((long) stage.ordinal() * 2 + (retrying ? 1 : 0)) * 1_000_003L + cursor) * 1_000_003L + placed) * 1_000_003L + skipped;
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
		if (!levelGround) {
			tag.putBoolean("no_level_ground", true);
		}
		if (bench != null) {
			tag.putLong("bench", bench.asLong());
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
		site.builder = Nbt.hasUuid(tag, "builder") ? Nbt.getUuid(tag, "builder") : null;
		site.queued = Nbt.getBoolean(tag, "queued");
		site.deconstruct = Nbt.getBoolean(tag, "deconstruct");
		site.repair = Nbt.getBoolean(tag, "repair");
		site.levelGround = !Nbt.getBoolean(tag, "no_level_ground");
		site.bench = Nbt.has(tag, "bench", Tag.TAG_LONG) ? BlockPos.of(Nbt.getLong(tag, "bench")) : null;
		return site;
	}

	/** Missing materials as "40 Glass, 12 Oak Planks" style pairs, largest first. */
	public List<Map.Entry<Item, Integer>> missingSorted() {
		List<Map.Entry<Item, Integer>> list = new ArrayList<>(new LinkedHashMap<>(missing).entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list;
	}
}
