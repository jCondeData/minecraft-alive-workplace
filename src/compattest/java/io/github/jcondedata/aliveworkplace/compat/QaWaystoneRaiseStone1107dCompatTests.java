package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.List;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (lane D, 2026-11-07), 41.1 from the spec: "the stone is the mod's own waystone block, named after the village". A hall
 * raises only a waystone that stands where its plaza put one: asked to raise a spot that holds plain stone (the stone
 * taken out and a block put in its place, or a plaza that was never finished), the extension answers no, leaves the
 * block as it is and registers no waystone there. (A mutant that dropped the "is it a waystone" guard survived the
 * waystone suite: WaystonesCompat:101; without it the mod's API places a waystone on the spot and leaves a named,
 * registered waystone hidden under plain stone.) The positive control raises a real waystone block the same way.
 */
public class QaWaystoneRaiseStone1107dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final BlockPos WAYSTONE = new BlockPos(6, 2, 5);

	/** The waystones Waystones has on its list at pos or the block above it, in this dimension. */
	private static List<Waystone> listedAt(ServerLevel level, BlockPos pos) {
		return WaystoneManagerImpl.get(level.getServer()).getWaystones()
			.filter(w -> w.getDimension().equals(level.dimension()) && (w.getPos().equals(pos) || w.getPos().equals(pos.above())))
			.toList();
	}

	private static String describe(List<Waystone> waystones) {
		return waystones.stream().map(w -> "'" + w.getName().getString() + "'@" + w.getPos() + " valid=" + w.isValid()).toList().toString();
	}

	//$ gametest_batch AREA '"qa1107dRaisePlainStone"'
	@GameTest(template = AREA, batch = "qa1107dRaisePlainStone")
	public void raisingAPlainStoneBlockAnswersNoAndRegistersNothing(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageWaystones.EXTENSION.present(), "the Waystones extension isn't filled");
		helper.setBlock(STONE, Blocks.STONE);
		helper.setBlock(STONE.above(), Blocks.AIR);
		BlockPos pos = helper.absolutePos(STONE);
		int before = listedAt(level, pos).size();
		boolean raised = VillageWaystones.EXTENSION.call(s -> s.raise(level, pos, "Stonebury"), true);
		List<Waystone> after = listedAt(level, pos);
		boolean named = after.stream().anyMatch(w -> w.getName().getString().equals("Stonebury"));
		BlockState lower = level.getBlockState(pos);
		BlockState upper = level.getBlockState(pos.above());
		// (tidy up what a wrong raise left behind, so later batches at this spot start clean)
		for (Waystone w : after) {
			WaystoneManagerImpl.get(level.getServer()).removeWaystone(w);
		}
		level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertFalse(raised, "raising a spot of plain stone answered yes");
		helper.assertTrue(lower.is(Blocks.STONE), "the plain stone became " + lower);
		helper.assertTrue(upper.isAir(), "above the plain stone now stands " + upper);
		helper.assertTrue(!named && after.size() == before, "raising plain stone registered a waystone there: " + describe(after)
			+ " (" + before + " before)");
		helper.assertTrue(WaystonesAPI.getWaystoneAt(level, pos).filter(Waystone::isValid).isEmpty(), "plain stone reads as a working waystone");
		helper.succeed();
	}

	//$ gametest_batch AREA '"qa1107dRaiseRealStone"'
	@GameTest(template = AREA, batch = "qa1107dRaiseRealStone")
	public void raisingARealWaystoneBlockAnswersYesAndNamesIt(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageWaystones.EXTENSION.present(), "the Waystones extension isn't filled");
		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("waystones", "waystone"));
		helper.assertTrue(block != Blocks.AIR, "no waystones:waystone block");
		BlockPos pos = helper.absolutePos(WAYSTONE);
		// (set down as a plain block, as a builder places it: not yet known to Waystones)
		level.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
		level.setBlock(pos.above(), block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
		boolean raised = VillageWaystones.EXTENSION.call(s -> s.raise(level, pos, "Truestone"), false);
		BlockState lower = level.getBlockState(pos);
		BlockState upper = level.getBlockState(pos.above());
		Waystone waystone = WaystonesAPI.getWaystoneAt(level, pos).filter(Waystone::isValid).orElse(null);
		String name = waystone == null ? null : waystone.getName().getString();
		// (take it down again, so later batches at this spot start clean)
		level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		for (Waystone w : listedAt(level, pos)) {
			WaystoneManagerImpl.get(level.getServer()).removeWaystone(w);
		}
		helper.assertTrue(raised, "raising a real waystone block answered no");
		helper.assertTrue(lower.is(block) && upper.is(block), "the waystone isn't its own block any more: " + lower + " / " + upper);
		if (waystone == null) {
			throw new GameTestAssertException("the raised waystone block isn't a working waystone");
		}
		helper.assertTrue("Truestone".equals(name), "the raised waystone is called '" + name + "'");
		helper.succeed();
	}
}
