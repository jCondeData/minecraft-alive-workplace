package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.wood.Trees;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The one check everything the Steward builds passes (27.19), and what keeps his sites careful once they run:
 * <ul>
 * <li>{@link #allowed}: a box only inside his own village (no nearer hall), never in a Keep Clear zone, never in a
 *     protected village whose owner isn't his hall's owner, and never through a section the {@link PlayerBuilt} ledger
 *     marked unless the owner approved it by hand;</li>
 * <li>{@link #natural}: his sites clear only natural blocks, so a block a player put in the way stays (its step is
 *     skipped and the desk says so);</li>
 * <li>{@link #shoppingList}: the materials all his waiting builds miss, the {@link #LIST_SIZE} most needed;</li>
 * <li>{@link #paused}: no new build is proposed while {@link #PAUSE_SITES} of his builds have waited a whole day.</li>
 * </ul>
 */
public final class StewardSafety {
	public static final String KEEP_CLEAR = "keep_clear";
	/** How many materials the shopping list shows. */
	public static final int LIST_SIZE = 8;
	/** Waiting builds that pause new proposals. */
	public static final int PAUSE_SITES = 2;
	/** How long a build waits before it counts towards the pause: a whole day. */
	public static final long PAUSE_TICKS = 24000L;
	/** How far apart the points checked along a box are. */
	private static final int STEP = 4;

	/** Why a box is refused, or empty when the Steward may build there. */
	public enum Refusal {
		KEEP_CLEAR, OTHER_VILLAGE, PROTECTED, PLAYER_BUILT
	}

	/** Keeps the ledger: a block a player breaks (placing is caught where the block item places it). */
	public static void init() {
		io.github.jcondedata.aliveworkplace.platform.Platform.get().allowBreakBlock((level, player, pos, state) -> {
			if (level instanceof ServerLevel server) {
				PlayerBuilt.changed(server, pos);
			}
			return true;
		});
	}

	/** {@link #check} with no refusal. */
	public static boolean allowed(ServerLevel level, BlockPos hall, BoundingBox box, boolean byHand) {
		return check(level, hall, box, byHand).isEmpty();
	}

	/**
	 * Whether the Steward of {@code hall} may build in {@code box}: checked at the corners, the middle and every
	 * {@link #STEP} blocks along the footprint. {@code byHand}: the owner approved this one himself, so the ledger of what
	 * players built doesn't hold it back (the rest still does).
	 */
	public static Optional<Refusal> check(ServerLevel level, BlockPos hall, BoundingBox box, boolean byHand) {
		CityPlan plan = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.plan() : CityPlan.EMPTY;
		UUID owner = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.owner() : null;
		int y = box.minY();
		for (BlockPos p : points(box, y)) {
			if (plan.zoneAt(hall, p).map(z -> KEEP_CLEAR.equals(z.kind())).orElse(false)) {
				return Optional.of(Refusal.KEEP_CLEAR);
			}
			// in that village: nearer its hall than his (where both protected areas overlap, the nearer hall's village)
			if (VillageProtection.foreignKeeper(level, owner, p).filter(h -> !h.equals(hall) && h.distSqr(p) < hall.distSqr(p)).isPresent()) {
				return Optional.of(Refusal.PROTECTED);
			}
		}
		for (BlockPos p : corners(box, y)) {
			Optional<BlockPos> nearest = VillageHalls.nearest(level, p);
			if (nearest.isPresent() && !nearest.get().equals(hall) && nearest.get().distSqr(p) < hall.distSqr(p)) {
				return Optional.of(Refusal.OTHER_VILLAGE);
			}
		}
		if (!byHand && PlayerBuilt.get(level).marked(box)) {
			return Optional.of(Refusal.PLAYER_BUILT);
		}
		return Optional.empty();
	}

	private static List<BlockPos> corners(BoundingBox box, int y) {
		return List.of(new BlockPos(box.minX(), y, box.minZ()), new BlockPos(box.maxX(), y, box.minZ()), new BlockPos(box.minX(), y, box.maxZ()),
			new BlockPos(box.maxX(), y, box.maxZ()), new BlockPos((box.minX() + box.maxX()) / 2, y, (box.minZ() + box.maxZ()) / 2));
	}

	private static List<BlockPos> points(BoundingBox box, int y) {
		List<BlockPos> out = new ArrayList<>(corners(box, y));
		for (int x = box.minX(); x <= box.maxX(); x += STEP) {
			for (int z = box.minZ(); z <= box.maxZ(); z += STEP) {
				out.add(new BlockPos(x, y, z));
			}
		}
		return out;
	}

	/** Natural growth and ground: what the Steward's sites clear without a second thought (logs too: trees on a plot). */
	public static boolean natural(BlockState state) {
		return Plots.clearable(state) || Plots.ground(state) || Trees.isLog(state);
	}

	/**
	 * Whether {@code state} at {@code pos} is a player's block the Steward's site must leave: not natural, in a section
	 * where a player built since the ledger began. (The village's own buildings, which an upgrade takes parts of, aren't.)
	 */
	public static boolean playersBlock(ServerLevel level, BlockPos pos, BlockState state) {
		return !natural(state) && PlayerBuilt.get(level).marked(pos);
	}

	// ---- materials ---------------------------------------------------------------------------------------------

	/** His builds waiting for materials right now. */
	public static List<BuildSite> waiting(ServerLevel level, BlockPos hall) {
		return StewardDesk.openSites(level, hall).stream()
			.filter(s -> s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !s.missing().isEmpty()).toList();
	}

	/** One list for all his waiting builds: what each misses, added up, the {@link #LIST_SIZE} most needed first. */
	public static List<Map.Entry<Item, Integer>> shoppingList(ServerLevel level, BlockPos hall) {
		Map<Item, Integer> total = new LinkedHashMap<>();
		for (BuildSite site : waiting(level, hall)) {
			site.missing().forEach((item, n) -> total.merge(item, n, Integer::sum));
		}
		return total.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.limit(LIST_SIZE).map(e -> Map.entry(e.getKey(), e.getValue())).toList();
	}

	/** The list as one line: "40 Glass, 12 Oak Planks". */
	public static Component describe(List<Map.Entry<Item, Integer>> list) {
		Component out = Component.empty();
		boolean first = true;
		for (Map.Entry<Item, Integer> e : list) {
			Component one = Component.translatable("screen.aliveworkplace.shopping.item", e.getValue(), e.getKey().getDescription());
			out = first ? one : Component.translatable("screen.aliveworkplace.shopping.and", out, one);
			first = false;
		}
		return out;
	}

	/** Notes when each open build began waiting for materials, and forgets it once he's back at work (every second). */
	public static void track(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		for (BuildSite site : StewardDesk.openSites(level, hall)) {
			if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				if (site.waitingSince() == BuildSite.NOT_WAITING) {
					site.setWaitingSince(now);
				}
			} else if (site.status() == BuildSite.Status.WORKING || site.status() == BuildSite.Status.FETCHING) {
				if (site.waitingSince() != BuildSite.NOT_WAITING) {
					site.setWaitingSince(BuildSite.NOT_WAITING);
				}
			}
		}
	}

	/** Whether new builds wait: {@link #PAUSE_SITES} of his builds have waited for materials a whole day. */
	public static boolean paused(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		return StewardDesk.openSites(level, hall).stream()
			.filter(s -> s.waitingSince() != BuildSite.NOT_WAITING && now - s.waitingSince() >= PAUSE_TICKS).count() >= PAUSE_SITES;
	}

	private StewardSafety() {
	}
}
