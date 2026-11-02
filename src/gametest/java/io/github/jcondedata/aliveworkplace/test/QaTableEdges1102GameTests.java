package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-02), the Blueprint Table's Get Blueprint and shelf, from the lang text ("You need a Blank Blueprint (craft
 * paper + blue dye)", "Got a blueprint for %s") and the blueprint-library wiki page: a blueprint costs one Blank
 * Blueprint, the screen only works at the table. Corners TableGameTests and QaBlueprintTableGameTests leave out: the
 * table broken or swapped for another block while its screen is still open (a Get Blueprint, a Store and a Take Out
 * packet then all do nothing and cost nothing), creative players (no blank needed, none taken) and a full inventory
 * (the new blueprint is never lost).
 */
public class QaTableEdges1102GameTests {
	private static final BlockPos TABLE = new BlockPos(1, 1, 1);
	private static final ResourceLocation STALL = StarterBlueprints.MARKET_STALL.id();

	private static ServerPlayer playerAtTable(GameTestHelper helper, GameType mode) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(mode);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		return player;
	}

	private static void leave(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	private static int blueprints(ServerPlayer player, ResourceLocation id) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (BlueprintItem.data(stack).map(d -> d.structure().equals(id)).orElse(false)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static int dropped(GameTestHelper helper, ResourceLocation id) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8),
			e -> BlueprintItem.data(e.getItem()).map(d -> d.structure().equals(id)).orElse(false)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static void brokenTable(GameTestHelper helper, net.minecraft.world.level.block.Block replacement) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = playerAtTable(helper, GameType.SURVIVAL);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
			// A blueprint on the shelf first, while the table stands.
			helper.assertTrue(TableServer.take(player, table, STALL), "could not take a blueprint at a standing table");
			player.getInventory().selected = player.getInventory().findSlotMatchingItem(firstBlueprint(player));
			helper.assertTrue(TableLibrary.store(player, table, false) == 1, "the held blueprint wasn't put on the shelf");
			helper.assertTrue(blueprints(player, STALL) == 0, "the stored blueprint is still in the inventory");
			// The screen is still open on the client; the table goes.
			helper.setBlock(TABLE, replacement);
			helper.assertTrue(!TableServer.take(player, table, STALL), "Get Blueprint worked at a table that is now " + replacement);
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "a blank was used at a missing table: "
				+ player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " left of 2");
			helper.assertTrue(blueprints(player, STALL) == 0, "a blueprint came out of a missing table");
			helper.assertTrue(!TableLibrary.takeOut(player, table, BlueprintItem.create(STALL, net.minecraft.core.Vec3i.ZERO)),
				"Take Out worked at a table that is now " + replacement);
			helper.assertTrue(blueprints(player, STALL) == 0, "Take Out at a missing table gave a blueprint");
			player.getInventory().add(BlueprintItem.create(STALL, net.minecraft.core.Vec3i.ZERO));
			player.getInventory().selected = player.getInventory().findSlotMatchingItem(firstBlueprint(player));
			helper.assertTrue(TableLibrary.store(player, table, true) == 0, "Store worked at a table that is now " + replacement);
			helper.assertTrue(blueprints(player, STALL) == 1, "Store at a missing table took the held blueprint away");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	private static ItemStack firstBlueprint(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				return player.getInventory().getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	/** Broken (now air) with the screen open: nothing is handed out, stored or taken out, and no blank is used. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTableBrokenWithItsScreenOpenHandsOutAndTakesNothing(GameTestHelper helper) {
		brokenTable(helper, Blocks.AIR);
	}

	/** Swapped for a crafting table (another block in its place): the same. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTableSwappedForAnotherBlockHandsOutAndTakesNothing(GameTestHelper helper) {
		brokenTable(helper, Blocks.CRAFTING_TABLE);
	}

	/** A creative player needs no Blank Blueprint, and one he carries isn't used. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aCreativePlayerGetsABlueprintWithoutABlank(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = playerAtTable(helper, GameType.CREATIVE);
		try {
			BlockPos table = helper.absolutePos(TABLE);
			helper.assertTrue(TableServer.take(player, table, STALL), "a creative player without a blank got nothing");
			helper.assertTrue(blueprints(player, STALL) == 1, "a creative player's blueprint isn't in the inventory");
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			helper.assertTrue(TableServer.take(player, table, STALL), "a creative player with blanks got nothing");
			helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "a creative player's blank was used: "
				+ player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " of 2 left");
			helper.assertTrue(blueprints(player, STALL) + dropped(helper, STALL) == 2, "a creative player has " + blueprints(player, STALL) + " Market Stall blueprints");
		} finally {
			leave(helper, player);
		}
		helper.succeed();
	}

	/**
	 * A full inventory with a stack of 2 blanks (taking one frees no slot): the blank is paid once and the blueprint is
	 * not lost, in the inventory or dropped at the player's feet.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFullInventoryNeverLosesTheNewBlueprint(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = playerAtTable(helper, GameType.SURVIVAL);
		{
			BlockPos table = helper.absolutePos(TABLE);
			player.getInventory().setItem(0, new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
			for (int i = 1; i < player.getInventory().items.size(); i++) {
				player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
			}
			helper.assertTrue(player.getInventory().getFreeSlot() < 0, "the inventory isn't full");
			boolean took = TableServer.take(player, table, STALL);
			int blanks = player.getInventory().countItem(ModItems.BLANK_BLUEPRINT);
			int have = blueprints(player, STALL);
			helper.runAfterDelay(2, () -> {
				int onFloor = dropped(helper, STALL);
				if (took) {
					helper.assertTrue(blanks == 1, "the blank was paid " + (2 - blanks) + " times");
					helper.assertTrue(have + onFloor == 1, "after Get Blueprint with a full inventory: " + have + " in it and " + onFloor + " on the floor");
				} else {
					helper.assertTrue(blanks == 2 && have + onFloor == 0, "refused, but " + blanks + " blanks are left and " + (have + onFloor) + " blueprints given");
				}
				leave(helper, player);
				helper.succeed();
			});
		}
	}
}
