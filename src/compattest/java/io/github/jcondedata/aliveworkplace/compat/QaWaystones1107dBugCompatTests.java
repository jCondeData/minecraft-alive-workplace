package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA for ROADMAP 41.1 to 41.3 (lane d, 2026-11-07), written from the spec and docs/wiki/waystones.md with the real
 * Waystones mod, for what the earlier waystone classes leave out: settlers and a village whose hall is gone (its stone
 * still standing), the other village leaving the network while settlers are on the road to its stone (saved and loaded
 * on the way), where travelers stand to step in at the two-block edge and on a step (from both sides), and the
 * network file read from an older or damaged save.
 */
public class QaWaystones1107dBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	// ---- two villages with their plazas (as QaWaystoneTravel1102dCompatTests sets them up) ----------------------------

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

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = placement(helper, origin);
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

	private static boolean standsAt(GameTestHelper helper, BlockPos plaza) {
		return VillageWaystones.isStone(helper.getLevel().getBlockState(stone(helper, plaza)));
	}

	private static void breakStone(GameTestHelper helper, BlockPos plaza) {
		BlockPos s = stone(helper, plaza);
		helper.getLevel().setBlock(s.above(), Blocks.AIR.defaultBlockState(), 3);
		helper.getLevel().setBlock(s, Blocks.AIR.defaultBlockState(), 3);
	}

	private static Optional<Chronicle.Entry> said(GameTestHelper helper, BlockPos hall, String key) {
		VillageHallBlockEntity entity = helper.getBlockEntity(hall);
		return entity.chronicle().stream().filter(e -> e.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key)).findFirst();
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
		RealmData.get(level.getServer()).removeOrder(GlobalPos.of(level.dimension(), a(helper)));
		for (BlockPos plaza : List.of(PLAZA_A, PLAZA_B)) {
			BuildSiteManager.get(level).forgetFinished(placement(helper, plaza));
		}
	}

	private static void start(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, a(helper), 160)) {
			if (VillageWaystones.isPlaza(f.structure())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(a(helper), 160)) {
				manager.remove(site.id());
			}
		}
		villages(helper);
	}

	/** An order from Ashford for Newbrook at {@code spot}, in {@code state}, going through {@code via}. */
	private static RealmData.Order order(GameTestHelper helper, BlockPos spot, String state, Optional<BlockPos> via) {
		ServerLevel level = helper.getLevel();
		return new RealmData.Order(GlobalPos.of(level.dimension(), a(helper)), spot, Optional.of(Component.literal("Newbrook")), state,
			level.getGameTime(), level.getGameTime() + 5000, 0, level.getGameTime(), List.of(), Optional.empty(), Map.of(), List.of(), false, via);
	}

	// ---- the tests ---------------------------------------------------------------------------------------------------

	/**
	 * 41.3 "between two villages that both have a waystone" with 41.2's "a village whose hall is gone leaves the Map":
	 * Brookmere's hall broken (its plaza and stone still standing, nothing rebuilt) is no village any more, so Ashford's
	 * settlers for a spot beyond it don't step out of its stone and the two aren't linked. Hypothesis: Brookmere stays in
	 * VillageWaystones.Data for good (only its own hall's round ever forgets it, and that hall is gone), and Settlers.via,
	 * which reads Data and VillageWaystones.linked (stones only), still sends the settlers through it.
	 */
	//$ gametest_batch AREA '"qaWaystones1107HallGone"'
	@GameTest(template = AREA, batch = "qaWaystones1107HallGone")
	public void settlersDontGoThroughAVillageWhoseHallIsGone(GameTestHelper helper) {
		start(helper);
		ServerLevel level = helper.getLevel();
		try {
			BlockPos spot = b(helper).offset(300, 0, 300);
			helper.assertTrue(Settlers.via(level, order(helper, spot, RealmData.MUSTER, Optional.empty())).equals(Optional.of(b(helper))),
				"setup: with both halls standing the settlers don't go through Brookmere");
			helper.setBlock(HALL_B, Blocks.AIR); // Brookmere's hall broken; its plaza and stone stand
			io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
			helper.assertTrue(standsAt(helper, PLAZA_B), "setup: Brookmere's stone went with the hall");
			Optional<BlockPos> via = Settlers.via(level, order(helper, spot, RealmData.MUSTER, Optional.empty()));
			helper.assertTrue(via.isEmpty(), "settlers go through the stone of a village whose hall is gone: " + via);
			helper.assertFalse(VillageWaystones.linked(level, a(helper), b(helper)), "Ashford is still linked to a village whose hall is gone");
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}
