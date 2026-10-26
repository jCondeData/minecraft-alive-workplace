package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.SupportType;

/**
 * B537, B538, B539: every blueprint this mod ships, read as the builder reads it, holds no block the game itself would
 * break or change once it is placed (the same rules as vanilla's own checks, block by block within the blueprint):
 * <ul>
 * <li>a standing lantern, candle or torch stands on a block whose top holds its middle (not a lectern, a chest, a bottom
 * slab: the Great Library's lantern on a lectern, B538);</li>
 * <li>no dirt path or farmland lies under a block the game counts as solid (a fence, a wall, a tent's wool: the game turns
 * it to dirt, the Hanging Gardens' paths, B539);</li>
 * <li>every bed's foot has its head one block towards its facing (the Observatory's bed, B537). A Wonder's parts are
 * left to QaWonderDesignsGameTests here, since a part may cut through a bed.</li>
 * </ul>
 * The Wonders' stages are also built for real in QaWonderDesignsGameTests (qaWonderStagesStand_*).
 */
public class BlueprintsStayGameTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBlueprintKeepsItsLampsPathsAndBeds(GameTestHelper helper) {
		var resources = helper.getLevel().getServer().getResourceManager();
		List<ResourceLocation> ids = resources.listResources("structure", r -> r.getPath().endsWith(".nbt")).keySet().stream()
			.filter(r -> r.getNamespace().equals(AliveWorkplace.MOD_ID))
			.map(r -> AliveWorkplace.id(r.getPath().substring("structure/".length(), r.getPath().length() - ".nbt".length())))
			.sorted().toList();
		helper.assertTrue(ids.size() >= 300, "only " + ids.size() + " blueprints found");
		List<String> wrong = new ArrayList<>();
		for (ResourceLocation id : ids) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			if (blueprint == null) {
				continue; // a blueprint whose mod is missing (read as nothing)
			}
			Map<BlockPos, BlockState> blocks = new HashMap<>();
			for (Blueprint.Entry e : blueprint.blocks()) {
				if (!e.state().isAir()) {
					blocks.put(e.pos().immutable(), e.state());
				}
			}
			boolean wonderPart = id.getPath().startsWith("wonder/");
			blocks.forEach((p, s) -> {
				String why = why(blocks, p, s, wonderPart);
				if (why != null) {
					wrong.add(id.getPath() + ": " + p.toShortString() + " " + why);
				}
			});
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " blocks the game would break or change: "
			+ wrong.subList(0, Math.min(8, wrong.size())));
		helper.succeed();
	}

	/** Why the game would break or change {@code s} at {@code p}, or null. */
	static String why(Map<BlockPos, BlockState> blocks, BlockPos p, BlockState s, boolean wonderPart) {
		Block block = s.getBlock();
		BlockState below = blocks.get(p.below());
		BlockState above = blocks.get(p.above());
		boolean stands = block instanceof LanternBlock && !s.getValue(LanternBlock.HANGING) || block instanceof CandleBlock
			|| block instanceof BaseTorchBlock && !(block instanceof WallTorchBlock) && !(block instanceof RedstoneWallTorchBlock);
		if (stands && below != null && !below.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP, SupportType.CENTER)) {
			return name(s) + " stands on " + name(below) + ", whose top can't hold it";
		}
		if ((s.is(Blocks.DIRT_PATH) || s.is(Blocks.FARMLAND)) && above != null && above.isSolid() && !(above.getBlock() instanceof FenceGateBlock)) {
			return name(s) + " under " + name(above) + " (the game turns it to dirt)";
		}
		if (block instanceof BedBlock && !wonderPart) {
			Direction facing = s.getValue(BedBlock.FACING);
			boolean foot = s.getValue(BedBlock.PART) == BedPart.FOOT;
			BlockState other = blocks.get(foot ? p.relative(facing) : p.relative(facing.getOpposite()));
			if (other == null || other.getBlock() != block || other.getValue(BedBlock.FACING) != facing || other.getValue(BedBlock.PART) == s.getValue(BedBlock.PART)) {
				return name(s) + "'s " + (foot ? "foot" : "head") + " facing " + facing.getName() + " has no other half";
			}
		}
		return null;
	}

	static String name(BlockState s) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}
}
