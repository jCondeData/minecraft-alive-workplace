package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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

	// Transient, recomputed as needed
	@Nullable
	private BuildPlan plan;
	private Status status = Status.STARTING;
	private Map<Item, Integer> missing = Map.of();
	@Nullable
	private net.minecraft.network.chat.Component detail;
	private Runnable onChange = () -> {
	};

	public BuildSite(UUID id, UUID owner, String ownerName, ResourceLocation structure, BlueprintData.Placement placement) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.structure = structure;
		this.placement = placement;
	}

	// --- plan & stepping -------------------------------------------------------------------

	@Nullable
	public BuildPlan plan(MinecraftServer server) {
		if (plan == null) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(server, structure);
			if (blueprint.isEmpty()) {
				status = Status.BLUEPRINT_MISSING;
				return null;
			}
			plan = BuildPlan.create(blueprint.get(), placement);
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
			if (s != BuildPlan.Stage.CLEAR) {
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
			case STRUCTURE -> retrying ? plan.steps(BuildPlan.Stage.STRUCTURE).size() : cursor;
			case DECORATION -> plan.steps(BuildPlan.Stage.STRUCTURE).size() + (retrying ? plan.steps(BuildPlan.Stage.DECORATION).size() : cursor);
			case DONE -> total;
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
		tag.putUUID("id", id);
		tag.putUUID("owner", owner);
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
			tag.putUUID("builder", builder);
		}
		return tag;
	}

	@Nullable
	public static BuildSite load(CompoundTag tag) {
		ResourceLocation structure = ResourceLocation.tryParse(tag.getString("structure"));
		Optional<BlueprintData.Placement> placement = BlueprintData.Placement.CODEC.parse(NbtOps.INSTANCE, tag.get("placement")).result();
		if (structure == null || placement.isEmpty() || !tag.hasUUID("id") || !tag.hasUUID("owner")) {
			return null;
		}
		BuildSite site = new BuildSite(tag.getUUID("id"), tag.getUUID("owner"), tag.getString("owner_name"), structure, placement.get());
		try {
			site.stage = BuildPlan.Stage.valueOf(tag.getString("stage"));
		} catch (IllegalArgumentException e) {
			site.stage = BuildPlan.Stage.CLEAR;
		}
		site.cursor = tag.getInt("cursor");
		site.retrying = tag.getBoolean("retrying");
		if (tag.contains("deferred", Tag.TAG_INT_ARRAY)) {
			for (int i : ((IntArrayTag) tag.get("deferred")).getAsIntArray()) {
				site.deferred.add(i);
			}
		}
		site.skipped = tag.getInt("skipped");
		site.placed = tag.getInt("placed");
		site.builder = tag.hasUUID("builder") ? tag.getUUID("builder") : null;
		return site;
	}

	/** Missing materials as "40 Glass, 12 Oak Planks" style pairs, largest first. */
	public List<Map.Entry<Item, Integer>> missingSorted() {
		List<Map.Entry<Item, Integer>> list = new ArrayList<>(new LinkedHashMap<>(missing).entrySet());
		list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		return list;
	}
}
