package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.work.JobSiteTickets;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The residents move too (ROADMAP 45.4). Everyone whose bed or workstation stands in a building on the move is noted on
 * its {@link MoveSite} as it starts (a {@link Resident}, saved with the site). While it's away they sleep in a free bed at
 * the village's inn (a bed round an Inn Counter), else the free bed nearest the Village Hall, outside the building's old
 * and new boxes; a worker keeps their job meanwhile (vanilla takes it from a Novice with no experience whose block is
 * gone, so they get the one point a hire gets). The moment the Mover puts their bed or workstation up again, it's
 * held for them (its point of interest filed and its one place taken, so no jobless villager or homeless neighbor slips
 * in), and their memories are the new block's: home ({@link MemoryModuleType#HOME}) and job site
 * ({@link MemoryModuleType#JOB_SITE}), the bed at the inn given back. Everything else reads those memories: the Hall's
 * People page and census ({@code VillageNeeds.bed}), their home ({@code Homes}, by the Hall's record of the building,
 * which follows it in {@link MoveSites}), the job-status cards and a Builder's bench ({@code Builders.benchPos}).
 * Couples and families are kept by who they are (UUIDs), so they follow by themselves. A Builder's bench moved with the
 * house stays his, and his builds' bench ({@link BuildSite#bench}) goes with it, so they keep reaching its chests.
 * <p>
 * A resident who isn't loaded when their block goes up gets it as soon as they are (each villager looks once a second,
 * {@link #tick}); one whose move is over is kept for {@link #KEEP_TICKS} in {@link MoveSites}' homecomings. A move
 * called off puts them back the same way, at the old spot. Nothing changes for a world from before 45.4: a site saved
 * then has no residents noted.
 * <p>
 * Someone who lives or works there but is too far off to be noted as it starts (over 128 blocks, or not loaded; B1154)
 * isn't forgotten: every bed and workstation in it that's taken but by nobody noted is a {@link Claim}, held for them
 * where it goes up. Seen during the move (their home or job site memory in the old box), they're noted then; seen after
 * it, their memory of the old spot is turned into the moved block ({@link #forward}). A claim nobody comes back for is
 * let go after {@link #KEEP_TICKS}, and one whose block is broken meanwhile is dropped.
 */
public final class MoveResidents {
	/** How long a resident who hasn't been loaded since their move ended is still waited for: five days. */
	public static final long KEEP_TICKS = 5 * 24000L;

	/**
	 * One resident of a building on the move: who; their bed's head and their workstation relative to the building's
	 * lowest corner (null: theirs isn't in it); the bed they sleep in while it's away (null: none found yet); where their
	 * bed and workstation are held for them now (null: not standing yet); and whether their memories say so already.
	 */
	public static final class Resident {
		final UUID id;
		/** (Null once the player gives them another bed at the Hall meanwhile: the moved one isn't theirs any more, B1194.) */
		@Nullable
		BlockPos bed;
		/** (Null once the player lets them go or gives them another job meanwhile, B1194.) */
		@Nullable
		BlockPos job;
		@Nullable
		BlockPos stay;
		@Nullable
		BlockPos bedAt;
		@Nullable
		BlockPos jobAt;
		boolean bedHome;
		boolean jobHome;
		/** For a homecoming: the game time it's given up after. */
		long until;

		Resident(UUID id, @Nullable BlockPos bed, @Nullable BlockPos job) {
			this.id = id;
			this.bed = bed == null ? null : bed.immutable();
			this.job = job == null ? null : job.immutable();
		}

		public UUID id() {
			return id;
		}

		/** Their bed's head, relative to the building's lowest corner; null if their bed isn't in it. */
		@Nullable
		public BlockPos bed() {
			return bed;
		}

		/** Their workstation, relative to the building's lowest corner; null if it isn't in it. */
		@Nullable
		public BlockPos job() {
			return job;
		}

		/** The bed they sleep in while the building is away (at the inn or by the Hall); null: none. */
		@Nullable
		public BlockPos stay() {
			return stay;
		}

		/** Whether everything of theirs that stands again is theirs in their memories. */
		public boolean home() {
			return (bedAt == null || bedHome) && (jobAt == null || jobHome);
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putUUID("id", id);
			if (bed != null) {
				tag.put("bed", NbtUtils.writeBlockPos(bed));
			}
			if (job != null) {
				tag.put("job", NbtUtils.writeBlockPos(job));
			}
			if (stay != null) {
				tag.put("stay", NbtUtils.writeBlockPos(stay));
			}
			if (bedAt != null) {
				tag.put("bed_at", NbtUtils.writeBlockPos(bedAt));
			}
			if (jobAt != null) {
				tag.put("job_at", NbtUtils.writeBlockPos(jobAt));
			}
			tag.putBoolean("bed_home", bedHome);
			tag.putBoolean("job_home", jobHome);
			tag.putLong("until", until);
			return tag;
		}

		static Optional<Resident> load(CompoundTag tag) {
			if (!tag.hasUUID("id")) {
				return Optional.empty();
			}
			Resident r = new Resident(tag.getUUID("id"), NbtUtils.readBlockPos(tag, "bed").orElse(null), NbtUtils.readBlockPos(tag, "job").orElse(null));
			r.stay = NbtUtils.readBlockPos(tag, "stay").orElse(null);
			r.bedAt = NbtUtils.readBlockPos(tag, "bed_at").orElse(null);
			r.jobAt = NbtUtils.readBlockPos(tag, "job_at").orElse(null);
			r.bedHome = tag.getBoolean("bed_home");
			r.jobHome = tag.getBoolean("job_home");
			r.until = tag.getLong("until");
			return Optional.of(r);
		}
	}

	/**
	 * A bed or workstation in a building on the move that's taken, but by nobody noted as it started (B1154): its spot
	 * relative to the building's lowest corner, whether it's a bed (else a workstation), and where it's held now (null:
	 * not standing yet). Once the move is over it waits in {@link MoveSites}' forwards with its old spot and the game time
	 * it's given up after.
	 */
	public static final class Claim {
		final BlockPos rel;
		final boolean bed;
		@Nullable
		BlockPos at;
		@Nullable
		BlockPos old;
		long until;

		Claim(BlockPos rel, boolean bed) {
			this.rel = rel.immutable();
			this.bed = bed;
		}

		/** Where it's held for its owner now; null: not standing. */
		@Nullable
		public BlockPos at() {
			return at;
		}

		/** Whether it's a bed (else a workstation). */
		public boolean bed() {
			return bed;
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.put("rel", NbtUtils.writeBlockPos(rel));
			tag.putBoolean("bed", bed);
			if (at != null) {
				tag.put("at", NbtUtils.writeBlockPos(at));
			}
			if (old != null) {
				tag.put("old", NbtUtils.writeBlockPos(old));
			}
			tag.putLong("until", until);
			return tag;
		}

		static Optional<Claim> load(CompoundTag tag) {
			Optional<BlockPos> rel = NbtUtils.readBlockPos(tag, "rel");
			if (rel.isEmpty()) {
				return Optional.empty();
			}
			Claim c = new Claim(rel.get(), tag.getBoolean("bed"));
			c.at = NbtUtils.readBlockPos(tag, "at").orElse(null);
			c.old = NbtUtils.readBlockPos(tag, "old").orElse(null);
			c.until = tag.getLong("until");
			return Optional.of(c);
		}
	}

	// ---- the move starts

	/**
	 * The building in {@code box} starts moving on {@code site}: everyone whose bed or workstation is in it is noted, the
	 * workers keep their jobs while their blocks are packed, and those with a bed there sleep at the inn or by the Hall.
	 */
	static void begin(ServerLevel level, MoveSite site, BoundingBox box) {
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		for (Villager villager : level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(128))) {
			note(level, site, box, min, villager);
		}
		// beds and workstations someone has who isn't here to be noted (far off, or not loaded): held for them (B1154)
		BlockPos center = box.getCenter();
		int radius = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 1;
		level.getPoiManager().getInSquare(MoveResidents::claimable, center, radius, PoiManager.Occupancy.IS_OCCUPIED)
			.filter(p -> box.isInside(p.getPos()))
			.sorted(Comparator.comparingLong(p -> p.getPos().asLong()))
			.forEach(p -> {
				BlockPos rel = p.getPos().subtract(min);
				boolean bed = p.getPoiType().is(PoiTypes.HOME);
				boolean noted = site.residents().stream().anyMatch(r -> rel.equals(bed ? r.bed : r.job));
				if (!noted) {
					site.claims().add(new Claim(rel, bed));
				}
			});
	}

	private static boolean claimable(Holder<PoiType> type) {
		return type.is(PoiTypes.HOME) || VillagerProfession.ALL_ACQUIRABLE_JOBS.test(type);
	}

	/**
	 * Notes {@code villager} as a resident of {@code site} if their bed or workstation is in {@code box} (the building as
	 * it stood, its lowest corner {@code min}). Returns the resident, or null if they don't live or work there.
	 */
	@Nullable
	private static Resident note(ServerLevel level, MoveSite site, BoundingBox box, BlockPos min, Villager villager) {
		BlockPos bed = inside(level, box, villager.getBrain().getMemory(MemoryModuleType.HOME)).map(p -> p.subtract(min)).orElse(null);
		BlockPos job = inside(level, box, villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)).map(p -> p.subtract(min)).orElse(null);
		if (bed == null && job == null) {
			return null;
		}
		Resident resident = new Resident(villager.getUUID(), bed, job);
		site.residents().add(resident);
		// a claim held for them since the move started is theirs: what of it stands again is held already
		for (java.util.Iterator<Claim> it = site.claims().iterator(); it.hasNext(); ) {
			Claim c = it.next();
			if (c.rel.equals(c.bed ? bed : job)) {
				if (c.bed) {
					resident.bedAt = c.at;
				} else {
					resident.jobAt = c.at;
				}
				it.remove();
			}
		}
		if (job != null && villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1); // keeps the job while the block is packed (as a hire gets, Jobs.employ)
		}
		if (bed != null && resident.bedAt == null && site.stage() != MoveSite.Stage.DONE) {
			shelter(level, site, resident, villager);
		}
		return resident;
	}

	/**
	 * {@code villager} wasn't noted as {@code site} started (too far off, or not loaded) but lives or works in the building
	 * (their memory says so): noted now, and given what of theirs stands again. False if they don't live or work there.
	 */
	static boolean late(ServerLevel level, MoveSite site, Villager villager) {
		Resident r = note(level, site, site.oldBox(), site.oldMin(), villager);
		if (r == null) {
			return false;
		}
		check(level, site, r, villager);
		return true;
	}

	private static Optional<BlockPos> inside(ServerLevel level, BoundingBox box, Optional<GlobalPos> pos) {
		return pos.filter(g -> g.dimension() == level.dimension() && box.isInside(g.pos())).map(GlobalPos::pos);
	}

	/** {@code resident} sleeps somewhere else while the building is away: a free bed at the inn, else nearest the Hall. */
	static void shelter(ServerLevel level, MoveSite site, Resident resident, Villager villager) {
		Optional<BlockPos> bed = stayBed(level, site);
		if (bed.isPresent() && HallBeds.moveIn(level, villager, bed.get())) {
			resident.stay = bed.get().immutable(); // (their own bed is let go: it comes down)
		}
	}

	/** A free bed for the move's residents: round the village's inn counters ({@link Innkeepers#counters}; nearest the Hall first), else round the Hall. */
	public static Optional<BlockPos> stayBed(ServerLevel level, MoveSite site) {
		BoundingBox old = site.oldBox();
		BoundingBox now = site.newBox();
		Predicate<BlockPos> ok = p -> !old.isInside(p) && !now.isInside(p) && !CampBeds.isWagonBed(level, p);
		PoiManager poi = level.getPoiManager();
		Predicate<Holder<PoiType>> beds = h -> h.is(PoiTypes.HOME);
		List<BlockPos> inns = Innkeepers.counters(level, site.hall(), VillageHalls.RADIUS).stream()
			.sorted(Comparator.comparingDouble((BlockPos p) -> p.distSqr(site.hall())).thenComparingLong(BlockPos::asLong)).toList();
		for (BlockPos counter : inns) {
			Optional<BlockPos> bed = poi.findClosest(beds, ok, counter, Innkeepers.RADIUS, PoiManager.Occupancy.HAS_SPACE);
			if (bed.isPresent()) {
				return bed;
			}
		}
		return poi.findClosest(beds, ok, site.hall(), VillageHalls.RADIUS, PoiManager.Occupancy.HAS_SPACE);
	}

	// ---- blocks coming down and going up

	/** Block {@code i} of {@code site} came down: a resident whose bed or workstation it was hasn't got it standing now. */
	static void taken(MoveSite site, int i) {
		BlockPos rel = site.plan().blocks().get(i).rel();
		for (Resident r : site.residents()) {
			if (rel.equals(r.bed)) {
				r.bedAt = null;
				r.bedHome = false;
			}
			if (rel.equals(r.job)) {
				r.jobAt = null;
				r.jobHome = false;
			}
		}
		for (Claim c : site.claims()) {
			if (rel.equals(c.rel)) {
				c.at = null;
			}
		}
	}

	/**
	 * Block {@code i} of {@code site} was put up at {@code pos} ({@code now}: at the new spot, or back at the old one): a
	 * workstation's builds follow it, and a resident's bed or workstation is held for them and is theirs at once.
	 */
	static void placed(ServerLevel level, MoveSite site, int i, BlockPos pos, byte now) {
		if (PoiTypes.forState(level.getBlockState(pos)).isEmpty()) {
			return; // nobody's bed or workstation
		}
		BlockPos rel = site.plan().blocks().get(i).rel();
		BlockPos other = now == MoveSite.NEW ? site.oldMin().offset(rel) : site.plan().at(rel, site.newMin(), site.rotation());
		for (BuildSite build : BuildSiteManager.get(level).all()) {
			if (other.equals(build.bench())) {
				build.setBench(pos.immutable()); // a Builder's bench moved with the house: his builds take from its chests there
			}
		}
		for (Resident r : site.residents()) {
			if (rel.equals(r.bed) && reserve(level, pos)) {
				r.bedAt = pos.immutable();
				r.bedHome = false;
			}
			if (rel.equals(r.job) && reserve(level, pos)) {
				r.jobAt = pos.immutable();
				r.jobHome = false;
			}
			if (!r.home() && level.getEntity(r.id) instanceof Villager villager) {
				settle(level, r, villager);
			}
		}
		for (Claim c : site.claims()) {
			if (rel.equals(c.rel) && reserve(level, pos)) {
				c.at = pos.immutable(); // held for whoever has it, when they're next seen (B1154)
			}
		}
	}

	/**
	 * Holds the bed or workstation at {@code pos} for a resident: its point of interest filed now if the level hasn't yet
	 * (it files a block's after the tick's work, B132) and its one place taken. False if it isn't one or someone has it.
	 */
	static boolean reserve(ServerLevel level, BlockPos pos) {
		PoiManager poi = level.getPoiManager();
		if (poi.getType(pos).isEmpty()) {
			Optional<Holder<PoiType>> type = PoiTypes.forState(level.getBlockState(pos));
			if (type.isEmpty()) {
				return false;
			}
			poi.add(pos.immutable(), type.get());
		}
		return poi.take(h -> true, (h, p) -> p.equals(pos), pos, 1).isPresent();
	}

	/** {@code villager}'s memories say what's held for them: their bed (the one at the inn given back) and their job site. */
	static void settle(ServerLevel level, Resident r, Villager villager) {
		if (r.bedAt != null && !r.bedHome) {
			GlobalPos target = GlobalPos.of(level.dimension(), r.bedAt);
			Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
			if (home.isEmpty() || !home.get().equals(target)) {
				if (villager.isSleeping()) {
					villager.stopSleeping();
				}
				villager.releasePoi(MemoryModuleType.HOME); // the bed at the inn is free again (one gone has nothing to let go)
				villager.getBrain().setMemory(MemoryModuleType.HOME, target);
			}
			r.bedHome = true;
			r.stay = null;
		}
		if (r.jobAt != null && !r.jobHome) {
			GlobalPos target = GlobalPos.of(level.dimension(), r.jobAt);
			Optional<GlobalPos> job = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
			if (job.isPresent() && !job.get().equals(target)) {
				Stations.releaseOld(level, villager, job.get()); // one they took meanwhile (the old one is gone: nothing to let go)
			}
			Stations.forgetWalkedTo(villager, target);
			villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, target);
			JobSiteTickets.hold(level, villager);
			if (villager.getVillagerXp() == 0) {
				villager.setVillagerXp(1);
			}
			r.jobHome = true;
		}
	}

	// ---- the move is over

	/**
	 * {@code site}'s move is over (moved, or put back): a resident whose bed or workstation stands where the building is
	 * now but wasn't put up by the Mover (a move called off before it came down) is given it too. Returns the residents not
	 * yet home (not loaded): the homecomings {@link MoveSites} keeps.
	 */
	static List<Resident> arrive(ServerLevel level, MoveSite site) {
		List<Resident> waiting = new java.util.ArrayList<>();
		for (Resident r : site.residents()) {
			Villager villager = level.getEntity(r.id) instanceof Villager v ? v : null;
			if (r.bed != null && r.bedAt == null) {
				BlockPos at = site.at(r.bed);
				if (reserve(level, at) || remembers(level, villager, MemoryModuleType.HOME, at)) {
					r.bedAt = at;
				}
			}
			if (r.job != null && r.jobAt == null) {
				BlockPos at = site.at(r.job);
				if (reserve(level, at) || remembers(level, villager, MemoryModuleType.JOB_SITE, at)) {
					r.jobAt = at;
				}
			}
			if (villager != null) {
				settle(level, r, villager);
			}
			if (!r.home()) {
				r.until = level.getGameTime() + KEEP_TICKS;
				waiting.add(r);
			}
		}
		return waiting;
	}

	/**
	 * {@code site}'s move is over: its claims that stand again wait for their owners, each with its old spot and kept for
	 * {@link #KEEP_TICKS} (B1154). A claim not standing (a move called off before it came down) is dropped: its owner
	 * still has it where it was.
	 */
	static List<Claim> forwards(ServerLevel level, MoveSite site) {
		List<Claim> out = new java.util.ArrayList<>();
		for (Claim c : site.claims()) {
			if (c.at != null) {
				c.old = site.oldMin().offset(c.rel);
				c.until = level.getGameTime() + KEEP_TICKS;
				out.add(c);
			}
		}
		return out;
	}

	/**
	 * {@code villager}, seen after a move, remembers {@code c}'s old spot as their home or job site: it's the moved block
	 * now (held for them since it went up). True if the claim is used up: it was theirs, given or not (its block broken
	 * meanwhile: their memory is left for vanilla to forget, as for any bed gone).
	 */
	static boolean forward(ServerLevel level, Claim c, Villager villager) {
		if (c.old == null || c.at == null) {
			return true;
		}
		MemoryModuleType<GlobalPos> memory = c.bed ? MemoryModuleType.HOME : MemoryModuleType.JOB_SITE;
		if (!villager.getBrain().getMemory(memory).filter(g -> g.equals(GlobalPos.of(level.dimension(), c.old))).isPresent()) {
			return false;
		}
		if (c.old.equals(c.at)) {
			return true; // a move called off: theirs where it was, held already
		}
		if (poiAt(level, c.old).isPresent()) {
			return false; // a bed or workstation stands at the old spot again: what they remember is that one
		}
		if (!standing(level, c)) {
			return true;
		}
		PoiManager poi = level.getPoiManager();
		if (poi.getFreeTickets(c.at) > 0 && !reserve(level, c.at)) {
			return true;
		}
		GlobalPos target = GlobalPos.of(level.dimension(), c.at);
		if (c.bed) {
			if (villager.isSleeping()) {
				villager.stopSleeping();
			}
			villager.getBrain().setMemory(MemoryModuleType.HOME, target); // (their old bed is gone: nothing to let go)
		} else {
			Stations.forgetWalkedTo(villager, target);
			villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, target);
			JobSiteTickets.hold(level, villager);
			if (villager.getVillagerXp() == 0) {
				villager.setVillagerXp(1);
			}
		}
		return true;
	}

	/** Whether {@code c}'s block still stands where it's held: a bed or a workstation, as it was, with its point of interest. */
	static boolean standing(ServerLevel level, Claim c) {
		return c.at != null && level.getPoiManager().getType(c.at).isPresent()
			&& poiAt(level, c.at).filter(t -> c.bed ? t.is(PoiTypes.HOME) : claimable(t) && !t.is(PoiTypes.HOME)).isPresent();
	}

	/**
	 * The point of interest at {@code pos}: by the block there if its chunk is loaded (the level files a block's change
	 * after the tick's work, B132, so a bed broken this tick is still filed), else as filed.
	 */
	private static Optional<Holder<PoiType>> poiAt(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) ? PoiTypes.forState(level.getBlockState(pos)) : level.getPoiManager().getType(pos);
	}

	/**
	 * {@code c} is given up (nobody came back for it in time, or there are too many): its hold is let go so another
	 * villager can have it. One whose block was broken has nothing to let go.
	 */
	static void giveUp(ServerLevel level, Claim c) {
		if (standing(level, c) && level.getPoiManager().getFreeTickets(c.at) == 0) {
			level.getPoiManager().release(c.at);
		}
	}

	/**
	 * {@code r} isn't coming back for what's held for them (not back within {@link #KEEP_TICKS}, or gone: dead, or turned
	 * into something else; B1201): what of theirs stands again and isn't theirs in their memories yet is let go, as a
	 * claim nobody came back for is, so another villager can have it.
	 */
	static void giveUp(ServerLevel level, Resident r) {
		if (!r.bedHome) {
			letGo(level, r.bedAt);
		}
		if (!r.jobHome) {
			letGo(level, r.jobAt);
		}
		r.bedAt = null;
		r.jobAt = null;
	}

	/**
	 * The player gave {@code r} another bed ({@code bed}) or another job, or none ({@code job}), at the Village Hall while
	 * their building was on the move or before they were home (B1194): that choice stands. The moved bed or workstation
	 * isn't theirs any more: it isn't held for them when it goes up, and what was held already is let go (one their memory
	 * had was let go by the choice itself).
	 */
	static void chosen(ServerLevel level, Resident r, boolean bed, boolean job) {
		if (bed && r.bed != null) {
			if (!r.bedHome) {
				letGo(level, r.bedAt);
			}
			r.bed = null;
			r.bedAt = null;
			r.bedHome = false;
			r.stay = null; // (their bed at the inn was let go as they moved into the new one)
		}
		if (job && r.job != null) {
			if (!r.jobHome) {
				letGo(level, r.jobAt);
			}
			r.job = null;
			r.jobAt = null;
			r.jobHome = false;
		}
	}

	/** Lets go of the one place held at {@code pos} (a bed or workstation the Mover put up), if it stands and is held. */
	private static void letGo(ServerLevel level, @Nullable BlockPos pos) {
		if (pos == null) {
			return;
		}
		PoiManager poi = level.getPoiManager();
		if (poi.getType(pos).isPresent() && poiAt(level, pos).isPresent() && poi.getFreeTickets(pos) == 0) {
			poi.release(pos);
		}
	}

	private static boolean remembers(ServerLevel level, @Nullable Villager villager, MemoryModuleType<GlobalPos> memory, BlockPos at) {
		return villager != null && villager.getBrain().getMemory(memory).filter(g -> g.equals(GlobalPos.of(level.dimension(), at))).isPresent()
			&& level.getPoiManager().getType(at).isPresent();
	}

	// ---- each villager, once a second

	/**
	 * Once a second (mixin/VillagerMixin): a resident of a building on the move, or of one moved while they were away,
	 * gets what's held for them; one still without a bed for the move looks for one at the inn every ten seconds.
	 */
	public static void tick(Villager villager) {
		if (villager.tickCount % 20 != 13 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		MoveSites.get(level).residentTick(level, villager);
	}

	/** {@link #tick} for a resident of a site still under way. */
	static void check(ServerLevel level, MoveSite site, Resident r, Villager villager) {
		if (!r.home()) {
			settle(level, r, villager);
		}
		if (r.bed != null && r.bedAt == null && r.stay == null && villager.tickCount % 200 == 13 && site.stage() != MoveSite.Stage.DONE) {
			shelter(level, site, r, villager);
		}
	}

	private MoveResidents() {
	}
}
