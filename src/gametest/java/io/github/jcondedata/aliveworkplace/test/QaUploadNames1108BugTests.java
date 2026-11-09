package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * QA (lane b, 2026-11-08), from B458's promise (an uploaded file keeps its name as a player wrote it: "Jesse's Café.nbt"
 * is called that) and vanilla's rule for every text one player shows to others (chat, signs, anvil names:
 * StringUtil.filterText drops the section sign and control characters, so nobody can color, obfuscate or break the
 * lines of what others read). A file name is the uploader's own text and every player sees it (the Table's list, the
 * import line in chat), so it keeps its words but never a section sign or a line break.
 */
public class QaUploadNames1108BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {

	//$ gametest_ticks_batch EMPTY_STRUCTURE '100' '"qaUploadNames1108"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "qaUploadNames1108")
	public void anUploadedFilesNameKeepsItsWordsButNoFormattingCodes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(0, 1, 0);
		helper.setBlock(src, Blocks.STONE);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(src), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		helper.setBlock(src, Blocks.AIR);
		String tag = Long.toHexString(level.getGameTime());
		check(helper, bytes, "§4§lServer§r Hall " + tag + ".nbt", "Hall " + tag);
		check(helper, bytes, "Two\nLines " + tag + ".nbt", "Lines " + tag);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, byte[] bytes, String fileName, String words) {
		ResourceLocation id;
		try {
			id = BlueprintImporter.importBytes(helper.getLevel().getServer(), "uploads/qa_namer", fileName, bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) '" + fileName + "' didn't import: " + e);
		}
		String shown = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(id).getString(); // what players read (a name equal to the id's own is not stored)
		boolean coded = shown.chars().anyMatch(c -> c == '§' || c < 32 || c == 127);
		helper.assertTrue(!coded, "an uploaded file's name is shown to every player with its formatting codes or line breaks: '"
			+ shown.replace("§", "<section sign>").replace("\n", "<line break>") + "' (from '"
			+ fileName.replace("§", "<section sign>").replace("\n", "<line break>") + "')");
		helper.assertTrue(shown.replace(" ", "").toLowerCase().contains(words.replace(" ", "").toLowerCase()),
			"an uploaded file's name lost its words: '" + shown + "', wanted '" + words + "'");
	}
}
