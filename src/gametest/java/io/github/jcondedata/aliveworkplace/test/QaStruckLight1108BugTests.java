package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.SURFACE;

import io.github.jcondedata.aliveworkplace.build.BuilderEvents;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1108-0337, B1023): docs/wiki/builder.md puts a struck builder's bag "on the nearest open ground at least 4
 * blocks away" where a player can get to it (B973). B1023 saw the bag go into the sealed cave under the wheat field once
 * in a full build, on a spot earlier tests had used. The search (BuilderEvents.outOfReach) takes "open to the sky" from
 * the sky LIGHT (BlockAndTintGetter.canSeeSky reads LightLayer.SKY), and the light engine catches up on its own thread,
 * so a cave sealed moments before the strike may still read as under the sky. This test lays the ground and the sealed
 * cave over an area first left open to the sky (its light settled), and strikes the builder in that same tick: the bag
 * must still lie where a player can walk to it once the light has settled. It fails 6 of 15 when repeated on one spot (B1023).
 */
public class QaStruckLight1108BugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '300' '"qaStruckLight"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaStruckLight")
	public void aCaveSealedTheTickBeforeTheStrikeNeverGetsTheBag(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		// first the whole area open to the sky (as a spot an earlier test cleared is), and the sky light let settle in
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y < 29; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(level.canSeeSky(helper.absolutePos(new BlockPos(14, 3, 10))), "setup: the open area isn't lit by the sky yet");
			strikeOverAFreshCave(helper, level);
		});
	}

	/** The ground, the cave, the builder and the strike, all in one tick; then the check once the light has caught up. */
	private static void strikeOverAFreshCave(GameTestHelper helper, ServerLevel level) {
		BlockPos bench = new BlockPos(13, SURFACE, 10);
		BlockPos stand = new BlockPos(10, SURFACE, 10);
		// the same ground as QaBuilderGaps1105bBugTests' wheat field: grass round a ripe field, a sealed cave 4-6 under it
		QaBuilderGaps1105bGameTests.earth(helper,
			(x, z) -> QaBuilderGaps1105bGameTests.field(x, z) ? Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7)
				: Blocks.GRASS_BLOCK.defaultBlockState(),
			(x, z) -> QaBuilderGaps1105bGameTests.field(x, z) ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7) : null);
		QaBuilderGaps1105bGameTests.cave(helper);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		Villager v = helper.spawn(EntityType.VILLAGER, stand);
		v.setNoAi(true);
		Builders.employ(level, v, helper.absolutePos(bench));
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.OAK_PLANKS, 20));
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.COBBLESTONE, 7));
		// struck in this very tick, as vanilla's thunderHit calls it (the bolt where he stands, the villager then discarded)
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(stand)));
		BuilderEvents.onStruckIntoWitch(level, v, bolt);
		v.discard();
		// once the light has settled (40 ticks), every item of the bag lies where a player walks to without digging
		helper.runAfterDelay(40, () -> {
			List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16));
			List<String> where = new ArrayList<>();
			int planks = 0;
			for (ItemEntity e : lying) {
				where.add(e.getItem().getCount() + " " + e.getItem().getItem() + " at " + helper.relativePos(e.blockPosition()).toShortString());
				if (e.getItem().is(Items.OAK_PLANKS)) {
					planks += e.getItem().getCount();
				}
			}
			QaBasementGround1104GameTests.LOGGER.info("qa struck light: on the ground {}", where);
			helper.assertTrue(planks == 20, "setup: the bag's 20 planks aren't all on the ground: " + where);
			for (ItemEntity e : lying) {
				QaBuilderGaps1105bGameTests.reachable(helper, e.blockPosition());
			}
			helper.succeed();
		});
	}
}
