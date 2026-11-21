package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-d-1121-1309) for B1188, from its spec: with villages on the waystone network, breaking one's Village Hall
 * (its plaza and stone left standing) must not make another village's hall round take that hall-less plaza as its own:
 * each village keeps its own plaza's stone, which keeps its village's name. Here with three villages (Ashford, Brookmere
 * and Cinderford): Brookmere's hall is broken the way a player breaks it, and Cinderford (now the nearest hall to
 * Brookmere's plaza) and Ashford go on with their rounds, in both orders, through a rename and a save and reload of the
 * network's record; then Brookmere's hall put back reclaims its own stone.
 */
public class QaWaystoneHallless1121dCompatTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_compat:huge_area";

	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos HALL_C = new BlockPos(28, 2, 1);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	private static final BlockPos PLAZA_C = new BlockPos(19, 2, 3);
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final List<BlockPos> HALLS = List.of(HALL_A, HALL_B, HALL_C);
	private static final List<BlockPos> PLAZAS = List.of(PLAZA_A, PLAZA_B, PLAZA_C);

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(helper, origin), UUID.randomUUID());
	}

	private static BlockPos stone(GameTestHelper helper, BlockPos plaza) {
		return helper.absolutePos(plaza).offset(STONE);
	}

	private static BlockPos at(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	private static void hall(GameTestHelper helper, BlockPos at, String name, UUID owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner, "Jesse");
	}

	private static String nameAt(GameTestHelper helper, BlockPos stone) {
		return VillageWaystones.EXTENSION.call(x -> x.nameAt(helper.getLevel(), stone), Optional.<String>empty()).orElse("(no waystone)");
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos h : HALLS) {
			VillageWaystones.Data.get(level).forget(at(helper, h));
		}
		for (BlockPos p : PLAZAS) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, p));
		}
		VillageHalls.forgetNearest();
	}

	/** The three villages, each with a hall and a plaza whose stone is raised, all on the network. */
	private static UUID start(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, at(helper, HALL_A), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(at(helper, HALL_A), 160)) {
				manager.remove(site.id());
			}
		}
		cleanUp(helper);
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		hall(helper, HALL_A, "Ashford", owner);
		hall(helper, HALL_B, "Brookmere", owner);
		hall(helper, HALL_C, "Cinderford", owner);
		for (BlockPos p : PLAZAS) {
			plaza(helper, p);
		}
		for (BlockPos h : HALLS) {
			VillageWaystones.round(level, at(helper, h));
		}
		for (int i = 0; i < 3; i++) {
			BlockPos h = at(helper, HALLS.get(i));
			helper.assertTrue(VillageWaystones.stoneOf(level, h).equals(Optional.of(stone(helper, PLAZAS.get(i)))),
				"setup: village " + i + "'s stone is " + VillageWaystones.stoneOf(level, h));
		}
		helper.assertTrue(VillageWaystones.linked(level, at(helper, HALL_A), at(helper, HALL_C)), "setup: Ashford and Cinderford aren't linked");
		return owner;
	}

	private static void ownStones(GameTestHelper helper, String when, String nameA, String nameC) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageWaystones.stoneOf(level, at(helper, HALL_C)).equals(Optional.of(stone(helper, PLAZA_C))),
			when + ": Cinderford's stone is " + VillageWaystones.stoneOf(level, at(helper, HALL_C)) + " (its own " + stone(helper, PLAZA_C)
				+ ", the hall-less Brookmere's " + stone(helper, PLAZA_B) + ")");
		helper.assertTrue(VillageWaystones.stoneOf(level, at(helper, HALL_A)).equals(Optional.of(stone(helper, PLAZA_A))),
			when + ": Ashford's stone is " + VillageWaystones.stoneOf(level, at(helper, HALL_A)));
		helper.assertValueEqual(nameAt(helper, stone(helper, PLAZA_B)), "Brookmere", when + ": the hall-less plaza's stone's name");
		helper.assertValueEqual(nameAt(helper, stone(helper, PLAZA_C)), nameC, when + ": Cinderford's stone's name");
		helper.assertValueEqual(nameAt(helper, stone(helper, PLAZA_A)), nameA, when + ": Ashford's stone's name");
		helper.assertFalse(VillageWaystones.villages(level).contains(at(helper, HALL_B)), when + ": Brookmere, whose hall is gone, is on the network");
		helper.assertTrue(VillageWaystones.linked(level, at(helper, HALL_A), at(helper, HALL_C)), when + ": Ashford and Cinderford aren't linked");
	}

	/**
	 * Brookmere's hall broken (destroyed as a player would): Cinderford, now the nearest hall to Brookmere's plaza, and
	 * Ashford go round several times in both orders, and Cinderford is renamed; each keeps its own stone with its own name,
	 * Brookmere's stone keeps "Brookmere", and Brookmere stays off the network.
	 */
	//$ gametest_batch HUGE '"qaWaystoneHallless1121dThree"'
	@GameTest(template = HUGE, batch = "qaWaystoneHallless1121dThree")
	public void threeVillagesKeepTheirOwnStonesWhenOneHallIsBroken(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		try {
			level.destroyBlock(at(helper, HALL_B), false);
			VillageHalls.forgetNearest();
			helper.assertTrue(VillageHalls.nearest(level, at(helper, PLAZA_B)).equals(Optional.of(at(helper, HALL_C))),
				"setup: Brookmere's plaza's nearest hall is " + VillageHalls.nearest(level, at(helper, PLAZA_B)) + ", not Cinderford");
			for (int i = 0; i < 3; i++) {
				VillageWaystones.round(level, at(helper, HALL_C));
				VillageWaystones.round(level, at(helper, HALL_A));
			}
			ownStones(helper, "after Cinderford's and Ashford's rounds", "Ashford", "Cinderford");
			for (int i = 0; i < 2; i++) {
				VillageWaystones.round(level, at(helper, HALL_A));
				VillageWaystones.round(level, at(helper, HALL_C));
			}
			ownStones(helper, "after Ashford's and Cinderford's rounds", "Ashford", "Cinderford");
			((VillageHallBlockEntity) helper.getBlockEntity(HALL_C)).setCustomName(Component.literal("Cindervale"));
			VillageWaystones.renamed(level, at(helper, HALL_C));
			VillageWaystones.round(level, at(helper, HALL_C));
			ownStones(helper, "after Cinderford was renamed Cindervale", "Ashford", "Cindervale");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * The record of whose plaza is whose survives a save and reload, and Brookmere's hall put back (after Cinderford's
	 * rounds) takes its own stone back, named Brookmere; all three are linked again with their own stones.
	 */
	//$ gametest_batch HUGE '"qaWaystoneHallless1121dRebuilt"'
	@GameTest(template = HUGE, batch = "qaWaystoneHallless1121dRebuilt")
	public void theRecordSurvivesAReloadAndTheRebuiltHallReclaimsItsStone(GameTestHelper helper) {
		UUID owner = start(helper);
		ServerLevel level = helper.getLevel();
		try {
			level.destroyBlock(at(helper, HALL_B), false);
			VillageHalls.forgetNearest();
			VillageWaystones.round(level, at(helper, HALL_C));
			VillageWaystones.round(level, at(helper, HALL_A));
			VillageWaystones.Data data = VillageWaystones.Data.get(level);
			CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
			VillageWaystones.Data back = VillageWaystones.Data.load(tag, level.registryAccess());
			for (int i = 0; i < 3; i++) {
				BlockPos origin = at(helper, PLAZAS.get(i));
				helper.assertValueEqual(back.owner(origin), Optional.of(at(helper, HALLS.get(i))), "after a reload, plaza " + i + "'s village");
				helper.assertValueEqual(back.owner(origin), data.owner(origin), "plaza " + i + "'s village, saved and loaded");
			}
			helper.assertValueEqual(back.stone(at(helper, HALL_C)), Optional.of(stone(helper, PLAZA_C)), "Cinderford's stone after a reload");
			helper.assertValueEqual(back.stone(at(helper, HALL_A)), Optional.of(stone(helper, PLAZA_A)), "Ashford's stone after a reload");
			hall(helper, HALL_B, "Brookmere", owner);
			VillageHalls.forgetNearest();
			VillageWaystones.round(level, at(helper, HALL_C));
			VillageWaystones.round(level, at(helper, HALL_B));
			VillageWaystones.round(level, at(helper, HALL_A));
			for (int i = 0; i < 3; i++) {
				BlockPos h = at(helper, HALLS.get(i));
				helper.assertValueEqual(VillageWaystones.stoneOf(level, h), Optional.of(stone(helper, PLAZAS.get(i))), "village " + i + "'s stone after the rebuild");
			}
			helper.assertValueEqual(nameAt(helper, stone(helper, PLAZA_B)), "Brookmere", "the rebuilt Brookmere's stone's name");
			helper.assertValueEqual(nameAt(helper, stone(helper, PLAZA_C)), "Cinderford", "Cinderford's stone's name");
			helper.assertTrue(VillageWaystones.linked(level, at(helper, HALL_B), at(helper, HALL_C))
				&& VillageWaystones.linked(level, at(helper, HALL_A), at(helper, HALL_B)), "the rebuilt Brookmere isn't linked again");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
