package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Function;

/**
 * QA (lane-b-1108-0638) for the Blueprint Table's requests (26.10, docs/wiki/blueprint-library.md): the Table answers a
 * player standing at it. A request that names a Table thousands of blocks away (a modified client can send any
 * position) must be refused without the server loading, or generating, the land there: on a friends' server one player
 * could otherwise make it generate chunks anywhere, a packet at a time. Each request kind that names a Table (Get
 * Blueprint, Store, Take Stored) is sent for a spot about 9,600 blocks off, and the chunk there must still be unloaded,
 * with nothing handed out or taken.
 */
public class QaTableFar1108bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation COTTAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "starter_cottage");

	//$ gametest_ticks_batch AREA '80' '"qa1108bTableFar"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108bTableFar")
	public void requestsNamingATableFarAwayLoadNoLandThere(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, QaBlueprintUnlocksGameTests.HALL, tableRel);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
		ServerPlayer p = c.player();
		p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		p.getInventory().add(new ItemStack(Items.OAK_PLANKS, 5));
		List<Function<BlockPos, net.minecraft.network.protocol.common.custom.CustomPacketPayload>> requests = List.of(
			far -> new TablePayloads.Take(far, COTTAGE),
			far -> new TablePayloads.Store(far, true),
			far -> new TablePayloads.TakeStored(far, new ItemStack(Items.OAK_PLANKS)));
		List<String> names = List.of("Get Blueprint", "Store", "Take Stored");
		BlockPos near = helper.absolutePos(tableRel);
		List<String> loaded = new java.util.ArrayList<>();
		for (int i = 0; i < requests.size(); i++) {
			// A fresh spot per request, far from every test area and player, so nothing else loads it.
			BlockPos far = near.offset(9_600 + i * 1_600, 0, 9_600 - i * 1_600);
			int cx = SectionPos.blockToSectionCoord(far.getX());
			int cz = SectionPos.blockToSectionCoord(far.getZ());
			helper.assertTrue(!level.hasChunk(cx, cz), "the far chunk " + cx + ", " + cz + " was loaded before the test sent anything");
			c.send(requests.get(i).apply(far));
			if (level.hasChunk(cx, cz)) {
				loaded.add(names.get(i) + " (Table at " + far.toShortString() + ")");
			}
		}
		helper.assertTrue(loaded.isEmpty(), "requests naming a Table about 9,600 blocks from the player loaded the land there: " + loaded);
		helper.assertTrue(p.getInventory().countItem(ModItems.BLUEPRINT) == 0 && p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2
				&& p.getInventory().countItem(Items.OAK_PLANKS) == 5,
			"a far Table's requests changed the player's things: " + p.getInventory().countItem(ModItems.BLUEPRINT) + " blueprints, "
				+ p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " blanks, " + p.getInventory().countItem(Items.OAK_PLANKS) + " planks");
		helper.succeed();
	}

	/** Control: the same requests at the Table the player stands by work (Get Blueprint hands out the cottage). */
	//$ gametest_ticks_batch AREA '80' '"qa1108bTableNear"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qa1108bTableNear")
	public void theSameRequestAtTheTableBesideThePlayerWorks(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		BlockPos tableRel = QaBlueprintUnlocksGameTests.TABLE;
		QaBlueprintUnlocksGameTests.village(helper, QaBlueprintUnlocksGameTests.HALL, tableRel);
		QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, tableRel);
		ServerPlayer p = c.player();
		p.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		helper.runAfterDelay(3, () -> {
			c.send(new TablePayloads.Take(helper.absolutePos(tableRel), COTTAGE));
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(p.getInventory().countItem(ModItems.BLUEPRINT) == 1 && p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1,
					"Get Blueprint at the Table beside the player: " + p.getInventory().countItem(ModItems.BLUEPRINT) + " blueprints, "
						+ p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) + " blanks (expected 1 and 1)");
				helper.succeed();
			});
		});
	}
}
