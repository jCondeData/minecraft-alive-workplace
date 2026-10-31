package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-31), from the spec, with the real Waystones mod: ROADMAP 41.3 ("between two villages that both have a
 * waystone, trade caravans ... step out at the other village's ... instead of walking the whole way") crossed with 31.20
 * (a caravan on a road longer than 300 blocks may go missing). Two villages 320 blocks apart, both stones standing,
 * {@code lostCaravanChance} forced to 1 in 1: the caravan goes by stone and never goes missing (no Lost Caravan starts);
 * with the far stone broken it walks the road, and then it does go missing (so the test proves something).
 */
public class QaLostCaravanWaystone1031CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it (WaystoneTravelCompatTests). */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final int ROAD = 320;

	private static void plaza(ServerLevel level, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
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

	private static void forgetPlaza(ServerLevel level, BlockPos origin) {
		BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE));
	}

	//$ gametest_ticks_batch AREA '200' '"qaLostCaravanWaystone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLostCaravanWaystone")
	public void aCaravanByWaystoneNeverGoesMissing(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = a.offset(ROAD, 0, 0);
		BlockPos plazaA = helper.absolutePos(PLAZA_A);
		BlockPos plazaB = b.offset(2, 0, 2);
		List<ChunkPos> forced = new ArrayList<>();
		for (int dx = -16; dx <= 32; dx += 16) {
			for (int dz = -16; dz <= 32; dz += 16) {
				ChunkPos c = new ChunkPos(b.offset(dx, 0, dz));
				if (!forced.contains(c)) {
					forced.add(c);
					level.setChunkForced(c.x, c.z, true);
				}
			}
		}
		int chance = LostCaravanStory.CHANCE;
		Caravans.Data data = Caravans.Data.get(level);
		Runnable cleanUp = () -> {
			LostCaravanStory.CHANCE = chance;
			Arcs.stopAll(level, a);
			Arcs.stopAll(level, b);
			data.clearRoad(a);
			data.remove(a);
			data.remove(b);
			VillageWaystones.Data.get(level).forget(a);
			VillageWaystones.Data.get(level).forget(b);
			forgetPlaza(level, plazaA);
			forgetPlaza(level, plazaB);
			if (level.getBlockState(b).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
			}
			forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
		};
		try {
			UUID owner = UUID.randomUUID();
			helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
			level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
			Object[][] villages = {{a, "Thornholm"}, {b, "Ashford"}};
			for (Object[] v : villages) {
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity((BlockPos) v[0]);
				hall.setCustomName(Component.literal((String) v[1]));
				hall.setOwner(owner, "Jesse");
				hall.setLastQuestDay(Long.MAX_VALUE / 2);
				Arcs.stopAll(level, (BlockPos) v[0]);
			}
			plaza(level, plazaA);
			plaza(level, plazaB);
			helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
			helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
			Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			chest.setItem(1, new ItemStack(Items.OAK_LOG, 64));
		} catch (RuntimeException e) {
			cleanUp.run();
			throw e;
		}
		helper.runAfterDelay(2, () -> { // (the Storehouse's point of interest is filed a moment after it's placed)
			try {
				VillageWaystones.round(level, a);
				VillageWaystones.round(level, b);
				helper.assertTrue(VillageWaystones.linked(level, a, b), "setup: the two stones don't stand (" + VillageWaystones.stoneOf(level, a)
					+ ", " + VillageWaystones.stoneOf(level, b) + ")");
				data.setWants(a, Component.literal("Thornholm"), List.of());
				data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
				helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
				LostCaravanStory.CHANCE = 1;
				for (int day = 0; day < 3; day++) {
					data.clearRoad(a);
					data.sent(a, -1);
					Caravans.round(level, a, null);
					List<Caravans.Shipment> out = data.onTheRoad().stream().filter(s -> s.from().equals(a) && s.to().equals(b)).toList();
					helper.assertTrue(Arcs.running(level, a) == null && Arcs.running(level, b) == null,
						"with both waystones standing the 320-block caravan went missing anyway (send " + day + "): " + Arcs.running(level, a));
					helper.assertTrue(out.size() == 1 && out.get(0).byWaystone(), "the caravan didn't go by waystone (send " + day + "): " + out);
				}
				// the far stone broken: it walks the road, and the forced 1 in 1 takes it
				BlockPos stone = plazaB.offset(STONE);
				level.setBlock(stone.above(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(stone, Blocks.AIR.defaultBlockState(), 3);
				helper.assertFalse(VillageWaystones.linked(level, a, b), "setup: still linked with Ashford's stone broken");
				data.clearRoad(a);
				data.sent(a, -1);
				Caravans.round(level, a, null);
				ArcState s = Arcs.running(level, a);
				helper.assertTrue(s != null && s.id.equals(LostCaravanStory.ARC.toString()),
					"walking the 320-block road with the chance forced, it didn't go missing (the setup proves nothing): " + data.onTheRoad());
			} finally {
				cleanUp.run();
			}
			helper.succeed();
		});
	}
}
