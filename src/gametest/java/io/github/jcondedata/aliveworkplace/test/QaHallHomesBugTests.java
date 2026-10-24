package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
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
 * QA (lane-c-1024-0353, 2026-10-24): bugs found in the Village Hall and its beds, each committed with its fix (B360, B361)
 * (the passing ones are in {@link QaHallHomesGameTests}).
 */
public class QaHallHomesBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

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

	/**
	 * village-hall.md "Everyone within 64 blocks belongs to it" (16 here): a villager and his bed, side by side, 13 blocks
	 * east and 13 south of the hall. The census counts him (the villagers are counted in a square) but not his bed (the
	 * beds in a circle, 18.4 away), so the hall, the "beds" tip and the Steward say a villager has no bed when he has.
	 * Expected: he and his bed are both in the village or both out, never one short.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaBedsCorner"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaBedsCorner")
	public void aVillagerAndHisBedInTheCornerAreNotABedShort(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(2, 2, 2);
		QaHallHomesGameTests.hall(helper, hallRel);
		QaHallHomesGameTests.bed(helper, hallRel.offset(13, 0, 13));
		QaHallHomesGameTests.adult(helper, hallRel.offset(14, 0, 13));
		helper.runAfterDelay(5, () -> {
			VillageHalls.Census census = VillageHalls.census(helper.getLevel(), helper.absolutePos(hallRel));
			helper.assertTrue(VillageAdvice.bedsShort(census) == 0, "a villager and his bed 13 east and 13 south: " + census.villagers() + " villager(s) counted, "
				+ census.beds() + " bed(s), so " + VillageAdvice.bedsShort(census) + " short");
			helper.succeed();
		});
	}

	/**
	 * One bed short, the "beds" tip on What next? and the Steward's reason (homes_starter_cottage holds only at exactly 1
	 * short) read "1 villagers have no bed"; the mod's other counted lines have a {@code .one} form. Expected: "1 villager
	 * has no bed".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaOneBedShort"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaOneBedShort")
	public void oneBedShortReadsInTheSingular(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(3, 2, 3);
		QaHallHomesGameTests.hall(helper, hallRel);
		QaHallHomesGameTests.adult(helper, new BlockPos(5, 2, 5));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(QaHallHomesGameTests.bedsShort(helper, hallRel) == 1, "setup: short of " + QaHallHomesGameTests.bedsShort(helper, hallRel));
			List<String> wrong = new ArrayList<>();
			List<String> titles = VillageAdvice.tips(helper.getLevel(), helper.absolutePos(hallRel)).stream().filter(t -> t.key().equals("beds"))
				.map(t -> t.title().getString()).toList();
			helper.assertTrue(titles.equals(List.of("1 villager has no bed")), "the beds tip: " + titles);
			VillageAdvice.tips(helper.getLevel(), helper.absolutePos(hallRel)).stream().filter(t -> t.key().equals("beds"))
				.map(t -> t.title().getString()).filter(s -> s.contains("1 villagers")).forEach(s -> wrong.add("What next? tip: '" + s + "'"));
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qa1b"));
			ServerPlayer p = c.player();
			p.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(hallRel.east(2))));
			c.seen().clear();
			p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), "workplace steward explain");
			for (Component line : c.seen()) {
				if (line.getString().contains("1 villagers")) {
					wrong.add("explain: '" + line.getString() + "'");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "one bed short reads: " + String.join("; ", wrong));
			helper.succeed();
		});
	}
}
