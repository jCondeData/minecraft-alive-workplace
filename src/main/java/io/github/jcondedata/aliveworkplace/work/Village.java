package io.github.jcondedata.aliveworkplace.work;

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
			|| job == ModVillagers.BALL_SMITH || job == ModVillagers.PORTER || job == ModVillagers.CHEF || job == VillagerProfession.ARMORER
			|| job == ModVillagers.BEEKEEPER || job == ModVillagers.FLORIST || job == ModVillagers.RANCHER || job == ModVillagers.UNDERTAKER || job == ModVillagers.SIFTER || job == ModVillagers.NETHERWORKER
			|| job == VillagerProfession.TOOLSMITH || job == VillagerProfession.WEAPONSMITH || job == VillagerProfession.FLETCHER
			|| job == VillagerProfession.SHEPHERD || job == VillagerProfession.BUTCHER || job == VillagerProfession.CARTOGRAPHER
			|| Fields.isFarmer(villager) && Fields.hasField(villager)
			|| Fishers.isFisherman(villager) && Fishers.isHired(villager);
	}

	/** Whether {@code taker} may help themselves to {@code giver}'s chests. */
	public static boolean sharesWith(ServerLevel level, Villager taker, Villager giver) {
		return sameSide(level, taker.getAttached(ModAttachments.BUILDER_EMPLOYER), giver.getAttached(ModAttachments.BUILDER_EMPLOYER));
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

	/** The workers taking part whose workstation is within {@link #RADIUS} of {@code pos} and who share with {@code boss}'s. */
	public static List<Villager> workersNear(ServerLevel level, BlockPos pos, @Nullable Employer boss) {
		List<Villager> out = new ArrayList<>();
		if (RADIUS <= 0) {
			return out;
		}
		double radiusSqr = (double) RADIUS * RADIUS;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(RADIUS + 16), v -> v.isAlive() && takesPart(v))) {
			BlockPos theirs = Builders.benchPos(v).orElse(null);
			if (theirs != null && theirs.distSqr(pos) <= radiusSqr && sameSide(level, boss, v.getAttached(ModAttachments.BUILDER_EMPLOYER))) {
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
			for (Villager other : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(RADIUS + 16),
				v -> v != villager && v.isAlive() && takesPart(v))) {
				BlockPos theirs = Builders.benchPos(other).orElse(null);
				if (theirs != null && !theirs.equals(station) && theirs.distSqr(station) <= radiusSqr && sharesWith(level, villager, other)
					&& seen.add(theirs)) {
					out.add(new Mate(theirs.immutable(), other.getVillagerData().getProfession()));
				}
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
		Set<BlockPos> seen = new HashSet<>(SupplyContainers.find(level, station, exclude));
		List<Stash> out = new ArrayList<>();
		for (Mate mate : mates(level, villager, station)) {
			List<BlockPos> chests = new ArrayList<>();
			for (BlockPos chest : SupplyContainers.find(level, mate.station(), exclude)) {
				if (seen.add(chest)) {
					chests.add(chest);
				}
			}
			if (!chests.isEmpty()) {
				out.add(new Stash(mate.station(), mate.job(), chests));
			}
		}
		return out;
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
