package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Blueprint Table as a library (36.13): bought blueprints stored in the table (the one in hand, or every one carried),
 * listed with how many of each, taken back out one at a time; a full inventory keeps it in the table, a broken table
 * drops them, and the table keeps them through a save and reload. Each goes through the same calls the screen's buttons
 * send ({@link TableLibrary#store}, {@link TableLibrary#takeOut}) and the payload the screen lists.
 */
public class TableLibraryGameTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE.id();
	private static final ResourceLocation STALL = StarterBlueprints.MARKET_STALL.id();

	private static ItemStack blueprint(ResourceLocation id) {
		return BlueprintItem.create(id, null);
	}

	private static ServerPlayer playerAtTable(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static BlueprintTableBlockEntity shelf(GameTestHelper helper) {
		BlockEntity entity = helper.getBlockEntity(TABLE);
		helper.assertTrue(entity instanceof BlueprintTableBlockEntity, "the Blueprint Table has no shelf: " + entity);
		return (BlueprintTableBlockEntity) entity;
	}

	private static int carried(ServerPlayer player, ResourceLocation id) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (BlueprintItem.data(stack).map(d -> d.structure().equals(id)).orElse(false)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** How many of the blueprint for {@code id} the screen's Stored tab lists, from what the server sends it. */
	private static int listed(ServerPlayer player, GameTestHelper helper, ResourceLocation id) {
		return TableServer.openPayload(player, helper.absolutePos(TABLE)).stored().stream()
			.filter(s -> BlueprintItem.data(s.stack()).map(d -> d.structure().equals(id)).orElse(false))
			.mapToInt(TablePayloads.Stored::count).sum();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void storingTheHeldBlueprintPutsItInTheTable(GameTestHelper helper) {
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			// Nothing in hand, or a Blank Blueprint: nothing to store.
			helper.assertTrue(TableLibrary.store(player, table, false) == 0, "stored something from an empty hand");
			player.getInventory().setItem(player.getInventory().selected, new ItemStack(ModItems.BLANK_BLUEPRINT, 4));
			helper.assertTrue(TableLibrary.store(player, table, false) == 0, "stored a Blank Blueprint");
			helper.assertTrue(player.getMainHandItem().getCount() == 4, "the Blank Blueprints were taken");
			player.getInventory().setItem(player.getInventory().selected, blueprint(COTTAGE));
			helper.assertTrue(TableLibrary.store(player, table, false) == 1, "the cottage blueprint in hand wasn't stored");
			helper.assertTrue(player.getMainHandItem().isEmpty(), "the stored blueprint is still in hand: " + player.getMainHandItem());
			helper.assertTrue(shelf(helper).count() == 1, "the table holds " + shelf(helper).count() + " blueprints, expected 1");
			helper.assertTrue(listed(player, helper, COTTAGE) == 1, "the Stored tab doesn't list the cottage");
			// Too far away: refused, the blueprint stays with the player.
			player.getInventory().setItem(player.getInventory().selected, blueprint(STALL));
			player.moveTo(new Vec3(table.getX() + 30, player.getY(), player.getZ()));
			helper.assertTrue(TableLibrary.store(player, table, false) == 0, "stored a blueprint from 30 blocks away");
			helper.assertTrue(carried(player, STALL) == 1, "the stall blueprint left the player at a distance");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void storeAllListsEachBlueprintWithItsCount(GameTestHelper helper) {
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			player.getInventory().setItem(0, blueprint(COTTAGE));
			player.getInventory().setItem(5, blueprint(COTTAGE));
			player.getInventory().setItem(20, blueprint(STALL));
			player.getInventory().setItem(30, blueprint(COTTAGE));
			player.getInventory().setItem(8, new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			player.getInventory().setItem(9, new ItemStack(Items.PAPER, 7));
			helper.assertTrue(TableLibrary.store(player, table, true) == 4, "Store All didn't store the four blueprints carried");
			helper.assertTrue(carried(player, COTTAGE) == 0 && carried(player, STALL) == 0, "a blueprint stayed in the inventory");
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2 && player.getInventory().countItem(Items.PAPER) == 7,
				"Store All took something that isn't a blueprint");
			// The count: alike blueprints together, in the order they went in.
			TablePayloads.Open open = TableServer.openPayload(player, table);
			helper.assertTrue(open.stored().size() == 2, "the Stored tab should list 2 blueprints, listed " + open.stored().size());
			helper.assertTrue(open.storedCount() == 4, "the Stored tab counts " + open.storedCount() + " in all, expected 4");
			helper.assertTrue(listed(player, helper, COTTAGE) == 3, "the cottage should be listed ×3, was ×" + listed(player, helper, COTTAGE));
			helper.assertTrue(listed(player, helper, STALL) == 1, "the stall should be listed ×1");
			helper.assertTrue(BlueprintItem.data(open.stored().get(0).stack()).map(d -> d.structure().equals(COTTAGE)).orElse(false),
				"the cottage went in first and should be listed first");
			helper.assertTrue(open.capacity() == BlueprintTableBlockEntity.CAPACITY, "the screen isn't told the table's size");
			// The list survives the wire, counts and all.
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			TablePayloads.Open.CODEC.encode(buf, open);
			TablePayloads.Open back = TablePayloads.Open.CODEC.decode(buf);
			helper.assertTrue(back.stored().size() == 2 && back.storedCount() == 4
				&& ItemStack.isSameItemSameComponents(back.stored().get(0).stack(), open.stored().get(0).stack()), "the stored list changed on the wire");
			// Nothing left to store.
			helper.assertTrue(TableLibrary.store(player, table, true) == 0, "Store All stored something with no blueprints carried");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void takingOneOutGivesItBackAndCountsDown(GameTestHelper helper) {
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			ItemStack cottage = blueprint(COTTAGE);
			player.getInventory().setItem(0, cottage.copy());
			player.getInventory().setItem(1, cottage.copy());
			TableLibrary.store(player, table, true);
			helper.assertTrue(TableLibrary.takeOut(player, table, cottage), "couldn't take a cottage out");
			helper.assertTrue(carried(player, COTTAGE) == 1, "the taken-out cottage isn't in the inventory");
			helper.assertTrue(listed(player, helper, COTTAGE) == 1, "the count didn't go down to 1");
			helper.assertTrue(TableLibrary.takeOut(player, table, cottage), "couldn't take the second cottage out");
			helper.assertTrue(carried(player, COTTAGE) == 2 && shelf(helper).count() == 0, "the table should be empty and the player hold 2");
			helper.assertTrue(TableServer.openPayload(player, table).stored().isEmpty(), "an empty table still lists something");
			// None left, or one that was never stored: refused.
			helper.assertFalse(TableLibrary.takeOut(player, table, cottage), "took a cottage out of an empty table");
			helper.assertTrue(carried(player, COTTAGE) == 2, "a blueprint came out of nowhere");
			// What comes out is the blueprint that went in, as it was.
			TableLibrary.store(player, table, true);
			ItemStack out = ItemStack.EMPTY;
			TableLibrary.takeOut(player, table, cottage);
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
					out = player.getInventory().getItem(i);
				}
			}
			helper.assertTrue(ItemStack.isSameItemSameComponents(out, cottage), "the blueprint changed in the table: " + out.getComponents());
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void aFullInventoryLeavesTheBlueprintInTheTable(GameTestHelper helper) {
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			player.getInventory().setItem(0, blueprint(STALL));
			TableLibrary.store(player, table, true);
			for (int i = 0; i < 36; i++) {
				player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			helper.assertFalse(TableLibrary.takeOut(player, table, blueprint(STALL)), "took a blueprint out into a full inventory");
			helper.assertTrue(shelf(helper).count() == 1, "the blueprint left the table with nowhere to go");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(table).inflate(2), e -> e.getItem().is(ModItems.BLUEPRINT)).isEmpty(),
				"the blueprint was dropped on the floor");
			// Room again: it comes out.
			player.getInventory().setItem(17, ItemStack.EMPTY);
			helper.assertTrue(TableLibrary.takeOut(player, table, blueprint(STALL)), "couldn't take it out once there was room");
			helper.assertTrue(carried(player, STALL) == 1, "the stall blueprint isn't in the freed slot");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void aFullTableKeepsTheRestWithThePlayer(GameTestHelper helper) {
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			BlueprintTableBlockEntity shelf = shelf(helper);
			for (int i = 0; i < BlueprintTableBlockEntity.CAPACITY - 1; i++) {
				shelf.put(blueprint(STALL));
			}
			for (int i = 0; i < 3; i++) {
				player.getInventory().setItem(i, blueprint(COTTAGE));
			}
			helper.assertTrue(TableLibrary.store(player, table, true) == 1, "a table one short of full should take one more");
			helper.assertTrue(shelf.count() == BlueprintTableBlockEntity.CAPACITY, "the table went past " + BlueprintTableBlockEntity.CAPACITY);
			helper.assertTrue(carried(player, COTTAGE) == 2, "the two that didn't fit should stay with the player");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	//$ gametest_batch '"aliveworkplace_test:build_area"' '"table_library_drop"'
	@GameTest(template = "aliveworkplace_test:build_area", batch = "table_library_drop")
	public void aBrokenTableDropsItsBlueprints(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = playerAtTable(helper);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			player.getInventory().setItem(0, blueprint(COTTAGE));
			player.getInventory().setItem(1, blueprint(COTTAGE));
			player.getInventory().setItem(2, blueprint(STALL));
			TableLibrary.store(player, table, true);
		} finally {
			leave(helper, player);
		}
		helper.destroyBlock(TABLE);
		helper.assertBlockPresent(Blocks.AIR, TABLE);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(TABLE)).inflate(3),
			e -> e.getItem().is(ModItems.BLUEPRINT));
		int cottages = 0;
		int stalls = 0;
		for (ItemEntity drop : drops) {
			ResourceLocation id = BlueprintItem.data(drop.getItem()).map(d -> d.structure()).orElse(null);
			cottages += COTTAGE.equals(id) ? drop.getItem().getCount() : 0;
			stalls += STALL.equals(id) ? drop.getItem().getCount() : 0;
		}
		helper.assertTrue(cottages == 2 && stalls == 1, "the broken table dropped " + cottages + " cottages and " + stalls + " stalls, expected 2 and 1");
		drops.forEach(ItemEntity::discard);
		helper.succeed();
	}

	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void theTableKeepsItsBlueprintsThroughASaveAndReload(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlueprintTableBlockEntity shelf = shelf(helper);
		shelf.put(blueprint(COTTAGE));
		shelf.put(blueprint(STALL));
		shelf.put(blueprint(COTTAGE));
		var registries = helper.getLevel().registryAccess();
		CompoundTag tag = shelf.saveWithFullMetadata(registries);
		BlockEntity back = BlockEntity.loadStatic(shelf.getBlockPos(), shelf.getBlockState(), tag, registries);
		helper.assertTrue(back instanceof BlueprintTableBlockEntity, "the table's shelf didn't load: " + back);
		BlueprintTableBlockEntity loaded = (BlueprintTableBlockEntity) back;
		helper.assertTrue(loaded.count() == 3, "the reloaded table holds " + loaded.count() + " blueprints, expected 3");
		List<BlueprintTableBlockEntity.Group> before = shelf.groups();
		List<BlueprintTableBlockEntity.Group> after = loaded.groups();
		helper.assertTrue(after.size() == 2, "the reloaded table lists " + after.size() + " blueprints, expected 2");
		for (int i = 0; i < before.size(); i++) {
			helper.assertTrue(ItemStack.isSameItemSameComponents(before.get(i).stack(), after.get(i).stack()) && before.get(i).count() == after.get(i).count(),
				"stored blueprint " + i + " changed through the reload");
		}
		// A table saved before 36.13 has no shelf data: it loads with nothing stored.
		CompoundTag old = shelf.saveWithFullMetadata(registries);
		old.remove("stored");
		BlockEntity fresh = BlockEntity.loadStatic(shelf.getBlockPos(), shelf.getBlockState(), old, registries);
		helper.assertTrue(fresh instanceof BlueprintTableBlockEntity t && t.count() == 0, "an old table's save didn't load empty");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStoredTabReadsInEnglish(GameTestHelper helper) {
		String[][] want = {
			{"Library", english("screen.aliveworkplace.table.tab_library")},
			{"Stored (3)", english("screen.aliveworkplace.table.tab_stored", 3)},
			{"4 of 200 stored", english("screen.aliveworkplace.table.stored_count", 4, 200)},
			{"2 stored", english("screen.aliveworkplace.table.stored_row", 2)},
			{"2 stored in this table", english("screen.aliveworkplace.table.stored_here", 2)},
			{"Take One Out", english("screen.aliveworkplace.table.take_out")},
			{"Store Held", english("screen.aliveworkplace.table.store_held")},
			{"Store All", english("screen.aliveworkplace.table.store_all")},
			{"Nothing stored yet.", english("screen.aliveworkplace.table.stored_none")},
			{"Stored 3 blueprints in the table", english("message.aliveworkplace.table.stored_many", 3)},
			{"Your inventory is full: the blueprint stays in the table", english("message.aliveworkplace.table.take_out_full")},
			{"The table is full: it holds 200 blueprints at most", english("message.aliveworkplace.table.store_full", 200)},
			{"Hold a blueprint in your hand to store it", english("message.aliveworkplace.table.store_not_held")},
		};
		for (String[] pair : want) {
			helper.assertTrue(pair[0].equals(pair[1]), "\"" + pair[1] + "\", expected \"" + pair[0] + "\"");
		}
		// The rest exist and read as sentences.
		for (String key : List.of("screen.aliveworkplace.table.stored_empty", "screen.aliveworkplace.table.stored_help", "screen.aliveworkplace.table.stored_pick",
			"message.aliveworkplace.table.store_none", "message.aliveworkplace.table.not_stored")) {
			String text = english(key);
			helper.assertTrue(text.length() > 10 && Character.isUpperCase(text.charAt(0)), key + " reads \"" + text + "\"");
		}
		helper.assertTrue(english("message.aliveworkplace.table.stored", "Blueprint: Starter Cottage").equals("Stored Blueprint: Starter Cottage in the table"),
			"the one-blueprint message reads wrong");
		helper.assertTrue(english("message.aliveworkplace.table.took_out", "Blueprint: Starter Cottage").equals("Took Blueprint: Starter Cottage out of the table"),
			"the take-out message reads wrong");
		helper.succeed();
	}

	private static String english(String key, Object... args) {
		return JewellerGameTests.english(Component.translatable(key, args));
	}
}
