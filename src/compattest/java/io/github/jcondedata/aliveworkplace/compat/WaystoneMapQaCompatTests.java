package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for ROADMAP 41.2 ("The hall's Map shows the waystone network") with the real Waystones mod, written from its text:
 * the Map lists the player's villages with a waystone, each with its distance and direction, and marks which ones the
 * player has discovered. What the builder's and Milestone 41's QA tests don't already cover: all eight directions and
 * both sides of a direction's edge, a village four blocks off and one 5,000 blocks off, nearest first with the list cut
 * at six and the rest counted; a far village whose chunks aren't loaded, discovered by one player and not by another;
 * and the switch turned off after the stones stand (they stay on the Map). Every line is read through en_us.json.
 */
public class WaystoneMapQaCompatTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String NETWORK = "aliveworkplace_village_waystones";
	private static final String LINE = "screen.aliveworkplace.hall.waystone";
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	private static void known(ServerLevel level, BlockPos hall, String name, UUID owner) {
		RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), hall), Component.literal(name),
			Optional.of(owner), "Jesse", "hamlet", 0, 0, level.getGameTime()));
	}

	private static void hallBlock(ServerLevel level, BlockPos at, String name, UUID owner) {
		level.setBlock(at, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		if (!(level.getBlockEntity(at) instanceof VillageHallBlockEntity hall)) {
			throw new GameTestAssertException("no hall at " + at);
		}
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner, "Jesse");
		known(level, at, name, owner);
	}

	/** The village network file with these villages' stones added, as if their halls raised them on an earlier day. */
	private static void keepStones(ServerLevel level, List<BlockPos> halls, List<BlockPos> stones) {
		CompoundTag tag = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
		long[] oldHalls = tag.getLongArray("halls");
		long[] oldStones = tag.getLongArray("stones");
		long[] h = new long[oldHalls.length + halls.size()];
		long[] s = new long[oldStones.length + stones.size()];
		System.arraycopy(oldHalls, 0, h, 0, oldHalls.length);
		System.arraycopy(oldStones, 0, s, 0, oldStones.length);
		for (int i = 0; i < halls.size(); i++) {
			h[oldHalls.length + i] = halls.get(i).asLong();
			s[oldStones.length + i] = stones.get(i).asLong();
		}
		tag.putLongArray("halls", h);
		tag.putLongArray("stones", s);
		level.getDataStorage().set(NETWORK, VillageWaystones.Data.load(tag, level.registryAccess()));
	}

	private static BlueprintData.Placement placement(ServerLevel level, BlockPos origin) {
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	/** Sets every block of the plaza at the absolute {@code origin} as a finished one stands, and records it finished. */
	private static BuildPlan plaza(ServerLevel level, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(level, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(level, origin), UUID.randomUUID());
		return plan;
	}

	/** The Map's waystone lines on the hall's screen, as the player reads them. */
	private static List<String> mapLines(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		List<String> all = WaystoneQaCompatTests.mapLore(player, hall).stream().map(Component::getString).toList();
		for (String line : all) {
			helper.assertFalse(line.contains("aliveworkplace."), "a raw key on the Map: " + all);
		}
		return WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
	}

	private static void forget(ServerLevel level, List<BlockPos> halls) {
		RealmData realms = RealmData.get(level.getServer());
		for (BlockPos hall : halls) {
			VillageWaystones.Data.get(level).forget(hall);
			realms.forgetKnown(GlobalPos.of(level.dimension(), hall));
		}
	}

	private record Far(String name, int dx, int dz, String reads) {
	}

	/**
	 * Distance and direction (41.2) for twelve villages round the hall: due north four blocks off (the nearest that counts
	 * as away), each of the eight directions about 2,500 blocks off, both sides of the east/north-east edge (22.49 and
	 * 22.54 degrees north of east) and one 5,000 blocks south-east. The Map lists them nearest first, six at a time with
	 * "and 6 more", the count in the heading; the next six come up once the nearest are off the network. (The far
	 * villages' stones aren't loaded or known to Waystones, so they read as broken; the near one has no stone block.)
	 */
	//$ gametest_batch AREA '"qaWaystoneMapCompass"'
	@GameTest(template = AREA, batch = "qaWaystoneMapCompass")
	public void theMapGivesEveryDirectionAndDistanceNearestFirst(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
		List<Far> villages = List.of(
			new Far("Nearby", 0, -4, "4 blocks north"),
			new Far("Edge East", 2000, -828, "2165 blocks east"),
			new Far("Edge Northeast", 2000, -830, "2165 blocks north-east"),
			new Far("North", 0, -2500, "2500 blocks north"),
			new Far("Northeast", 1800, -1800, "2546 blocks north-east"),
			new Far("East", 2600, 0, "2600 blocks east"),
			new Far("Southeast", 1900, 1900, "2687 blocks south-east"),
			new Far("South", 0, 2800, "2800 blocks south"),
			new Far("Southwest", -2000, 2000, "2828 blocks south-west"),
			new Far("West", -2900, 0, "2900 blocks west"),
			new Far("Northwest", -2100, -2100, "2970 blocks north-west"),
			new Far("Faraway", 3000, 4000, "5000 blocks south-east"));
		List<BlockPos> halls = new ArrayList<>();
		List<BlockPos> stones = new ArrayList<>();
		for (Far v : villages) {
			BlockPos stone = hall.offset(v.dx(), 0, v.dz());
			// The village's hall stands a few blocks beyond its stone (the near one's in the test area).
			BlockPos at = v.dx() == 0 && v.dz() == -4 ? helper.absolutePos(new BlockPos(15, 2, 6)) : stone.offset(2, 0, 2);
			halls.add(at);
			stones.add(stone);
		}
		List<BlockPos> everyHall = new ArrayList<>(halls);
		everyHall.add(hall);
		forget(level, everyHall);
		try {
			hallBlock(level, hall, "Home", owner);
			hallBlock(level, halls.get(0), "Nearby", owner);
			for (int i = 1; i < villages.size(); i++) {
				helper.assertFalse(level.isLoaded(halls.get(i)), "setup: " + villages.get(i).name() + "'s hall is loaded at " + halls.get(i));
				known(level, halls.get(i), villages.get(i).name(), owner);
			}
			keepStones(level, halls, stones);

			List<String> first = mapLines(helper, player, hall);
			List<String> want = new ArrayList<>();
			want.add("Waystone network (12):");
			for (int i = 0; i < 6; i++) {
				want.add(villages.get(i).name() + ", " + villages.get(i).reads() + ": its waystone is broken");
			}
			want.add("and 6 more");
			helper.assertTrue(first.equals(want), "the Map, nearest six:\n  got  " + first + "\n  want " + want);

			for (int i = 0; i < 6; i++) {
				VillageWaystones.Data.get(level).forget(halls.get(i));
			}
			List<String> second = mapLines(helper, player, hall);
			List<String> next = new ArrayList<>();
			next.add("Waystone network (6):");
			for (int i = 6; i < villages.size(); i++) {
				next.add(villages.get(i).name() + ", " + villages.get(i).reads() + ": its waystone is broken");
			}
			// (exactly six listed: no "and N more" line)
			helper.assertTrue(second.equals(next), "the Map, the next six:\n  got  " + second + "\n  want " + next);
		} finally {
			forget(level, everyHall);
			level.setBlock(halls.get(0), Blocks.AIR.defaultBlockState(), 3);
		}
		helper.succeed();
	}

	/**
	 * A village 1,500 blocks east whose chunks are no longer loaded, its plaza's waystone standing: on Home's Map it reads
	 * "discovered" for the player who used that stone and "not discovered yet" for one who didn't, with its distance and
	 * direction and its own name, never "broken". (The usual case on a server: the other villages are far off.)
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaWaystoneMapUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaWaystoneMapUnloaded")
	public void aFarVillageNotLoadedIsListedDiscoveredOrNotPerPlayer(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer traveler = helper.makeMockServerPlayerInLevel();
		ServerPlayer newcomer = helper.makeMockServerPlayerInLevel();
		UUID owner = traveler.getUUID();
		BlockPos home = helper.absolutePos(new BlockPos(15, 2, 15));
		BlockPos far = home.offset(1500, 0, 0);
		BlockPos origin = far.offset(2, 0, 2);
		BlockPos stone = origin.offset(STONE);
		List<BlockPos> halls = List.of(home, far);
		forget(level, halls);
		helper.assertFalse(level.isLoaded(far), "setup: the far village is loaded before the test builds it");
		hallBlock(level, home, "Home", owner);
		hallBlock(level, far, "Farhollow", owner);
		BuildPlan plan = plaza(level, origin);
		VillageWaystones.round(level, far);
		helper.assertTrue(VillageWaystones.stoneOf(level, far).equals(Optional.of(stone)), "setup: Farhollow's stone: " + VillageWaystones.stoneOf(level, far));
		Waystone waystone = WaystonesAPI.getWaystoneAt(level, stone).orElseThrow(() -> new GameTestAssertException("setup: Farhollow has no waystone"));
		WaystonesAPI.activateWaystone(traveler, waystone);
		awaitUnloaded(helper, halls, plan, List.of(far, stone), traveler, newcomer, 200);
	}

	private static void awaitUnloaded(GameTestHelper helper, List<BlockPos> halls, BuildPlan plan, List<BlockPos> spots, ServerPlayer traveler,
		ServerPlayer newcomer, int tries) {
		ServerLevel level = helper.getLevel();
		if (spots.stream().anyMatch(level::isLoaded) && tries > 0) {
			helper.runAfterDelay(5, () -> awaitUnloaded(helper, halls, plan, spots, traveler, newcomer, tries - 1));
			return;
		}
		BlockPos home = halls.get(0);
		BlockPos far = halls.get(1);
		try {
			helper.assertFalse(spots.stream().anyMatch(level::isLoaded), "setup: Farhollow's chunks never unloaded");
			LOGGER.info("QA 41.2: Farhollow's chunks unloaded after {} polls of 5 ticks", 200 - tries);
			String where = "1505 blocks east"; // the stone is 1505 east and 5 south of Home's hall
			List<String> seen = mapLines(helper, traveler, home);
			helper.assertTrue(seen.equals(List.of("Waystone network (1):", "Farhollow, " + where + ": discovered")),
				"the Map for the player who used Farhollow's stone: " + seen);
			helper.assertFalse(level.isLoaded(far), "opening the Map loaded Farhollow's chunk");
			List<String> fresh = mapLines(helper, newcomer, home);
			helper.assertTrue(fresh.equals(List.of("Waystone network (1):", "Farhollow, " + where + ": not discovered yet")),
				"the Map for a player who never used it: " + fresh);
		} finally {
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.LANDSCAPE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.FOUNDATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (step.secondaryPos() != null) {
						level.setBlock(step.secondaryPos(), Blocks.AIR.defaultBlockState(), 3);
					}
					level.setBlock(step.pos(), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
			BuildSiteManager.get(level).forgetFinished(placement(level, far.offset(2, 0, 2)));
			forget(level, halls);
		}
		helper.succeed();
	}

	/**
	 * {@code villageWaystones} turned off after both stones stand (its tooltip: "stones already standing stay"): the Map still
	 * lists both villages with their stones, this one first, and a stone broken meanwhile reads as broken.
	 */
	//$ gametest_batch AREA '"qaWaystoneMapSwitchOff"'
	@GameTest(template = AREA, batch = "qaWaystoneMapSwitchOff")
	public void theSwitchOffTheStandingStonesStayOnTheMap(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		WaystoneQaCompatTests.cleanUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean was = VillageWaystones.ENABLED;
		try {
			WaystoneQaCompatTests.villages(helper, true);
			BlockPos a = helper.absolutePos(WaystoneQaCompatTests.HALL_A);
			// (the player's own villages: the hall is theirs)
			WaystoneQaCompatTests.hall(helper, WaystoneQaCompatTests.HALL_A, "Ashford", player.getUUID());
			WaystoneQaCompatTests.hall(helper, WaystoneQaCompatTests.HALL_B, "Brookmere", player.getUUID());
			VillageWaystones.ENABLED = false;
			VillageWaystones.round(level, a);
			VillageWaystones.round(level, helper.absolutePos(WaystoneQaCompatTests.HALL_B));
			List<String> lines = mapLines(helper, player, a);
			helper.assertTrue(lines.size() == 3 && lines.get(0).equals("Waystone network (2):") && lines.get(1).equals("Ashford, this village: not discovered yet")
				&& lines.get(2).startsWith("Brookmere, ") && lines.get(2).endsWith("blocks south-east: not discovered yet"), "the Map with the switch off: " + lines);

			WaystoneQaCompatTests.breakStone(helper, WaystoneQaCompatTests.PLAZA_B);
			List<String> after = mapLines(helper, player, a);
			helper.assertTrue(after.size() == 3 && after.get(2).startsWith("Brookmere, ") && after.get(2).endsWith(": its waystone is broken"),
				"a stone broken with the switch off: " + after);
		} finally {
			VillageWaystones.ENABLED = was;
			WaystoneQaCompatTests.cleanUp(helper);
		}
		helper.succeed();
	}
}
