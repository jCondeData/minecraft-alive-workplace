package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Roads between villages (ROADMAP 27.17). For each caravan route ({@link Caravans}), either way, the village plans its
 * half of a road to the other village: a street ({@link CityPlan.Road#STREET}) from the node of its own roads nearest
 * the other hall (or from the hall, with none) towards the point halfway between the halls, in the style of the zone it
 * starts from, up to {@link #REACH} blocks or halfway, whichever is less. When the two halves would end within
 * {@link #JOIN} blocks of each other, both go on to the halfway point, so they meet there. The half is a road on the
 * village's plan ({@link CityPlan.Road#toward}) found and built as the village's other roads are ({@link Roads}), but its
 * way is looked for only where the world is loaded: no chunk is loaded for it, and the search waits until it is.
 *
 * <p>A half that stops short of halfway ends at a milestone ({@link #milestone}): a stone post with a lantern and a sign
 * naming the other village and how far it is. Each half's end and state are kept on the caravans' list
 * ({@link Caravans.Half}), so a village knows the other's half even where the world isn't loaded; on a finished road
 * caravans arrive in three quarters of the time, and both chronicles note it. Config {@code caravanRoads},
 * {@code caravanRoadReach}.
 */
public final class CaravanRoads {
	/** Config {@code caravanRoads}: off, villages build no roads to each other. */
	public static boolean ENABLED = true;
	/** Config {@code caravanRoadReach}: the longest half a village builds. */
	public static int REACH = 256;
	/** Two halves that would end this close are carried on to meet. */
	public static final int JOIN = 32;
	/** A half's points on the plan are this far apart: its way is found one stretch at a time, each where the world is loaded. */
	public static final int WAYPOINT = 16;
	/** How far round a stretch the world must be loaded before its way is looked for (the search's reach and its look over gaps). */
	public static final int LOADED_MARGIN = 48;
	/** How often (ticks) a hall looks at its routes' roads. */
	static final int CHECK_EVERY = 100;

	/** From the hall's tick, every {@link #CHECK_EVERY} ticks while its Steward is at work on roads. */
	static void tick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (ENABLED && Math.floorMod(level.getGameTime() + hall.hashCode(), CHECK_EVERY) == 0 && Roads.working(level, hall, entity.plan())) {
			plan(level, hall, entity);
		}
	}

	/**
	 * Plans the hall's half of a road to each village it has a route with (once its drawn roads have their ways found, as
	 * the half starts from one of them), takes off the plan the halves not yet started of routes that are gone, notes on
	 * the caravans' list where each half ends, and writes the chronicle's line for a road finished or ended at a milestone.
	 */
	public static void plan(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (!ENABLED) {
			return;
		}
		Caravans.Data data = Caravans.Data.get(level);
		Set<BlockPos> partners = data.partners(hall);
		CityPlan plan = entity.plan();
		for (int i = plan.roads().size() - 1; i >= 0; i--) {
			CityPlan.Road road = plan.roads().get(i);
			if (road.caravan() && !road.routed() && !partners.contains(hall.offset(road.toward().get()))) {
				CityPlan next = plan.removeRoad(i);
				if (next != null) {
					plan = next;
				}
			}
		}
		boolean drawnPending = plan.roads().stream().anyMatch(r -> !r.caravan() && !r.lane() && r.approved() && !r.routed());
		for (BlockPos other : partners) {
			if (data.village(other) == null) {
				continue;
			}
			CityPlan.Road road = roadTo(plan, hall, other);
			if (road == null) {
				if (!drawnPending) {
					CityPlan.Road half = half(level, hall, plan, other);
					CityPlan next = half == null ? null : plan.addRoad(half);
					if (next != null) {
						plan = next;
					}
				}
				continue;
			}
			if (road.routed() && !road.route().isEmpty()) {
				Caravans.Half was = data.half(hall, other);
				BlockPos end = road.route().get(road.route().size() - 1).offset(hall);
				data.setHalf(hall, other, new Caravans.Half(end, road.finished(), endsShort(hall, road), was != null && was.noted() && was.end().equals(end)));
			}
		}
		if (plan != entity.plan()) {
			entity.setPlan(plan);
		}
		for (BlockPos other : partners) {
			note(level, hall, other, data);
		}
	}

	/** The hall's half of the road towards {@code other} on its plan, or null. */
	@Nullable
	public static CityPlan.Road roadTo(CityPlan plan, BlockPos hall, BlockPos other) {
		BlockPos offset = other.subtract(hall);
		for (CityPlan.Road road : plan.roads()) {
			if (road.toward().filter(offset::equals).isPresent()) {
				return road;
			}
		}
		return null;
	}

	/** The point halfway between two halls (at the first's height). */
	static BlockPos halfway(BlockPos hall, BlockPos other) {
		return new BlockPos(Math.floorDiv(hall.getX() + other.getX(), 2), hall.getY(), Math.floorDiv(hall.getZ() + other.getZ(), 2));
	}

	private static double flat(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	/**
	 * The hall's half of a road to {@code other}: from the node of its roads nearest {@code other} (the hall, with none),
	 * straight towards halfway, as far as {@link #REACH} or halfway, whichever is less; all the way to halfway when the
	 * two halves would end within {@link #JOIN} blocks of each other. Points every {@link #WAYPOINT} blocks. Null when the
	 * start is halfway already.
	 */
	@Nullable
	static CityPlan.Road half(ServerLevel level, BlockPos hall, CityPlan plan, BlockPos other) {
		BlockPos start = hall;
		String style = "";
		double best = Double.MAX_VALUE;
		for (CityPlan.Road road : plan.roads()) {
			if (road.lane() || road.caravan() || !road.routed()) {
				continue;
			}
			for (BlockPos offset : road.route()) {
				BlockPos p = offset.offset(hall);
				double d = flat(p, other);
				if (d < best) {
					best = d;
					start = p;
					style = road.style();
				}
			}
		}
		Optional<CityPlan.Zone> zone = plan.zoneAt(hall, start);
		if (zone.isPresent() && !zone.get().style().isEmpty()) {
			style = zone.get().style(); // the style of the zone it starts from
		}
		BlockPos mid = halfway(hall, other);
		double toMid = flat(start, mid);
		if (toMid < 2) {
			return null;
		}
		// Each half goes REACH from its hall's side; the two would leave a gap of twice the shortfall in the middle.
		double shortfall = Math.max(0, flat(hall, mid) - REACH);
		double length = 2 * shortfall <= JOIN ? toMid : Math.min(REACH, toMid);
		List<BlockPos> points = new ArrayList<>();
		int steps = Math.max(1, (int) Math.ceil(length / WAYPOINT));
		for (int i = 0; i <= steps; i++) {
			double t = Math.min(length, i * (double) WAYPOINT) / toMid;
			int x = (int) Math.round(start.getX() + (mid.getX() - start.getX()) * t);
			int z = (int) Math.round(start.getZ() + (mid.getZ() - start.getZ()) * t);
			BlockPos p = new BlockPos(x - hall.getX(), 0, z - hall.getZ());
			if (points.isEmpty() || !points.get(points.size() - 1).equals(p)) {
				points.add(p);
			}
		}
		if (points.size() < 2 || points.size() > CityPlan.MAX_ROAD_POINTS) {
			return null;
		}
		return new CityPlan.Road(points, CityPlan.Road.STREET, style, true, List.of(), false, List.of(), false, 0, 0,
			Optional.of(other.subtract(hall)));
	}

	/** Whether a half's way ends short of halfway (more than half of {@link #JOIN} from it): it ends at a milestone. */
	public static boolean endsShort(BlockPos hall, CityPlan.Road road) {
		if (!road.caravan() || road.route().isEmpty()) {
			return false;
		}
		BlockPos end = road.route().get(road.route().size() - 1).offset(hall);
		return flat(end, halfway(hall, hall.offset(road.toward().get()))) > JOIN / 2.0;
	}

	/** The other village's name, as the caravans' list has it. */
	static Component nameOf(ServerLevel level, BlockPos other) {
		Caravans.Village v = Caravans.Data.get(level).village(other);
		return v == null ? Component.translatable("chronicle.aliveworkplace.someone") : v.name();
	}

	/**
	 * The milestone at the end of a half that stops short (27.17), for the segment's blueprint: beside the road's last
	 * nodes (or just past its end), a post of stone bricks and chiseled stone bricks, a stone brick wall and a lantern on
	 * top, and on the post's side towards the village a sign naming the other village and how far it is from there.
	 * {@code fits} says whether a 4-tall post fits at a spot; the sign's text goes in {@code data}.
	 */
	static void milestone(ServerLevel level, BlockPos hall, CityPlan.Road road, List<BlockPos> route, int halfWidth,
						  java.util.function.Predicate<BlockPos> fits, java.util.Map<BlockPos, BlockState> blocks, java.util.Map<BlockPos, CompoundTag> data) {
		int last = route.size() - 1;
		BlockPos post = null;
		Direction back = null;
		for (int j = last; j >= Math.max(1, last - 4) && post == null; j--) {
			Direction along = Roads.along(route, j);
			for (Direction side : new Direction[] {along.getClockWise(), along.getCounterClockWise()}) {
				BlockPos at = route.get(j).relative(side, halfWidth + 1);
				if (fits.test(at) && fits.test(at.relative(along.getOpposite()))) {
					post = at;
					back = along.getOpposite();
					break;
				}
			}
		}
		if (post == null) {
			Direction along = Roads.along(route, last);
			post = route.get(last).relative(along, 2); // past the end of the road
			back = along.getOpposite();
		}
		blocks.put(post, Blocks.STONE_BRICKS.defaultBlockState());
		blocks.put(post.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		blocks.put(post.above(2), Blocks.STONE_BRICK_WALL.defaultBlockState());
		blocks.put(post.above(3), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		BlockPos other = hall.offset(road.toward().get());
		int distance = (int) Math.round(flat(post, other));
		blocks.put(post.above().relative(back), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, back));
		data.put(post.above().relative(back), signText(nameOf(level, other), distance));
	}

	/** A milestone's sign: "To", the village's name, "N blocks"; waxed, so it isn't edited by a passer-by. */
	public static CompoundTag signText(Component name, int distance) {
		SignText text = new SignText()
			.setMessage(0, Component.translatable("sign.aliveworkplace.milestone.to"))
			.setMessage(1, name.copy())
			.setMessage(2, Component.translatable("sign.aliveworkplace.milestone.distance", distance));
		CompoundTag tag = new CompoundTag();
		tag.put("front_text", SignText.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow());
		tag.putBoolean("is_waxed", true);
		return tag;
	}

	/** A segment of a half was built: the half's end and state are noted on the caravans' list, and the chronicles told. */
	static void segmentBuilt(ServerLevel level, BlockPos hall, CityPlan.Road road) {
		if (!road.caravan() || road.route().isEmpty()) {
			return;
		}
		Caravans.Data data = Caravans.Data.get(level);
		BlockPos other = hall.offset(road.toward().get());
		Caravans.Half was = data.half(hall, other);
		BlockPos end = road.route().get(road.route().size() - 1).offset(hall);
		data.setHalf(hall, other, new Caravans.Half(end, road.finished(), endsShort(hall, road), was != null && was.noted()));
		note(level, hall, other, data);
	}

	/**
	 * The chronicles' lines for the road between {@code hall} and {@code other}: when both halves are built and meet,
	 * "The road to X is finished" in this village's chronicle (and the other's, if its hall is loaded; else on its own
	 * next round); when this village's half is built and stops at a milestone, that.
	 */
	static void note(ServerLevel level, BlockPos hall, BlockPos other, Caravans.Data data) {
		Caravans.Half ours = data.half(hall, other);
		if (ours == null || ours.noted() || !ours.finished()) {
			return;
		}
		if (data.roadFinished(hall, other)) {
			Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_road_finished", nameOf(level, other)));
			data.setHalf(hall, other, new Caravans.Half(ours.end(), true, ours.milestone(), true));
			Caravans.Half theirs = data.half(other, hall);
			if (theirs != null && !theirs.noted() && level.isLoaded(other) && level.getBlockEntity(other) instanceof VillageHallBlockEntity) {
				Chronicle.record(level, other, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_road_finished", nameOf(level, hall)));
				data.setHalf(other, hall, new Caravans.Half(theirs.end(), theirs.finished(), theirs.milestone(), true));
			}
		} else if (ours.milestone()) {
			Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_road_milestone",
				nameOf(level, other), (int) Math.round(flat(ours.end(), other))));
			data.setHalf(hall, other, new Caravans.Half(ours.end(), true, true, true));
		}
	}

	/** Whether the world is loaded round the stretch from {@code from} to {@code to} (feet or columns), {@link #LOADED_MARGIN} blocks beyond. */
	static boolean loaded(ServerLevel level, BlockPos from, BlockPos to) {
		int x0 = (Math.min(from.getX(), to.getX()) - LOADED_MARGIN) >> 4;
		int x1 = (Math.max(from.getX(), to.getX()) + LOADED_MARGIN) >> 4;
		int z0 = (Math.min(from.getZ(), to.getZ()) - LOADED_MARGIN) >> 4;
		int z1 = (Math.max(from.getZ(), to.getZ()) + LOADED_MARGIN) >> 4;
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				if (!level.hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		return true;
	}

	private CaravanRoads() {
	}
}
