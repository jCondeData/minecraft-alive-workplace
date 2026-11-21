package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=rebuild_&lt;family&gt; (ROADMAP 49.1, tools/blueprints/REBUILD.md), from 36.23's HouseFurnishedScene but for any
 * blueprint: the family's first drawing (or REBUILD_BLUEPRINT, e.g. a candidate on its branch) as a builder with the
 * furniture mods would build it (FurnitureChoice.forNewBuild), filmed from outside on four sides (01-04) and then at
 * eye level once per room (10_room_1, 11_room_2...). Rooms are the furniture spots grouped by floor and nearness; each
 * room's camera stands on a free floor cell of that floor (air at the feet and the eye, floor under it), never in a
 * wall, never on or right behind a seat, and with a clear line of sight to the middle of the room's furniture; the
 * farthest such cell wins, so the shot takes in the room. A room with no such cell is reported, not filmed blind.
 * Passes when the blueprint is there, every furniture spot holds the piece chosen, and every room got its shot.
 */
final class RebuildScene {
	/** The families with a scene in tools/showcase/scenes.py (rebuild_&lt;family&gt;): each rebuild adds its own. Any other
	 *  family films too, by hand (SCENE=rebuild_&lt;family&gt;), but isn't in the nightly showcase. */
	static final List<String> FILMED = List.of("well", "house_t1_b", "well_house", "fountain", "lily_pond", "footbridge", "ranch",
		"park_bench", "sundial", "flower_planters", "fire_pit", "gazebo", "topiary_garden", "stag_statue", "starter_cottage", "stone_house",
		"townhouse", "artisans_house", "terrace", "masons_yard", "sawmill", "carpenters_workshop", "builders_workshop", "camp_kitchen", "smithy",
		"tinkers_workshop", "brass_foundry", "stone_wall", "wall_tower", "palisade_gate", "palisade", "butchers_shop", "winery", "farmstead",
		"library", "schoolhouse", "school", "watermill", "manor", "weavers_cottage", "post_office", "graveyard", "ferry_house", "clock_tower",
		"nether_gate", "waystone_plaza", "mega_shrine", "founder_statue", "fletchers_lodge", "tailors_shop", "print_shop",
		"glassblowers_workshop", "jewellers_workshop", "crafters_workshop", "workshop", "trail", "bandit_camp", "raiders_stockade",
		"bandit_fort", "caravan_wreck", "chapel", "market_cross", "market_corner", "market_square", "leaders_hall", "trade_hall",
		"starter_cottage_looks", "stone_house_looks", "townhouse_looks", "terrace_looks", "manor_looks", "weavers_cottage_looks", "house_t1_b_looks", "farmstead_looks", "ranch_looks", "clinic", "physicians_house", "healing_center", "barracks", "guard_house", "fire_station",
		"gatehouse", "lookout_tower", "royal_hall", "observatory", "lighthouse", "berry_farm",
		"apiary_garden", "greenhouse", "flower_shop", "trainers_house", "supply_shop", "market_stall", "compost_yard", "quarry",
		"sifting_shed", "crushing_mill", "windmill", "hanging_gardens", "great_wall", "train_station", "pump_house", "terminal_nook",
		"habitat_garden", "ball_workshop", "arena", "research_lab", "great_library", "stadium", "great_cathedral",
		"colossus", "pokemon_center", "pokemon_center_looks");
	/** The rebuilt Wonders (52.6) among them: filmed as their whole blueprint ({@code wonder/<id>}, every stage standing). */
	static final Set<String> WONDERS = Set.of("observatory", "lighthouse", "hanging_gardens", "great_wall", "great_library", "stadium",
		"great_cathedral", "colossus");
	/** SCENE=rebuild_&lt;family&gt;_looks films the family's biome looks side by side ({@link #placeLooks}). */
	static final String LOOKS_SUFFIX = "_looks";
	private static final BlockPos AT = new BlockPos(0, -60, 0);
	private static final int FIRST = 80;
	private static final int EACH = 50;
	private static final double EYE = 1.62;

	private record View(String name, Vec3 eye, Vec3 at, int fov) {
	}

	private final String family;
	private final ResourceLocation blueprint;
	private final List<View> views = Collections.synchronizedList(new ArrayList<>());
	private final List<String> wrong = Collections.synchronizedList(new ArrayList<>());
	private volatile boolean placed;
	private volatile String summary = "";
	private int tick;
	private int filmFrom = -1;

