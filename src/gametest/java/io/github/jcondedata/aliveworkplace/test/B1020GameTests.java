package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.table.PreviewView;
import io.github.jcondedata.aliveworkplace.table.SafeRender;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * B1020: the Blueprint Table's 3D preview crashed the game on Cozy Home's tinted furniture, because Minecraft's
 * single-block renderer asks block colors with no level and Cozy Home's color provider assumes one. The preview now
 * asks with its own little level ({@link PreviewView}) and guards every tint and every block ({@link SafeRender}).
 * The renderer itself is client-only; these check the pieces it uses, headless, with a Cozy-Home-like provider.
 */
public class B1020GameTests implements FabricGameTest {
	/** The shape of a block color provider (the client's BlockColor), without its client-only class. */
	private interface Provider {
		int color(BlockState state, BlockAndTintGetter view, BlockPos pos, int tintIndex);
	}

	/** Like Cozy Home's provider (ModRenderLayers line 248): refuses a null view, then reads the biome's grass color. */
	private static final Provider COZY_LIKE = (state, view, pos, tintIndex) -> {
		Objects.requireNonNull(view, "view is null");
		Objects.requireNonNull(pos, "pos is null");
		return view.getBlockTint(pos, Biome::getGrassColor);
	};

	/** With the preview's own level, a provider that needs one gets the viewer's biome color instead of crashing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTintProviderThatNeedsALevelGetsThePreviews(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 3);
		BlockState chair = Blocks.OAK_STAIRS.defaultBlockState();
		boolean vanillaWayThrows = false;
		try {
			COZY_LIKE.color(chair, null, null, 0); // what renderSingleBlock does
		} catch (NullPointerException e) {
			vanillaWayThrows = true;
		}
		helper.assertTrue(vanillaWayThrows, "the Cozy-Home-like provider should throw on a null level, as the crash report shows");

		Biome biome = helper.getLevel().getBiome(helper.absolutePos(BlockPos.ZERO)).value();
		PreviewView view = new PreviewView(biome);
		view.put(pos, chair);
		int tint = SafeRender.tint(chair, () -> COZY_LIKE.color(chair, view, pos, 0));
		helper.assertTrue(tint == biome.getGrassColor(pos.getX(), pos.getZ()),
			"the provider should get the viewer's biome grass color through the preview's level, got " + Integer.toHexString(tint));

		PreviewView noBiome = new PreviewView(null);
		helper.assertTrue(noBiome.getBlockTint(pos, Biome::getGrassColor) == 0xFFFFFF, "without a biome the preview's tint is plain white");
		helper.succeed();
	}

	/** A provider that throws anyway (a null, a missing method) leaves the block untinted, logged once, and never crashes. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aThrowingTintOrDrawFallsBackInsteadOfCrashing(GameTestHelper helper) {
		BlockState sofa = Blocks.BAMBOO_MOSAIC_STAIRS.defaultBlockState();
		int tint = SafeRender.tint(sofa, () -> COZY_LIKE.color(sofa, null, null, 0));
		helper.assertTrue(tint == SafeRender.NO_TINT, "a throwing provider should fall back to no tint, got " + tint);
		helper.assertTrue(SafeRender.warned(sofa.getBlock()), "the failing block should be logged");
		helper.assertTrue(SafeRender.tint(sofa, () -> COZY_LIKE.color(sofa, null, null, 0)) == SafeRender.NO_TINT,
			"every frame after the first falls back the same way");

		BlockState table = Blocks.BAMBOO_MOSAIC_SLAB.defaultBlockState();
		helper.assertTrue(SafeRender.tint(table, () -> {
			throw new NoSuchMethodError("cozyhome changed its API");
		}) == SafeRender.NO_TINT, "a missing method in another mod falls back to no tint too");

		BlockState lamp = Blocks.BAMBOO_MOSAIC.defaultBlockState();
		helper.assertFalse(SafeRender.draw(lamp, () -> {
			throw new IllegalStateException("model exploded");
		}), "a block whose drawing throws should be skipped");
		AtomicBoolean drawn = new AtomicBoolean();
		helper.assertTrue(SafeRender.draw(Blocks.STONE.defaultBlockState(), () -> drawn.set(true)) && drawn.get(),
			"a block that draws fine is drawn");
		helper.assertFalse(SafeRender.warned(Blocks.STONE), "a block that draws fine is never logged");
		helper.assertTrue(SafeRender.tint(Blocks.STONE.defaultBlockState(), () -> 0x123456) == 0x123456, "a working provider's tint is kept");
		helper.succeed();
	}

	/** The preview's level answers like a small, fully lit world holding only the build's blocks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePreviewLevelHoldsOnlyTheBuild(GameTestHelper helper) {
		PreviewView view = new PreviewView(null);
		BlockPos pos = new BlockPos(1, 0, 1);
		view.put(pos, Blocks.WATER.defaultBlockState());
		helper.assertTrue(view.getBlockState(pos).is(Blocks.WATER), "the build's block is there");
		helper.assertTrue(view.getFluidState(pos).is(Fluids.WATER), "its fluid too");
		helper.assertTrue(view.getBlockState(pos.above()).isAir(), "everything else is air");
		helper.assertTrue(view.getBlockEntity(pos) == null, "no block entities");
		helper.assertTrue(view.getBrightness(LightLayer.BLOCK, pos) == 15 && view.getRawBrightness(pos, 0) == 15, "fully lit");
		helper.assertTrue(view.getLightEngine() != null, "a light engine for providers that ask for one");
		helper.assertTrue(view.getShade(Direction.UP, true) == 1f && view.getShade(Direction.DOWN, true) == 0.5f,
			"shaded like the overworld");
		helper.assertTrue(view.getMinBuildHeight() <= -1 && view.getMaxBuildHeight() > 64, "a basement and a tower both fit");
		helper.succeed();
	}
}
