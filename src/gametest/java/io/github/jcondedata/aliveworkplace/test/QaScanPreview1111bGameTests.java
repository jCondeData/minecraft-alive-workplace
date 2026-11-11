package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B, 2026-11-11), from the Scan Tool's promise (mark two corners, sneak-right-click the air, one Blank Blueprint
 * in survival: what is built in the box is saved; "There's nothing built in that box" when nothing is) and the Blueprint
 * Table's (every build it lists has a preview that draws it; B798: banners, signs, pots and skulls are drawn as their
 * item). A survival player scans boxes holding only one kind of block each. Either the scan is refused as empty with the
 * Blank kept, or the Table's preview of the saved build draws at least one cell for the blocks it lists. Each test
 * deletes the scans its player saved (file, template and name), so TablePreviewGameTests never judges them (B811).
 */
public class QaScanPreview1111bGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:build_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	static final BlockPos AT = new BlockPos(3, 2, 3);

	/**
	 * Builds with {@code setup}, scans the box {@code a}..{@code b} through a survival player's real clicks with one
	 * Blank Blueprint, and checks the promise. The player's saved scans are deleted when the test ends, pass or fail.
	 */
	static void scanAndCheck(GameTestHelper helper, String what, BlockPos a, BlockPos b, Consumer<GameTestHelper> setup) {
		Leftovers.clear(helper);
		MinecraftServer server = helper.getLevel().getServer();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		Leftovers.after(helper, () -> deleteScans(server, player));
		setup.accept(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, a);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.first"), what + ": first corner said " + QaStageThreeGameTests.last(seen));
		QaStageThreeGameTests.click(helper, player, b);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.marked"), what + ": second corner said " + QaStageThreeGameTests.last(seen));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		String said = QaStageThreeGameTests.last(seen);
		int blanks = player.getInventory().countItem(ModItems.BLANK_BLUEPRINT);
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		if (said.equals("message.aliveworkplace.scan.empty")) {
			helper.assertTrue(blanks == 1 && blueprint.isEmpty(), what + ": refused as empty but the Blank was spent (" + blanks + " left) or a blueprint given");
			LOGGER.info("qa scan preview 1111b: {}: refused as empty, the Blank kept", what);
			helper.succeed();
			return;
		}
		helper.assertTrue(said.equals("message.aliveworkplace.scan.saved"), what + ": the scan said " + said);
		helper.assertTrue(blanks == 0, what + ": saved but " + blanks + " Blank Blueprint(s) left, not 0");
		helper.assertTrue(!blueprint.isEmpty(), what + ": saved but no blueprint given");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		Optional<TablePayloads.Entry> entry = TableServer.listing(server).stream().filter(e -> e.id().equals(id)).findFirst();
		helper.assertTrue(entry.isPresent(), what + ": the saved scan " + id + " isn't on the Blueprint Table");
		Optional<TablePayloads.Preview> preview = TableServer.preview(server, id);
		helper.assertTrue(preview.isPresent(), what + ": the Table lists " + id + " but has no preview of it");
		int cells = preview.get().count();
		int listed = entry.get().blocks();
		helper.assertTrue(cells > 0, what + ": a survival scan spent a Blank and the Table lists " + id + " with " + listed
			+ " block(s), but its preview draws 0 cells (an empty preview)");
		helper.assertTrue(listed > 0, what + ": the preview draws " + cells + " cell(s) but the Table lists " + id + " with 0 blocks");
		LOGGER.info("qa scan preview 1111b: {}: saved {}, listed with {} block(s), preview {} cell(s)", what, id, listed, cells);
		helper.succeed();
	}

	/**
	 * The scans {@code player} saved (the blueprints in their inventory): dropped from the template manager, their files
	 * deleted, their names forgotten. Only this test's own ids, so a scan of another test is never touched.
	 */
	static void deleteScans(MinecraftServer server, ServerPlayer player) {
		var manager = server.getStructureManager();
		Path structures = server.getWorldPath(LevelResource.GENERATED_DIR);
		for (ItemStack s : player.getInventory().items) {
			if (!s.is(ModItems.BLUEPRINT)) {
				continue;
			}
			Optional<ResourceLocation> found = BlueprintItem.data(s).map(d -> d.structure());
			if (found.isEmpty() || !found.get().getPath().startsWith("scans/")) {
				continue;
			}
			ResourceLocation id = found.get();
			manager.remove(id);
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintNames.put(server, id, "");
			Path file = structures.resolve(id.getNamespace()).resolve("structures").resolve(id.getPath() + ".nbt");
			try {
				Files.deleteIfExists(file);
				try (Stream<Path> left = Files.list(file.getParent())) {
					if (left.findAny().isEmpty()) {
						Files.deleteIfExists(file.getParent());
					}
				}
			} catch (IOException e) {
				throw new IllegalStateException("could not delete the scan " + file + ": " + e);
			}
		}
	}

	// ---- blocks drawn by their block entity (B798): drawn as their item ----

	/** A standing banner alone in the box. */
	//$ gametest_batch AREA '"qaScanPreview1111bBanner"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bBanner")
	public void aScannedBannerAloneHasAPreview(GameTestHelper helper) {
		scanAndCheck(helper, "a standing banner", AT, AT, h -> h.setBlock(AT, Blocks.RED_BANNER));
	}

	/** An oak wall sign on a stone wall; only the sign is in the box. */
	//$ gametest_batch AREA '"qaScanPreview1111bWallSign"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bWallSign")
	public void aScannedWallSignAloneHasAPreview(GameTestHelper helper) {
		scanAndCheck(helper, "a wall sign", AT, AT, h -> {
			h.setBlock(AT.east(), Blocks.STONE);
			h.setBlock(AT, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.WEST));
		});
	}

	/** A skeleton skull on the floor. */
	//$ gametest_batch AREA '"qaScanPreview1111bSkull"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bSkull")
	public void aScannedSkullAloneHasAPreview(GameTestHelper helper) {
		scanAndCheck(helper, "a skeleton skull", AT, AT, h -> h.setBlock(AT, Blocks.SKELETON_SKULL));
	}

	/** A plain decorated pot. */
	//$ gametest_batch AREA '"qaScanPreview1111bPot"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bPot")
	public void aScannedDecoratedPotAloneHasAPreview(GameTestHelper helper) {
		scanAndCheck(helper, "a decorated pot", AT, AT, h -> h.setBlock(AT, Blocks.DECORATED_POT));
	}

	/** A chest (drawn by its block entity, animated). */
	//$ gametest_batch AREA '"qaScanPreview1111bChest"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bChest")
	public void aScannedChestAloneHasAPreview(GameTestHelper helper) {
		scanAndCheck(helper, "a chest", AT, AT, h -> h.setBlock(AT, Blocks.CHEST));
	}

	/** Control: two oak planks in a 2x2x1 box (half air). */
	//$ gametest_batch AREA '"qaScanPreview1111bPlanks"'
	@GameTest(template = AREA, batch = "qaScanPreview1111bPlanks")
	public void scannedPlanksHaveAPreview(GameTestHelper helper) {
		scanAndCheck(helper, "oak planks", AT, AT.offset(1, 1, 0), h -> {
			h.setBlock(AT, Blocks.OAK_PLANKS);
			h.setBlock(AT.east(), Blocks.OAK_PLANKS);
		});
	}
}
