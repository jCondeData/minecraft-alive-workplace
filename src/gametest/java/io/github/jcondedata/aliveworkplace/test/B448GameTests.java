package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlock;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B448: the mod's own facing blocks (Village Hall, Storehouse, Mailbox, Shop Counter, Grave, and every other block of
 * ours with a horizontal facing) turn and mirror with a placed blueprint like vanilla's facing blocks, for every facing,
 * turn and mirror, and keep their other properties. The Grave is in no starter build, so the QA test's sweep of the
 * starter builds doesn't reach it; this checks every block of ours directly.
 */
public class B448GameTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyOwnFacingBlockTurnsAndMirrors(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<String> checked = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals("aliveworkplace") || !block.defaultBlockState().hasProperty(HorizontalDirectionalBlock.FACING)) {
				continue;
			}
			checked.add(id.getPath());
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockState drawn = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
				for (Mirror mirror : Mirror.values()) {
					for (Rotation rotation : Rotation.values()) {
						Direction expected = rotation.rotate(mirror.mirror(facing));
						BlockState built = MaterialRules.transformed(drawn, mirror, rotation);
						if (!built.is(block) || built.getValue(HorizontalDirectionalBlock.FACING) != expected) {
							wrong.add(id.getPath() + " facing " + facing + " " + mirror + " " + rotation + ": " + built + ", expected " + expected);
						}
					}
				}
			}
		}
		for (Block block : List.<Block>of(ModBlocks.VILLAGE_HALL, ModBlocks.STOREHOUSE, ModBlocks.MAILBOX, ModBlocks.SHOP_COUNTER, ModBlocks.GRAVE)) {
			helper.assertTrue(checked.contains(BuiltInRegistries.BLOCK.getKey(block).getPath()), "not checked: " + block);
		}
		// turning keeps the block's other state: a mailbox with mail still has mail
		BlockState full = ModBlocks.MAILBOX.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
			.setValue(MailboxBlock.HAS_MAIL, true);
		BlockState turned = MaterialRules.transformed(full, Mirror.LEFT_RIGHT, Rotation.CLOCKWISE_90);
		helper.assertTrue(turned.getValue(MailboxBlock.HAS_MAIL), "a turned mailbox lost its mail flag: " + turned);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong, e.g. " + wrong.subList(0, Math.min(4, wrong.size())));
		helper.succeed();
	}
}
