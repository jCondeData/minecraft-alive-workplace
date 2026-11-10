package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The buildings on the move in a dimension (ROADMAP 45.3), saved as {@code aliveworkplace_move_sites}: one
 * {@link MoveSite} per building a Mover has started on. A move confirmed on the Hall's Move page waits in
 * {@link PendingMoves} until a Mover of its village picks it up ({@link #begin}); it stays there while he works, and
 * calling it off on the Move page (the entry gone) cancels the site, which puts everything back. A world from before
 * 45.3 loads with none; a site that doesn't read is skipped, never the rest.
 */
public final class MoveSites extends SavedData {
	public static final String NAME = "aliveworkplace_move_sites";

	private final List<MoveSite> sites = new ArrayList<>();
	/** Residents of a move that's over who weren't loaded to take their bed or workstation yet (45.4, {@link MoveResidents}). */
	private final List<MoveResidents.Resident> homecomings = new ArrayList<>();
	/**
	 * Beds and workstations of a move that's over, held for someone who was too far off to be noted as it started (B1154,
	 * {@link MoveResidents.Claim}): whoever remembers the old spot gets the moved one when next seen. At most
	 * {@link #MAX_FORWARDS}; each is let go after {@link MoveResidents#KEEP_TICKS}.
	 */
	private final List<MoveResidents.Claim> forwards = new ArrayList<>();
	/** The most claims kept waiting: the oldest is let go first. */
	public static final int MAX_FORWARDS = 256;
	/** The game time forwards are next looked over (gone blocks dropped, old ones let go); not saved. */
	private long nextSweep;

	public static MoveSites get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(MoveSites::new, MoveSites::load, null), NAME);
	}

	public List<MoveSite> all() {
		return List.copyOf(sites);
	}

	/** The site moving the building whose origin was {@code from}, if one has started. */
	public Optional<MoveSite> forBuilding(BlockPos from) {
		return sites.stream().filter(s -> s.from().equals(from)).findFirst();
	}

	/**
	 * The site a Mover of the village round {@code hall} works on: one already started (two Movers share it), else the
	 * oldest move waiting there, started now (its building captured as it stands). Empty: nothing to move.
	 */
	public Optional<MoveSite> work(ServerLevel level, BlockPos hall) {
		for (MoveSite site : sites) {
			if (site.hall().equals(hall)) {
				return Optional.of(site);
			}
		}
		for (PendingMoves.Move move : PendingMoves.get(level).of(hall)) {
			Optional<MoveSite> started = begin(level, move);
			if (started.isPresent()) {
				return started;
			}
		}
		return Optional.empty();
	}

	/** Starts moving the building of {@code move}: captured as it stands now. Empty if the building is gone (the move is dropped). */
	public Optional<MoveSite> begin(ServerLevel level, PendingMoves.Move move) {
		Optional<MoveSite> known = forBuilding(move.from());
		if (known.isPresent()) {
			return known;
		}
		Optional<BuildSiteManager.Finished> building = BuildSiteManager.get(level).finishedAt(level, move.structure(), move.from());
		BoundingBox box = building.map(f -> MovePlan.box(level, f)).orElse(null);
		Optional<MovePlan> plan = building.flatMap(f -> MovePlan.capture(level, f));
		if (box == null || plan.isEmpty()) {
			PendingMoves.get(level).remove(move.from()); // nothing left to move
			return Optional.empty();
		}
		MoveSite site = MoveSite.start(move.hall(), move.from(), plan.get(), new BlockPos(box.minX(), box.minY(), box.minZ()), move.to(),
			move.rotation(), move.by());
		MoveResidents.begin(level, site, box); // its residents sleep at the inn while it's away (45.4)
		sites.add(site);
		setDirty();
		return Optional.of(site);
	}

	/**
	 * One step on {@code site} (a Mover's swing); when the move is over the site is put away: the Hall's record of the
	 * building follows it to its new spot (or stays, canceled) and the waiting move is cleared. Returns the block worked on.
	 */
	public Optional<BlockPos> step(ServerLevel level, MoveSite site) {
		if (!site.canceled() && site.stage() != MoveSite.Stage.DONE && PendingMoves.get(level).forBuilding(site.from()).isEmpty()) {
			site.cancel(); // called off on the Move page
		}
		Optional<BlockPos> worked = site.stage() == MoveSite.Stage.DONE ? Optional.empty() : site.step(level);
		setDirty();
		if (site.stage() == MoveSite.Stage.DONE) {
			finish(level, site);
		}
		return worked;
	}

	/** Cancels the move of the building at {@code from}: everything taken down goes back up where it was. */
	public boolean cancel(ServerLevel level, BlockPos from) {
		PendingMoves.get(level).remove(from);
		Optional<MoveSite> site = forBuilding(from);
		site.ifPresent(MoveSite::cancel);
		setDirty();
		return site.isPresent();
	}

	private void finish(ServerLevel level, MoveSite site) {
		sites.remove(site);
		PendingMoves.get(level).remove(site.from());
		tell(level, site);
		long time = level.getGameTime();
		homecomings.removeIf(r -> r.until < time);
		homecomings.addAll(MoveResidents.arrive(level, site)); // its residents' beds and workstations are theirs where it stands (45.4)
		forwards.addAll(MoveResidents.forwards(level, site)); // and theirs who were too far off to be noted (B1154)
		sweep(level);
		BuildSiteManager manager = BuildSiteManager.get(level);
		if (site.canceled() || site.plan().structure() == null) {
			return;
		}
		Optional<BuildSiteManager.Finished> old = manager.finishedAt(level, site.plan().structure(), site.from());
		if (old.isEmpty()) {
			return;
		}
		BlueprintData.Placement was = old.get().placement();
		// the template's corner moves with the building: where it was in the old box, turned, from the new corner
		BlockPos origin = site.plan().at(was.origin().subtract(site.oldMin()), site.newMin(), site.rotation());
		BlueprintData.Placement now = new BlueprintData.Placement(was.dimension(), origin, was.rotation().getRotated(site.rotation()), was.mirror());
		var furniture = manager.furniture(was);
		manager.forgetFinished(was);
		manager.recordFinished(old.get().structure(), now, old.get().owner());
		manager.setFurniture(now, furniture);
	}

	/** The residents a moved building still waits for (not loaded since it was put up), for the tests and the scene. */
	public List<MoveResidents.Resident> homecomings() {
		return List.copyOf(homecomings);
	}

	/** The claims a moved building still holds for someone too far off to be noted (B1154), for the tests. */
	public List<MoveResidents.Claim> forwards() {
		return List.copyOf(forwards);
	}

	/** Drops the forwards whose block is gone and lets go of those kept too long, or past {@link #MAX_FORWARDS}. */
	private void sweep(ServerLevel level) {
		sweep(level, level.getGameTime());
	}

	/** {@link #sweep(ServerLevel)} as at game time {@code time} (the tests look days ahead with it). */
	public void sweep(ServerLevel level, long time) {
		nextSweep = time + 1200;
		int before = forwards.size();
		for (java.util.Iterator<MoveResidents.Claim> it = forwards.iterator(); it.hasNext(); ) {
			MoveResidents.Claim c = it.next();
			if (c.until < time) {
				MoveResidents.giveUp(level, c);
				it.remove();
			} else if (c.at() == null || level.isLoaded(c.at()) && !MoveResidents.standing(level, c)) {
				it.remove(); // broken meanwhile: nothing held any more
			}
		}
		while (forwards.size() > MAX_FORWARDS) {
			MoveResidents.giveUp(level, forwards.remove(0));
		}
		if (forwards.size() != before) {
			setDirty();
		}
	}

	/** {@link MoveResidents#tick}: {@code villager}, if a resident of a building on the move or moved, gets what's theirs. */
	void residentTick(ServerLevel level, net.minecraft.world.entity.npc.Villager villager) {
		if (sites.isEmpty() && homecomings.isEmpty() && forwards.isEmpty()) {
			return;
		}
		java.util.UUID id = villager.getUUID();
		for (MoveSite site : sites) {
			for (MoveResidents.Resident r : site.residents()) {
				if (r.id().equals(id)) {
					MoveResidents.check(level, site, r, villager);
					setDirty();
					return;
				}
			}
		}
		for (MoveSite site : sites) {
			if (MoveResidents.late(level, site, villager)) { // too far off to be noted as it started (B1154)
				setDirty();
				return;
			}
		}
		for (java.util.Iterator<MoveResidents.Resident> it = homecomings.iterator(); it.hasNext(); ) {
			MoveResidents.Resident r = it.next();
			if (r.id().equals(id)) {
				MoveResidents.settle(level, r, villager);
				it.remove();
				setDirty();
				return;
			}
		}
		if (forwards.isEmpty()) {
			return;
		}
		if (level.getGameTime() >= nextSweep) {
			sweep(level);
		}
		boolean used = false;
		for (java.util.Iterator<MoveResidents.Claim> it = forwards.iterator(); it.hasNext(); ) {
			if (MoveResidents.forward(level, it.next(), villager)) {
				it.remove();
				used = true;
			}
		}
		if (used) {
			setDirty();
		}
	}

	/** Tells whoever confirmed the move that it's over (moved, or put back). */
	private static void tell(ServerLevel level, MoveSite site) {
		net.minecraft.world.entity.player.Player player = site.by() == null ? null : level.getPlayerByUUID(site.by());
		if (player == null || site.plan().structure() == null) {
			return;
		}
		net.minecraft.network.chat.Component name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(site.plan().structure());
		io.github.jcondedata.aliveworkplace.mc.Chat.chat(player, site.canceled()
			? net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.put_back", name)
			: net.minecraft.network.chat.Component.translatable("message.aliveworkplace.move.done", name, site.total() - site.skipped()));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (MoveSite site : sites) {
			list.add(site.save());
		}
		tag.put("sites", list);
		ListTag people = new ListTag();
		for (MoveResidents.Resident r : homecomings) {
			people.add(r.save());
		}
		tag.put("homecomings", people);
		ListTag held = new ListTag();
		for (MoveResidents.Claim c : forwards) {
			held.add(c.save());
		}
		tag.put("forwards", held);
		return tag;
	}

	public static MoveSites load(CompoundTag tag, HolderLookup.Provider registries) {
		MoveSites out = new MoveSites();
		ListTag list = Nbt.getList(tag, "sites", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			MoveSite.load(Nbt.compoundAt(list, i)).ifPresent(out.sites::add);
		}
		ListTag people = Nbt.getList(tag, "homecomings", Tag.TAG_COMPOUND); // (none in a world from before 45.4)
		for (int i = 0; i < people.size(); i++) {
			MoveResidents.Resident.load(Nbt.compoundAt(people, i)).ifPresent(out.homecomings::add);
		}
		ListTag held = Nbt.getList(tag, "forwards", Tag.TAG_COMPOUND); // (none in a world from before B1154's fix)
		for (int i = 0; i < held.size(); i++) {
			MoveResidents.Claim.load(Nbt.compoundAt(held, i)).ifPresent(out.forwards::add);
		}
		return out;
	}
}
