package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blueprint file import. Fixtures are written by third-party libraries (see tools/blueprints/fixtures.py)
 * and all describe the same 5x4x5 test hut, so each can be compared block-for-block with test_hut.nbt.
 */
public class ImportGameTests implements FabricGameTest {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void readsLitematicaFiles(GameTestHelper helper) {
		BlueprintFiles.Result result = read(helper, "hut.litematic");
		helper.assertValueEqual(result.format(), "litematic", "format");
		assertSameAsTestHut(helper, result.blueprint());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void readsWorldEditV2Files(GameTestHelper helper) {
		BlueprintFiles.Result result = read(helper, "hut_v2.schem");
		helper.assertValueEqual(result.format(), "schem", "format");
		assertSameAsTestHut(helper, result.blueprint());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void readsWorldEditV3FilesWithBlockEntities(GameTestHelper helper) {
		BlueprintFiles.Result result = read(helper, "hut_v3.schem");
		Blueprint bp = result.blueprint();
		Blueprint.Entry chest = find(bp, new BlockPos(1, 1, 1));
		helper.assertTrue(chest != null && chest.state().is(Blocks.CHEST), "no chest at 1,1,1: " + chest);
		helper.assertTrue(chest.nbt() != null && chest.nbt().getString("id").equals("minecraft:chest"), "chest block entity missing id: " + chest.nbt());
		helper.assertTrue(chest.nbt().getList("Items", 10).size() == 1, "chest items not carried in blueprint data: " + chest.nbt());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void upgradesBlocksFromOlderVersions(GameTestHelper helper) {
		Blueprint bp = read(helper, "old_1_20_2.schem").blueprint();
		Blueprint.Entry grass = find(bp, new BlockPos(0, 1, 0));
		helper.assertTrue(grass != null && grass.state().is(Blocks.SHORT_GRASS), "1.20.2 'grass' should become short_grass, got " + grass);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rejectsFilesThatAreNotBlueprints(GameTestHelper helper) {
		try {
			BlueprintFiles.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "junk"), "definitely not nbt".getBytes(),
				helper.getLevel().getServer().getFixerUpper(), BlueprintFiles.DEFAULT_MAX_VOLUME);
			throw new GameTestAssertException("junk bytes were accepted");
		} catch (BlueprintFormatException expected) {
			// good
		}
		try {
			BlueprintFiles.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "big"), bytes("hut_v2.schem"),
				helper.getLevel().getServer().getFixerUpper(), 50);
			throw new GameTestAssertException("volume limit was not enforced");
		} catch (BlueprintFormatException expected) {
			// good
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void importedFilesJoinTheLibraryWithUniqueNames(GameTestHelper helper) {
		clearGenerated(helper, "gametest_names");
		try {
			BlueprintImporter.Imported first = BlueprintImporter.importBytes(helper.getLevel().getServer(), "gametest_names", "My Cool Hut!.litematic", bytes("hut.litematic"));
			BlueprintImporter.Imported second = BlueprintImporter.importBytes(helper.getLevel().getServer(), "gametest_names", "My Cool Hut!.litematic", bytes("hut.litematic"));
			helper.assertValueEqual(first.id().getPath(), "gametest_names/my_cool_hut", "first id");
			helper.assertValueEqual(second.id().getPath(), "gametest_names/my_cool_hut_2", "second id");
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), first.id()).isPresent(), "imported blueprint not in library");
			helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), false).contains(first.id()), "imported blueprint not listed");
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException("import failed: " + e.getMessage());
		}
		helper.succeed();
	}

	@GameTest(template = "aliveworkplace_test:build_area", timeoutTicks = 2400)
	public void buildersBuildImportedBlueprints(GameTestHelper helper) {
		BlueprintImporter.Imported imported;
		try {
			clearGenerated(helper, "gametest_build");
			imported = BlueprintImporter.importBytes(helper.getLevel().getServer(), "gametest_build", "from_worldedit.schem", bytes("hut_v2.schem"));
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException("import failed: " + e.getMessage());
		}
		helper.getLevel().getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, helper.getLevel().getServer());
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 25));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 55));
		chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_DOOR));
		chest.setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(helper.getLevel(), villager, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(helper.getLevel(), villager, null, imported.id(), new BlueprintData.Placement(
			helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(helper.getLevel());
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null, "still building: " + site.status());
			List<BlockPos> unfinished = plan.unfinished(helper.getLevel());
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong");
		});
	}

	// --- helpers ---------------------------------------------------------------------------

	private static BlueprintFiles.Result read(GameTestHelper helper, String fixture) {
		try {
			return BlueprintFiles.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "fixture"), bytes(fixture),
				helper.getLevel().getServer().getFixerUpper(), BlueprintFiles.DEFAULT_MAX_VOLUME);
		} catch (BlueprintFormatException e) {
			throw new GameTestAssertException(fixture + " failed to load: " + e.getMessage());
		}
	}

	/** The gametest world is reused between local runs: remove earlier imports so names are predictable. */
	static void clearGenerated(GameTestHelper helper, String folder) {
		java.nio.file.Path dir = helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.GENERATED_DIR)
			.resolve("aliveworkplace/structures").resolve(folder);
		try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
				// Forget it in the template manager too: another test may have loaded it (e.g. by listing the library).
				String name = p.getFileName().toString();
				if (name.endsWith(".nbt")) {
					helper.getLevel().getServer().getStructureManager().remove(io.github.jcondedata.aliveworkplace.AliveWorkplace.id(
						folder + "/" + name.substring(0, name.length() - 4)));
				}
				p.toFile().delete();
			});
		} catch (IOException ignored) {
			// nothing to clear
		}
	}

	static byte[] bytes(String fixture) {
		try (InputStream in = ImportGameTests.class.getResourceAsStream("/fixtures/" + fixture)) {
			if (in == null) {
				throw new GameTestAssertException("missing fixture " + fixture);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new GameTestAssertException("could not read fixture " + fixture);
		}
	}

	private static Blueprint.Entry find(Blueprint bp, BlockPos pos) {
		for (Blueprint.Entry e : bp.blocks()) {
			if (e.pos().equals(pos)) {
				return e;
			}
		}
		return null;
	}

	private static void assertSameAsTestHut(GameTestHelper helper, Blueprint actual) {
		Blueprint expected = BlueprintLibrary.get(helper.getLevel(), TEST_HUT).orElseThrow(() -> new GameTestAssertException("test hut missing"));
		helper.assertValueEqual(actual.size(), expected.size(), "size");
		Map<BlockPos, BlockState> want = new HashMap<>();
		for (Blueprint.Entry e : expected.blocks()) {
			want.put(e.pos(), e.state());
		}
		int wrong = 0;
		StringBuilder sample = new StringBuilder();
		for (Blueprint.Entry e : actual.blocks()) {
			BlockState w = want.getOrDefault(e.pos(), Blocks.AIR.defaultBlockState());
			if (!w.equals(e.state())) {
				if (wrong++ < 4) {
					sample.append(e.pos().toShortString()).append(" got ").append(e.state()).append(" want ").append(w).append("; ");
				}
			}
		}
		helper.assertTrue(wrong == 0, wrong + " block(s) differ from test_hut: " + sample);
		helper.assertValueEqual(actual.solidBlockCount(), expected.solidBlockCount(), "solid block count");
	}
}
