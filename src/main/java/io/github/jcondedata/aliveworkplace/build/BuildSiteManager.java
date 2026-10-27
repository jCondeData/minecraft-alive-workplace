package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** All build sites in one dimension, saved with the world. */
public final class BuildSiteManager extends SavedData {
	public static final String NAME = "aliveworkplace_build_sites";

	private final Map<UUID, BuildSite> sites = new LinkedHashMap<>();

	/** A building a builder finished here: what, where and for whom (so an upgrade can line up with it). */
	public record Finished(ResourceLocation structure, BlueprintData.Placement placement, UUID owner) {
	}

	/** Most finished buildings remembered per dimension (the oldest are forgotten first). */
	private static final int MAX_FINISHED = 2000;
	private final java.util.ArrayDeque<Finished> finished = new java.util.ArrayDeque<>();
	/**
	 * Per finished building, the blocks a repair went for and couldn't put back (nowhere to stand within reach of them,
	 * say): upkeep leaves those be. Else the builder fetched the same block, found it couldn't be done, skipped it and took
	 * it back to the chest every time it looked, for good (rc.11 playtest: a chimney's campfire). Saved; forgotten when
	 * the building is built again on its spot (an upgrade) or taken down.
	 */
	private final Map<BlueprintData.Placement, java.util.Set<net.minecraft.core.BlockPos>> gaveUp = new java.util.HashMap<>();
	/** Per finished building, the modded furniture it was built with (37.3; none: vanilla). Saved; forgotten with the building. */
	private final Map<BlueprintData.Placement, io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice> furniture = new java.util.HashMap<>();
	/**
	 * 39.5: a finished contraption's turning check, due at game time {@code due}: its kinetic parts' positions, who built it
	 * (for the line the owner reads) and how often it has looked (a wheel's water takes a moment to fall). Saved.
	 */
	public record TurnCheck(ResourceLocation structure, BlueprintData.Placement placement, UUID owner, String builder,
							List<net.minecraft.core.BlockPos> parts, long due, int tries) {
	}

	private final List<TurnCheck> turnChecks = new java.util.ArrayList<>();
	/** 39.5: per finished contraption, the parts its last check found not turning (empty: all turn). Saved with the building. */
	private final Map<BlueprintData.Placement, List<net.minecraft.core.BlockPos>> notTurning = new java.util.HashMap<>();
	/** 36.15: per finished building, the question about repairing it and its answer ({@link RepairAsks}; none: never asked). Saved with the building. */
	private final Map<BlueprintData.Placement, RepairAsks.Ask> repairAsks = new java.util.HashMap<>();

	/** 36.21: the finished buildings whose owner asked for a refresh to the latest look that no builder has taken up yet. Saved with the building. */
	private final java.util.Set<BlueprintData.Placement> refreshAsked = new java.util.HashSet<>();

	/** Whether a refresh of the building at {@code placement} waits for a free builder (36.21). */
	public boolean refreshAsked(BlueprintData.Placement placement) {
		return refreshAsked.contains(placement);
	}

	/** Whether any refresh waits for a free builder (36.21). */
	public boolean anyRefreshAsked() {
		return !refreshAsked.isEmpty();
	}

	/** Records (or, false, takes back) a refresh of the building at {@code placement} waiting for a free builder (36.21). */
	public void setRefreshAsked(BlueprintData.Placement placement, boolean asked) {
		if (asked ? refreshAsked.add(placement) : refreshAsked.remove(placement)) {
			setDirty();
		}
	}

	/** The question about repairing the building at {@code placement} (36.15), or null if it was never asked or is settled. */
	@Nullable
	public RepairAsks.Ask repairAsk(BlueprintData.Placement placement) {
		return repairAsks.get(placement);
	}

	/** Records the question about repairing the building at {@code placement}, or (null) takes it off the list. */
	public void setRepairAsk(BlueprintData.Placement placement, @Nullable RepairAsks.Ask ask) {
		if (ask == null ? repairAsks.remove(placement) != null : !ask.equals(repairAsks.put(placement, ask))) {
			setDirty();
		}
	}

	/** Schedules a turning check (39.5), replacing one for the same spot. */
	public void scheduleTurnCheck(TurnCheck check) {
		turnChecks.removeIf(c -> c.placement().equals(check.placement()));
		turnChecks.add(check);
		setDirty();
	}