	RebuildScene(String family) {
		this.family = family;
		String override = System.getenv("REBUILD_BLUEPRINT");
		this.blueprint = override != null && !override.isBlank() ? AliveWorkplace.id(override)
			: WONDERS.contains(family) ? WonderBlueprints.idOf(AliveWorkplace.id(family)) : AliveWorkplace.id(family);
	}

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.bobView().set(false);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				place(level);
				placed = true;
			});
		}
		if (filmFrom < 0) {
			if (placed && tick >= FIRST) {
				filmFrom = tick;
			}
			return;
		}
		int t = tick - filmFrom;
		int n = views.size();
		if (t % EACH == 0 && t / EACH < n) {
			View view = views.get(t / EACH);
			mc.options.fov().set(view.fov());
			Vec3 base = Vec3.atLowerCornerOf(AT);
			server.execute(() -> {
				server.overworld().getEntitiesOfClass(Mob.class, new AABB(AT).inflate(40)).forEach(net.minecraft.world.entity.Entity::discard);
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), base.add(view.eye()), base.add(view.at()));
			});
		}
		if (t >= 40 && (t - 40) % EACH == 0 && (t - 40) / EACH < n) {
			ScreenshotHarness.shot(mc, views.get((t - 40) / EACH).name());
		}
		if (t == n * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty() ? summary : String.join("; ", wrong));
			mc.stop();
		}
	}

	private void place(ServerLevel level) {
		if (family.endsWith(LOOKS_SUFFIX)) {
			placeLooks(level, AliveWorkplace.id(family.substring(0, family.length() - LOOKS_SUFFIX.length())));
			return;
		}
		Blueprint base = BlueprintLibrary.get(level, blueprint).orElse(null);
		if (base == null) {
			wrong.add("no blueprint " + blueprint + " for the family " + family);
			return;
		}
		Vec3i size = base.size();
		for (BlockPos p : BlockPos.betweenClosed(AT.offset(-16, -1, -16), AT.offset(size.getX() + 16, size.getY() + 4, size.getZ() + 16))) {
			BlockState want = p.getY() == AT.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (level.getBlockState(p) != want) {
				level.setBlock(p, want, 2 | 16);
			}
		}
		FurnitureChoice choice = FurnitureChoice.forNewBuild(blueprint);
		for (Blueprint.Entry e : choice.apply(base, blueprint).blocks()) {
			level.setBlock(AT.offset(e.pos()), e.state(), 2 | 16);
		}
		Map<String, Integer> perMod = new TreeMap<>();
		Set<BlockPos> furniture = new HashSet<>();
		for (FurnitureChoice.Placed piece : choice.spots().values()) {
			perMod.merge(piece.mod(), 1, Integer::sum);
			for (Map.Entry<BlockPos, BlockState> cell : piece.cells().entrySet()) {
				BlockPos at = piece.pos().offset(cell.getKey());
				furniture.add(at);
				if (cell.getValue().isAir()) {
					continue;
				}
				BlockPos found = standing(level, at, cell.getValue());
				if (found == null) {
					BlockState there = level.getBlockState(AT.offset(at));
					wrong.add(piece.kind() + " at " + at.toShortString() + " is " + BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not "
						+ BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
				} else {
					furniture.add(found);
				}
			}
		}
		List<FurnitureSpot> spots = BlueprintFurniture.of(blueprint);
		for (FurnitureSpot s : spots) {
			furniture.addAll(s.positions());
		}
		if (!spots.isEmpty() && !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_chair"))) {
			wrong.add("the furniture mods aren't loaded: film this scene with -Pfurniture=true");
		}
		outside(size);
		Map<FurnitureSpot, Integer> floors = new java.util.HashMap<>();
		for (FurnitureSpot s : spots) {
			floors.put(s, floorOf(level, s, furniture, base.basement()));
		}
		List<List<FurnitureSpot>> rooms = rooms(spots, floors);
		int filmed = 0;
		for (int i = 0; i < rooms.size(); i++) {
			View view = roomView(level, rooms.get(i), floors, furniture, size, i);
			if (view == null) {
				wrong.add("room " + (i + 1) + " (" + rooms.get(i).size() + " pieces at y " + rooms.get(i).get(0).pos().getY()
					+ ", floor " + roomFloor(rooms.get(i), floors) + ") has no free floor cell with a clear view of its furniture");
			} else {
				views.add(view);
				filmed++;
			}
			AliveWorkplace.LOG.info("REBUILD_SCENE room {}: floor {}, pieces {}, camera {}", i + 1, roomFloor(rooms.get(i), floors),
				rooms.get(i).stream().map(s -> s.kind().id() + "@" + s.pos().toShortString()).toList(), view == null ? "none" : view.eye());
		}
		AliveWorkplace.LOG.info("REBUILD_SCENE {} ({}): {} spots, {} rooms, {} filmed, pieces per mod {}", family, blueprint, spots.size(),
			rooms.size(), filmed, perMod);
		summary = family + " (" + blueprint.getPath() + "): " + spots.size() + " furniture spots placed in " + rooms.size()
			+ " rooms, each filmed at eye level; pieces per mod " + perMod;
	}

	/**
	 * SCENE=rebuild_&lt;family&gt;_looks (49.10a): the family's first level as drawn (plains) and in each look it has
	 * ({@link BlueprintLooks#of}: the village cultures it is drawn in, 49.10b), furnished, side by side in that order;
	 * filmed as a row from the front, then each from its front corner, named by its look and numbered by its culture's
	 * place in {@link BlueprintLooks#CULTURES} (10_plains, 11_desert ... 14_taiga), so a culture the family has no look
	 * for leaves its number out. Passes when every look is there and every furniture spot holds its piece.
	 */
	private void placeLooks(ServerLevel level, ResourceLocation family) {
		List<ResourceLocation> ids = BlueprintLooks.of(family); // as drawn first, then only the looks the family has
		if (ids.size() < 2) {
			wrong.add(family + " has no looks");
			return;
		}
		List<Blueprint> drawn = new ArrayList<>();
		int width = 0;
		int depth = 0;
		int height = 0;
		for (ResourceLocation id : ids) {
			Blueprint b = BlueprintLibrary.get(level, id).orElse(null);
			if (b == null) {
				wrong.add("no blueprint " + id);
				return;
			}
			drawn.add(b);
			width = Math.max(width, b.size().getX());
			depth = Math.max(depth, b.size().getZ());
			height = Math.max(height, b.size().getY());
		}
		int step = width + 6;
		int rowX = step * ids.size();
		for (BlockPos p : BlockPos.betweenClosed(AT.offset(-16, -1, -16), AT.offset(rowX + 16, height + 4, depth + 16))) {
			BlockState want = p.getY() == AT.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (level.getBlockState(p) != want) {
				level.setBlock(p, want, 2 | 16);
			}
		}
		int pieces = 0;
		for (int i = 0; i < ids.size(); i++) {
			ResourceLocation id = ids.get(i);
			BlockPos at = AT.offset(i * step, 0, 0);
			FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
			for (Blueprint.Entry e : choice.apply(drawn.get(i), id).blocks()) {
				level.setBlock(at.offset(e.pos()), e.state(), 2 | 16);
			}
			for (FurnitureChoice.Placed piece : choice.spots().values()) {
				pieces++;
				for (Map.Entry<BlockPos, BlockState> cell : piece.cells().entrySet()) {
					BlockState there = level.getBlockState(at.offset(piece.pos()).offset(cell.getKey()));
					if (!there.is(cell.getValue().getBlock()) && !cell.getValue().isAir()) {
						wrong.add(id.getPath() + ": " + piece.kind() + " at " + piece.pos().offset(cell.getKey()).toShortString() + " is "
							+ BuiltInRegistries.BLOCK.getKey(there.getBlock()));
					}
				}
			}
		}
		double mid = height / 2.0;
		double cz = depth / 2.0;
		double rowMid = (step * (ids.size() - 1) + width) / 2.0;
		views.add(new View("01_row_front", new Vec3(rowMid, height * 0.8 + 6, -rowX * 0.55), new Vec3(rowMid, mid, cz), 70));
		views.add(new View("02_row_back", new Vec3(rowMid, height * 0.8 + 6, depth + rowX * 0.55), new Vec3(rowMid, mid, cz), 70));
		double back = Math.max(width, depth) * 0.9 + 6;
		for (int i = 0; i < ids.size(); i++) {
			double cx = i * step + width / 2.0;
			String name = i == 0 ? "plains" : BlueprintLooks.lookOf(ids.get(i)).orElse(ids.get(i).getPath());
			int culture = BlueprintLooks.CULTURES.indexOf(name);
			int number = i == 0 ? 10 : culture >= 0 ? 11 + culture : 10 + i;
			views.add(new View(String.format("%02d_%s", number, name), new Vec3(cx - back * 0.45, height * 0.45 + 2, cz - back * 0.85),
				new Vec3(cx, mid, cz), 70));
		}
		AliveWorkplace.LOG.info("REBUILD_SCENE {}: {} looks, {} furniture pieces", this.family, ids.size(), pieces);
		summary = this.family + ": " + family.getPath() + " as drawn and in "
			+ String.join(", ", ids.subList(1, ids.size()).stream().map(id -> BlueprintLooks.lookOf(id).orElse(id.getPath())).toList()) + ", "
			+ pieces + " furniture pieces placed";
	}

	/** Four outside views, a quarter turn apart, far enough back to take in the whole blueprint. */
	private void outside(Vec3i size) {
		double cx = size.getX() / 2.0;
		double cz = size.getZ() / 2.0;
		double mid = size.getY() / 2.0;
		double back = Math.max(size.getX(), size.getZ()) * 0.9 + 6;
		double up = size.getY() * 0.45 + 2;
		views.add(new View("01_outside_front", new Vec3(cx - back * 0.35, up, cz - back * 0.9), new Vec3(cx, mid, cz), 70));
		views.add(new View("02_outside_back", new Vec3(cx + back * 0.35, up, cz + back * 0.9), new Vec3(cx, mid, cz), 70));
		views.add(new View("03_outside_west", new Vec3(cx - back * 0.9, up, cz + back * 0.35), new Vec3(cx, mid, cz), 70));
		views.add(new View("04_outside_east", new Vec3(cx + back * 0.9, up, cz - back * 0.35), new Vec3(cx, mid, cz), 70));
	}

	/**
	 * Where the piece chosen for a cell stands: at its cell, or (B1386) set down straight below it onto the piece under it
	 * with nothing but air between, as {@code Resting.reseat} puts a lamp or crockery when the counter or table under it
	 * became a lower modded piece (the builder builds it there too). Null when it is in neither place.
	 */
	private static BlockPos standing(ServerLevel level, BlockPos at, BlockState want) {
		for (BlockPos p = at; at.getY() - p.getY() <= 3; p = p.below()) {
			BlockState there = level.getBlockState(AT.offset(p));
			if (there.is(want.getBlock())) {
				return p;
			}
			if (!there.isAir()) {
				return null;
			}
		}
		return null;
	}

	/** The spots grouped into rooms: the same floor (within 1) and within 5 blocks of another spot of the room. */
	static List<List<FurnitureSpot>> rooms(List<FurnitureSpot> spots, Map<FurnitureSpot, Integer> floors) {
		List<List<FurnitureSpot>> rooms = new ArrayList<>();
		Set<FurnitureSpot> done = new HashSet<>();
		for (FurnitureSpot seed : spots) {
			if (done.contains(seed)) {
				continue;
			}
			List<FurnitureSpot> room = new ArrayList<>();
			List<FurnitureSpot> todo = new ArrayList<>(List.of(seed));
			done.add(seed);
			while (!todo.isEmpty()) {
				FurnitureSpot s = todo.remove(todo.size() - 1);
				room.add(s);
				for (FurnitureSpot o : spots) {
					if (!done.contains(o) && Math.abs(floors.get(o) - floors.get(s)) <= 1
						&& Math.abs(o.pos().getX() - s.pos().getX()) <= 5 && Math.abs(o.pos().getZ() - s.pos().getZ()) <= 5) {
						done.add(o);
						todo.add(o);
					}
				}
			}
			rooms.add(room);
		}
		return rooms;
	}

	/**
	 * The floor a piece's room stands on, measured in the built world (B1386): the level of the feet over the first solid
	 * block under the spot that isn't furniture. A chair stands on it; a lamp hanging from a ceiling, a picture or shelf up a
	 * wall, a lamp or crockery on a counter all look down past the furniture to the same floor, whatever their height, and a
	 * cellar's floor is as far under y 0 as the build goes. (It used to be guessed from the kind, as y - 2 for a lamp, and
	 * kept at 0 or over: a cellar's lamps looked for a camera on the ground floor and a lamp hung low over an upper floor
	 * looked for one in the ceiling under it.)
	 */
	private static int floorOf(ServerLevel level, FurnitureSpot s, Set<BlockPos> furniture, int basement) {
		for (BlockPos p = s.pos(); p.getY() >= -basement - 1 && s.pos().getY() - p.getY() <= 8; p = p.below()) {
			BlockPos world = AT.offset(p);
			if (!furniture.contains(p) && !level.getBlockState(world).getCollisionShape(level, world).isEmpty()) {
				return p.getY() + 1;
			}
		}
		return s.pos().getY();
	}

	/** A room's floor: the one most of its pieces stand over (the lowest of equals; a shelf over a workbench reads one high). */
	private static int roomFloor(List<FurnitureSpot> room, Map<FurnitureSpot, Integer> floors) {
		Map<Integer, Integer> count = new TreeMap<>();
		room.forEach(s -> count.merge(floors.get(s), 1, Integer::sum));
		int best = floors.get(room.get(0));
		int most = 0;
		for (Map.Entry<Integer, Integer> e : count.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	/**
	 * The room's eye-level shot: from a free floor cell ({@link #standable}, not by a seat) of the room's floor with a clear
	 * line to the middle of its pieces, the farthest such cell; looked for within 3 blocks of the pieces, then (a lone lamp
	 * in a narrow corner) within 6. A room's other floors are tried after its own (a piece read one high). Null: no such cell.
	 */
	private View roomView(ServerLevel level, List<FurnitureSpot> room, Map<FurnitureSpot, Integer> floors, Set<BlockPos> furniture, Vec3i size,
						  int index) {
		double tx = 0;
		double ty = 0;
		double tz = 0;
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (FurnitureSpot s : room) {
			tx += s.pos().getX() + 0.5;
			ty += s.pos().getY() + 0.5;
			tz += s.pos().getZ() + 0.5;
			minX = Math.min(minX, s.pos().getX());
			maxX = Math.max(maxX, s.pos().getX());
			minZ = Math.min(minZ, s.pos().getZ());
			maxZ = Math.max(maxZ, s.pos().getZ());
		}
		int own = roomFloor(room, floors);
		List<Integer> levels = new ArrayList<>(List.of(own));
		room.stream().map(floors::get).distinct().sorted((a, b) -> Math.abs(a - own) - Math.abs(b - own)).filter(f -> f != own).forEach(levels::add);
		for (int reach : new int[] {3, 6}) {
			for (int floor : levels) {
				Vec3 target = new Vec3(tx / room.size(), Math.max(floor + 0.5, Math.min(floor + 2.5, ty / room.size())), tz / room.size());
				View best = null;
				double bestDistance = -1;
				for (int x = Math.max(0, minX - reach); x <= Math.min(size.getX() - 1, maxX + reach); x++) {
					for (int z = Math.max(0, minZ - reach); z <= Math.min(size.getZ() - 1, maxZ + reach); z++) {
						BlockPos feet = AT.offset(x, floor, z);
						if (!standable(level, feet) || nearSeat(level, feet)) {
							continue;
						}
						Vec3 eye = new Vec3(x + 0.5, floor + EYE, z + 0.5);
						double distance = eye.distanceTo(target);
						if (distance < 2.0 || distance <= bestDistance || !clearLine(level, eye, target, furniture)) {
							continue;
						}
						bestDistance = distance;
						best = new View(String.format("%02d_room_%d", 10 + index, index + 1), eye, target, 80);
					}
				}
				if (best != null) {
					return best;
				}
			}
		}
		return null;
	}

	/** Air (no collision) at the feet and the eye, and a floor under the feet: the camera is never inside a wall. */
	private static boolean standable(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
	}

	/** On a seat, or right behind one (a stair seat's back faces its facing side): a shot from there is a chair's back. */
	private static boolean nearSeat(ServerLevel level, BlockPos feet) {
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof StairBlock || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("chair")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Nothing in the way on the straight line from the eye to the target (sampled every tenth of a block): no point of it
	 * inside a block's collision shape, a glass pane or a fence as much as a wall (B1386: only full blocks counted, so the
	 * farthest cell could be outside, looking in through a window), except the room's own furniture, which the shot is of.
	 */
	private static boolean clearLine(ServerLevel level, Vec3 eye, Vec3 target, Set<BlockPos> furniture) {
		Vec3 a = Vec3.atLowerCornerOf(AT).add(eye);
		Vec3 b = Vec3.atLowerCornerOf(AT).add(target);
		int steps = (int) Math.ceil(a.distanceTo(b) * 10);
		for (int i = 1; i < steps - 5; i++) {
			Vec3 point = a.lerp(b, i / (double) steps);
			BlockPos p = BlockPos.containing(point);
			if (furniture.contains(p.subtract(AT))) {
				continue;
			}
			Vec3 local = point.subtract(p.getX(), p.getY(), p.getZ());
			for (AABB box : level.getBlockState(p).getCollisionShape(level, p).toAabbs()) {
				if (box.contains(local)) {
					return false;
				}
			}
		}
		return true;
	}
}
