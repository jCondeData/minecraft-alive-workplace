package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.STONEWORK_MIDDLE;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.lane;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.layAll;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.p;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.road;
import static io.github.jcondedata.aliveworkplace.test.QaRoadGameTests.route;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA for ROADMAP 27.16 (lamps, bridges and steps), from the spec, beyond RoadGameTests: lamps every 16 blocks on alternate
 * sides round the corners of a long street, none by a door even where doors line the street, one at a crossing (paved
 * square), lantern posts every 12 blocks of a lane, a Sandstone street's lamp in Sandstone, lamps lighting a dark bed; a
 * river 16 wide bridged with 4 pillars and one 17 wide refused; a dry drop deeper than 2 bridged (2 deep isn't a gap) and
 * a pillar never deeper than 12.
 */
public class QaRoadLampGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = QaRoadGameTests.AREA;

	private static int lampHeight(GameTestHelper helper) {
		return BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.STREET_LAMP.id()).orElseThrow().size().getY();
	}

	/** The top block of every Street Lamp standing on ground whose feet are at test y {@code feet}: the only blocks that high. */
	private static List<BlockPos> lampTops(GameTestHelper helper, int feet) {
		int y = feet + lampHeight(helper) - 1;
		List<BlockPos> tops = new ArrayList<>();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				if (!helper.getBlockState(new BlockPos(x, y, z)).isAir()) {
					tops.add(new BlockPos(x, y, z));
				}
			}
		}
		return tops;
	}

	/** The index of the node of {@code nodes} nearest the column of {@code abs}. */
	private static int nearest(List<BlockPos> nodes, BlockPos abs) {
		int best = 0;
		for (int i = 1; i < nodes.size(); i++) {
			if (dist(nodes.get(i), abs) < dist(nodes.get(best), abs)) {
				best = i;
			}
		}
		return best;
	}

	private static double dist(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static void door(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(new BlockPos(x, 3, z), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A street round three corners, about 70 blocks: a lamp every 16 blocks, the sides alternating, every one off the road. */
	//$ gametest_ticks_batch AREA '40' '"qaLampSpacing"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLampSpacing")
	public void qaLampsStandEverySixteenBlocksOnAlternateSidesRoundCorners(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(14, 2, 14), 14);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 5, 5), p(helper, hall, 24, 5),
			p(helper, hall, 24, 24), p(helper, hall, 5, 24), p(helper, hall, 5, 10))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		List<BlockPos> nodes = route(street, hall);
		helper.assertTrue(nodes.size() >= 64, "the street is " + nodes.size() + " nodes, not about 70");
		layAll(helper, hall, null);
		street = entity.plan().roads().get(0);
		List<BlockPos> tops = lampTops(helper, 2);
		int expected = (nodes.size() - 1) / Roads.LAMP_EVERY;
		helper.assertTrue(tops.size() == expected, tops.size() + " lamps on a " + nodes.size() + "-block street, not " + expected + ": " + tops);
		helper.assertValueEqual(street.lamps(), tops.size(), "lamps the plan counts");
		List<int[]> lamps = new ArrayList<>(); // {node, side}
		for (BlockPos t : tops) {
			BlockPos abs = helper.absolutePos(t);
			int j = nearest(nodes, abs);
			BlockPos a = nodes.get(Math.max(0, j - 1));
			BlockPos b = nodes.get(Math.min(nodes.size() - 1, j + 1));
			int cross = (b.getX() - a.getX()) * (abs.getZ() - nodes.get(j).getZ()) - (b.getZ() - a.getZ()) * (abs.getX() - nodes.get(j).getX());
			lamps.add(new int[] {j, Integer.signum(cross)});
			helper.assertTrue(dist(nodes.get(j), abs) >= 4, "a lamp stands on the road at " + abs);
		}
		lamps.sort((x, y) -> Integer.compare(x[0], y[0]));
		for (int i = 0; i < lamps.size(); i++) {
			int[] l = lamps.get(i);
			helper.assertTrue(l[1] != 0, "a lamp in line with the road at node " + l[0]);
			if (i > 0) {
				int gap = l[0] - lamps.get(i - 1)[0];
				helper.assertTrue(gap >= Roads.LAMP_EVERY - 3 && gap <= Roads.LAMP_EVERY + 3, "lamps " + gap + " blocks apart, not 16: " + nodes.size() + " nodes, lamps at "
					+ lamps.stream().map(x -> x[0]).toList());
				helper.assertTrue(l[1] != lamps.get(i - 1)[1], "two lamps in a row on the same side, at nodes " + lamps.get(i - 1)[0] + " and " + l[0]);
			}
		}
		helper.succeed();
	}

	/** Doors line both sides of the street just past where its lamp would stand: no lamp stands within 2 blocks of any door. */
	//$ gametest_ticks_batch AREA '40' '"qaLampDoors"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLampDoors")
	public void qaNoLampStandsByADoorEvenWhereDoorsLineTheStreet(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		List<BlockPos> doors = new ArrayList<>();
		for (int x = 13; x <= 25; x += 2) {
			for (int z : new int[] {8, 20}) { // 2 blocks past where a lamp's foot would stand on either side, not on it
				door(helper, x, z);
				doors.add(new BlockPos(x, 2, z));
			}
		}
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 14), p(helper, hall, 27, 14))));
		Roads.routeNow(helper.getLevel(), hall);
		layAll(helper, hall, null);
		int lampTop = 2 + lampHeight(helper) - 1;
		for (BlockPos d : doors) {
			for (BlockPos q : BlockPos.betweenClosed(d.offset(-2, 0, -2), d.offset(2, lampTop - 2, 2))) {
				BlockState st = helper.getBlockState(q);
				helper.assertTrue(st.isAir() || st.is(Blocks.OAK_DOOR), "a lamp stands by the door at " + d + ": " + st + " at " + q);
			}
		}
		helper.assertValueEqual(entity.plan().roads().get(0).lamps(), lampTops(helper, 2).size(), "lamps the plan counts and lamps standing");
		helper.succeed();
	}

	/** Two streets crossing: the crossing is paved square (all middle, no edge blocks), and it gets its lamps. */
	//$ gametest_ticks_batch AREA '40' '"qaLampCrossing"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLampCrossing")
	public void qaACrossingIsPavedSquareAndLit(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(26, 2, 26), 14);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 14), p(helper, hall, 27, 14)))
			.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 8, 2), p(helper, hall, 8, 27))));
		Roads.routeNow(helper.getLevel(), hall);
		helper.assertTrue(entity.plan().roads().stream().allMatch(CityPlan.Road::routed), "both streets weren't routed");
		layAll(helper, hall, null);
		for (int x = 7; x <= 9; x++) {
			for (int z = 13; z <= 15; z++) {
				helper.assertTrue(STONEWORK_MIDDLE.contains(at(helper, x, 1, z)), "the crossing isn't paved square at x " + x + " z " + z + ": " + at(helper, x, 1, z));
			}
		}
		helper.assertTrue(at(helper, 20, 1, 13) == Blocks.COBBLESTONE && at(helper, 8 - 1, 1, 22) == Blocks.COBBLESTONE, "away from the crossing the edges are gone");
		List<BlockPos> tops = lampTops(helper, 2);
		long near = tops.stream().filter(t -> Math.max(Math.abs(t.getX() - 8), Math.abs(t.getZ() - 14)) <= 6).count();
		int lamps = entity.plan().roads().stream().mapToInt(CityPlan.Road::lamps).sum();
		helper.assertValueEqual(lamps, tops.size(), "lamps the plan counts and lamps standing");
		// one lamp every 16 blocks of each 26-block street, and one at the crossing for each
		helper.assertTrue(tops.size() >= 4, "not a lamp for each street and each street's crossing: " + tops);
		helper.assertTrue(near >= 2, "the crossing has " + near + " lamps within 6 blocks: " + tops);
		helper.succeed();
	}

	/** A lane in As drawn: a lantern post (the style's spruce fence, two high, a lantern on top) every 12 blocks; no Street Lamps. */
	//$ gametest_ticks_batch AREA '40' '"qaLampLane"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLampLane")
	public void qaALaneGetsALanternPostEveryTwelveBlocks(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(lane("", p(helper, hall, 2, 10), p(helper, hall, 27, 10))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road l = entity.plan().roads().get(0);
		List<BlockPos> nodes = route(l, hall);
		helper.assertTrue(l.lane() && nodes.size() >= 24, "the lane is " + nodes.size() + " nodes");
		layAll(helper, hall, null);
		List<BlockPos> lanterns = new ArrayList<>();
		for (BlockPos q : BlockPos.betweenClosed(new BlockPos(0, 2, 0), new BlockPos(29, 8, 29))) {
			if (helper.getBlockState(q).is(Blocks.LANTERN)) {
				lanterns.add(q.immutable());
			}
		}
		int expected = (nodes.size() - 2) / Roads.POST_EVERY;
		helper.assertTrue(lanterns.size() == expected, lanterns.size() + " lantern posts on a " + nodes.size() + "-block lane, not " + expected + ": " + lanterns);
		for (BlockPos lantern : lanterns) {
			helper.assertTrue(lantern.getY() == 4 && at(helper, lantern.getX(), 3, lantern.getZ()) == Blocks.SPRUCE_FENCE
				&& at(helper, lantern.getX(), 2, lantern.getZ()) == Blocks.SPRUCE_FENCE && at(helper, lantern.getX(), 1, lantern.getZ()) == Blocks.GRASS_BLOCK,
				"the lantern at " + lantern + " isn't on a spruce fence two high on the ground");
			helper.assertTrue(!helper.getBlockState(lantern).getValue(net.minecraft.world.level.block.LanternBlock.HANGING), "the post's lantern hangs");
		}
		lanterns.sort((a, b) -> Integer.compare(a.getX(), b.getX()));
		for (int i = 1; i < lanterns.size(); i++) {
			int gap = lanterns.get(i).getX() - lanterns.get(i - 1).getX();
			helper.assertTrue(gap >= Roads.POST_EVERY - 3 && gap <= Roads.POST_EVERY + 3, "lantern posts " + gap + " blocks apart, not 12: " + lanterns);
		}
		helper.assertTrue(lampTops(helper, 2).isEmpty(), "a lane got a Street Lamp: " + lampTops(helper, 2));
		helper.succeed();
	}

	/** A Sandstone street's lamp is the Street Lamp in Sandstone: the same blocks as that blueprint (its lanterns the style's lamp). */
	//$ gametest_ticks_batch AREA '40' '"qaLampStyle"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaLampStyle")
	public void qaASandstoneStreetsLampIsTheSandstoneStreetLamp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		Blueprint styled = BlueprintLibrary.get(level, BlueprintStyles.styled(StarterBlueprints.STREET_LAMP.id(), "sandstone"))
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no Sandstone Street Lamp"));
		Set<Block> want = new HashSet<>();
		styled.blocks().stream().filter(e -> !e.state().isAir()).forEach(e -> want.add(e.state().getBlock()));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "sandstone", p(helper, hall, 2, 14), p(helper, hall, 27, 14))));
		Roads.routeNow(level, hall);
		layAll(helper, hall, null);
		helper.assertTrue(at(helper, 10, 1, 14) == Blocks.SMOOTH_SANDSTONE || at(helper, 10, 1, 14) == Blocks.CUT_SANDSTONE, "the street isn't Sandstone");
		List<BlockPos> tops = lampTops(helper, 2);
		helper.assertTrue(tops.size() == 1, "not one lamp: " + tops);
		BlockPos t = tops.get(0);
		Set<Block> got = new HashSet<>();
		int w = styled.size().getX() / 2;
		for (BlockPos q : BlockPos.betweenClosed(new BlockPos(t.getX() - w, 2, t.getZ() - w), new BlockPos(t.getX() + w, t.getY(), t.getZ() + w))) {
			BlockState st = helper.getBlockState(q);
			if (!st.isAir()) {
				got.add(st.getBlock());
			}
		}
		helper.assertValueEqual(got, want, "the lamp's blocks");
		helper.succeed();
	}

	/** A bed in the dark beside a street: once the street and its lamp are laid, the bed is lit (no more a "dark" bed). */
	//$ gametest_ticks_batch AREA '200' '"qaLampBeds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLampBeds")
	public void qaStreetLampsLightTheBedsNearThem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		helper.setBlock(new BlockPos(18, 2, 9), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		helper.setBlock(new BlockPos(18, 2, 8), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		BlockPos bed = helper.absolutePos(new BlockPos(18, 2, 9));
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 2, 14), p(helper, hall, 27, 14))));
		Roads.routeNow(level, hall);
		helper.assertTrue(VillageAdvice.darkBeds(level, hall).contains(bed), "setup: the bed isn't dark before the street: " + VillageAdvice.darkBeds(level, hall));
		layAll(helper, hall, null);
		helper.assertTrue(lampTops(helper, 2).size() == 1, "not one lamp: " + lampTops(helper, 2));
		helper.succeedWhen(() -> helper.assertTrue(!VillageAdvice.darkBeds(level, hall).contains(bed),
			"the bed by the street lamp " + lampTops(helper, 2) + " is still dark: light " + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, bed)));
	}

	/** A river 16 wide, the widest a bridge spans: bridged, a stair at each end, rails, a pillar every 4 blocks (4 of them) to the bed. */
	//$ gametest_ticks_batch AREA '40' '"qaBridge16"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBridge16")
	public void qaARiverSixteenWideIsBridgedWithFourPillars(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 3, 26), 14);
		river(helper, 6, 21);
		helper.setBlock(new BlockPos(2, 3, 26), io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 1, 8), p(helper, hall, 28, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.gap() == 0, "a 16-wide river was refused: gap " + street.gap());
		List<BlockPos> nodes = route(street, hall);
		helper.assertTrue(nodes.get(nodes.size() - 1).distManhattan(helper.absolutePos(new BlockPos(28, 3, 8))) <= 2, "the street doesn't cross: " + nodes.get(nodes.size() - 1));
		helper.assertTrue(Roads.deskNotes(entity.plan()).isEmpty(), "the desk has a note for a bridged river");
		layAll(helper, hall, null);
		for (int x = 6; x <= 21; x++) {
			BlockState deck = helper.getBlockState(new BlockPos(x, 3, 8));
			helper.assertTrue(x == 6 || x == 21 ? deck.is(Blocks.STONE_BRICK_STAIRS) : deck.is(Blocks.STONE_BRICKS), "the deck at x " + x + " is " + deck);
			helper.assertTrue(at(helper, x, 4, 6) == Blocks.STONE_BRICK_WALL && at(helper, x, 4, 10) == Blocks.STONE_BRICK_WALL, "no rails at x " + x);
		}
		int pillars = 0;
		for (int x = 6; x <= 21; x++) {
			if (at(helper, x, 2, 8) == Blocks.STONE_BRICKS) {
				pillars++;
				helper.assertTrue(at(helper, x, 1, 8) == Blocks.STONE_BRICKS, "the pillar at x " + x + " doesn't reach the bed");
			}
		}
		helper.assertTrue(pillars == 4, pillars + " pillars under a 16-wide bridge, not 4");
		helper.succeed();
	}

	/** A river 17 wide, one more than a bridge spans: the road stops at the near bank and the desk says so, in English. */
	//$ gametest_ticks_batch AREA '40' '"qaBridge17"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBridge17")
	public void qaARiverSeventeenWideIsRefused(GameTestHelper helper) {
		BlockPos hall = ground(helper, new BlockPos(2, 3, 26), 14);
		river(helper, 6, 22);
		helper.setBlock(new BlockPos(2, 3, 26), io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 1, 8), p(helper, hall, 28, 8))));
		Roads.routeNow(helper.getLevel(), hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.routed() && street.gap() == 17, "the 17-wide river: routed " + street.routed() + ", gap " + street.gap());
		int bank = helper.absolutePos(new BlockPos(5, 0, 0)).getX();
		helper.assertTrue(route(street, hall).stream().allMatch(n -> n.getX() <= bank), "the road goes past the near bank");
		helper.assertValueEqual(Roads.deskNotes(entity.plan()).stream().map(Component::getString).toList(),
			List.of("A road stops at a gap 17 blocks wide: a bridge spans 16 at most"), "the desk's note");
		layAll(helper, hall, null);
		helper.assertTrue(at(helper, 10, 3, 8) == Blocks.AIR && at(helper, 10, 2, 8) == Blocks.WATER, "something was built over the 17-wide river");
		helper.succeed();
	}

	/**
	 * Ground 15 high with a dry ravine 14 deep and 6 wide: the street bridges it, its pillar stops 12 blocks down; a trench
	 * 2 deep is no gap, one 3 deep is.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaBridgeRavine"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaBridgeRavine")
	public void qaADryDropDeeperThanTwoIsBridgedAndAPillarStopsTwelveDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper, new BlockPos(2, 2, 26), 14);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				int floor = 15;
				if (x >= 12 && x <= 17) {
					floor = z <= 12 ? 1 : z >= 16 && z <= 19 ? 13 : z >= 23 && z <= 26 ? 12 : 15;
				}
				for (int y = 1; y <= 15; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < floor ? Blocks.DIRT : y == floor ? Blocks.GRASS_BLOCK : Blocks.AIR);
				}
				for (int y = 16; y < 24; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(new BlockPos(2, 16, 28), io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		hall = helper.absolutePos(new BlockPos(2, 16, 28));
		// the trenches, straight from the gap finder: 2 deep isn't a gap, 3 deep is (6 wide)
		helper.assertTrue(Roads.gap(level, helper.absolutePos(new BlockPos(1, 16, 17)), helper.absolutePos(new BlockPos(28, 16, 17))) == null, "a trench 2 deep is a gap");
		Roads.Gap three = Roads.gap(level, helper.absolutePos(new BlockPos(1, 16, 24)), helper.absolutePos(new BlockPos(28, 16, 24)));
		helper.assertTrue(three != null && three.width() == 6, "a trench 3 deep and 6 wide: " + three);
		VillageHallBlockEntity entity = hall(helper, hall);
		entity.setPlan(CityPlan.EMPTY.addRoad(road(CityPlan.Road.STREET, "stonework", p(helper, hall, 1, 6), p(helper, hall, 28, 6))));
		Roads.routeNow(level, hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		helper.assertTrue(street.gap() == 0, "a 6-wide ravine was refused: gap " + street.gap());
		List<BlockPos> nodes = route(street, hall);
		helper.assertTrue(nodes.get(nodes.size() - 1).distManhattan(helper.absolutePos(new BlockPos(28, 16, 6))) <= 2, "the street doesn't cross: " + nodes);
		layAll(helper, hall, null);
		for (int x = 13; x <= 16; x++) {
			helper.assertTrue(at(helper, x, 16, 6) == Blocks.STONE_BRICKS, "the deck at x " + x + " is " + at(helper, x, 16, 6));
		}
		int pillars = 0;
		for (int x = 12; x <= 17; x++) {
			if (at(helper, x, 15, 6) == Blocks.STONE_BRICKS) {
				pillars++;
				int depth = 0;
				for (int y = 15; y >= 2 && at(helper, x, y, 6) == Blocks.STONE_BRICKS; y--) {
					depth++;
				}
				helper.assertTrue(depth == 12, "the pillar at x " + x + " is " + depth + " deep, not 12 (the most) over a ravine 14 deep");
			}
		}
		helper.assertTrue(pillars >= 1, "no pillar under the ravine's bridge");
		helper.succeed();
	}

	/** A river (water 2 deep, bed at y 0) at test x {@code x0..x1} across the whole area, the banks raised to y 2. */
	private static void river(GameTestHelper helper, int x0, int x1) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				boolean water = x >= x0 && x <= x1;
				helper.setBlock(new BlockPos(x, 1, z), water ? Blocks.WATER : Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), water ? Blocks.WATER : Blocks.GRASS_BLOCK);
			}
		}
	}
}
