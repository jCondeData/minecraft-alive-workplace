package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * QA (lane b, 2026-11-14), B1195's other door: a .nbt uploaded at the Blueprint Table goes through
 * BlueprintImporter.importBytes (as TableServer's upload does), not the Scan Tool. These are the side that must keep
 * working: a file with only a chest (the preview draws it) and a file of stone with water both import, and the Table's
 * preview has something to draw for each. The fluid-only files are B1239 (QaUploadPreview1114bBugTests on
 * tests/upload-fluid-1114b).
 */
public class QaUploadPreview1114bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos SRC = new BlockPos(1, 2, 1);

	/** A 3 x 2 x 3 .nbt of {@code block}, with {@code extra} (or nothing) at its first corner; the source blocks cleared after. */
	static byte[] file(GameTestHelper helper, Block block, Block extra) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(2, 1, 2))) {
			helper.setBlock(p, block);
		}
		if (extra != null) {
			helper.setBlock(SRC, extra);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(2, 1, 2))) {
			helper.setBlock(p, Blocks.AIR);
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
	}

	/** The imported file taken away again, so no other test (TablePreviewGameTests) meets it on the Table. */
	private static void forget(GameTestHelper helper, ResourceLocation id) {
		MinecraftServer server = helper.getLevel().getServer();
		server.getStructureManager().remove(id);
		Path file = server.getWorldPath(LevelResource.GENERATED_DIR).resolve(id.getNamespace()).resolve("structures").resolve(id.getPath() + ".nbt");
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new GameTestAssertException("(cleanup) couldn't delete " + file + ": " + e);
		}
	}

	private static String name(GameTestHelper helper, String what) {
		return "qa_upload_" + what + "_" + Long.toHexString(helper.getLevel().getGameTime()) + "_" + Integer.toHexString(System.identityHashCode(helper)) + ".nbt";
	}

	/** {@code block} alone, uploaded as a player's file, is refused: nothing imported, so nothing listed with an empty preview. */
	static void assertRefused(GameTestHelper helper, Block block, String what) {
		Leftovers.clear(helper);
		byte[] bytes = file(helper, block, null);
		try {
			BlueprintImporter.Imported imported = BlueprintImporter.importBytes(helper.getLevel().getServer(), "uploads/qa_tester", name(helper, what), bytes);
			long drawn = imported.blueprint().blocks().stream().filter(b -> TableServer.previewShows(b.state())).count();
			forget(helper, imported.id());
			throw new GameTestAssertException("a file of only " + what + " was imported as " + imported.id() + " (" + imported.blueprint().solidBlockCount()
				+ " blocks counted, " + drawn + " the Table's preview would draw): it's listed with an empty picture (expected: refused, as a scan of it is since B1195)");
		} catch (BlueprintFormatException e) {
			helper.succeed();
		}
	}

	/** {@code block} (with {@code extra} at a corner) uploaded as a player's file imports, and the preview has something to draw. */
	private static void assertImported(GameTestHelper helper, Block block, Block extra, String what) {
		Leftovers.clear(helper);
		byte[] bytes = file(helper, block, extra);
		try {
			BlueprintImporter.Imported imported = BlueprintImporter.importBytes(helper.getLevel().getServer(), "uploads/qa_tester", name(helper, what), bytes);
			long drawn = imported.blueprint().blocks().stream().filter(b -> TableServer.previewShows(b.state())).count();
			forget(helper, imported.id());
			helper.assertTrue(drawn > 0, what + " imported, but the Table's preview draws none of its " + imported.blueprint().solidBlockCount() + " blocks");
			helper.succeed();
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException(what + " wasn't imported: " + e.toComponent().getString());
		}
	}

	/** A file of only a chest (no full block) still imports: the preview draws chests (B1195's own boundary). */
	//$ gametest_batch AREA '"qaUploadChest1114b"'
	@GameTest(template = AREA, batch = "qaUploadChest1114b")
	public void anUploadedChestAloneImports(GameTestHelper helper) {
		assertImported(helper, Blocks.AIR, Blocks.CHEST, "a chest alone");
	}

	/** A pond with one stone block in it imports: one block the preview draws is enough. */
	//$ gametest_batch AREA '"qaUploadStoneWater1114b"'
	@GameTest(template = AREA, batch = "qaUploadStoneWater1114b")
	public void anUploadedPondWithOneStoneImports(GameTestHelper helper) {
		assertImported(helper, Blocks.WATER, Blocks.STONE, "a pond with one stone");
	}
}