	/** Takes the turning checks due by {@code gameTime} off the list (39.5). */
	public List<TurnCheck> dueTurnChecks(long gameTime) {
		List<TurnCheck> due = turnChecks.stream().filter(c -> c.due() <= gameTime).toList();
		if (!due.isEmpty()) {
			turnChecks.removeAll(due);
			setDirty();
		}
		return due;
	}

	/** The turning checks still to come (39.5). */
	public List<TurnCheck> turnChecks() {
		return List.copyOf(turnChecks);
	}

	/** What the turning check found at {@code placement} (39.5): the parts that don't turn; empty if not checked. */
	public java.util.Optional<List<net.minecraft.core.BlockPos>> notTurning(BlueprintData.Placement placement) {
		return java.util.Optional.ofNullable(notTurning.get(placement));
	}

	public void setNotTurning(BlueprintData.Placement placement, List<net.minecraft.core.BlockPos> parts) {
		notTurning.put(placement, List.copyOf(parts));
		setDirty();
	}

	/** Counts every site or finished building added or removed (not saved): lets a reader tell the list hasn't changed (B85). */
	private int changes;

	/** A number that changes whenever a site or finished building is added or removed (not when a site works on). */
	public int changes() {
		return changes;
	}

	/** Remembers a finished building (replacing an older one on the same spot, such as the tier it upgraded). */
	public void recordFinished(ResourceLocation structure, BlueprintData.Placement placement, UUID owner) {
		if (io.github.jcondedata.aliveworkplace.city.Roads.isSegment(structure)) {
			return; // road segments are kept on the City Plan (27.15), not with the buildings
		}
		finished.removeIf(f -> f.placement().equals(placement));
		gaveUp.remove(placement);
		furniture.remove(placement);
		notTurning.remove(placement);
		repairAsks.remove(placement);
		refreshAsked.remove(placement);
		finished.addLast(new Finished(structure, placement, owner));
		while (finished.size() > MAX_FINISHED) {
			finished.removeFirst();
		}
		changes++;
		setDirty();
	}

	/** The modded furniture the finished building on this spot was built with (37.3). */
	public io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture(BlueprintData.Placement placement) {
		return furniture.getOrDefault(placement, io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.VANILLA);
	}

	/** Records the furniture the finished building on this spot was built with (after {@link #recordFinished}). */
	public void setFurniture(BlueprintData.Placement placement, io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice choice) {
		if (choice.isVanilla()) {
			if (furniture.remove(placement) != null) {
				setDirty();
			}
		} else {
			furniture.put(placement, choice);
			setDirty();
		}
	}

	/** Forgets the building on this spot (it was taken down). */
	public void forgetFinished(BlueprintData.Placement placement) {
		gaveUp.remove(placement);
		furniture.remove(placement);
		notTurning.remove(placement);
		repairAsks.remove(placement);
		refreshAsked.remove(placement);
		turnChecks.removeIf(c -> c.placement().equals(placement));
		if (finished.removeIf(f -> f.placement().equals(placement))) {
			changes++;
			setDirty();
		}
	}

	/** A repair of the building at {@code placement} couldn't put back the blocks at {@code positions}: upkeep leaves them be. */
	public void gaveUp(BlueprintData.Placement placement, java.util.Collection<net.minecraft.core.BlockPos> positions) {
		if (!positions.isEmpty() && gaveUp.computeIfAbsent(placement, p -> new java.util.HashSet<>()).addAll(positions)) {
			setDirty();
		}
	}

	/** The blocks of the building at {@code placement} a repair couldn't put back (none: empty). */
	public java.util.Set<net.minecraft.core.BlockPos> gaveUp(BlueprintData.Placement placement) {
		return Collections.unmodifiableSet(gaveUp.getOrDefault(placement, java.util.Set.of()));
	}

	/** The finished buildings in {@code level} whose origin is within {@code radius} blocks of {@code pos}. */
	public List<Finished> finishedNear(ServerLevel level, net.minecraft.core.BlockPos pos, int radius) {
		List<Finished> out = new java.util.ArrayList<>();
		for (Finished f : finished) {
			if (f.placement().dimension().equals(Ids.of(level.dimension())) && f.placement().origin().distSqr(pos) <= (double) radius * radius) {
				out.add(f);
			}
		}
		return out;
	}

