package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B1073: a banner built from a file keeps what a player's could carry (a loom's 6 layers), and no less: this checks the
 * cap leaves a player's own banner whole.
 */
public class BlockDataSizeGameTests implements FabricGameTest {
	private static final BlockPos POS = new BlockPos(1, 2, 1);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBannerFromAFileKeepsALoomsLayersAndNoMore(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(POS);
		BlockState state = Blocks.WHITE_BANNER.defaultBlockState();
		helper.assertTrue(layers(helper, pos, state, 6) == 6, "a banner a loom could make (6 layers) should keep all 6");
		helper.assertTrue(layers(helper, pos, state, 3) == 3, "a banner of 3 layers should keep 3");
		int kept = layers(helper, pos, state, 200);
		helper.assertTrue(kept == BannerBlockEntity.MAX_PATTERNS, "a banner of 200 layers from a file should keep "
			+ BannerBlockEntity.MAX_PATTERNS + ", kept " + kept);
		helper.succeed();
	}

	private static int layers(GameTestHelper helper, BlockPos pos, BlockState state, int count) {
		CompoundTag nbt = new CompoundTag();
		ListTag patterns = new ListTag();
		for (int i = 0; i < count; i++) {
			CompoundTag layer = new CompoundTag();
			layer.putString("pattern", "minecraft:stripe_top");
			layer.putString("color", "red");
			patterns.add(layer);
		}
		nbt.put("patterns", patterns);
		CompoundTag out = BlockEntityData.sanitize(helper.getLevel(), pos, state, new BannerBlockEntity(pos, state), nbt);
		return out.getList("patterns", Tag.TAG_COMPOUND).size();
	}
}
