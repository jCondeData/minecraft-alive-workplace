package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.shop.ShopLedger;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, optional-mod absence), bug B416: with CobbleDollars a shop's takings while its owner is offline are kept in
 * the shops' ledger and paid when they join ("While you were away, your shops took ..."). If CobbleDollars is gone by
 * then (taken out of the pack, or broken by an update, when Money falls back to emeralds), the takings must still reach
 * the owner, in emeralds at the rate (100 CobbleDollars an emerald by default), not vanish; what doesn't make a whole
 * emerald stays owed. This test world has no CobbleDollars, so a ledger entry made with it is exactly that case. The
 * same payout with CobbleDollars: {@code ShopLedgerCompatTests}.
 */
public class QaShopAwayBugTests implements FabricGameTest {
	private static final String AWAY = "message.aliveworkplace.shop.while_away";

	/** The "while you were away" lines {@code seen} holds, as read, each once. */
	private static List<String> awayLines(List<Component> seen) {
		java.util.Set<Component> once = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		return seen.stream().filter(c -> AWAY.equals(QaStageFiveGameTests.key(c)) && once.add(c)).map(Component::getString).toList();
	}

	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayBug")
	public void takingsOwedInCobbleDollarsArePaidInEmeraldsWhenCobbleDollarsIsGone(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		helper.assertTrue(Money.DOLLARS_PER_EMERALD == 100, "setup: the rate is " + Money.DOLLARS_PER_EMERALD);
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		var owner = c.player();
		owner.getInventory().clearContent();
		ShopLedger.get(server).owe(owner.getUUID(), 700); // two sales made while CobbleDollars was installed
		ShopLedger.get(server).owe(owner.getUUID(), 534);
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 1234, "setup: owed " + ShopLedger.get(server).pending(owner.getUUID()));
		ShopLedger.collect(owner); // the owner joins, CobbleDollars now gone
		int emeralds = owner.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(emeralds == 12, "1234 CobbleDollars of takings were paid with " + emeralds + " emeralds, not 12");
		long owed = ShopLedger.get(server).pending(owner.getUUID());
		helper.assertTrue(owed == 34, "after 12 emeralds, " + owed + " CobbleDollars are still owed, not the 34 left over");
		helper.assertTrue(awayLines(c.seen()).equals(List.of("While you were away, your shops took 12 emeralds.")),
			"the owner was told: " + awayLines(c.seen()));
		helper.succeed();
	}

	/** Less than an emerald's worth is kept owed with nothing said, and paid once later takings make up an emerald. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayBug")
	public void takingsUnderAnEmeraldStayOwedUntilTheyMakeOne(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		var owner = c.player();
		owner.getInventory().clearContent();
		ShopLedger.get(server).owe(owner.getUUID(), 60);
		ShopLedger.collect(owner);
		helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 0, "60 CobbleDollars paid " + owner.getInventory().countItem(Items.EMERALD) + " emeralds");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 60, "60 CobbleDollars owed became " + ShopLedger.get(server).pending(owner.getUUID()));
		helper.assertTrue(awayLines(c.seen()).isEmpty(), "the owner was told about nothing paid: " + awayLines(c.seen()));
		ShopLedger.get(server).owe(owner.getUUID(), 40); // another sale before the next join
		ShopLedger.collect(owner);
		helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 1, "100 CobbleDollars paid " + owner.getInventory().countItem(Items.EMERALD) + " emeralds");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 0, "still owed: " + ShopLedger.get(server).pending(owner.getUUID()));
		helper.assertTrue(awayLines(c.seen()).equals(List.of("While you were away, your shops took 1 emerald.")), "the owner was told: " + awayLines(c.seen()));
		helper.succeed();
	}

	/** A fortune is paid a full inventory at a time, in stacks, and the rest stays owed for the next join. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayBug")
	public void aFortuneIsPaidAnInventoryOfEmeraldsAtATime(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		var owner = c.player();
		owner.getInventory().clearContent();
		ShopLedger.get(server).owe(owner.getUUID(), 100L * (36 * 64 + 10) + 5);
		ShopLedger.collect(owner);
		int emeralds = owner.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(emeralds == 36 * 64, "paid " + emeralds + " emeralds, not a full inventory (2304)");
		for (int s = 0; s < 36; s++) {
			helper.assertTrue(owner.getInventory().getItem(s).getCount() <= 64, "slot " + s + " holds " + owner.getInventory().getItem(s).getCount());
		}
		long owed = ShopLedger.get(server).pending(owner.getUUID());
		helper.assertTrue(owed == 1005, "still owed " + owed + ", not 1005 CobbleDollars");
		helper.assertTrue(awayLines(c.seen()).equals(List.of("While you were away, your shops took 2304 emeralds.")), "the owner was told: " + awayLines(c.seen()));
		owner.getInventory().clearContent();
		ShopLedger.collect(owner);
		helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 10 && ShopLedger.get(server).pending(owner.getUUID()) == 5,
			"the next join paid " + owner.getInventory().countItem(Items.EMERALD) + " emeralds and left " + ShopLedger.get(server).pending(owner.getUUID()) + " owed");
		helper.succeed();
	}
}