	/**
	 * The finished buildings in {@code level} that reach within {@code radius} blocks of {@code pos}: measured to the
	 * nearest block of the building's outline (its origin when its blueprint is gone), so a big building standing by
	 * {@code pos} counts though its corner lies farther (B598).
	 */
	public List<Finished> finishedReaching(ServerLevel level, net.minecraft.core.BlockPos pos, int radius) {
		List<Finished> out = new java.util.ArrayList<>();
		ResourceLocation dimension = Ids.of(level.dimension());
		double max = (double) radius * radius;
		for (Finished f : finished) {
			if (!f.placement().dimension().equals(dimension)) {
				continue;
			}
			net.minecraft.core.BlockPos nearest = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, f.structure())
				.map(b -> {
					net.minecraft.world.level.levelgen.structure.BoundingBox box = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), b.size());
					return new net.minecraft.core.BlockPos(
						Math.clamp(pos.getX(), box.minX(), box.maxX()),
						Math.clamp(pos.getY(), box.minY(), box.maxY()),
						Math.clamp(pos.getZ(), box.minZ(), box.maxZ()));
				})
				.orElse(f.placement().origin());
			if (nearest.distSqr(pos) <= max) {
				out.add(f);
			}
		}
		return out;
	}

	/** Every finished building in {@code level}. */
	public List<Finished> finishedIn(ServerLevel level) {
		List<Finished> out = new java.util.ArrayList<>();
		ResourceLocation dimension = Ids.of(level.dimension());
		for (Finished f : finished) {
			if (f.placement().dimension().equals(dimension)) {
				out.add(f);
			}
		}
		return out;
	}

	/** The finished building of {@code structure} whose outline contains {@code pos}, if any. */
	public java.util.Optional<Finished> finishedAt(ServerLevel level, ResourceLocation structure, net.minecraft.core.BlockPos pos) {
		for (Finished f : finished) {
			if (f.structure().equals(structure) && f.placement().dimension().equals(Ids.of(level.dimension()))) {
				boolean inside = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, structure)
					.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), b.size()).isInside(pos))
					.orElse(false);
				if (inside) {
					return java.util.Optional.of(f);
				}
			}
		}
		return java.util.Optional.empty();
	}

	public static BuildSiteManager get(ServerLevel level) {
		BuildSiteManager manager = level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BuildSiteManager::new, BuildSiteManager::load, null), NAME);
		manager.level = level;
		return manager;
	}

	/** The level these sites are in (each level has its own manager), for taking down a removed site's scaffolding. */
	@Nullable
	private ServerLevel level;

	public BuildSite create(UUID owner, String ownerName, ResourceLocation structure, BlueprintData.Placement placement) {
		BuildSite site = new BuildSite(UUID.randomUUID(), owner, ownerName, structure, placement);
		add(site);
		return site;
	}

	private void add(BuildSite site) {
		site.setOnChange(this::setDirty);
		sites.put(site.id(), site);
		changes++;
		setDirty();
	}

	@Nullable
	public BuildSite get(UUID id) {
		return sites.get(id);
	}

	public void remove(UUID id) {
		BuildSite site = sites.remove(id);
		if (site != null) {
			if (level != null) {
				Scaffolds.dismantle(level, site); // 35.6: cancelled, or ended any other way, with scaffolds still up
			}
			changes++;
			setDirty();
		}
	}

	public Collection<BuildSite> all() {
		return Collections.unmodifiableCollection(sites.values());
	}

	public List<BuildSite> ownedBy(UUID owner) {
		return sites.values().stream().filter(s -> s.owner().equals(owner)).toList();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (BuildSite site : sites.values()) {
			list.add(site.save());
		}
		tag.put("sites", list);
		ListTag done = new ListTag();
		for (Finished f : finished) {
			CompoundTag entry = new CompoundTag();
			entry.putString("structure", f.structure().toString());
			entry.put("placement", BlueprintData.Placement.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, f.placement()).getOrThrow());
			Nbt.putUuid(entry, "owner", f.owner());
			java.util.Set<net.minecraft.core.BlockPos> left = gaveUp.get(f.placement());
			if (left != null && !left.isEmpty()) {
				entry.putLongArray("gave_up", left.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
			}
			io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice chosen = furniture.get(f.placement());
			if (chosen != null && !chosen.isVanilla()) {
				entry.put("furniture", chosen.save());
			}
			List<net.minecraft.core.BlockPos> stuck = notTurning.get(f.placement());
			if (stuck != null) { // 39.5: checked (an empty list: everything turned)
				entry.putLongArray("not_turning", stuck.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
			}
			RepairAsks.Ask ask = repairAsks.get(f.placement());
			if (ask != null) { // 36.15
				entry.putString("repair_ask", ask.state().key());
				entry.putInt("repair_missing", ask.missing());
			}
			if (refreshAsked.contains(f.placement())) { // 36.21
				entry.putBoolean("refresh_asked", true);
			}
			done.add(entry);
		}
		tag.put("finished", done);
		if (!turnChecks.isEmpty()) { // 39.5
			ListTag checks = new ListTag();
			for (TurnCheck c : turnChecks) {
				CompoundTag entry = new CompoundTag();
				entry.putString("structure", c.structure().toString());
				entry.put("placement", BlueprintData.Placement.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, c.placement()).getOrThrow());
				Nbt.putUuid(entry, "owner", c.owner());
				entry.putString("builder", c.builder());
				entry.putLongArray("parts", c.parts().stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
				entry.putLong("due", c.due());
				entry.putInt("tries", c.tries());
				checks.add(entry);
			}
			tag.put("turn_checks", checks);
		}
		return tag;
	}

	public static BuildSiteManager load(CompoundTag tag, HolderLookup.Provider registries) {
		BuildSiteManager manager = new BuildSiteManager();
		ListTag list = Nbt.getList(tag, "sites", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			BuildSite site = BuildSite.load(Nbt.compoundAt(list, i));
			if (site != null) {
				manager.add(site);
			}
		}
		ListTag done = Nbt.getList(tag, "finished", Tag.TAG_COMPOUND);
		for (int i = 0; i < done.size(); i++) {
			CompoundTag entry = Nbt.compoundAt(done, i);
			ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(entry, "structure"));
			var placement = BlueprintData.Placement.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get("placement")).result();
			if (structure != null && placement.isPresent() && Nbt.hasUuid(entry, "owner")) {
				manager.finished.addLast(new Finished(structure, placement.get(), Nbt.getUuid(entry, "owner")));
				if (Nbt.has(entry, "gave_up", Tag.TAG_LONG_ARRAY)) { // (older saves have none)
					java.util.Set<net.minecraft.core.BlockPos> left = new java.util.HashSet<>();
					for (long l : Nbt.getLongArray(entry, "gave_up")) {
						left.add(net.minecraft.core.BlockPos.of(l));
					}
					manager.gaveUp.put(placement.get(), left);
				}
				if (Nbt.has(entry, "furniture", Tag.TAG_LIST)) { // 37.3: older saves have none (vanilla)
					var chosen = io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice.load(Nbt.getList(entry, "furniture", Tag.TAG_COMPOUND));
					if (!chosen.isVanilla()) {
						manager.furniture.put(placement.get(), chosen);
					}
				}
				if (Nbt.has(entry, "not_turning", Tag.TAG_LONG_ARRAY)) { // 39.5: older saves have none (not checked)
					manager.notTurning.put(placement.get(), java.util.Arrays.stream(Nbt.getLongArray(entry, "not_turning"))
						.mapToObj(net.minecraft.core.BlockPos::of).toList());
				}
				RepairAsks.State.of(Nbt.getString(entry, "repair_ask")).ifPresent(state -> // 36.15: older saves have none (never asked)
					manager.repairAsks.put(placement.get(), new RepairAsks.Ask(state, Nbt.getInt(entry, "repair_missing"))));
				if (Nbt.getBoolean(entry, "refresh_asked")) { // 36.21: older saves have none
					manager.refreshAsked.add(placement.get());
				}
			}
		}
		ListTag checks = Nbt.getList(tag, "turn_checks", Tag.TAG_COMPOUND); // 39.5: older saves have none
		for (int i = 0; i < checks.size(); i++) {
			CompoundTag entry = Nbt.compoundAt(checks, i);
			ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(entry, "structure"));
			var placement = BlueprintData.Placement.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, entry.get("placement")).result();
			if (structure != null && placement.isPresent() && Nbt.hasUuid(entry, "owner")) {
				manager.turnChecks.add(new TurnCheck(structure, placement.get(), Nbt.getUuid(entry, "owner"), Nbt.getString(entry, "builder"),
					java.util.Arrays.stream(Nbt.getLongArray(entry, "parts")).mapToObj(net.minecraft.core.BlockPos::of).toList(),
					Nbt.getLong(entry, "due"), Nbt.getInt(entry, "tries")));
			}
		}
		manager.setDirty(false);
		return manager;
	}
}
