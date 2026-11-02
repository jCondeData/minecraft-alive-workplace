package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-02), from 41.3's spec and the wiki ("a caravan by waystone walks from the Storehouse to the spot
 * beside the village's waystone and steps into it, or steps out of it and comes in from there"), with the real Waystones
 * mod and a player watching (the caravan is seen only then). No test looked at the seen caravan's walk by waystone:
 * <ul>
 * <li>Leaving by waystone: its party's edge is the spot beside Ashford's stone; a carter 2.5 blocks short of it walks
 * on, at the spot (1.5 blocks, as every party's edge) the party steps in and is gone.</li>
 * <li>Ashford's stone broken before it leaves: the caravan walks the road, its party not by waystone, its edge not by
 * the broken stone.</li>
 * <li>Arriving by waystone: Brookmere's party steps out beside Brookmere's stone and walks in from there.</li>
 * </ul>
 * The village helpers are WaystoneTravelCompatTests' (copied: they are private there).
 */
public class QaCaravanStone1102dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final String CARAVANS = "aliveworkplace_caravans";

	/** Two villages, Ashford and Brookmere, each with its finished plaza and a standing waystone. */
	private static void villages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		cleanUp(helper);
		// Each test here runs in a batch of its own: no other test's village with a standing stone (left by an earlier
		// batch, e.g. WaystonesCompatTests') may be the one settlers pick on the way to their spot.
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
		helper.assertTrue(VillageWaystones.linked(level, a(helper), b(helper)), "setup: the two stones don't stand (" + VillageWaystones.stoneOf(level, a(helper))
			+ ", " + VillageWaystones.stoneOf(level, b(helper)) + ")");
	}

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	/** Sets every block of the plaza at {@code origin} as a finished one stands, and records it finished. */
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

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static void breakStone(GameTestHelper helper, BlockPos plaza) {
		BlockPos s = stone(helper, plaza);
		helper.getLevel().setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(s, Blocks.AIR.defaultBlockState(), 3);
	}

	/** A Storehouse with a chest by each hall, Ashford's with 64 oak logs. */
	private static Caravans.Data trade(GameTestHelper helper) {
		helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
		helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
		chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		return Caravans.Data.get(helper.getLevel());
	}

	/** Brookmere waits for 32 oak logs, and Ashford has a route to it: set after the halls' first rounds, which write their own wants. */
	private static void route(GameTestHelper helper, Caravans.Data data) {
		data.setWants(a(helper), Component.literal("Ashford"), List.of());
		data.setWants(b(helper), Component.literal("Brookmere"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(a(helper), b(helper)), "setup: no route");
	}

	/** Today's caravan from Ashford: sent by Ashford's hall round. */
	private static Caravans.Shipment send(GameTestHelper helper, Caravans.Data data) {
		data.sent(a(helper), -1);
		Caravans.round(helper.getLevel(), a(helper), null);
		return data.onTheRoad().stream().filter(s -> s.from().equals(a(helper)) && s.to().equals(b(helper))).reduce((x, y) -> y)
			.orElseThrow(() -> new GameTestAssertException("no caravan left Ashford: " + data.onTheRoad()));
	}

	private static List<Caravans.Shipment> toBrookmere(GameTestHelper helper, Caravans.Data data) {
		return data.onTheRoad().stream().filter(s -> s.from().equals(a(helper)) && s.to().equals(b(helper))).toList();
	}

	/** Whether the chronicle of the hall at {@code hall} (in the test area) has a line {@code key}. */
	private static boolean said(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().anyMatch(e -> e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	private static Component line(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().map(Chronicle.Entry::text).filter(t -> t.getContents() instanceof TranslatableContents c && c.getKey().equals(key))
			.findFirst().orElseThrow(() -> new GameTestAssertException("no '" + key + "' in the chronicle"));
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Caravans.Data data = Caravans.Data.get(level);
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			BlockPos hall = helper.absolutePos(at);
			data.remove(hall);
			VillageWaystones.Data.get(level).forget(hall);
		}
		RealmData realms = RealmData.get(level.getServer());
		realms.removeOrder(GlobalPos.of(level.dimension(), a(helper)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) { // (else a later test by the same spot finds a plaza built)
			BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(plaza), Rotation.NONE, Mirror.NONE));
		}
	}

	private static boolean sightsWere;

	/** A player standing between the two villages (within both halls' sight range), so the caravans are seen. */
	private static ServerPlayer watcher(GameTestHelper helper) {
		sightsWere = CaravanSights.ENABLED;
		CaravanSights.ENABLED = true; // (switched off for GameTests by the config)
		ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 8)));
		watcher.moveTo(at.x, at.y, at.z);
		return watcher;
	}

	private static void done(GameTestHelper helper, ServerPlayer watcher) {
		ServerLevel level = helper.getLevel();
		CaravanSights.clear(level, a(helper));
		CaravanSights.clear(level, b(helper));
		level.getServer().getPlayerList().remove(watcher);
		CaravanSights.ENABLED = sightsWere;
		cleanUp(helper);
	}

	private static double across(Villager v, BlockPos spot) {
		double dx = v.getX() - (spot.getX() + 0.5);
		double dz = v.getZ() - (spot.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A caravan leaving Ashford by waystone, seen: its party is by waystone and its edge is the spot beside Ashford's stone
	 * (not the village's edge); the carter 2.5 blocks short of that spot walks on; set at the spot, the party steps into
	 * the stone and is gone (the carter and both llamas).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCaravanStoneLeaving1102d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCaravanStoneLeaving1102d")
	public void aSeenCaravanByWaystoneWalksToTheStoneAndStepsIn(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		villages(helper);
		Caravans.Data data = trade(helper);
		ServerPlayer watcher = watcher(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				CaravanSights.clear(level, a(helper));
				route(helper, data);
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(s.byWaystone(), "setup: the caravan doesn't go by waystone: " + s);
				CaravanSights.Party party = CaravanSights.party(level, a(helper));
				helper.assertTrue(party != null, "a caravan leaving with a player watching should be seen setting out, no party is out at Ashford");
				helper.assertTrue(party.waystone() && party.phase() == CaravanSights.Phase.LEAVING,
					"a caravan leaving by waystone: expected its party by waystone and leaving, got waystone " + party.waystone() + ", " + party.phase());
				BlockPos beside = VillageWaystones.beside(level, stone(helper, PLAZA_A), party.yard());
				helper.assertTrue(party.edge().equals(beside),
					"a caravan leaving by waystone walks to the spot beside its stone " + beside.toShortString() + ", its edge is " + party.edge().toShortString());
				Villager carter = party.carter();
				BlockPos edge = party.edge();
				carter.teleportTo(edge.getX() + 0.5 + 2.5, edge.getY(), edge.getZ() + 0.5);
				CaravanSights.tick();
				helper.assertTrue(CaravanSights.party(level, a(helper)) == party && !carter.isRemoved(),
					"a carter 2.5 blocks short of the stone should walk on, the party is " + (CaravanSights.party(level, a(helper)) == null ? "gone" : "replaced"));
				carter.teleportTo(edge.getX() + 0.5, edge.getY(), edge.getZ() + 0.5);
				List<? extends net.minecraft.world.entity.Entity> llamas = List.copyOf(party.llamas());
				CaravanSights.tick();
				helper.assertTrue(CaravanSights.party(level, a(helper)) == null, "at the spot beside the stone the party should step in and be gone, it is still out");
				helper.assertTrue(carter.isRemoved() && llamas.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
					"stepped into the stone, the carter and the llamas should be gone: carter removed " + carter.isRemoved() + ", llamas " + llamas.stream().map(net.minecraft.world.entity.Entity::isRemoved).toList());
			} finally {
				done(helper, watcher);
			}
			helper.succeed();
		});
	}

	/**
	 * Ashford's stone broken before the caravan leaves: it walks the road, and the party seen setting out isn't by
	 * waystone, its edge out on the road (not by the broken stone's spot).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCaravanStoneBroken1102d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCaravanStoneBroken1102d")
	public void aSeenCaravanWithItsStoneBrokenWalksTheRoad(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		villages(helper);
		Caravans.Data data = trade(helper);
		ServerPlayer watcher = watcher(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				CaravanSights.clear(level, a(helper));
				route(helper, data);
				breakStone(helper, PLAZA_A);
				helper.assertTrue(!VillageWaystones.linked(level, a(helper), b(helper)), "setup: with Ashford's stone broken the villages are still linked");
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(!s.byWaystone(), "with Ashford's stone broken the caravan should walk the road, it goes by waystone: " + s);
				CaravanSights.Party party = CaravanSights.party(level, a(helper));
				helper.assertTrue(party != null, "a caravan leaving with a player watching should be seen setting out, no party is out at Ashford");
				helper.assertTrue(!party.waystone(), "with the stone broken the party seen setting out shouldn't be by waystone");
				BlockPos stone = stone(helper, PLAZA_A);
				helper.assertTrue(party.edge().distSqr(stone) > 3 * 3,
					"with the stone broken the party should walk to the village's edge, its edge " + party.edge().toShortString() + " is by the broken stone " + stone.toShortString());
			} finally {
				done(helper, watcher);
			}
			helper.succeed();
		});
	}

	/**
	 * A caravan by waystone arriving at Brookmere, seen: Brookmere's party comes by waystone, its carter stepping out on
	 * the spot beside Brookmere's stone (within 1.5 blocks), and walks in toward the Storehouse yard from there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCaravanStoneArriving1102d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCaravanStoneArriving1102d")
	public void aSeenCaravanByWaystoneStepsOutBesideTheOtherStone(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		villages(helper);
		Caravans.Data data = trade(helper);
		ServerPlayer watcher = watcher(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			try {
				route(helper, data);
				Caravans.Shipment s = send(helper, data);
				helper.assertTrue(s.byWaystone(), "setup: the caravan doesn't go by waystone: " + s);
				CaravanSights.clear(level, a(helper));
				CaravanSights.clear(level, b(helper));
				data.hurry(b(helper));
				Caravans.round(level, b(helper), null);
				helper.assertTrue(toBrookmere(helper, data).isEmpty(), "setup: the caravan is still on the road: " + toBrookmere(helper, data));
				CaravanSights.Party party = CaravanSights.party(level, b(helper));
				helper.assertTrue(party != null, "a caravan arriving with a player watching should be seen coming in, no party is out at Brookmere");
				helper.assertTrue(party.waystone() && party.phase() == CaravanSights.Phase.ARRIVING,
					"a caravan arriving by waystone: expected its party by waystone and arriving, got waystone " + party.waystone() + ", " + party.phase());
				BlockPos beside = VillageWaystones.beside(level, stone(helper, PLAZA_B), party.yard());
				helper.assertTrue(party.edge().equals(beside),
					"a caravan arriving by waystone steps out beside Brookmere's stone " + beside.toShortString() + ", its edge is " + party.edge().toShortString());
				double off = across(party.carter(), beside);
				helper.assertTrue(off <= 1.5, "the carter should step out on the spot beside the stone, he is " + String.format("%.1f", off) + " blocks from it");
			} finally {
				done(helper, watcher);
			}
			helper.succeed();
		});
	}
}
