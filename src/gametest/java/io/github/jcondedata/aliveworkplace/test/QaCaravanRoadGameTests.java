package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CaravanRoads;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.17 (roads between villages), from the spec, beyond CaravanRoadGameTests: the reach and the join at
 * their boundaries from both sides (halfway at 256, a 30-block gap joined, a 34-block gap ending short), the
 * {@code caravanRoadReach} config, the half starting from the end of the village's nearest road and in the style of the
 * zone it starts from, no route no road, and every sentence the feature shows a player (both chronicle lines and the
 * milestone's sign) rendered with real arguments. The halves are planned only (no routing) where the routing isn't the
 * point, so most of these need no land loaded.
 */
public class QaCaravanRoadGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** The area cleared to flat grass (floor y 1), old sites round it forgotten, and a hall at {@code at}; its absolute position. */
	private static BlockPos ground(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 60, level.getMinBuildHeight(), a.getZ() - 60, a.getX() + 90, level.getMaxBuildHeight(), a.getZ() + 90);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(at);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
	}

	private static double flat(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	/** The last planned point of the half (absolute). */
	private static BlockPos lastPoint(CityPlan.Road road, BlockPos hall) {
		return road.points().get(road.points().size() - 1).offset(hall);
	}

	/** Villages on the caravans' list at {@code others}, named, with routes from {@code a}; all taken off the list when the test ends. */
	private static Caravans.Data villages(GameTestHelper helper, BlockPos a, List<BlockPos> others, boolean routes) {
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		Leftovers.after(helper, () -> {
			data.remove(a);
			others.forEach(data::remove);
		});
		data.setWants(a, Component.literal("Ashford"), List.of());
		for (int i = 0; i < others.size(); i++) {
			data.setWants(others.get(i), Component.literal("Other" + i), List.of());
			if (routes) {
				helper.assertTrue(data.toggleRoute(a, others.get(i)), "setup: no route to " + others.get(i));
			}
		}
		return data;
	}

	/**
	 * The reach at its boundary, from both sides (544 apart: exactly 32 apart, joined). Halls 512 apart: halfway is 256, exactly the reach, so the half goes to
	 * halfway. 542 apart: each half stops at 256 and the two would be 30 apart, within 32, so they're joined (the half goes
	 * on to halfway, 271). 546 apart: 34 apart, not joined, the half goes 256 and stops short (a milestone).
	 */
	//$ gametest_ticks_batch AREA '20' '"qaCaravanReach"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCaravanReach")
	public void theReachAndTheJoinAtTheirBoundaries(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper, new BlockPos(14, 2, 14));
		helper.assertTrue(CaravanRoads.REACH == 256, "setup: caravanRoadReach isn't 256: " + CaravanRoads.REACH);
		BlockPos at512 = a.offset(0, 0, -512);
		BlockPos at542 = a.offset(542, 0, 0);
		BlockPos at546 = a.offset(0, 0, 546);
		BlockPos at544 = a.offset(-544, 0, 0);
		Caravans.Data data = villages(helper, a, List.of(at512, at542, at546), true);
		data.setWants(at544, Component.literal("Westfold"), List.of());
		Leftovers.after(helper, () -> data.remove(at544));
		helper.assertTrue(data.toggleRoute(at544, a), "setup: no route from the west"); // its route: Ashford builds its half all the same
		CaravanRoads.plan(level, a, hall(helper, a));
		CityPlan plan = hall(helper, a).plan();
		helper.assertValueEqual(plan.caravanRoads(), 4, "halves planned for four routes");
		// 544 apart: the halves would end exactly 32 apart, "within 32 blocks", so they're joined at halfway (272)
		CityPlan.Road r544 = CaravanRoads.roadTo(plan, a, at544);
		helper.assertTrue(r544 != null && Math.abs(flat(a, lastPoint(r544, a)) - 272) <= 1.5,
			"halves exactly 32 apart aren't joined: " + (r544 == null ? "no half" : "the half ends " + flat(a, lastPoint(r544, a)) + " out, halfway is 272"));
		CityPlan.Road r512 = CaravanRoads.roadTo(plan, a, at512);
		CityPlan.Road r542 = CaravanRoads.roadTo(plan, a, at542);
		CityPlan.Road r546 = CaravanRoads.roadTo(plan, a, at546);
		helper.assertTrue(r512 != null && r542 != null && r546 != null, "a route got no half: " + r512 + " / " + r542 + " / " + r546);
		helper.assertTrue(Math.abs(flat(a, lastPoint(r512, a)) - 256) <= 1.5 && flat(lastPoint(r512, a), a.offset(0, 0, -256)) <= 1.5,
			"halfway at exactly 256: the half ends at " + lastPoint(r512, a) + ", " + flat(a, lastPoint(r512, a)) + " out");
		helper.assertTrue(Math.abs(flat(a, lastPoint(r542, a)) - 271) <= 1.5,
			"halves 30 apart aren't joined: the half ends " + flat(a, lastPoint(r542, a)) + " out, halfway is 271");
		helper.assertTrue(Math.abs(flat(a, lastPoint(r546, a)) - 256) <= 1.5,
			"halves 34 apart: the half ends " + flat(a, lastPoint(r546, a)) + " out, not at the reach (256)");
		// each half is a straight line from the hall towards the other village (nothing of its own drawn to start from)
		helper.assertTrue(r546.points().get(0).equals(BlockPos.ZERO) && r546.points().stream().allMatch(p -> p.getX() == 0 && p.getZ() >= 0),
			"the half to the south doesn't run from the hall towards it: " + r546.points());
		helper.assertTrue(r542.points().stream().allMatch(p -> p.getZ() == 0 && p.getX() >= 0), "the half to the east wanders: " + r542.points());
		for (CityPlan.Road r : List.of(r512, r542, r546)) {
			helper.assertTrue(r.width() == CityPlan.Road.STREET && r.caravan() && r.approved(), "a half isn't an approved street: " + r);
		}
		helper.succeed();
	}

	/** caravanRoadReach set to 100: a half towards a village 400 away goes 100 blocks; put back, 200 (halfway). */
	//$ gametest_ticks_batch AREA '20' '"qaCaravanReachConfig"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCaravanReachConfig")
	public void caravanRoadReachConfigLimitsTheHalf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper, new BlockPos(14, 2, 14));
		BlockPos other = a.offset(0, 0, -400);
		int was = CaravanRoads.REACH;
		Leftovers.after(helper, () -> CaravanRoads.REACH = was);
		villages(helper, a, List.of(other), true);
		CaravanRoads.REACH = 100;
		CaravanRoads.plan(level, a, hall(helper, a));
		CityPlan.Road half = CaravanRoads.roadTo(hall(helper, a).plan(), a, other);
		helper.assertTrue(half != null, "no half planned with the reach at 100");
		helper.assertTrue(Math.abs(flat(a, lastPoint(half, a)) - 100) <= 1.5, "with the reach at 100 the half goes " + flat(a, lastPoint(half, a)));
		CaravanRoads.REACH = was;
		hall(helper, a).setPlan(CityPlan.EMPTY);
		CaravanRoads.plan(level, a, hall(helper, a));
		half = CaravanRoads.roadTo(hall(helper, a).plan(), a, other);
		helper.assertTrue(half != null && Math.abs(flat(a, lastPoint(half, a)) - 200) <= 1.5,
			"with the reach at 256 a half towards a village 400 away doesn't go to halfway: " + (half == null ? null : lastPoint(half, a)));
		helper.succeed();
	}

	/** Villages on the list but no trade route between them: no road; the route started, a half. */
	//$ gametest_ticks_batch AREA '20' '"qaCaravanNoRoute"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCaravanNoRoute")
	public void noRouteNoRoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper, new BlockPos(14, 2, 14));
		BlockPos other = a.offset(0, 0, -150);
		Caravans.Data data = villages(helper, a, List.of(other), false);
		CaravanRoads.plan(level, a, hall(helper, a));
		helper.assertTrue(hall(helper, a).plan().roads().isEmpty(), "a road planned with no trade route: " + hall(helper, a).plan().roads());
		helper.assertTrue(data.half(a, other) == null, "a half on the caravans' list with no route");
		helper.assertTrue(data.toggleRoute(a, other), "setup: no route");
		CaravanRoads.plan(level, a, hall(helper, a));
		helper.assertTrue(CaravanRoads.roadTo(hall(helper, a).plan(), a, other) != null, "no half once the route is on");
		helper.succeed();
	}

	/**
	 * The style of the zone it starts from: the hall's cell in a zone styled Dark Oak gives the half Dark Oak; the zone
	 * moved off the hall's cell (only far corner cells), the half isn't Dark Oak.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaCaravanStyle"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCaravanStyle")
	public void theHalfTakesTheStyleOfTheZoneItStartsFrom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper, new BlockPos(14, 2, 14));
		BlockPos other = a.offset(0, 0, -200);
		villages(helper, a, List.of(other), true);
		BitSet round = new BitSet();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				round.set(CityPlan.cellAt(a, a.offset(dx, 0, dz)));
			}
		}
		CityPlan zoned = CityPlan.EMPTY.addZone("homes", "Homes 1", "dark_oak");
		helper.assertTrue(zoned != null, "setup: no zone");
		hall(helper, a).setPlan(zoned.paint(0, round));
		CaravanRoads.plan(level, a, hall(helper, a));
		CityPlan.Road half = CaravanRoads.roadTo(hall(helper, a).plan(), a, other);
		helper.assertTrue(half != null, "no half planned in a zoned village");
		helper.assertValueEqual(half.style(), "dark_oak", "the half's style, starting in a Dark Oak zone");
		// the zone elsewhere: not where the half starts
		BitSet far = new BitSet();
		far.set(CityPlan.cellAt(a, a.offset(40, 0, 40)));
		hall(helper, a).setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "dark_oak").paint(0, far));
		CaravanRoads.plan(level, a, hall(helper, a));
		half = CaravanRoads.roadTo(hall(helper, a).plan(), a, other);
		helper.assertTrue(half != null && !half.style().equals("dark_oak"), "the half took the style of a zone it doesn't start from: " + half);
		helper.succeed();
	}

	/**
	 * A street from the end of its nearest road: the village's own street runs 20 blocks east from the hall, and the
	 * other village is east; once the street's way is found the half starts at its far end, not at the hall.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCaravanFromRoad"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCaravanFromRoad")
	public void theHalfStartsFromTheEndOfTheNearestRoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper, new BlockPos(4, 2, 14));
		BlockPos other = a.offset(300, 0, 0);
		helper.runAfterDelay(2, () -> {
			villages(helper, a, List.of(other), true);
			CityPlan.Road street = new CityPlan.Road(List.of(new BlockPos(0, 0, 3), new BlockPos(20, 0, 3)), CityPlan.Road.STREET, "", true);
			hall(helper, a).setPlan(CityPlan.EMPTY.addRoad(street));
			Roads.routeNow(level, a);
			CityPlan.Road drawn = hall(helper, a).plan().roads().get(0);
			helper.assertTrue(drawn.routed() && !drawn.route().isEmpty(), "setup: the street's way wasn't found");
			BlockPos far = drawn.route().stream().map(p -> p.offset(a)).max(java.util.Comparator.comparingInt(BlockPos::getX)).orElseThrow();
			CaravanRoads.plan(level, a, hall(helper, a));
			CityPlan.Road half = CaravanRoads.roadTo(hall(helper, a).plan(), a, other);
			helper.assertTrue(half != null, "no half planned from a village with a street");
			BlockPos start = half.points().get(0).offset(a);
			helper.assertTrue(flat(start, far) <= 2, "the half starts at " + start + ", not at the end of the street (" + far + ")");
			helper.succeed();
		});
	}

	/**
	 * Every sentence a player reads: both chronicles' "The road to X is finished" (the spec's words), the milestone line,
	 * and the milestone sign's three lines, each rendered with real arguments and the same as en_us.json.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaCaravanSentences"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaCaravanSentences")
	public void everySentenceReadsAsWritten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos a = ground(helper, new BlockPos(4, 2, 14));
		BlockPos b = helper.absolutePos(new BlockPos(24, 2, 14));
		level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), Block.UPDATE_ALL);
		BlockPos far = a.offset(0, 0, -900);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.remove(a);
			data.remove(b);
			data.remove(far);
		});
		data.setWants(a, Component.literal("Ashford"), List.of());
		data.setWants(b, Component.literal("Bramble"), List.of());
		data.setWants(far, Component.literal("Farhollow"), List.of());
		helper.assertTrue(data.toggleRoute(a, b) && data.toggleRoute(a, far), "setup: no routes");
		// both halves built and meeting at the middle (as the builders leave them on the caravans' list)
		BlockPos mid = a.offset(10, 0, 0);
		data.setHalf(a, b, new Caravans.Half(mid, true, false, false));
		data.setHalf(b, a, new Caravans.Half(mid, true, false, false));
		// Ashford's half towards Farhollow built and ending short, 644 blocks from it
		BlockPos end = a.offset(0, 0, -256);
		data.setHalf(a, far, new Caravans.Half(end, true, true, false));
		CaravanRoads.plan(level, a, hall(helper, a));
		helper.assertTrue(line(hall(helper, a), "The road to Bramble is finished"), "Ashford's chronicle: " + lines(hall(helper, a)));
		helper.assertTrue(line(hall(helper, b), "The road to Ashford is finished"), "Bramble's chronicle: " + lines(hall(helper, b)));
		helper.assertTrue(line(hall(helper, a), "Our road towards Farhollow is built; it ends at a milestone, 644 blocks from there"),
			"the milestone's line: " + lines(hall(helper, a)));
		// the milestone's sign
		CompoundTag tag = CaravanRoads.signText(Component.literal("Farhollow"), 644);
		SignText text = SignText.DIRECT_CODEC.parse(NbtOps.INSTANCE, tag.get("front_text")).getOrThrow();
		helper.assertValueEqual(text.getMessage(0, false).getString(), "To", "the sign's first line");
		helper.assertValueEqual(text.getMessage(1, false).getString(), "Farhollow", "the sign's second line");
		helper.assertValueEqual(text.getMessage(2, false).getString(), "644 blocks", "the sign's third line");
		helper.assertTrue(tag.getBoolean("is_waxed"), "the milestone's sign can be edited");
		helper.succeed();
	}

	private static List<String> lines(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CARAVAN).map(e -> e.text().getString()).toList();
	}

	private static boolean line(VillageHallBlockEntity entity, String text) {
		return lines(entity).contains(text);
	}
}
