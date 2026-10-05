package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-1005-0734), ROADMAP 23.7 "imports that just work": every file either imports, or is refused with a message
 * that says which block or format was the problem. The corpus tests cover the sample files; these cover the refusals
 * no test rendered yet (not a build file at all, empty builds, a Litematica file with no regions, a damaged
 * WorldEdit file) and both sides of the two size limits, with files made in the test so the boundary is exact.
 * Each goes through {@link BlueprintImporter#importBytes}, the import a player's upload and the import folder use.
 */
public class QaImportEdgesGameTests implements FabricGameTest {
	private static final String FOLDER = "gametest_qa_import_edges";

	/** Text, an empty file and a picture renamed to .schem are not Minecraft files: the player is told so. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void filesThatAreNotMinecraftFilesSaySo(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		String not = "this isn't a Minecraft build file";
		helper.assertValueEqual(refused(helper, "notes.schem", "my castle, 40 by 40".getBytes(StandardCharsets.UTF_8)), not, "text file");
		helper.assertValueEqual(refused(helper, "empty.litematic", new byte[0]), not, "empty file");
		byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
		helper.assertValueEqual(refused(helper, "picture.nbt", png), not, "a PNG renamed .nbt");
		helper.succeed();
	}

	/** A structure block's .nbt saved without compression imports like a compressed one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUncompressedStructureFileImports(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		BlueprintImporter.Imported imported = importOk(helper, "plain.nbt", plain(structure(3, 2, 4)));
		helper.assertValueEqual(imported.blueprint().size(), new Vec3i(3, 2, 4), "size");
		helper.assertTrue(stoneAtOrigin(imported.blueprint()), "the stone block didn't come along");
		helper.assertValueEqual(imported.summary().getString(), "Imported " + imported.id() + " (3 × 2 × 4)", "message");
		helper.succeed();
	}

	/** The build-size limit (1,000,000 blocks in total): exactly at it imports, one layer more is refused and says so. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theBuildSizeLimitFromBothSides(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		helper.assertValueEqual(BlueprintFiles.DEFAULT_MAX_VOLUME, 1_000_000L, "the limit this test was written for");
		BlueprintImporter.Imported at = importOk(helper, "at_limit.nbt", gzip(structure(100, 100, 100)));
		helper.assertValueEqual(at.blueprint().size(), new Vec3i(100, 100, 100), "a build of exactly the limit");
		helper.assertValueEqual(refused(helper, "over_limit.nbt", gzip(structure(100, 101, 100))),
			"build is 100 × 101 × 100; the limit is 1000000 blocks in total", "one layer over the limit");
		helper.assertValueEqual(refused(helper, "over_limit.schem", gzip(sponge(1000, 1001, 1))),
			"build is 1000 × 1001 × 1; the limit is 1000000 blocks in total", "a WorldEdit file over the limit");
		helper.succeed();
	}

	/** The file-size limit (8 MB): a file of exactly 8 MB is read (and refused only for what it holds), one byte more is refused for its size. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFileSizeLimitFromBothSides(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		int max = BlueprintFiles.MAX_FILE_BYTES;
		helper.assertValueEqual(max, 8 * 1024 * 1024, "the limit this test was written for");
		helper.assertValueEqual(refused(helper, "at_limit.schem", new byte[max]), "this isn't a Minecraft build file",
			"a file of exactly 8 MB is read, not refused for its size");
		String over = refused(helper, "over_limit.schem", new byte[max + 1]);
		helper.assertTrue(over.startsWith("file is ") && over.endsWith("; the limit is 8192 KB"), "a file one byte over: " + over);
		helper.succeed();
	}

	/** Builds with nothing in them: no size, only air, or a Litematica file with no regions. Each says what's wrong. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void emptyBuildsAreRefusedAndSaySo(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		helper.assertValueEqual(refused(helper, "flat.nbt", gzip(structure(0, 0, 0))), "the build is empty", "a 0 × 0 × 0 structure");
		CompoundTag air = structure(3, 3, 3);
		air.getList("palette", 10).getCompound(0).putString("Name", "minecraft:air");
		helper.assertValueEqual(refused(helper, "air.nbt", gzip(air)), "the build is empty", "a structure of only air");
		helper.assertValueEqual(refused(helper, "nothing.schem", gzip(sponge(0, 3, 3))), "the build is empty", "a 0-wide schematic");
		CompoundTag lite = new CompoundTag();
		lite.putInt("Version", 6);
		lite.putInt("MinecraftDataVersion", 3955);
		lite.put("Metadata", new CompoundTag());
		lite.put("Regions", new CompoundTag());
		helper.assertValueEqual(refused(helper, "no_regions.litematic", gzip(lite)), "the Litematica file has no regions", "no regions");
		helper.succeed();
	}

	/** A WorldEdit file whose block data is cut off is called damaged, not imported half. (An index past the palette becomes air: see ROADMAP Notes, qa-1005-0734.) */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aDamagedSchematicSaysSo(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		CompoundTag shortData = sponge(4, 1, 1);
		shortData.putByteArray("BlockData", new byte[] {0, 0});
		helper.assertValueEqual(refused(helper, "short.schem", gzip(shortData)), "the schematic file is damaged", "block data cut off");
		CompoundTag ok = sponge(2, 1, 1);
		BlueprintImporter.Imported fine = importOk(helper, "fine.schem", gzip(ok));
		helper.assertTrue(stoneAtOrigin(fine.blueprint()), "the undamaged twin didn't import its stone");
		helper.succeed();
	}

	// --- files made here ----------------------------------------------------------------------

	/** A vanilla structure file of the given size with one stone block at its corner. */
	private static CompoundTag structure(int x, int y, int z) {
		CompoundTag root = new CompoundTag();
		root.putInt("DataVersion", 3955);
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(x));
		size.add(IntTag.valueOf(y));
		size.add(IntTag.valueOf(z));
		root.put("size", size);
		ListTag palette = new ListTag();
		CompoundTag stone = new CompoundTag();
		stone.putString("Name", "minecraft:stone");
		palette.add(stone);
		root.put("palette", palette);
		ListTag blocks = new ListTag();
		if (x > 0 && y > 0 && z > 0) {
			CompoundTag block = new CompoundTag();
			ListTag pos = new ListTag();
			pos.add(IntTag.valueOf(0));
			pos.add(IntTag.valueOf(0));
			pos.add(IntTag.valueOf(0));
			block.put("pos", pos);
			block.putInt("state", 0);
			blocks.add(block);
		}
		root.put("blocks", blocks);
		root.put("entities", new ListTag());
		return root;
	}

	/** A WorldEdit (Sponge v2) schematic: stone at the origin, air elsewhere. Sizes are unsigned shorts in the format. */
	private static CompoundTag sponge(int w, int h, int l) {
		CompoundTag s = new CompoundTag();
		s.putInt("Version", 2);
		s.putInt("DataVersion", 3955);
		s.putShort("Width", (short) w);
		s.putShort("Height", (short) h);
		s.putShort("Length", (short) l);
		CompoundTag palette = new CompoundTag();
		palette.putInt("minecraft:stone", 0);
		palette.putInt("minecraft:air", 1);
		s.put("Palette", palette);
		s.putInt("PaletteMax", 2);
		long volume = (long) w * h * l;
		byte[] data = new byte[(int) Math.min(volume, 4096)];
		for (int i = 1; i < data.length; i++) {
			data[i] = 1;
		}
		s.putByteArray("BlockData", data);
		s.put("BlockEntities", new ListTag());
		s.put("Metadata", new CompoundTag());
		s.put("Unused", StringTag.valueOf("qa"));
		return s;
	}

	private static byte[] gzip(CompoundTag tag) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			NbtIo.writeCompressed(tag, out);
		} catch (IOException e) {
			throw new GameTestAssertException("couldn't write the test file: " + e);
		}
		return out.toByteArray();
	}

	private static byte[] plain(CompoundTag tag) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (DataOutputStream data = new DataOutputStream(out)) {
			NbtIo.write(tag, data);
		} catch (IOException e) {
			throw new GameTestAssertException("couldn't write the test file: " + e);
		}
		return out.toByteArray();
	}

	private static boolean stoneAtOrigin(Blueprint bp) {
		for (Blueprint.Entry e : bp.blocks()) {
			if (e.pos().equals(BlockPos.ZERO)) {
				return e.state().is(Blocks.STONE);
			}
		}
		return false;
	}

	private static BlueprintImporter.Imported importOk(GameTestHelper helper, String file, byte[] bytes) {
		try {
			return BlueprintImporter.importBytes(helper.getLevel().getServer(), FOLDER, file, bytes);
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException(file + " was refused: " + e.toComponent().getString());
		}
	}

	/** The reason a file is refused, as the player reads it. */
	private static String refused(GameTestHelper helper, String file, byte[] bytes) {
		try {
			BlueprintImporter.importBytes(helper.getLevel().getServer(), FOLDER, file, bytes);
		} catch (BlueprintFormatException e) {
			return e.toComponent().getString();
		} catch (RuntimeException e) {
			throw new GameTestAssertException(file + " crashed the import instead of saying why: " + e);
		}
		throw new GameTestAssertException(file + " was imported, but should have been refused");
	}
}
