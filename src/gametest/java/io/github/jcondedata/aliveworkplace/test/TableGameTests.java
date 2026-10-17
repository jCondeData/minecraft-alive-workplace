package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StylePicker;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** The Blueprint Table's server side: listing, materials, taking blueprints and uploads. */
public class TableGameTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void listingShowsStarterBlueprintsFirst(GameTestHelper helper) {
		List<TablePayloads.Entry> entries = TableServer.listing(helper.getLevel().getServer());
		// every starter blueprint but those only villagers sell (34.15a)
		int listed = (int) StarterBlueprints.ALL.stream().filter(e -> !StarterBlueprints.SOLD_ONLY.contains(e)).count();
		helper.assertTrue(entries.size() >= listed, "library too small: " + entries.size());
		for (int i = 0; i < listed; i++) {
			helper.assertTrue(entries.get(i).id().getNamespace().equals(AliveWorkplace.MOD_ID) && !entries.get(i).id().getPath().contains("/"),
				"entry " + i + " should be a starter blueprint, was " + entries.get(i).id());
		}
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void libraryHasBlueprintsAndStructureBlockSavesButNotWorldgenPieces(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		var manager = server.getStructureManager();
		ResourceLocation saved = ResourceLocation.withDefaultNamespace("gametest_players_house");
		manager.getOrCreate(saved).fillFromWorld(helper.getLevel(), helper.absolutePos(TABLE), new net.minecraft.core.Vec3i(2, 2, 2), false, null);
		helper.assertTrue(manager.save(saved), "could not save a structure like a Structure Block does");
		List<ResourceLocation> library = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.list(server, false);
		helper.assertTrue(library.contains(StarterBlueprints.STARTER_COTTAGE.id()), "starter cottage missing from the library");
		helper.assertTrue(library.contains(saved), "structure-block save missing from the library");
		helper.assertTrue(library.stream().noneMatch(id -> id.getNamespace().equals("fabric-gametest-api-v1") || id.getPath().startsWith("village/")),
			"worldgen/test structures leaked into the library: " + library);
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void materialsListMatchesTheBuild(GameTestHelper helper) {
		var cottage = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		List<TablePayloads.Material> materials = TableServer.materials(cottage);
		int planks = materials.stream().filter(m -> m.item().equals(BuiltInRegistries.ITEM.getKey(Items.BIRCH_PLANKS))).mapToInt(TablePayloads.Material::count).sum();
		int doors = materials.stream().filter(m -> m.item().equals(BuiltInRegistries.ITEM.getKey(Items.OAK_DOOR))).mapToInt(TablePayloads.Material::count).sum();
		helper.assertTrue(planks > 50, "cottage should need lots of birch planks, got " + planks);
		helper.assertTrue(doors == 1, "cottage has exactly one door (both halves = 1 item), got " + doors);
		for (int i = 1; i < materials.size(); i++) {
			helper.assertTrue(materials.get(i - 1).count() >= materials.get(i).count(), "materials not sorted by count");
		}
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void materialsPayloadOfTheLargestBuildHoldsEveryMaterial(GameTestHelper helper) {
		// The table's "All materials" list shows what this payload carries, so it must hold every material, uncapped.
		var server = helper.getLevel().getServer();
		// Not another test's fixture: gametest_corpus/* builds can sit in the listing depending on test order (build 1016 failed on one).
		TablePayloads.Entry largest = TableServer.listing(server).stream()
			.filter(e -> !e.id().getPath().startsWith("gametest_corpus/"))
			.max(java.util.Comparator.comparingInt(TablePayloads.Entry::blocks)).orElseThrow();
		var blueprint = BlueprintLibrary.get(server, largest.id()).orElseThrow();
		java.util.Map<ResourceLocation, Integer> expected = new java.util.HashMap<>();
		for (var entry : blueprint.blocks()) {
			if (io.github.jcondedata.aliveworkplace.build.MaterialRules.classify(entry.state(), entry.nbt())
				!= io.github.jcondedata.aliveworkplace.build.MaterialRules.Kind.SKIP) {
				for (var r : io.github.jcondedata.aliveworkplace.build.MaterialRules.requirements(entry.state(), entry.nbt())) {
					expected.merge(BuiltInRegistries.ITEM.getKey(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(r.item())), r.count(), Integer::sum);
				}
			}
		}
		for (var entity : blueprint.entities()) {
			var cost = io.github.jcondedata.aliveworkplace.blueprint.BlueprintEntities.cost(entity.nbt());
			if (cost != null && cost != Items.AIR) {
				expected.merge(BuiltInRegistries.ITEM.getKey(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(cost)), 1, Integer::sum);
			}
		}
		helper.assertTrue(expected.size() > 20, largest.id() + " should need more than 20 materials (the table's short list), needs " + expected.size());

		TablePayloads.Details sent = new TablePayloads.Details(largest.id(), TableServer.materials(blueprint), List.of());
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		TablePayloads.Details.CODEC.encode(buf, sent);
		TablePayloads.Details got = TablePayloads.Details.CODEC.decode(buf);
		java.util.Map<ResourceLocation, Integer> received = new java.util.HashMap<>();
		for (TablePayloads.Material m : got.materials()) {
			helper.assertTrue(received.put(m.item(), m.count()) == null, m.item() + " listed twice");
		}
		helper.assertTrue(received.equals(expected), largest.id() + ": the payload's " + received.size() + " materials differ from MaterialRules' "
			+ expected.size() + ": missing " + com.google.common.collect.Maps.difference(expected, received).entriesOnlyOnLeft().keySet()
			+ ", extra " + com.google.common.collect.Maps.difference(expected, received).entriesOnlyOnRight().keySet()
			+ ", counts differ " + com.google.common.collect.Maps.difference(expected, received).entriesDiffering());
		for (int i = 1; i < got.materials().size(); i++) {
			helper.assertTrue(got.materials().get(i - 1).count() >= got.materials().get(i).count(), "materials not sorted by count");
		}
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void takingABlueprintCostsABlankBlueprint(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			helper.assertTrue(!TableServer.take(player, table, StarterBlueprints.MARKET_STALL.id()), "took a blueprint without a Blank Blueprint");
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
			helper.assertTrue(TableServer.take(player, table, StarterBlueprints.MARKET_STALL.id()), "could not take a blueprint");
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "blank blueprint not consumed");
			boolean found = false;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				found |= BlueprintItem.data(player.getInventory().getItem(i))
					.map(d -> d.structure().equals(StarterBlueprints.MARKET_STALL.id())).orElse(false);
			}
			helper.assertTrue(found, "no Market Stall blueprint in the inventory");
			player.moveTo(new Vec3(helper.absolutePos(TABLE).getX() + 30, player.getY(), player.getZ()));
			helper.assertTrue(!TableServer.take(player, table, StarterBlueprints.MARKET_STALL.id()), "took a blueprint from 30 blocks away");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void uploadsArriveInPiecesAndJoinTheLibrary(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ImportGameTests.clearGenerated(helper, "uploads/test_mock_player");
		ServerPlayer player = playerAtTable(helper);
		try {
			byte[] file = ImportGameTests.bytes("hut.litematic");
			send(helper, player, "Tiny Hut.litematic", file, 100);
			ResourceLocation id = AliveWorkplace.id("uploads/test_mock_player/tiny_hut");
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), id).isPresent(), "upload did not create " + id);

			// A piece out of order must not produce a blueprint.
			byte[] piece = Arrays.copyOfRange(file, 100, 200);
			TableServer.upload(player, new TablePayloads.UploadChunk(helper.absolutePos(TABLE), "Broken.litematic", file.length, 100, piece));
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), AliveWorkplace.id("uploads/test_mock_player/broken")).isEmpty(), "out-of-order upload was accepted");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"uploads_off"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "uploads_off")
	public void uploadsCanBeTurnedOff(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = playerAtTable(helper);
		var rule = helper.getLevel().getGameRules().getRule(ModGameRules.ALLOW_UPLOADS);
		rule.set(false, helper.getLevel().getServer());
		try {
			send(helper, player, "Forbidden.litematic", ImportGameTests.bytes("hut.litematic"), 30_000);
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), AliveWorkplace.id("uploads/test_mock_player/forbidden")).isEmpty(),
				"upload went through with workplaceAllowUploads=false");
		} finally {
			rule.set(true, helper.getLevel().getServer());
			leave(helper, player);
		}
		helper.succeed();
	}

	/**
	 * The Scan Tool: a little build marked with two corners is saved as a blueprint for a Blank Blueprint (none, no scan);
	 * the player gets the blueprint, it's in the library with the build's blocks, and the tool's name names it.
	 */
	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void scanToolSavesABuild(GameTestHelper helper) {
		ServerPlayer player = playerAtTable(helper);
		try {
			for (int x = 3; x <= 5; x++) {
				helper.setBlock(new BlockPos(x, 2, 3), net.minecraft.world.level.block.Blocks.OAK_PLANKS);
			}
			helper.setBlock(new BlockPos(4, 3, 3), net.minecraft.world.level.block.Blocks.LANTERN);
			net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
				helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(new BlockPos(5, 3, 3)));
			String name = "Scan Test " + Long.toHexString(helper.getLevel().getGameTime());
			var refused = io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem.save(helper.getLevel(), player, box, name);
			helper.assertTrue(refused.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& t.getKey().equals("message.aliveworkplace.scan.no_blank"), "saved without a Blank Blueprint: " + refused.getString());
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			var saved = io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem.save(helper.getLevel(), player, box, name);
			helper.assertTrue(saved.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& t.getKey().equals("message.aliveworkplace.scan.saved"), "not saved: " + saved.getString());
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "one Blank Blueprint should be used");
			ItemStack blueprint = ItemStack.EMPTY;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
					blueprint = player.getInventory().getItem(i);
				}
			}
			helper.assertTrue(!blueprint.isEmpty(), "no blueprint given");
			ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
			helper.assertTrue(id.getPath().startsWith("scans/") && id.getPath().contains("scan_test_"), "id " + id);
			var bp = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
			helper.assertTrue(bp.size().equals(new net.minecraft.core.Vec3i(3, 2, 1)), "size " + bp.size());
			helper.assertTrue(bp.solidBlockCount() == 4, "blocks " + bp.solidBlockCount());
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	private static void send(GameTestHelper helper, ServerPlayer player, String name, byte[] file, int chunkSize) {
		for (int offset = 0; offset < file.length; offset += chunkSize) {
			byte[] chunk = Arrays.copyOfRange(file, offset, Math.min(file.length, offset + chunkSize));
			TableServer.upload(player, new TablePayloads.UploadChunk(helper.absolutePos(TABLE), name, file.length, offset, chunk));
		}
	}

	/** The style the style screen (sneak-click) sets on a cottage blueprint in hand, as the blueprint id it leaves there. */
	private static ResourceLocation styleMenuPick(GameTestHelper helper, String style) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE.id(), StarterBlueprints.STARTER_COTTAGE.size()));
			int i = BlueprintStyles.all().stream().map(BlueprintStyles.Style::name).toList().indexOf(style);
			helper.assertTrue(i >= 0, "no " + style + " style");
			StylePicker.forTest(player, InteractionHand.MAIN_HAND).press(StylePicker.slot(i + 1), player);
			return BlueprintItem.data(player.getMainHandItem()).orElseThrow().structure();
		} finally {
			leave(helper, player);
		}
	}

	/** 26.11: Get Blueprint with a style chosen gives the blueprint the style screen would have made. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void takingAStyledBlueprintGivesItInThatStyle(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ResourceLocation menu = styleMenuPick(helper, "stonework");
		ServerPlayer player = playerAtTable(helper);
		try {
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "stonework");
			helper.assertTrue(TableServer.take(player, helper.absolutePos(TABLE), styled), "could not take the cottage in Stonework");
			ResourceLocation got = null;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				got = BlueprintItem.data(player.getInventory().getItem(i)).map(d -> d.structure()).orElse(got);
			}
			helper.assertTrue(menu.equals(got), "the Table gave " + got + ", the style screen sets " + menu);
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	/** 26.11: the Table's materials in a style are the style screen's blueprint's; the build offers that style, Apricorn only with Cobblemon. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void styledMaterialsMatchTheStyleScreensBlueprint(GameTestHelper helper) {
		var level = helper.getLevel();
		ResourceLocation menu = styleMenuPick(helper, "sandstone");
		ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		TablePayloads.Details details = TableServer.details(level, null, styled).orElse(null);
		helper.assertTrue(details != null && details.id().equals(styled), "no details for the cottage in Sandstone");
		List<TablePayloads.Material> want = TableServer.materials(BlueprintLibrary.get(level, menu).orElseThrow());
		helper.assertTrue(details.materials().equals(want), "the Table lists " + details.materials() + ", the style screen's blueprint needs " + want);
		helper.assertTrue(!want.equals(TableServer.details(level, null, StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow().materials()),
			"Sandstone needs the same as the cottage as drawn");
		List<String> names = details.styles().stream().map(TablePayloads.StyleChoice::name).toList();
		helper.assertTrue(names.contains("sandstone") && names.contains("stonework"), "the cottage offers " + names);
		helper.assertTrue(names.contains("apricorn") == io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon"),
			"Apricorn offered " + names.contains("apricorn") + " with Cobblemon " + io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon"));
		helper.assertTrue(TableServer.preview(level.getServer(), styled).map(p -> p.id().equals(styled)).orElse(false), "no Sandstone preview");
		helper.succeed();
	}

	/** 26.11: a style the build doesn't take is refused: no details, no preview, no blueprint, the Blank Blueprint kept. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUnsupportedStyleIsRefused(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ResourceLocation bad = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "no_such_style");
		helper.assertTrue(TableServer.details(helper.getLevel(), null, bad).isEmpty(), "details sent for an unknown style");
		helper.assertTrue(TableServer.preview(helper.getLevel().getServer(), bad).isEmpty(), "a preview for an unknown style");
		ServerPlayer player = playerAtTable(helper);
		try {
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			helper.assertTrue(!TableServer.take(player, helper.absolutePos(TABLE), bad), "took the cottage in a style that isn't there");
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "the Blank Blueprint was used up");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	private static ServerPlayer playerAtTable(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}
}
