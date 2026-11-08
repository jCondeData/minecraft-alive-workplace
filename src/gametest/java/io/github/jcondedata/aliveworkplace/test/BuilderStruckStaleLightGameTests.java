package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.SURFACE;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.cave;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.earth;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.field;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.rel;

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

/**
 * B1023 (B973 back, intermittently): where a struck builder's bag goes must not depend on the light engine. The search
 * took "open to the sky" from the sky light, which the light engine works out a few ticks after blocks change, so a cave
 * sealed moments before (or under a busy server) still read as under the sky and won as the nearest spot. Here the
 * wheat field and the sealed cave under it are made and the builder is struck in the same tick, before any light update
 * has run, so the stale light is there every time.
 */
public class BuilderStruckStaleLightGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A builder in his ripe wheat field (Table 3 east in it), a sealed cave 4 to 6 under the field, struck the tick the
	 * ground was made: everything he left lies on the surface a player walks on, not in the cave.
	 */
	//$ gametest_ticks_batch HUGE '100' '"b1023StaleLight"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "b1023StaleLight")
	public void aBuilderStruckOverACaveSealedThisTickGetsHisBagOnTheSurface(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		earth(helper, (x, z) -> field(x, z) ? Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7) : Blocks.GRASS_BLOCK.defaultBlockState(),
			(x, z) -> field(x, z) ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7) : null);
		cave(helper);
		BlockPos bench = new BlockPos(13, SURFACE, 10);
		BlockPos stand = new BlockPos(10, SURFACE, 10);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		Villager v = helper.spawn(EntityType.VILLAGER, stand);
		v.setNoAi(true);
		Builders.employ(level, v, helper.absolutePos(bench));
		ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.OAK_PLANKS, 12));
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(stand)));
		// the cave was sealed this tick: its sky light is whatever it was before (the light engine hasn't run yet)
		boolean stale = level.canSeeSky(helper.absolutePos(new BlockPos(14, 3, 10)));
		BuilderEvents.onStruckIntoWitch(level, v, bolt);
		v.discard();
		List<String> where = new ArrayList<>();
		boolean inCave = false;
		boolean any = false;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds())) {
			if (e.getItem().is(Items.OAK_PLANKS)) {
				any = true;
				BlockPos p = rel(helper, e.blockPosition());
				where.add(p.toShortString());
				inCave |= p.getY() < SURFACE - 1;
			}
		}
		QaBasementGround1104GameTests.LOGGER.info("b1023 stale light: cave reads under the sky {}, the planks at {}", stale, where);
		helper.assertTrue(any, "the builder's planks weren't put down anywhere");
		helper.assertFalse(inCave, "the planks were put in the sealed cave under the field, at " + where);
		helper.assertTrue(where.stream().allMatch(s -> s.split(", ")[1].equals(String.valueOf(SURFACE))),
			"the planks aren't on the surface (y " + SURFACE + "): " + where);
		helper.succeed();
	}
}
