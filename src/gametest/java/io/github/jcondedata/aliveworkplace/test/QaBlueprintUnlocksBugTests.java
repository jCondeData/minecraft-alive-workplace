package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBlueprintUnlocksGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBlueprintUnlocksGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaBlueprintUnlocksGameTests.TABLE;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks.State;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Bug tests for 26.10 found by QA (lane-b-1025-0038); the builder commits each with its fix. */
public class QaBlueprintUnlocksBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	/**
	 * A found Market Stall Blueprint in the off hand, the main hand empty, right-clicked on the Table: the build unlocks
	 * in the village and the chat says so, as from the main hand (the B338/B341 rule: either hand). Bug B419; the Table keeps
	 * nothing.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockOffHand"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockOffHand")
	public void aFoundBlueprintInTheOffHandUnlocksItsBuild(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, HALL, StarterBlueprints.MARKET_STALL) == State.LOCKED, "the Market Stall isn't locked to begin with");
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), StarterBlueprints.MARKET_STALL.size()));
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, HALL, StarterBlueprints.MARKET_STALL) == State.UNLOCKED,
				"a found Market Stall in the off hand didn't unlock it (saw " + QaStageFourGameTests.keys(c.seen()) + ")");
			Component line = QaStageFourGameTests.find(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE);
			helper.assertTrue(line != null, "no chat line about the new blueprint: " + QaStageFourGameTests.keys(c.seen()));
			QaStageFourGameTests.assertRendered(helper, line, "Market Stall");
			helper.assertTrue(p.getOffhandItem().is(ModItems.BLUEPRINT), "the Table took the off hand's blueprint: " + p.getOffhandItem());
			helper.succeed();
		});
	}

	/** B419's neighbour: the off hand's blueprint says "new blueprint" once and opens the Table screen once, with the build unlocked. */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockOffHandOnce"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockOffHandOnce")
	public void aFoundBlueprintInTheOffHandOpensTheTableOnce(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, BlueprintItem.create(StarterBlueprints.FOUNTAIN.id(), StarterBlueprints.FOUNTAIN.size()));
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE) == 1,
				"the off hand's blueprint said 'new blueprint' " + QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE) + " times");
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1, "the Table screen opened " + opened.size() + " times");
			helper.assertTrue(opened.get(0).entries().stream().anyMatch(e -> e.id().equals(StarterBlueprints.FOUNTAIN.id()) && e.unlocked()),
				"the Table screen doesn't show the Fountain unlocked");
			helper.succeed();
		});
	}

	/** B419's neighbour: anything else in the off hand (a stick), the main hand empty, opens the Table as usual and unlocks nothing. */
	//$ gametest_ticks_batch AREA '40' '"qaUnlockOffHandStick"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaUnlockOffHandStick")
	public void anythingElseInTheOffHandOpensTheTableAsUsual(GameTestHelper helper) {
		QaBlueprintUnlocksGameTests.setUp(helper, 16);
		QaBlueprintUnlocksGameTests.village(helper, HALL, TABLE);
		helper.runAfterDelay(3, () -> {
			VillageHalls.forgetNearest();
			QaStageFiveGameTests.Client c = QaBlueprintUnlocksGameTests.client(helper, TABLE);
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
			c.sent();
			QaBlueprintUnlocksGameTests.clickTable(helper, p, TABLE);
			helper.assertTrue(c.sent(TablePayloads.Open.class).size() == 1, "a stick in the off hand didn't open the Table once");
			helper.assertTrue(QaBlueprintUnlocksGameTests.linesOf(c.seen(), QaBlueprintUnlocksGameTests.NEW_LINE) == 0, "a stick unlocked something");
			helper.assertTrue(QaBlueprintUnlocksGameTests.state(helper, HALL, StarterBlueprints.MARKET_STALL) == State.LOCKED, "a stick unlocked the Market Stall");
			helper.assertTrue(p.getOffhandItem().is(Items.STICK), "the Table took the stick");
			helper.succeed();
		});
	}
}
