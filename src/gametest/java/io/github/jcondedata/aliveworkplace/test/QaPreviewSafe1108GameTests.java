package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.table.PreviewView;
import io.github.jcondedata.aliveworkplace.table.SafeRender;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA for B1020 (lane-b-1108-0037), written from the bug's "Expected": the Blueprint Table's preview never crashes on
 * another mod's block, and a tint provider that looks around the level it is given gets sane answers. Real furniture
 * mods' providers do more than read one biome color: connected sofas look at their neighbors, some ask for their block
 * entity or the light, some cast the level to a world class. Each of those, through the preview's own little level
 * ({@link PreviewView}) and the guard ({@link SafeRender}), must give a color or fall back to none, never throw. The
 * client renderer itself is checked by the table_preview_safe scene.
 */
public class QaPreviewSafe1108GameTests implements FabricGameTest {
	/** The shape of a client block color provider, without its client-only class. */
	private interface Provider {
		int color(BlockState state, BlockAndTintGetter view, BlockPos pos, int tintIndex);
	}

	private static Biome biome(GameTestHelper helper) {
		return helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO)).value();
	}

	/** A sofa that joins its neighbors reads the build's own blocks around it, including a basement under y = 0. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSofaThatLooksAtItsNeighborsSeesTheBuildsBlocks(GameTestHelper helper) {
		PreviewView view = new PreviewView(biome(helper));
		BlockPos sofa = new BlockPos(3, 1, 2);
		BlockState sofaState = Blocks.CHERRY_STAIRS.defaultBlockState();
		view.put(sofa, sofaState);
		view.put(sofa.east(), sofaState);
		view.put(sofa.below(), Blocks.CHERRY_PLANKS.defaultBlockState());
		BlockPos cellar = new BlockPos(3, -6, 2);
		view.put(cellar, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
		// joined to the east: one color, alone: another (like a connected sofa's "middle" and "end" pieces)
		Provider connected = (state, v, pos, tint) -> v.getBlockState(pos.east()).is(state.getBlock()) ? 0x00FF00 : 0xFF0000;
		helper.assertTrue(SafeRender.tint(sofaState, () -> connected.color(sofaState, view, sofa, 0)) == 0x00FF00,
			"the sofa should see its partner to the east in the build");
		helper.assertTrue(SafeRender.tint(sofaState, () -> connected.color(sofaState, view, sofa.east(), 0)) == 0xFF0000,
			"the sofa at the east end has nothing beside it and should get its end color");
		helper.assertTrue(view.getBlockState(sofa.below()).is(Blocks.CHERRY_PLANKS), "the floor under the sofa is the build's");
		helper.assertTrue(view.getBlockState(cellar).is(Blocks.COBBLED_DEEPSLATE), "a basement block under y = 0 is in the preview's level too");
		helper.assertFalse(view.isOutsideBuildHeight(cellar), "a basement six deep is inside the preview's height");
		helper.assertFalse(SafeRender.warned(Blocks.CHERRY_STAIRS), "a provider that works is never logged");
		helper.succeed();
	}

	/** A provider that asks for its block entity, the light or the blocks in a box gets empty or full-light answers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aProviderAskingForBlockEntitiesOrLightGetsSaneAnswers(GameTestHelper helper) {
		PreviewView view = new PreviewView(null);
		BlockPos chest = new BlockPos(1, 1, 1);
		BlockPos lamp = new BlockPos(2, 1, 1);
		view.put(chest, Blocks.CHEST.defaultBlockState());
		view.put(lamp, Blocks.GLOWSTONE.defaultBlockState());
		helper.assertTrue(view.getBlockEntity(chest, BlockEntityType.CHEST).isEmpty(), "a typed block-entity lookup is empty, not a crash");
		helper.assertTrue(view.getLightEmission(lamp) == 15, "the glowstone's own light reads from the build's block");
		helper.assertTrue(view.getMaxLightLevel() == 15, "light levels run to 15");
		helper.assertTrue(view.getBrightness(LightLayer.SKY, chest) == 15 && view.canSeeSky(chest), "the preview is under open sky");
		// a provider reading the light engine directly gets an answer (no lit chunks: dark), not an exception
		int engine = SafeRender.tint(Blocks.CHERRY_SLAB.defaultBlockState(),
			() -> view.getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(lamp));
		helper.assertTrue(engine != SafeRender.NO_TINT, "the preview's light engine answers a provider without throwing, got " + engine);
		helper.assertFalse(SafeRender.warned(Blocks.CHERRY_SLAB), "asking the light engine is never logged as a failure");
		List<BlockState> inBox = view.getBlockStates(new AABB(0, 0, 0, 4, 3, 3)).filter(s -> !s.isAir()).toList();
		helper.assertTrue(inBox.size() == 2, "a box around the chest and lamp holds just those two blocks, got " + inBox);
		helper.assertTrue(view.getBlockState(new BlockPos(1, 400, 1)).isAir() && view.isOutsideBuildHeight(new BlockPos(1, 400, 1)),
			"high above the build is air and outside the height");
		helper.succeed();
	}

	/** The biome's own grass, foliage and water colors reach a provider; without a biome everything is untinted white. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theViewersBiomeColorsReachEveryKindOfTint(GameTestHelper helper) {
		Biome biome = biome(helper);
		PreviewView view = new PreviewView(biome);
		BlockPos pos = new BlockPos(4, 2, 7);
		helper.assertTrue(view.getBlockTint(pos, (b, x, z) -> b.getFoliageColor()) == biome.getFoliageColor(), "the biome's foliage color");
		helper.assertTrue(view.getBlockTint(pos, (b, x, z) -> b.getWaterColor()) == biome.getWaterColor(), "the biome's water color");
		helper.assertTrue(view.getBlockTint(pos, Biome::getGrassColor) == biome.getGrassColor(pos.getX(), pos.getZ()), "the biome's grass color");
		PreviewView none = new PreviewView(null);
		helper.assertTrue(none.getBlockTint(pos, (b, x, z) -> b.getFoliageColor()) == 0xFFFFFF
			&& none.getBlockTint(pos, (b, x, z) -> b.getWaterColor()) == 0xFFFFFF, "without a viewer's biome every tint is white");
		helper.succeed();
	}

	/** A provider that casts the level to a world (Level), or asks a block entity that isn't there, draws untinted. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aProviderThatAssumesARealWorldFallsBackToNoTint(GameTestHelper helper) {
		PreviewView view = new PreviewView(biome(helper));
		BlockPos pos = new BlockPos(1, 1, 1);
		BlockState armchair = Blocks.CHERRY_FENCE.defaultBlockState();
		BlockState rug = Blocks.CHERRY_TRAPDOOR.defaultBlockState();
		view.put(pos, armchair);
		Provider castsToWorld = (state, v, p, tint) -> (int) ((Level) v).getDayTime();
		Provider readsItsBlockEntity = (state, v, p, tint) -> v.getBlockEntity(p).getBlockPos().getX();
		helper.assertTrue(SafeRender.tint(armchair, () -> castsToWorld.color(armchair, view, pos, 0)) == SafeRender.NO_TINT,
			"a provider casting the preview's level to a world should fall back to no tint");
		helper.assertTrue(SafeRender.tint(rug, () -> readsItsBlockEntity.color(rug, view, pos, 0)) == SafeRender.NO_TINT,
			"a provider reading a block entity the preview doesn't have should fall back to no tint");
		helper.assertTrue(SafeRender.warned(Blocks.CHERRY_FENCE) && SafeRender.warned(Blocks.CHERRY_TRAPDOOR), "both are logged");
		// the same block with a provider that works again (say, once the mod's level check passes) keeps its color
		helper.assertTrue(SafeRender.tint(armchair, () -> 0x335577) == 0x335577, "a logged block still gets a working provider's color");
		helper.succeed();
	}
}
