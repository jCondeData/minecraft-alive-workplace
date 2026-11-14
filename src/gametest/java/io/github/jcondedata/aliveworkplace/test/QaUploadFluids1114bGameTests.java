package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * QA (lane b, 2026-11-14, second run) for B1239, from the bug report: a build file whose blocks the Blueprint Table's
 * preview would draw none of (only water, only lava) is refused like an empty file ("Could not import ...") and nothing
 * is listed; a file with one block the preview draws still imports. These go further than QaUploadPreview1114bGameTests:
 * the real upload at a Table by a player (the line he reads, word for word, and no blueprint made or file written), a
 * pond with one waterlogged slab (imports) and a file of only waterlogged stairs (real blocks: imports), files of only
 * blocks no one sees (barriers, light blocks, a mix with water and lava: refused), an upload over an existing
 * blueprint's id that is refused leaving the old one as it was, and /workplace import's folder refusing a pond.
 */
public class QaUploadFluids1114bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos SRC = new BlockPos(1, 2, 1);

	/** A 3 x 2 x 3 .nbt: {@code fill} everywhere, {@code corner} (or nothing) at its first corner; the blocks cleared after. */
	static byte[] file(GameTestHelper helper, BlockState fill, BlockState corner) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(2, 1, 2))) {
			level.setBlock(helper.absolutePos(p), fill, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		if (corner != null) {
			level.setBlock(helper.absolutePos(SRC), corner, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		for (BlockPos p : BlockPos.betweenClosed(SRC, SRC.offset(2, 1, 2))) {
			level.setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(template.save(new CompoundTag()), out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
	}

	private static String unique(String what) {
		return QaStageSixGameTests.name("qf" + what.substring(0, Math.min(4, what.length())));
	}

	private static Path structures(MinecraftServer server, String folder) {
		return server.getWorldPath(LevelResource.GENERATED_DIR).resolve("aliveworkplace/structures").resolve(folder);
	}

	/** The .nbt files in {@code folder} (none when it doesn't exist). */
	private static List<String> files(MinecraftServer server, String folder) {
		Path dir = structures(server, folder);
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString()).toList();
		} catch (IOException e) {
			throw new GameTestAssertException("can't list " + dir + ": " + e);
		}
	}

	/** {@code bytes} imported as a player's upload must be refused as empty; nothing made under its id, no file written. */
	private static void assertRefused(GameTestHelper helper, byte[] bytes, String what) {
		MinecraftServer server = helper.getLevel().getServer();
		String folder = "uploads/" + unique(what);
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, folder));
		try {
			BlueprintImporter.Imported imported = BlueprintImporter.importBytes(server, folder, "pool.nbt", bytes);
			long drawn = imported.blueprint().blocks().stream().filter(b -> TableServer.previewShows(b.state())).count();
			throw new GameTestAssertException("a file of " + what + " was imported as " + imported.id() + " (" + drawn + " blocks the preview draws)");
		} catch (BlueprintFormatException e) {
			String said = JewellerGameTests.english(e.toComponent());
			helper.assertTrue(said.equals("the build is empty"), what + ": refused, but it reads '" + said + "', not 'the build is empty'");
		}
		helper.assertTrue(server.getStructureManager().get(AliveWorkplace.id(folder + "/pool")).isEmpty(), what + ": a blueprint was made though refused");
		helper.assertTrue(files(server, folder).isEmpty(), what + ": refused, but files were written: " + files(server, folder));
	}

	/** {@code bytes} imported as a player's upload goes through, and the preview has blocks to draw. */
	private static void assertImports(GameTestHelper helper, byte[] bytes, String what) {
		MinecraftServer server = helper.getLevel().getServer();
		String folder = "uploads/" + unique(what);
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, folder));
		try {
			BlueprintImporter.Imported imported = BlueprintImporter.importBytes(server, folder, "pool.nbt", bytes);
			long drawn = imported.blueprint().blocks().stream().filter(b -> TableServer.previewShows(b.state())).count();
			helper.assertTrue(drawn > 0, what + " imported, but the preview draws none of it");
			helper.assertTrue(server.getStructureManager().get(imported.id()).isPresent(), what + ": imported but not there as " + imported.id());
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException(what + " was refused: " + JewellerGameTests.english(e.toComponent()));
		}
	}

	// ---- the Table: what the player reads, what's made -----------------------------------------------------------------

	/**
	 * A player uploads a pond (only water) at a Blueprint Table: refused, and the line he reads is the lang file's,
	 * word for word: "Could not import Pond.nbt: the build is empty". No blueprint in the library, no file in his folder.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaB1239TablePond1114b"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB1239TablePond1114b")
	public void aPondUploadedAtTheTableIsRefusedWithTheCouldNotImportLine(GameTestHelper helper) {
		Leftovers.clear(helper);
		byte[] pond = file(helper, Blocks.WATER.defaultBlockState(), null);
		BlockPos rel = new BlockPos(5, 2, 5);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		String name = unique("table");
		String folder = "uploads/" + name;
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, folder));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, name);
		QaStageFourGameTests.press(helper, c.player(), rel);
		c.sent();
		QaStageSixGameTests.upload(c, helper.absolutePos(rel), "Pond.nbt", pond, 1000);
		List<TablePayloads.UploadResult> results = c.sent(TablePayloads.UploadResult.class);
		helper.assertTrue(results.size() == 1 && !results.get(0).ok() && results.get(0).id().isEmpty(), "a pond's upload: " + results);
		String said = JewellerGameTests.english(results.get(0).message());
		helper.assertTrue(said.equals("Could not import Pond.nbt: the build is empty"), "the refusal reads: " + said);
		ResourceLocation made = AliveWorkplace.id(folder + "/pond");
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), made).isEmpty(), "a pond's blueprint is in the library: " + made);
		helper.assertTrue(files(helper.getLevel().getServer(), folder).isEmpty(), "files written for a refused pond: " + files(helper.getLevel().getServer(), folder));
		helper.succeed();
	}

	/** The same Table, a pond with one waterlogged oak slab in it: the slab is drawn, so it imports and is in the library. */
	//$ gametest_ticks_batch AREA '100' '"qaB1239TableSlab1114b"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaB1239TableSlab1114b")
	public void aPondWithOneWaterloggedSlabUploadedAtTheTableImports(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockState slab = Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(SlabBlock.WATERLOGGED, true);
		byte[] pond = file(helper, Blocks.WATER.defaultBlockState(), slab);
		BlockPos rel = new BlockPos(5, 2, 5);
		helper.setBlock(rel, ModBlocks.BLUEPRINT_TABLE);
		String name = unique("slab");
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, "uploads/" + name));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, name);
		QaStageFourGameTests.press(helper, c.player(), rel);
		c.sent();
		QaStageSixGameTests.upload(c, helper.absolutePos(rel), "Slab Pond.nbt", pond, 1000);
		List<TablePayloads.UploadResult> results = c.sent(TablePayloads.UploadResult.class);
		helper.assertTrue(results.size() == 1 && results.get(0).ok() && results.get(0).id().isPresent(), "a pond with a slab: " + results);
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), results.get(0).id().get()).isPresent(), "not in the library: " + results.get(0).id());
		helper.succeed();
	}

	// ---- the boundary, both sides ----------------------------------------------------------------------------------

	/** Only waterlogged stairs (every block holds water, but each is a real stair the preview draws): imports. */
	//$ gametest_batch AREA '"qaB1239Stairs1114b"'
	@GameTest(template = AREA, batch = "qaB1239Stairs1114b")
	public void aFileOfOnlyWaterloggedStairsImports(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockState stair = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.WATERLOGGED, true);
		assertImports(helper, file(helper, stair, null), "only waterlogged stairs");
		helper.succeed();
	}

	/** Only barriers, only light blocks, and lava with a barrier and a light block: the preview draws none, so each is refused. */
	//$ gametest_batch AREA '"qaB1239Unseen1114b"'
	@GameTest(template = AREA, batch = "qaB1239Unseen1114b")
	public void filesOfOnlyBlocksNoOneSeesAreRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		assertRefused(helper, file(helper, Blocks.BARRIER.defaultBlockState(), null), "only barriers");
		assertRefused(helper, file(helper, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), null), "only light blocks");
		byte[] mixed = file(helper, Blocks.LAVA.defaultBlockState(), Blocks.BARRIER.defaultBlockState());
		assertRefused(helper, mixed, "lava with a barrier");
		assertRefused(helper, file(helper, Blocks.WATER.defaultBlockState(), Blocks.LIGHT.defaultBlockState()), "water with a light block");
		helper.succeed();
	}

	/** Barriers with one glass block (the preview draws glass): imports. The other side of the line above. */
	//$ gametest_batch AREA '"qaB1239Glass1114b"'
	@GameTest(template = AREA, batch = "qaB1239Glass1114b")
	public void barriersWithOneGlassBlockImport(GameTestHelper helper) {
		Leftovers.clear(helper);
		assertImports(helper, file(helper, Blocks.BARRIER.defaultBlockState(), Blocks.GLASS.defaultBlockState()), "barriers with one glass block");
		helper.succeed();
	}

	// ---- an upload over a blueprint already there; /workplace import --------------------------------------------------

	/**
	 * A good file imported as an id, then a pond imported as the same id (a Create schematic uploaded again does this):
	 * the pond is refused and the blueprint already there is left as it was (still listed, still stone, its file kept).
	 */
	//$ gametest_batch AREA '"qaB1239SameId1114b"'
	@GameTest(template = AREA, batch = "qaB1239SameId1114b")
	public void aPondImportedOverABlueprintLeavesTheOldOneAsItWas(GameTestHelper helper) {
		Leftovers.clear(helper);
		MinecraftServer server = helper.getLevel().getServer();
		String folder = "uploads/" + unique("sameid");
		Leftovers.after(helper, () -> ImportGameTests.clearGenerated(helper, folder));
		ResourceLocation id = AliveWorkplace.id(folder + "/hut");
		byte[] stone = file(helper, Blocks.STONE.defaultBlockState(), null);
		byte[] pond = file(helper, Blocks.WATER.defaultBlockState(), null);
		try {
			BlueprintImporter.importAs(server, id, stone);
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException("setup: stone wasn't imported: " + JewellerGameTests.english(e.toComponent()));
		}
		helper.assertTrue(files(server, folder).size() == 1, "setup: the stone file wasn't written: " + files(server, folder));
		try {
			BlueprintImporter.importAs(server, id, pond);
			throw new GameTestAssertException("a pond was imported over " + id);
		} catch (BlueprintFormatException e) {
			helper.assertTrue(JewellerGameTests.english(e.toComponent()).equals("the build is empty"), "the pond's refusal: " + JewellerGameTests.english(e.toComponent()));
		}
		StructureTemplate left = server.getStructureManager().get(id).orElse(null);
		helper.assertTrue(left != null, "the blueprint already there is gone after a refused pond: " + id);
		helper.assertTrue(left.getSize().equals(new Vec3i(3, 2, 3)), "the blueprint already there changed size: " + left.getSize());
		helper.assertTrue(files(server, folder).size() == 1, "the blueprint's file after a refused pond: " + files(server, folder));
		helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), id).isPresent(), "the blueprint already there isn't listed after a refused pond");
		helper.succeed();
	}

	/** /workplace import's folder holding a lava pool: "Could not import ...: the build is empty", moved to failed/, nothing imported. */
	//$ gametest_batch AREA '"qaB1239ImportFolder1114b"'
	@GameTest(template = AREA, batch = "qaB1239ImportFolder1114b")
	public void aLavaPoolInTheImportFolderIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		MinecraftServer server = helper.getLevel().getServer();
		String fileName = unique("folder") + ".nbt";
		String base = BlueprintImporter.sanitize(fileName);
		Path folder = BlueprintImporter.importFolderPath(server);
		Path failed = folder.resolve("failed").resolve(fileName);
		Leftovers.after(helper, () -> {
			try {
				Files.deleteIfExists(folder.resolve(fileName));
				Files.deleteIfExists(failed);
				Files.deleteIfExists(folder.resolve("done").resolve(fileName));
			} catch (IOException ignored) {
				// nothing to clear
			}
			ImportGameTests.clearGenerated(helper, "imported");
		});
		byte[] lava = file(helper, Blocks.LAVA.defaultBlockState(), null);
		try {
			Files.createDirectories(folder);
			Files.write(folder.resolve(fileName), lava);
		} catch (IOException e) {
			throw new GameTestAssertException("setup: couldn't write " + fileName + ": " + e);
		}
		List<Component> messages = BlueprintImporter.importFolder(server);
		List<String> mine = messages.stream().map(JewellerGameTests::english).filter(m -> m.contains(fileName)).toList();
		helper.assertTrue(mine.size() == 1 && mine.get(0).equals("Could not import " + fileName + ": the build is empty"),
			"/workplace import of a lava pool says: " + mine + " (all: " + messages.stream().map(JewellerGameTests::english).toList() + ")");
		helper.assertTrue(Files.exists(failed), "the lava pool wasn't moved to failed/");
		helper.assertTrue(server.getStructureManager().get(AliveWorkplace.id("imported/" + base)).isEmpty(), "a lava pool was imported as imported/" + base);
		helper.assertTrue(!Files.exists(structures(server, "imported").resolve(base + ".nbt")), "a lava pool's file was written");
		helper.succeed();
	}
}
