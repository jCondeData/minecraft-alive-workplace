package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * B438: a double chest, facing any of the four ways in a blueprint, stays one double chest when the blueprint is turned
 * and mirrored either way (front-back as the style screen and Steward's streets do, and left-right), for plain and
 * trapped chests; placed in the world, the two halves join.
 */
public class MirroredChestGameTests implements FabricGameTest {
	private static final String BUILD_AREA = "aliveworkplace_test:build_area";

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aDoubleChestStaysJoinedInEveryTurnAndMirror(GameTestHelper helper) {
		List<String> broken = new ArrayList<>();
		int checked = 0;
		for (BlockState chest : List.of(Blocks.CHEST.defaultBlockState(), Blocks.TRAPPED_CHEST.defaultBlockState())) {
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockState left = chest.setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT);
				BlockState right = chest.setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.RIGHT);
				BlockPos leftAt = BlockPos.ZERO;
				BlockPos rightAt = leftAt.relative(ChestBlock.getConnectedDirection(left));
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) {
						checked++;
						BlockPos l = StructureTemplate.transform(leftAt, mirror, rotation, BlockPos.ZERO);
						BlockPos r = StructureTemplate.transform(rightAt, mirror, rotation, BlockPos.ZERO);
						BlockState a = MaterialRules.transformed(left, mirror, rotation);
						BlockState b = MaterialRules.transformed(right, mirror, rotation);
						boolean joined = a.getValue(ChestBlock.TYPE) != ChestType.SINGLE && b.getValue(ChestBlock.TYPE) == a.getValue(ChestBlock.TYPE).getOpposite()
							&& a.getValue(ChestBlock.FACING) == b.getValue(ChestBlock.FACING)
							&& l.relative(ChestBlock.getConnectedDirection(a)).equals(r) && r.relative(ChestBlock.getConnectedDirection(b)).equals(l);
						if (!joined) {
							broken.add(chest.getBlock() + " " + facing + " " + mirror + " " + rotation + ": " + a + " at " + l + ", " + b + " at " + r);
						}
					}
				}
			}
		}
		helper.assertTrue(broken.isEmpty(), "double chests that come apart (" + broken.size() + " of " + checked + "): " + broken);
		helper.succeed();
	}

	//$ gametest BUILD_AREA
	@GameTest(template = BUILD_AREA)
	public void aMirroredEastFacingDoubleChestJoinsWhenPlaced(GameTestHelper helper) {
		BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST).setValue(ChestBlock.TYPE, ChestType.LEFT);
		BlockState right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);
		BlockPos rightLocal = BlockPos.ZERO.relative(ChestBlock.getConnectedDirection(left));
		BlockPos origin = new BlockPos(3, 2, 2);
		BlockPos l = origin.offset(StructureTemplate.transform(BlockPos.ZERO, Mirror.FRONT_BACK, Rotation.NONE, BlockPos.ZERO));
		BlockPos r = origin.offset(StructureTemplate.transform(rightLocal, Mirror.FRONT_BACK, Rotation.NONE, BlockPos.ZERO));
		helper.setBlock(l, MaterialRules.transformed(left, Mirror.FRONT_BACK, Rotation.NONE));
		helper.setBlock(r, MaterialRules.transformed(right, Mirror.FRONT_BACK, Rotation.NONE));
		helper.succeedWhen(() -> {
			BlockState a = helper.getBlockState(l);
			BlockState b = helper.getBlockState(r);
			helper.assertTrue(a.getValue(ChestBlock.TYPE) != ChestType.SINGLE && b.getValue(ChestBlock.TYPE) == a.getValue(ChestBlock.TYPE).getOpposite(),
				"the mirrored double chest came out as two single chests: " + a + " / " + b);
			helper.assertTrue(helper.absolutePos(l).relative(ChestBlock.getConnectedDirection(a)).equals(helper.absolutePos(r)),
				"the mirrored double chest's halves point away from each other: " + a + " / " + b);
		});
	}
}
