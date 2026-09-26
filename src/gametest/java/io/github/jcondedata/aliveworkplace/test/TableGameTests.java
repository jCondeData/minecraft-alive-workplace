package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** The Blueprint Table's server side: listing, materials, taking blueprints and uploads. */
public class TableGameTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void listingShowsStarterBlueprintsFirst(GameTestHelper helper) {
		List<TablePayloads.Entry> entries = TableServer.listing(helper.getLevel().getServer());
		helper.assertTrue(entries.size() >= StarterBlueprints.ALL.size(), "library too small: " + entries.size());
		for (int i = 0; i < StarterBlueprints.ALL.size(); i++) {
			helper.assertTrue(entries.get(i).id().getNamespace().equals(AliveWorkplace.MOD_ID) && !entries.get(i).id().getPath().contains("/"),
				"entry " + i + " should be a starter blueprint, was " + entries.get(i).id());
		}
		helper.succeed();
	}

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

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void materialsListMatchesTheBuild(GameTestHelper helper) {
		var cottage = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		List<TablePayloads.Material> materials = TableServer.materials(cottage);
		int planks = materials.stream().filter(m -> m.item().equals(BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS))).mapToInt(TablePayloads.Material::count).sum();
		int doors = materials.stream().filter(m -> m.item().equals(BuiltInRegistries.ITEM.getKey(Items.OAK_DOOR))).mapToInt(TablePayloads.Material::count).sum();
		helper.assertTrue(planks > 50, "cottage should need lots of oak planks, got " + planks);
		helper.assertTrue(doors == 1, "cottage has exactly one door (both halves = 1 item), got " + doors);
		for (int i = 1; i < materials.size(); i++) {
			helper.assertTrue(materials.get(i - 1).count() >= materials.get(i).count(), "materials not sorted by count");
		}
		helper.succeed();
	}

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

	private static void send(GameTestHelper helper, ServerPlayer player, String name, byte[] file, int chunkSize) {
		for (int offset = 0; offset < file.length; offset += chunkSize) {
			byte[] chunk = Arrays.copyOfRange(file, offset, Math.min(file.length, offset + chunkSize));
			TableServer.upload(player, new TablePayloads.UploadChunk(helper.absolutePos(TABLE), name, file.length, offset, chunk));
		}
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
