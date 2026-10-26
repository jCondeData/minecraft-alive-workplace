package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1025-1838), B283 by another way in: the tower's builder dies with its roof on (a raid). His site is
 * dropped with him ("keep what was built"), so the half tower has neither an open site nor a finished record, and laid
 * out again it sits on its own roof. Spec: every build started finishes, and no tower goes up on another; the next free
 * builder should finish the tower where it stands (its own placement), not start a second one on its roof.
 */
public class QaWallTowerBugGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch AREA '80' '"qaWallBugDeathRound"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaWallBugDeathRound")
	public void aTowersBuilderKilledWithTheRoofOnGetsNoSecondTowerOnTop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaWallTowerGameTests.Village v = QaWallTowerGameTests.village(helper);
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			Walls.Piece tower = Walls.pieces(level, v.hall()).get(0);
			helper.assertTrue(tower.kind() == Walls.Kind.TOWER, "setup: the first piece is a " + tower.kind());
			Villager builder = v.builders().get(0);
			BuildSite site = Builders.start(level, builder, v.owner().getUUID(), "", tower.blueprint(), tower.placement());
			site.setStewardHall(v.hall());
			site.setLevelGround(false);
			BuildPlan plan = site.plan(level);
			for (BuildPlan.Stage stage : java.util.List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (!step.state().is(Blocks.LADDER)) {
						level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS);
					}
				}
			}
			builder.kill();
			BlockPos table = new BlockPos(18, 2, 18);
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			Villager next = helper.spawn(EntityType.VILLAGER, table.east());
			Builders.employ(level, next, helper.absolutePos(table));
			helper.runAfterDelay(5, () -> {
				boolean on = Walls.ENABLED;
				Walls.ENABLED = true;
				try {
					helper.assertTrue(!builder.isAlive(), "setup: the builder didn't die");
					String siteAfterDeath = BuildSiteManager.get(level).get(site.id()) == null ? "dropped" : "kept";
					for (int round = 0; round < 3; round++) {
						Walls.round(level, v.hall(), v.entity());
					}
					BoundingBox t = tower.box();
					for (BuildSite other : BuildSiteManager.get(level).all()) {
						BoundingBox box = BlueprintLibrary.get(level, other.structure()).map(b -> BlueprintOutline.bounds(other.placement(), b.size())).orElse(null);
						if (box == null || other.id().equals(site.id())) {
							continue;
						}
						boolean over = box.minX() <= t.maxX() && box.maxX() >= t.minX() && box.minZ() <= t.maxZ() && box.maxZ() >= t.minZ();
						helper.assertTrue(!over || other.placement().equals(tower.placement()), "the tower's builder died with its roof on (his site "
							+ siteAfterDeath + "): a second " + other.structure().getPath() + " opened at " + other.placement().origin().toShortString()
							+ " on top of the half-built tower at " + tower.placement().origin().toShortString());
					}
					org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa").info("[QA B283] after the death (site {}): {} sites, none on top of the tower",
						siteAfterDeath, BuildSiteManager.get(level).all().size());
				} finally {
					Walls.ENABLED = on;
				}
				helper.succeed();
			});
		} finally {
			Walls.ENABLED = was;
		}
	}

	/**
	 * B488 with a restart: the tower's builder dies with the roof on, the server saves and loads the sites, a new builder
	 * is hired at the same bench; the hall's round hands him the half-built tower (nothing new opens) and he finishes it
	 * where it stands, every ladder up, the only site there.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaWallBugDeathReload"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaWallBugDeathReload")
	public void aTowerWhoseBuilderDiedIsFinishedByTheNextAfterARestart(GameTestHelper helper) {
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		QaWallTowerGameTests.Village v = QaWallTowerGameTests.village(helper);
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		Walls.Piece tower;
		java.util.UUID id;
		java.util.List<BuildPlan.Step> ladders;
		Villager builder = v.builders().get(0);
		try {
			tower = Walls.pieces(level, v.hall()).get(0);
			helper.assertTrue(tower.kind() == Walls.Kind.TOWER, "setup: the first piece is a " + tower.kind());
			BuildSite site = Builders.start(level, builder, v.owner().getUUID(), "", tower.blueprint(), tower.placement());
			site.setStewardHall(v.hall());
			site.setLevelGround(false);
			id = site.id();
			BuildPlan plan = site.plan(level);
			ladders = plan.steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.LADDER)).toList();
			for (BuildPlan.Stage stage : java.util.List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (!step.state().is(Blocks.LADDER)) {
						level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS);
					}
				}
			}
		} finally {
			Walls.ENABLED = was;
		}
		builder.kill();
		Villager[] next = {null};
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!builder.isAlive(), "setup: the builder didn't die");
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new net.minecraft.nbt.CompoundTag(), registries), registries));
			BuildSite kept = BuildSiteManager.get(level).get(id);
			helper.assertTrue(kept != null && kept.builder() == null, "the half-built tower's site after the death and a restart: "
				+ (kept == null ? "dropped" : "still on " + kept.builder()));
			BlockPos table = new BlockPos(10, 2, 14); // the dead builder's table, by the stocked chest
			next[0] = helper.spawn(EntityType.VILLAGER, table.east());
			Builders.employ(level, next[0], helper.absolutePos(table));
			boolean on = Walls.ENABLED;
			Walls.ENABLED = true;
			try {
				for (int round = 0; round < 3; round++) {
					Walls.round(level, v.hall(), v.entity());
				}
			} finally {
				Walls.ENABLED = on;
			}
			helper.assertTrue(next[0].getUUID().equals(kept.builder()), "the next builder wasn't given the half-built tower (on it: " + kept.builder() + ")");
			assertOnlyTheTower(helper, level, tower, id, "after the rounds");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(next[0] != null, "the next builder isn't hired yet");
			BuildSite kept = BuildSiteManager.get(level).get(id);
			helper.assertTrue(kept == null, "the tower isn't finished yet (stage " + (kept == null ? null : kept.stage()) + ")");
			long up = ladders.stream().filter(s -> level.getBlockState(s.pos()).is(Blocks.LADDER)).count();
			helper.assertTrue(up == ladders.size(), up + " of the tower's " + ladders.size() + " ladders are up");
			helper.assertTrue(Walls.stands(level, tower), "the tower isn't recorded as standing");
			assertOnlyTheTower(helper, level, tower, id, "after it was finished");
		});
	}

	private static void assertOnlyTheTower(GameTestHelper helper, ServerLevel level, Walls.Piece tower, java.util.UUID own, String when) {
		BoundingBox t = tower.box();
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			BoundingBox box = BlueprintLibrary.get(level, other.structure()).map(b -> BlueprintOutline.bounds(other.placement(), b.size())).orElse(null);
			if (box == null || other.id().equals(own)) {
				continue;
			}
			boolean over = box.minX() <= t.maxX() && box.maxX() >= t.minX() && box.minZ() <= t.maxZ() && box.maxZ() >= t.minZ();
			helper.assertTrue(!over, when + ": a second " + other.structure().getPath() + " opened at " + other.placement().origin().toShortString()
				+ " on the tower at " + tower.placement().origin().toShortString());
		}
	}
}
