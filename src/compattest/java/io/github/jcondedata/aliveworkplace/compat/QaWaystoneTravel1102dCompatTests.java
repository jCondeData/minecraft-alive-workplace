package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for ROADMAP 41.3 (lane-d-1102-0108), written from the spec ("between two villages that both have a waystone,
 * caravans and settlers walk to their stone and step out at the other village's; if either stone is missing or broken,
 * they walk as today"), with the real Waystones mod. The 41.3 tests break only the far stone; these break the near one,
 * break and rebuild a stone mid-trip, and send settlers where going by waystone would be the longer way.
 */
public class QaWaystoneTravel1102dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	private static void villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		cleanUp(helper);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		for (Object[] v : new Object[][] {{HALL_A, "Ashford", PLAZA_A}, {HALL_B, "Brookmere", PLAZA_B}}) {
			helper.setBlock((BlockPos) v[0], ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity hall = helper.getBlockEntity((BlockPos) v[0]);
			hall.setCustomName(Component.literal((String) v[1]));
			hall.setOwner(owner, "Jesse");
			plaza(helper, (BlockPos) v[2]);
		}
		VillageWaystones.round(level, a(helper));
		VillageWaystones.round(level, b(helper));
		helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the two stones don't stand");
	}

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement, UUID.randomUUID());
	}

	/** The stone of the plaza at {@code plaza} set back as a finished plaza has it (the builder's repair). */
	private static void rebuildStone(GameTestHelper helper, BlockPos plaza) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(plaza), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		BlockPos stone = stone(helper, plaza);
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (step.pos().equals(stone) || step.pos().equals(stone.above())) {
					level.setBlock(step.pos(), step.state(), 3);
					VillageWaystones.placed(level, step.pos(), step.state()); // as BuilderWork does for every block it sets
					if (step.secondaryPos() != null && step.secondaryState() != null) {
						level.setBlock(step.secondaryPos(), step.secondaryState(), 3);
					}
				}
			}
		}
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static void breakStone(GameTestHelper helper, BlockPos plaza) {
		BlockPos s = stone(helper, plaza);
		helper.getLevel().setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(s, Blocks.AIR.defaultBlockState(), 3);
	}

	private static Caravans.Data trade(GameTestHelper helper) {
		helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
		helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		return Caravans.Data.get(helper.getLevel());
	}

	private static void route(GameTestHelper helper, Caravans.Data data) {
		data.setWants(a(helper), Component.literal("Ashford"), List.of());
		data.setWants(b(helper), Component.literal("Brookmere"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(a(helper), b(helper)), "setup: no route");
	}

	private static Caravans.Shipment send(GameTestHelper helper, Caravans.Data data) {
		data.sent(a(helper), -1);
		Caravans.round(helper.getLevel(), a(helper), null);
		return data.onTheRoad().stream().filter(s -> s.from().equals(a(helper)) && s.to().equals(b(helper))).reduce((x, y) -> y)
			.orElseThrow(() -> new GameTestAssertException("no caravan left Ashford: " + data.onTheRoad()));
	}

	private static List<Caravans.Shipment> toBrookmere(GameTestHelper helper, Caravans.Data data) {
		return data.onTheRoad().stream().filter(s -> s.from().equals(a(helper)) && s.to().equals(b(helper))).toList();
	}

	private static boolean said(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().anyMatch(e -> e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			BlockPos hall = helper.absolutePos(at);
			data.remove(hall);
			VillageWaystones.Data.get(level).forget(hall);
		}
		RealmData.get(level.getServer()).removeOrder(GlobalPos.of(level.dimension(), a(helper)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
			BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(plaza), Rotation.NONE, Mirror.NONE));
		}
	}

	private static void start(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		villages(helper);
	}

	/**
	 * "If either stone is broken, they walk": the caravan's own village's stone (Ashford's, where it stepped in) broken
	 * mid-trip, it can't step out at Brookmere either: it walks the whole road from then, its goods whole, and the
	 * chronicle says so.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWaystoneHomeStoneBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaystoneHomeStoneBroken")
	public void aCaravanWhoseHomeStoneBreaksMidTripWalksTheRoad(GameTestHelper helper) {
		start(helper);
		Caravans.Data data = trade(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				route(helper, data);
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(s.byWaystone(), "setup: the caravan doesn't go by waystone: " + s);
				breakStone(helper, PLAZA_A);
				helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "a broken home stone still links the villages");
				data.hurry(b(helper));
				long now = level.getGameTime();
				Caravans.round(level, b(helper), null);
				List<Caravans.Shipment> road = toBrookmere(helper, data);
				long trip = Caravans.trip(level, a(helper), b(helper), data);
				helper.assertTrue(road.size() == 1 && !road.get(0).byWaystone() && road.get(0).arrives() == now + trip,
					"with Ashford's stone broken the caravan should walk the road (" + trip + " ticks from now): " + road);
				helper.assertTrue(road.get(0).goods().stream().mapToInt(ItemStack::getCount).sum() == 32, "it lost goods: " + road.get(0).goods());
				helper.assertTrue(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked"), "Brookmere's chronicle doesn't say it walked");
				helper.assertFalse(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_came"), "it stepped out with Ashford's stone broken");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * The stone is checked when the caravan steps out: Brookmere's stone broken while the caravan is still on its way
	 * changes nothing before it is due, and set back before then (the builder's repair), the caravan steps out of it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaWaystoneRepaired"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaWaystoneRepaired")
	public void aStoneRepairedBeforeTheCaravanIsDueStillLetsItStepOut(GameTestHelper helper) {
		start(helper);
		Caravans.Data data = trade(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				route(helper, data);
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(s.byWaystone() && s.arrives() > level.getGameTime() + 1, "setup: the caravan doesn't go by waystone: " + s);
				breakStone(helper, PLAZA_B);
				Caravans.round(level, b(helper), null); // not yet due
				List<Caravans.Shipment> road = toBrookmere(helper, data);
				helper.assertTrue(road.size() == 1 && road.get(0).byWaystone() && road.get(0).arrives() == s.arrives(),
					"a caravan not yet due was sent walking by a stone broken ahead of it: " + road + ", was " + s);
				helper.assertFalse(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked"), "the chronicle says it walked before it was due");

				rebuildStone(helper, PLAZA_B);
				helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the repaired stone doesn't link the villages");
				data.hurry(b(helper));
				Caravans.round(level, b(helper), null);
				helper.assertTrue(toBrookmere(helper, data).isEmpty(), "the caravan is still on the road: " + toBrookmere(helper, data));
				helper.assertTrue(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_came"), "it didn't step out of the repaired stone");
				helper.assertFalse(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked"), "it walked with both stones standing");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * The step out on the very tick the caravan is due (not hurried: its own 30 seconds run out), Brookmere's stone broken
	 * on the way: it doesn't step out of the broken stone, it walks the road from then.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaWaystoneDueTick"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaWaystoneDueTick")
	public void aCaravanDueThisVeryTickWithABrokenStoneWalks(GameTestHelper helper) {
		start(helper);
		Caravans.Data data = trade(helper);
		Caravans.Shipment[] sent = new Caravans.Shipment[1];
		helper.runAfterDelay(2, () -> {
			route(helper, data);
			sent[0] = send(helper, data);
			helper.assertTrue(sent[0].byWaystone(), "setup: the caravan doesn't go by waystone: " + sent[0]);
			breakStone(helper, PLAZA_B);
		});
		helper.onEachTick(() -> {
			ServerLevel level = helper.getLevel();
			if (sent[0] == null || level.getGameTime() < sent[0].arrives()) {
				return;
			}
			try {
				long now = level.getGameTime();
				helper.assertTrue(now == sent[0].arrives(), "setup: missed the tick it was due (" + now + ", due " + sent[0].arrives() + ")");
				Caravans.round(level, b(helper), null);
				List<Caravans.Shipment> road = toBrookmere(helper, data);
				long trip = Caravans.trip(level, a(helper), b(helper), data);
				helper.assertTrue(road.size() == 1 && !road.get(0).byWaystone() && road.get(0).arrives() == now + trip,
					"due this tick with Brookmere's stone broken, the caravan should walk the road (" + trip + " ticks): " + road);
				helper.assertFalse(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_came"), "it stepped out of a broken stone");
				helper.assertTrue(said(helper, HALL_B, "chronicle.aliveworkplace.waystone_walked"), "Brookmere's chronicle doesn't say it walked");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/** Two settlers called to Ashford's hall for a colony at {@code spot}; returns the order's mother. */
	private static GlobalPos settlers(GameTestHelper helper, BlockPos spot, Villager[] out) {
		ServerLevel level = helper.getLevel();
		Villager dara = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		Villager tomas = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		out[0] = dara;
		out[1] = tomas;
		GlobalPos mother = GlobalPos.of(level.dimension(), a(helper));
		RealmData.get(level.getServer()).putOrder(new RealmData.Order(mother, spot, Optional.of(Component.literal("Newbrook")), RealmData.MUSTER, level.getGameTime(), 0, 0,
			level.getGameTime(), List.of(dara.getUUID(), tomas.getUUID()), Optional.of(dara.getUUID()), Map.of(), List.of()));
		return mother;
	}

	/**
	 * Settlers go by waystone only when the other village's stone is nearer their spot than home: for a colony on the far
	 * side of Ashford from Brookmere they walk the whole road from home (no village to go through, the walking journey,
	 * nobody gone into Ashford's stone).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneSettlersAway"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneSettlersAway")
	public void settlersForASpotAwayFromTheOtherStoneWalkFromHome(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		BlockPos spot = a(helper).offset(-300, 0, -300);
		Villager[] who = new Villager[2];
		GlobalPos mother = settlers(helper, spot, who);
		RealmData realms = RealmData.get(level.getServer());
		helper.onEachTick(() -> {
			RealmData.Order order = realms.orders().stream().filter(o -> o.mother().equals(mother)).findFirst().orElse(null);
			if (order == null) {
				return;
			}
			if (order.state().equals(RealmData.LEAVING)) {
				helper.assertTrue(order.via().isEmpty(), "settlers for a spot away from Brookmere go through its stone: " + order.via());
				BlockPos edge = Settlers.edge(level, order);
				helper.assertFalse(edge.equals(VillageWaystones.beside(level, stone(helper, PLAZA_A), a(helper))),
					"settlers walking the road are sent to Ashford's stone");
				return;
			}
			if (!order.state().equals(RealmData.ON_ROAD)) {
				return;
			}
			try {
				helper.assertTrue(order.via().isEmpty(), "on the road by a stone farther from the spot than home: " + order.via());
				helper.assertTrue(order.settlers().size() == 2 && !who[0].isAlive() && !who[1].isAlive(), "the settlers didn't go: " + order.settlers().size());
				long left = order.arrives() - level.getGameTime();
				long walking = Settlers.journey(order);
				helper.assertTrue(left <= walking && left >= walking - 2, "the walking journey: " + left + " ticks left, expected " + walking);
				helper.assertTrue(Settlers.stepOut(level, order) == order, "a walking order changed at stepOut");
				helper.assertFalse(said(helper, HALL_A, "chronicle.aliveworkplace.settlers_walked"), "the chronicle says a waystone was broken");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * "Nearer the spot than the mother village", from both sides of the edge: a spot exactly as far from Brookmere's
	 * stone as from Ashford's hall (321 east and 300 north of the hall, the stone 21 east and 21 south of it) is no
	 * nearer, so the settlers walk; one block further east, the stone is the nearer and they go through it.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaWaystoneSettlersTie"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaWaystoneSettlersTie")
	public void settlersGoByWaystoneOnlyWhenTheStoneIsStrictlyNearer(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		try {
			BlockPos s = stone(helper, PLAZA_B);
			helper.assertTrue(s.getX() - a(helper).getX() == 21 && s.getZ() - a(helper).getZ() == 21, "setup: Brookmere's stone isn't 21 by 21 from Ashford's hall: " + s);
			GlobalPos mother = GlobalPos.of(level.dimension(), a(helper));
			for (int east : new int[] {321, 322}) {
				BlockPos spot = a(helper).offset(east, 0, -300);
				RealmData.Order order = new RealmData.Order(mother, spot, Optional.of(Component.literal("Newbrook")), RealmData.MUSTER, level.getGameTime(), 0, 0,
					level.getGameTime(), List.of(), Optional.empty(), Map.of(), List.of());
				double home = Math.hypot(spot.getX() - a(helper).getX(), spot.getZ() - a(helper).getZ());
				double byStone = Math.hypot(spot.getX() - s.getX(), spot.getZ() - s.getZ());
				Optional<BlockPos> via = Settlers.via(level, order);
				if (east == 321) {
					helper.assertTrue(home == byStone, "setup: not a tie: " + home + " and " + byStone);
					helper.assertTrue(via.isEmpty(), "a stone exactly as far from the spot as home is taken: " + via);
				} else {
					helper.assertTrue(byStone < home, "setup: the stone isn't nearer: " + byStone + " and " + home);
					helper.assertTrue(via.equals(Optional.of(b(helper))), "a stone nearer the spot isn't taken: " + via);
				}
			}
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * Settlers whose own village's stone is broken before they set out walk the whole road from home, though Brookmere's
	 * stone stands nearer their spot.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneSettlersHomeBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneSettlersHomeBroken")
	public void settlersWhoseHomeStoneIsBrokenWalkFromHome(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		BlockPos spot = b(helper).offset(300, 0, 300);
		breakStone(helper, PLAZA_A);
		helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "setup: a broken home stone still links the villages");
		Villager[] who = new Villager[2];
		GlobalPos mother = settlers(helper, spot, who);
		RealmData realms = RealmData.get(level.getServer());
		helper.onEachTick(() -> {
			RealmData.Order order = realms.orders().stream().filter(o -> o.mother().equals(mother)).findFirst().orElse(null);
			if (order == null || !order.state().equals(RealmData.ON_ROAD)) {
				return;
			}
			try {
				helper.assertTrue(order.via().isEmpty(), "settlers go through Brookmere with Ashford's stone broken: " + order.via());
				helper.assertTrue(order.settlers().size() == 2, "the settlers didn't go: " + order.settlers().size());
				long left = order.arrives() - level.getGameTime();
				long walking = Settlers.journey(order);
				helper.assertTrue(left <= walking && left >= walking - 2, "the walking journey: " + left + " ticks left, expected " + walking);
				helper.assertTrue(Settlers.via(level, order).isEmpty(), "a broken home stone still offers a way through");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}
}
