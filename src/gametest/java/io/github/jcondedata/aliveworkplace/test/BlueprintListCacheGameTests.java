package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

/**
 * B412: the blueprint library's list is read from the template manager (every data pack and mod jar) once, not on every
 * builder's level-up, yet a structure saved into the world's {@code generated} folder shows at once and a deleted one
 * goes. Measured by {@link BlueprintLibrary#rescans()}, a count, so it can't be flaky. Each test runs in one tick,
 * alone in its batch, so no other test lists the library in between.
 */
public class BlueprintListCacheGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final ResourceLocation SAVED = ResourceLocation.fromNamespaceAndPath("aliveworkplace_b412", "saved_shed");

	/** Levelling builders up again and again (each one lists the library) reads the template manager once at most. */
	//$ gametest_batch EMPTY_STRUCTURE '"blueprint_list_cache"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "blueprint_list_cache")
	public void levelUpsReuseTheListing(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<ResourceLocation> first = BlueprintLibrary.list(server, false);
		int before = BlueprintLibrary.rescans();
		for (int i = 0; i < 20; i++) {
			BlueprintUnlocks.nextForLevelUp(helper.getLevel(), null); // what a builder's level-up works out (26.10)
			helper.assertTrue(BlueprintLibrary.list(server, false).equals(first), "the listing changed with nothing new saved");
		}
		helper.assertTrue(BlueprintLibrary.rescans() == before,
			"20 level-ups read every structure again " + (BlueprintLibrary.rescans() - before) + " times (expected 0)");
		helper.assertTrue(!first.isEmpty() && first.stream().anyMatch(id -> id.getPath().contains("cottage")), "the library lists the starter builds");
		// The kept list is the template manager's own: with everything, the same ids as asking it directly.
		helper.assertTrue(BlueprintLibrary.list(server, true).equals(server.getStructureManager().listTemplates().sorted().toList()),
			"the kept list differs from the template manager's");
		helper.succeed();
	}

	/** A structure saved with a Structure Block (into {@code generated}) is listed straight away, and gone once deleted. */
	//$ gametest_batch EMPTY_STRUCTURE '"blueprint_list_saved"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "blueprint_list_saved")
	public void aSavedStructureShowsAtOnce(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		StructureTemplateManager manager = server.getStructureManager();
		Path file = server.getWorldPath(LevelResource.GENERATED_DIR).resolve(SAVED.getNamespace()).resolve("structures")
			.resolve(SAVED.getPath() + ".nbt");
		delete(manager, file); // the gametest world is reused between local runs
		helper.assertFalse(BlueprintLibrary.list(server, false).contains(SAVED), "a structure nobody saved is listed");
		int before = BlueprintLibrary.rescans();
		manager.getOrCreate(SAVED);
		helper.assertTrue(manager.save(SAVED) && Files.isRegularFile(file), "the structure wasn't saved to " + file);
		try {
			helper.assertTrue(BlueprintLibrary.list(server, false).contains(SAVED), "a structure just saved isn't listed");
			helper.assertTrue(BlueprintLibrary.rescans() == before + 1, "the new save didn't refresh the listing once");
			BlueprintLibrary.list(server, false);
			helper.assertTrue(BlueprintLibrary.rescans() == before + 1, "the listing was read again with nothing new saved");
		} finally {
			delete(manager, file);
		}
		helper.assertFalse(BlueprintLibrary.list(server, false).contains(SAVED), "a deleted structure is still listed");
		helper.succeed();
	}

	private static void delete(StructureTemplateManager manager, Path file) {
		manager.remove(SAVED);
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("could not delete " + file + ": " + e);
		}
	}
}
