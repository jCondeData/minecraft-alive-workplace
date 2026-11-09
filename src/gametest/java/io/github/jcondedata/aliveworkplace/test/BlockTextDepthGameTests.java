package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * B1099 and B1100, around the fix: a line nested past the depth a builder keeps as it is still loses its click event and
 * keeps its words; a line nested a little less keeps its shape (and still loses its click event); a keybind of a real
 * binding with a short name stays a keybind.
 */
public class BlockTextDepthGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String CLICK = "{\"text\":\"hello\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op @a\"}}";

	private static String copiedLine(GameTestHelper helper, String line) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, Blocks.OAK_SIGN);
		BlockEntity sign = helper.getBlockEntity(pos);
		CompoundTag nbt = sign.saveWithoutMetadata(helper.getLevel().registryAccess());
		QaUploadTextEdges1109bGameTests.line(nbt, line);
		CompoundTag clean = BlockEntityData.sanitize(helper.getLevel(), helper.absolutePos(pos), sign.getBlockState(), sign, nbt);
		helper.assertTrue(clean != null, "the sign's data was dropped");
		return clean.getCompound("front_text").getList("messages", 8).getString(0);
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void aVeryDeepLineLosesItsClickEventAndKeepsItsWords(GameTestHelper helper) {
		String kept = copiedLine(helper, "[".repeat(1000) + CLICK + "]".repeat(1000));
		helper.assertTrue(kept.equals("\"hello\""), "a line nested 1,000 deep was copied as " + kept.substring(0, Math.min(200, kept.length())));
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void aLineNestedUnderTheLimitKeepsItsShapeAndLosesItsClickEvent(GameTestHelper helper) {
		String kept = copiedLine(helper, "[".repeat(200) + CLICK + "]".repeat(200));
		helper.assertTrue(!kept.contains("clickEvent") && !kept.contains("/op"), "the click event was kept: " + kept);
		helper.assertTrue(kept.startsWith("[[[") && kept.contains("hello"), "a line nested 200 deep lost its shape: " + kept);
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void aKeybindOfARealBindingStaysAKeybind(GameTestHelper helper) {
		String line = "{\"keybind\":\"key.jump\"}";
		String kept = copiedLine(helper, line);
		helper.assertTrue(kept.equals(line), "a jump keybind was copied as " + kept);
		helper.succeed();
	}
}
