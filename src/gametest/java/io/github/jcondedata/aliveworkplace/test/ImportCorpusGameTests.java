package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * ROADMAP 23.7, imports that just work: the sample corpus in {@code fixtures/corpus} (drawn by
 * tools/blueprints/fixtures.py, part of this repository) goes through the same import a player's upload or the import
 * folder uses. Every file either imports with the right blocks, or is refused with a message that says which block or
 * which format was the problem.
 */
public class ImportCorpusGameTests implements FabricGameTest {
	private static final String FOLDER = "gametest_corpus";

	/** The 48×8×48 hall reads the same from Litematica, WorldEdit (Sponge v3) and a structure block's .nbt. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBigBuildImportsTheSameFromEveryFormat(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		Map<BlockPos, BlockState> first = null;
		for (String file : List.of("big_hall.litematic", "big_hall.schem", "big_hall.nbt")) {
			BlueprintImporter.Imported imported = importOk(helper, file);
			Blueprint bp = imported.blueprint();
			helper.assertValueEqual(bp.size(), new Vec3i(48, 8, 48), file + " size");
			helper.assertValueEqual(imported.unknownBlocks(), 0, file + " unknown blocks");
			Map<BlockPos, BlockState> blocks = blocks(bp);
			helper.assertTrue(blocks.get(new BlockPos(0, 0, 0)).is(Blocks.STONE_BRICKS), file + ": no stone-brick floor");
			helper.assertTrue(blocks.get(new BlockPos(0, 3, 0)).is(Blocks.OAK_LOG), file + ": no log corner");
			BlockState door = blocks.get(new BlockPos(23, 2, 0));
			helper.assertTrue(door.is(Blocks.OAK_DOOR) && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER, file + ": door top " + door);
			helper.assertTrue(blocks.get(new BlockPos(10, 1, 40)).is(Blocks.OAK_STAIRS), file + ": no stairs");
			Blueprint.Entry chest = entry(bp, new BlockPos(2, 1, 2));
			helper.assertTrue(chest != null && chest.state().is(Blocks.CHEST), file + ": no chest: " + chest);
			CompoundTag items = chest.nbt() == null ? null : chest.nbt().getList("Items", Tag.TAG_COMPOUND).getCompound(0);
			helper.assertTrue(items != null && items.getString("id").equals("minecraft:bread") && items.getInt("count") == 12,
				file + ": the chest's bread didn't come along: " + chest.nbt());
			String said = imported.summary().getString();
			helper.assertValueEqual(said, "Imported " + imported.id() + " (48 × 8 × 48)", file + " message");
			if (first == null) {
				first = blocks;
			} else {
				int differ = 0;
				for (Map.Entry<BlockPos, BlockState> e : first.entrySet()) {
					if (!e.getValue().equals(blocks.getOrDefault(e.getKey(), Blocks.AIR.defaultBlockState()))) {
						differ++;
					}
				}
				helper.assertTrue(differ == 0 && first.size() == blocks.size(), file + ": " + differ + " block(s) differ from the .litematic");
			}
		}
		helper.succeed();
	}

	/** Blocks of mods that aren't installed become air, and the message names them, the commonest first. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blocksFromMissingModsAreNamed(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		BlueprintImporter.Imported imported = importOk(helper, "modded_mix.schem");
		helper.assertValueEqual(imported.unknownBlocks(), 7, "unknown blocks (5 shafts, a casing, a sconce)");
		helper.assertValueEqual(imported.unknownNames(), List.of("create:shaft", "create:andesite_casing", "supplementaries:sconce"), "names");
		helper.assertTrue(blocks(imported.blueprint()).get(new BlockPos(2, 1, 2)).isAir(), "a shaft didn't become air");
		helper.assertValueEqual(imported.summary().getString(), "Imported " + imported.id() + " (5 × 3 × 5). 7 blocks from mods that aren't "
			+ "installed became air: create:shaft, create:andesite_casing, supplementaries:sconce.", "message");
		helper.succeed();
	}

	/** More than three missing kinds: the three commonest, then how many more. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void manyMissingKindsAreShortened(GameTestHelper helper) {
		helper.assertValueEqual(BlueprintImporter.names(List.of("a:one", "b:two", "c:three", "d:four", "e:five")).getString(),
			"a:one, b:two, c:three and 2 more", "names");
		helper.assertValueEqual(BlueprintImporter.names(List.of("a:one")).getString(), "a:one", "one name");
		helper.succeed();
	}

	/** A build of nothing but missing mods' blocks is refused, naming them, not called "empty". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBuildOfOnlyMissingBlocksSaysWhich(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		helper.assertValueEqual(refused(helper, "all_modded.litematic"),
			"every block in it is from a mod that isn't installed (create:andesite_casing, mekanism:steel_casing)", "message");
		helper.succeed();
	}

	/** Builds saved by Minecraft 1.12 (before the flattening), 1.16 and WorldEdit's first .schem version are upgraded. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oldVersionsAndFormatsAreUpgraded(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		Map<BlockPos, BlockState> old = blocks(importOk(helper, "old_1_12.nbt").blueprint());
		helper.assertTrue(old.get(new BlockPos(0, 0, 0)).is(Blocks.SPRUCE_PLANKS), "1.12 planks/spruce: " + old.get(BlockPos.ZERO));
		helper.assertTrue(old.get(new BlockPos(1, 0, 0)).is(Blocks.RED_WOOL), "1.12 wool/red: " + old.get(new BlockPos(1, 0, 0)));
		helper.assertTrue(old.get(new BlockPos(2, 0, 0)).is(Blocks.GRANITE), "1.12 stone/granite: " + old.get(new BlockPos(2, 0, 0)));
		BlueprintImporter.Imported lite = importOk(helper, "old_1_16.litematic");
		Map<BlockPos, BlockState> l = blocks(lite.blueprint());
		helper.assertTrue(l.get(new BlockPos(0, 0, 0)).is(Blocks.DIRT_PATH), "1.16 grass_path: " + l.get(BlockPos.ZERO));
		helper.assertTrue(l.get(new BlockPos(1, 1, 0)).is(Blocks.SHORT_GRASS), "1.16 grass: " + l.get(new BlockPos(1, 1, 0)));
		helper.assertValueEqual(lite.unknownBlocks(), 0, "1.16 unknown blocks");
		Map<BlockPos, BlockState> v1 = blocks(importOk(helper, "sponge_v1.schem").blueprint());
		helper.assertTrue(v1.get(new BlockPos(1, 0, 0)).is(Blocks.STONE_BRICKS) && v1.get(new BlockPos(0, 1, 0)).is(Blocks.TORCH),
			"Sponge v1: " + v1);
		helper.succeed();
	}

	/** Files that can't be read say why: which format, or that the file is damaged and where. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void unreadableFilesSayWhy(GameTestHelper helper) {
		ImportGameTests.clearGenerated(helper, FOLDER);
		helper.assertTrue(refused(helper, "old_mcedit.schematic").startsWith("this is an old MCEdit .schematic"), "MCEdit");
		helper.assertValueEqual(refused(helper, "cut_short.litematic"), "the file is cut short and won't unpack; download or copy it again", "cut short");
		helper.assertValueEqual(refused(helper, "damaged_region.litematic"), "the Litematica file is damaged: its region 'main' is missing block data",
			"damaged region");
		helper.assertValueEqual(refused(helper, "a_level_dat.nbt"),
			"this file holds Minecraft data, but not a build (supported: .litematic, .schem, .nbt)", "not a build");
		helper.succeed();
	}

	// --- helpers ---------------------------------------------------------------------------

	private static BlueprintImporter.Imported importOk(GameTestHelper helper, String file) {
		MinecraftServer server = helper.getLevel().getServer();
		try {
			return BlueprintImporter.importBytes(server, FOLDER, file, ImportGameTests.bytes("corpus/" + file));
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException(file + " was refused: " + e.toComponent().getString());
		}
	}

	/** The reason a file is refused, as the player reads it. */
	private static String refused(GameTestHelper helper, String file) {
		try {
			BlueprintImporter.importBytes(helper.getLevel().getServer(), FOLDER, file, ImportGameTests.bytes("corpus/" + file));
		} catch (BlueprintFormatException e) {
			return e.toComponent().getString();
		}
		throw new GameTestAssertException(file + " was imported, but should have been refused");
	}

	private static Map<BlockPos, BlockState> blocks(Blueprint bp) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (Blueprint.Entry e : bp.blocks()) {
			if (!e.state().isAir()) {
				out.put(e.pos(), e.state());
			}
		}
		return new HashMap<>(out) {
			@Override
			public BlockState get(Object key) {
				return getOrDefault(key, Blocks.AIR.defaultBlockState());
			}
		};
	}

	private static Blueprint.Entry entry(Blueprint bp, BlockPos pos) {
		for (Blueprint.Entry e : bp.blocks()) {
			if (e.pos().equals(pos)) {
				return e;
			}
		}
		return null;
	}
}
