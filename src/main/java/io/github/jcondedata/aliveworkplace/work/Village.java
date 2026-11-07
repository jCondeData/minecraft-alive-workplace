package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Workers near each other work as one village: one short of something takes it from another worker's chests — the
 * builder's stone from the miner's, the farmer's hoe from the builder's. A village is simply workers whose
 * workstations are within {@link #RADIUS} blocks of each other: no building, bell or structure is needed, so villages
 * from any pack work, and so does a player's base. Only workers who answer to the same people share: those hired by the
 * same player (or a friend of theirs, see {@link Friends}), and village workers nobody has hired with each other.
 * What a worker makes still goes into its own chests; the others come and get it.
 */
public final class Village {
	/** Workstations this close together are one village. */
	public static int RADIUS = 48;
	private static final int RECHECK_TICKS = 100;

	/** A worker's chests: the ones within the supply radius of their workstation. */
	public record Stash(BlockPos station, VillagerProfession job, List<BlockPos> chests) {
	}

	/** Something found in a village-mate's stash, and the chest it's in. */
	public record Find(Stash stash, BlockPos chest) {
	}

	/** A village-mate: their workstation and their job. */
	public record Mate(BlockPos station, VillagerProfession job) {
	}

	private record Cached(long until, BlockPos station, List<Mate> mates) {
	}

	private static final Map<Villager, Cached> CACHE = new WeakHashMap<>();

	/** Whether this villager's job takes part: the ones who gather, make and build. */
	public static boolean takesPart(Villager villager) {
		if (villager.isBaby()) {
			return false;
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		return job == ModVillagers.BUILDER || job == ModVillagers.MINER || job == ModVillagers.LUMBERJACK || job == ModVillagers.ORCHARD_KEEPER
			|| job == ModVillagers.BALL_SMITH || job == ModVillagers.PORTER || job == ModVillagers.CHEF || job == VillagerProfession.ARMORER || job == ModVillagers.JEWELLER
			|| job == ModVillagers.BEEKEEPER || job == ModVillagers.FLORIST || job == ModVillagers.RANCHER || job == ModVillagers.UNDERTAKER || job == ModVillagers.SIFTER || job == ModVillagers.NETHERWORKER || job == ModVillagers.COMPOSTER
			|| job == ModVillagers.BERRY_BREEDER || job == ModVillagers.CAMP_COOK || job == ModVillagers.HABITAT_KEEPER || job == ModVillagers.GEM_GROWER || job == ModVillagers.VINTNER || job == ModVillagers.TAILOR || job == ModVillagers.PRINTER
			|| job == VillagerProfession.TOOLSMITH || job == VillagerProfession.WEAPONSMITH || job == VillagerProfession.FLETCHER
			|| job == VillagerProfession.SHEPHERD || job == VillagerProfession.BUTCHER || job == VillagerProfession.CARTOGRAPHER
			|| Fields.isFarmer(villager) && Fields.hasField(villager)
			|| Fishers.isFisherman(villager) && Fishers.isHired(villager);
	}

	/** Whether {@code taker} may help themselves to {@code giver}'s chests. */
	public static boolean sharesWith(ServerLevel level, Villager taker, Villager giver) {
		return sameSide(level, ModAttachments.BUILDER_EMPLOYER.get(taker), ModAttachments.BUILDER_EMPLOYER.get(giver));
	}

	/**
	 * Whether workers answering to {@code takerBoss} may take from workers answering to {@code giverBoss} (null: a village
	 * worker nobody hired): the same player or a friend of the giver's, or both the village's.
	 */
	public static boolean sameSide(ServerLevel level, @Nullable Employer takerBoss, @Nullable Employer giverBoss) {
		if (takerBoss == null || giverBoss == null) {
			return takerBoss == null && giverBoss == null;
		}
		return Friends.get(level.getServer()).mayDirect(giverBoss.id(), takerBoss.id());
	}

	/**
	 * B290: whether {@code villager}, working at {@code station}, may take from the Storehouse at {@code storehouse} owned
	 * by {@code owner} (null: nobody placed it). The Storehouse is the village's one store, so beyond {@link #sameSide}
	 * a village's own side counts as its Village Hall owner's: an unowned Storehouse in a village whose hall a player
	 * owns is that player's, and so is an unhired builder working on that player's build or in that player's village.
	 * Before, a hired builder never counted a village's Storehouse and an unhired one never counted a player's, so the
	 * Storehouse fed a builder only while his own table stood by it (its chests were then his own).
	 */
	public static boolean sharesStorehouse(ServerLevel level, Villager villager, BlockPos station, BlockPos storehouse, @Nullable Employer owner) {
		Employer boss = ModAttachments.BUILDER_EMPLOYER.get(villager);
		if (sameSide(level, boss, owner)) {
			return true;
		}
		Employer store = owner != null ? owner : hallOwner(level, storehouse);
		if (store == null) {
			return false; // a village's own store, where no player owns the village: its own workers' only
		}
		if (boss != null) {
			return sameSide(level, boss, store);
		}
		BuildSite site = villager.getVillagerData().getProfession() == ModVillagers.BUILDER ? Builders.activeSite(level, villager) : null;
		if (site != null && site.owner() != null && !site.ownerName().isEmpty() && sameSide(level, new Employer(site.owner(), site.ownerName()), store)) {
			return true;
		}
		Employer hall = hallOwner(level, station);
		if (hall != null) {
			return sameSide(level, hall, store);
		}
		// B297: a player's Storehouse in a village no player owns the hall of is that village's store, so the village's own
		// builder working for the village (a Steward's build, a Builder's Workshop's, or nothing yet) counts it too. Before,
		// he never did: his stock was only what stood round his own table, until that table broke and he took the one
		// by the Storehouse. A builder on another player's build still doesn't (that player's Storehouse is theirs).
		return villager.getVillagerData().getProfession() == ModVillagers.BUILDER && (site == null || site.owner() == null || site.ownerName().isEmpty());
	}

	/** The player who owns the Village Hall whose village {@code pos} is in, or null (no hall, or nobody's). */
	@Nullable
	static Employer hallOwner(ServerLevel level, BlockPos pos) {
		return io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(level, pos)
			.filter(level::isLoaded)
			.map(hall -> level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity && entity.owner() != null
				? new Employer(entity.owner(), entity.ownerName()) : null)
			.orElse(null);
	}

	/** The workers taking part whose workstation is within {@link #RADIUS} of {@code pos} and who share with {@code boss}'s. */
	public static List<Villager> workersNear(ServerLevel level, BlockPos pos, @Nullable Employer boss) {
		List<Villager> out = new ArrayList<>();
		if (RADIUS <= 0) {
			return out;
		}
		double radiusSqr = (double) RADIUS * RADIUS;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(RADIUS + 16), v -> v.isAlive()
			&& (takesPart(v) || io.github.jcondedata.aliveworkplace.legend.StrangeMoods.claiming(v)))) { // a strange mood's wants are on the board (29.10)
			BlockPos theirs = Builders.benchPos(v).orElse(null);
			if (theirs != null && theirs.distSqr(pos) <= radiusSqr && sameSide(level, boss, ModAttachments.BUILDER_EMPLOYER.get(v))) {
				out.add(v);
			}
		}
		out.sort(Comparator.comparingDouble(v -> Builders.benchPos(v).orElse(pos).distSqr(pos)));
		return out;
	}

	/** The workers {@code villager} may take from, nearest first (not counting workers at the same workstation). */
	public static List<Mate> mates(ServerLevel level, Villager villager, BlockPos station) {
		long now = level.getGameTime();
		synchronized (CACHE) {
			Cached cached = CACHE.get(villager);
			if (cached != null && cached.until() > now && cached.station().equals(station)) {
				return cached.mates();
			}
		}
		List<Mate> out = new ArrayList<>();
		if (takesPart(villager)) {
			double radiusSqr = (double) RADIUS * RADIUS;
			Set<BlockPos> seen = new HashSet<>();
			// Their workstation is within RADIUS, but they may be out working up to RADIUS beyond it (a porter fetching
			// from the far side of the village): look that far, or the storehouse drops out of the list while it's away.
			for (Villager other : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(2 * RADIUS + 16),
				v -> v != villager && v.isAlive() && takesPart(v))) {
				BlockPos theirs = Builders.benchPos(other).orElse(null);
				if (theirs != null && !theirs.equals(station) && theirs.distSqr(station) <= radiusSqr && sharesWith(level, villager, other)
					&& seen.add(theirs)) {
					out.add(new Mate(theirs.immutable(), other.getVillagerData().getProfession()));
				}
			}
			// B226: a Storehouse is the village's store whether or not its porter is about (asleep at home in a chunk
			// that isn't loaded, beyond the search, or the job lost): found by its block, and shared as its owner's
			// (the porter there answers to them), so a builder with no chests by its bench still builds from it
			for (BlockPos storehouse : level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, station, RADIUS,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				if (storehouse.equals(station) || !seen.add(storehouse.immutable()) || !level.isLoaded(storehouse)
					|| !(level.getBlockEntity(storehouse) instanceof io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity store)
					|| !sharesStorehouse(level, villager, station, storehouse, store.owner())) {
					continue;
				}
				out.add(new Mate(storehouse.immutable(), ModVillagers.PORTER));
			}
			out.sort(Comparator.comparingDouble(m -> m.station().distSqr(station)));
		}
		List<Mate> mates = List.copyOf(out);
		synchronized (CACHE) {
			CACHE.put(villager, new Cached(now + RECHECK_TICKS, station.immutable(), mates));
		}
		return mates;
	}

	/** Forget what was found (tests; a worker just moved in or left). */
	public static void forget(Villager villager) {
		synchronized (CACHE) {
			CACHE.remove(villager);
		}
	}

	/**
	 * The village-mates' stashes {@code villager} may take from, nearest first — without the chests that are theirs
	 * too (near their own workstation) or inside {@code exclude} (a build site).
	 */
	public static List<Stash> stashes(ServerLevel level, Villager villager, BlockPos station, @Nullable BoundingBox exclude) {
		Set<BlockPos> own = new HashSet<>();
		for (BlockPos chest : SupplyContainers.find(level, station, exclude)) {
			own.add(chest);
			own.addAll(io.github.jcondedata.aliveworkplace.store.StorageNetworks.members(level, chest));
		}
		return stashesOf(level, mates(level, villager, station), own, exclude);
	}

	/**
	 * B627: the stashes of every worker taking part whose workstation is within {@link #RADIUS} of {@code station} (a
	 * Hauler Golem's Storehouse, or where it stands) and who answers to {@code side}'s people, nearest first. Unlike
	 * {@link #stashes}, nobody is left out: the list isn't seen from one worker's side, so no worker's own chest drops
	 * out of it. Every Storehouse about, the one at {@code station} too, is listed as well, whoever owns it, as a
	 * porter's stash ({@link ModVillagers#PORTER}), so its chests are never counted as a worker's.
	 */
	public static List<Stash> workerStashes(ServerLevel level, BlockPos station, @Nullable Employer side, @Nullable BoundingBox exclude) {
		List<Mate> out = new ArrayList<>();
		if (RADIUS > 0) {
			double radiusSqr = (double) RADIUS * RADIUS;
			Set<BlockPos> seen = new HashSet<>();
			for (Villager other : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(2 * RADIUS + 16),
				v -> v.isAlive() && takesPart(v))) {
				BlockPos theirs = Builders.benchPos(other).orElse(null);
				if (theirs != null && !theirs.equals(station) && theirs.distSqr(station) <= radiusSqr
					&& sameSide(level, side, ModAttachments.BUILDER_EMPLOYER.get(other)) && seen.add(theirs)) {
					out.add(new Mate(theirs.immutable(), other.getVillagerData().getProfession()));
				}
			}
			for (BlockPos storehouse : level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, station, RADIUS,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				if (seen.add(storehouse.immutable()) && level.isLoaded(storehouse)) {
					out.add(new Mate(storehouse.immutable(), ModVillagers.PORTER));
				}
			}
			out.sort(Comparator.comparingDouble(m -> m.station().distSqr(station)));
		}
		return stashesOf(level, out, new HashSet<>(), exclude);
	}

	/** The stashes of {@code mates} (nearest first), without the chests in {@code seen} or inside {@code exclude}. */
	private static List<Stash> stashesOf(ServerLevel level, List<Mate> mates, Set<BlockPos> seen, @Nullable BoundingBox exclude) {
		// B297/B298: a Storehouse's chests are the Storehouse's, even when another worker's bench stands nearer by them (the
		// Storehouse's own Blueprint Table, one block from its chests, with a builder at it): listed nearest-first they
		// were that builder's, kept back for his builds and never the village's store, so a builder at the Builder's
		// Workshop's table counted none of them. The Storehouses take their chests first; the list stays nearest first.
		Map<Mate, List<BlockPos>> lists = new java.util.HashMap<>();
		for (int pass = 0; pass < 2; pass++) {
			for (Mate mate : mates) {
				if ((mate.job() == ModVillagers.PORTER) == (pass == 0)) {
					lists.put(mate, claim(level, mate, exclude, seen));
				}
			}
		}
		List<Stash> out = new ArrayList<>();
		for (Mate mate : mates) {
			List<BlockPos> chests = lists.getOrDefault(mate, List.of());
			if (!chests.isEmpty()) {
				out.add(new Stash(mate.station(), mate.job(), chests));
			}
		}
		return out;
	}

	/** The chests round {@code mate}'s workstation not already in {@code seen}, which now holds them too. */
	private static List<BlockPos> claim(ServerLevel level, Mate mate, @Nullable BoundingBox exclude, Set<BlockPos> seen) {
		List<BlockPos> chests = new ArrayList<>();
		for (BlockPos chest : SupplyContainers.find(level, mate.station(), exclude)) {
			// 37.10: a Storehouse's network head stands for the chests it joins; listed in another stash already, those
			// chests are taken one by one instead, so nothing counts twice
			Set<BlockPos> joined = io.github.jcondedata.aliveworkplace.store.StorageNetworks.members(level, chest);
			if (joined.isEmpty() || joined.stream().noneMatch(seen::contains)) {
				if (seen.add(chest)) {
					chests.add(chest);
					seen.addAll(joined);
				}
			} else {
				for (BlockPos member : joined) {
					if (seen.add(member)) {
						chests.add(member);
					}
				}
			}
		}
		return chests;
	}

	/** B297: why a Storehouse isn't counted: beyond the village's reach, someone else's, or no chest round it to read. */
	public enum StoreMiss {
		TOO_FAR, NOT_SHARED, NO_CHESTS
	}

	/** The nearest Storehouse {@code villager} doesn't count, why, how far it is (whole blocks, rounded up) and its owner (null: the village's). */
	public record UnusedStore(BlockPos storehouse, StoreMiss why, int distance, @Nullable Employer owner) {
	}

	/**
	 * B297: the nearest Storehouse within twice the village's reach of {@code station} that {@code villager} doesn't count
	 * (for the status, so a player can see why his builder says he needs what the Storehouse holds), or null when he
	 * counts one (its chests are his own or a stash of his) or there is none about.
	 */
	@Nullable
	public static UnusedStore unusedStorehouse(ServerLevel level, Villager villager, BlockPos station, @Nullable BoundingBox exclude) {
		if (RADIUS <= 0) {
			return null;
		}
		List<BlockPos> found = new ArrayList<>(level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, station, 2 * RADIUS,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList());
		found.sort(Comparator.comparingDouble(p -> p.distSqr(station)));
		UnusedStore nearest = null;
		double radiusSqr = (double) RADIUS * RADIUS;
		for (BlockPos storehouse : found) {
			// B355: rounded up, so a Storehouse past the reach never reads as at it (20.4 blocks: 21, in a 20-block village)
			int distance = (int) Math.ceil(Math.sqrt(storehouse.distSqr(station)));
			UnusedStore miss;
			if (storehouse.distSqr(station) > radiusSqr) {
				miss = new UnusedStore(storehouse.immutable(), StoreMiss.TOO_FAR, distance, null);
			} else if (!level.isLoaded(storehouse) || !(level.getBlockEntity(storehouse) instanceof io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity store)) {
				miss = new UnusedStore(storehouse.immutable(), StoreMiss.NO_CHESTS, distance, null);
			} else if (!sharesStorehouse(level, villager, station, storehouse, store.owner())) {
				Employer owner = store.owner() != null ? store.owner() : hallOwner(level, storehouse);
				miss = new UnusedStore(storehouse.immutable(), StoreMiss.NOT_SHARED, distance, owner);
			} else if (SupplyContainers.find(level, storehouse, exclude).isEmpty()) {
				miss = new UnusedStore(storehouse.immutable(), StoreMiss.NO_CHESTS, distance, store.owner());
			} else {
				return null; // counted: as his own chests, or as the village's store
			}
			if (nearest == null) {
				nearest = miss;
			}
		}
		return nearest;
	}

	/** The nearest village-mate's stash holding something that passes {@code test}; null if nobody has any. */
	@Nullable
	public static Find find(ServerLevel level, Villager villager, BlockPos station, @Nullable BoundingBox exclude, Predicate<ItemStack> test) {
		for (Stash stash : stashes(level, villager, station, exclude)) {
			BlockPos chest = SupplyContainers.firstMatching(level, stash.chests(), test);
			if (chest != null) {
				return new Find(stash, chest);
			}
		}
		return null;
	}

	/** Every chest {@code villager} can take from: their own first, then their village-mates'. */
	public static List<BlockPos> allChests(ServerLevel level, Villager villager, BlockPos station, @Nullable BoundingBox exclude) {
		List<BlockPos> out = new ArrayList<>(SupplyContainers.find(level, station, exclude));
		for (Stash stash : stashes(level, villager, station, exclude)) {
			out.addAll(stash.chests());
		}
		return out;
	}

	private Village() {
	}
}
