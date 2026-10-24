package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-c-1024-0353, 2026-10-24): bugs found in the Village Hall and its beds, each committed with its fix (B360)
 * (the passing ones are in {@link QaHallHomesGameTests}).
 */
public class QaHallHomesBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * city-plan.md ("Bind it: right-click a Village Hall"), village-ledger and village-hall.md (the Name Tag): from the OFF
	 * hand with the main hand empty, as B338 fixed for the Captain's Insignia, each still does its job at the hall; today
	 * the empty main hand opens the hall's screen and the off hand is never asked.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaHallOffHand"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHallOffHand")
	public void thePlanTheLedgerAndANameTagWorkFromTheOffHandAtTheHall(GameTestHelper helper) {
		BlockPos rel = new BlockPos(3, 2, 3);
		QaHallHomesGameTests.hall(helper, rel);
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(rel);
		helper.runAfterDelay(2, () -> {
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaho"));
			ServerPlayer p = c.player();
			List<String> wrong = new ArrayList<>();

			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.CITY_PLAN));
			QaStageFourGameTests.press(helper, p, rel);
			var plan = p.getOffhandItem().get(ModComponents.CITY_PLAN_HALL);
			if (plan == null || !plan.hall().pos().equals(abs)) {
				wrong.add("the City Plan wasn't bound (saw " + QaStageFourGameTests.keys(c.seen()) + ", screen open: " + (p.containerMenu != p.inventoryMenu) + ")");
			}
			p.closeContainer();

			c.seen().clear();
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.VILLAGE_LEDGER));
			QaStageFourGameTests.press(helper, p, rel);
			var ledger = p.getOffhandItem().get(ModComponents.LEDGER);
			if (ledger == null || !ledger.hall().pos().equals(abs)) {
				wrong.add("the Village Ledger wasn't bound (saw " + QaStageFourGameTests.keys(c.seen()) + ")");
			}
			p.closeContainer();

			c.seen().clear();
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(DataComponents.CUSTOM_NAME, Component.literal("Oakmere"));
			p.setItemInHand(InteractionHand.OFF_HAND, tag);
			QaStageFourGameTests.press(helper, p, rel);
			if (!"Oakmere".equals(VillageHalls.name(level, abs).getString())) {
				wrong.add("the Name Tag didn't name the village (it is " + VillageHalls.name(level, abs).getString() + ", saw " + QaStageFourGameTests.keys(c.seen()) + ")");
			}
			p.closeContainer();
			helper.assertTrue(wrong.isEmpty(), "from the off hand at the hall: " + String.join("; ", wrong));
			helper.succeed();
		});
	}



}
