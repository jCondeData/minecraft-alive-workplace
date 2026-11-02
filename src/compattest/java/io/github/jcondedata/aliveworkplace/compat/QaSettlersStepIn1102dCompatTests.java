package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA for ROADMAP 41.3 (lane-d-1102-0408), from the spec: settlers going by waystone "walk to their stone and step out at
 * the other village's"; "if either stone is missing or broken, they walk as today". These pin the moment the settlers
 * step in, while a player watches (so they don't just vanish out of sight): only once every one of them stands at the
 * stone, never with one still on the way, never at a stone when they are walking the road, and never once the far stone
 * is broken; and that settlers held up on the way leave all the same when their time is up.
 */
public class QaSettlersStepIn1102dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlockPos b(GameTestHelper helper) {
		return helper.absolutePos(HALL_B);
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
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

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
		RealmData.get(level.getServer()).removeOrder(GlobalPos.of(level.dimension(), a(helper)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
			BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(plaza), Rotation.NONE, Mirror.NONE));
		}
	}

	/** Ashford and Brookmere, each with its plaza and standing stone, linked; a player watching beside Ashford's hall. */
	private static void villages(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, a(helper), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement()); // since B620 a plaza left nearby would be Ashford's too
			}
		}
		// a plaza another batch's builder left going up nearby (WaystonesCompatTests' waystonePlaza) would be Ashford's too,
		// its stone standing: Ashford's own stone broken, the villages stayed linked through it
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(a(helper), 160)) {
				manager.remove(site.id());
			}
		}
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
		ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 8)));
		watcher.moveTo(at.x, at.y, at.z);
	}

	/** A settler standing still (no AI, so nobody walks them) at {@code at}. */
	private static Villager settler(GameTestHelper helper, Vec3 at) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		v.setNoAi(true);
		v.moveTo(at.x, at.y, at.z);
		return v;
	}

	/** An order already LEAVING since {@code since} ticks ago, by Brookmere's stone or ({@code via} false) walking. */
	private static GlobalPos leaving(GameTestHelper helper, boolean via, long since, Villager... who) {
		ServerLevel level = helper.getLevel();
		GlobalPos mother = GlobalPos.of(level.dimension(), a(helper));
		long now = level.getGameTime();
		BlockPos spot = b(helper).offset(300, 0, 300);
		RealmData.Order order = new RealmData.Order(mother, spot, Optional.of(Component.literal("Newbrook")), RealmData.LEAVING, now - since, 0, 0,
			now - since - 100, java.util.Arrays.stream(who).map(Villager::getUUID).toList(), Optional.of(who[0].getUUID()), Map.of(), List.of())
			.withVia(via ? Optional.of(b(helper)) : Optional.empty());
		RealmData.get(level.getServer()).putOrder(order);
		return mother;
	}

	private static RealmData.Order order(GameTestHelper helper, GlobalPos mother) {
		return RealmData.get(helper.getLevel().getServer()).orders().stream().filter(o -> o.mother().equals(mother)).findFirst().orElse(null);
	}

	private static boolean left(GameTestHelper helper, GlobalPos mother) {
		RealmData.Order o = order(helper, mother);
		return o != null && !o.state().equals(RealmData.LEAVING);
	}

	/** The spot beside Ashford's stone where settlers going by waystone step in. */
	private static Vec3 stepIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos s = VillageWaystones.stoneOf(level, a(helper)).orElseThrow(() -> new GameTestAssertException("setup: Ashford has no stone"));
		return Vec3.atBottomCenterOf(VillageWaystones.beside(level, s, a(helper)));
	}

	/**
	 * Two settlers going by waystone: one at the stone, the other still three blocks short of it. With a player watching,
	 * nobody steps in for 60 ticks (not one without the other); once the second comes within two blocks, both go.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSettlersStepInAll"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSettlersStepInAll")
	public void settlersStepInOnlyOnceEveryOneStandsAtTheStone(GameTestHelper helper) {
		villages(helper);
		Vec3 at = stepIn(helper);
		Vec3 behind = at.add(at.x > stone(helper, PLAZA_A).getX() + 0.5 ? 3 : -3, 0, 0);
		Villager dara = settler(helper, at);
		Villager tomas = settler(helper, behind);
		GlobalPos mother = leaving(helper, true, 0, dara, tomas);
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> {
			long t = helper.getLevel().getGameTime() - start;
			try {
				if (t < 60) {
					helper.assertFalse(left(helper, mother), "settlers stepped in at tick " + t + " with one still 3 blocks from the stone");
					helper.assertTrue(dara.isAlive() && tomas.isAlive(), "a settler vanished before both reached the stone");
					return;
				}
				if (t == 60) {
					tomas.moveTo(at.x + (behind.x > at.x ? 2 : -2), at.y, at.z);
					return;
				}
				if (t < 80) {
					return;
				}
				helper.assertTrue(left(helper, mother), "both settlers stand at the stone (2 blocks apart), yet they haven't stepped in after 20 ticks: "
					+ order(helper, mother));
				helper.assertTrue(order(helper, mother).settlers().size() == 2, "not both settlers went: " + order(helper, mother).settlers().size());
			} catch (GameTestAssertException e) {
				cleanUp(helper);
				throw e;
			}
			cleanUp(helper);
			helper.succeed();
		});
	}

	/**
	 * Settlers walking the whole road (no village to go through) who happen to stand by their own village's stone don't
	 * vanish into it while a player watches: the stone is nothing to them.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaSettlersWalkingPastStone"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaSettlersWalkingPastStone")
	public void settlersWalkingTheRoadDontStepIntoTheStone(GameTestHelper helper) {
		villages(helper);
		Vec3 at = stepIn(helper);
		Villager dara = settler(helper, at);
		Villager tomas = settler(helper, at.add(0, 0, 0.5));
		GlobalPos mother = leaving(helper, false, 0, dara, tomas);
		helper.runAfterDelay(80, () -> {
			try {
				helper.assertFalse(left(helper, mother), "settlers walking the road vanished into their stone in sight of a player: " + order(helper, mother));
				helper.assertTrue(dara.isAlive() && tomas.isAlive(), "a walking settler vanished at the stone");
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * Brookmere's stone broken while the settlers stand at Ashford's: "if either stone is broken, they walk as today", so
	 * they don't step in, and they're sent out on the road instead of to the stone.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaSettlersFarStoneBroken"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaSettlersFarStoneBroken")
	public void settlersAtTheStoneDontStepInOnceTheFarStoneBreaks(GameTestHelper helper) {
		villages(helper);
		ServerLevel level = helper.getLevel();
		Vec3 at = stepIn(helper);
		BlockPos far = stone(helper, PLAZA_B);
		level.setBlock(far.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
		helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "setup: a broken far stone still links the villages");
		Villager dara = settler(helper, at);
		Villager tomas = settler(helper, at.add(0, 0, 0.5));
		GlobalPos mother = leaving(helper, true, 0, dara, tomas);
		helper.runAfterDelay(80, () -> {
			try {
				RealmData.Order order = order(helper, mother);
				helper.assertFalse(left(helper, mother), "settlers stepped into Ashford's stone with Brookmere's broken: " + order);
				BlockPos edge = Settlers.edge(level, order);
				helper.assertTrue(Vec3.atBottomCenterOf(edge).distanceTo(at) > 10,
					"with Brookmere's stone broken, the settlers are still sent to Ashford's stone: " + edge);
			} finally {
				cleanUp(helper);
			}
			helper.succeed();
		});
	}

	/**
	 * Settlers held up short of the stone leave all the same when their time is up ({@link Settlers#LEAVE_TICKS} after
	 * they set out), and not a tick before: ten ticks short, they're still here; at the time, they go.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaSettlersLateAtStone"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaSettlersLateAtStone")
	public void settlersHeldUpShortOfTheStoneLeaveWhenTheirTimeIsUp(GameTestHelper helper) {
		villages(helper);
		Vec3 at = stepIn(helper);
		Vec3 short1 = at.add(at.x > stone(helper, PLAZA_A).getX() + 0.5 ? 5 : -5, 0, 0);
		Villager dara = settler(helper, short1);
		Villager tomas = settler(helper, short1.add(0, 0, 0.5));
		GlobalPos mother = leaving(helper, true, Settlers.LEAVE_TICKS - 40, dara, tomas);
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> {
			long t = helper.getLevel().getGameTime() - start;
			try {
				if (t < 30) {
					helper.assertFalse(left(helper, mother), "settlers 5 blocks short of the stone left at tick " + t + ", " + (40 - t) + " ticks before their time");
					// still called away while they leave, so their work doesn't pull them back from the road
					helper.assertTrue(t < 2 || Settlers.called(dara) && Settlers.called(tomas), "a settler on the way out isn't called away from work at tick " + t);
					return;
				}
				if (t < 50) {
					return;
				}
				helper.assertTrue(left(helper, mother), "settlers held up short of the stone didn't leave when their time was up: " + order(helper, mother));
			} catch (GameTestAssertException e) {
				cleanUp(helper);
				throw e;
			}
			cleanUp(helper);
			helper.succeed();
		});
	}
}
